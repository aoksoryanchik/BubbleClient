package net.fabricmc.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class ExampleMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            // Ищем цель в радиусе 10 блоков
            for (PlayerEntity target : client.world.getPlayers()) {
                if (target != client.player && target.isAlive() && client.player.distanceTo(target) < 10) {
                    // Авто-удар
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

