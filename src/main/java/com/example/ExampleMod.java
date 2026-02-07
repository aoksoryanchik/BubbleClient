package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
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
    public static int thX = 10, thY = 10; 
    
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, 
                      keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();
    public static PlayerEntity currentTarget = null;
    
    // Анимация полоски здоровья
    private float lastHealth = 0;

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
            
            if (kaFOV < 360.0) {
                double yawToTarget = Math.toDegrees(Math.atan2(p.getZ() - client.player.getZ(), p.getX() - client.player.getX())) - 90;
                double angleDiff = Math.abs(MathHelper.wrapDegrees(yawToTarget - client.player.getYaw()));
                if (angleDiff > kaFOV / 2.0) continue;
            }

            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }
        
        currentTarget = target;
        if (target != null) {
            // Наводка (Rotation) + Тряска (Shake)
            double s = shakeIntensity * 0.15;
            Vec3d tPos = target.getPos().add((random.nextDouble()-0.5)*s, target.getHeight()*(0.35+random.nextDouble()*0.45), (random.nextDouble()-0.5)*s);
            Vec3d diff = tPos.subtract(client.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
            float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
            
            client.player.setYaw(client.player.getYaw() + MathHelper.wrapDegrees(yaw - client.player.getYaw()));
            client.player.setPitch(client.player.getPitch() + MathHelper.wrapDegrees(pitch - client.player.getPitch()));
            
            if (client.player.getAttackCooldownProgress(0) >= 0.92f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, kaRange);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= 0.92f) {
                boolean isFalling = client.player.fallDistance > 0.0f && !client.player.isOnGround() && !client.player.isClimbing() && !client.player.isTouchingWater();
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

        int x = thX, y = thY;
        int w = 140, h = 42;

        // Фон Nursultan Style
        ctx.fill(x, y, x + w, y + h, 0xAA0A0A0A); 
        ctx.drawBorder(x, y, w, h, 0xFF333333); 

        // ОТРИСОВКА ТОЛЬКО ГОЛОВЫ через скин
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (entry != null) {
            Identifier skin = entry.getSkinTextures().texture();
            // Рисуем голову (8,8,8,8 область в скине)
            ctx.drawTexture(skin, x + 5, y + 5, 32, 32, 8, 8, 8, 8, 64, 64);
            // Рисуем второй слой головы (шлем), если есть
            ctx.drawTexture(skin, x + 5, y + 5, 32, 32, 40, 8, 8, 8, 64, 64);
        }

        ctx.drawTextWithShadow(client.textRenderer, entity.getName().getString(), x + 42, y + 7, -1);
        
        // Полоска здоровья (Плавный градиент Nursultan)
        int barX = x + 42;
        int barY = y + 20;
        int maxBarW = w - 50;
        
        float hp = entity.getHealth() / entity.getMaxHealth();
        if (Math.abs(lastHealth - hp) > 0.001) lastHealth += (hp - lastHealth) * 0.1f;
        
        // Подложка полоски
        ctx.fill(barX, barY, barX + maxBarW, barY + 5, 0xFF222222);
        
        // Рендер градиента (Серый -> Белый)
        int currentW = (int)(maxBarW * lastHealth);
        for (int i = 0; i < currentW; i++) {
            float ratio = (float)i / maxBarW;
            int color = lerpColor(0xFFAAAAAA, 0xFFFFFFFF, ratio);
            ctx.fill(barX + i, barY, barX + i + 1, barY + 5, color);
        }
        
        String hpString = String.format("%.1f", entity.getHealth());
        ctx.drawText(client.textRenderer, hpString, barX + maxBarW - client.textRenderer.getWidth(hpString), y + 28, 0xFFAAAAAA, false);
    }

    private int lerpColor(int c1, int c2, float t) {
        int a = (int)((c1 >> 24 & 0xFF) * (1 - t) + (c2 >> 24 & 0xFF) * t);
        int r = (int)((c1 >> 16 & 0xFF) * (1 - t) + (c2 >> 16 & 0xFF) * t);
        int g = (int)((c1 >> 8 & 0xFF) * (1 - t) + (c2 >> 8 & 0xFF) * t);
        int b = (int)((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
        ctx.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, 0, (Math.abs(diff)<15)?0xFF00AAFF:-1);
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

    // --- GUI (ВОССТАНОВЛЕНО И УЛУЧШЕНО) ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2-95, y = height/2-105;
            ctx.fill(x, y, x+190, y+160, 0xFF0D0D0D); // Темный фон
            ctx.drawBorder(x, y, 190, 160, 0xFF00AAFF); // Синий акцент
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y+10, -1);
            
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyWP};
            
            for(int i=0; i<5; i++) {
                int iy = y+38+i*23;
                boolean h = mx>=x+10 && mx<=x+180 && my>=iy && my<=iy+19;
                ctx.fill(x+10, iy, x+180, iy+19, h ? 0xFF1A1A1A : 0xFF121212);
                String kN = keys[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(keys[i], 0).toUpperCase();
                ctx.drawTextWithShadow(textRenderer, names[i] + " §7[" + kN + "]", x+15, iy+5, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i==0 || i==1 || i==4) ctx.drawTextWithShadow(textRenderer, "⚙", x+170, iy+5, -1);
            }
        }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-95, y = height/2-105;
            for(int i=0; i<5; i++) {
                int iy = y+38+i*23;
                if(mx>=x+160 && mx<=x+180 && my>=iy && my<=iy+19) {
                    if(i==0) client.setScreen(new KillAuraSettings(this));
                    if(i==1) client.setScreen(new TriggerSettings(this));
                    if(i==4) client.setScreen(new WaypointSettings(this));
                    return true;
                } else if(mx>=x+10 && mx<=x+160 && my>=iy && my<=iy+19) {
                    if(b == 0) {
                        if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot; if(i==2) fullbright=!fullbright;
                        if(i==3) autoTotem=!autoTotem; if(i==4) waypointActive=!waypointActive;
                    } else client.setScreen(new BindScreen(this, i));
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
            ctx.fill(0, 0, width, height, 0xBF000000); 
            int x = width/2, y = height/2;
            ctx.fill(x-110, y-100, x+110, y+110, 0xFF0D0D0D);
            ctx.drawBorder(x-110, y-100, 220, 210, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lAURA SETTINGS", x, y-90, -1);
            
            drawSetting(ctx, "Range:", kaRange, x-100, y-60, x+40, mx, my);
            drawSetting(ctx, "Walls:", kaWallsRange, x-100, y-40, x+40, mx, my);
            drawSetting(ctx, "FOV:", kaFOV, x-100, y-20, x+40, mx, my);
            drawSetting(ctx, "Shake:", (double)shakeIntensity, x-100, y, x+40, mx, my);
            
            boolean hHud = mx>=x-100 && mx<=x+100 && my>=y+60 && my<=y+75;
            ctx.fill(x-100, y+60, x+100, y+75, hHud ? 0xFF333333 : 0xFF151515);
            ctx.drawCenteredTextWithShadow(textRenderer, "EDIT HUD", x, y+64, hHud ? 0xFF00AAFF : -1);
        }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        private void drawSetting(DrawContext ctx, String label, double val, int x, int y, int startX, int mx, int my) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, -1);
            ctx.drawTextWithShadow(textRenderer, "<", startX, y, mx>=startX && mx<=startX+10 && my>=y && my<=y+10 ? 0xFF00AAFF : -1);
            ctx.drawTextWithShadow(textRenderer, ">", startX+50, y, mx>=startX+50 && mx<=startX+60 && my>=y && my<=y+10 ? 0xFF00AAFF : -1);
            String s = (label.equals("FOV:")) ? String.valueOf((int)val) : String.format("%.1f", val);
            ctx.drawTextWithShadow(textRenderer, s, startX + 25 - textRenderer.getWidth(s)/2, y, -1);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2, bx = x+40;
            if(my>=y-60 && my<=y-50) { if(mx>=bx && mx<=bx+15) kaRange-=0.1; if(mx>=bx+45 && mx<=bx+60) kaRange+=0.1; }
            if(my>=y-40 && my<=y-30) { if(mx>=bx && mx<=bx+15) kaWallsRange-=0.1; if(mx>=bx+45 && mx<=bx+60) kaWallsRange+=0.1; }
            if(my>=y-20 && my<=y-10) { if(mx>=bx && mx<=bx+15) kaFOV-=10; if(mx>=bx+45 && mx<=bx+60) kaFOV+=10; }
            if(my>=y && my<=y+10) { if(mx>=bx && mx<=bx+15) shakeIntensity-=0.1f; if(mx>=bx+45 && mx<=bx+60) shakeIntensity+=0.1f; }
            if(mx>=x-100 && mx<=x+100 && my>=y+60 && my<=y+75) client.setScreen(new HudEditor(this));
            saveConfig(); return super.mouseClicked(mx, my, b);
        }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class HudEditor extends Screen {
        private final Screen parent;
        public HudEditor(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x44000000); 
            ctx.drawCenteredTextWithShadow(textRenderer, "DRAG HUD (RIGHT CLICK)", width/2, 50, -1);
            if(GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 1) == 1) {
                thX = mx - 70; thY = my - 20;
            }
        }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) { saveConfig(); client.setScreen(parent); } return true; }
    }

    public static class TriggerSettings extends Screen {
        private final Screen parent; public TriggerSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) { 
            ctx.fill(0,0,width,height,0x90000000); 
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), width/2, height/2, -1); 
        }
        @Override public boolean mouseClicked(double mx, double my, int b) { tbCrits = !tbCrits; saveConfig(); return true; }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen parent; public WaypointSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) { ctx.fill(0,0,width,height,0x90000000); ctx.drawCenteredTextWithShadow(textRenderer, "X: "+(int)wpX+" Y: "+(int)wpY+" Z: "+(int)wpZ, width/2, height/2, -1); }
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
        @Override public void render(DrawContext ctx, int mx, int my, float d) { ctx.fill(0,0,width,height,0x90000000); ctx.drawCenteredTextWithShadow(textRenderer, "PRESS KEY", width/2, height/2, -1); }
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
                    kaRange=Double.parseDouble(p[0]); kaWallsRange=Double.parseDouble(p[1]);
                    thX=Integer.parseInt(p[5]); thY=Integer.parseInt(p[6]);
                    keyKA=Integer.parseInt(p[9]); keyTB=Integer.parseInt(p[10]);
                    keyFB=Integer.parseInt(p[11]); keyAT=Integer.parseInt(p[12]); keyWP=Integer.parseInt(p[13]);
                    tbCrits=Boolean.parseBoolean(p[16]); kaFOV=Double.parseDouble(p[17]);
                }
            }
        } catch (Exception ignored) {}
    }
}

