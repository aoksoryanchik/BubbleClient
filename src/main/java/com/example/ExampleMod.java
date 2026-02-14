package com.example;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, esp = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, elytraSwap = true, fastPearl = true;
    public static double kaRange = 3.8D, kaWallsRange = 3.0D;
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyESP = -1, keyFP = GLFW.GLFW_KEY_G, keyES = GLFW.GLFW_KEY_C;
    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        WorldRenderEvents.LAST.register(this::onWorldRender);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long win = client.getWindow().getHandle();
            if (isPressed(win, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());
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
            if (antiVelocity && client.player.hurtTime > 0) client.player.setVelocity(0, client.player.getVelocity().y, 0);
        });
    }

    private void onWorldRender(WorldRenderContext context) {
        if (!esp) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        MatrixStack ms = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickDelta(true);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // ИСПРАВЛЕНО: В 1.21.4 шейдеры берутся через CoreShaders
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isInvisible()) continue;
            double x = MathHelper.lerp(tickDelta, p.prevX, p.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, p.prevY, p.getY()) - camPos.y;
            double z = MathHelper.lerp(tickDelta, p.prevZ, p.getZ()) - camPos.z;

            ms.push();
            ms.translate(x, y, z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            Matrix4f mat = ms.peek().getPositionMatrix();
            float w = p.getWidth() / 2f + 0.05f;
            float h = p.getHeight() + 0.05f;

            Tessellator tessellator = Tessellator.getInstance();
            // ИСПРАВЛЕНО: В 1.21.4 метод begin требует DrawMode и VertexFormat
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            int r = 0, g = 204, b = 255, a = 255;
            buffer.vertex(mat, -w, 0, 0).color(r, g, b, a);
            buffer.vertex(mat, w, 0, 0).color(r, g, b, a);
            buffer.vertex(mat, w, h, 0).color(r, g, b, a);
            buffer.vertex(mat, -w, h, 0).color(r, g, b, a);
            buffer.vertex(mat, -w, 0, 0).color(r, g, b, a);

            // ИСПРАВЛЕНО: Рендеринг через drawWithGlobalProgram
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            ms.pop();
        }
        RenderSystem.enableDepthTest();
    }

    private void runAura(MinecraftClient c) {
        PlayerEntity target = null; double dist = Double.MAX_VALUE;
        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || p.isSpectator() || p.isInvisible()) continue;
            if (friendsList.contains(p.getName().getString().toLowerCase())) continue;
            double d = c.player.distanceTo(p);
            if (d <= kaRange && (c.player.canSee(p) || d <= kaWallsRange)) {
                if (d < dist) { dist = d; target = p; }
            }
        }
        if (target != null) {
            if (autoRun) c.player.setSprinting(true);
            Vec3d diff = target.getPos().add(0, target.getHeight() * 0.7, 0).subtract(c.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
            float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
            c.player.setYaw(lerpAngle(c.player.getYaw(), yaw, 0.70f));
            c.player.setPitch(lerpAngle(c.player.getPitch(), pitch, 0.70f));
            if (c.player.getAttackCooldownProgress(0) >= (0.92f + random.nextFloat() * 0.04f)) {
                c.interactionManager.attackEntity(c.player, target);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private float lerpAngle(float start, float end, float speed) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + diff * speed;
    }

    private void checkTotem(MinecraftClient c) {
        if (!c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 45; i++) {
                if (c.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int slot = (i < 9) ? (i + 36) : i;
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, c.player);
                    break;
                }
            }
        }
    }

    private void swapElytra(MinecraftClient client) {
        int slot = -1;
        boolean wearingElytra = client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (wearingElytra) {
                // ИСПРАВЛЕНО: Проверка на нагрудник в 1.21.4 через тип брони
                if (stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == EquipmentSlot.CHEST) {
                    slot = i; break;
                }
            } else if (stack.isOf(Items.ELYTRA)) {
                slot = i; break;
            }
        }
        if (slot != -1) {
            int sid = client.player.playerScreenHandler.syncId;
            int invSlot = slot < 9 ? slot + 36 : slot;
            client.interactionManager.clickSlot(sid, invSlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(sid, 6, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(sid, invSlot, 0, SlotActionType.PICKUP, client.player);
        }
    }

    private void throwPearl(MinecraftClient client) {
        int pearlSlot = -1;
        for (int i = 0; i < 9; i++) if (client.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { pearlSlot = i; break; }
        if (pearlSlot != -1) {
            int old = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = pearlSlot;
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.getInventory().selectedSlot = old;
        }
    }

    private void runTrigger(MinecraftClient c) {
        HitResult hit = c.crosshairTarget;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity p) {
            if (p.isAlive() && !friendsList.contains(p.getName().getString().toLowerCase())) {
                if (c.player.getAttackCooldownProgress(0) >= 1.0F) {
                    c.interactionManager.attackEntity(c.player, p);
                    c.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private void notify(MinecraftClient c, String mod, boolean state) {
        c.player.sendMessage(Text.literal("§b[Bubble] §f" + mod + ": " + (state ? "§aВКЛ" : "§cВЫКЛ")), true);
    }

    private boolean isPressed(long handle, int key) {
        if (key == -1) return false;
        boolean pressed = InputUtil.isKeyPressed(handle, key);
        if (pressed && !keyStates[key]) { keyStates[key] = true; return true; }
        if (!pressed) keyStates[key] = false;
        return false;
    }

    public static void updateFriends(String raw) {
        friendsRaw = raw; friendsList.clear();
        if (raw != null && !raw.isEmpty()) Arrays.stream(raw.split(",")).map(String::trim).map(String::toLowerCase).forEach(friendsList::add);
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + friendsRaw + ":" + esp + ":" + keyESP + ":" + keyFP + ":" + keyES + ":" + antiVelocity);
        } catch (Exception ignored) {}
    }

    public void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":", -1);
            if (p.length >= 13) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                autoRun = Boolean.parseBoolean(p[2]); keyKA = Integer.parseInt(p[3]);
                keyTB = Integer.parseInt(p[4]); keyFB = Integer.parseInt(p[5]);
                keyAT = Integer.parseInt(p[6]); updateFriends(p[7]);
                esp = Boolean.parseBoolean(p[8]); keyESP = Integer.parseInt(p[9]);
                keyFP = Integer.parseInt(p[10]); keyES = Integer.parseInt(p[11]);
                antiVelocity = Boolean.parseBoolean(p[12]);
            }
        } catch (Exception ignored) {}
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 95, cy - 105, cx + 95, cy + 120, 0xDD101010);
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE 1.21.4", cx, cy - 95, 0x00CCFF);
            String[] names = { "KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "FastPearl", "ElytraSwap" };
            boolean[] states = { killaura, triggerbot, fullbright, autoTotem, esp, fastPearl, elytraSwap };
            for (int i = 0; i < names.length; i++) {
                int iy = cy - 70 + i * 24;
                boolean h = mx >= cx - 85 && mx <= cx + 85 && my >= iy && my <= iy + 20;
                ctx.fill(cx - 85, iy, cx + 85, iy + 20, h ? 0xEE404040 : 0xEE202020);
                ctx.drawText(textRenderer, names[i], cx - 80, iy + 6, states[i] ? 0x00FF00 : 0xFFFFFF, true);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 7; i++) {
                int iy = cy - 70 + i * 24;
                if (mx >= cx - 85 && mx <= cx + 85 && my >= iy && my <= iy + 20) {
                    if (b == 1) { client.setScreen(new BindScreen(this, i)); return true; }
                    if (b == 0) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        else if (i == 1) triggerbot = !triggerbot;
                        else if (i == 2) fullbright = !fullbright;
                        else if (i == 3) autoTotem = !autoTotem;
                        else if (i == 4) esp = !esp;
                        else if (i == 5) fastPearl = !fastPearl;
                        else if (i == 6) elytraSwap = !elytraSwap;
                        saveConfig(); return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent; private TextFieldWidget rF, wF, fF;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA")); this.parent = parent; }
        @Override protected void init() {
            int x = width/2, y = height/2;
            rF = new TextFieldWidget(textRenderer, x - 40, y - 75, 40, 14, Text.literal("")); rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, x - 40, y - 55, 40, 14, Text.literal("")); wF.setText(String.valueOf(kaWallsRange));
            fF = new TextFieldWidget(textRenderer, x + 15, y - 75, 100, 14, Text.literal("Friends")); fF.setText(friendsRaw);
            addDrawableChild(rF); addDrawableChild(wF); addDrawableChild(fF);
        }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int x = width/2, y = height/2;
            ctx.fill(x - 120, y - 90, x + 130, y + 65, 0xDD101010);
            ctx.drawText(textRenderer, "Range:", x - 115, y - 72, -1, true);
            ctx.drawText(textRenderer, "Walls:", x - 115, y - 52, -1, true);
            drawBtn(ctx, x - 115, y - 20, "AresMine", mx, my);
            drawBtn(ctx, x - 30, y - 20, "MainBlaze", mx, my);
            drawCheck(ctx, x - 115, y + 10, "AutoRun", autoRun, mx, my);
            drawCheck(ctx, x - 30, y + 10, "AntiVelocity", antiVelocity, mx, my);
        }
        private void drawBtn(DrawContext ctx, int x, int y, String name, int mx, int my) {
            boolean h = mx >= x && mx <= x + 75 && my >= y && my <= y + 15;
            ctx.fill(x, y, x + 75, y + 15, h ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, name, x + 37, y + 4, -1);
        }
        private void drawCheck(DrawContext ctx, int x, int y, String name, boolean state, int mx, int my) {
            boolean h = mx >= x && mx <= x + 85 && my >= y && my <= y + 15;
            ctx.fill(x, y, x + 85, y + 15, h ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, name, x + 42, y + 4, state ? 0x00FF00 : 0xFFFFFF);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 115 && mx <= x - 40 && my >= y - 20 && my <= y - 5) { rF.setText("3.8"); wF.setText("3.1"); return true; }
            if (mx >= x - 30 && mx <= x + 45 && my >= y - 20 && my <= y - 5) { rF.setText("4.0"); wF.setText("3.3"); return true; }
            if (mx >= x - 115 && mx <= x - 30 && my >= y + 10 && my <= y + 25) { autoRun = !autoRun; return true; }
            if (mx >= x - 30 && mx <= x + 55 && my >= y + 10 && my <= y + 25) { antiVelocity = !antiVelocity; return true; }
            return super.mouseClicked(mx, my, b);
        }
        @Override public void close() { try { kaRange = Double.parseDouble(rF.getText()); kaWallsRange = Double.parseDouble(wF.getText()); } catch (Exception ignored) {} updateFriends(fF.getText()); saveConfig(); client.setScreen(parent); }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen parent, int id) { super(Text.literal("Bind")); this.parent = parent; this.id = id; }
        @Override public boolean keyPressed(int k, int s, int m) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if (id == 0) keyKA = v; else if (id == 1) keyTB = v; else if (id == 2) keyFB = v; else if (id == 3) keyAT = v; else if (id == 4) keyESP = v; else if (id == 5) keyFP = v; else if (id == 6) keyES = v;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override public void render(DrawContext ctx, int mx, int my, float d) { super.render(ctx, mx, my, d); ctx.drawCenteredTextWithShadow(textRenderer, "НАЖМИ КЛАВИШУ", width/2, height/2, 0x00CCFF); }
    }
}

