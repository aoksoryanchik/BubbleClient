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

    // --- ВНУТРЕННИЕ КЛАССЫ НАСТРОЕК (Полное восстановление из .jar) ---
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

    // --- СОСТОЯНИЯ МОДУЛЕЙ ---
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean autoTotem = true;
    public static boolean targetHudActive = true;
    public static int thX = 30, thY = 30;

    // --- КЛАВИШИ (БИНДЫ) ---
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
        
        // РЕГИСТРАЦИЯ ТИКОВ (Здесь всё: и меню, и модули)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();

            // 1. ПРОВЕРКА ОТКРЫТИЯ МЕНЮ (Right Shift)
            if (isPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                if (client.currentScreen == null) {
                    client.setScreen(new BubbleMenu());
                }
            }

            // 2. ОБРАБОТКА БИНДОВ (Только если меню закрыто)
            if (client.currentScreen == null) {
                if (isPressed(handle, keyKA)) killaura = !killaura;
                if (isPressed(handle, keyTB)) triggerbot = !triggerbot;
                if (isPressed(handle, keyFB)) fullbright = !fullbright;
                if (isPressed(handle, keyAT)) autoTotem = !autoTotem;
            }

            // 3. ЛОГИКА МОДУЛЕЙ
            if (fullbright) {
                client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else {
                client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION);
            }
            
            if (autoTotem) handleAutoTotem(client);
            if (killaura) runAura(client); else currentTarget = null;
            if (triggerbot) runTrigger(client);
        });

        // РЕНДЕР HUD ЭЛЕМЕНТОВ
        HudRenderCallback.EVENT.register(this::renderEverything);
    }

    private void renderEverything(DrawContext ctx, RenderTickCounter tick) {
        if (targetHudActive) renderTargetHUD(ctx);
        if (WaypointSettings.active) renderWaypointArrow(ctx);
    }

    // --- КИЛЛАУРА И ТАРГЕТ СТИК ---
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
            // Shake logic
            float s = KillAuraSettings.shake * 2.1f;
            client.player.setYaw(client.player.getYaw() + (random.nextFloat() - 0.5f) * s);
            client.player.setPitch(client.player.getPitch() + (random.nextFloat() - 0.5f) * s);

            // Target Stick (Присоска)
            if (KillAuraSettings.stick && target.getHealth() < 14.0f) {
                Vec3d velocity = target.getPos().subtract(client.player.getPos()).normalize().multiply(0.048);
                client.player.addVelocity(velocity.x, 0, velocity.z);
            }

            // Attack logic (Strict 0.95 CD)
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

    // --- TARGET HUD (Nursultan Style) ---
    private void renderTargetHUD(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (currentTarget == null) return;

        int x = thX, y = thY, w = 140, h = 48;
        ctx.fill(x, y, x + w, y + h, 0xDD0C0C0C); 

        // ИСПРАВЛЕНИЕ ОШИБКИ ИЗ-ЗА IDENTIFIER
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(currentTarget.getUuid());
        if (entry != null) {
            Identifier skinTexture = entry.getSkinTextures().texture();
            ctx.drawTexture(RenderLayer::getGuiTextured, skinTexture, x + 6, y + 6, 8, 8, 36, 36, 64, 64);
        }

        ctx.drawTextWithShadow(client.textRenderer, currentTarget.getName().getString(), x + 48, y + 8, -1);
        
        float hpRatio = MathHelper.clamp(currentTarget.getHealth() / currentTarget.getMaxHealth(), 0, 1);
        animatedHP += (hpRatio - animatedHP) * 0.15f;
        
        int barW = (int) (86 * animatedHP);
        long time = System.currentTimeMillis();
        for (int i = 0; i < barW; i++) {
            float wave = (float) Math.sin((time / 300.0) + (i / 12.0)) * 0.5f + 0.5f;
            int color = (255 << 24) | ((int)(140 + wave * 115) << 16) | ((int)(140 + wave * 115) << 8) | (int)(140 + wave * 115);
            ctx.fill(x + 48 + i, y + 22, x + 49 + i, y + 28, color);
        }
        ctx.drawText(client.textRenderer, "HP: " + String.format("%.1f", currentTarget.getHealth()), x + 48, y + 32, 0xFFAAAAAA, false);
    }

    private void renderWaypointArrow(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d vec = new Vec3d(WaypointSettings.x, WaypointSettings.y, WaypointSettings.z).subtract(client.player.getPos());
        float targetYaw = (float) Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90F;
        float diff = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
        ctx.getMatrices().push();
        ctx.getMatrices().translate(client.getWindow().getScaledWidth() / 2f, client.getWindow().getScaledHeight() / 2f - 45, 0);
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
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false; return false;
    }

    // --- ГЛАВНОЕ МЕНЮ (BubbleMenu.class) ---
    public static class BubbleMenu extends Screen {
        private String inputVal = "";
        private int selectedParam = -1; 

        public BubbleMenu() { super(Text.literal("")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int startX = width / 2 - 170, startY = height / 2 - 115;

            // CONFIGS
            ctx.fill(startX, startY, startX + 100, startY + 200, 0xFF101010);
            ctx.drawBorder(startX, startY, 100, 200, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "CONFIGS", startX + 50, startY + 12, 0xFF00AAFF);
            
            renderConfigBtn(ctx, "MineBlaze", startX + 10, startY + 45, mx, my);
            renderConfigBtn(ctx, "AresMine", startX + 10, startY + 75, mx, my);

            // SETTINGS
            ctx.fill(startX + 105, startY, startX + 350, startY + 200, 0xFF101010);
            ctx.drawBorder(startX + 105, startY, 245, 200, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "MODULE PARAMETERS", startX + 227, startY + 12, 0xFF00AAFF);

            renderSetting(ctx, "Aura Range", KillAuraSettings.range, startX + 120, startY + 50, selectedParam == 0);
            renderSetting(ctx, "Wall Range", KillAuraSettings.wallsRange, startX + 120, startY + 75, selectedParam == 1);
            renderSetting(ctx, "Shake Power", (double) KillAuraSettings.shake, startX + 120, startY + 100, selectedParam == 2);
            
            renderToggle(ctx, "Stick (Magnet)", KillAuraSettings.stick, startX + 120, startY + 130);
            renderToggle(ctx, "Target HUD", targetHudActive, startX + 120, startY + 155);
            renderToggle(ctx, "Waypoint", WaypointSettings.active, startX + 120, startY + 175);

            if (selectedParam != -1) {
                ctx.drawCenteredTextWithShadow(textRenderer, "Value: §a" + inputVal + "_", startX + 227, startY + 188, -1);
            }
        }

        private void renderSetting(DrawContext ctx, String label, double val, int x, int y, boolean active) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, (active ? "§b" : "") + "< " + String.format("%.1f", val) + " >", x + 155, y, -1);
        }

        private void renderToggle(DrawContext ctx, String label, boolean state, int x, int y) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, state ? "§aON" : "§cOFF", x + 155, y, -1);
        }

        private void renderConfigBtn(DrawContext ctx, String name, int x, int y, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 80 && my >= y && my <= y + 22;
            ctx.fill(x, y, x + 80, y + 22, hover ? 0xFF404040 : 0xFF202020);
            ctx.drawCenteredTextWithShadow(textRenderer, name, x + 40, y + 7, -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int startX = width / 2 - 170, startY = height / 2 - 115;
            // Config logic
            if (mx >= startX + 10 && mx <= startX + 90) {
                if (my >= startY + 45 && my <= startY + 67) { KillAuraSettings.range = 3.1; KillAuraSettings.wallsRange = 3.1; KillAuraSettings.shake = 0.3f; }
                if (my >= startY + 75 && my <= startY + 97) { KillAuraSettings.range = 3.8; KillAuraSettings.wallsRange = 3.0; KillAuraSettings.shake = 0.5f; }
            }
            // Value editing
            if (mx >= startX + 260 && mx <= startX + 340) {
                if (my >= startY + 50 && my <= startY + 65) { selectedParam = 0; inputVal = ""; }
                if (my >= startY + 75 && my <= startY + 90) { selectedParam = 1; inputVal = ""; }
                if (my >= startY + 100 && my <= startY + 115) { selectedParam = 2; inputVal = ""; }
                if (my >= startY + 130 && my <= startY + 145) KillAuraSettings.stick = !KillAuraSettings.stick;
                if (my >= startY + 155 && my <= startY + 170) targetHudActive = !targetHudActive;
                if (my >= startY + 175 && my <= startY + 190) WaypointSettings.active = !WaypointSettings.active;
            }
            saveConfig();
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER && selectedParam != -1) {
                try {
                    double v = Double.parseDouble(inputVal);
                    if (selectedParam == 0) KillAuraSettings.range = v;
                    if (selectedParam == 1) KillAuraSettings.wallsRange = v;
                    if (selectedParam == 2) KillAuraSettings.shake = (float) v;
                } catch (Exception ignored) {}
                selectedParam = -1; return true;
            }
            if ((keyCode >= 48 && keyCode <= 57) || keyCode == 46) inputVal += (char) keyCode;
            if (keyCode == 259 && inputVal.length() > 0) inputVal = inputVal.substring(0, inputVal.length() - 1);
            if (keyCode == 256) client.setScreen(null);
            return true;
        }
    }

    // --- КОНФИГИ ---
    public static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println(KillAuraSettings.range + ":" + KillAuraSettings.wallsRange + ":" + KillAuraSettings.shake + ":" + KillAuraSettings.stick + ":" + targetHudActive + ":" + WaypointSettings.active);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 6) {
                KillAuraSettings.range = Double.parseDouble(p[0]); KillAuraSettings.wallsRange = Double.parseDouble(p[1]);
                KillAuraSettings.shake = Float.parseFloat(p[2]); KillAuraSettings.stick = Boolean.parseBoolean(p[3]);
                targetHudActive = Boolean.parseBoolean(p[4]); WaypointSettings.active = Boolean.parseBoolean(p[5]);
            }
        } catch (Exception ignored) {}
    }
}

