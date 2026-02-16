package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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

public class ExampleMod implements ModInitializer {
    // Настройки
    public static boolean killaura = false, triggerbot = false, fullbright = true, esp = true;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, elytraSwap = true, fastPearl = true, smartCrits = false;
    
    // Дистанции и ротации
    public static double kaRange = 3.4, kawallsRange = 3.0;
    public static float serverYaw, serverPitch;
    public static boolean targetFound = false;
    public static PlayerEntity currentTarget = null;

    // Бинды
    public static int keyKA = -1, keyTR = -1, keyFB = -1, keyAT = -1, keyESP = -1;
    public static int keyFP = GLFW.GLFW_KEY_Q, keyES = GLFW.GLFW_KEY_C;

    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();
    public static final boolean[] keyStates = new boolean[512];
    public static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderESP);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long win = client.getWindow().getHandle();

            if (isPressed(win, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            handleBinds(client, win);

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);

            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(client.player.getVelocity().multiply(0.6, 1.0, 0.6));
            }
        });
    }

    public void handleBinds(MinecraftClient client, long win) {
        if (client.currentScreen != null) return;
        if (fastPearl && isPressed(win, keyFP)) throwPearl(client);
        if (elytraSwap && isPressed(win, keyES)) swapElytra(client);
        
        if (isPressed(win, keyKA)) { killaura = !killaura; notify(client, "KillAura", killaura); }
        if (isPressed(win, keyTR)) { triggerbot = !triggerbot; notify(client, "TriggerBot", triggerbot); }
        if (isPressed(win, keyFB)) { fullbright = !fullbright; notify(client, "FullBright", fullbright); }
        if (isPressed(win, keyAT)) { autoTotem = !autoTotem; notify(client, "AutoTotem", autoTotem); }
        if (isPressed(win, keyESP)) { esp = !esp; notify(client, "ESP", esp); }
    }

    public void runAura(MinecraftClient client) {
        currentTarget = null;
        double dist = kaRange;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || friendsList.contains(p.getName().getString().toLowerCase())) continue;
            double d = client.player.distanceTo(p);
            
            if (client.player.canSee(p)) {
                if (d > kaRange) continue;
            } else {
                if (d > kawallsRange) continue;
            }
            if (d < dist) { dist = d; currentTarget = p; }
        }

        if (currentTarget != null) {
            targetFound = true;
            double randomHeight = 0.2 + (Math.random() * 0.5);
            Vec3d tPos = currentTarget.getPos().add(0, currentTarget.getHeight() * randomHeight, 0);
            Vec3d diff = tPos.subtract(client.player.getEyePos());

            float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
            float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));

            serverYaw = tYaw + (float)((Math.random() - 0.5) * 0.4);
            serverPitch = tPitch + (float)((Math.random() - 0.5) * 0.4);

            if (client.player.getAttackCooldownProgress(0.0f) >= 0.95f) {
                boolean isFalling = client.player.fallDistance > 0 && !client.player.isOnGround();
                
                // ПАКЕТ ДЛЯ 1.21.4 (4 аргумента)
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    serverYaw, serverPitch, client.player.isOnGround(), client.player.horizontalCollision
                ));
                
                if (!smartCrits || isFalling) {
                    client.interactionManager.attackEntity(client.player, currentTarget);
                    client.player.swingHand(Hand.MAIN_HAND);
                    if (autoRun) client.player.setSprinting(true);
                }
            }
        } else {
            targetFound = false;
        }
    }

    public void runTrigger(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p) {
            if (p.isAlive() && !friendsList.contains(p.getName().getString().toLowerCase()) && client.player.getAttackCooldownProgress(0.0f) >= 0.98f) {
                client.interactionManager.attackEntity(client.player, p);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public void swapElytra(MinecraftClient client) {
        int slot = -1;
        ItemStack chest = client.player.getInventory().getArmorStack(2);
        boolean isElytra = chest.isOf(Items.ELYTRA);
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (isElytra) { if (s.getItem().toString().contains("chestplate")) { slot = i; break; } }
            else if (s.isOf(Items.ELYTRA)) { slot = i; break; }
        }
        if (slot != -1) {
            int invS = slot < 9 ? slot + 36 : slot;
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, invS, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, invS, 0, SlotActionType.PICKUP, client.player);
        }
    }

    public void throwPearl(MinecraftClient client) {
        int ps = -1;
        for (int i = 0; i < 9; i++) if (client.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { ps = i; break; }
        if (ps != -1) {
            int old = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = ps;
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.getInventory().selectedSlot = old;
        }
    }

    public void checkTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        for (int i = 0; i < 45; i++) {
            if (client.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                int s = (i < 9) ? (i + 36) : i;
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, s, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, s, 0, SlotActionType.PICKUP, client.player);
                break;
            }
        }
    }

    public void renderESP(WorldRenderContext context) {
        if (!esp) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        MatrixStack ms = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive()) continue;
            ms.push();
            double x = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevX, p.getX()) - camPos.x;
            double y = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevY, p.getY()) - camPos.y;
            double z = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevZ, p.getZ()) - camPos.z;
            ms.translate(x, y, z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.getYaw()));
            boolean isTarget = targetFound && (p == currentTarget);
            drawBox(buffer, ms.peek().getPositionMatrix(), p.getWidth()/2 + 0.05f, p.getHeight() + 0.05f, isTarget ? 1f : 0f, isTarget ? 0f : 1f, isTarget ? 0f : 1f, 1f);
            ms.pop();
        }
        consumers.draw(RenderLayer.getLines());
        RenderSystem.enableDepthTest();
    }

    public void drawBox(VertexConsumer b, Matrix4f m, float w, float h, float r, float g, float bl, float a) {
        line(b, m, -w, 0, -w, w, 0, -w, r, g, bl, a); line(b, m, w, 0, -w, w, 0, w, r, g, bl, a);
        line(b, m, w, 0, w, -w, 0, w, r, g, bl, a); line(b, m, -w, 0, w, -w, 0, -w, r, g, bl, a);
        line(b, m, -w, h, -w, w, h, -w, r, g, bl, a); line(b, m, w, h, -w, w, h, w, r, g, bl, a);
        line(b, m, w, h, w, -w, h, w, r, g, bl, a); line(b, m, -w, h, w, -w, h, -w, r, g, bl, a);
        line(b, m, -w, 0, -w, -w, h, -w, r, g, bl, a); line(b, m, w, 0, -w, w, h, -w, r, g, bl, a);
        line(b, m, w, 0, w, w, h, w, r, g, bl, a); line(b, m, -w, 0, w, -w, h, w, r, g, bl, a);
    }

    public void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, a).normal(0, 1, 0);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a).normal(0, 1, 0);
    }

    public void notify(MinecraftClient c, String n, boolean v) {
        if (c.player != null) c.player.sendMessage(Text.literal("§b[Bubble] §f" + n + ": " + (v ? "§aON" : "§cOFF")), true);
    }

    public boolean isPressed(long h, int k) {
        if (k == -1) return false;
        boolean p = InputUtil.isKeyPressed(h, k);
        if (p && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!p) keyStates[k] = false;
        return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kawallsRange + ":" + autoRun + ":" + keyKA + ":" + keyTR + ":" + keyFB + ":" + keyAT + ":" + keyESP + ":" + keyFP + ":" + keyES + ":" + antiVelocity + ":" + fullbright + ":" + smartCrits + ":" + friendsRaw);
        } catch (Exception ignored) {}
    }

    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":", -1);
            if (p.length >= 14) {
                kaRange = Double.parseDouble(p[0]); kawallsRange = Double.parseDouble(p[1]);
                autoRun = Boolean.parseBoolean(p[2]); keyKA = Integer.parseInt(p[3]);
                keyTR = Integer.parseInt(p[4]); keyFB = Integer.parseInt(p[5]);
                keyAT = Integer.parseInt(p[6]); keyESP = Integer.parseInt(p[7]);
                keyFP = Integer.parseInt(p[8]); keyES = Integer.parseInt(p[9]);
                antiVelocity = Boolean.parseBoolean(p[10]); fullbright = Boolean.parseBoolean(p[11]);
                smartCrits = Boolean.parseBoolean(p[12]); friendsRaw = p[13];
                friendsList.clear();
                if (!friendsRaw.isEmpty()) Arrays.stream(friendsRaw.split(",")).map(String::trim).map(String::toLowerCase).forEach(friendsList::add);
            }
        } catch (Exception ignored) {}
    }

    // ВНУТРЕННИЕ КЛАССЫ ЭКРАНОВ
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 95, cy - 100, cx + 95, cy + 110, 0xDD050505);
            ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "BUBBLE ABSOLUTE", cx, cy - 90, 0x55FFFF);
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "FastPearl", "ElytraSwap"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, esp, fastPearl, elytraSwap};
            for (int i = 0; i < names.length; i++) {
                int iy = cy - 70 + i * 24;
                boolean h = mx >= cx - 85 && mx <= cx + 85 && my >= iy && my <= iy + 20;
                ctx.fill(cx - 85, iy, cx + 85, iy + 20, h ? 0xEE303030 : 0xEE151515);
                ctx.drawText(MinecraftClient.getInstance().textRenderer, names[i], cx - 80, iy + 6, states[i] ? 0x00FF00 : 0xFFFFFF, true);
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
        private final Screen parent;
        private TextFieldWidget rF, wF, fF;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA")); this.parent = parent; }
        protected void init() {
            int cx = width / 2, cy = height / 2;
            rF = new TextFieldWidget(textRenderer, cx - 40, cy - 75, 40, 14, Text.literal(""));
            rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, cx - 40, cy - 55, 40, 14, Text.literal(""));
            wF.setText(String.valueOf(kawallsRange));
            fF = new TextFieldWidget(textRenderer, cx - 60, cy - 35, 120, 14, Text.literal("Friends"));
            fF.setText(friendsRaw);
            addDrawableChild(rF); addDrawableChild(wF); addDrawableChild(fF);
        }
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 120, cy - 90, cx + 120, cy + 90, 0xEE050505);
            ctx.drawText(textRenderer, "Range:", cx - 110, cy - 72, -1, true);
            ctx.drawText(textRenderer, "Walls:", cx - 110, cy - 52, -1, true);
            drawCheck(ctx, cx - 110, cy + 20, "Smart Crits", smartCrits, mx, my);
            drawCheck(ctx, cx - 110, cy + 40, "AntiVelocity", antiVelocity, mx, my);
            drawCheck(ctx, cx - 110, cy + 60, "AutoRun", autoRun, mx, my);
        }
        public void drawCheck(DrawContext ctx, int x, int y, String n, boolean s, int mx, int my) {
            boolean h = mx >= x && mx <= x + 220 && my >= y && my <= y + 15;
            ctx.fill(x, y, x + 220, y + 15, h ? 0x40404040 : 0x20202020);
            ctx.drawText(textRenderer, n + ": " + (s ? "§aON" : "§cOFF"), x + 5, y + 4, 0xFFFFFF, true);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            if (mx >= cx - 110 && mx <= cx + 110 && my >= cy + 20 && my <= cy + 35) smartCrits = !smartCrits;
            if (mx >= cx - 110 && mx <= cx + 110 && my >= cy + 40 && my <= cy + 55) antiVelocity = !antiVelocity;
            if (mx >= cx - 110 && mx <= cx + 110 && my >= cy + 60 && my <= cy + 75) autoRun = !autoRun;
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public void close() {
            try { kaRange = Double.parseDouble(rF.getText()); kawallsRange = Double.parseDouble(wF.getText()); } catch (Exception ignored) {}
            friendsRaw = fF.getText(); friendsList.clear();
            if (!friendsRaw.isEmpty()) Arrays.stream(friendsRaw.split(",")).map(String::trim).map(String::toLowerCase).forEach(friendsList::add);
            saveConfig(); client.setScreen(parent);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen parent, int id) { super(Text.literal("Bind")); this.parent = parent; this.id = id; }
        @Override
        public boolean keyPressed(int k, int s, int n) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if (id == 0) keyKA = v; else if (id == 1) keyTR = v; else if (id == 2) keyFB = v;
            else if (id == 3) keyAT = v; else if (id == 4) keyESP = v; else if (id == 5) keyFP = v; else if (id == 6) keyES = v;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            ctx.drawCenteredTextWithShadow(textRenderer, "НАЖМИ КЛАВИШУ", width / 2, height / 2, 0x00CCFF);
        }
    }
}
