package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;

    private static KeyBinding menuKey;

    @Override
    public void onInitialize() {
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bubbleclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_0,
                "category.bubbleclient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (menuKey.wasPressed()) {
                client.setScreen(new BubbleMenu(Text.literal("Bubble Menu")));
            }

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });
    }

    private void runKillaura(MinecraftClient client) {
        for (PlayerEntity target : client.world.getPlayers()) {
            if (target != client.player && target.isAlive() && client.player.distanceTo(target) <= 3.0) {
                if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hit).getEntity();
            if (target instanceof PlayerEntity && client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    // КЛАСС МЕНЮ ВНУТРИ ОСНОВНОГО ФАЙЛА
    public static class BubbleMenu extends Screen {
        public BubbleMenu(Text title) { super(title); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            
            int x = width / 2 - 100;
            int y = height / 2 - 50;

            // Рисуем основной прямоугольник (фон меню)
            context.fill(x, y, x + 200, y + 100, 0xAA000000); // Полупрозрачный черный
            context.drawBorder(x, y, 200, 100, 0xFF5555FF); // Синяя рамка

            context.drawCenteredTextWithShadow(this.textRenderer, "BUBBLE CLIENT v1.0", width / 2, y + 10, 0xFFFFFF);

            // Кнопка Киллауры
            renderButton(context, x + 10, y + 35, "Killaura", killaura, mouseX, mouseY);
            // Кнопка Триггербота
            renderButton(context, x + 10, y + 65, "TriggerBot", triggerbot, mouseX, mouseY);

            super.render(context, mouseX, mouseY, delta);
        }

        private void renderButton(DrawContext context, int x, int y, String name, boolean state, int mx, int my) {
            int color = state ? 0xFF00FF00 : 0xFFFF0000; // Зеленый если ВКЛ, красный если ВЫКЛ
            context.fill(x, y, x + 10, y + 10, color); // Квадратик
            context.drawTextWithShadow(this.textRenderer, name + " [" + (state ? "ON" : "OFF") + "]", x + 15, y + 1, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2 - 100;
            int y = height / 2 - 50;

            // Проверка клика по Киллауре
            if (mouseX >= x + 10 && mouseX <= x + 150 && mouseY >= y + 35 && mouseY <= y + 45) {
                killaura = !killaura;
                return true;
            }
            // Проверка клика по Триггерботу
            if (mouseX >= x + 10 && mouseX <= x + 150 && mouseY >= y + 65 && mouseY <= y + 75) {
                triggerbot = !triggerbot;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean shouldPause() { return false; } // Игра не ставится на паузу в меню
    }
}
