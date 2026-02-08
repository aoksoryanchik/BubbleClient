package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExampleMod implements ModInitializer {
    // Состояния модулей (ВЕРНУТО ПОЛНОСТЬЮ)
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true, sticky = true;
    public static boolean invWalk = true, fastPlace = false, noSlow = true; 

    // Настройки
    public static double kaRange = 3.9, kaWallsRange = 3.2;
    public static float shakeIntensity = 0.7f;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    private static double smoothAngle = 0;

    // Клавиши
    public static int keyKA = GLFW.GLFW_KEY_R, keyTB = 0, keyFB = GLFW.GLFW_KEY_B, keyAT = 0, keyWP = GLFW.GLFW_KEY_N;
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_ultra_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            // Открытие меню на "0"
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Обработка биндов
            if (client.currentScreen == null) {
                handleKeyBinds(h);
            }

            // FullBright Logic
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            
            // Расширенный AutoTotem
            if (autoTotem) handleAutoTotem(client);
            
            // Расширенный AntiVelocity (с учетом состояния игрока)
            if (antiVelocity && client.player.hurtTime > 0) {
                if (!client.player.isTouchingWater() && !client.player.isInLava() && !client.player.isClimbing()) {
                    Vec3d velocity = client.player.getVelocity();
                    client.player.setVelocity(velocity.x * 0.3, velocity.y, velocity.z * 0.3);
                }
            }

            // Запуск основных функций
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });

        // Отрисовка навигатора
        HudRenderCallback.EVENT.register((ctx, delta) -> {
            renderWaypoints(ctx);
        });
    }

    private void handleKeyBinds(long h) {
        if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
        if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
        if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
        if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
        if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
    }

    private void handleAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    int slot = i < 9 ? i + 36 : i;
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 45, SlotActionType.SWAP, client.player);
                    break;
                }
            }
        }
    }

    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isCreative() || p.isInvisible()) continue;
            
            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d;
                target = p;
            }
        }
        
        if (target != null) {
            if (autoRun && !client.player.isInsideWaterOrBubbleColumn()) client.player.setSprinting(true);
            
            // Расширенная липкая аура (Sticky)
            if (sticky && target.hurtTime > 3) {
                Vec3d diff = target.getPos().subtract(client.player.getPos()).normalize().multiply(0.15);
                client.player.addVelocity(diff.x, 0, diff.z);
            }

            // Продвинутые ротации
            lookAt(client.player, target.getPos().add(0, target.getHeight() * 0.72, 0));
            
            // Логика удара
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Shake (тряска) для обхода
                if (shakeIntensity > 0) {
                    client.player.setYaw(client.player.getYaw() + (float)(Math.random() - 0.5) * shakeIntensity);
                    client.player.setPitch(client.player.getPitch() + (float)(Math.random() - 0.5) * shakeIntensity);
                }
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).multiply(kaRange);
        Vec3d end = eye.add(look);
        Box box = client.player.getBoundingBox().stretch(look).expand(1.0D);
        
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, end, box, (e) -> e instanceof PlayerEntity && e != client.player, kaRange * kaRange);
        
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            boolean canHit = tbCrits ? (client.player.fallDistance > 0 && !client.player.isOnGround() && !client.player.isClimbing()) : true;
            if (client.player.getAttackCooldownProgress(0) >= 0.95f && canHit) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void lookAt(PlayerEntity p, Vec3d target) {
        Vec3d diff = target.subtract(p.getEyePos());
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, dist));
        
        p.setYaw(p.getYaw() + MathHelper.wrapDegrees(yaw - p.getYaw()));
        p.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    private void renderWaypoints(DrawContext ctx) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        double dist = Math.sqrt(client.player.squaredDistanceTo(wpX, wpY, wpZ));
        double targetAngle = Math.toDegrees(Math.atan2(wpZ - client.player.getZ(), wpX - client.player.getX())) - 90;
        double rel = MathHelper.wrapDegrees(targetAngle - client.player.getYaw());
        
        smoothAngle += (rel - smoothAngle) * 0.25;
        
        String arrow = "↑";
        int color = 0xFFFFFFFF;
        if (Math.abs(smoothAngle) < 15) { arrow = "↑ [ПРЯМО]"; color = 0xFF00FF00; }
        else if (smoothAngle > 15 && smoothAngle < 165) arrow = "→";
        else if (smoothAngle < -15 && smoothAngle > -165) arrow = "←";
        else arrow = "↓";

        int centerX = client.getWindow().getScaledWidth() / 2;
        ctx.drawCenteredTextWithShadow(client.textRenderer, arrow + String.format(" §b(%.1f m)", dist), centerX, 10, color);
        ctx.drawCenteredTextWithShadow(client.textRenderer, String.format("§7Target: §fX:%.0f Y:%.0f Z:%.0f", wpX, wpY, wpZ), centerX, 22, -1);
    }

    private void sendNotify(String m, boolean s) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + m + " : " + (s ? "§aON" : "§cOFF")), true);
        }
    }

    private boolean isPressed(long h, int k) {
        if (k == 0) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    // --- GUI СИСТЕМА ---
    public static class BubbleMenu extends Screen {
        private int bindIdx = -1;
        public BubbleMenu() { super(Text.literal("Bubble")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int x = width/2 - 100, y = height/2 - 80;
            ctx.fill(x, y, x + 200, y + 160, 0xEE050505); 
            ctx.drawBorder(x, y, 200, 160, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT §7v2.5", width/2, y + 10, -1);
            
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyWP};

            for(int i = 0; i < 5; i++) {
                int iy = y + 35 + i * 22;
                boolean h = mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 18;
                ctx.fill(x + 10, iy, x + 190, iy + 18, h ? 0xFF252525 : 0xFF121212);
                String k = bindIdx == i ? "§b[???]" : (keys[i] != 0 ? " §7[" + GLFW.glfwGetKeyName(keys[i], 0).toUpperCase() + "]" : "");
                ctx.drawTextWithShadow(textRenderer, names[i] + k, x + 15, iy + 5, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i == 0 || i == 1 || i == 4) ctx.drawTextWithShadow(textRenderer, "⚙", x + 175, iy + 5, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 100, y = height/2 - 80;
            for(int i = 0; i < 5; i++) {
                int iy = y + 35 + i * 22;
                if(mx >= x + 10 && mx <= x + 190 && my >= iy && my <= iy + 18) {
                    if (b == 1) { bindIdx = i; return true; }
                    if(mx >= x + 170) {
                        if(i == 0) client.setScreen(new KillAuraSettings(this));
                        if(i == 1) client.setScreen(new TriggerSettings(this));
                        if(i == 4) client.setScreen(new WaypointSettings(this));
                    } else {
                        if(i == 0) killaura = !killaura; if(i == 1) triggerbot = !triggerbot;
                        if(i == 2) fullbright = !fullbright; if(i == 3) autoTotem = !autoTotem;
                        if(i == 4) waypointActive = !waypointActive;
                        saveConfig();
                    }
                    return true;
                }
            }
            return super.mouseClicked(mx, my, b);
        }

        @Override public boolean keyPressed(int k, int s, int m) {
            if (bindIdx != -1) {
                if (k == GLFW.GLFW_KEY_ESCAPE) k = 0;
                if (bindIdx == 0) keyKA = k; if (bindIdx == 1) keyTB = k;
                if (bindIdx == 2) keyFB = k; if (bindIdx == 3) keyAT = k;
                if (bindIdx == 4) keyWP = k;
                bindIdx = -1; saveConfig(); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // --- ПОДМЕНЮ НАСТРОЕК (ВЕРНУТО ПОЛНОСТЬЮ) ---
    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2, f3;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override protected void init() {
            f1 = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 60, 40, 14, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 40, 40, 14, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 20, 40, 14, Text.literal(""));
            f1.setText(""+kaRange); f2.setText(""+kaWallsRange); f3.setText(""+shakeIntensity);
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(width/2 - 110, height/2 - 90, width/2 + 110, height/2 + 90, 0xEE050505);
            ctx.drawTextWithShadow(textRenderer, "Reach:", width/2 - 100, height/2 - 57, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", width/2 - 100, height/2 - 37, -1);
            ctx.drawTextWithShadow(textRenderer, "Shake:", width/2 - 100, height/2 - 17, -1);
            drawBtn(ctx, "Sticky", sticky, height/2 + 5, mx, my);
            drawBtn(ctx, "AntiVelocity", antiVelocity, height/2 + 25, mx, my);
            drawBtn(ctx, "AutoRun", autoRun, height/2 + 45, mx, my);
            super.render(ctx, mx, my, d);
        }
        private void drawBtn(DrawContext ctx, String t, boolean v, int y, int mx, int my) {
            boolean h = mx >= width/2 - 100 && mx <= width/2 + 100 && my >= y && my <= y + 16;
            ctx.fill(width/2 - 100, y, width/2 + 100, y + 16, h ? 0xFF333333 : 0xFF151515);
            ctx.drawTextWithShadow(textRenderer, t + ": " + (v ? "§aON" : "§cOFF"), width/2 - 95, y + 4, -1);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (mx >= width/2 - 100 && mx <= width/2 + 100) {
                if (my >= height/2 + 5 && my <= height/2 + 21) sticky = !sticky;
                if (my >= height/2 + 25 && my <= height/2 + 41) antiVelocity = !antiVelocity;
                if (my >= height/2 + 45 && my <= height/2 + 61) autoRun = !autoRun;
                saveConfig(); return true;
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange=Double.parseDouble(f1.getText()); kaWallsRange=Double.parseDouble(f2.getText()); shakeIntensity=Float.parseFloat(f3.getText()); } catch(Exception e){}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen p;
        public TriggerSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(width/2 - 80, height/2 - 20, width/2 + 80, height/2 + 20, 0xEE050505);
            boolean h = mx >= width/2-70 && mx <= width/2+70 && my >= height/2-10 && my <= height/2+10;
            ctx.fill(width/2-75, height/2-15, width/2+75, height/2+15, h ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), width/2, height/2 - 4, -1);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (mx >= width/2-75 && mx <= width/2+75 && my >= height/2-15 && my <= height/2+15) { tbCrits = !tbCrits; saveConfig(); return true; }
            return false;
        }
        @Override public boolean keyPressed(int k, int s, int m) { if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(p); return true; } return false; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget fx, fy, fz;
        public WaypointSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override protected void init() {
            fx = new TextFieldWidget(textRenderer, width/2+10, height/2-40, 60, 14, Text.literal(""));
            fy = new TextFieldWidget(textRenderer, width/2+10, height/2-20, 60, 14, Text.literal(""));
            fz = new TextFieldWidget(textRenderer, width/2+10, height/2, 60, 14, Text.literal(""));
            fx.setText(""+(int)wpX); fy.setText(""+(int)wpY); fz.setText(""+(int)wpZ);
            addDrawableChild(fx); addDrawableChild(fy); addDrawableChild(fz);
        }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(width/2-90, height/2-70, width/2+90, height/2+50, 0xEE050505);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bSET WAYPOINT", width/2, height/2-60, -1);
            ctx.drawTextWithShadow(textRenderer, "X:", width/2-80, height/2-37, -1);
            ctx.drawTextWithShadow(textRenderer, "Y:", width/2-80, height/2-17, -1);
            ctx.drawTextWithShadow(textRenderer, "Z:", width/2-80, height/2+3, -1);
            super.render(ctx, mx, my, d);
        }
        @Override public boolean keyPressed(int k, int s, int n) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX=Double.parseDouble(fx.getText()); wpY=Double.parseDouble(fy.getText()); wpZ=Double.parseDouble(fz.getText()); } catch(Exception e){}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, n);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange+":"+kaWallsRange+":"+wpX+":"+wpY+":"+wpZ+":"+keyKA+":"+keyTB+":"+keyFB+":"+keyAT+":"+keyWP+":"+sticky+":"+antiVelocity+":"+autoRun+":"+tbCrits+":"+shakeIntensity);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 15) {
                kaRange=Double.parseDouble(p[0]); kaWallsRange=Double.parseDouble(p[1]);
                wpX=Double.parseDouble(p[2]); wpY=Double.parseDouble(p[3]); wpZ=Double.parseDouble(p[4]);
                keyKA=Integer.parseInt(p[5]); keyTB=Integer.parseInt(p[6]); keyFB=Integer.parseInt(p[7]);
                keyAT=Integer.parseInt(p[8]); keyWP=Integer.parseInt(p[9]);
                sticky=Boolean.parseBoolean(p[10]); antiVelocity=Boolean.parseBoolean(p[11]);
                autoRun=Boolean.parseBoolean(p[12]); tbCrits=Boolean.parseBoolean(p[13]);
                shakeIntensity=Float.parseFloat(p[14]);
            }
        } catch (Exception ignored) {}
    }
}

