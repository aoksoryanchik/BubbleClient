package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
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
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true;

    public static double kaRange = 3.8, kaWallsRange = 3.0, kaFOV = 360.0;
    public static float shakeIntensity = 0.5f;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static int thX = 50, thY = 50; 
    
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, 
                      keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();
    public static PlayerEntity currentTarget = null;
    
    // Для плавной полоски здоровья
    private float animatedHealth = 0f;

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();
            
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());
            
            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }
            
            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && client.player.input.movementForward > 0) client.player.setSprinting(true);
            
            if (killaura) runAura(client); else currentTarget = null;
            if (triggerbot) runTrigger(client);
        });
        HudRenderCallback.EVENT.register(this::renderEverything);
    }

    private void renderEverything(DrawContext ctx, RenderTickCounter tick) {
        renderWaypointArrow(ctx);
        renderTargetHUD(ctx);
    }

    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isInvisible() || p.isCreative()) continue;
            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }
        currentTarget = target;
        if (target != null && client.player.getAttackCooldownProgress(0) >= 0.92f) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, kaRange);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                // Жесткая проверка на крит: игрок должен падать
                boolean isFalling = !client.player.isOnGround() && client.player.fallDistance > 0.0f && !client.player.isClimbing() && !client.player.isTouchingWater();
                if (!tbCrits || isFalling) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private EntityHitResult raycastEntity(MinecraftClient client, double range) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).multiply(range);
        Box box = client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0);
        return ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e.isAlive(), range * range);
    }

    private void renderTargetHUD(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (currentTarget == null && !(client.currentScreen instanceof HudEditor)) return;
        
        PlayerEntity entity = (currentTarget != null) ? currentTarget : client.player;
        
        // Плавная анимация полоски
        float targetHP = entity.getHealth() / entity.getMaxHealth();
        animatedHealth += (targetHP - animatedHealth) * 0.1f;

        int x = thX, y = thY;
        int w = 120, h = 35;

        // Фон в стиле Nursultan (темно-серый полупрозрачный)
        ctx.fill(x, y, x + w, y + h, 0x99151515);
        ctx.fill(x, y + h - 2, x + w, y + h, 0x44000000); // Тень внизу

        // Отрисовка ГОЛОВЫ (используем смещение по Y, чтобы скрыть туловище)
        ctx.getMatrices().push();
        // Метод drawEntity рисует центр сущности, поэтому мы "запихиваем" тело под худ, оставляя голову
        InventoryScreen.drawEntity(ctx, x + 15, y + 32, x + 15, y + 32, 22, 0.0625f, 0, 0, entity);
        ctx.getMatrices().pop();

        // Имя
        ctx.drawTextWithShadow(client.textRenderer, entity.getName().getString(), x + 35, y + 5, -1);
        
        // Полоска здоровья (Серая подложка + Градиентная линия)
        int barWidth = w - 45;
        int barX = x + 35;
        int barY = y + 18;
        
        ctx.fill(barX, barY, barX + barWidth, barY + 4, 0x55333333); // Темно-серая база
        
        // Рисуем полоску с закосом под градиент (бело-голубой)
        int currentBarW = (int)(barWidth * animatedHealth);
        ctx.fill(barX, barY, barX + currentBarW, barY + 4, 0xFFFFFFFF); // Белый верхний слой
        ctx.fill(barX, barY + 1, barX + currentBarW, barY + 4, 0xFF00AAFF); // Голубой низ (эффект градиента)
        
        // Текст ХП
        String hpText = (int)entity.getHealth() + " HP";
        ctx.drawText(client.textRenderer, hpText, barX + barWidth - client.textRenderer.getWidth(hpText), y + 24, 0xFFAAAAAA, false);
    }

    private void renderWaypointArrow(DrawContext ctx) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d targetVec = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
        float tYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90F;
        float diff = MathHelper.wrapDegrees(tYaw - client.player.getYaw());
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(client.getWindow().getScaledWidth()/2f, client.getWindow().getScaledHeight()/2f-30, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(diff));
        ctx.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, 0, 0xFF00AAFF);
        ms.pop();
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

    private void sendNotify(String mod, boolean s) {
        if (MinecraftClient.getInstance().player != null) 
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + mod + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if(k==GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    // --- ОБНОВЛЕННОЕ МЕНЮ (БОЛЕЕ КРАСИВОЕ) ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x60000000); // Мягкое размытие (визуальное)
            
            int x = width/2 - 110;
            int y = height/2 - 90;
            int w = 220;
            int h = 180;

            // Основное окно
            ctx.fill(x, y, x + w, y + h, 0xFF121212);
            ctx.fill(x, y, x + w, y + 2, 0xFF00AAFF); // Полоска сверху
            
            ctx.drawCenteredTextWithShadow(textRenderer, "B U B B L E", x + w/2, y + 10, 0xFF00AAFF);
            ctx.fill(x + 10, y + 25, x + w - 10, y + 26, 0x33FFFFFF); // Разделитель

            String[] mods = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            
            for(int i=0; i<5; i++) {
                int btnY = y + 35 + (i * 28);
                boolean hover = mx >= x + 10 && mx <= x + w - 10 && my >= btnY && my <= btnY + 22;
                
                // Кнопка модуля
                ctx.fill(x + 10, btnY, x + w - 10, btnY + 22, hover ? 0xFF252525 : 0xFF1A1A1A);
                ctx.drawTextWithShadow(textRenderer, mods[i], x + 20, btnY + 7, states[i] ? 0xFF00FF00 : 0xFFBBBBBB);
                
                // Переключатель (Чекбокс)
                ctx.fill(x + w - 30, btnY + 6, x + w - 15, btnY + 16, states[i] ? 0xFF00AAFF : 0xFF333333);
                
                if(i == 0 || i == 1) { // Иконка настроек для Кауры и Триггера
                    ctx.drawTextWithShadow(textRenderer, "§b#", x + w - 50, btnY + 7, -1);
                }
            }
        }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 110;
            int y = height/2 - 90;
            for(int i=0; i<5; i++) {
                int btnY = y + 35 + (i * 28);
                if(mx >= x + 10 && mx <= x + 210 && my >= btnY && my <= btnY + 22) {
                    if(mx > x + 160 && (i == 0 || i == 1)) {
                         if(i == 0) client.setScreen(new KillAuraSettings(this));
                         if(i == 1) client.setScreen(new TriggerSettings(this));
                    } else {
                         if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot;
                         if(i==2) fullbright=!fullbright; if(i==3) autoTotem=!autoTotem;
                         if(i==4) waypointActive=!waypointActive;
                    }
                    saveConfig(); return true;
                }
            }
            return false;
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0,0,width,height,0x90000000);
            int x = width/2-100, y = height/2-80;
            ctx.fill(x, y, x+200, y+140, 0xFF121212);
            ctx.drawCenteredTextWithShadow(textRenderer, "KILL AURA CFG", width/2, y+10, 0xFF00AAFF);
            
            ctx.drawTextWithShadow(textRenderer, "Range: " + kaRange, x+20, y+40, -1);
            ctx.drawTextWithShadow(textRenderer, "FOV: " + (int)kaFOV, x+20, y+60, -1);
            
            boolean hHud = mx >= x+20 && mx <= x+180 && my >= y+90 && my <= y+110;
            ctx.fill(x+20, y+90, x+180, y+110, hHud ? 0xFF00AAFF : 0xFF222222);
            ctx.drawCenteredTextWithShadow(textRenderer, "MOVE HUD", width/2, y+96, -1);
        }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-100, y = height/2-80;
            if(mx >= x+20 && mx <= x+180 && my >= y+90 && my <= y+110) client.setScreen(new HudEditor(this));
            return super.mouseClicked(mx, my, b);
        }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class HudEditor extends Screen {
        private final Screen parent;
        public HudEditor(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x44000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "Hold LEFT CLICK to move HUD", width/2, 20, -1);
            if(GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == 1) {
                thX = mx - 60; thY = my - 17;
            }
        }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) { saveConfig(); client.setScreen(parent); } return true; }
    }

    public static class TriggerSettings extends Screen {
        private final Screen parent;
        public TriggerSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0,0,width,height,0x90000000);
            ctx.fill(width/2-60, height/2-30, width/2+60, height/2+30, 0xFF121212);
            ctx.drawCenteredTextWithShadow(textRenderer, "ONLY CRITS", width/2, height/2-10, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, tbCrits ? "§aENABLED" : "§cDISABLED", width/2, height/2+5, -1);
        }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override public boolean mouseClicked(double mx, double my, int b) { tbCrits = !tbCrits; saveConfig(); return true; }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen parent; public WaypointSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0,0,width,height,0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "X: "+(int)wpX+" Y: "+(int)wpY+" Z: "+(int)wpZ, width/2, height/2, -1);
        }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.parent = p; this.id = id; }
        @Override public boolean keyPressed(int k, int s, int m) {
            if(k == 256) k = 0;
            if(id==0) keyKA=k; if(id==1) keyTB=k; if(id==2) keyFB=k; if(id==3) keyAT=k; if(id==4) keyWP=k;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0,0,width,height,0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS KEY TO BIND", width/2, height/2, -1);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + thX + ":" + thY + ":0:" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + shakeIntensity + ":" + antiVelocity + ":" + tbCrits + ":" + kaFOV);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            if (!lines.isEmpty()) {
                String[] p = lines.get(0).split(":");
                if(p.length >= 18) {
                    kaRange=Double.parseDouble(p[0]); thX=Integer.parseInt(p[5]); thY=Integer.parseInt(p[6]);
                    tbCrits=Boolean.parseBoolean(p[16]); kaFOV=Double.parseDouble(p[17]);
                    keyKA=Integer.parseInt(p[9]); keyTB=Integer.parseInt(p[10]);
                }
            }
        } catch (Exception ignored) {}
    }
}

