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

public class ExampleMod implements ModInitializer {
    private static KeyBinding toggleKey;
    private static boolean enabled = false;

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
                client.player.sendMessage(Text.literal("§l[Bubble] §fKillaura: " + (enabled ? "§aEnabled" : "§cDisabled")), true);
            }

            if (!enabled) return;

            PlayerEntity target = null;
            double closestDistance = 3.1; // Радиус 3 блока + запас

            for (PlayerEntity p : client.world.getPlayers()) {
                if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= closestDistance) {
                    target = p;
                    closestDistance = client.player.distanceTo(p);
                }
            }

            if (target != null) {
                // ЛОГИКА АВТО-НАВОДКИ (Aimbot)
                lookAtEntity(client, target);

                // ЛОГИКА УДАРА
                if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        });
    }

    private void lookAtEntity(MinecraftClient client, PlayerEntity target) {
        // Вычисляем вектор до хитбокса (грудь/голова)
        Vec3d targetPos = target.getPos().add(0, target.getStandingEyeHeight() / 1.5, 0);
        Vec3d playerPos = client.player.getEyePos();

        double diffX = targetPos.x - playerPos.x;
        double diffY = targetPos.y - playerPos.y;
        double diffZ = targetPos.z - playerPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        // Плавность наводки (чтобы античит не кикнул)
        client.player.setYaw(lerpAngle(client.player.getYaw(), targetYaw, 25f)); // Скорость 25
        client.player.setPitch(lerpAngle(client.player.getPitch(), targetPitch, 25f));
    }

    private float lerpAngle(float start, float end, float step) {
        float diff = MathHelper.wrapDegrees(end - start);
        if (diff > step) diff = step;
        if (diff < -step) diff = -step;
        return start + diff;
    }
}
