package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ExampleMod implements ModInitializer {

    // --- ГЛОБАЛЬНЫЕ МОДУЛИ ---
    public static boolean killaura = false, triggerbot = false, autoTotem = true, fullbright = false, waypointActive = false;
    public static boolean autoRun = true, antiVelocity = true, antiInvisible = true;

    // --- НАСТРОЙКИ ---
    public static double kaRange = 3.2, kaWallsRange = 3.0;
    public static float shakeIntensity = 0.5f;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    
    // Бинды
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN;
    
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    public static PlayerEntity currentTarget = null;

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            handleKeybinds(client);

            // 1. FULLBRIGHT (Максимальное освещение)
            if (fullbright) {
                client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }

            // 2. AUTO-TOTEM (Неурезанная логика перекладывания)
            if (autoTotem) runAutoTotem(client);

            // 3. AUTO-RUN (Умный спринт)
            if (autoRun) {
                if (client.player.input.movementForward > 0 && !client.player.isSneaking() && !client.player.isHorizontalCollision) {
                    client.player.setSprinting(true);
                }
            }

            // 4. ANTI-VELOCITY (Логика отмены отдачи через обнуление векторов)
            if (antiVelocity && client.player.velocityModified) {
                client.player.setVelocity(0, client.player.getVelocity().y, 0);
            }

            // 5. KILL AURA (Основной движок)
            if (killaura) runKillAura(client); else currentTarget = null;
        });
    }

    private void runKillAura(MinecraftClient client) {
        double bestDist = kaRange;
        PlayerEntity target = null;

        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || !player.isAlive() || player.isCreative()) continue;

            // ФУНКЦИЯ ANTI-INVISIBLE (Бьет невидимок, если включено)
            if (!antiInvisible && player.isInvisible()) continue;

            double d = client.player.distanceTo(player);
            if (d <= bestDist) {
                // Raytrace / Wall Check
                if (!client.player.canSee(player) && d > kaWallsRange) continue;
                bestDist = d;
                target = player;
            }
        }

        currentTarget = target;
        if (target != null) {
            // Сложная наводка (Rotations + Shake)
            float[] rots = getRotations(client.player, target);
            client.player.setYaw(rots[0]);
            client.player.setPitch(rots[1]);

            // Пакетная критическая атака (если в прыжке)
            if (client.player.getAttackCooldownProgress(0) >= 0.93f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private float[] getRotations(PlayerEntity self, PlayerEntity target) {
        double s = shakeIntensity * 0.13;
        // Наводка не в одну точку, а в область хитбокса (обход античита)
        Vec3d targetPos = target.getPos().add(
                ThreadLocalRandom.current().nextDouble(-s, s),
                target.getHeight() * (0.3 + ThreadLocalRandom.current().nextDouble(0.1, 0.4)),
                ThreadLocalRandom.current().nextDouble(-s, s)
        );

        Vec3d diff = targetPos.subtract(self.getEyePos());
        double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ));

        return new float[]{
                self.getYaw() + MathHelper.wrapDegrees(yaw - self.getYaw()),
                self.getPitch() + MathHelper.wrapDegrees(pitch - self.getPitch())
        };
    }

    private void runAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;
        
        for (int i = 0; i < 45; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                // Быстрый перенос в левую руку
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, client.player);
                break;
            }
        }
    }

    private void handleKeybinds(MinecraftClient client) {
        long h = client.getWindow().getHandle();
        if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
            client.setScreen(new BubbleMenu());
        }
        if (client.currentScreen == null) {
            if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
        }
    }

    // --- ГРАФИЧЕСКИЙ ИНТЕРФЕЙС (GUI) ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 90, y = height/2 - 80;
            ctx.fill(x, y, x + 180, y + 160, 0xFF101010);
            ctx.drawBorder(x, y, 180, 160, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bBUBBLE §fCLIENT", width/2, y + 10, -1);

            String[] mods = {"KillAura", "TriggerBot", "AutoTotem", "FullBright", "Waypoint"};
            boolean[] states = {killaura, triggerbot, autoTotem, fullbright, waypointActive};

            for (int i = 0; i < mods.length; i++) {
                int iy = y + 40 + (i * 22);
                boolean hover = mx >= x + 10 && mx <= x + 170 && my >= iy && my <= iy + 18;
                ctx.fill(x + 10, iy, x + 170, iy + 18, hover ? 0xFF202020 : 0xFF161616);
                ctx.drawTextWithShadow(client.textRenderer, mods[i], x + 15, iy + 5, states[i] ? 0xFF00FFAA : -1);
                if (i == 0) ctx.drawTextWithShadow(client.textRenderer, "§7[R]", x + 155, iy + 5, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 90, y = height/2 - 80;
            for (int i = 0; i < 5; i++) {
                int iy = y + 40 + (i * 22);
                if (mx >= x + 10 && mx <= x + 170 && my >= iy && my <= iy + 18) {
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
        private TextFieldWidget rangeField;
        public KillAuraSettings(Screen parent) { super(Text.literal("Settings")); this.parent = parent; }

        @Override
        protected void init() {
            rangeField = new TextFieldWidget(client.textRenderer, width/2 + 20, height/2 - 50, 40, 16, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            addSelectableChild(rangeField);
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xF0000000);
            int x = width/2, y = height/2;
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bKILL AURA §fADVANCED", x, y - 80, -1);
            ctx.drawTextWithShadow(client.textRenderer, "Range:", x - 70, y - 46, -1);
            rangeField.render(ctx, mx, my, d);

            drawOpt(ctx, "AutoRun", autoRun, y - 10, mx, my);
            drawOpt(ctx, "AntiVelocity", antiVelocity, y + 10, mx, my);
            drawOpt(ctx, "Anti-Invisible", antiInvisible, y + 30, mx, my);

            drawPreset(ctx, "Ares", x - 85, y + 60, mx, my);
            drawPreset(ctx, "Blaze", x - 20, y + 60, mx, my);
            drawPreset(ctx, "FunTime", x + 45, y + 60, mx, my);
        }

        private void drawOpt(DrawContext ctx, String n, boolean s, int y, int mx, int my) {
            boolean h = mx >= width/2 - 70 && mx <= width/2 + 70 && my >= y && my <= y + 10;
            ctx.drawCenteredTextWithShadow(client.textRenderer, n + ": " + (s ? "§aON" : "§cOFF"), width/2, y, h ? 0xFF00AAFF : -1);
        }

        private void drawPreset(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 45 && my >= y && my <= y + 12;
            ctx.drawCenteredTextWithShadow(client.textRenderer, n, x + 22, y, h ? 0xFF00AAFF : -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 70 && mx <= x + 70) {
                if (my >= y - 10 && my <= y) autoRun = !autoRun;
                if (my >= y + 10 && my <= y + 20) antiVelocity = !antiVelocity;
                if (my >= y + 30 && my <= y + 40) antiInvisible = !antiInvisible;
            }
            if (my >= y + 60 && my <= y + 72) {
                if (mx >= x - 85 && mx <= x - 40) { kaRange = 3.8; rangeField.setText("3.8"); }
                if (mx >= x - 20 && mx <= x + 25) { kaRange = 3.1; rangeField.setText("3.1"); }
                if (mx >= x + 45 && mx <= x + 100) { kaRange = 3.02; rangeField.setText("3.02"); }
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

    // --- СЛУЖЕБНЫЕ МЕТОДЫ ---
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

