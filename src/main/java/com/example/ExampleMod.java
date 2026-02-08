package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    // Основные модули
    public static boolean killaura = false, triggerbot = false, fullbright = false, esp = false, viewModel = true;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, anim1 = false, isDestructed = false; 
    
    // Параметры Киллауры
    public static double kaRange = 3.8, kaWallsRange = 3.0;
    
    // Координаты рук
    public static float vmX = 0.45f, vmY = -0.35f, vmZ = -0.7f;

    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyESP = -1;
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || isDestructed) return;
            long h = client.getWindow().getHandle();

            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());

            // Модули
            if (fullbright) client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) client.player.setSprinting(true);
            if (autoTotem && client.player.getHealth() <= 8.0f) handleAutoTotem(client);
            
            // Anti-Velocity (теперь тут)
            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(client.player.getVelocity().multiply(0.6, 1.0, 0.6));
            }

            if (killaura) runAura(client);
        });
    }

    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isCreative()) continue;
            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }
        if (target != null) {
            updateRotations(client.player, target.getPos().add(0, target.getHeight()*0.5, 0));
            if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void updateRotations(PlayerEntity player, Vec3d target) {
        Vec3d diff = target.subtract(player.getEyePos());
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x*diff.x + diff.z*diff.z)));
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(tYaw - player.getYaw()) * 0.25f);
        player.setPitch(player.getPitch() + (tPitch - player.getPitch()) * 0.25f);
    }

    private void handleAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, client.player);
                    break;
                }
            }
        }
    }

    // --- ГЛАВНОЕ МЕНЮ (ЧИСТЫЙ СТИЛЬ) ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            int x = width/2 - 90, y = height/2 - 100;
            ctx.fill(x, y, x + 180, y + 200, 0xFF050505);
            ctx.drawBorder(x, y, 180, 200, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bBUBBLE", width/2, y + 8, -1);
            
            String[] names = {"KillAura", "TriggerBot", "FullBright", "ESP", "HandView", "AutoRun", "AutoTotem"};
            boolean[] states = {killaura, triggerbot, fullbright, esp, viewModel, autoRun, autoTotem};
            
            for (int i = 0; i < names.length; i++) {
                int iy = y + 30 + (i * 22);
                ctx.fill(x + 5, iy, x + 175, iy + 18, 0xFF111111);
                ctx.drawTextWithShadow(textRenderer, names[i], x + 10, iy + 5, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                if (i == 0 || i == 4) ctx.drawTextWithShadow(textRenderer, "⚙", x + 160, iy + 5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 90, y = height/2 - 100;
            for (int i = 0; i < 7; i++) {
                int iy = y + 30 + (i * 22);
                if (mx >= x + 5 && mx <= x + 175 && my >= iy && my <= iy + 18) {
                    if (mx >= x + 150) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        if (i == 4) client.setScreen(new HandSettings(this));
                    } else {
                        if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot;
                        if(i==2) fullbright=!fullbright; if(i==3) esp=!esp;
                        if(i==4) viewModel=!viewModel; if(i==5) autoRun=!autoRun;
                        if(i==6) autoTotem=!autoTotem;
                        saveConfig();
                    } return true;
                }
            } return false;
        }
    }

    // --- НАСТРОЙКИ КИЛЛАУРЫ (ANTI-VELOCITY ТУТ) ---
    public static class KillAuraSettings extends Screen {
        private final Screen p;
        private TextFieldWidget f1;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            f1 = new TextFieldWidget(textRenderer, width/2 + 10, height/2 - 50, 40, 14, Text.literal(""));
            f1.setText(String.valueOf(kaRange)); addDrawableChild(f1);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xFF000000);
            int x = width/2, y = height/2;
            ctx.drawCenteredTextWithShadow(textRenderer, "§bAURA & COMBAT", x, y - 80, -1);
            ctx.drawTextWithShadow(textRenderer, "Distance:", x - 60, y - 47, -1);
            
            // Кнопка Anti-Velocity
            ctx.fill(x - 70, y - 10, x + 70, y + 10, antiVelocity ? 0xFF00AAFF : 0xFF222222);
            ctx.drawCenteredTextWithShadow(textRenderer, "Anti-Velocity: " + (antiVelocity ? "ON" : "OFF"), x, y - 4, -1);
            
            // Пресет FunTime
            boolean hFT = mx >= x - 70 && mx <= x + 70 && my >= y + 20 && my <= y + 40;
            ctx.fill(x - 70, y + 20, x + 70, y + 40, hFT ? 0xFF333333 : 0xFF151515);
            ctx.drawCenteredTextWithShadow(textRenderer, "§6Preset: FunTime", x, y + 26, -1);

            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 70 && mx <= x + 70 && my >= y - 10 && my <= y + 10) { antiVelocity = !antiVelocity; return true; }
            if (mx >= x - 70 && mx <= x + 70 && my >= y + 20 && my <= y + 40) { 
                kaRange = 3.1; kaWallsRange = 2.5; antiVelocity = true; 
                f1.setText("3.1"); return true; 
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange = Double.parseDouble(f1.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            } return super.keyPressed(k, s, m);
        }
    }

    // --- НАСТРОЙКИ РУК (ANIMATION 1 ТУТ) ---
    public static class HandSettings extends Screen {
        private final Screen p;
        public HandSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xFF000000);
            int x = width/2, y = height/2;
            ctx.drawCenteredTextWithShadow(textRenderer, "§bHANDS VIEW", x, y - 50, -1);
            
            ctx.fill(x - 60, y - 10, x + 60, y + 10, anim1 ? 0xFF00AAFF : 0xFF222222);
            ctx.drawCenteredTextWithShadow(textRenderer, "Animation 1", x, y - 4, -1);
            
            ctx.drawCenteredTextWithShadow(textRenderer, "§7(Наклон меча + плавность + малый размер)", x, y + 25, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 60 && mx <= x + 60 && my >= y - 10 && my <= y + 10) { anim1 = !anim1; saveConfig(); return true; }
            return false;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(p); return true; }
            return super.keyPressed(k, s, m);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + anim1 + ":" + antiVelocity + ":" + autoRun + ":" + killaura);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            kaRange = Double.parseDouble(p[0]);
            anim1 = Boolean.parseBoolean(p[1]);
            antiVelocity = Boolean.parseBoolean(p[2]);
            autoRun = Boolean.parseBoolean(p[3]);
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k <= 0) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }
}
