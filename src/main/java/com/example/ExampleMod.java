package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    private static KeyBinding toggleKey;
    private static boolean enabled = false; // По умолчанию выключена

    @Override
    public void onInitialize() {
        // Регистрируем кнопку "P" (GLFW_KEY_P)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bubbleclient.toggle", 
                InputUtil.Type.KEYSYM, 
                GLFW.GLFW_KEY_P, 
                "category.bubbleclient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Проверяем нажатие кнопки
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                // Пишем в чат статус (только тебе видно)
                client.player.sendMessage(Text.literal("Killaura: " + (enabled ? "§aON" : "§cOFF")), true);
            }

            // Если выключена — ничего не делаем
            if (!enabled) return;

            double range = 3.0;
            for (PlayerEntity target : client.world.getPlayers()) {
                if (target != client.player && target.isAlive() && client.player.distanceTo(target) <= range) {
                    if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                    break;
                }
            }
        });
    }
}
