package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    public static int menuKey = GLFW.GLFW_KEY_0;

    public static String bindingFor = ""; 
    private static final boolean[] keyStates = new boolean[512];
    private boolean menuPressed = false;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            long handle = client.getWindow().getHandle();

            // Открытие меню на 0
            boolean menuDown = InputUtil.isKeyPressed(handle, menuKey);
            if (menuDown && !menuPressed) {
                client.setScreen(new BubbleMenu());
            }
            menuPressed = menuDown;

            // Бинды функций
            if (client.currentScreen == null) {
                if (isPressed(handle, killauraKey)) {
                    killaura = !killaura;
                    client.player.sendMessage(Text.literal("§b[B] §fKillaura: " + (killaura ? "§aON" : "§cOFF")), true);
                }
                if (isPressed(handle, triggerbotKey)) {
                    triggerbot = !triggerbot;
                    client.player.sendMessage(Text.literal("§b[B] §fTrigger: " + (triggerbot ? "§aON" : "§cOFF")), true);
                }
            }

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });
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
            context.fill(0, 0, width, height, 0x95000000); // Четкий фон без блюра
            
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            context.fill(x, y, x + 160, y + 80, 0xFF121212);
            context.drawBorder(x, y, 160, 80, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 6, -1);

            drawBtn(context, x + 10, y + 30, "Killaura", killaura, killauraKey, "ka", mouseX, mouseY);
            drawBtn(context, x + 10, y + 55, "TriggerBot", triggerbot, triggerbotKey, "tb", mouseX, mouseY);
        }

        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int key, String id, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kName = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0);
            if (kName == null) kName = "KEY_" + key;

            context.fill(x, y, x + 140, y + 16, hover ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + kName.toUpperCase() + "]", x + 15, y + 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            if (isOver(mouseX, mouseY, x + 10, y + 30)) {
                if (button == 0) killaura = !killaura;
                else if (button == 1) bindingFor = "ka";
            }
            if (isOver(mouseX, mouseY, x + 10, y + 55)) {
                if (button == 0) triggerbot = !triggerbot;
                else if (button == 1) bindingFor = "tb";
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingFor.isEmpty()) {
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                    if (bindingTargetIs("ka")) killauraKey = keyCode;
                    if (bindingTargetIs("tb")) triggerbotKey = keyCode;
                }
                bindingFor = "";
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean bindingTargetIs(String id) { return bindingFor.equals(id); }
        private boolean isOver(double mx, double my, int x, int y) { return mx >= x && mx <= x + 140 && my >= y && my <= y + 16; }
        @Override public boolean shouldPause() { return false; }
    }
}
