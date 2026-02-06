package com.example;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean esp = false;
    
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    public static int espKey = GLFW.GLFW_KEY_M;
    public static int menuKey = GLFW.GLFW_KEY_0;

    private static final boolean[] keyStates = new boolean[512];

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long h = client.getWindow().getHandle();

            // Открытие меню (строгий фикс чата)
            if (isPressed(h, menuKey) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Обработка биндов и вывод в чат (Overlay)
            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) {
                    killaura = !killaura;
                    sendMsg(client, "Killaura", killaura);
                }
                if (isPressed(h, triggerbotKey)) {
                    triggerbot = !triggerbot;
                    sendMsg(client, "Triggerbot", triggerbot);
                }
                if (isPressed(h, espKey)) {
                    esp = !esp;
                    sendMsg(client, "ESP", esp);
                }
            }

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        // ESP через LINES (X-Ray эффект)
        WorldRenderEvents.LAST.register(context -> {
            if (!esp) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            // В 1.21.4 используем RenderLayer.getLines() для стабильности
            VertexConsumerProvider.Immediate consumers = context.consumers();
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

            RenderSystem.disableDepthTest(); // Видим сквозь стены

            for (PlayerEntity entity : client.world.getPlayers()) {
                if (entity == client.player || !entity.isAlive() || entity.isInvisible()) continue;

                MatrixStack matrices = context.matrixStack();
                Vec3d cam = context.camera().getPos();
                float delta = context.tickCounter().getTickDelta(true);

                double x = (entity.prevX + (entity.getX() - entity.prevX) * delta) - cam.x;
                double y = (entity.prevY + (entity.getY() - entity.prevY) * delta) - cam.y;
                double z = (entity.prevZ + (entity.getZ() - entity.prevZ) * delta) - cam.z;

                matrices.push();
                matrices.translate(x, y, z);
                Box b = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());
                
                // Рисуем бокс через современный буфер
                WorldRenderer.drawBox(matrices, buffer, b, 0f, 0.8f, 1f, 1f);
                matrices.pop();
            }
            
            consumers.draw(); // Принудительно рисуем линии сейчас
            RenderSystem.enableDepthTest();
        });
    }

    private void sendMsg(MinecraftClient client, String name, boolean state) {
        client.player.sendMessage(Text.literal("§bBubble §8» §f" + name + ": " + (state ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k < 0 || k >= 512) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false;
        return false;
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.8) {
                target = p; break;
            }
        }
        if (target != null) {
            // ФИКС БЕГА: принудительно зажимаем спринт при наличии цели
            if (client.player.input.movementForward > 0) {
                client.player.setSprinting(true);
            }
            
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

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 50;
            context.fill(x, y, x + 160, y + 100, 0xFF121212);
            context.drawBorder(x, y, 160, 100, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            drawBtn(context, x+10, y+25, "Killaura", killaura, mouseX, mouseY);
            drawBtn(context, x+10, y+50, "TriggerBot", triggerbot, mouseX, mouseY);
            drawBtn(context, x+10, y+75, "ESP", esp, mouseX, mouseY);
        }
        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int mx, int my) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            context.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name, x + 15, y + 4, -1);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
