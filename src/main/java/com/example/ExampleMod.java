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

    // --- КЛАССЫ НАСТРОЕК (ПОЛНАЯ ВЕРСИЯ) ---
    public static class KillAuraSettings {
        public static double range = 3.8;
        public static double wallsRange = 3.0;
        public static float shake = 0.5f;
        public static boolean stick = true;
    }

    public static class TriggerSettings {
        public static double range = 3.5;
        public static float cooldown = 0.95f;
    }

    public static class WaypointSettings {
        public static double x = 0, y = 64, z = 0;
        public static boolean active = false;
    }

    // --- ПЕРЕМЕННЫЕ СОСТОЯНИЯ ---
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean autoTotem = true;
    public static boolean targetHudActive = true;
    public static int thX = 30, thY = 30;

    // --- БИНДЫ ---
    public static int keyMenu = GLFW.GLFW_KEY_0; // МЕНЮ НА 0
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
            long handle = client.getWindow().getHandle();

            // ОТКРЫТИЕ МЕНЮ НА 0
            if (isPressed(handle, keyMenu)) {
                if (client.currentScreen == null) {
                    client.setScreen(new BubbleMenu());
                }
            }

            // ОБРАБОТКА ОСТАЛЬНЫХ БИНДОВ
            if (client.currentScreen == null) {
                if (isPressed(handle, keyKA)) killaura = !killaura;
                if (isPressed(handle, keyTB)) triggerbot = !triggerbot;
                if (isPressed(handle, keyFB)) fullbright = !fullbright;
                if (isPressed(handle, keyAT)) autoTotem = !autoTotem;
            }

            // МОДУЛИ
            if (fullbright) {
                client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }
            
            if (autoTotem) handleAutoTotem(client);
            if (killaura) runAura(client); else currentTarget = null;
            if (triggerbot) runTrigger(client);
        });

        HudRenderCallback.EVENT.register(this::renderEverything);
    }

    private void renderEverything(DrawContext ctx, RenderTickCounter tick) {
        if (targetHudActive) renderTargetHUD(ctx);
        if (WaypointSettings.active) renderWaypointArrow(ctx);
    }

    // --- ЛОГИКА КИЛЛАУРЫ + ПРИСОСКА ---
    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isCreative()) continue;
            double d = client.player.distanceTo(p);
            if (d <= KillAuraSettings.range && d < bestDist) {
                if (!client.player.canSee(p) && d > KillAuraSettings.wallsRange) continue;
                bestDist = d; target = p;
            }
        }

        currentTarget = target;
        if (target != null) {
            // Тряска
            float s = KillAuraSettings.shake * 2.2f;
            client.player.setYaw(client.player.getYaw() + (random.nextFloat() - 0.5f) * s);
            client.player.setPitch(client.player.getPitch() + (random.nextFloat() - 0.5f) * s);

            // ПРИСОСКА (Target Stick)
            if (KillAuraSettings.stick && target.getHealth() < 12.0f) {
                Vec3d vec = target.getPos().subtract(client.player.getPos()).normalize().multiply(0.05);
                client.player.addVelocity(vec.x, 0, vec.z);
            }

            // УДАР (0.95 КД)
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, TriggerSettings.range);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= TriggerSettings.cooldown) {
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

    // --- ВИЗУАЛ: TARGET HUD ---
    private void renderTargetHUD(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (currentTarget == null) return;

        int x = thX, y = thY, w = 140, h = 45;
        ctx.fill(x, y, x + w, y + h, 0xCC0A0A0A); 

        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(currentTarget.getUuid());
        if (entry != null) {
            Identifier skin = entry.getSkinTextures().texture();
            ctx.drawTexture(RenderLayer::getGuiTextured, skin, x + 5, y + 5, 8, 8, 35, 35, 64, 64);
        }

        ctx.drawTextWithShadow(client.textRenderer, currentTarget.getName().getString(), x + 45, y + 6, -1);
        
        float hpPercent = MathHelper.clamp(currentTarget.getHealth() / currentTarget.getMaxHealth(), 0, 1);
        animatedHP += (hpPercent - animatedHP) * 0.12f;
        
        int barW = (int) (85 * animatedHP);
        long time = System.currentTimeMillis();
        for (int i = 0; i < barW; i++) {
            float wave = (float) Math.sin((time / 350.0) + (i / 10.0)) * 0.5f + 0.5f;
            int color = (255 << 24) | ((int)(150 + wave * 105) << 16) | ((int)(150 + wave * 105) << 8) | (int)(150 + wave * 105);
            ctx.fill(x + 45 + i, y + 20, x + 46 + i, y + 26, color);
        }
        ctx.drawText(client.textRenderer, "Health: " + (int)currentTarget.getHealth(), x + 45, y + 30, 0xFFAAAAAA, false);
    }

    private void renderWaypointArrow(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d targetVec = new Vec3d(WaypointSettings.x, WaypointSettings.y, WaypointSettings.z).subtract(client.player.getPos());
        float tYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90F;
        float diff = MathHelper.wrapDegrees(tYaw - client.player.getYaw());
        ctx.getMatrices().push();
        ctx.getMatrices().translate(client.getWindow().getScaledWidth() / 2f, client.getWindow().getScaledHeight() / 2f - 40, 0);
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

    // --- КЛАСС ГЛАВНОГО МЕНЮ ---
    public static class BubbleMenu extends Screen {
        private String inputVal = "";
        private int selectedParam = -1; 

        public BubbleMenu() { super(Text.literal("")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x85000000);
            int x = width / 2 - 165, y = height / 2 - 110;

            // CONFIGS
            ctx.fill(x, y, x + 90, y + 190, 0xFF121212);
            ctx.drawBorder(x, y, 90, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "CONFIGS", x + 45, y + 12, 0xFF00AAFF);
            
            drawBtn(ctx, "MineBlaze", x + 10, y + 45, mx, my);
            drawBtn(ctx, "AresMine", x + 10, y + 75, mx, my);

            // SETTINGS
            ctx.fill(x + 95, y, x + 335, y + 190, 0xFF121212);
            ctx.drawBorder(x + 95, y, 240, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "AURA PARAMETERS", x + 215, y + 12, 0xFF00AAFF);

            drawRow(ctx, "Range", KillAuraSettings.range, x + 110, y + 50, selectedParam == 0);
            drawRow(ctx, "Walls", KillAuraSettings.wallsRange, x + 110, y + 75, selectedParam == 1);
            drawRow(ctx, "Shake", (double) KillAuraSettings.shake, x + 110, y + 100, selectedParam == 2);
            
            drawBool(ctx, "Target Stick", KillAuraSettings.stick, x + 110, y + 130);
            drawBool(ctx, "Target HUD", targetHudActive, x + 110, y + 155);

            if (selectedParam != -1) {
                ctx.drawCenteredTextWithShadow(textRenderer, "Input: §a" + inputVal + "_", x + 215, y + 175, -1);
            }
        }

        private void drawRow(DrawContext ctx, String n, double v, int x, int y, boolean s) {
            ctx.drawTextWithShadow(textRenderer, n, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, (s ? "§b" : "") + "< " + String.format("%.1f", v) + " >", x + 150, y, -1);
        }

        private void drawBool(DrawContext ctx, String n, boolean st, int x, int y) {
            ctx.drawTextWithShadow(textRenderer, n, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, st ? "§aON" : "§cOFF", x + 150, y, -1);
        }

        private void drawBtn(DrawContext ctx, String t, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 70 && my >= y && my <= y + 22;
            ctx.fill(x, y, x + 70, y + 22, h ? 0xFF353535 : 0xFF202020);
            ctx.drawCenteredTextWithShadow(textRenderer, t, x + 35, y + 7, -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2 - 165, y = height / 2 - 110;
            if (mx >= x + 10 && mx <= x + 80) {
                if (my >= y + 45 && my <= y + 67) { KillAuraSettings.range = 3.1; KillAuraSettings.wallsRange = 3.1; KillAuraSettings.shake = 0.3f; }
                if (my >= y + 75 && my <= y + 97) { KillAuraSettings.range = 3.8; KillAuraSettings.wallsRange = 3.0; KillAuraSettings.shake = 0.5f; }
            }
            if (mx >= x + 250 && mx <= x + 320) {
                if (my >= y + 50 && my <= y + 65) { selectedParam = 0; inputVal = ""; }
                if (my >= y + 75 && my <= y + 90) { selectedParam = 1; inputVal = ""; }
                if (my >= y + 100 && my <= y + 115) { selectedParam = 2; inputVal = ""; }
                if (my >= y + 130 && my <= y + 145) KillAuraSettings.stick = !KillAuraSettings.stick;
                if (my >= y + 155 && my <= y + 170) targetHudActive = !targetHudActive;
            }
            saveConfig();
            return true;
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ENTER && selectedParam != -1) {
                try {
                    double v = Double.parseDouble(inputVal);
                    if (selectedParam == 0) KillAuraSettings.range = v;
                    if (selectedParam == 1) KillAuraSettings.wallsRange = v;
                    if (selectedParam == 2) KillAuraSettings.shake = (float) v;
                } catch (Exception ignored) {}
                selectedParam = -1; return true;
            }
            if ((k >= 48 && k <= 57) || k == 46) inputVal += (char) k;
            if (k == 259 && inputVal.length() > 0) inputVal = inputVal.substring(0, inputVal.length() - 1);
            if (k == 256) client.setScreen(null);
            return true;
        }
    }

    // --- КОНФИГИ ---
    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(KillAuraSettings.range + ":" + KillAuraSettings.wallsRange + ":" + KillAuraSettings.shake + ":" + KillAuraSettings.stick + ":" + targetHudActive);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 5) {
                KillAuraSettings.range = Double.parseDouble(p[0]); KillAuraSettings.wallsRange = Double.parseDouble(p[1]);
                KillAuraSettings.shake = Float.parseFloat(p[2]); KillAuraSettings.stick = Boolean.parseBoolean(p[3]);
                targetHudActive = Boolean.parseBoolean(p[4]);
            }
        } catch (Exception ignored) {}
    }
}

