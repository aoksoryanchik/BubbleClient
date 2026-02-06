package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean esp = false; // Теперь это Glow + NightVision
    
    public static int killauraKey = GLFW.GLFW_KEY_P;
    public static int triggerbotKey = GLFW.GLFW_KEY_R;
    public static int espKey = GLFW.GLFW_KEY_M;
    public static int menuKey = GLFW.GLFW_KEY_0;

    public static String bindingFor = ""; 
    private static final boolean[] keyStates = new boolean[512];

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            // ФИКС МЕНЮ: Открывается только если чат ЗАКРЫТ
            if (isPressed(h, menuKey) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) killaura = !killaura;
                if (isPressed(h, triggerbotKey)) triggerbot = !triggerbot;
                if (isPressed(h, espKey)) {
                    esp = !esp;
                    // Убираем ночное зрение при выключении
                    if (!esp) client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                }
            }

            // ЛОГИКА ESP (Glow + NightVision)
            if (esp) {
                // Даем себе ночное зрение без пузырьков
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
                
                // Подсвечиваем всех игроков вокруг
                for (PlayerEntity enemy : client.world.getPlayers()) {
                    if (enemy != client.player && enemy.isAlive()) {
                        enemy.setGlowing(true); // Включает ванильный контур сквозь стены
                    }
                }
            } else {
                // Выключаем подсветку у других, если ESP выключен
                for (PlayerEntity enemy : client.world.getPlayers()) {
                    if (enemy != client.player) enemy.setGlowing(false);
                }
            }

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });
    }

    private boolean isPressed(long h, int k) {
        if (k < 0 || k >= 512) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false;
        return false;
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity t = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive() && client.player.distanceTo(p) <= 3.8) { t = p; break; }
        }
        if (t != null) {
            client.player.setYaw((float) Math.toDegrees(Math.atan2(t.getZ() - client.player.getZ(), t.getX() - client.player.getX())) - 90);
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, t);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult res && res.getEntity() instanceof PlayerEntity) {
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, res.getEntity());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 50;
            context.fill(x, y, x + 160, y + 100, 0xFF121212);
            context.drawBorder(x, y, 160, 100, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            drawBtn(context, x+10, y+25, "Killaura", killaura, killauraKey, "ka", mouseX, mouseY);
            drawBtn(context, x+10, y+50, "TriggerBot", triggerbot, triggerbotKey, "tb", mouseX, mouseY);
            drawBtn(context, x+10, y+75, "Glow ESP", esp, espKey, "esp", mouseX, mouseY);
        }
        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int key, String id, int mx, int my) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kName = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0);
            if (kName == null) kName = "K_" + key;
            context.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + kName.toUpperCase() + "]", x + 15, y + 4, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width/2-80, y = height/2-50;
            if (mx >= x+10 && mx <= x+150) {
                if (my >= y+25 && my <= y+41) { if (btn == 0) killaura=!killaura; else bindingFor="ka"; }
                if (my >= y+50 && my <= y+66) { if (btn == 0) triggerbot=!triggerbot; else bindingFor="tb"; }
                if (my >= y+75 && my <= y+91) { if (btn == 0) esp=!esp; else bindingFor="esp"; }
            }
            return super.mouseClicked(mx, my, btn);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (!bindingFor.isEmpty()) {
                if (bindingFor.equals("ka")) killauraKey = k;
                if (bindingFor.equals("tb")) triggerbotKey = k;
                if (bindingFor.equals("esp")) espKey = k;
                bindingFor = ""; return true;
            }
            return super.keyPressed(k, s, m);
        }
        @Override public boolean shouldPause() { return false; }
    }
}

