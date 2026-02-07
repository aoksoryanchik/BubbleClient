package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
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
import java.util.function.Function;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true, targetHudActive = true;

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
    private float animatedHP = 0;

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
        if (waypointActive) renderWaypointArrow(ctx);
        if (targetHudActive) renderTargetHUD(ctx);
    }

    private void runAura(MinecraftClient client) {
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isInvisible() || p.isCreative()) continue;
            
            double yawToTarget = Math.toDegrees(Math.atan2(p.getZ() - client.player.getZ(), p.getX() - client.player.getX())) - 90;
            double angleDiff = Math.abs(MathHelper.wrapDegrees(yawToTarget - client.player.getYaw()));
            if (angleDiff > kaFOV / 2.0) continue;

            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; target = p;
            }
        }
        
        currentTarget = target;
        if (target != null) {
            double s = shakeIntensity * 0.15;
            Vec3d tPos = target.getPos().add((random.nextDouble()-0.5)*s, target.getHeight()*(0.4+random.nextDouble()*0.4), (random.nextDouble()-0.5)*s);
            Vec3d diff = tPos.subtract(client.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
            float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
            
            client.player.setYaw(client.player.getYaw() + MathHelper.wrapDegrees(yaw - client.player.getYaw()));
            client.player.setPitch(client.player.getPitch() + MathHelper.wrapDegrees(pitch - client.player.getPitch()));
            
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, kaRange);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= 0.95f) {
                if (!tbCrits || (client.player.fallDistance > 0 && !client.player.isOnGround())) {
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

        int x = thX, y = thY, w = 110, h = 36;
        
        // Фон Nursultan Style
        ctx.fill(x, y, x + w, y + h, 0x90101010); 

        // Рендер ГОЛОВЫ (Исправленный метод для 1.20+)
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (entry != null) {
            Identifier skin = entry.getSkinTextures().texture();
            // Исправление ошибки "incompatible types"
            ctx.drawTexture(RenderLayer::getGuiTextured, skin, x + 4, y + 4, 8, 8, 28, 28, 64, 64);
            ctx.drawTexture(RenderLayer::getGuiTextured, skin, x + 4, y + 4, 40, 8, 28, 28, 64, 64);
        }

        ctx.drawTextWithShadow(client.textRenderer, entity.getName().getString(), x + 38, y + 4, -1);
        
        // Плавная полоска с переливом (Градиент)
        float hp = entity.getHealth() / entity.getMaxHealth();
        animatedHP += (hp - animatedHP) * 0.1f;
        
        int barX = x + 38, barY = y + 16, barW = 65, barH = 4;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0x40303030);
        
        int currentW = (int)(barW * animatedHP);
        long time = System.currentTimeMillis();
        
        for (int i = 0; i < currentW; i++) {
            float ratio = (float)i / barW;
            // Математика градиента Nursultan
            float wave = (float) Math.sin((time / 400.0) + (i / 8.0)) * 0.5f + 0.5f;
            int r = (int)(200 + wave * 55);
            int g = (int)(200 + wave * 55);
            int b = (int)(200 + wave * 55);
            int color = (255 << 24) | (r << 16) | (g << 8) | b;
            ctx.fill(barX + i, barY, barX + i + 1, barY + barH, color);
        }
        
        ctx.drawText(client.textRenderer, String.format("%.1f", entity.getHealth()), barX, barY + 8, 0xFFAAAAAA, false);
    }

    private void renderWaypointArrow(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d targetVec = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
        float tYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90F;
        float diff = MathHelper.wrapDegrees(tYaw - client.player.getYaw());
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(client.getWindow().getScaledWidth()/2f, client.getWindow().getScaledHeight()/2f-35, 0);
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

    // --- КЛАССЫ МЕНЮ И НАСТРОЕК (БЕЗ СОКРАЩЕНИЙ) ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x80000000);
            int x = width/2-100, y = height/2-110;
            ctx.fill(x, y, x+200, y+190, 0xFF0B0B0B);
            ctx.drawBorder(x, y, 200, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y+10, -1);
            
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "TargetHUD"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive, targetHudActive};
            
            for(int i=0; i<6; i++) {
                int iy = y+35+i*23;
                boolean hover = mx>=x+10 && mx<=x+190 && my>=iy && my<=iy+20;
                ctx.fill(x+10, iy, x+190, iy+20, hover ? 0xFF181818 : 0xFF121212);
                ctx.drawTextWithShadow(textRenderer, names[i], x+15, iy+6, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i==0 || i==1 || i==4) ctx.drawTextWithShadow(textRenderer, "⚙", x+175, iy+6, -1);
            }
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-100, y = height/2-110;
            for(int i=0; i<6; i++) {
                int iy = y+35+i*23;
                if(mx>=x+10 && mx<=x+190 && my>=iy && my<=iy+20) {
                    if(mx > x+160 && i==0) client.setScreen(new KillAuraSettings(this));
                    else if(mx > x+160 && i==1) client.setScreen(new TriggerSettings(this));
                    else if(mx > x+160 && i==4) client.setScreen(new WaypointSettings(this));
                    else {
                        if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot; if(i==2) fullbright=!fullbright;
                        if(i==3) autoTotem=!autoTotem; if(i==4) waypointActive=!waypointActive; if(i==5) targetHudActive=!targetHudActive;
                        if(b != 0) client.setScreen(new BindScreen(this, i));
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
            int x = width/2-110, y = height/2-90;
            ctx.fill(x, y, x+220, y+160, 0xFF0B0B0B);
            ctx.drawBorder(x, y, 220, 160, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lAURA CONFIG", width/2, y+10, -1);
            
            drawSlider(ctx, "Range", kaRange, y+40, mx, my);
            drawSlider(ctx, "Walls", kaWallsRange, y+65, mx, my);
            drawSlider(ctx, "FOV", kaFOV, y+90, mx, my);
            drawSlider(ctx, "Shake", (double)shakeIntensity, y+115, mx, my);
            
            boolean hoverHud = mx >= x+10 && mx <= x+210 && my >= y+138 && my <= y+155;
            ctx.fill(x+10, y+138, x+210, y+155, hoverHud ? 0xFF00AAFF : 0xFF151515);
            ctx.drawCenteredTextWithShadow(textRenderer, "MOVE HUD", width/2, y+142, hoverHud ? -1 : 0xFF00AAFF);
        }
        private void drawSlider(DrawContext ctx, String name, double val, int y, int mx, int my) {
            ctx.drawTextWithShadow(textRenderer, name + ": " + String.format("%.1f", val), width/2-100, y, -1);
            ctx.fill(width/2-100, y+12, width/2+100, y+14, 0xFF202020);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int y = height/2-90;
            if(my >= y+40 && my <= y+55) kaRange = (mx < width/2) ? Math.max(0, kaRange-0.1) : kaRange+0.1;
            if(my >= y+65 && my <= y+80) kaWallsRange = (mx < width/2) ? Math.max(0, kaWallsRange-0.1) : kaWallsRange+0.1;
            if(my >= y+90 && my <= y+105) kaFOV = (mx < width/2) ? Math.max(0, kaFOV-10) : Math.min(360, kaFOV+10);
            if(my >= y+115 && my <= y+130) shakeIntensity = (float) ((mx < width/2) ? Math.max(0, shakeIntensity-0.1) : shakeIntensity+0.1);
            if(mx >= width/2-100 && mx <= width/2+100 && my >= y+138 && my <= y+155) client.setScreen(new HudEditor(this));
            saveConfig(); return true;
        }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class HudEditor extends Screen {
        private final Screen parent;
        public HudEditor(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.drawCenteredTextWithShadow(textRenderer, "DRAG HUD WITH LEFT CLICK", width/2, 20, 0xFF00AAFF);
            if(GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == 1) { thX = mx-20; thY = my-20; }
        }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class TriggerSettings extends Screen {
        private final Screen parent; public TriggerSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) { ctx.fill(0,0,width,height,0xAA000000); ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + tbCrits, width/2, height/2, -1); }
        @Override public boolean mouseClicked(double mx, double my, int b) { tbCrits = !tbCrits; saveConfig(); return true; }
        @Override public boolean keyPressed(int k, int s, int m) { if(k == 256) client.setScreen(parent); return true; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen parent; public WaypointSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override public void render(DrawContext ctx, int mx, int my, float d) { ctx.fill(0,0,width,height,0xAA000000); ctx.drawCenteredTextWithShadow(textRenderer, "X: "+(int)wpX+" Y: "+(int)wpY+" Z: "+(int)wpZ, width/2, height/2, -1); }
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
        @Override public void render(DrawContext ctx, int mx, int my, float d) { ctx.drawCenteredTextWithShadow(textRenderer, "PRESS ANY KEY", width/2, height/2, -1); }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + thX + ":" + thY + ":" + targetHudActive + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + shakeIntensity + ":" + antiVelocity + ":" + tbCrits + ":" + kaFOV);
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
                    targetHudActive=Boolean.parseBoolean(p[7]); keyKA=Integer.parseInt(p[9]);
                    tbCrits=Boolean.parseBoolean(p[16]); kaFOV=Double.parseDouble(p[17]);
                }
            }
        } catch (Exception ignored) {}
    }
}

