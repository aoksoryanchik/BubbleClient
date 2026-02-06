package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false;
    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static boolean kaAutoRun = true;

    public static int killauraKey = GLFW.GLFW_KEY_P, triggerbotKey = GLFW.GLFW_KEY_R, fbKey = GLFW.GLFW_KEY_M, menuKey = GLFW.GLFW_KEY_0;
    public static String bindingFor = "";
    private static final boolean[] keyStates = new boolean[512];

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long h = client.getWindow().getHandle();

            if (isPressed(h, menuKey) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) toggle(client, "Killaura", !killaura);
                if (isPressed(h, triggerbotKey)) toggle(client, "Triggerbot", !triggerbot);
                if (isPressed(h, fbKey)) toggle(client, "FullBright", !fullbright);
            }

            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else if (client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }

            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });
    }

    private void toggle(MinecraftClient c, String n, boolean s) {
        if(n.equals("Killaura")) killaura = s;
        if(n.equals("Triggerbot")) triggerbot = s;
        if(n.equals("FullBright")) fullbright = s;
        c.player.sendMessage(Text.literal("§bBubble §8» §f" + n + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k <= 0 || k >= 512) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false;
        return false;
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive()) {
                double dist = client.player.distanceTo(p);
                boolean canSee = client.player.canSee(p);
                if (dist <= (canSee ? kaRange : kaWallsRange)) { target = p; break; }
            }
        }
        if (target != null) {
            if (kaAutoRun && client.player.input.movementForward > 0) client.player.setSprinting(true);
            client.player.setYaw((float) Math.toDegrees(Math.atan2(target.getZ() - client.player.getZ(), target.getX() - client.player.getX())) - 90);
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, target);
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
        public BubbleMenu() { super(Text.literal("Bubble")); }

        @Override
        public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 50;
            context.fill(x, y, x + 160, y + 100, 0xFF121212);
            context.drawBorder(x, y, 160, 100, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            
            drawBtn(context, x+10, y+25, "KillAura", killaura, killauraKey, "ka", mx, my, true);
            drawBtn(context, x+10, y+50, "TriggerBot", triggerbot, triggerbotKey, "tb", mx, my, false);
            drawBtn(context, x+10, y+75, "FullBright", fullbright, fbKey, "fb", mx, my, false);
        }

        private void drawBtn(DrawContext context, int x, int y, String name, boolean on, int key, String id, int mx, int my, boolean settings) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kName = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(key, 0);
            if (kName == null) kName = "K_" + key;
            context.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            context.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(textRenderer, name + " §7[" + kName.toUpperCase() + "]", x + 15, y + 4, -1);
            if (settings) {
                boolean hSet = mx >= x + 125 && mx <= x + 140 && my >= y && my <= y + 16;
                context.drawTextWithShadow(textRenderer, hSet ? "§f⚙" : "§b⚙", x + 130, y + 4, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width/2 - 80, y = height/2 - 50;
            if (mx >= x + 10 && mx <= x + 150 && my >= y + 25 && my <= y + 41) {
                if (mx >= x + 125 && btn == 1) {
                    MinecraftClient.getInstance().setScreen(new KillAuraSettings(this));
                    return true;
                }
                handle(btn, "ka"); return true;
            }
            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 50 && my <= y + 66) { handle(btn, "tb"); return true; }
                if (my >= y + 75 && my <= y + 91) { handle(btn, "fb"); return true; }
            }
            return super.mouseClicked(mx, my, btn);
        }

        private void handle(int btn, String id) {
            if (btn == 0) {
                if(id.equals("ka")) killaura = !killaura;
                if(id.equals("tb")) triggerbot = !triggerbot;
                if(id.equals("fb")) fullbright = !fullbright;
            } else if (btn == 1) bindingFor = id;
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (!bindingFor.isEmpty()) {
                if (bindingFor.equals("ka")) killauraKey = k;
                if (bindingFor.equals("tb")) triggerbotKey = k;
                if (bindingFor.equals("fb")) fbKey = k;
                bindingFor = ""; return true;
            }
            if (k == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
            return super.keyPressed(k, s, m);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rangeField, wallsField;

        public KillAuraSettings(Screen parent) {
            super(Text.literal("KA Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int x = width/2 - 70;
            rangeField = new TextFieldWidget(textRenderer, x + 80, height/2 - 32, 40, 12, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            wallsField = new TextFieldWidget(textRenderer, x + 80, height/2 - 12, 40, 12, Text.literal(""));
            wallsField.setText(String.valueOf(kaWallsRange));
            this.addSelectableChild(rangeField);
            this.addSelectableChild(wallsField);
        }

        @Override
        public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            
            // Основная панель настроек
            int x = width/2 - 60, y = height/2 - 60;
            context.fill(x, y, x + 150, y + 110, 0xFF121212);
            context.drawBorder(x, y, 150, 110, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§bSettings", x + 75, y + 6, -1);

            // Панель пресета Aresmine (СЛЕВА)
            int px = x - 90;
            context.fill(px, y, px + 85, y + 110, 0xFF121212);
            context.drawBorder(px, y, 85, 110, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§eПод Aresmine", px + 42, y + 15, -1);
            
            // Кнопка "Поставить"
            boolean hAres = mx >= px + 10 && mx <= px + 75 && my >= y + 40 && my <= y + 56;
            context.fill(px + 10, y + 40, px + 75, y + 56, hAres ? 0xFF252525 : 0xFF181818);
            context.drawBorder(px + 10, y + 40, 65, 16, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "Поставить", px + 42, y + 44, -1);

            // Рендер полей и текста
            context.drawTextWithShadow(textRenderer, "Range:", x + 10, y + 25, -1);
            context.drawTextWithShadow(textRenderer, "Walls:", x + 10, y + 45, -1);
            context.drawTextWithShadow(textRenderer, "Auto Run:", x + 10, y + 70, -1);

            drawSlider(context, x + 10, y + 35, kaRange / 6.0);
            drawSlider(context, x + 10, y + 55, kaWallsRange / 6.0);
            
            context.fill(x + 120, y + 68, x + 132, y + 80, 0xFF181818);
            context.drawBorder(x + 120, y + 68, 12, 12, 0xFF00AAFF);
            if (kaAutoRun) context.drawCenteredTextWithShadow(textRenderer, "✔", x + 126, y + 70, 0xFF00FF00);

            rangeField.render(context, mx, my, delta);
            wallsField.render(context, mx, my, delta);
            context.drawTextWithShadow(textRenderer, "§7[ESC] Назад", x + 10, y + 95, 0xFFAAAAAA);
        }

        private void drawSlider(DrawContext context, int x, int y, double progress) {
            context.fill(x, y, x + 60, y + 4, 0xFF181818);
            context.fill(x, y, x + (int)(60 * Math.min(progress, 1.0)), y + 4, 0xFF00AAFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width/2 - 60, y = height/2 - 60;
            int px = x - 90;

            // Клик по кнопке "Поставить"
            if (mx >= px + 10 && mx <= px + 75 && my >= y + 40 && my <= y + 56) {
                kaRange = 3.8;
                kaWallsRange = 3.0;
                kaAutoRun = true;
                rangeField.setText("3.8");
                wallsField.setText("3.0");
                return true;
            }

            if (mx >= x + 120 && mx <= x + 132 && my >= y + 68 && my <= y + 80) {
                kaAutoRun = !kaAutoRun; return true;
            }

            if (mx >= x + 10 && mx <= x + 70) {
                if (my >= y + 35 && my <= y + 39) {
                    kaRange = Math.round(((mx - (x + 10)) / 60.0) * 6.0 * 10.0) / 10.0;
                    rangeField.setText(String.valueOf(kaRange));
                }
                if (my >= y + 55 && my <= y + 59) {
                    kaWallsRange = Math.round(((mx - (x + 10)) / 60.0) * 6.0 * 10.0) / 10.0;
                    wallsField.setText(String.valueOf(kaWallsRange));
                }
            }

            rangeField.setFocused(rangeField.isMouseOver(mx, my));
            wallsField.setFocused(wallsField.isMouseOver(mx, my));
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { MinecraftClient.getInstance().setScreen(parent); return true; }
            boolean r = rangeField.keyPressed(k, s, m) || wallsField.keyPressed(k, s, m);
            updateVals();
            return r || super.keyPressed(k, s, m);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            boolean r = rangeField.charTyped(chr, modifiers) || wallsField.charTyped(chr, modifiers);
            updateVals(); return r;
        }

        private void updateVals() {
            try { kaRange = Double.parseDouble(rangeField.getText()); } catch (Exception ignored) {}
            try { kaWallsRange = Double.parseDouble(wallsField.getText()); } catch (Exception ignored) {}
        }
        @Override public boolean shouldPause() { return false; }
    }
}
