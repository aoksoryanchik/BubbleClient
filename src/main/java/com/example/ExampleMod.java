package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    // Настройки функций
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    
    // Клавиши по умолчанию
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    public static int menuKey = GLFW.GLFW_KEY_0;

    public static String bindingFor = ""; 
    private boolean menuPressed = false;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // 1. Обработка открытия меню
            boolean isMenuDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), menuKey);
            if (isMenuDown && !menuPressed) {
                client.setScreen(new BubbleMenu());
            }
            menuPressed = isMenuDown;

            // 2. Обработка Биндов (включение/выключение функций)
            if (client.currentScreen == null) {
                if (isKeyDown(client, killauraKey)) killaura = !killaura;
                if (isKeyDown(client, triggerbotKey)) triggerbot = !triggerbot;
            }

            // 3. Работа Киллауры
            if (killaura) {
                runKillaura(client);
            }

            // 4. Работа Триггербота
            if (triggerbot && !killaura) {
                runTriggerbot(client);
            }
        });
    }

    private boolean isKeyDown(MinecraftClient client, int key) {
        // Умная проверка нажатия (чтобы не переключалось 20 раз в секунду)
        static boolean[] state = new boolean[512];
        boolean isDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), key);
        if (isDown && !state[key]) {
            state[key] = true;
            client.player.sendMessage(Text.literal("§b[Bubble] §fToggle!"), true);
            return true;
        }
        if (!isDown) state[key] = false;
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
            // Мгновенная наводка
            double dx = target.getX() - client.player.getX();
            double dz = target.getZ() - client.player.getZ();
            client.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90);

            // Удар с проверкой кулдауна
            if (client.player.getAttackCooldownProgress(0) >= 0.93f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity) {
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, entityHit.getEntity());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // ВНУТРЕННИЙ КЛАСС МЕНЮ
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x85000000); // Затемнение фона
            
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            context.fill(x, y, x + 160, y + 80, 0xFF121212); // Основной бокс
            context.drawBorder(x, y, 160, 80, 0xFF5555FF); // Рамка

            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 6, -1);

            drawButton(context, x + 10, y + 30, "Killaura", killaura, killauraKey, "ka", mouseX, mouseY);
            drawButton(context, x + 10, y + 55, "TriggerBot", triggerbot, triggerbotKey, "tb", mouseX, mouseY);
        }

        private void drawButton(DrawContext context, int x, int y, String name, boolean enabled, int key, String id, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String keyStr = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0);
            if (keyStr == null) keyStr = "K_" + key;

            context.fill(x, y, x + 140, y + 16, hover ? 0xFF222222 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, enabled ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + keyStr.toUpperCase() + "]", x + 15, y + 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            if (isOver(mouseX, mouseY, x + 10, y + 30)) handle(button, "ka");
            if (isOver(mouseX, mouseY, x + 10, y + 55)) handle(button, "tb");

            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void handle(int button, String id) {
            if (button == 0) { // ЛКМ
                if (id.equals("ka")) killaura = !killaura;
                if (id.equals("tb")) triggerbot = !triggerbot;
            } else if (button == 1) { // ПКМ
                bindingFor = id;
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingFor.isEmpty()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { bindingFor = ""; return true; }
                if (bindingFor.equals("ka")) killauraKey = keyCode;
                if (bindingFor.equals("tb")) triggerbotKey = keyCode;
                bindingFor = "";
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean isOver(double mx, double my, int x, int y) { return mx >= x && mx <= x + 140 && my >= y && my <= y + 16; }
        @Override public boolean shouldPause() { return false; }
    }
}
