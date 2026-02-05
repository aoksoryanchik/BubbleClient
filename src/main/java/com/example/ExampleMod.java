package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            // Дистанция 3 блока
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
