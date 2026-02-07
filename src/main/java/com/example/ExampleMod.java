package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    [span_1](start_span)// Состояния модулей (из оригинального ExampleMod.class)[span_1](end_span)
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean autoTotem = true;
    public static boolean targetHudActive = true;
    public static boolean kaTargetStick = true;
    public static boolean waypointActive = false;

    // Настройки параметров
    public static double kaRange = 3.8;
    public static double kaWallsRange = 3.0;
    public static float shakeIntensity = 0.5f;
    public static int thX = 30, thY = 30;
    public static double wpX = 0, wpY = 64, wpZ = 0;

    // Клавиши управления
    public static int keyKA = GLFW.GLFW_KEY_R;
    public static int keyTB = GLFW.GLFW_KEY_Z;
    public static int keyFB = GLFW.GLFW_KEY_B;
    public static int keyAT = GLFW.GLFW_KEY_G;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();
    public static PlayerEntity currentTarget = null;
    private float animatedHP = 0;

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            [span_2](start_span)// Открытие оригинального меню (BubbleMenu.class)[span_2](end_span)
            if (isPressed(h, GLFW.GLFW_KEY_RIGHT_SHIFT) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            [span_3](start_span)// Обработка нажатий клавиш (BindScreen.class логика)[span_3](end_span)
            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) killaura = !killaura;
                if (isPressed(h, keyTB)) triggerbot = !triggerbot;
                if (isPressed(h, keyFB)) fullbright = !fullbright;
                if (isPressed(h, keyAT)) autoTotem = !autoTotem;
            }

            // Модули
            if (fullbright) client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            
            if (autoTotem) handleAutoTotem(client);
            if (killaura) runAura(client); else currentTarget = null;
            if (triggerbot) runTrigger(client);
        });

        HudRenderCallback.EVENT.register(this::renderEverything);
    }

    private void renderEverything(DrawContext ctx, RenderTickCounter tick) {
        if (targetHudActive) renderTargetHUD(ctx);
        if (waypointActive) renderWaypointArrow(ctx);
    }

    // --- КИЛЛАУРА И ТАРГЕТ (ПРИСОСКА) ---
    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isCreative()) continue;
            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }

        currentTarget = target;
        if (target != null) {
            // Тряска (Shake)
            float s = shakeIntensity * 2.0f;
            client.player.setYaw(client.player.getYaw() + (random.nextFloat() - 0.5f) * s);
            client.player.setPitch(client.player.getPitch() + (random.nextFloat() - 0.5f) * s);

            // TARGET (Присоска) - Плавное прилипание
            if (kaTargetStick && target.getHealth() < 12.0f) {
                Vec3d motion = target.getPos().subtract(client.player.getPos()).normalize().multiply(0.05);
                client.player.addVelocity(motion.x, 0, motion.z);
            }

            // Удар (КД 0.95)
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // --- ТРИГГЕРБОТ (БЕЗ НАСТРОЕК) ---
    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, kaRange);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private EntityHitResult raycastEntity(MinecraftClient client, double range) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).multiply(range);
        Box box = client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0);
        return ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e.isAlive(), range * range);
    }

    // --- TARGET HUD (NURSULTAN STYLE) ---
    private void renderTargetHUD(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (currentTarget == null) return;

        int x = thX, y = thY, w = 130, h = 45;
        ctx.fill(x, y, x + w, y + h, 0xCC000000); // Глубокий темный фон

        // Голова игрока (Метод из новых версий 1.20+)
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(currentTarget.getUuid());
        if (entry != null) {
            Identifier skin = entry.getSkinTextures().texture();
            ctx.drawTexture(RenderLayer::getGuiTextured, skin, x + 5, y + 5, 8, 8, 35, 35, 64, 64);
        }

        ctx.drawTextWithShadow(client.textRenderer, currentTarget.getName().getString(), x + 45, y + 6, -1);
        
        // Анимированная полоска HP
        float hpRatio = MathHelper.clamp(currentTarget.getHealth() / currentTarget.getMaxHealth(), 0, 1);
        animatedHP += (hpRatio - animatedHP) * 0.1f;
        
        int barW = (int) (80 * animatedHP);
        long time = System.currentTimeMillis();
        for (int i = 0; i < barW; i++) {
            float wave = (float) Math.sin((time / 400.0) + (i / 8.0)) * 0.5f + 0.5f;
            int color = (255 << 24) | ((int)(170 + wave * 85) << 16) | ((int)(170 + wave * 85) << 8) | (int)(170 + wave * 85);
            ctx.fill(x + 45 + i, y + 20, x + 46 + i, y + 26, color);
        }
        ctx.drawText(client.textRenderer, "Health: " + (int)currentTarget.getHealth(), x + 45, y + 30, 0xFFBBBBBB, false);
    }

    private void renderWaypointArrow(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d targetVec = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
        float tYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90F;
        float diff = MathHelper.wrapDegrees(tYaw - client.player.getYaw());
        ctx.getMatrices().push();
        ctx.getMatrices().translate(client.getWindow().getScaledWidth() / 2f, client.getWindow().getScaledHeight() / 2f - 35, 0);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(diff));
        ctx.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, 0, 0xFF00AAFF);
        ctx.getMatrices().pop();
    }

    private void handleAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, client.player);
                    break;
                }
            }
        }
    }

    private boolean isPressed(long h, int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    [span_4](start_span)// --- ОРИГИНАЛЬНОЕ МЕНЮ (BubbleMenu.class)[span_4](end_span) ---
    public static class BubbleMenu extends Screen {
        private String inputVal = "";
        private int selectedParam = -1; // 0: Range, 1: Walls, 2: Shake

        public BubbleMenu() { super(Text.literal("")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x85000000);
            int x = width / 2 - 160, y = height / 2 - 110;

            // CONFIGS
            ctx.fill(x, y, x + 90, y + 190, 0xFF141414);
            ctx.drawBorder(x, y, 90, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "CONFIGS", x + 45, y + 10, 0xFF00AAFF);
            
            drawMenuBtn(ctx, "MineBlaze", x + 10, y + 40, mx, my);
            drawMenuBtn(ctx, "AresMine", x + 10, y + 65, mx, my);

            // SETTINGS
            ctx.fill(x + 95, y, x + 330, y + 190, 0xFF141414);
            ctx.drawBorder(x + 95, y, 235, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "AURA CONFIGURATION", x + 212, y + 10, 0xFF00AAFF);

            renderSetting(ctx, "Range", kaRange, x + 110, y + 45, selectedParam == 0);
            renderSetting(ctx, "Walls", kaWallsRange, x + 110, y + 70, selectedParam == 1);
            renderSetting(ctx, "Shake", (double) shakeIntensity, x + 110, y + 95, selectedParam == 2);
            
            renderToggle(ctx, "Stick Target", kaTargetStick, x + 110, y + 125);
            renderToggle(ctx, "TargetHUD", targetHudActive, x + 110, y + 145);

            if (selectedParam != -1) {
                ctx.drawCenteredTextWithShadow(textRenderer, "Input: §a" + inputVal + "_", x + 212, y + 175, -1);
            }
        }

        private void renderSetting(DrawContext ctx, String n, double v, int x, int y, boolean s) {
            ctx.drawTextWithShadow(textRenderer, n, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, (s ? "§b" : "") + "< " + String.format("%.1f", v) + " >", x + 140, y, -1);
        }

        private void renderToggle(DrawContext ctx, String n, boolean st, int x, int y) {
            ctx.drawTextWithShadow(textRenderer, n, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, st ? "§aON" : "§cOFF", x + 140, y, -1);
        }

        private void drawMenuBtn(DrawContext ctx, String t, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 70 && my >= y && my <= y + 18;
            ctx.fill(x, y, x + 70, y + 18, h ? 0xFF353535 : 0xFF252525);
            ctx.drawCenteredTextWithShadow(textRenderer, t, x + 35, y + 5, -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2 - 160, y = height / 2 - 110;
            // Config logic
            if (mx >= x + 10 && mx <= x + 80) {
                if (my >= y + 40 && my <= y + 58) { kaRange = 3.1; kaWallsRange = 3.1; shakeIntensity = 0.3f; }
                if (my >= y + 65 && my <= y + 83) { kaRange = 3.8; kaWallsRange = 3.0; shakeIntensity = 0.5f; }
            }
            // Param logic
            if (mx >= x + 240 && mx <= x + 320) {
                if (my >= y + 45 && my <= y + 60) { selectedParam = 0; inputVal = ""; }
                if (my >= y + 70 && my <= y + 85) { selectedParam = 1; inputVal = ""; }
                if (my >= y + 95 && my <= y + 110) { selectedParam = 2; inputVal = ""; }
                if (my >= y + 125 && my <= y + 140) kaTargetStick = !kaTargetStick;
                if (my >= y + 145 && my <= y + 160) targetHudActive = !targetHudActive;
            }
            saveConfig();
            return true;
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ENTER && selectedParam != -1) {
                try {
                    double val = Double.parseDouble(inputVal);
                    if (selectedParam == 0) kaRange = val;
                    if (selectedParam == 1) kaWallsRange = val;
                    if (selectedParam == 2) shakeIntensity = (float) val;
                } catch (Exception ignored) {}
                selectedParam = -1; return true;
            }
            if ((k >= 48 && k <= 57) || k == 46) inputVal += (char) k;
            if (k == 259 && inputVal.length() > 0) inputVal = inputVal.substring(0, inputVal.length() - 1);
            if (k == 256) client.setScreen(null);
            return true;
        }
    }

    // --- СИСТЕМА КОНФИГОВ ---
    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + shakeIntensity + ":" + kaTargetStick + ":" + targetHudActive);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 5) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                shakeIntensity = Float.parseFloat(p[2]); kaTargetStick = Boolean.parseBoolean(p[3]);
                targetHudActive = Boolean.parseBoolean(p[4]);
            }
        } catch (Exception ignored) {}
    }
}

