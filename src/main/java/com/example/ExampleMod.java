package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, esp = false, chestESP = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, elytraSwap = true, fastPearl = true;

    public static double kaRange = 3.8, kawallsRange = 3.0;
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyESP = -1;
    public static int keyFP = GLFW.GLFW_KEY_V, keyES = GLFW.GLFW_KEY_C;

    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderESP);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long win = client.getWindow().getHandle();

            if (isPressed(win, GLFW.GLFW_KEY_G) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (fastPearl && isPressed(win, keyFP)) throwPearl(client);
                if (elytraSwap && isPressed(win, keyES)) swapElytra(client);
                if (isPressed(win, keyKA)) { killaura = !killaura; notify(client, "KillAura", killaura); }
                if (isPressed(win, keyTB)) { triggerbot = !triggerbot; notify(client, "TriggerBot", triggerbot); }
                if (isPressed(win, keyFB)) { fullbright = !fullbright; notify(client, "FullBright", fullbright); }
                if (isPressed(win, keyAT)) { autoTotem = !autoTotem; notify(client, "AutoTotem", autoTotem); }
                if (isPressed(win, keyESP)) { esp = !esp; notify(client, "ESP", esp); }
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);

            if (antiVelocity && client.player.hurtTime == 9 && !client.player.isDead()) {
                client.player.setVelocity(client.player.getVelocity().multiply(0.6, 1.0, 0.6));
            }
        });
    }

    private void renderESP(WorldRenderContext context) {
        if (!esp && !chestESP) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        MatrixStack ms = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ESP НА ИГРОКОВ
        if (esp) {
            for (PlayerEntity p : client.world.getPlayers()) {
                if (p == client.player || !p.isAlive()) continue;
                ms.push();
                double x = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevX, p.getX()) - camPos.x;
                double y = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevY, p.getY()) - camPos.y;
                double z = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevZ, p.getZ()) - camPos.z;
                ms.translate(x, y, z);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
                drawBox(buffer, ms.peek().getPositionMatrix(), p.getWidth() / 2 + 0.05f, p.getHeight() + 0.05f, 0.0f, 0.7f, 1.0f);
                ms.pop();
            }
        }

        // ESP НА СУНДУКИ
        if (chestESP) {
            int dist = client.options.getClampedViewDistance();
            int pCX = client.player.getChunkPos().x;
            int pCZ = client.player.getChunkPos().z;

            for (int x = pCX - dist; x <= pCX + dist; x++) {
                for (int z = pCZ - dist; z <= pCZ + dist; z++) {
                    WorldChunk chunk = client.world.getChunk(x, z);
                    if (chunk != null) {
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                                ms.push();
                                double bx = be.getPos().getX() - camPos.x;
                                double by = be.getPos().getY() - camPos.y;
                                double bz = be.getPos().getZ() - camPos.z;
                                ms.translate(bx, by, bz);

                                float r = 1.0f, g = 0.8f, b = 0.0f;
                                if (be instanceof EnderChestBlockEntity) { r = 0.2f; g = 0.8f; b = 0.8f; }
                                if (be instanceof ShulkerBoxBlockEntity) { r = 0.8f; g = 0.2f; b = 1.0f; }

                                drawBox(buffer, ms.peek().getPositionMatrix(), 0.51f, 1.01f, r, g, b);
                                ms.pop();
                            }
                        }
                    }
                }
            }
        }

        consumers.draw(RenderLayer.getLines());
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private void drawBox(VertexConsumer b, Matrix4f m, float w, float h, float r, float g, float bl) {
        line(b, m, -w, 0, -w, w, 0, -w, r, g, bl);
        line(b, m, w, 0, -w, w, 0, w, r, g, bl);
        line(b, m, w, 0, w, -w, 0, w, r, g, bl);
        line(b, m, -w, 0, w, -w, 0, -w, r, g, bl);
        line(b, m, -w, h, -w, w, h, -w, r, g, bl);
        line(b, m, w, h, -w, w, h, w, r, g, bl);
        line(b, m, w, h, w, -w, h, w, r, g, bl);
        line(b, m, -w, h, w, -w, h, -w, r, g, bl);
        line(b, m, -w, 0, -w, -w, h, -w, r, g, bl);
        line(b, m, w, 0, -w, w, h, -w, r, g, bl);
        line(b, m, w, 0, w, w, h, w, r, g, bl);
        line(b, m, -w, 0, w, -w, h, w, r, g, bl);
    }

    private void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, 1.0f).normal(0, 1, 0);
        b.vertex(m, x2, y2, z2).color(r, g, bl, 1.0f).normal(0, 1, 0);
    }

    private void runAura(MinecraftClient c) {
        PlayerEntity target = null;
        double dist = Double.MAX_VALUE;
        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || friendsList.contains(p.getName().getString().toLowerCase())) continue;
            double d = c.player.distanceTo(p);
            if (d < kaRange && (c.player.canSee(p) || d <= kawallsRange)) {
                if (d < dist) { dist = d; target = p; }
            }
        }
        if (target != null) {
            Vec3d diff = target.getPos().add(0, target.getHeight() * 0.7, 0).subtract(c.player.getEyePos());
            c.player.setYaw((float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f);
            c.player.setPitch((float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z))));
            if (c.player.getAttackCooldownProgress(0) >= 0.95f) {
                c.interactionManager.attackEntity(c.player, target);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTrigger(MinecraftClient c) {
        if (c.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p) {
            if (!friendsList.contains(p.getName().getString().toLowerCase()) && c.player.getAttackCooldownProgress(0) >= 0.95f) {
                c.interactionManager.attackEntity(c.player, p);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void throwPearl(MinecraftClient client) {
        int ps = -1;
        for (int i = 0; i < 36; i++) if (client.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { ps = i; break; }
        if (ps != -1) {
            int old = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = ps < 9 ? ps : old;
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.getInventory().selectedSlot = old;
        }
    }

    private void swapElytra(MinecraftClient client) {
        int slot = -1;
        boolean wear = client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (wear ? s.isOf(Items.NETHERITE_CHESTPLATE) : s.isOf(Items.ELYTRA)) { slot = i; break; }
        }
        if (slot != -1) client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot < 9 ? slot + 36 : slot, 0, SlotActionType.PICKUP, client.player);
    }

    private void checkTotem(MinecraftClient c) {
        if (!c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 45; i++) {
                if (c.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, c.player);
                    break;
                }
            }
        }
    }

    private void notify(MinecraftClient c, String n, boolean s) {
        c.player.sendMessage(Text.literal("§b[Bubble] §f" + n + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k == -1) return false;
        boolean p = InputUtil.isKeyPressed(h, k);
        if (p && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!p) keyStates[k] = false;
        return false;
    }

    public static void updateFriends(String r) {
        friendsRaw = r; friendsList.clear();
        if (r != null && !r.isEmpty()) Arrays.stream(r.split(",")).forEach(s -> friendsList.add(s.trim().toLowerCase()));
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kawallsRange + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + friendsRaw + ":" + esp + ":" + keyESP + ":" + keyFP + ":" + keyES + ":" + antiVelocity + ":" + chestESP);
        } catch (Exception ignored) {}
    }

    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":", -1);
            if (p.length >= 14) {
                kaRange = Double.parseDouble(p[0]); kawallsRange = Double.parseDouble(p[1]);
                autoRun = Boolean.parseBoolean(p[2]); keyKA = Integer.parseInt(p[3]);
                keyTB = Integer.parseInt(p[4]); keyFB = Integer.parseInt(p[5]);
                keyAT = Integer.parseInt(p[6]); friendsRaw = p[7];
                esp = Boolean.parseBoolean(p[8]); keyESP = Integer.parseInt(p[9]);
                keyFP = Integer.parseInt(p[10]); keyES = Integer.parseInt(p[11]);
                antiVelocity = Boolean.parseBoolean(p[12]); chestESP = Boolean.parseBoolean(p[13]);
                updateFriends(friendsRaw);
            }
        } catch (Exception ignored) {}
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 95, cy - 110, cx + 95, cy + 130, 0xD0101010);
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE 1.21.4", cx, cy - 100, 0x00CCFF);
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "ChestESP", "FastPearl", "ElytraSwap"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, esp, chestESP, fastPearl, elytraSwap};
            for (int i = 0; i < names.length; i++) {
                int iy = cy - 80 + i * 25;
                ctx.fill(cx - 85, iy, cx + 85, iy + 22, (mx >= cx - 85 && mx <= cx + 85 && my >= iy && my <= iy + 22) ? 0xEE404040 : 0xEE202020);
                ctx.drawText(textRenderer, names[i], cx - 80, iy + 7, states[i] ? 0x00FF00 : 0xFFFFFF, true);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 8; i++) {
                int iy = cy - 80 + i * 25;
                if (mx >= cx - 85 && mx <= cx + 85 && my >= iy && my <= iy + 22) {
                    if (b == 0) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        else if (i == 1) triggerbot = !triggerbot;
                        else if (i == 2) fullbright = !fullbright;
                        else if (i == 3) autoTotem = !autoTotem;
                        else if (i == 4) esp = !esp;
                        else if (i == 5) chestESP = !chestESP;
                        else if (i == 6) fastPearl = !fastPearl;
                        else if (i == 7) elytraSwap = !elytraSwap;
                        saveConfig(); return true;
                    }
                    if (b == 1) { client.setScreen(new BindScreen(this, i)); return true; }
                }
            }
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent; private TextFieldWidget rF, wF, fF;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA")); this.parent = parent; }
        @Override
        protected void init() {
            int cx = width / 2, cy = height / 2;
            rF = new TextFieldWidget(textRenderer, cx - 40, cy - 75, 40, 14, Text.literal("")); rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, cx - 40, cy - 55, 40, 14, Text.literal("")); wF.setText(String.valueOf(kawallsRange));
            fF = new TextFieldWidget(textRenderer, cx + 15, cy - 75, 100, 14, Text.literal("Friends")); fF.setText(friendsRaw);
            addDrawableChild(rF); addDrawableChild(wF); addDrawableChild(fF);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 120, cy - 90, cx + 130, cy + 65, 0xD0101010);
            ctx.drawText(textRenderer, "Range:", cx - 115, cy - 72, -1, true);
            ctx.drawText(textRenderer, "Walls:", cx - 115, cy - 52, -1, true);
            drawBtn(ctx, cx - 115, cy - 20, "AresMine", mx, my);
            drawBtn(ctx, cx - 30, cy - 20, "MainBlaze", mx, my);
            drawCheck(ctx, cx - 95, cy + 10, "AutoRun", autoRun, mx, my);
            drawCheck(ctx, cx + 10, cy + 10, "AntiVel", antiVelocity, mx, my);
        }

        private void drawBtn(DrawContext ctx, int x, int y, String n, int mx, int my) {
            ctx.fill(x, y, x + 75, y + 15, (mx >= x && mx <= x + 75 && my >= y && my <= y + 15) ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x + 37, y + 4, -1);
        }

        private void drawCheck(DrawContext ctx, int x, int y, String n, boolean s, int mx, int my) {
            ctx.fill(x, y, x + 85, y + 15, (mx >= x && mx <= x + 85 && my >= y && my <= y + 15) ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x + 42, y + 4, s ? 0x00FF00 : 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            if (mx >= cx - 115 && mx <= cx - 40 && my >= cy - 20 && my <= cy - 5) { rF.setText("3.8"); wF.setText("3.1"); }
            if (mx >= cx - 30 && mx <= cx + 45 && my >= cy - 20 && my <= cy - 5) { rF.setText("4.0"); wF.setText("3.3"); }
            if (mx >= cx - 95 && mx <= cx - 10 && my >= cy + 10 && my <= cy + 25) { autoRun = !autoRun; return true; }
            if (mx >= cx + 10 && mx <= cx + 95 && my >= cy + 10 && my <= cy + 25) { antiVelocity = !antiVelocity; return true; }
            return super.mouseClicked(mx, my, b);
        }

        @Override
        public void close() {
            try { kaRange = Double.parseDouble(rF.getText()); kawallsRange = Double.parseDouble(wF.getText()); } catch (Exception e) {}
            updateFriends(fF.getText()); saveConfig(); client.setScreen(parent);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen parent, int id) { super(Text.literal("Bind")); this.parent = parent; this.id = id; }
        @Override
        public boolean keyPressed(int k, int s, int n) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if (id == 0) keyKA = v; else if (id == 1) keyTB = v; else if (id == 2) keyFB = v;
            else if (id == 3) keyAT = v; else if (id == 4) keyESP = v;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            ctx.drawCenteredTextWithShadow(textRenderer, "НАЖМИ КЛАВИШУ (ESC - СБРОС)", width / 2, height / 2, 0x00CCFF);
        }
    }
}

