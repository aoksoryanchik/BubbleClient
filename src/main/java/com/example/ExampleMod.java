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
import net.minecraft.util.math.Vec3d;
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
                client.player.sendMessage(Text.literal("§d§l[Bubble] §fMode: " + (enabled ? "§bDOMINATION" : "§7OFF")), true);
            }

            if (!enabled) return;

            // 1. АНТИ-ОТДАЧА (Velocity) - тебя сложнее откинуть
            if (client.player.hurtTime > 0) {
                client.player.setVelocity(client.player.getVelocity().multiply(0.6, 1.0, 0.6));
            }

            PlayerEntity target = getClosestTarget(client, 3.1);

            if (target != null) {
                // 2. МЕРТВАЯ ХВАТКА (Наводка без шансов на промах)
                updateRotation(client, target);

                // 3. УМНЫЕ УДАРЫ (Максимальный DPS)
                float cooldown = client.player.getAttackCooldownProgress(0f);
                if (cooldown >= 0.93f) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
                
                // 4. СТРЕЙФ-ПОМОЩЬ (заставляет врага промахиваться)
                if (client.player.isOnGround() && client.player.distanceTo(target) < 2.0) {
                   client.player.updateVelocity(0.02f, new Vec3d(1, 0, 0));
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
        // Наводка в верхнюю часть хитбокса (голова/шея) для точности
        double diffX = target.getX() - client.player.getX();
        double diffY = (target.getY() + target.getStandingEyeHeight() * 0.85) - (client.player.getY() + client.player.getStandingEyeHeight());
        double diffZ = target.getZ() - client.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        // Стабильная доводка без лишнего вылета за хитбокс
        client.player.setYaw(lerpAngle(client.player.getYaw(), targetYaw, 45f));
        client.player.setPitch(lerpAngle(client.player.getPitch(), targetPitch, 45f));
    }

    private float lerpAngle(float start, float end, float step) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + MathHelper.clamp(diff, -step, step);
    }
}
