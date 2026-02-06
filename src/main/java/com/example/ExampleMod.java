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
import java.util.List;

public class ExampleMod implements ModInitializer {
    // Состояния функций
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false, autoTotem = true, noFire = true, viewModelActive = true;
    
    // Параметры функций
    public static double kaRange = 3.8, kaWallsRange = 3.0, wpX = 0, wpY = 64, wpZ = 0;
    public static float handX = 0.0f, handY = 0.0f, handZ = 0.0f;

    // Клавиши
    public static int menuKey = GLFW.GLFW_KEY_0;
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long h = client.getWindow().getHandle();
            
            // Меню
            if (isPressed(h, menuKey) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // FullBright
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }

            // Auto-Totem
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

            // NoFire (ИСПРАВЛЕНО ДЛЯ 1.21.x)
            if (noFire && client.player.isOnFire()) {
                client.player.clearFireTicks();
            }

            // KillAura
            if (killaura) {
                runKillaura(client);
            }

            // TriggerBot
            if (triggerbot && !killaura) {
                runTriggerbot(client);
            }
        });

        // 3D Waypoint
        WorldRenderEvents.LAST.register(context -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (dist > 500) return;

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(wpX - context.camera().getPos().x, (wpY - context.camera().getPos().y) + 1.5, wpZ - context.camera().getPos().z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            
            float scale = (float) Math.max(0.02, dist * 0.012);
            matrices.scale(-scale, -scale, scale);

            VertexConsumerProvider consumers = context.consumers();
            if (consumers != null) {
                Matrix4f posMat = matrices.peek().getPositionMatrix();
                String text = "§b[!] ЦЕЛЬ §f(" + (int)dist + "m)";
                client.textRenderer.draw(text, -client.textRenderer.getWidth(text)/2f, 0, -1, false, posMat, consumers, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            matrices.pop();
        });

        // HUD Навигатор
        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (dist > 10) {
                String info = String.format("§bTarget: §f[%.0f, %.0f, %.0f] §e(%.0f m)", wpX, wpY, wpZ, dist);
                drawContext.drawCenteredTextWithShadow(client.textRenderer, info, drawContext.getScaledWindowWidth() / 2, 10, -1);
            }
        });
    }

    private void runKillaura(MinecraftClient client) {
        for (PlayerEntity target : client.world.getPlayers()) {
            if (target != client.player && target.isAlive()) {
                double distance = client.player.distanceTo(target);
                double reach = client.player.canSee(target) ? kaRange : kaWallsRange;
                
                if (distance <= reach) {
                    if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                        break;
                    }
                }
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult res && res.getEntity() instanceof PlayerEntity target) {
            if (target.isAlive() && client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x85000000);
            int x = width / 2 - 85, y = height / 2 - 100;
            ctx.fill(x, y, x + 170, y + 190, 0xFF101010);
            ctx.drawBorder(x, y, 170, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 8, -1);
            drawBtn(ctx, x + 10, y + 30, "KillAura", killaura, mx, my);
            drawBtn(ctx, x + 10, y + 50, "TriggerBot", triggerbot, mx, my);
            drawBtn(ctx, x + 10, y + 70, "FullBright", fullbright, mx, my);
            drawBtn(ctx, x + 10, y + 90, "AutoTotem", autoTotem, mx, my);
            drawBtn(ctx, x + 10, y + 110, "NoFire", noFire, mx, my);
            drawBtn(ctx, x + 10, y + 130, "Waypoint", waypointActive, mx, my);
            drawBtn(ctx, x + 10, y + 150, "ViewModel", viewModelActive, mx, my);
            ctx.drawCenteredTextWithShadow(textRenderer, "§7[ Настроить модули ]", width / 2, y + 175, -1);
        }
        private void drawBtn(DrawContext ctx, int x, int y, String name, boolean enabled, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 150 && my >= y && my <= y + 16;
            ctx.fill(x, y, x + 150, y + 16, hover ? 0xFF202020 : 0xFF151515);
            ctx.drawTextWithShadow(textRenderer, name, x + 10, y + 4, enabled ? 0xFF00FF00 : 0xFFFF3333);
            ctx.drawTextWithShadow(textRenderer, "§b⚙", x + 135, y + 4, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int x = width / 2 - 85, y = height / 2 - 100;
            if (mx >= x + 130 && mx <= x + 155) {
                if (my >= y + 30 && my <= y + 46) client.setScreen(new ConfigScreen(this, "KA"));
                if (my >= y + 130 && my <= y + 146) client.setScreen(new ConfigScreen(this, "WP"));
                if (my >= y + 150 && my <= y + 166) client.setScreen(new ConfigScreen(this, "VM"));
            } else if (mx >= x + 10 && mx <= x + 130) {
                if (my >= y + 30 && my <= y + 46) killaura = !killaura;
                if (my >= y + 50 && my <= y + 66) triggerbot = !triggerbot;
                if (my >= y + 70 && my <= y + 86) fullbright = !fullbright;
                if (my >= y + 90 && my <= y + 106) autoTotem = !autoTotem;
                if (my >= y + 110 && my <= y + 126) noFire = !noFire;
                if (my >= y + 130 && my <= y + 146) waypointActive = !waypointActive;
                if (my >= y + 150 && my <= y + 166) viewModelActive = !viewModelActive;
            }
            saveConfig();
            return super.mouseClicked(mx, my, button);
        }
    }

    public static class ConfigScreen extends Screen {
        private final Screen parent; private final String type; private TextFieldWidget f1, f2, f3;
        public ConfigScreen(Screen parent, String type) { super(Text.literal("S")); this.parent = parent; this.type = type; }
        @Override
        protected void init() {
            f1 = new TextFieldWidget(textRenderer, width/2 - 40, height/2 - 30, 80, 14, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, width/2 - 40, height/2 - 10, 80, 14, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, width/2 - 40, height/2 + 10, 80, 14, Text.literal(""));
            if (type.equals("VM")) { f1.setText(String.valueOf(handX)); f2.setText(String.valueOf(handY)); f3.setText(String.valueOf(handZ)); }
            if (type.equals("WP")) { f1.setText(String.valueOf(wpX)); f2.setText(String.valueOf(wpY)); f3.setText(String.valueOf(wpZ)); }
            if (type.equals("KA")) { f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange)); f3.setVisible(false); }
            addSelectableChild(f1); addSelectableChild(f2); addSelectableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0xCC000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "Settings: " + type, width/2, height/2 - 50, 0xFF00AAFF);
            f1.render(ctx, mx, my, delta); f2.render(ctx, mx, my, delta);
            if (f3.isVisible()) f3.render(ctx, mx, my, delta);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    if (type.equals("VM")) { handX = Float.parseFloat(f1.getText()); handY = Float.parseFloat(f2.getText()); handZ = Float.parseFloat(f3.getText()); }
                    if (type.equals("WP")) { wpX = Double.parseDouble(f1.getText()); wpY = Double.parseDouble(f2.getText()); wpZ = Double.parseDouble(f3.getText()); }
                    if (type.equals("KA")) { kaRange = Double.parseDouble(f1.getText()); kaWallsRange = Double.parseDouble(f2.getText()); }
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    private boolean isPressed(long h, int k) {
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false; return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println("kaRange:" + kaRange + "\nkaWalls:" + kaWallsRange + "\nwpX:" + wpX + "\nwpY:" + wpY + "\nwpZ:" + wpZ + "\nhandX:" + handX + "\nhandY:" + handY + "\nhandZ:" + handZ);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            for (String l : lines) {
                String[] p = l.split(":");
                if (p[0].equals("kaRange")) kaRange = Double.parseDouble(p[1]);
                if (p[0].equals("wpX")) wpX = Double.parseDouble(p[1]);
                if (p[0].equals("handX")) handX = Float.parseFloat(p[1]);
            }
        } catch (Exception ignored) {}
    }
}

