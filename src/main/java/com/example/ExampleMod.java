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

    public static String bindingFor = ""; 
    private static final boolean[] keyStates = new boolean[512];

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();
            
            if (isPressed(h, menuKey)) client.setScreen(new BubbleMenu());

            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) killaura = !killaura;
                if (isPressed(h, triggerbotKey)) triggerbot = !triggerbot;
                if (isPressed(h, espKey)) esp = !esp;
            }

            String msg = "§bBubble §7| " + (killaura ? "§aKA " : "§cKA ") + (triggerbot ? "§aTB " : "§cTB ") + (esp ? "§aESP" : "§cESP");
            client.player.sendMessage(Text.literal(msg), true);

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        // Исправленный рендер для 1.21.4
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!esp) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            // В 1.21.4 используем специальный буфер, который рисует ПОВЕРХ
            VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getDebugQuads()); 

            for (PlayerEntity entity : client.world.getPlayers()) {
                if (entity == client.player || !entity.isAlive() || entity.isInvisible()) continue;
                
                MatrixStack matrices = context.matrixStack();
                Vec3d cam = context.camera().getPos();
                
                matrices.push();
                double x = entity.getX() - cam.x;
                double y = entity.getY() - cam.y;
                double z = entity.getZ() - cam.z;
                matrices.translate(x, y, z);

                Box b = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ()).expand(0.05);
                
                // Рисуем бокс через новые методы VertexConsumer
                drawEspBox(matrices, buffer, b);
                
                matrices.pop();
            }
        });
    }

    private void drawEspBox(MatrixStack matrices, VertexConsumer buffer, Box b) {
        // В 1.21.4 мы рисуем линии через VertexConsumer
        // Белый цвет, полная непрозрачность
        float r=1, g=1, bl=1, a=1;
        
        // Отрисовка линий (12 ребер)
        renderLine(matrices, buffer, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, r, g, bl, a);
        renderLine(matrices, buffer, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, r, g, bl, a);
        renderLine(matrices, buffer, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, r, g, bl, a);
        renderLine(matrices, buffer, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, r, g, bl, a);

        renderLine(matrices, buffer, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, r, g, bl, a);
        renderLine(matrices, buffer, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, r, g, bl, a);
        renderLine(matrices, buffer, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, bl, a);
        renderLine(matrices, buffer, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, r, g, bl, a);

        renderLine(matrices, buffer, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, r, g, bl, a);
        renderLine(matrices, buffer, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, r, g, bl, a);
        renderLine(matrices, buffer, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, r, g, bl, a);
        renderLine(matrices, buffer, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, bl, a);
    }

    private void renderLine(MatrixStack matrices, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        buffer.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).normal(entry, 0, 1, 0);
        buffer.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).normal(entry, 0, 1, 0);
    }

    private boolean isPressed(long handle, int key) {
        if (key < 0 || key >= 512) return false;
        boolean down = InputUtil.isKeyPressed(handle, key);
        if (down && !keyStates[key]) { keyStates[key] = true; return true; }
        if (!down) keyStates[key] = false;
        return false;
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.6) { target = p; break; }
        }
        if (target != null) {
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
        public BubbleMenu() { super(Text.literal("Menu")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 50;
            context.fill(x, y, x + 160, y + 100, 0xFF121212);
            context.drawBorder(x, y, 160, 100, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            drawBtn(context, x+10, y+25, "Killaura", killaura, killauraKey, "ka", mouseX, mouseY);
            drawBtn(context, x+10, y+50, "TriggerBot", triggerbot, triggerbotKey, "tb", mouseX, mouseY);
            drawBtn(context, x+10, y+75, "ESP", esp, espKey, "esp", mouseX, mouseY);
        }
        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int key, String id, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kName = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0);
            if (kName == null) kName = "K_" + key;
            context.fill(x, y, x + 140, y + 16, hover ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + kName.toUpperCase() + "]", x + 15, y + 4, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width/2-80, y = height/2-50;
            if (isOver(mx, my, x+10, y+25)) { if (btn == 0) killaura=!killaura; else bindingFor="ka"; }
            if (isOver(mx, my, x+10, y+50)) { if (btn == 0) triggerbot=!triggerbot; else bindingFor="tb"; }
            if (isOver(mx, my, x+10, y+75)) { if (btn == 0) esp=!esp; else bindingFor="esp"; }
            return super.mouseClicked(mx, my, btn);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (!bindingFor.isEmpty()) {
                if (bindingFor.equals("ka")) killauraKey = k;
                if (bindingFor.equals("tb")) triggerbotKey = k;
                if (bindingFor.equals("esp")) espKey = k;
                bindingFor = ""; return true;
            }
            return super.keyPressed(k, s, m);
        }
        private boolean isOver(double mx, double my, int x, int y) { return mx >= x && mx <= x + 140 && my >= y && my <= y + 16; }
        @Override public boolean shouldPause() { return false; }
    }
}
