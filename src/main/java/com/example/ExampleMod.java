
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
                client.player.sendMessage(Text.literal("§6§l[Bubble] §fKillaura: " + (enabled ? "§aREADY" : "§cOFF")), true);
            }

            if (!enabled) return;

            PlayerEntity target = getClosestTarget(client, 3.0);

            if (target != null) {
                // Плотная наводка (Stick Aim)
                updateRotation(client, target);

                // Агрессивные удары (без лишних задержек)
                float cooldown = client.player.getAttackCooldownProgress(0f);
                
                // Бьем сразу, как только кулдаун позволяет нанести нормальный урон (от 0.9)
                if (cooldown >= 0.92f) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        });
    }

    private PlayerEntity getClosestTarget(MinecraftClient client, double range) {
        PlayerEntity closest = null;
        double dist = range;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && !p.isInvisible()) {
                double d = client.player.distanceTo(p);
                if (d < dist) {
                    dist = d;
                    closest = p;
                }
            }
        }
        return closest;
    }

    private void updateRotation(MinecraftClient client, PlayerEntity target) {
        // Наводимся чуть выше центра (в область шеи)
        double diffX = target.getX() - client.player.getX();
        double diffY = (target.getY() + target.getStandingEyeHeight() * 0.7) - (client.player.getY() + client.player.getStandingEyeHeight());
        double diffZ = target.getZ() - client.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        // Минимальное дрожание только внутри хитбокса
        float jitter = (random.nextFloat() - 0.5f) * 0.8f; 
        targetYaw += jitter;
        targetPitch += jitter;

        // Повысил скорость доводки (было 18, стало 35) для "мертвой хватки"
        client.player.setYaw(lerpAngle(client.player.getYaw(), targetYaw, 35f));
        client.player.setPitch(lerpAngle(client.player.getPitch(), targetPitch, 35f));
    }

    private float lerpAngle(float start, float end, float step) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + MathHelper.clamp(diff, -step, step);
    }
}
