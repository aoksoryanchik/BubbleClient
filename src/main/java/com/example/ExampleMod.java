package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.screen.slot.SlotActionType;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, kaLegit = false;
    public static double kaRange = 3.8D, kaWallsRange = 3.0D;
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1;
    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();
    
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    public static PlayerEntity auraTarget = null;

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
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (autoRun && killaura && auraTarget != null && auraTarget.isAlive()) client.player.setSprinting(true);
            
            if (killaura) runAura(client); else auraTarget = null;
            if (triggerbot) runTrigger(client);
            
            if (antiVelocity && client.player.hurtTime > 0) {
                Vec3d v = client.player.getVelocity();
                client.player.setVelocity(0, v.y, 0);
            }
        });
    }

    private void checkTotem(MinecraftClient c) {
        if (c.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (c.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, (i < 9) ? (i + 36) : i, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId, (i < 9) ? (i + 36) : i, 0, SlotActionType.PICKUP, c.player);
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
            String name = p.getName().getString().toLowerCase();
            if (friendsList.contains(name)) continue;
            
            double d = c.player.distanceTo(p);
            if (d > kaRange || d >= best) continue;
            if (!c.player.canSee(p) && d > kaWallsRange) continue;
            
            best = d;
            auraTarget = p;
        }

        if (auraTarget != null) {
            rotate(c.player, auraTarget.getPos().add(0, auraTarget.getHeight() * 0.5, 0), kaLegit);
            
            float cooldown = c.player.getAttackCooldownProgress(0.5f);
            double vy = c.player.getVelocity().y;
            // Критуем только если падаем (vy < 0)
            boolean isFalling = vy < -0.05 && !c.player.isOnGround() && !c.player.isClimbing() && !c.player.isTouchingWater();
            
            boolean canStrike;
            if (!c.player.isOnGround()) {
                // В воздухе: ждем падения для крита
                canStrike = isFalling && cooldown >= 0.92F;
            } else {
                // На земле: бьем сразу по готовности КД
                canStrike = cooldown >= 1.0F;
            }

            if (canStrike) {
                c.interactionManager.attackEntity(c.player, auraTarget);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTrigger(MinecraftClient c) {
        HitResult h = c.crosshairTarget;
        if (h != null && h.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) h).getEntity();
            if (e instanceof PlayerEntity && e.isAlive()) {
                String name = e.getName().getString().toLowerCase();
                if (!friendsList.contains(name)) {
                    if (c.player.getAttackCooldownProgress(0) >= 1.0F) {
                        c.interactionManager.attackEntity(c.player, e);
                        c.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    private void rotate(PlayerEntity p, Vec3d t, boolean legit) {
        Vec3d d = t.subtract(p.getEyePos());
        double dist = Math.sqrt(d.x * d.x + d.z * d.z);
        float ty = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
        float tp = (float) -Math.toDegrees(Math.atan2(d.y, dist));
        float speed = legit ? 0.45F : 1.0F;
        p.setYaw(p.getYaw() + MathHelper.wrapDegrees(ty - p.getYaw()) * speed);
        p.setPitch(p.getPitch() + MathHelper.wrapDegrees(tp - p.getPitch()) * speed);
    }

    private void notify(MinecraftClient c, String m, boolean s) {
        c.player.sendMessage(Text.literal("§b[Bubble] §f" + m + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k == -1) return false;
        boolean p = InputUtil.isKeyPressed(h, k);
        if (p && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!p) keyStates[k] = false;
        return false;
    }

    public static void updateFriends(String raw) {
        friendsRaw = raw;
        friendsList.clear();
        if (!raw.isEmpty()) {
            Arrays.stream(raw.split(",")).map(String::trim).map(String::toLowerCase).forEach(friendsList::add);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + antiVelocity + ":" + kaLegit + ":" + friendsRaw);
        } catch (Exception ignored) {}
    }

    public void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String content = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0);
            String[] p = content.split(":", -1);
            if (p.length >= 9) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]); 
                autoRun = Boolean.parseBoolean(p[2]); keyKA = Integer.parseInt(p[3]); 
                keyTB = Integer.parseInt(p[4]); keyFB = Integer.parseInt(p[5]); 
                keyAT = Integer.parseInt(p[6]); antiVelocity = Boolean.parseBoolean(p[7]); 
                kaLegit = Boolean.parseBoolean(p[8]);
                if (p.length > 9) updateFriends(p[9]);
            }
        } catch (Exception ignored) {}
    }

    // --- GUI ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0x80000000); 
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            super.render(ctx, mx, my, d);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 90, cy - 100, cx + 90, cy + 110, -16448251);
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE CLIENT", cx, cy - 90, -1);
            String[] n = { "KillAura", "TriggerBot", "FullBright", "AutoTotem" };
            boolean[] s = { killaura, triggerbot, fullbright, autoTotem };
            for (int i = 0; i < 4; i++) {
                int iy = cy - 60 + i * 25;
                boolean h = (mx >= cx - 80 && mx <= cx + 80 && my >= iy && my <= iy + 20);
                ctx.fill(cx - 80, iy, cx + 80, iy + 20, h ? -15066598 : -15724528);
                ctx.drawText(textRenderer, n[i], cx - 75, iy + 6, s[i] ? 0xFF00FF00 : 0xFFFFFFFF, true);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 4; i++) {
                int iy = cy - 60 + i * 25;
                if (mx >= cx - 80 && mx <= cx + 80 && my >= iy && my <= iy + 20) {
                    if (b == 1) { client.setScreen(new BindScreen(this, i)); return true; }
                    if (b == 0) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        else if (i == 1) triggerbot = !triggerbot;
                        else if (i == 2) fullbright = !fullbright;
                        else if (i == 3) autoTotem = !autoTotem;
                        saveConfig(); return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget rF, wF, fF;
        public KillAuraSettings(Screen p) { super(Text.literal("KA")); this.p = p; }
        @Override protected void init() {
            int x = width/2, y = height/2;
            rF = new TextFieldWidget(textRenderer, x-60, y-70, 45, 14, Text.literal("")); rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, x-60, y-50, 45, 14, Text.literal("")); wF.setText(String.valueOf(kaWallsRange));
            fF = new TextFieldWidget(textRenderer, x + 120, y - 50, 100, 14, Text.literal("")); fF.setText(friendsRaw);
            addDrawableChild(rF); addDrawableChild(wF); addDrawableChild(fF);
        }
        @Override public void renderBackground(DrawContext context, int x, int y, float d) {
            context.fill(0, 0, width, height, 0x80000000);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            super.render(ctx, mx, my, d);
            int x = width/2, y = height/2;
            ctx.fill(x-180, y-95, x+110, y+115, -16448251);
            ctx.drawCenteredTextWithShadow(textRenderer, "KA SETTINGS", x - 35, y-88, -1);
            ctx.drawText(textRenderer, "Range:", x-170, y-67, -1, true);
            ctx.drawText(textRenderer, "Walls:", x-170, y-47, -1, true);
            drawBtn(ctx, x-170, y-25, 200, 14, "AutoRun: ", autoRun, mx, my);
            drawBtn(ctx, x-170, y-5, 200, 14, "AntiVel: ", antiVelocity, mx, my);
        }
        private void drawBtn(DrawContext c, int x, int y, int w, int h, String t, boolean s, int mx, int my) {
            c.fill(x, y, x+w, y+h, (mx>=x && mx<=x+w && my>=y && my<=y+h) ? -14540254 : -15658735);
            c.drawText(textRenderer, t + (s ? "ON" : "OFF"), x+5, y+3, s ? 0xFF00FF00 : 0xFFFF0000, true);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x-170 && mx <= x+30) {
                if (my >= y-25 && my <= y-11) autoRun = !autoRun;
                if (my >= y-5 && my <= y+9) antiVelocity = !antiVelocity;
                saveConfig();
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override public void close() { 
            try { 
                kaRange=Double.parseDouble(rF.getText()); 
                kaWallsRange=Double.parseDouble(wF.getText());
                updateFriends(fF.getText());
            } catch(Exception e){} 
            saveConfig(); client.setScreen(p); 
        }
    }

    public static class BindScreen extends Screen {
        private final Screen p; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("Bind")); this.p = p; this.id = id; }
        @Override public void renderBackground(DrawContext context, int x, int y, float d) {
            context.fill(0, 0, width, height, 0xCC000000);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            super.render(ctx, mx, my, d);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS ANY KEY", width/2, height/2, -1);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if(id==0)keyKA=v; else if(id==1)keyTB=v; else if(id==2)keyFB=v; else if(id==3)keyAT=v;
            saveConfig(); client.setScreen(p); return true;
        }
    }
}

