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
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, hvhMode = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true;
    public static double kaRange = 3.8D, kaWallsRange = 3.0D;
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1;
    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();
    
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private static long lastAttackTime = 0;
    private final Random random = new Random();

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

            // ИСПРАВЛЕННЫЙ HVH MOVE (Без лагов)
            if (hvhMode && !client.player.isOnGround()) {
                if (client.options.jumpKey.isPressed()) {
                    client.player.setVelocity(client.player.getVelocity().x, 0.05, client.player.getVelocity().z);
                    client.player.jump();
                }
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
            
            // Стабильный AntiVelocity
            if (antiVelocity && client.player.hurtTime > 0) {
                double factor = hvhMode ? 0.0D : 0.45D;
                client.player.setVelocity(client.player.getVelocity().x * factor, client.player.getVelocity().y, client.player.getVelocity().z * factor);
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
        PlayerEntity target = null;
        double bestDist = Double.MAX_VALUE;

        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || p.isSpectator()) continue;
            if (friendsList.contains(p.getName().getString().toLowerCase())) continue;
            
            double d = c.player.distanceTo(p);
            double currentRange = hvhMode ? 5.5D : kaRange;
            if (d <= currentRange && d < bestDist) {
                bestDist = d;
                target = p;
            }
        }

        if (target != null) {
            if (autoRun) c.player.setSprinting(true);

            // МГНОВЕННЫЕ РОТАЦИИ ДЛЯ HVH
            Vec3d diff = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(c.player.getEyePos());
            float targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));

            if (hvhMode) {
                c.player.setYaw(targetYaw);
                c.player.setPitch(targetPitch);
            } else {
                c.player.setYaw(lerpAngle(c.player.getYaw(), targetYaw, 0.45f));
                c.player.setPitch(lerpAngle(c.player.getPitch(), targetPitch, 0.45f));
            }

            float cooldown = c.player.getAttackCooldownProgress(0.5f);
            if (cooldown >= (hvhMode ? 0.90F : 0.93F)) {
                c.interactionManager.attackEntity(c.player, target);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private float lerpAngle(float start, float end, float speed) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + diff * speed;
    }

    private void runTrigger(MinecraftClient c) {
        HitResult h = c.crosshairTarget;
        if (h != null && h.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) h).getEntity();
            if (e instanceof PlayerEntity && e.isAlive() && !friendsList.contains(e.getName().getString().toLowerCase())) {
                if (c.player.getAttackCooldownProgress(0) >= 1.0F) {
                    c.interactionManager.attackEntity(c.player, e);
                    c.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private void notify(MinecraftClient c, String m, boolean s) {
        c.player.sendMessage(Text.literal("§b[Bubble] §f" + m + ": " + (s ? "§aВКЛ" : "§cВЫКЛ")), true);
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
        if (!raw.isEmpty() && !raw.equals("Friends:")) {
            Arrays.stream(raw.split(",")).map(String::trim).map(String::toLowerCase).forEach(friendsList::add);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + hvhMode + ":" + friendsRaw);
        } catch (Exception ignored) {}
    }

    public void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":", -1);
            if (p.length >= 9) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]); 
                autoRun = Boolean.parseBoolean(p[2]); keyKA = Integer.parseInt(p[3]); 
                keyTB = Integer.parseInt(p[4]); keyFB = Integer.parseInt(p[5]); 
                keyAT = Integer.parseInt(p[6]); hvhMode = Boolean.parseBoolean(p[7]); 
                updateFriends(p[8]);
            }
        } catch (Exception ignored) {}
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 90, cy - 80, cx + 90, cy + 90, 0xFF1A1A1B);
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE CLIENT", cx, cy - 70, -1);
            String[] n = { "KillAura", "TriggerBot", "FullBright", "AutoTotem" };
            boolean[] s = { killaura, triggerbot, fullbright, autoTotem };
            for (int i = 0; i < 4; i++) {
                int iy = cy - 40 + i * 25;
                ctx.fill(cx - 80, iy, cx + 80, iy + 20, (mx >= cx - 80 && mx <= cx + 80 && my >= iy && my <= iy + 20) ? 0xFF2D2D2E : 0xFF232324);
                ctx.drawText(textRenderer, n[i], cx - 75, iy + 6, s[i] ? 0xFF00FF00 : 0xFFFFFFFF, true);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 4; i++) {
                int iy = cy - 40 + i * 25;
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
        private final Screen parent;
        private TextFieldWidget rangeField, wallsField, friendsField;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA")); this.parent = parent; }
        @Override protected void init() {
            int x = width/2, y = height/2;
            rangeField = new TextFieldWidget(textRenderer, x - 100, y - 75, 80, 14, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            wallsField = new TextFieldWidget(textRenderer, x - 100, y - 55, 80, 14, Text.literal(""));
            wallsField.setText(String.valueOf(kaWallsRange));
            friendsField = new TextFieldWidget(textRenderer, x + 15, y - 75, 100, 14, Text.literal("Friends:"));
            friendsField.setText(friendsRaw.isEmpty() ? "Friends:" : friendsRaw);
            addDrawableChild(rangeField); addDrawableChild(wallsField); addDrawableChild(friendsField);
        }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int x = width/2, y = height/2;
            ctx.fill(x - 180, y - 100, x - 5, y + 110, 0xFF1A1A1B);
            ctx.drawText(textRenderer, "Range:", x - 170, y - 72, -1, true);
            ctx.drawText(textRenderer, "Walls:", x - 170, y - 52, -1, true);
            drawBtn(ctx, x - 170, y - 30, 155, 14, "AutoRun: ", autoRun, mx, my);
            drawBtn(ctx, x - 170, y - 10, 155, 14, "AntiVelocity: ", antiVelocity, mx, my);
            drawBtn(ctx, x - 170, y + 10, 155, 14, "HVH MODE: ", hvhMode, mx, my);
            btn(ctx, x - 170, y + 50, 155, 14, "MineBlaze", mx, my);
            btn(ctx, x - 170, y + 70, 155, 14, "AresMine", mx, my);
        }
        private void drawBtn(DrawContext c, int x, int y, int w, int h, String t, boolean s, int mx, int my) {
            c.fill(x, y, x+w, y+h, (mx>=x && mx<=x+w && my>=y && my<=y+h) ? 0xFF2D2D2E : 0xFF232324);
            c.drawText(textRenderer, t + (s ? "§aON" : "§cOFF"), x+5, y+3, -1, true);
        }
        private void btn(DrawContext c, int x, int y, int w, int h, String t, int mx, int my) {
            c.fill(x, y, x+w, y+h, (mx>=x && mx<=x+w && my>=y && my<=y+h) ? 0xFF2D2D2E : 0xFF232324);
            c.drawCenteredTextWithShadow(textRenderer, t, x+w/2, y+3, -1);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 170 && mx <= x - 15) {
                if (my >= y - 30 && my <= y - 16) autoRun = !autoRun;
                if (my >= y - 10 && my <= y + 4) antiVelocity = !antiVelocity;
                if (my >= y + 10 && my <= y + 24) hvhMode = !hvhMode;
                if (my >= y + 50 && my <= y + 64) { kaRange = 3.1; kaWallsRange = 0.0; rangeField.setText("3.1"); wallsField.setText("0.0"); }
                if (my >= y + 70 && my <= y + 84) { kaRange = 3.6; kaWallsRange = 3.6; rangeField.setText("3.6"); wallsField.setText("3.6"); }
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override public void close() { 
            try { kaRange = Double.parseDouble(rangeField.getText()); kaWallsRange = Double.parseDouble(wallsField.getText()); } catch (Exception ignored) {}
            updateFriends(friendsField.getText()); saveConfig(); client.setScreen(parent); 
        }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen parent, int id) { super(Text.literal("Bind")); this.parent = parent; this.id = id; }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) { super.render(ctx, mx, my, delta); ctx.drawCenteredTextWithShadow(textRenderer, "PRESS ANY KEY", width/2, height/2, -1); }
        @Override public boolean keyPressed(int k, int s, int m) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if(id==0)keyKA=v; else if(id==1)keyTB=v; else if(id==2)keyFB=v; else if(id==3)keyAT=v;
            saveConfig(); client.setScreen(parent); return true;
        }
    }
}
