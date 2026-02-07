package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true;

    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static float shakeIntensity = 0.5f;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, 
                      keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

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
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) client.player.setSprinting(true);
            
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });
        HudRenderCallback.EVENT.register(this::renderWaypointArrow);
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
        if (target != null) {
            double s = shakeIntensity * 0.15;
            double rX = (random.nextDouble() - 0.5) * s;
            double rZ = (random.nextDouble() - 0.5) * s;
            Vec3d tPos = target.getPos().add(rX, target.getHeight() * (0.35 + random.nextDouble() * 0.45), rZ);
            Vec3d diff = tPos.subtract(client.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
            float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
            client.player.setYaw(client.player.getYaw() + MathHelper.wrapDegrees(yaw - client.player.getYaw()));
            client.player.setPitch(client.player.getPitch() + MathHelper.wrapDegrees(pitch - client.player.getPitch()));
            
            EntityHitResult hit = raycastEntity(client, kaRange);
            if (hit != null && hit.getEntity() == target) {
                if (client.player.getAttackCooldownProgress(0) >= 0.92f) {
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
        return ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e.isAlive() && !e.isSpectator(), range * range);
    }

    private void renderWaypointArrow(DrawContext ctx, RenderTickCounter tick) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Vec3d targetVec = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
        float tYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90F;
        float diff = MathHelper.wrapDegrees(tYaw - client.player.getYaw());
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(client.getWindow().getScaledWidth() / 2f, client.getWindow().getScaledHeight() / 2f - 30, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(diff));
        ctx.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, 0, (Math.abs(diff) < 15) ? 0xFF00AAFF : -1);
        ms.pop();
        double d = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
        ctx.drawCenteredTextWithShadow(client.textRenderer, String.format("§f%.0fm", d), client.getWindow().getScaledWidth()/2, client.getWindow().getScaledHeight()/2-45, -1);
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

    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, kaRange);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            // ФИКС ТРИГГЕРБОТА: проверяем условия и бьем
            boolean canAttack = client.player.getAttackCooldownProgress(0) >= 0.95f;
            boolean crit = !tbCrits || (client.player.fallDistance > 0.05f && !client.player.isOnGround());
            
            if (canAttack && crit) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
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

    // --- GUI ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2-90, y = height/2-105;
            ctx.fill(x, y, x+180, y+155, 0xFF050505);
            ctx.drawBorder(x, y, 180, 155, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y+10, -1);
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyWP};
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                boolean h = mx>=x+10 && mx<=x+170 && my>=iy && my<=iy+18;
                ctx.fill(x+10, iy, x+170, iy+18, h ? 0xFF1A1A1A : 0xFF101010);
                String kN = keys[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(keys[i], 0).toUpperCase();
                ctx.drawTextWithShadow(textRenderer, names[i] + " §7[" + kN + "]", x+15, iy+5, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i==0 || i==1 || i==4) ctx.drawTextWithShadow(textRenderer, "⚙", x+155, iy+5, -1);
            }
        }
        
        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            // ПУСТО: Чтобы не было размытия
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-90, y = height/2-105;
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                if(mx>=x+150 && mx<=x+170 && my>=iy && my<=iy+18) {
                    if(i==0) client.setScreen(new KillAuraSettings(this));
                    if(i==1) client.setScreen(new TriggerSettings(this));
                    if(i==4) client.setScreen(new WaypointSettings(this));
                    return true;
                } else if(mx>=x+10 && mx<=x+150 && my>=iy && my<=iy+18) {
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

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            // ФИКС РАЗМЫТИЯ: Используем плотный черный цвет
            ctx.fill(0, 0, width, height, 0xBF000000); 
            int x = width/2, y = height/2;
            
            ctx.fill(x-115, y-95, x+115, y+110, 0xFF050505);
            ctx.drawBorder(x-115, y-95, 230, 205, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lKILL AURA SETTINGS", x, y-85, -1);
            
            // Отрисовка с выровненными цифрами
            drawSetting(ctx, "Range:", kaRange, x-105, y-41, x+50, mx, my);
            drawSetting(ctx, "WallsRange:", kaWallsRange, x-105, y-18, x+50, mx, my);
            drawSetting(ctx, "Shake:", (double)shakeIntensity, x-105, y+5, x+50, mx, my);
            
            drawStatus(ctx, "Auto-run:", x-105, y+35, autoRun, mx, my);
            drawStatus(ctx, "AntiVelocity:", x-105, y+55, antiVelocity, mx, my);
            
            int cx = x-240;
            ctx.fill(cx, y-95, cx+120, y+90, 0xFF050505);
            ctx.drawBorder(cx, y-95, 120, 185, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bCONFIGS", cx+60, y-85, -1);
            drawBtn(ctx, "MineBlaze", cx+10, y-50, mx, my);
            drawBtn(ctx, "AresMine", cx+10, y-25, mx, my);
            
            super.render(ctx, mx, my, d);
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            // ПУСТО: Блокируем блюр
        }

        private void drawSetting(DrawContext ctx, String label, double val, int x, int y, int startX, int mx, int my) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, -1);
            int arrowLX = startX;
            int arrowRX = startX + 50;
            
            boolean hL = mx >= arrowLX && mx <= arrowLX + 10 && my >= y && my <= y + 10;
            boolean hR = mx >= arrowRX && mx <= arrowRX + 10 && my >= y && my <= y + 10;
            
            ctx.drawTextWithShadow(textRenderer, "<", arrowLX, y, hL ? 0xFF00AAFF : -1);
            ctx.drawTextWithShadow(textRenderer, ">", arrowRX, y, hR ? 0xFF00AAFF : -1);
            
            // ИДЕАЛЬНАЯ ЦЕНТРОВКА
            String s = String.format("%.1f", val).replace(".", ",");
            int textW = textRenderer.getWidth(s);
            // Середина между стрелками = (LX + 10 + RX) / 2 = (startX + 60) / 2 = startX + 30
            int centerPos = startX + 30; 
            ctx.drawTextWithShadow(textRenderer, s, centerPos - (textW / 2), y, -1);
        }

        private void drawStatus(DrawContext ctx, String t, int x, int y, boolean s, int mx, int my) {
            boolean h = mx>=x && mx<=x+120 && my>=y && my<=y+10;
            ctx.drawTextWithShadow(textRenderer, t, x, y, h ? 0xFF00AAFF : -1);
            ctx.drawTextWithShadow(textRenderer, s ? "§aВКЛ" : "§cВЫКЛ", x+85, y, -1);
        }

        private void drawBtn(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx>=x && mx<=x+100 && my>=y && my<=y+18;
            ctx.fill(x, y, x+100, y+18, h ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x+50, y+5, h ? 0xFF00AAFF : -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2, cx = x-240;
            int bx = x+50;
            if(my>=y-41 && my<=y-31) {
                if(mx>=bx && mx<=bx+15) kaRange -= 0.1;
                if(mx>=bx+45 && mx<=bx+60) kaRange += 0.1;
            }
            if(my>=y-18 && my<=y-8) {
                if(mx>=bx && mx<=bx+15) kaWallsRange -= 0.1;
                if(mx>=bx+45 && mx<=bx+60) kaWallsRange += 0.1;
            }
            if(my>=y+5 && my<=y+15) {
                if(mx>=bx && mx<=bx+15) shakeIntensity -= 0.1f;
                if(mx>=bx+45 && mx<=bx+60) shakeIntensity += 0.1f;
            }
            if(mx>=x-105 && mx<=x+20) {
                if(my>=y+35 && my<=y+45) autoRun = !autoRun;
                if(my>=y+55 && my<=y+65) antiVelocity = !antiVelocity;
            }
            if(mx>=cx+10 && mx<=cx+110) {
                if(my>=y-50 && my<=y-32) { kaRange=3.1; kaWallsRange=0.0; shakeIntensity=0.2f; }
                if(my>=y-25 && my<=y-7) { kaRange=3.8; kaWallsRange=3.0; shakeIntensity=0.6f; }
            }
            saveConfig(); return super.mouseClicked(mx, my, b);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(parent); return true; }
            return super.keyPressed(k, s, m);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen parent; public TriggerSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aВКЛ" : "§cВЫКЛ"), width/2, height/2, -1);
        }
        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override
        public boolean mouseClicked(double mx, double my, int b) { tbCrits = !tbCrits; saveConfig(); return true; }
        @Override
        public boolean keyPressed(int k, int s, int m) { if(k == GLFW.GLFW_KEY_ESCAPE) client.setScreen(parent); return true; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen parent; public WaypointSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "X: " + (int)wpX + " Y: " + (int)wpY + " Z: " + (int)wpZ, width/2, height/2, -1);
        }
        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
        @Override
        public boolean keyPressed(int k, int s, int m) { if(k == GLFW.GLFW_KEY_ESCAPE) client.setScreen(parent); return true; }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.parent = p; this.id = id; }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) k = GLFW.GLFW_KEY_UNKNOWN;
            if(id==0) keyKA=k; if(id==1) keyTB=k; if(id==2) keyFB=k; if(id==3) keyAT=k; if(id==4) keyWP=k;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) { ctx.fill(0,0,width,height, 0x90000000); ctx.drawCenteredTextWithShadow(textRenderer, "НАЖМИ КЛАВИШУ", width/2, height/2, -1); }
        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":0:0:0:" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + shakeIntensity + ":" + antiVelocity + ":" + tbCrits);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            if (!lines.isEmpty()) {
                String[] p = lines.get(0).split(":");
                if(p.length >= 17) {
                    kaRange=Double.parseDouble(p[0]); kaWallsRange=Double.parseDouble(p[1]);
                    wpX=Double.parseDouble(p[2]); wpY=Double.parseDouble(p[3]); wpZ=Double.parseDouble(p[4]);
                    autoRun=Boolean.parseBoolean(p[8]); keyKA=Integer.parseInt(p[9]); keyTB=Integer.parseInt(p[10]); 
                    keyFB=Integer.parseInt(p[11]); keyAT=Integer.parseInt(p[12]); keyWP=Integer.parseInt(p[13]);
                    shakeIntensity=Float.parseFloat(p[14]); antiVelocity=Boolean.parseBoolean(p[15]); tbCrits=Boolean.parseBoolean(p[16]);
                }
            }
        } catch (Exception ignored) {}
    }
}
