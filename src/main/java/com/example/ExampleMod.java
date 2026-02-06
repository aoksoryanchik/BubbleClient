package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    public static String bindingTarget = ""; 
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bubble.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_0, "Bubble"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (menuKey.wasPressed()) client.setScreen(new BubbleMenu());

            // Работа функций
            if (killaura) runKillaura(client);
        });
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.1) {
                target = p; break;
            }
        }

        if (target != null) {
            // Мягкая, но точная наводка
            lookAt(client, target);

            // ЛОГИКА КРИТОВ
            boolean isFalling = client.player.fallDistance > 0.05f && !client.player.isOnGround();
            float cooldown = client.player.getAttackCooldownProgress(0.5f);

            // Бьем только если кулдаун заряжен и мы падаем (для крита)
            // Либо если мы на земле, но кулдаун полный (обычный удар)
            if (cooldown >= 0.93f) {
                if (isFalling || client.player.isOnGround()) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private void lookAt(MinecraftClient client, PlayerEntity target) {
        double dx = target.getX() - client.player.getX();
        double dy = (target.getY() + target.getStandingEyeHeight() * 0.7) - (client.player.getY() + client.player.getStandingEyeHeight());
        double dz = target.getZ() - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        client.player.setYaw(lerp(client.player.getYaw(), yaw, 40f));
        client.player.setPitch(lerp(client.player.getPitch(), pitch, 40f));
    }

    private float lerp(float start, float end, float speed) {
        float f = MathHelper.wrapDegrees(end - start);
        return start + MathHelper.clamp(f, -speed, speed);
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x80000000); // Фон
            int x = width / 2 - 80;
            int y = height / 2 - 40;

            context.fill(x, y, x + 160, y + 80, 0xFF101010);
            context.drawBorder(x, y, 160, 80, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§bBUBBLE CLIENT", width / 2, y + 6, -1);

            drawMod(context, x + 10, y + 30, "Killaura", killaura, killauraKey, "ka", mouseX, mouseY);
            drawMod(context, x + 10, y + 55, "TriggerBot", triggerbot, triggerbotKey, "tb", mouseX, mouseY);
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawMod(DrawContext context, int x, int y, String name, boolean state, int key, String id, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 140 && my >= y && my <= y + 15;
            String keyName = bindingTarget.equals(id) ? "..." : GLFW.glfwGetKeyName(key, 0);
            if (keyName == null) keyName = "NONE";

            context.fill(x, y, x + 140, y + 15, hover ? 0xFF202020 : 0xFF151515);
            context.fill(x + 2, y + 3, x + 10, y + 11, state ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + keyName.toUpperCase() + "]", x + 15, y + 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2 - 80;
            int y = height / 2 - 40;
            if (mouseX >= x + 10 && mouseX <= x + 150) {
                if (mouseY >= y + 30 && mouseY <= y + 45) {
                    if (button == 0) killaura = !killaura;
                    if (button == 1) bindingTarget = "ka";
                }
                if (mouseY >= y + 55 && mouseY <= y + 70) {
                    if (button == 0) triggerbot = !triggerbot;
                    if (button == 1) bindingTarget = "tb";
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingTarget.isEmpty()) {
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                    if (bindingTarget.equals("ka")) killauraKey = keyCode;
                    if (bindingTarget.equals("tb")) triggerbotKey = keyCode;
                }
                bindingTarget = "";
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
