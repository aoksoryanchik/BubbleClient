package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExampleMod implements ModInitializer {
    // --- СОСТОЯНИЯ МОДУЛЕЙ ---
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean waypointActive = false;
    public static boolean autoTotem = true;
    public static boolean noFire = true;
    public static boolean viewModelActive = true;
    public static boolean espPlayers = true;

    // --- НАСТРОЙКИ ---
    public static double kaRange = 3.8;
    public static double kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float handX = 0.0f, handY = 0.0f, handZ = 0.0f;
    public static int menuKey = GLFW.GLFW_KEY_0;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        // Основной цикл тиков клиента
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            long handle = client.getWindow().getHandle();
            
            // Обработка открытия меню
            if (isPressed(handle, menuKey) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Модуль: FullBright (Бесконечное ночное зрение)
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else if (client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                // Если выключили, можно оставить как есть или снять эффект
            }

            // Модуль: Auto-Totem (Автоматическая перестановка тотема)
            if (autoTotem && client.player.getHealth() <= 4.0f) {
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

            // Модуль: NoFire (Убирает визуальный огонь и горение)
            if (noFire && client.player.isOnFire()) {
                client.player.setFireTicks(0);
            }

            // Модуль: KillAura
            if (killaura) {
                runKillaura(client);
            }

            // Модуль: TriggerBot
            if (triggerbot && !killaura) {
                runTriggerbot(client);
            }
        });

        // Рендер в мире (3D Waypoint)
        WorldRenderEvents.LAST.register(context -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double distance = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (distance > 1000) return; // Слишком далеко

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            
            // Расчет позиции относительно камеры
            double renderX = wpX - context.camera().getPos().x;
            double renderY = (wpY - context.camera().getPos().y) + 1.2;
            double renderZ = wpZ - context.camera().getPos().z;

            matrices.translate(renderX, renderY, renderZ);
            
            // Поворот текста к игроку (Billboard эффект)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            
            // Масштабирование в зависимости от дистанции
            float scale = (float) Math.max(0.015, distance * 0.01);
            matrices.scale(-scale, -scale, scale);

            VertexConsumerProvider consumers = context.consumers();
            if (consumers != null) {
                String text = String.format("§b[ TARGET ] §f%.0fm", distance);
                Matrix4f modelViewMatrix = matrices.peek().getPositionMatrix();
                client.textRenderer.draw(text, -client.textRenderer.getWidth(text) / 2f, 0, -1, false, modelViewMatrix, consumers, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            matrices.pop();
        });

        // Рендер на экране (HUD Navigator)
        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            String info = String.format("§bEvent: §f%.1f, %.1f, %.1f §7(§e%d m§7)", wpX, wpY, wpZ, (int)dist);
            drawContext.drawCenteredTextWithShadow(client.textRenderer, info, drawContext.getScaledWindowWidth() / 2, 5, -1);
        });
    }

    private void runKillaura(MinecraftClient client) {
        for (PlayerEntity target : client.world.getPlayers()) {
            if (target == client.player || !target.isAlive() || target.isInvisible()) continue;
            
            double dist = client.player.distanceTo(target);
            double reach = client.player.canSee(target) ? kaRange : kaWallsRange;

            if (dist <= reach) {
                if (client.player.getAttackCooldownProgress(0) >= 0.92f) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                    break; 
                }
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity target) {
            if (target.isAlive() && client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // --- КЛАССЫ ИНТЕРФЕЙСА ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble Menu")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x80000000); // Полупрозрачный фон
            
            int x = width / 2 - 90, y = height / 2 - 110;
            ctx.fill(x, y, x + 180, y + 210, 0xFF0F0F0F); // Основное окно
            ctx.drawBorder(x, y, 180, 210, 0xFF00AAFF);
            
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT v4.2", width / 2, y + 10, -1);
            
            // Список модулей
            renderModule(ctx, x + 10, y + 35, "KillAura", killaura, mx, my, 0);
            renderModule(ctx, x + 10, y + 55, "TriggerBot", triggerbot, mx, my, 1);
            renderModule(ctx, x + 10, y + 75, "FullBright", fullbright, mx, my, 2);
            renderModule(ctx, x + 10, y + 95, "AutoTotem", autoTotem, mx, my, 3);
            renderModule(ctx, x + 10, y + 115, "NoFire", noFire, mx, my, 4);
            renderModule(ctx, x + 10, y + 135, "Waypoint", waypointActive, mx, my, 5);
            renderModule(ctx, x + 10, y + 155, "Hands Mod", viewModelActive, mx, my, 6);

            ctx.drawCenteredTextWithShadow(textRenderer, "§7Press §f[ESC] §7to close", width / 2, y + 190, 0xFFAAAAAA);
        }

        private void renderModule(DrawContext ctx, int x, int y, String name, boolean state, int mx, int my, int id) {
            boolean hover = mx >= x && mx <= x + 160 && my >= y && my <= y + 18;
            ctx.fill(x, y, x + 160, y + 18, hover ? 0xFF252525 : 0xFF181818);
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + 5, state ? 0xFF55FF55 : 0xFFFF5555);
            ctx.drawTextWithShadow(textRenderer, "§b[SETTINGS]", x + 105, y + 5, hover ? -1 : 0xFF777777);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int x = width / 2 - 90, y = height / 2 - 110;
            
            // Обработка кликов
            if (mx >= x + 110 && mx <= x + 170) { // Клики по [SETTINGS]
                if (my >= y + 35 && my <= y + 53) client.setScreen(new ConfigScreen(this, "KA"));
                if (my >= y + 135 && my <= y + 153) client.setScreen(new ConfigScreen(this, "WP"));
                if (my >= y + 155 && my <= y + 173) client.setScreen(new ConfigScreen(this, "VM"));
            } else if (mx >= x + 10 && mx <= x + 110) { // Клики по названию (Toggle)
                if (my >= y + 35 && my <= y + 53) killaura = !killaura;
                if (my >= y + 55 && my <= y + 73) triggerbot = !triggerbot;
                if (my >= y + 75 && my <= y + 93) fullbright = !fullbright;
                if (my >= y + 95 && my <= y + 113) autoTotem = !autoTotem;
                if (my >= y + 115 && my <= y + 133) noFire = !noFire;
                if (my >= y + 135 && my <= y + 153) waypointActive = !waypointActive;
                if (my >= y + 155 && my <= y + 173) viewModelActive = !viewModelActive;
            }
            saveConfig();
            return super.mouseClicked(mx, my, button);
        }
    }

    public static class ConfigScreen extends Screen {
        private final Screen parent;
        private final String mod;
        private TextFieldWidget edit1, edit2, edit3;

        public ConfigScreen(Screen parent, String mod) { 
            super(Text.literal("Config")); 
            this.parent = parent; 
            this.mod = mod; 
        }

        @Override
        protected void init() {
            edit1 = new TextFieldWidget(textRenderer, width/2 - 50, height/2 - 40, 100, 16, Text.literal(""));
            edit2 = new TextFieldWidget(textRenderer, width/2 - 50, height/2 - 10, 100, 16, Text.literal(""));
            edit3 = new TextFieldWidget(textRenderer, width/2 - 50, height/2 + 20, 100, 16, Text.literal(""));

            if (mod.equals("KA")) { edit1.setText(String.valueOf(kaRange)); edit2.setText(String.valueOf(kaWallsRange)); edit3.setVisible(false); }
            if (mod.equals("WP")) { edit1.setText(String.valueOf(wpX)); edit2.setText(String.valueOf(wpY)); edit3.setText(String.valueOf(wpZ)); }
            if (mod.equals("VM")) { edit1.setText(String.valueOf(handX)); edit2.setText(String.valueOf(handY)); edit3.setText(String.valueOf(handZ)); }

            addSelectableChild(edit1); addSelectableChild(edit2); addSelectableChild(edit3);
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xF0050505);
            ctx.drawCenteredTextWithShadow(textRenderer, "Editing: " + mod, width/2, height/2 - 65, 0xFF00AAFF);
            edit1.render(ctx, mx, my, d); edit2.render(ctx, mx, my, d);
            if (edit3.isVisible()) edit3.render(ctx, mx, my, d);
            ctx.drawCenteredTextWithShadow(textRenderer, "§7Press ESC to save", width/2, height/2 + 50, -1);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    if (mod.equals("KA")) { kaRange = Double.parseDouble(edit1.getText()); kaWallsRange = Double.parseDouble(edit2.getText()); }
                    if (mod.equals("WP")) { wpX = Double.parseDouble(edit1.getText()); wpY = Double.parseDouble(edit2.getText()); wpZ = Double.parseDouble(edit3.getText()); }
                    if (mod.equals("VM")) { handX = Float.parseFloat(edit1.getText()); handY = Float.parseFloat(edit2.getText()); handZ = Float.parseFloat(edit3.getText()); }
                } catch (Exception ignored) {}
                saveConfig();
                client.setScreen(parent);
                return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // --- УТИЛИТЫ ---

    private boolean isPressed(long handle, int key) {
        boolean isDown = InputUtil.isKeyPressed(handle, key);
        if (isDown && !keyStates[key]) { keyStates[key] = true; return true; }
        if (!isDown) keyStates[key] = false;
        return false;
    }

    public static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("kaRange:" + kaRange);
            writer.println("kaWalls:" + kaWallsRange);
            writer.println("wpX:" + wpX);
            writer.println("wpY:" + wpY);
            writer.println("wpZ:" + wpZ);
            writer.println("handX:" + handX);
            writer.println("handY:" + handY);
            writer.println("handZ:" + handZ);
        } catch (IOException ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length < 2) continue;
                if (parts[0].equals("kaRange")) kaRange = Double.parseDouble(parts[1]);
                if (parts[0].equals("wpX")) wpX = Double.parseDouble(parts[1]);
                if (parts[0].equals("handX")) handX = Float.parseFloat(parts[1]);
                // ... допиши остальные при необходимости
            }
        } catch (Exception ignored) {}
    }
}

