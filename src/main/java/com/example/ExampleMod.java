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
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    // Состояния модулей
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean autoTotem = true;
    public static boolean autoRun = true;
    public static boolean targetHudActive = true;
    public static boolean kaTargetStick = true;
    public static boolean waypointActive = false;

    // Настройки
    public static double kaRange = 3.8;
    public static double kaWallsRange = 3.0;
    public static float shakeIntensity = 0.5f;
    public static int thX = 10, thY = 10;
    public static double wpX = 0, wpY = 64, wpZ = 0;

    // Бинды
    public static int keyKA = GLFW.GLFW_KEY_R;
    public static int keyTB = GLFW.GLFW_KEY_Z;
    public static int keyFB = GLFW.GLFW_KEY_B;
    public static int keyAT = GLFW.GLFW_KEY_G;
    public static int keyWP = GLFW.GLFW_KEY_O;

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

            // Открытие меню на Правый Shift
            if (isPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Обработка биндов в игре
            if (client.currentScreen == null) {
                if (isPressed(handle, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(handle, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(handle, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(handle, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(handle, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }

            // Логика модулей
            if (fullbright) {
                client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && client.player.input.movementForward > 0) client.player.setSprinting(true);

            if (killaura) runAura(client); else currentTarget = null;
            if (triggerbot) runTrigger(client);
        });

        HudRenderCallback.EVENT.register(this::renderEverything);
    }

    private void renderEverything(DrawContext ctx, RenderTickCounter tick) {
        if (targetHudActive) renderTargetHUD(ctx);
        if (waypointActive) renderWaypointArrow(ctx);
    }

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
            float s = shakeIntensity * 1.5f;
            client.player.setYaw(client.player.getYaw() + (random.nextFloat() - 0.5f) * s);
            client.player.setPitch(client.player.getPitch() + (random.nextFloat() - 0.5f) * s);

            // Target (Присоска) - притягивает к врагу, если у него < 10 HP
            if (kaTargetStick && target.getHealth() < 10.0f) {
                Vec3d diff = target.getPos().subtract(client.player.getPos()).normalize().multiply(0.045);
                client.player.addVelocity(diff.x, 0, diff.z);
            }

            // Удар
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

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

    private void renderTargetHUD(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (currentTarget == null) return;

        int x = thX, y = thY, w = 130, h = 42;
        ctx.fill(x, y, x + w, y + h, 0xAA101010); // Фон

        // Рендер головы (Face only)
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(currentTarget.getUuid());
        if (entry != null) {
            Identifier skin = entry.getSkinTextures().texture();
            ctx.drawTexture(RenderLayer::getGuiTextured, skin, x + 5, y + 5, 8, 8, 32, 32, 64, 64);
            ctx.drawTexture(RenderLayer::getGuiTextured, skin, x + 5, y + 5, 40, 8, 32, 32, 64, 64);
        }

        ctx.drawTextWithShadow(client.textRenderer, currentTarget.getName().getString(), x + 42, y + 6, -1);

        // Полоска здоровья с переливом (Nursultan Style)
        float hpPercent = currentTarget.getHealth() / currentTarget.getMaxHealth();
        animatedHP += (hpPercent - animatedHP) * 0.12f;

        int barX = x + 42, barY = y + 18, barW = 80, barH = 5;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0x40FFFFFF); // Подложка

        int currentBarW = (int) (barW * animatedHP);
        long time = System.currentTimeMillis();
        for (int i = 0; i < currentBarW; i++) {
            float wave = (float) Math.sin((time / 350.0) + (i / 7.0)) * 0.5f + 0.5f;
            int r = (int) (160 + wave * 95);
            int g = (int) (160 + wave * 95);
            int b = (int) (160 + wave * 95);
            ctx.fill(barX + i, barY, barX + i + 1, barY + barH, (255 << 24) | (r << 16) | (g << 8) | b);
        }
        ctx.drawText(client.textRenderer, String.format("HP: %.1f", currentTarget.getHealth()), barX, barY + 9, 0xFFBBBBBB, false);
    }

    private void renderWaypointArrow(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d targetVec = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
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

    private void sendNotify(String mod, boolean s) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + mod + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    // --- ИНТЕРФЕЙС МЕНЮ (СТАРЫЙ СТИЛЬ) ---
    public static class BubbleMenu extends Screen {
        private String inputVal = "";
        private int selectedParam = -1; // 0: Range, 1: Walls, 2: Shake

        public BubbleMenu() { super(Text.literal("")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x85000000);
            int x = width / 2 - 160, y = height / 2 - 100;

            // Левая часть (CONFIGS)
            ctx.fill(x, y, x + 90, y + 180, 0xFF121212);
            ctx.drawBorder(x, y, 90, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "CONFIGS", x + 45, y + 10, 0xFF00AAFF);
            
            drawButton(ctx, "MineBlaze", x + 10, y + 35, mx, my);
            drawButton(ctx, "AresMine", x + 10, y + 60, mx, my);

            // Правая часть (SETTINGS)
            ctx.fill(x + 95, y, x + 320, y + 180, 0xFF121212);
            ctx.drawBorder(x + 95, y, 225, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "KILL AURA SETTINGS", x + 207, y + 10, 0xFF00AAFF);

            drawRow(ctx, "Range", kaRange, x + 110, y + 40, selectedParam == 0);
            drawRow(ctx, "WallsRange", kaWallsRange, x + 110, y + 65, selectedParam == 1);
            drawRow(ctx, "Shake", (double) shakeIntensity, x + 110, y + 90, selectedParam == 2);
            
            // Чекбоксы
            drawCheckbox(ctx, "Target Stick", kaTargetStick, x + 110, y + 115);
            drawCheckbox(ctx, "TargetHUD", targetHudActive, x + 110, y + 135);

            if (selectedParam != -1) {
                ctx.drawCenteredTextWithShadow(textRenderer, "Type & Enter: §a" + inputVal + "_", x + 207, y + 160, -1);
            }
        }

        private void drawRow(DrawContext ctx, String name, double val, int x, int y, boolean sel) {
            ctx.drawTextWithShadow(textRenderer, name, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, (sel ? "§b" : "") + "< " + String.format("%.1f", val) + " >", x + 130, y, -1);
        }

        private void drawCheckbox(DrawContext ctx, String name, boolean state, int x, int y) {
            ctx.drawTextWithShadow(textRenderer, name, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, state ? "§a[ON]" : "§c[OFF]", x + 130, y, -1);
        }

        private void drawButton(DrawContext ctx, String text, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 70 && my >= y && my <= y + 18;
            ctx.fill(x, y, x + 70, y + 18, h ? 0xFF303030 : 0xFF202020);
            ctx.drawCenteredTextWithShadow(textRenderer, text, x + 35, y + 5, -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2 - 160, y = height / 2 - 100;
            // Клик по конфигам
            if (mx >= x + 10 && mx <= x + 80) {
                if (my >= y + 35 && my <= y + 53) { kaRange = 3.1; kaWallsRange = 3.1; shakeIntensity = 0.3f; }
                if (my >= y + 60 && my <= y + 78) { kaRange = 3.8; kaWallsRange = 3.0; shakeIntensity = 0.5f; }
            }
            // Клик по параметрам (для ввода)
            if (mx >= x + 220 && mx <= x + 300) {
                if (my >= y + 40 && my <= y + 55) { selectedParam = 0; inputVal = ""; }
                if (my >= y + 65 && my <= y + 80) { selectedParam = 1; inputVal = ""; }
                if (my >= y + 90 && my <= y + 105) { selectedParam = 2; inputVal = ""; }
                if (my >= y + 115 && my <= y + 130) kaTargetStick = !kaTargetStick;
                if (my >= y + 135 && my <= y + 150) targetHudActive = !targetHudActive;
            }
            saveConfig();
            return true;
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ENTER && selectedParam != -1) {
                try {
                    double v = Double.parseDouble(inputVal);
                    if (selectedParam == 0) kaRange = v;
                    if (selectedParam == 1) kaWallsRange = v;
                    if (selectedParam == 2) shakeIntensity = (float) v;
                } catch (Exception ignored) {}
                selectedParam = -1; return true;
            }
            if ((k >= 48 && k <= 57) || k == 46) inputVal += (char) k;
            if (k == 259 && inputVal.length() > 0) inputVal = inputVal.substring(0, inputVal.length() - 1);
            if (k == 256) client.setScreen(null);
            return true;
        }
    }

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
                kaRange = Double.parseDouble(p[0]);
                kaWallsRange = Double.parseDouble(p[1]);
                shakeIntensity = Float.parseFloat(p[2]);
                kaTargetStick = Boolean.parseBoolean(p[3]);
                targetHudActive = Boolean.parseBoolean(p[4]);
            }
        } catch (Exception ignored) {}
    }
}

