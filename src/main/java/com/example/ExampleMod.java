package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean esp = true;
    
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    public static int menuKey = GLFW.GLFW_KEY_0;

    public static String bindingFor = ""; 
    private static final boolean[] keyStates = new boolean[512];
    private boolean menuPressed = false;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();
            
            boolean menuDown = InputUtil.isKeyPressed(handle, menuKey);
            if (menuDown && !menuPressed) client.setScreen(new BubbleMenu());
            menuPressed = menuDown;

            if (client.currentScreen == null) {
                if (isPressed(handle, killauraKey)) killaura = !killaura;
                if (isPressed(handle, triggerbotKey)) triggerbot = !triggerbot;
            }

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        WorldRenderEvents.LAST.register(context -> {
            if (!esp) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            for (PlayerEntity entity : client.world.getPlayers()) {
                if (entity == client.player || !entity.isAlive() || entity.isInvisible()) continue;
                
                MatrixStack matrices = context.matrixStack();
                Vec3d camPos = context.camera().getPos();
                VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getLines());

                matrices.push();
                double x = entity.prevX + (entity.getX() - entity.prevX) * context.tickCounter().getTickDelta(true) - camPos.x;
                double y = entity.prevY + (entity.getY() - entity.prevY) * context.tickCounter().getTickDelta(true) - camPos.y;
                double z = entity.prevZ + (entity.getZ() - entity.prevZ) * context.tickCounter().getTickDelta(true) - camPos.z;
                matrices.translate(x, y, z);

                Box b = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());
                
                // Рендер бокса (12 линий)
                MatrixStack.Entry entry = matrices.peek();
                float r = 1, g = 1, b1 = 1, a = 1;
                
                // Нижний квадрат
                drawLine(buffer, entry, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, r, g, b1, a);
                drawLine(buffer, entry, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, r, g, b1, a);
                drawLine(buffer, entry, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, r, g, b1, a);
                drawLine(buffer, entry, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, r, g, b1, a);
                // Верхний квадрат
                drawLine(buffer, entry, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, r, g, b1, a);
                drawLine(buffer, entry, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, r, g, b1, a);
                drawLine(buffer, entry, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, b1, a);
                drawLine(buffer, entry, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, r, g, b1, a);
                // Стойки
                drawLine(buffer, entry, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, r, g, b1, a);
                drawLine(buffer, entry, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, r, g, b1, a);
                drawLine(buffer, entry, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, r, g, b1, a);
                drawLine(buffer, entry, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, b1, a);

                matrices.pop();
            }
        });
    }

    private void drawLine(VertexConsumer buffer, MatrixStack.Entry entry, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        buffer.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).normal(entry, 0, 1, 0);
    }

    private boolean isPressed(long handle, int key) {
        if (key < 0 || key >= 512) return false;
        boolean down = InputUtil.isKeyPressed(handle, key);
        if (down && !keyStates[key]) {
            keyStates[key] = true;
            return true;
        }
        if (!down) keyStates[key] = false;
        return false;
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.2) {
                target = p; break;
            }
        }
        if (target != null) {
            if (client.player.input.movementForward > 0) client.player.setSprinting(true);
            double dx = target.getX() - client.player.getX();
            double dz = target.getZ() - client.player.getZ();
            client.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90);
            if (client.player.getAttackCooldownProgress(0) >= 0.92f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult res && res.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width / 2 - 80, y = height / 2 - 50;
            context.fill(x, y, x + 160, y + 100, 0xFF121212);
            context.drawBorder(x, y, 160, 100, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 6, -1);
            drawBtn(context, x + 10, y + 25, "Killaura", killaura, killauraKey, "ka", mouseX, mouseY);
            drawBtn(context, x + 10, y + 50, "TriggerBot", triggerbot, triggerbotKey, "tb", mouseX, mouseY);
            drawBtn(context, x + 10, y + 75, "ESP", esp, -1, "esp", mouseX, mouseY);
        }

        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int key, String id, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kName = key == -1 ? "" : (bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0));
            if (kName == null) kName = "KEY_" + key;
            context.fill(x, y, x + 140, y + 16, hover ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + (key == -1 ? "" : " §7[" + kName.toUpperCase() + "]"), x + 15, y + 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2 - 80, y = height / 2 - 50;
            if (isOver(mouseX, mouseY, x + 10, y + 25)) { if (button == 0) killaura = !killaura; else if (button == 1) bindingFor = "ka"; }
            if (isOver(mouseX, mouseY, x + 10, y + 50)) { if (button == 0) triggerbot = !triggerbot; else if (button == 1) bindingFor = "tb"; }
            if (isOver(mouseX, mouseY, x + 10, y + 75)) { if (button == 0) esp = !esp; }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingFor.isEmpty() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                if (bindingFor.equals("ka")) killauraKey = keyCode;
                if (bindingFor.equals("tb")) triggerbotKey = keyCode;
                bindingFor = ""; return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        private boolean isOver(double mx, double my, int x, int y) { return mx >= x && mx <= x + 140 && my >= y && my <= y + 16; }
        @Override public boolean shouldPause() { return false; }
    }
}
