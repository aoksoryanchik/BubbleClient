package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ExampleMod implements ModInitializer {

    // --- МОДУЛИ ---
    public static boolean killaura = false, triggerbot = false, autoTotem = true, fullbright = false, waypointActive = false;
    public static boolean autoRun = true, antiVelocity = true, antiInvisible = true;

    // --- НАСТРОЙКИ ---
    public static double kaRange = 3.2, kaWallsRange = 3.0;
    public static float shakeIntensity = 0.5f;
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN;
    
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    
    // Переменная для хранения текущего рандомного порога КД
    private float currentCooldownThreshold = 0.93f;

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            long h = client.getWindow().getHandle();
            
            // Меню и бинды
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());
            if (client.currentScreen == null && isPressed(h, keyKA)) {
                killaura = !killaura;
                sendNotify("KillAura", killaura);
            }

            // 1. FULLBRIGHT (Максимум)
            if (fullbright) {
                client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }

            // 2. AUTO-TOTEM (Мгновенный Swap)
            if (autoTotem && client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                for (int i = 0; i < 45; i++) {
                    if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, client.player);
                        break;
                    }
                }
            }

            // 3. AUTO-RUN
            if (autoRun && client.player.input.movementForward > 0 && !client.player.isSneaking() && !client.player.isHorizontalCollision) {
                client.player.setSprinting(true);
            }

            // 4. ANTI-VELOCITY (Исправлено: Полная блокировка отдачи)
            if (antiVelocity && client.player.velocityModified) {
                Vec3d currentVel = client.player.getVelocity();
                client.player.setVelocity(0, currentVel.y, 0);
                client.player.velocityModified = false;
            }

            // 5. KILL AURA (С рандомным КД 0.93-0.96)
            if (killaura) runKillAura(client);
        });
    }

    private void runKillAura(MinecraftClient client) {
        double bestDist = kaRange;
        PlayerEntity target = null;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isCreative()) continue;
            
            // ANTI-INVISIBLE (Включено в ядро)
            if (!antiInvisible && p.isInvisible()) continue;

            double d = client.player.distanceTo(p);
            if (d <= bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }

        if (target != null) {
            // Наводка с Shake
            float[] rots = getRotations(client.player, target);
            client.player.setYaw(rots[0]);
            client.player.setPitch(rots[1]);

            // РАНДОМНОЕ КД: Проверка порога от 0.93 до 0.96
            if (client.player.getAttackCooldownProgress(0) >= currentCooldownThreshold) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Генерируем новый порог для следующего удара
                currentCooldownThreshold = 0.93f + (ThreadLocalRandom.current().nextFloat() * (0.96f - 0.93f));
            }
        } else {
            // Если цели нет, сбрасываем порог на дефолт
            currentCooldownThreshold = 0.93f;
        }
    }

    private float[] getRotations(PlayerEntity self, PlayerEntity target) {
        double s = shakeIntensity * 0.13;
        Vec3d tPos = target.getPos().add(
                ThreadLocalRandom.current().nextDouble(-s, s),
                target.getHeight() * (0.35 + ThreadLocalRandom.current().nextDouble(0.1, 0.4)),
                ThreadLocalRandom.current().nextDouble(-s, s)
        );
        Vec3d diff = tPos.subtract(self.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        return new float[]{
                self.getYaw() + MathHelper.wrapDegrees(yaw - self.getYaw()),
                self.getPitch() + MathHelper.wrapDegrees(pitch - self.getPitch())
        };
    }

    // --- GUI СИСТЕМА ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x98000000);
            int x = width/2 - 100, y = height/2 - 80;
            ctx.fill(x, y, x + 200, y + 160, 0xFF0D0D0D);
            ctx.drawBorder(x, y, 200, 160, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bBUBBLE §fPREMIUM", width/2, y + 12, -1);
            
            String[] mods = {"KillAura", "TriggerBot", "AutoTotem", "FullBright", "Waypoint"};
            boolean[] st = {killaura, triggerbot, autoTotem, fullbright, waypointActive};
            
            for (int i = 0; i < mods.length; i++) {
                int iy = y + 40 + (i * 22);
                boolean h = mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 18;
                ctx.fill(x + 10, iy, x + 190, iy + 18, h ? 0xFF202020 : 0xFF141414);
                ctx.drawTextWithShadow(client.textRenderer, mods[i], x + 20, iy + 5, st[i] ? 0xFF00FFAA : -1);
                if (i == 0) ctx.drawTextWithShadow(client.textRenderer, "§7[SETTINGS]", x + 130, iy + 5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 100, y = height/2 - 80;
            for (int i = 0; i < 5; i++) {
                int iy = y + 40 + (i * 22);
                if (mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 18) {
                    if (i == 0 && b == 1) client.setScreen(new KillAuraSettings(this));
                    else if (i == 0) killaura = !killaura;
                    if (i == 1) triggerbot = !triggerbot;
                    if (i == 2) autoTotem = !autoTotem;
                    if (i == 3) fullbright = !fullbright;
                    if (i == 4) waypointActive = !waypointActive;
                    saveConfig(); return true;
                }
            }
            return false;
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rF;
        public KillAuraSettings(Screen parent) { super(Text.literal("Settings")); this.parent = parent; }
        @Override
        protected void init() {
            rF = new TextFieldWidget(client.textRenderer, width/2 + 20, height/2 - 50, 40, 16, Text.literal(""));
            rF.setText(String.valueOf(kaRange));
            addSelectableChild(rF);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xF8000000);
            int x = width/2, y = height/2;
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bKA §fCONFIG", x, y - 85, -1);
            ctx.drawTextWithShadow(client.textRenderer, "Range:", x - 70, y - 46, -1);
            rF.render(ctx, mx, my, d);
            drawO(ctx, "AutoRun", autoRun, y - 10, mx, my);
            drawO(ctx, "AntiVelocity", antiVelocity, y + 10, mx, my);
            drawO(ctx, "Anti-Invisible", antiInvisible, y + 30, mx, my);
            drawP(ctx, "Ares", x - 85, y + 60, mx, my);
            drawP(ctx, "Blaze", x - 20, y + 60, mx, my);
            drawP(ctx, "FunTime", x + 45, y + 60, mx, my);
        }
        private void drawO(DrawContext ctx, String n, boolean s, int y, int mx, int my) {
            boolean h = mx >= width/2 - 80 && mx <= width/2 + 80 && my >= y && my <= y + 12;
            ctx.drawCenteredTextWithShadow(client.textRenderer, n + ": " + (s ? "§aON" : "§cOFF"), width/2, y, h ? 0xFF00AAFF : -1);
        }
        private void drawP(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 50 && my >= y && my <= y + 14;
            ctx.drawCenteredTextWithShadow(client.textRenderer, n, x + 25, y, h ? 0xFF00AAFF : -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 80 && mx <= x + 80) {
                if (my >= y - 10 && my <= y + 2) autoRun = !autoRun;
                if (my >= y + 10 && my <= y + 22) antiVelocity = !antiVelocity;
                if (my >= y + 30 && my <= y + 42) antiInvisible = !antiInvisible;
            }
            if (my >= y + 60 && my <= y + 74) {
                if (mx >= x - 85 && mx <= x - 35) { kaRange = 3.8; rF.setText("3.8"); }
                if (mx >= x - 20 && mx <= x + 30) { kaRange = 3.1; rF.setText("3.1"); }
                if (mx >= x + 45 && mx <= x + 95) { kaRange = 3.02; rF.setText("3.02"); }
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int n) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange = Double.parseDouble(rF.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return rF.keyPressed(k, s, n) || super.keyPressed(k, s, n);
        }
    }

    // --- СЛУЖЕБНЫЕ ---
    private static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + autoRun + ":" + antiVelocity + ":" + antiInvisible);
        } catch (Exception ignored) {}
    }
    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            kaRange = Double.parseDouble(p[0]); autoRun = Boolean.parseBoolean(p[1]);
            antiVelocity = Boolean.parseBoolean(p[2]); antiInvisible = Boolean.parseBoolean(p[3]);
        } catch (Exception ignored) {}
    }
    private boolean isPressed(long h, int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false;
        return false;
    }
    private void sendNotify(String m, boolean s) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + m + ": " + (s ? "§aON" : "§cOFF")), true);
    }
}

