package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("bubbleclient");

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            // Дистанция срабатывания: 3 блока
            double range = 3.0;
            
            for (PlayerEntity target : client.world.getPlayers()) {
                // Не бьем себя, проверяем что цель жива и находится в радиусе 3 блоков
                if (target != client.player && target.isAlive() && client.player.distanceTo(target) <= range) {
                    // Проверка КД удара (1.0f = замах полностью завершен)
                    if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                    break; // Бьем только одну цель за тик
                }
            }
        });
    }
}
