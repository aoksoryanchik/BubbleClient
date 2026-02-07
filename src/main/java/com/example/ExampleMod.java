package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true;

    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float shakeIntensity = 0.5f;
    
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, 
                      keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());
            
            handleKeys(h, client);
            
            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) client.player.setSprinting(true);
            
            // Киллаура теперь работает более стабильно
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });

        HudRenderCallback.EVENT.register(this::renderNavigationArrow);
    }

    private void handleKeys(long h, MinecraftClient client) {
        if (client.currentScreen != null) return;
        if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
        if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
        if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
        if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
        if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
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
            Vec3d pos = target.getPos().add(0, target.getHeight() * 0.5, 0);
            syncRotations(client.player, pos);
            if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void syncRotations(PlayerEntity player, Vec3d target) {
        Vec3d diff = target.subtract(player.getEyePos());
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(tYaw - player.getYaw()));
        player.setPitch(player.getPitch() + MathHelper.wrapDegrees(tPitch - player.getPitch()));
        
        // Срочная отправка пакета перед ударом
        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision));
    }

    private void renderNavigationArrow(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int cx = client.getWindow().getScaledWidth() / 2;
        int cy = client.getWindow().getScaledHeight() / 2;

        Vec3d diff = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
        float targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float yawDiff = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());

        // Рисуем стрелку-указатель
        String arrow = "▲";
        int color = (Math.abs(yawDiff) < 10) ? 0xFF00AAFF : 0xFFFFFFFF; // Синий если смотришь ровно

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, cy - 30, 0); // Позиция чуть выше прицела
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(yawDiff));
        ctx.drawCenteredTextWithShadow(client.textRenderer, arrow, 0, 0, color);
        ms.pop();

        ctx.drawCenteredTextWithShadow(client.textRenderer, String.format("§f[%.0fm]", client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ))), cx, cy - 45, -1);
    }

    // --- ОСТАЛЬНЫЕ МЕХАНИКИ ---

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
        double reach = kaRange;
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).multiply(reach);
        Box box = client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e.isAlive() && e != client.player, reach * reach);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0f : 0.95f)) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void sendNotify(String module, boolean state) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + module + ": " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    private boolean isPressed(long h, int k) {
        if(k==GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    // --- GUI СЕКЦИЯ ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2-90, y = height/2-105;
            ctx.fill(x, y, x+180, y+155, 0xFF050505);
            ctx.drawBorder(x, y, 180, 155, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y+10, -1);
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] k = {keyKA, keyTB, keyFB, keyAT, keyWP};
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                boolean h = mx>=x+10 && mx<=x+170 && my>=iy && my<=iy+18;
                ctx.fill(x+10, iy, x+170, iy+18, h ? 0xFF1A1A1A : 0xFF101010);
                String kN = k[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(k[i], 0).toUpperCase();
                ctx.drawTextWithShadow(textRenderer, n[i] + " §7[" + kN + "]", x+15, iy+5, s[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i==0 || i==1 || i==4) ctx.drawTextWithShadow(textRenderer, "⚙", x+155, iy+5, -1);
            }
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
        private final Screen p; private TextFieldWidget f1, f2, f3;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2 + 25;
            f1 = new TextFieldWidget(textRenderer, x, height/2-45, 45, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2-20, 45, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height/2+5, 45, 16, Text.literal(""));
            updateFields();
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000); 
            int x = width/2, y = height/2;
            ctx.fill(x-115, y-95, x+115, y+90, 0xFF0A0A0A);
            ctx.drawBorder(x-115, y-95, 230, 185, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKILL AURA", x, y-85, -1);
            ctx.drawTextWithShadow(textRenderer, "Дистанция:", x-105, y-41, -1);
            ctx.drawTextWithShadow(textRenderer, "Стены:", x-105, y-16, -1);
            ctx.drawTextWithShadow(textRenderer, "Тряска:", x-105, y+9, -1);
            drawArr(ctx, x, y, mx, my);
            drawChk(ctx, "Авто-Бег", autoRun, y+35, mx, my);
            drawChk(ctx, "Анти-Отдача", antiVelocity, y+50, mx, my);
            
            // СЕКЦИЯ КОНФИГОВ
            int cx = x-240;
            ctx.fill(cx, y-95, cx+120, y+90, 0xFF0A0A0A);
            ctx.drawBorder(cx, y-95, 120, 185, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bКОНФИГИ", cx+60, y-85, -1);
            drawCfgBtn(ctx, "AresMine", cx+10, y-45, mx, my);
            drawCfgBtn(ctx, "MineBlaze", cx+10, y-20, mx, my);
            
            f1.render(ctx, mx, my, d); f2.render(ctx, mx, my, d); f3.render(ctx, mx, my, d);
        }
        private void drawCfgBtn(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x+100 && my >= y && my <= y+18;
            ctx.fill(x, y, x+100, y+18, h ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x+50, y+5, h ? 0xFF00AAFF : -1);
        }
        private void drawArr(DrawContext ctx, int x, int y, int mx, int my) {
            int[] ys = {-45, -20, 5};
            for(int iy : ys) {
                drawB(ctx, x+72, y+iy, ">", mx, my); drawB(ctx, x+10, y+iy, "<", mx, my);
            }
        }
        private void drawB(DrawContext ctx, int x, int y, String t, int mx, int my) {
            boolean h = mx >= x && mx <= x+12 && my >= y && my <= y+16;
            ctx.fill(x, y, x+12, y+16, h ? 0xFF333333 : 0xFF111111);
            ctx.drawTextWithShadow(textRenderer, t, x+3, y+4, h ? 0xFF00AAFF : -1);
        }
        private void drawChk(DrawContext ctx, String n, boolean s, int y, int mx, int my) {
            boolean h = mx>=width/2-60 && mx<=width/2+60 && my>=y && my<=y+12;
            ctx.drawCenteredTextWithShadow(textRenderer, n + ": " + (s?"§aВКЛ":"§cВЫКЛ"), width/2, y, h ? -1 : 0xFFCCCCCC);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if(my>=y-45 && my<=y-29) { if(mx>=x+72 && mx<=x+84) kaRange+=0.1; if(mx>=x+10 && mx<=x+22) kaRange-=0.1; f1.setText(String.format("%.1f", kaRange)); }
            if(my>=y-20 && my<=y-4) { if(mx>=x+72 && mx<=x+84) kaWallsRange+=0.1; if(mx>=x+10 && mx<=x+22) kaWallsRange-=0.1; f2.setText(String.format("%.1f", kaWallsRange)); }
            if(my>=y+5 && my<=y+21) { if(mx>=x+72 && mx<=x+84) shakeIntensity+=0.1; if(mx>=x+10 && mx<=x+22) shakeIntensity-=0.1; f3.setText(String.format("%.1f", shakeIntensity)); }
            if(mx>=x-60 && mx<=x+60) {
                if(my>=y+35 && my<=y+47) autoRun=!autoRun; if(my>=y+50 && my<=y+62) antiVelocity=!antiVelocity;
            }
            int cx = x-240;
            if(mx >= cx+10 && mx <= cx+110) {
                if(my >= y-45 && my <= y-27) { kaRange=3.8; kaWallsRange=3.0; shakeIntensity=0.8f; autoRun=true; antiVelocity=true; updateFields(); }
                if(my >= y-20 && my <= y-2) { kaRange=3.1; kaWallsRange=0.0; shakeIntensity=0.2f; autoRun=true; antiVelocity=false; updateFields(); }
            }
            f1.mouseClicked(mx, my, b); f2.mouseClicked(mx, my, b); f3.mouseClicked(mx, my, b);
            return super.mouseClicked(mx, my, b);
        }
        private void updateFields() {
            f1.setText(String.format("%.1f", kaRange)); f2.setText(String.format("%.1f", kaWallsRange)); f3.setText(String.format("%.1f", shakeIntensity));
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k==GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange=Double.parseDouble(f1.getText().replace(",", ".")); kaWallsRange=Double.parseDouble(f2.getText().replace(",", ".")); shakeIntensity=Float.parseFloat(f3.getText().replace(",", ".")); } catch(Exception ignored){}
                saveConfig(); client.setScreen(p); return true;
            }
            return f1.keyPressed(k, s, m) || f2.keyPressed(k, s, m) || f3.keyPressed(k, s, m);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen p; public TriggerSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x-80, y-40, x+80, y+40, 0xFF0A0A0A);
            ctx.drawBorder(x-80, y-40, 160, 80, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bTRIGGER BOT", x, y-30, -1);
            boolean h = mx >= x-70 && mx <= x+70 && my >= y && my <= y+12;
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), x, y, h ? -1 : 0xFFCCCCCC);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if(mx >= width/2-70 && mx <= width/2+70 && my >= height/2 && my <= height/2+12) { tbCrits = !tbCrits; saveConfig(); return true; }
            return false;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) { if(k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(p); return true; } return false; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2, f3;
        public WaypointSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2 + 20;
            f1 = new TextFieldWidget(textRenderer, x, height/2-45, 50, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2-20, 50, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height/2+5, 50, 16, Text.literal(""));
            f1.setText(String.valueOf((int)wpX)); f2.setText(String.valueOf((int)wpY)); f3.setText(String.valueOf((int)wpZ));
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x-115, y-90, x+115, y+90, 0xFF0A0A0A);
            ctx.drawBorder(x-115, y-90, 230, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bWAYPOINT", x, y-80, -1);
            f1.render(ctx, mx, my, d); f2.render(ctx, mx, my, d); f3.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            f1.mouseClicked(mx, my, b); f2.mouseClicked(mx, my, b); f3.mouseClicked(mx, my, b);
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k==GLFW.GLFW_KEY_ESCAPE) {
                try { wpX=Double.parseDouble(f1.getText()); wpY=Double.parseDouble(f2.getText()); wpZ=Double.parseDouble(f3.getText()); } catch(Exception ignored){}
                saveConfig(); client.setScreen(p); return true;
            }
            return f1.keyPressed(k, s, m) || f2.keyPressed(k, s, m) || f3.keyPressed(k, s, m);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen p; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.p = p; this.id = id; }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) k = GLFW.GLFW_KEY_UNKNOWN;
            if(id==0) keyKA=k; if(id==1) keyTB=k; if(id==2) keyFB=k; if(id==3) keyAT=k; if(id==4) keyWP=k;
            saveConfig(); client.setScreen(p); return true;
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) { ctx.fill(0,0,width,height, 0xEE000000); ctx.drawCenteredTextWithShadow(textRenderer, "PRESS KEY", width/2, height/2, -1); }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange+":"+kaWallsRange+":"+wpX+":"+wpY+":"+wpZ+":0:0:0:"+autoRun+":"+keyKA+":"+keyTB+":"+keyFB+":"+keyAT+":"+keyWP+":"+shakeIntensity+":"+antiVelocity+":"+tbCrits);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if(p.length >= 17) {
                kaRange=Double.parseDouble(p[0]); kaWallsRange=Double.parseDouble(p[1]);
                wpX=Double.parseDouble(p[2]); wpY=Double.parseDouble(p[3]); wpZ=Double.parseDouble(p[4]);
                autoRun=Boolean.parseBoolean(p[8]);
                keyKA=Integer.parseInt(p[9]); keyTB=Integer.parseInt(p[10]); keyFB=Integer.parseInt(p[11]);
                keyAT=Integer.parseInt(p[12]); keyWP=Integer.parseInt(p[13]);
                shakeIntensity=Float.parseFloat(p[14]); antiVelocity=Boolean.parseBoolean(p[15]);
                tbCrits=Boolean.parseBoolean(p[16]);
            }
        } catch (Exception ignored) {}
    }
}

