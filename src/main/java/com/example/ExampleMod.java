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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true, kaLegit = false;
    public static double kaRange = 3.8D, kaWallsRange = 3.0D, wpX = 0.0D, wpY = 64.0D, wpZ = 0.0D;
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyWP = -1;
    
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private static final Random rnd = new Random();
    public static PlayerEntity auraTarget = null;
    private static long lastAttackTime = 0;

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long win = client.getWindow().getHandle();

            if (isPressed(win, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (isPressed(win, keyKA)) { killaura = !killaura; notify(client, "KillAura", killaura); }
                if (isPressed(win, keyTB)) { triggerbot = !triggerbot; notify(client, "TriggerBot", triggerbot); }
                if (isPressed(win, keyFB)) { fullbright = !fullbright; notify(client, "FullBright", fullbright); }
                if (isPressed(win, keyAT)) { autoTotem = !autoTotem; notify(client, "AutoTotem", autoTotem); }
                if (isPressed(win, keyWP)) { waypointActive = !waypointActive; notify(client, "Waypoint", waypointActive); }
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (autoRun && killaura && auraTarget != null && auraTarget.isAlive()) client.player.setSprinting(true);
            
            if (killaura) runAura(client); else auraTarget = null;
            if (triggerbot) runTrigger(client);
            if (antiVelocity && client.player.hurtTime > 0) client.player.setVelocity(0, client.player.getVelocity().y, 0);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            if (!waypointActive) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            double d = mc.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            float yaw = (float) Math.toDegrees(Math.atan2(wpZ - mc.player.getZ(), wpX - mc.player.getX())) - 90.0F;
            float diff = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
            String arr = (Math.abs(diff) < 10) ? "↑" : ((diff > 0) ? "→" : "←");
            ctx.drawText(mc.textRenderer, String.format("WP: %.0f %.0f %.0f | %s | %.1f", wpX, wpY, wpZ, arr, d), ctx.getScaledWindowWidth()/2, 10, -1, true);
        });
    }

    private void checkTotem(MinecraftClient c) {
        if (c.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (c.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, (i < 9) ? (i + 36) : i, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, 45, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, c.player);
                    break;
                }
            }
        }
    }

    private void runAura(MinecraftClient c) {
        auraTarget = null;
        double best = Double.MAX_VALUE;
        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || p.isSpectator()) continue;
            double d = c.player.distanceTo(p);
            if (d > kaRange || d >= best) continue;
            if (!c.player.canSee(p) && d > kaWallsRange) continue;
            
            // Legit Check: если включен легит, не бить тех, кто за спиной (FOV 120)
            if (kaLegit) {
                float yaw = (float) Math.toDegrees(Math.atan2(p.getZ() - c.player.getZ(), p.getX() - c.player.getX())) - 90.0F;
                if (Math.abs(MathHelper.wrapDegrees(yaw - c.player.getYaw())) > 60) continue;
            }

            best = d;
            auraTarget = p;
        }

        if (auraTarget != null) {
            double jitter = kaLegit ? 0.02 : 0.1;
            double o = (rnd.nextDouble() - 0.5) * jitter;
            lookAt(c.player, auraTarget.getPos().add(o, auraTarget.getHeight() * (0.4 + rnd.nextDouble() * 0.3), o), kaLegit);
            
            // Атака
            long now = System.currentTimeMillis();
            float cooldown = c.player.getAttackCooldownProgress(0);
            
            boolean canAttack = kaLegit ? (cooldown >= 1.0F && now - lastAttackTime > 150 + rnd.nextInt(100)) : (cooldown >= 1.0F);
            
            if (canAttack) {
                c.interactionManager.attackEntity(c.player, auraTarget);
                c.player.swingHand(Hand.MAIN_HAND);
                lastAttackTime = now;
            }
        }
    }

    private void runTrigger(MinecraftClient c) {
        HitResult h = c.crosshairTarget;
        if (h != null && h.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) h).getEntity();
            if (e instanceof PlayerEntity && e.isAlive() && c.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0F : 0.92F)) {
                c.interactionManager.attackEntity(c.player, e);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void lookAt(PlayerEntity p, Vec3d t, boolean legit) {
        Vec3d d = t.subtract(p.getEyePos());
        double dist = Math.sqrt(d.x * d.x + d.z * d.z);
        float ty = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
        float tp = (float) -Math.toDegrees(Math.atan2(d.y, dist));
        
        float speed = legit ? 0.25F : 1.0F; // Легитная наводка медленнее
        p.setYaw(p.getYaw() + MathHelper.wrapDegrees(ty - p.getYaw()) * speed);
        p.setPitch(p.getPitch() + MathHelper.wrapDegrees(tp - p.getPitch()) * speed);
    }

    private void notify(MinecraftClient c, String m, boolean s) {
        c.player.sendMessage(Text.of("§b[Bubble] §f" + m + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k == -1) return false;
        boolean p = InputUtil.isKeyPressed(h, k);
        if (p && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!p) keyStates[k] = false;
        return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + antiVelocity + ":" + tbCrits + ":" + kaLegit);
        } catch (Exception ignored) {}
    }

    public void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 14) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]); 
                wpX = Double.parseDouble(p[2]); wpY = Double.parseDouble(p[3]); wpZ = Double.parseDouble(p[4]);
                autoRun = Boolean.parseBoolean(p[5]); keyKA = Integer.parseInt(p[6]); 
                keyTB = Integer.parseInt(p[7]); keyFB = Integer.parseInt(p[8]); 
                keyAT = Integer.parseInt(p[9]); keyWP = Integer.parseInt(p[10]); 
                antiVelocity = Boolean.parseBoolean(p[11]); tbCrits = Boolean.parseBoolean(p[12]);
                kaLegit = Boolean.parseBoolean(p[13]);
            }
        } catch (Exception ignored) {}
    }

    // --- GUI ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.of("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            if (client.gameRenderer.getShader() != null) client.gameRenderer.setBlockEntityBatchId(null); // Попытка убить блюр
            ctx.fill(0, 0, width, height, 0x70000000); 
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 90, cy - 105, cx + 90, cy + 155, -16448251);
            ctx.drawBorder(cx - 90, cy - 105, 180, 260, -16733441);
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE CLIENT", cx, cy - 95, -1);
            String[] n = { "KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint" };
            boolean[] s = { killaura, triggerbot, fullbright, autoTotem, waypointActive };
            for (int i = 0; i < 5; i++) {
                int iy = cy - 70 + i * 25;
                boolean h = (mx >= cx - 80 && mx <= cx + 80 && my >= iy && my <= iy + 20);
                ctx.fill(cx - 80, iy, cx + 80, iy + 20, h ? -15066598 : -15724528);
                ctx.drawText(textRenderer, n[i], cx - 75, iy + 6, s[i] ? 0xFF00FF00 : 0xFFFFFFFF, true);
                ctx.drawText(textRenderer, ">>", cx + 65, iy + 6, -1, true);
            }
            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 5; i++) {
                int iy = cy - 70 + i * 25;
                if (mx >= cx - 80 && mx <= cx + 80 && my >= iy && my <= iy + 20) {
                    if (b == 1 || mx >= cx + 60) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        else if (i == 4) client.setScreen(new WaypointSettings(this));
                        return true;
                    }
                    if (b == 0) {
                        if (i == 0) killaura = !killaura;
                        else if (i == 4) waypointActive = !waypointActive;
                        else client.setScreen(new BindScreen(this, i));
                        saveConfig(); return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget rF, wF;
        public KillAuraSettings(Screen p) { super(Text.of("KA")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2, y = height/2;
            rF = new TextFieldWidget(textRenderer, x+25, y-70, 45, 14, Text.of("")); rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, x+25, y-50, 45, 14, Text.of("")); wF.setText(String.valueOf(kaWallsRange));
            addDrawableChild(rF); addDrawableChild(wF);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x70000000);
            int x = width/2, y = height/2;
            ctx.fill(x-180, y-95, x+110, y+110, -16448251); ctx.drawBorder(x-180, y-95, 290, 205, -16733441);
            ctx.drawCenteredTextWithShadow(textRenderer, "KA SETTINGS", x - 35, y-88, -1);
            ctx.drawText(textRenderer, "Range:", x-170, y-67, -1, true); ctx.drawText(textRenderer, "Walls:", x-170, y-47, -1, true);
            
            // Основные кнопки
            btn(ctx, x-170, y-25, 200, 14, "AutoRun: " + autoRun, mx, my);
            btn(ctx, x-170, y-5, 200, 14, "AntiVelocity: " + antiVelocity, mx, my);
            btn(ctx, x-170, y+15, 200, 14, "BIND KEY", mx, my);
            
            // Правое меню (Легит)
            ctx.fill(x + 40, y - 25, x + 100, y + 15, kaLegit ? 0xAA00FF00 : 0xAAFF0000);
            ctx.drawCenteredTextWithShadow(textRenderer, "LEGIT", x + 70, y - 10, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, kaLegit ? "ON" : "OFF", x + 70, y, -1);

            ctx.drawCenteredTextWithShadow(textRenderer, "PRESETS", x - 35, y+45, -1);
            btn(ctx, x-170, y+55, 270, 14, "MineBlaze (Legit Recommend)", mx, my);
            btn(ctx, x-170, y+75, 270, 14, "AresMine (Blatant)", mx, my);
            super.render(ctx, mx, my, d);
        }
        private void btn(DrawContext c, int x, int y, int w, int h, String t, int mx, int my) {
            c.fill(x, y, x+w, y+h, (mx>=x && mx<=x+w && my>=y && my<=y+h) ? -14540254 : -15658735);
            c.drawCenteredTextWithShadow(textRenderer, t, x+w/2, y+3, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x + 40 && mx <= x + 100 && my >= y - 25 && my <= y + 15) { kaLegit = !kaLegit; saveConfig(); return true; }
            if (mx >= x-170 && mx <= x+100) {
                if (my >= y-25 && my <= y-11) autoRun = !autoRun;
                if (my >= y-5 && my <= y+9) antiVelocity = !antiVelocity;
                if (my >= y+15 && my <= y+29) client.setScreen(new BindScreen(this, 0));
                if (my >= y+55 && my <= y+69) { kaRange=3.1; kaWallsRange=0.0; kaLegit=true; rF.setText("3.1"); wF.setText("0.0"); }
                if (my >= y+75 && my <= y+89) { kaRange=3.8; kaWallsRange=3.0; kaLegit=false; rF.setText("3.8"); wF.setText("3.0"); }
                saveConfig();
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public void close() {
            try { kaRange = Double.parseDouble(rF.getText()); kaWallsRange = Double.parseDouble(wF.getText()); } catch(Exception e){}
            saveConfig(); client.setScreen(p);
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget fX, fY, fZ;
        public WaypointSettings(Screen p) { super(Text.of("WP")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2+20, y = height/2;
            fX = new TextFieldWidget(textRenderer, x, y-45, 50, 16, Text.of("")); fX.setText(String.valueOf((int)wpX));
            fY = new TextFieldWidget(textRenderer, x, y-20, 50, 16, Text.of("")); fY.setText(String.valueOf((int)wpY));
            fZ = new TextFieldWidget(textRenderer, x, y+5, 50, 16, Text.of("")); fZ.setText(String.valueOf((int)wpZ));
            addDrawableChild(fX); addDrawableChild(fY); addDrawableChild(fZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x70000000);
            int x = width/2, y = height/2;
            ctx.fill(x-115, y-90, x+115, y+90, -16448251); ctx.drawBorder(x-115, y-90, 230, 180, -16733441);
            ctx.drawCenteredTextWithShadow(textRenderer, "WAYPOINT", x, y-80, -1);
            btn(ctx, x-100, y+40, 200, 15, "BIND KEY", mx, my);
            super.render(ctx, mx, my, d);
        }
        private void btn(DrawContext c, int x, int y, int w, int h, String t, int mx, int my) {
            c.fill(x, y, x+w, y+h, (mx>=x && mx<=x+w && my>=y && my<=y+h) ? -14540254 : -15658735);
            c.drawCenteredTextWithShadow(textRenderer, t, x+w/2, y+3, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x-100 && mx <= x+100 && my >= y+40 && my <= y+55) { client.setScreen(new BindScreen(this, 4)); return true; }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public void close() {
            try { wpX = Double.parseDouble(fX.getText()); wpY = Double.parseDouble(fY.getText()); wpZ = Double.parseDouble(fZ.getText()); } catch(Exception e){}
            saveConfig(); client.setScreen(p);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen p; private final int id;
        public BindScreen(Screen p, int id) { super(Text.of("Bind")); this.p = p; this.id = id; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xCC000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS ANY KEY", width/2, height/2, -1);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if(id==0)keyKA=v; else if(id==1)keyTB=v; else if(id==2)keyFB=v; else if(id==3)keyAT=v; else if(id==4)keyWP=v;
            saveConfig(); client.setScreen(p); return true;
        }
    }
}

