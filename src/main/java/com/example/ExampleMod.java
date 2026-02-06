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
                
                // Рендерим линии бокса вручную, чтобы не было ошибок компиляции
                renderBoxOutline(matrices, buffer, b, 1f, 1f, 1f, 1f);
                
                matrices.pop();
            }
        });
    }

    private void renderBoxOutline(MatrixStack matrices, VertexConsumer buffer, Box b, float r, float g, float b1, float a) {
        MatrixStack.Entry entry = matrices.peek();
        buffer.vertex(entry, (float)b.minX, (float)b.minY, (float)b.minZ).color(r, g, b1, a).normal(entry, 1, 0, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.minY, (float)b.minZ).color(r, g, b1, a).normal(entry, 1, 0, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.minY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 0, 1);
        buffer.vertex(entry, (float)b.minX, (float)b.minY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 0, 1);
        buffer.vertex(entry, (float)b.maxX, (float)b.minY, (float)b.maxZ).color(r, g, b1, a).normal(entry, -1, 0, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.minY, (float)b.maxZ).color(r, g, b1, a).normal(entry, -1, 0, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.minY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 0, -1);
        buffer.vertex(entry, (float)b.maxX, (float)b.minY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 0, -1);
        buffer.vertex(entry, (float)b.minX, (float)b.maxY, (float)b.minZ).color(r, g, b1, a).normal(entry, 1, 0, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.maxY, (float)b.minZ).color(r, g, b1, a).normal(entry, 1, 0, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.maxY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 0, 1);
        buffer.vertex(entry, (float)b.minX, (float)b.maxY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 0, 1);
        buffer.vertex(entry, (float)b.maxX, (float)b.maxY, (float)b.maxZ).color(r, g, b1, a).normal(entry, -1, 0, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.maxY, (float)b.maxZ).color(r, g, b1, a).normal(entry, -1, 0, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.maxY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 0, -1);
        buffer.vertex(entry, (float)b.maxX, (float)b.maxY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 0, -1);
        buffer.vertex(entry, (float)b.minX, (float)b.minY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.maxY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.minY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.maxY, (float)b.minZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.minY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.minX, (float)b.maxY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.minY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)b.maxX, (float)b.maxY, (float)b.maxZ).color(r, g, b1, a).normal(entry, 0, 1, 0);
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

        private void drawBtn(DrawContext context, int x, int y, String name, boolean on,
