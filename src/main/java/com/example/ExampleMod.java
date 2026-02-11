package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
    
    // --- ПЕРЕМЕННЫЕ СОСТОЯНИЯ ---
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean autoTotem = true;
    public static boolean fullbright = false;
    public static boolean waypointActive = false;
    
    // Настройки внутри модулей
    public static boolean autoRun = true;
    public static boolean antiVelocity = true;
    public static boolean antiInvisible = true; // Твоя новая функция
    public static boolean smartRotations = true;
    public static boolean rayTrace = true;

    // Параметры KillAura
    public static double kaRange = 3.2;
    public static double kaWallsRange = 3.0;
    public static float rotationSpeed = 180.0f;
    public static float shakeIntensity = 0.5f;
    
    // Бинды и конфиг
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyTB = GLFW.GLFW_KEY_UNKNOWN;
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    
    // Служебные объекты
    private final Random random = new Random();
    public static PlayerEntity currentTarget = null;
    private static float lastYaw, lastPitch;

    @Override
    public void onInitialize() {
        loadConfig();
        
        // Главный цикл тиков
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            handleInput(client);
            
            if (fullbright) {
                client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }
            
            if (autoTotem) handleAutoTotem(client);
            
            if (autoRun && client.player.input.movementForward > 0 && !client.player.isHorizontalCollision) {
                client.player.setSprinting(true);
            }
            
            if (killaura) {
                runKillAura(client);
            } else {
                currentTarget = null;
            }
        });
    }

    // --- ЛОГИКА KILL AURA (ПРОДВИНУТАЯ) ---
    private void runKillAura(MinecraftClient client) {
        currentTarget = findBestTarget(client);
        
        if (currentTarget != null) {
            // Математика ротаций с учетом Shake и Smooth
            float[] rotations = calculateRotations(client.player, currentTarget);
            
            // Применяем вращение
            client.player.setYaw(rotations[0]);
            client.player.setPitch(rotations[1]);

            // Удар по КД (Attack Speed 1.21.4)
            if (client.player.getAttackCooldownProgress(0) >= 0.93f) {
                client.interactionManager.attackEntity(client.player, currentTarget);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private PlayerEntity findBestTarget(MinecraftClient client) {
        PlayerEntity best = null;
        double dist = Double.MAX_VALUE;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isCreative()) continue;
            
            // РЕАЛИЗАЦИЯ ANTI-INVISIBLE
            if (!antiInvisible && p.isInvisible()) continue;

            double d = client.player.distanceTo(p);
            if (d <= kaRange) {
                // RayTrace Check (Walls)
                if (rayTrace && !client.player.canSee(p) && d > kaWallsRange) continue;
                
                if (d < dist) {
                    dist = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private float[] calculateRotations(PlayerEntity self, Entity target) {
        Vec3d eyePos = self.getEyePos();
        // Рандомизация точки удара (Shake) для обхода античита
        double s = shakeIntensity * 0.1;
        Vec3d targetVec = target.getPos().add(
            ThreadLocalRandom.current().nextDouble(-s, s),
            target.getHeight() * (0.4 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1)),
            ThreadLocalRandom.current().nextDouble(-s, s)
        );

        double diffX = targetVec.x - eyePos.x;
        double diffY = targetVec.y - eyePos.y;
        double diffZ = targetVec.z - eyePos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{
            self.getYaw() + MathHelper.wrapDegrees(yaw - self.getYaw()),
            self.getPitch() + MathHelper.wrapDegrees(pitch - self.getPitch())
        };
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МОДУЛИ ---
    private void handleAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;
        for (int i = 0; i < 45; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 45, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
                break;
            }
        }
    }

    private void handleInput(MinecraftClient client) {
        long h = client.getWindow().getHandle();
        if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
            client.setScreen(new BubbleMenu());
        }
        if (client.currentScreen == null) {
            if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
        }
    }

    // --- ИНТЕРФЕЙС (МЕНЮ И НАСТРОЙКИ) ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x80000000);
            int x = width/2 - 100, y = height/2 - 80;
            ctx.fill(x, y, x + 200, y + 160, 0xFF151515);
            ctx.drawBorder(x, y, 200, 160, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bBUBBLE §fPREMIUM", width/2, y + 10, -1);

            String[] mods = {"KillAura", "TriggerBot", "AutoTotem", "FullBright", "Waypoint"};
            boolean[] states = {killaura, triggerbot, autoTotem, fullbright, waypointActive};

            for (int i = 0; i < mods.length; i++) {
                int iy = y + 40 + (i * 22);
                boolean hover = mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 16;
                ctx.fill(x + 10, iy, x + 190, iy + 16, hover ? 0xFF252525 : 0xFF1A1A1A);
                ctx.drawTextWithShadow(client.textRenderer, mods[i], x + 15, iy + 4, states[i] ? 0xFF00FF00 : -1);
                if (i == 0) ctx.drawTextWithShadow(client.textRenderer, "§7[R-CLICK]", x + 130, iy + 4, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 100, y = height/2 - 80;
            for (int i = 0; i < 5; i++) {
                int iy = y + 40 + (i * 22);
                if (mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 16) {
                    if (i == 0) {
                        if (b == 1) client.setScreen(new KillAuraSettings(this));
                        else killaura = !killaura;
                    }
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
        private TextFieldWidget rangeField;
        public KillAuraSettings(Screen parent) { super(Text.literal("Settings")); this.parent = parent; }

        @Override
        protected void init() {
            rangeField = new TextFieldWidget(client.textRenderer, width/2 + 20, height/2 - 60, 40, 16, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            addSelectableChild(rangeField);
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xF0050505);
            int x = width/2, y = height/2;
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bKILL AURA §fCONFIG", x, y - 90, -1);
            
            ctx.drawTextWithShadow(client.textRenderer, "Reach Distance:", x - 80, y - 56, -1);
            rangeField.render(ctx, mx, my, d);

            // Твои настройки
            drawCheck(ctx, "AutoRun (Sprinting)", autoRun, y - 20, mx, my);
            drawCheck(ctx, "AntiVelocity (No-KB)", antiVelocity, y + 5, mx, my);
            drawCheck(ctx, "Anti-Invisible (Hit Inv)", antiInvisible, y + 30, mx, my);

            // Конфиги серверов
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§7--- PRESETS ---", x, y + 55, -1);
            drawBtn(ctx, "AresMine", x - 90, y + 75, mx, my);
            drawBtn(ctx, "MineBlaze", x - 25, y + 75, mx, my);
            drawBtn(ctx, "FunTime", x + 40, y + 75, mx, my);
        }

        private void drawCheck(DrawContext ctx, String n, boolean s, int y, int mx, int my) {
            boolean h = mx >= width/2 - 80 && mx <= width/2 + 80 && my >= y && my <= y + 12;
            ctx.drawCenteredTextWithShadow(client.textRenderer, n + ": " + (s ? "§aON" : "§cOFF"), width/2, y, h ? 0xFF00AAFF : -1);
        }

        private void drawBtn(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 55 && my >= y && my <= y + 14;
            ctx.fill(x, y, x + 55, y + 14, h ? 0xFF303030 : 0xFF151515);
            ctx.drawCenteredTextWithShadow(client.textRenderer, n, x + 27, y + 3, h ? 0xFF00AAFF : -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 80 && mx <= x + 80) {
                if (my >= y - 20 && my <= y - 8) autoRun = !autoRun;
                if (my >= y + 5 && my <= y + 17) antiVelocity = !antiVelocity;
                if (my >= y + 30 && my <= y + 42) antiInvisible = !antiInvisible;
            }
            if (my >= y + 75 && my <= y + 89) {
                if (mx >= x - 90 && mx <= x - 35) { kaRange = 3.8; rangeField.setText("3.8"); shakeIntensity = 0.8f; }
                if (mx >= x - 25 && mx <= x + 30) { kaRange = 3.1; rangeField.setText("3.1"); shakeIntensity = 0.2f; }
                if (mx >= x + 40 && mx <= x + 95) { kaRange = 3.02; rangeField.setText("3.02"); shakeIntensity = 0.5f; }
            }
            return super.mouseClicked(mx, my, b);
        }

        @Override
        public boolean keyPressed(int k, int s, int n) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange = Double.parseDouble(rangeField.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return rangeField.keyPressed(k, s, n) || super.keyPressed(k, s, n);
        }
    }

    // --- СИСТЕМА СОХРАНЕНИЯ ---
    private static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + autoRun + ":" + antiVelocity + ":" + antiInvisible + ":" + keyKA);
        } catch (IOException ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            kaRange = Double.parseDouble(p[0]);
            autoRun = Boolean.parseBoolean(p[1]);
            antiVelocity = Boolean.parseBoolean(p[2]);
            antiInvisible = Boolean.parseBoolean(p[3]);
            keyKA = Integer.parseInt(p[4]);
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false;
        return false;
    }

    private static void sendNotify(String m, boolean s) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + m + ": " + (s ? "§aON" : "§cOFF")), true);
    }
}
