package com.example;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static double kaRange = 3.8, kaWallsRange = 3.0, wpX = 0, wpY = 64, wpZ = 0;
    public static boolean kaAutoRun = true;
    public static int killauraKey = GLFW.GLFW_KEY_P, triggerbotKey = GLFW.GLFW_KEY_R, fbKey = GLFW.GLFW_KEY_M, wpKey = GLFW.GLFW_KEY_V, menuKey = GLFW.GLFW_KEY_0;
    public static String bindingFor = "";
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long h = client.getWindow().getHandle();
            if (isPressed(h, menuKey) && client.currentScreen == null) client.setScreen(new BubbleMenu());
            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) toggle(client, "Killaura", !killaura);
                if (isPressed(h, triggerbotKey)) toggle(client, "Triggerbot", !triggerbot);
                if (isPressed(h, fbKey)) toggle(client, "FullBright", !fullbright);
                if (isPressed(h, wpKey)) toggle(client, "Waypoint", !waypointActive);
            }
            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        // Рендеринг метки В МИРЕ
        WorldRenderEvents.LAST.register(context -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = context.camera().getPos();
            
            double targetX = wpX - camPos.x;
            double targetY = wpY - camPos.y;
            double targetZ = wpZ - camPos.z;
            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));

            matrices.push();
            matrices.translate(targetX, targetY + 1.5, targetZ); // Метка чуть выше земли
            
            // Заставляем текст всегда смотреть на игрока
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            
            float scale = (float) (dist * 0.01); // Чем дальше, тем больше текст, чтобы было видно
            if (scale < 0.02f) scale = 0.02f;
            matrices.scale(-scale, -scale, scale);

            String text = "§b[!] ИВЕНТ §f(" + (int)dist + "m)";
            String coords = "§7" + (int)wpX + " " + (int)wpY + " " + (int)wpZ;
            
            int width = client.textRenderer.getWidth(text);
            int halfWidth = width / 2;

            // Рисуем фон и текст прямо в мире
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            
            // Используем VertexConsumerProvider из контекста
            var consumers = context.consumers();
            if (consumers != null) {
                client.textRenderer.draw(text, -halfWidth, 0, -1, true, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
                client.textRenderer.draw(coords, -client.textRenderer.getWidth(coords) / 2, 10, -1, true, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            
            matrices.pop();
        });
    }

    private void toggle(MinecraftClient c, String n, boolean s) {
        if(n.equals("Killaura")) killaura = s;
        if(n.equals("Triggerbot")) triggerbot = s;
        if(n.equals("FullBright")) fullbright = s;
        if(n.equals("Waypoint")) waypointActive = s;
        saveConfig();
        c.player.sendMessage(Text.literal("§bBubble §8» §f" + n + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    public static void saveConfig() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("kaRange:" + kaRange); writer.println("kaWallsRange:" + kaWallsRange);
            writer.println("kaAutoRun:" + kaAutoRun);
            writer.println("wpX:" + wpX); writer.println("wpY:" + wpY); writer.println("wpZ:" + wpZ);
            writer.println("killauraKey:" + killauraKey); writer.println("triggerbotKey:" + triggerbotKey);
            writer.println("fbKey:" + fbKey); writer.println("wpKey:" + wpKey);
        } catch (IOException ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            for (String line : lines) {
                String[] p = line.split(":");
                if (p[0].equals("kaRange")) kaRange = Double.parseDouble(p[1]);
                if (p[0].equals("kaWallsRange")) kaWallsRange = Double.parseDouble(p[1]);
                if (p[0].equals("kaAutoRun")) kaAutoRun = Boolean.parseBoolean(p[1]);
                if (p[0].equals("wpX")) wpX = Double.parseDouble(p[1]);
                if (p[0].equals("wpY")) wpY = Double.parseDouble(p[1]);
                if (p[0].equals("wpZ")) wpZ = Double.parseDouble(p[1]);
                if (p[0].equals("killauraKey")) killauraKey = Integer.parseInt(p[1]);
                if (p[0].equals("triggerbotKey")) triggerbotKey = Integer.parseInt(p[1]);
                if (p[0].equals("fbKey")) fbKey = Integer.parseInt(p[1]);
                if (p[0].equals("wpKey")) wpKey = Integer.parseInt(p[1]);
            }
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k <= 0 || k >= 512) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false;
        return false;
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive()) {
                double dist = client.player.distanceTo(p);
                boolean canSee = client.player.canSee(p);
                if (dist <= (canSee ? kaRange : kaWallsRange)) { target = p; break; }
            }
        }
        if (target != null) {
            if (kaAutoRun && client.player.input.movementForward > 0) client.player.setSprinting(true);
            client.player.setYaw((float) Math.toDegrees(Math.atan2(target.getZ() - client.player.getZ(), target.getX() - client.player.getX())) - 90);
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult res && res.getEntity() instanceof PlayerEntity) {
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, res.getEntity());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // --- ГЛАВНОЕ МЕНЮ ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 60;
            context.fill(x, y, x + 160, y + 120, 0xFF121212);
            context.drawBorder(x, y, 160, 120, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            drawBtn(context, x+10, y+25, "KillAura", killaura, killauraKey, "ka", mx, my, true);
            drawBtn(context, x+10, y+50, "TriggerBot", triggerbot, triggerbotKey, "tb", mx, my, false);
            drawBtn(context, x+10, y+75, "FullBright", fullbright, fbKey, "fb", mx, my, false);
            drawBtn(context, x+10, y+100, "Waypoint", waypointActive, wpKey, "wp", mx, my, true);
        }
        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int key, String id, int mx, int my, boolean settings) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kName = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0);
            if (kName == null) kName = "K_" + key;
            context.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + kName.toUpperCase() + "]", x + 15, y + 4, -1);
            if (settings) {
                boolean hSet = mx >= x + 125 && mx <= x + 140 && my >= y && my <= y + 16;
                context.drawTextWithShadow(textRenderer, hSet ? "§f⚙" : "§b⚙", x + 130, y + 4, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width/2 - 80, y = height/2 - 60;
            if (mx >= x + 125 && mx <= x + 150) {
                if (my >= y + 25 && my <= y + 41 && btn == 1) { client.setScreen(new KillAuraSettings(this)); return true; }
                if (my >= y + 100 && my <= y + 116 && btn == 1) { client.setScreen(new WaypointSettings(this)); return true; }
            }
            if (mx >= x + 10 && mx <= x + 125) {
                if (my >= y + 25 && my <= y + 41) handle(btn, "ka");
                if (my >= y + 50 && my <= y + 66) handle(btn, "tb");
                if (my >= y + 75 && my <= y + 91) handle(btn, "fb");
                if (my >= y + 100 && my <= y + 116) handle(btn, "wp");
            }
            return super.mouseClicked(mx, my, btn);
        }
        private void handle(int btn, String id) {
            if (btn == 0) {
                if(id.equals("ka")) killaura = !killaura;
                if(id.equals("tb")) triggerbot = !triggerbot;
                if(id.equals("fb")) fullbright = !fullbright;
                if(id.equals("wp")) waypointActive = !waypointActive;
                saveConfig();
            } else if (btn == 1) bindingFor = id;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (!bindingFor.isEmpty()) {
                if (bindingFor.equals("ka")) killauraKey = k;
                if (bindingFor.equals("tb")) triggerbotKey = k;
                if (bindingFor.equals("fb")) fbKey = k;
                if (bindingFor.equals("wp")) wpKey = k;
                bindingFor = ""; saveConfig(); return true;
            }
            if (k == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
            return super.keyPressed(k, s, m);
        }
    }

    // --- МЕНЮ КООРДИНАТ ---
    public static class WaypointSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget editX, editY, editZ;
        public WaypointSettings(Screen parent) { super(Text.literal("WP Settings")); this.parent = parent; }
        @Override
        protected void init() {
            int x = width/2 - 30;
            editX = new TextFieldWidget(textRenderer, x, height/2 - 30, 60, 12, Text.literal(""));
            editX.setText(String.valueOf(wpX));
            editY = new TextFieldWidget(textRenderer, x, height/2 - 10, 60, 12, Text.literal(""));
            editY.setText(String.valueOf(wpY));
            editZ = new TextFieldWidget(textRenderer, x, height/2 + 10, 60, 12, Text.literal(""));
            editZ.setText(String.valueOf(wpZ));
            addSelectableChild(editX); addSelectableChild(editY); addSelectableChild(editZ);
        }
        @Override
        public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            context.drawCenteredTextWithShadow(textRenderer, "§bКоординаты метки", width/2, height/2 - 50, -1);
            context.drawTextWithShadow(textRenderer, "X:", width/2 - 50, height/2 - 28, -1);
            context.drawTextWithShadow(textRenderer, "Y:", width/2 - 50, height/2 - 8, -1);
            context.drawTextWithShadow(textRenderer, "Z:", width/2 - 50, height/2 + 12, -1);
            editX.render(context, mx, my, delta);
            editY.render(context, mx, my, delta);
            editZ.render(context, mx, my, delta);
            context.drawCenteredTextWithShadow(textRenderer, "§7[ESC] Сохранить и выйти", width/2, height/2 + 40, -1);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX = Double.parseDouble(editX.getText()); wpY = Double.parseDouble(editY.getText()); wpZ = Double.parseDouble(editZ.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // --- МЕНЮ КИЛЛАУРЫ ---
    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rangeField, wallsField;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA Settings")); this.parent = parent; }
        @Override
        protected void init() {
            int x = width/2 - 70;
            rangeField = new TextFieldWidget(textRenderer, x + 80, height/2 - 32, 40, 12, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            wallsField = new TextFieldWidget(textRenderer, x + 80, height/2 - 12, 40, 12, Text.literal(""));
            wallsField.setText(String.valueOf(kaWallsRange));
            addSelectableChild(rangeField); addSelectableChild(wallsField);
        }
        @Override
        public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 60, y = height/2 - 60;
            context.fill(x, y, x + 150, y + 110, 0xFF121212);
            context.drawBorder(x, y, 150, 110, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§bSettings", x + 75, y + 6, -1);
            int px = x - 90;
            context.fill(px, y, px + 85, y + 110, 0xFF121212);
            context.drawBorder(px, y, 85, 110, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§eПод Aresmine", px + 42, y + 15, -1);
            boolean hAres = mx >= px + 10 && mx <= px + 75 && my >= y + 40 && my <= y + 56;
            context.fill(px + 10, y + 40, px + 75, y + 56, hAres ? 0xFF252525 : 0xFF181818);
            context.drawBorder(px + 10, y + 40, 65, 16, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "Поставить", px + 42, y + 44, -1);
            context.drawTextWithShadow(textRenderer, "Range:", x + 10, y + 25, -1);
            context.drawTextWithShadow(textRenderer, "Walls:", x + 10, y + 45, -1);
            context.drawTextWithShadow(textRenderer, "Auto Run:", x + 10, y + 70, -1);
            context.fill(x + 120, y + 68, x + 132, y + 80, 0xFF181818);
            context.drawBorder(x + 120, y + 68, 12, 12, 0xFF00AAFF);
            if (kaAutoRun) context.drawCenteredTextWithShadow(textRenderer, "✔", x + 126, y + 70, 0xFF00FF00);
            rangeField.render(context, mx, my, delta);
            wallsField.render(context, mx, my, delta);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width/2 - 60, y = height/2 - 60;
            int px = x - 90;
            if (mx >= px + 10 && mx <= px + 75 && my >= y + 40 && my <= y + 56) {
                kaRange = 3.8; kaWallsRange = 3.0; kaAutoRun = true;
                rangeField.setText("3.8"); wallsField.setText("3.0"); saveConfig(); return true;
            }
            if (mx >= x + 120 && mx <= x + 132 && my >= y + 68 && my <= y + 80) { kaAutoRun = !kaAutoRun; saveConfig(); return true; }
            rangeField.setFocused(rangeField.isMouseOver(mx, my));
            wallsField.setFocused(wallsField.isMouseOver(mx, my));
            return super.mouseClicked(mx, my, btn);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(parent); return true; }
            boolean r = rangeField.keyPressed(k, s, m) || wallsField.keyPressed(k, s, m);
            updateVals(); return r;
        }
        private void updateVals() {
            try { kaRange = Double.parseDouble(rangeField.getText()); kaWallsRange = Double.parseDouble(wallsField.getText()); saveConfig(); } catch (Exception ignored) {}
        }
    }
}

