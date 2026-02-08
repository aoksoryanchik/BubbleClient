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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import java.util.ArrayList;
import java.util.List;

public class ExampleMod implements ModInitializer {
    // --- ВСЕ ПЕРЕМЕННЫЕ СОСТОЯНИЙ (ВОССТАНОВЛЕНО) ---
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean waypointActive = false;
    public static boolean autoTotem = true;
    public static boolean autoRun = true;
    public static boolean antiVelocity = true;
    public static boolean tbCrits = true;
    public static boolean sticky = true;
    public static boolean invWalk = true;
    public static boolean fastPlace = false;
    public static boolean noSlow = true;
    public static boolean esp = false;
    public static boolean flight = false;

    // --- ПАРАМЕТРЫ НАСТРОЕК ---
    public static double kaRange = 3.8;
    public static double kaWallsRange = 3.0;
    public static float shakeIntensity = 0.5f;
    public static double wpX = 0;
    public static double wpY = 64;
    public static double wpZ = 0;
    private static double smoothAngle = 0;

    // --- КЛАВИШИ И БИНДЫ ---
    public static int keyKA = GLFW.GLFW_KEY_R;
    public static int keyTB = 0;
    public static int keyFB = GLFW.GLFW_KEY_B;
    public static int keyAT = 0;
    public static int keyWP = GLFW.GLFW_KEY_N;
    public static int keyMenu = GLFW.GLFW_KEY_0;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_client_pro_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();

            // Открытие меню
            if (isPressed(handle, keyMenu) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Обработка горячих клавиш в игре
            if (client.currentScreen == null) {
                if (isPressed(handle, keyKA)) { killaura = !killaura; sendNotification("KillAura", killaura); }
                if (isPressed(handle, keyTB)) { triggerbot = !triggerbot; sendNotification("TriggerBot", triggerbot); }
                if (isPressed(handle, keyFB)) { fullbright = !fullbright; sendNotification("FullBright", fullbright); }
                if (isPressed(handle, keyAT)) { autoTotem = !autoTotem; sendNotification("AutoTotem", autoTotem); }
                if (isPressed(handle, keyWP)) { waypointActive = !waypointActive; sendNotification("Waypoint", waypointActive); }
            }

            // Модуль FullBright
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            
            // Модуль AutoTotem (Полный цикл проверки инвентаря)
            if (autoTotem) {
                if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                    for (int i = 0; i < 45; i++) {
                        if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                            int inventorySlot = i < 9 ? i + 36 : i;
                            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, inventorySlot, 45, SlotActionType.SWAP, client.player);
                            break;
                        }
                    }
                }
            }
            
            // Модуль AntiVelocity (С проверкой на препятствия)
            if (antiVelocity && client.player.hurtTime > 0) {
                if (!client.player.isTouchingWater() && !client.player.isInLava() && !client.player.isClimbing()) {
                    Vec3d currentVelocity = client.player.getVelocity();
                    client.player.setVelocity(currentVelocity.x * 0.32, currentVelocity.y, currentVelocity.z * 0.32);
                }
            }

            // Запуск циклов функций
            if (killaura) runKillAura(client);
            if (triggerbot) runTriggerBot(client);
        });

        // HUD Отрисовка навигатора (Стрелка, Дистанция, Координаты)
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double distance = Math.sqrt(client.player.squaredDistanceTo(wpX, wpY, wpZ));
            double targetAngle = Math.toDegrees(Math.atan2(wpZ - client.player.getZ(), wpX - client.player.getX())) - 90;
            double relativeAngle = MathHelper.wrapDegrees(targetAngle - client.player.getYaw());
            
            smoothAngle += (relativeAngle - smoothAngle) * 0.22;
            
            String directionIcon = "↑";
            String colorCode = "§f";
            if (Math.abs(smoothAngle) < 15) { directionIcon = "↑ [ПРЯМО]"; colorCode = "§a"; }
            else if (smoothAngle > 15 && smoothAngle < 165) directionIcon = "→";
            else if (smoothAngle < -15 && smoothAngle > -165) directionIcon = "←";
            else directionIcon = "↓";

            int screenWidth = client.getWindow().getScaledWidth();
            drawContext.drawCenteredTextWithShadow(client.textRenderer, colorCode + directionIcon + String.format(" §b(%.1f m)", distance), screenWidth / 2, 10, -1);
            drawContext.drawCenteredTextWithShadow(client.textRenderer, String.format("§7Цель: X:%.0f Y:%.0f Z:%.0f", wpX, wpY, wpZ), screenWidth / 2, 22, -1);
        });
    }

    private void runKillAura(MinecraftClient client) {
        PlayerEntity targetEntity = null;
        double shortestDistance = Double.MAX_VALUE;

        for (PlayerEntity otherPlayer : client.world.getPlayers()) {
            if (otherPlayer == client.player || !otherPlayer.isAlive() || otherPlayer.isCreative() || otherPlayer.isInvisible()) continue;
            
            double dist = client.player.distanceTo(otherPlayer);
            if (dist <= kaRange && dist < shortestDistance) {
                if (!client.player.canSee(otherPlayer) && dist > kaWallsRange) continue;
                shortestDistance = dist;
                targetEntity = otherPlayer;
            }
        }
        
        if (targetEntity != null) {
            if (autoRun && !client.player.isInsideWaterOrBubbleColumn()) client.player.setSprinting(true);
            
            // Sticky logic (Притяжение к цели)
            if (sticky && targetEntity.hurtTime > 3) {
                Vec3d pullVec = targetEntity.getPos().subtract(client.player.getPos()).normalize().multiply(0.145);
                client.player.addVelocity(pullVec.x, 0, pullVec.z);
            }

            // Ротации
            rotateTowards(client.player, targetEntity.getPos().add(0, targetEntity.getHeight() * 0.72, 0));
            
            // Удар
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, targetEntity);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Shake (Тряска камеры)
                if (shakeIntensity > 0) {
                    client.player.setYaw(client.player.getYaw() + (float)(Math.random() - 0.5) * shakeIntensity);
                    client.player.setPitch(client.player.getPitch() + (float)(Math.random() - 0.5) * shakeIntensity);
                }
            }
        }
    }

    private void runTriggerBot(MinecraftClient client) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0F).multiply(kaRange);
        Vec3d traceEnd = eyePos.add(lookVec);
        Box searchBox = client.player.getBoundingBox().stretch(lookVec).expand(1.0D);
        
        EntityHitResult hitResult = ProjectileUtil.raycast(client.player, eyePos, traceEnd, searchBox, (entity) -> entity instanceof PlayerEntity && entity != client.player, kaRange * kaRange);
        
        if (hitResult != null && hitResult.getEntity() instanceof PlayerEntity target) {
            boolean readyForCrit = !tbCrits || (client.player.fallDistance > 0.0F && !client.player.isOnGround() && !client.player.isClimbing() && !client.player.isTouchingWater());
            if (client.player.getAttackCooldownProgress(0) >= 0.95f && readyForCrit) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void rotateTowards(PlayerEntity player, Vec3d targetPos) {
        Vec3d delta = targetPos.subtract(player.getEyePos());
        double distanceXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, distanceXZ));
        
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(yaw - player.getYaw()));
        player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    private void sendNotification(String module, boolean state) {
        if (MinecraftClient.getInstance().player != null) {
            String status = state ? "§aВКЛ" : "§cВЫКЛ";
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + module + " : " + status), true);
        }
    }

    private boolean isPressed(long windowHandle, int keyCode) {
        if (keyCode == 0) return false;
        boolean currentlyPressed = InputUtil.isKeyPressed(windowHandle, keyCode);
        if (currentlyPressed && !keyStates[keyCode]) {
            keyStates[keyCode] = true;
            return true;
        }
        if (!currentlyPressed) keyStates[keyCode] = false;
        return false;
    }

    // --- ОСНОВНОЕ GUI МЕНЮ ---
    public static class BubbleMenu extends Screen {
        private int bindingModuleIndex = -1;
        public BubbleMenu() { super(Text.literal("Bubble")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int startX = width / 2 - 100;
            int startY = height / 2 - 90;
            
            // Фон и рамка
            context.fill(startX, startY, startX + 200, startY + 180, 0xEE050505); 
            context.drawBorder(startX, startY, 200, 180, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE PRO §7v3.0", width / 2, startY + 10, -1);
            
            String[] moduleNames = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "Flight", "ESP"};
            boolean[] moduleStates = {killaura, triggerbot, fullbright, autoTotem, waypointActive, flight, esp};
            int[] moduleKeys = {keyKA, keyTB, keyFB, keyAT, keyWP, 0, 0};

            for(int i = 0; i < moduleNames.length; i++) {
                int itemY = startY + 35 + i * 20;
                boolean isHovered = mouseX >= startX + 10 && mouseX <= startX + 190 && mouseY >= itemY && mouseY <= itemY + 16;
                
                context.fill(startX + 10, itemY, startX + 190, itemY + 16, isHovered ? 0xFF282828 : 0xFF121212);
                
                String keyText = bindingModuleIndex == i ? "§b[???]" : (moduleKeys[i] != 0 ? " §7[" + GLFW.glfwGetKeyName(moduleKeys[i], 0).toUpperCase() + "]" : "");
                context.drawTextWithShadow(textRenderer, moduleNames[i] + keyText, startX + 15, itemY + 4, moduleStates[i] ? 0xFF00FF00 : 0xFFFF4444);
                
                if(i == 0 || i == 1 || i == 4) {
                    context.drawTextWithShadow(textRenderer, "⚙", startX + 175, itemY + 4, -1);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int startX = width / 2 - 100;
            int startY = height / 2 - 90;

            for(int i = 0; i < 7; i++) {
                int itemY = startY + 35 + i * 20;
                if(mouseX >= startX + 10 && mouseX <= startX + 190 && mouseY >= itemY && mouseY <= itemY + 16) {
                    if (button == 1) { bindingModuleIndex = i; return true; } // ПКМ для бинда
                    
                    if(mouseX >= startX + 170 && (i == 0 || i == 1 || i == 4)) {
                        if(i == 0) client.setScreen(new KillAuraSettings(this));
                        if(i == 1) client.setScreen(new TriggerSettings(this));
                        if(i == 4) client.setScreen(new WaypointSettings(this));
                    } else {
                        if(i == 0) killaura = !killaura;
                        if(i == 1) triggerbot = !triggerbot;
                        if(i == 2) fullbright = !fullbright;
                        if(i == 3) autoTotem = !autoTotem;
                        if(i == 4) waypointActive = !waypointActive;
                        if(i == 5) flight = !flight;
                        if(i == 6) esp = !esp;
                        saveConfig();
                    }
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (bindingModuleIndex != -1) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) keyCode = 0;
                if (bindingModuleIndex == 0) keyKA = keyCode;
                if (bindingModuleIndex == 1) keyTB = keyCode;
                if (bindingModuleIndex == 2) keyFB = keyCode;
                if (bindingModuleIndex == 3) keyAT = keyCode;
                if (bindingModuleIndex == 4) keyWP = keyCode;
                bindingModuleIndex = -1;
                saveConfig();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    // --- ПОДМЕНЮ НАСТРОЕК (ПОЛНЫЕ) ---
    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget fRange, fWalls, fShake;

        public KillAuraSettings(Screen parent) { super(Text.literal("")); this.parent = parent; }

        @Override
        protected void init() {
            fRange = new TextFieldWidget(textRenderer, width / 2 + 30, height / 2 - 70, 45, 14, Text.literal(""));
            fWalls = new TextFieldWidget(textRenderer, width / 2 + 30, height / 2 - 50, 45, 14, Text.literal(""));
            fShake = new TextFieldWidget(textRenderer, width / 2 + 30, height / 2 - 30, 45, 14, Text.literal(""));
            fRange.setText("" + kaRange);
            fWalls.setText("" + kaWallsRange);
            fShake.setText("" + shakeIntensity);
            addDrawableChild(fRange); addDrawableChild(fWalls); addDrawableChild(fShake);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(width / 2 - 110, height / 2 - 100, width / 2 + 110, height / 2 + 90, 0xEE050505);
            context.drawTextWithShadow(textRenderer, "Дистанция:", width / 2 - 100, height / 2 - 67, -1);
            context.drawTextWithShadow(textRenderer, "Сквозь стены:", width / 2 - 100, height / 2 - 47, -1);
            context.drawTextWithShadow(textRenderer, "Тряска:", width / 2 - 100, height / 2 - 27, -1);
            
            drawToggleButton(context, "Sticky (Липкая)", sticky, height / 2 - 5, mouseX, mouseY);
            drawToggleButton(context, "AntiVelocity", antiVelocity, height / 2 + 15, mouseX, mouseY);
            drawToggleButton(context, "AutoRun (Бег)", autoRun, height / 2 + 35, mouseX, mouseY);
            drawToggleButton(context, "NoSlow (Без замедл.)", noSlow, height / 2 + 55, mouseX, mouseY);
            
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawToggleButton(DrawContext context, String text, boolean val, int y, int mx, int my) {
            boolean hovered = mx >= width / 2 - 100 && mx <= width / 2 + 100 && my >= y && my <= y + 16;
            context.fill(width / 2 - 100, y, width / 2 + 100, y + 16, hovered ? 0xFF353535 : 0xFF181818);
            context.drawTextWithShadow(textRenderer, text + ": " + (val ? "§aВКЛ" : "§cВЫКЛ"), width / 2 - 95, y + 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= width / 2 - 100 && mouseX <= width / 2 + 100) {
                if (mouseY >= height / 2 - 5 && mouseY <= height / 2 + 11) sticky = !sticky;
                if (mouseY >= height / 2 + 15 && mouseY <= height/2 + 31) antiVelocity = !antiVelocity;
                if (mouseY >= height / 2 + 35 && mouseY <= height / 2 + 51) autoRun = !autoRun;
                if (mouseY >= height / 2 + 55 && mouseY <= height / 2 + 71) noSlow = !noSlow;
                saveConfig(); return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(fRange.getText());
                    kaWallsRange = Double.parseDouble(fWalls.getText());
                    shakeIntensity = Float.parseFloat(fShake.getText());
                } catch(Exception ignored){}
                saveConfig();
                client.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen parent;
        public TriggerSettings(Screen parent) { super(Text.literal("")); this.parent = parent; }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(width / 2 - 80, height / 2 - 30, width / 2 + 80, height / 2 + 30, 0xEE050505);
            boolean h = mouseX >= width / 2 - 75 && mouseX <= width / 2 + 75 && mouseY >= height / 2 - 12 && mouseY <= height / 2 + 12;
            context.fill(width / 2 - 75, height / 2 - 12, width / 2 + 75, height / 2 + 12, h ? 0xFF303030 : 0xFF151515);
            context.drawCenteredTextWithShadow(textRenderer, "Только криты: " + (tbCrits ? "§aДА" : "§cНЕТ"), width / 2, height / 2 - 4, -1);
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= width / 2 - 75 && mouseX <= width / 2 + 75 && mouseY >= height / 2 - 12 && mouseY <= height / 2 + 12) {
                tbCrits = !tbCrits; saveConfig(); return true;
            }
            return false;
        }
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(parent); return true; }
            return false;
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget fx, fy, fz;
        public WaypointSettings(Screen parent) { super(Text.literal("")); this.parent = parent; }
        @Override
        protected void init() {
            fx = new TextFieldWidget(textRenderer, width / 2 + 10, height / 2 - 45, 65, 14, Text.literal(""));
            fy = new TextFieldWidget(textRenderer, width / 2 + 10, height / 2 - 25, 65, 14, Text.literal(""));
            fz = new TextFieldWidget(textRenderer, width / 2 + 10, height / 2 - 5, 65, 14, Text.literal(""));
            fx.setText("" + (int)wpX); fy.setText("" + (int)wpY); fz.setText("" + (int)wpZ);
            addDrawableChild(fx); addDrawableChild(fy); addDrawableChild(fz);
        }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(width / 2 - 100, height / 2 - 80, width / 2 + 100, height / 2 + 60, 0xEE050505);
            context.drawCenteredTextWithShadow(textRenderer, "§bКООРДИНАТЫ ЦЕЛИ", width / 2, height / 2 - 70, -1);
            context.drawTextWithShadow(textRenderer, "Координата X:", width / 2 - 90, height / 2 - 42, -1);
            context.drawTextWithShadow(textRenderer, "Координата Y:", width / 2 - 90, height / 2 - 22, -1);
            context.drawTextWithShadow(textRenderer, "Координата Z:", width / 2 - 90, height / 2 - 2, -1);
            super.render(context, mouseX, mouseY, delta);
        }
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    wpX = Double.parseDouble(fx.getText());
                    wpY = Double.parseDouble(fy.getText());
                    wpZ = Double.parseDouble(fz.getText());
                } catch(Exception ignored){}
                saveConfig();
                client.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + sticky + ":" + antiVelocity + ":" + autoRun + ":" + tbCrits + ":" + shakeIntensity + ":" + invWalk + ":" + fastPlace + ":" + noSlow);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            if (lines.isEmpty()) return;
            String[] p = lines.get(0).split(":");
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
}
