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
            double range = 3.0;

            for (PlayerEntity p : client.world.getPlayers()) {
                if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= range) {
                    target = p;
                    break;
                }
            }

            if (target != null) {
                // Плавная наводка с небольшим "дрожанием" (Random jitter)
                updateRotation(client, target);

                // ЛОГИКА КРИТОВ И УДАРОВ
                boolean isFalling = client.player.fallDistance > 0.0f && !client.player.isOnGround() && !client.player.isClimbing();
                float cooldown = client.player.getAttackCooldownProgress(0.5f); // Берем прогресс с небольшим запасом

                // Бьем только если кулдаун прошел И мы в прыжке (падаем) для крита
                if (cooldown >= 0.95f + (random.nextFloat() * 0.1f)) { 
                    if (isFalling || client.player.isInSneakingPose()) {
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        });
    }

    private void updateRotation(MinecraftClient client, PlayerEntity target) {
        double diffX = target.getX() - client.player.getX();
        double diffY = (target.getY() + target.getStandingEyeHeight() * 0.8) - (client.player.getY() + client.player.getStandingEyeHeight());
        double diffZ = target.getZ() - client.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        // Рандомное смещение прицела (чтобы не было идеальной точки)
        targetYaw += (random.nextFloat() - 0.5f) * 1.2f;
        targetPitch += (random.nextFloat() - 0.5f) * 1.2f;

        client.player.setYaw(lerpAngle(client.player.getYaw(), targetYaw, 15f + random.nextFloat() * 10f));
        client.player.setPitch(lerpAngle(client.player.getPitch(), targetPitch, 15f + random.nextFloat() * 10f));
    }

    private float lerpAngle(float start, float end, float step) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + MathHelper.clamp(diff, -step, step);
    }
}
