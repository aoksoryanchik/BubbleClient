package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExampleMod implements ModInitializer {
    // Состояния модулей
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true, sticky = true;
    
    // Настройки параметров
    public static double kaRange = 3.9, kaWallsRange = 3.2;
    public static float shakeIntensity = 0.7f;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    private static double lastAngleHUD = 0;

    // Клавиши (бинды)
    public static int keyKA = GLFW.GLFW_KEY_R, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_B;
    public static int keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_N;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();

            // Открытие меню на "0"
            if (isPressed(handle, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Обработка биндов
            if (client.currentScreen == null) {
                if (isPressed(handle, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(handle, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(handle, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(handle, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(handle, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }

            // FullBright
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            
            // AutoTotem
            if (autoTotem) handleAutoTotem(client);
            
            // AntiVelocity (Обход откидывания)
            if (antiVelocity && client.player.hurtTime > 0 && !client.options.jumpKey.isPressed()) {
                Vec3d vel = client.player.getVelocity();
                client.player.setVelocity(vel.x * 0.4, vel.y, vel.z * 0.4);
            }

            // Модули атаки
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });

        // Рендер Навигатора (HUD)
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = Math.sqrt(client.player.squaredDistanceTo(wpX, wpY, wpZ));
            double angleToTarget = Math.toDegrees(Math.atan2(wpZ - client.player.getZ(), wpX - client.player.getX())) - 90;
            double relativeAngle = MathHelper.wrapDegrees(angleToTarget - client.player.getYaw());
            
            // Сглаживание стрелки
            lastAngleHUD = lastAngleHUD + (relativeAngle - lastAngleHUD) * 0.2;
            
            String arrow = "↑";
            String color = "§f";
            
            if (Math.abs(lastAngleHUD) < 10) {
                arrow = "↑ [ПРЯМО]";
                color = "§a"; // Зеленый если идем верно
            } else if (lastAngleHUD > 10 && lastAngleHUD < 170) {
                arrow = "→";
            } else if (lastAngleHUD < -10 && lastAngleHUD > -170) {
                arrow = "←";
            } else {
                arrow = "↓";
            }

            String line1 = String.format("%s%s §b(%.1f m)", color, arrow, dist);
            String line2 = String.format("§7Цель: §fX:%.0f Y:%.0f Z:%.0f", wpX, wpY, wpZ);
            
            int screenWidth = client.getWindow().getScaledWidth();
            drawContext.drawCenteredTextWithShadow(client.textRenderer, line1, screenWidth / 2, 10, -1);
            drawContext.drawCenteredTextWithShadow(client.textRenderer, line2, screenWidth / 2, 22, -1);
        });
    }

    private void handleAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    int slot = i < 9 ? i + 36 : i;
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 45, SlotActionType.SWAP, client.player);
                    break;
                }
            }
        }
    }

    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isInvisible() || p.isCreative()) continue;
            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }
        
        if (target != null) {
            if (autoRun) client.player.setSprinting(true);

            // Sticky logic: подтягивание к цели
            if (sticky && target.hurtTime > 4) {
                Vec3d vec = target.getPos().subtract(client.player.getPos()).normalize().multiply(0.12);
                client.player.addVelocity(vec.x, 0, vec.z);
            }

            // Ротации (наведение)
            updateRotations(client.player, target.getPos().add(0, target.getHeight() * 0.75, 0), 180.0f);
            
            // Удар
            if (client.player.getAttackCooldownProgress(0) >= 0.92f && target.hurtTime <= 10) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                // Микро-шейк для обхода паттерн-чеков
                if (shakeIntensity > 0) {
                    client.player.setYaw(client.player.getYaw() + (float)(Math.random() - 0.5) * shakeIntensity);
                }
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).multiply(kaRange);
        Box box = client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e != client.player, kaRange * kaRange);
        
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0f : 0.92f)) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void updateRotations(PlayerEntity player, Vec3d target, float speed) {
        Vec3d diff = target.subtract(player.getEyePos());
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(tYaw - player.getYaw()), -speed, speed));
        player.setPitch(player.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(tPitch - player.getPitch()), -speed, speed));
    }

    private void sendNotify(String module, boolean state) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + module + " : " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    // МЕНЮ КЛИЕНТА
    public static class BubbleMenu extends Screen {
        private int bindingIdx = -1;
        public BubbleMenu() { super(Text.literal("Bubble")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            // FIX: Удаление блюра через Reflection (чтобы не было ошибок компиляции)
            try {
                Field f = client.gameRenderer.getClass().getDeclaredField("postProcessor");
                f.setAccessible(true);
                f.set(client.gameRenderer, null);
            } catch (Exception ignored) {}

            int x = width/2 - 100, y = height/2 - 80;
            ctx.fill(x, y, x + 200, y + 160, 0xDD050505); 
            ctx.drawBorder(x, y, 200, 160, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT §7(v2.0)", width/2, y + 10, -1);
            
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyWP};

            for(int i = 0; i < names.length; i++) {
                int iy = y + 35 + i * 22;
                boolean hover = mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 18;
                ctx.fill(x + 10, iy, x + 190, iy + 18, hover ? 0xFF252525 : 0xFF121212);
                
                String keyName = bindingIdx == i ? "§b[???]" : (keys[i] != 0 ? " §7[" + GLFW.glfwGetKeyName(keys[i], 0).toUpperCase() + "]" : "");
                ctx.drawTextWithShadow(textRenderer, names[i] + keyName, x + 15, iy + 5, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                
                // Иконка настроек для КА, Триггера и Вейпоинта
                if(i == 0 || i == 1 || i == 4) ctx.drawTextWithShadow(textRenderer, "⚙", x + 175, iy + 5, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int x = width/2 - 100, y = height/2 - 80;
            for(int i = 0; i < 5; i++) {
                int iy = y + 35 + i * 22;
                if(mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 18) {
                    if (button == 1) { bindingIdx = i; return true; } // ПКМ для бинда
                    
                    if(mx >= x + 170) { // Клик на шестеренку
                        if(i == 0) client.setScreen(new KillAuraSettings(this));
                        if(i == 1) client.setScreen(new TriggerSettings(this));
                        if(i == 4) client.setScreen(new WaypointSettings(this));
                    } else { // Клик на модуль
                        if(i == 0) killaura = !killaura; if(i == 1) triggerbot = !triggerbot;
                        if(i == 2) fullbright = !fullbright; if(i == 3) autoTotem = !autoTotem;
                        if(i == 4) waypointActive = !waypointActive;
                        saveConfig();
                    }
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override public boolean keyPressed(int k, int s, int m) {
            if (bindingIdx != -1) {
                if (k == GLFW.GLFW_KEY_ESCAPE) k = 0;
                if (bindingIdx == 0) keyKA = k; if (bindingIdx == 1) keyTB = k;
                if (bindingIdx == 2) keyFB = k; if (bindingIdx == 3) keyAT = k;
                if (bindingIdx == 4) keyWP = k;
                bindingIdx = -1; saveConfig(); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // НАСТРОЙКИ KILL AURA
    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget fRange, fWalls, fShake;
        public KillAuraSettings(Screen p) { super(Text.literal("KA")); this.parent = p; }
        
        @Override protected void init() {
            fRange = new TextFieldWidget(textRenderer, width/2 + 20, height/2 - 60, 40, 14, Text.literal(""));
            fWalls = new TextFieldWidget(textRenderer, width/2 + 20, height/2 - 40, 40, 14, Text.literal(""));
            fShake = new TextFieldWidget(textRenderer, width/2 + 20, height/2 - 20, 40, 14, Text.literal(""));
            fRange.setText(String.valueOf(kaRange)); fWalls.setText(String.valueOf(kaWallsRange)); fShake.setText(String.valueOf(shakeIntensity));
            addDrawableChild(fRange); addDrawableChild(fWalls); addDrawableChild(fShake);
        }

        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(width/2 - 110, height/2 - 90, width/2 + 110, height/2 + 90, 0xEE050505);
            ctx.drawBorder(width/2 - 110, height/2 - 90, 220, 180, 0xFF00AAFF);
            ctx.drawTextWithShadow(textRenderer, "Reach:", width/2 - 100, height/2 - 57, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", width/2 - 100, height/2 - 37, -1);
            ctx.drawTextWithShadow(textRenderer, "Shake:", width/2 - 100, height/2 - 17, -1);
            
            drawToggleButton(ctx, "Sticky (Прилипание)", sticky, height/2 + 5, mx, my);
            drawToggleButton(ctx, "AntiVelocity", antiVelocity, height/2 + 25, mx, my);
            drawToggleButton(ctx, "AutoRun (Спринт)", autoRun, height/2 + 45, mx, my);
            super.render(ctx, mx, my, d);
        }

        private void drawToggleButton(DrawContext ctx, String text, boolean val, int y, int mx, int my) {
            int x = width/2 - 100;
            boolean h = mx >= x && mx <= x + 200 && my >= y && my <= y + 16;
            ctx.fill(x, y, x + 200, y + 16, h ? 0xFF333333 : 0xFF151515);
            ctx.drawTextWithShadow(textRenderer, text + ": " + (val ? "§aON" : "§cOFF"), x + 5, y + 4, -1);
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 100;
            if (mx >= x && mx <= x + 200) {
                if (my >= height/2 + 5 && my <= height/2 + 21) sticky = !sticky;
                if (my >= height/2 + 25 && my <= height/2 + 41) antiVelocity = !antiVelocity;
                if (my >= height/2 + 45 && my <= height/2 + 61) autoRun = !autoRun;
                saveConfig();
            }
            return super.mouseClicked(mx, my, b);
        }

        @Override public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(fRange.getText());
                    kaWallsRange = Double.parseDouble(fWalls.getText());
                    shakeIntensity = Float.parseFloat(fShake.getText());
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // НАСТРОЙКИ TRIGGER BOT
    public static class TriggerSettings extends Screen {
        private final Screen parent;
        public TriggerSettings(Screen p) { super(Text.literal("TB")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(width/2 - 80, height/2 - 30, width/2 + 80, height/2 + 30, 0xEE050505);
            boolean h = mx >= width/2 - 70 && mx <= width/2 + 70 && my >= height/2 - 5 && my <= height/2 + 10;
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), width/2, height/2, h ? 0xFF00AAFF : -1);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (mx >= width/2 - 70 && mx <= width/2 + 70 && my >= height/2 - 5 && my <= height/2 + 10) {
                tbCrits = !tbCrits; saveConfig(); return true;
            }
            return false;
        }
        @Override public boolean keyPressed(int k, int s, int m) { if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(parent); return true; } return false; }
    }

    // НАСТРОЙКИ WAYPOINT
    public static class WaypointSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget fx, fy, fz;
        public WaypointSettings(Screen p) { super(Text.literal("WP")); this.parent = p; }
        @Override protected void init() {
            fx = new TextFieldWidget(textRenderer, width/2 + 10, height/2 - 40, 60, 14, Text.literal(""));
            fy = new TextFieldWidget(textRenderer, width/2 + 10, height/2 - 20, 60, 14, Text.literal(""));
            fz = new TextFieldWidget(textRenderer, width/2 + 10, height/2, 60, 14, Text.literal(""));
            fx.setText(String.valueOf((int)wpX)); fy.setText(String.valueOf((int)wpY)); fz.setText(String.valueOf((int)wpZ));
            addDrawableChild(fx); addDrawableChild(fy); addDrawableChild(fz);
        }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(width/2 - 90, height/2 - 70, width/2 + 90, height/2 + 50, 0xEE050505);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bВЕЙПОИНТ (КООРДИНАТЫ)", width/2, height/2 - 60, -1);
            ctx.drawTextWithShadow(textRenderer, "X:", width/2 - 80, height/2 - 37, -1);
            ctx.drawTextWithShadow(textRenderer, "Y:", width/2 - 80, height/2 - 17, -1);
            ctx.drawTextWithShadow(textRenderer, "Z:", width/2 - 80, height/2 + 3, -1);
            super.render(ctx, mx, my, d);
        }
        @Override public boolean keyPressed(int k, int s, int n) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX = Double.parseDouble(fx.getText()); wpY = Double.parseDouble(fy.getText()); wpZ = Double.parseDouble(fz.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, n);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + sticky + ":" + antiVelocity + ":" + autoRun + ":" + tbCrits + ":" + shakeIntensity);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 15) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                wpX = Double.parseDouble(p[2]); wpY = Double.parseDouble(p[3]); wpZ = Double.parseDouble(p[4]);
                keyKA = Integer.parseInt(p[5]); keyTB = Integer.parseInt(p[6]); keyFB = Integer.parseInt(p[7]);
                keyAT = Integer.parseInt(p[8]); keyWP = Integer.parseInt(p[9]);
                sticky = Boolean.parseBoolean(p[10]); antiVelocity = Boolean.parseBoolean(p[11]);
                autoRun = Boolean.parseBoolean(p[12]); tbCrits = Boolean.parseBoolean(p[13]);
                shakeIntensity = Float.parseFloat(p[14]);
            }
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k == 0) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }
}

