package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    
    public static String bindingFor = ""; // Для системы биндов
    private static KeyBinding menuKey;

    @Override
    public void onInitialize() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bubble.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_0, "Bubble"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (menuKey.wasPressed()) client.setScreen(new BubbleMenu());

            // Быстрая проверка нажатия биндов вне меню
            checkKey(client);

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });
    }

    private void checkKey(MinecraftClient client) {
        if (InputUtil.isKeyPressed(client.getWindow().getHandle(), killauraKey)) {
            killaura = !killaura;
            client.player.sendMessage(Text.literal("§l[B] §fKillaura: " + (killaura ? "§aON" : "§cOFF")), true);
        }
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.0) {
                target = p; break;
            }
        }
        if (target != null) {
            // Возвращаем жесткую наводку
            double diffX = target.getX() - client.player.getX();
            double diffZ = target.getZ() - client.player.getZ();
            client.player.setYaw((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90);

            if (client.player.getAttackCooldownProgress(0) >= 0.92f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        // Логика триггера (упрощенная для стабильности)
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Рендерим только темный градиент вместо размытия фона
            context.fillGradient(0, 0, this.width, this.height, 0x77000000, 0x77000000);
            
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            // Дизайн с тонкими рамками
            context.fill(x, y, x + 160, y + 80, 0xEE111111);
            context.drawBorder(x, y, 160, 80, 0xFF5555FF);

            renderBtn(context, x + 10, y + 25, "Killaura", killaura, killauraKey, "ka");
            renderBtn(context, x + 10, y + 50, "TriggerBot", triggerbot, triggerbotKey, "tb");

            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 5, -1);
            super.render(context, mouseX, mouseY, delta);
        }

        private void renderBtn(DrawContext context, int x, int y, String name, boolean state, int key, String id) {
            String keyName = GLFW.glfwGetKeyName(key, 0);
            if (keyName == null) keyName = "KEY " + key;
            if (bindingFor.equals(id)) keyName = "...";

            context.fill(x, y, x + 8, y + 8, state ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + keyName.toUpperCase() + "]", x + 12, y, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            if (button == 0) { // Левая кнопка - вкл/выкл
                if (isHovered(mouseX, mouseY, x + 10, y + 25)) killaura = !killaura;
                if (isHovered(mouseX, mouseY, x + 10, y + 50)) triggerbot = !triggerbot;
            } else if (button == 1) { // Правая кнопка - начать бинд
                if (isHovered(mouseX, mouseY, x + 10, y + 25)) bindingFor = "ka";
                if (isHovered(mouseX, mouseY, x + 10, y + 50)) bindingFor = "tb";
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingFor.isEmpty() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                if (bindingFor.equals("ka")) killauraKey = keyCode;
                if (bindingFor.equals("tb")) triggerbotKey = keyCode;
                bindingFor = "";
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean isHovered(double mx, double my, int x, int y) {
            return mx >= x && mx <= x + 140 && my >= y && my <= y + 10;
        }

        @Override
        public boolean shouldPause() { return false; }
    }
}
