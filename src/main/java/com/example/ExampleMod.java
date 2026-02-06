package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    private static KeyBinding toggleKey;
    private static boolean enabled = false;
    private final Random random = new Random();
    private int attackDelay = 0;

    @Override
    public void onInitialize() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bubbleclient.toggle", 
                InputUtil.Type.KEYSYM, 
                GLFW.GLFW_KEY_P, 
                "category.bubbleclient"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                client.player.sendMessage(Text.literal("§b[Bubble] §fKillaura: " + (enabled ? "§aON" : "§cOFF")), true);
            }

            if (!enabled) return;

            PlayerEntity target = null;
            // Уменьшил до 2.8 для обхода античита на дистанцию
            double range = 2.8;

            for (PlayerEntity p : client.world.getPlayers()) {
                if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= range) {
                    target = p;
                    break;
                }
            }

            if (target != null) {
                // Оставляем ту самую классную наводку
                updateRotation(client, target);

                // Новая логика ударов
                if (attackDelay > 0) {
                    attackDelay--;
                } else {
                    float cooldown = client.player.getAttackCooldownProgress(0.5f);
                    
                    // Бьем, если кулдаун почти заряжен (0.9+)
                    if (cooldown >= 0.9f) {
                        // Пытаемся ударить. Больше не ждем "идеального падения", 
                        // просто проверяем, что мы не лезем по лестнице.
                        if (!client.player.isClimbing() && !client.player.isSwimming()) {
                            
                            // Сама атака через InteractionManager (самый надежный способ)
                            client.interactionManager.attackEntity(client.player, target);
                            client.player.swingHand(Hand.MAIN_HAND);
                            
                            // Добавляем случайную паузу между ударами (от 1 до 3 тиков)
                            // Это сбивает античит с толку
                            attackDelay = 1 + random.nextInt(2);
                        }
                    }
                }
            }
        });
    }

    private void updateRotation(MinecraftClient client, PlayerEntity target) {
        // ... (код наводки остается таким же, он крутой)
        double diffX = target.getX() - client.player.getX();
        double diffY = (target.getY() + target.getStandingEyeHeight() * 0.75) - (client.player.getY() + client.player.getStandingEyeHeight());
        double diffZ = target.getZ() - client.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        // Jitter (дрожание) для легитности
        targetYaw += (random.nextFloat() - 0.5f) * 1.5f;
        targetPitch += (random.nextFloat() - 0.5f) * 1.5f;

        client.player.setYaw(lerpAngle(client.player.getYaw(), targetYaw, 18f + random.nextFloat() * 5f));
        client.player.setPitch(lerpAngle(client.player.getPitch(), targetPitch, 18f + random.nextFloat() * 5f));
    }

    private float lerpAngle(float start, float end, float step) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + MathHelper.clamp(diff, -step, step);
    }
}
