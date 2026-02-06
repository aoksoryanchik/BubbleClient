package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    // Настройки функций
    public static boolean killauraEnabled = false;
    public static boolean triggerbotEnabled = false;
    
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;

    private static KeyBinding menuKey;

    @Override
    public void onInitialize() {
        // Клавиша открытия меню на "0"
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bubbleclient.menu", 
                InputUtil.Type.KEYSYM, 
                GLFW.GLFW_KEY_0, 
                "category.bubbleclient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Открытие меню
            if (menuKey.wasPressed()) {
                // client.setScreen(new ClickGuiScreen()); 
                // Пока закомментируем, чтобы билд не упал без файла экрана
                client.player.sendMessage(Text.literal("§d[Bubble] §fМеню в разработке..."), true);
            }

            // Логика Киллауры
            if (killauraEnabled) {
                runKillaura(client);
            }
            
            // Логика Триггербота
            if (triggerbotEnabled && !killauraEnabled) {
                runTriggerbot(client);
            }
        });
    }

    private void runKillaura(MinecraftClient client) {
        // Тот самый мощный код киллауры, который мы писали раньше
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.0) {
                target = p; break;
            }
        }
        if (target != null && client.player.getAttackCooldownProgress(0) >= 0.95f) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        // Твой триггербот
        if (client.crosshairTarget != null && client.player.getAttackCooldownProgress(0) >= 0.98f) {
            // Удар если наведен на сущность
        }
    }
}
