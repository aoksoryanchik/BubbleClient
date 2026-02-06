package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, noFire = true, viewModelActive = true, autoRun = false;
    public static boolean antiVelocity = true, screenShake = true;

    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float handX = 0.0f, handY = 0.0f, handZ = 0.0f;
    
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyAT = GLFW.GLFW_KEY_UNKNOWN, keyNF = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN, keyVM = GLFW.GLFW_KEY_UNKNOWN;

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
            if (isPressed(h, keyKA)) killaura = !killaura;
            if (isPressed(h, keyTB)) triggerbot = !triggerbot;
            if (isPressed(h, keyFB)) fullbright = !fullbright;

            if (noFire) {
                client.player.setFireTicks(0);
                if (client.player.isOnFire()) client.player.extinguish();
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));

            if (autoTotem && client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                for (int i = 0; i < 45; i++) {
                    if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, client.player);
                        break;
                    }
                }
            }

            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(client.player.getVelocity().multiply(0.6, 1.0, 0.6));
            }

            if (killaura) {
                runKillaura(client);
                if (autoRun) client.options.sprintKey.setPressed(true);
            }
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        WorldRenderEvents.LAST.register(this::renderWaypoint);

        HudRenderCallback.EVENT.register((ctx, t) -> {
            if (waypointActive) ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, String.format("§bTarget: §f%.0f %.0f %.0f", wpX, wpY, wpZ), ctx.getScaledWindowWidth()/2, 10, -1);
        });
    }

    private void runKillaura(MinecraftClient client) {
        for (PlayerEntity target : client.world.getPlayers()) {
            if (target == client.player || !target.isAlive() || target.isInvisible()) continue;
            double d = client.player.distanceTo(target);
            if (d <= (client.player.canSee(target) ? kaRange : kaWallsRange)) {
                float rndCD = 0.95f + (random.nextFloat() * 0.1f);
                if (client.player.getAttackCooldownProgress(0.5f) >= rndCD) {
                    if (screenShake) {
                        client.player.setYaw(client.player.getYaw() + (random.nextFloat() - 0.5f) * 0.35f);
                        client.player.setPitch(client.player.getPitch() + (random.nextFloat() - 0.5f) * 0.35f);
                    }
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult res && res.getEntity() instanceof PlayerEntity target) {
            if (target.isAlive() && client.player.getAttackCooldownProgress(0) >= 0.98f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void renderWaypoint(WorldRenderContext context) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        double d = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
        MatrixStack ms = context.matrixStack();
        ms.push();
        ms.translate(wpX - context.camera().getPos().x, (wpY - context.camera().getPos().y) + 1.5, wpZ - context.camera().getPos().z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
        float s = (float) Math.max(0.02, d * 0.012);
        ms.scale(-s, -s, s);
        VertexConsumerProvider vcp = context.consumers();
        if (vcp != null) client.textRenderer.draw("§b[!] TARGET", -client.textRenderer.getWidth("[!] TARGET")/2f, 0, -1, false, ms.peek().getPositionMatrix(), vcp, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
        ms.pop();
    }

    // --- GUI & CONFIG (СОХРАНЕНО БЕЗ ИЗМЕНЕНИЙ) ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            int x = width/2-90, y = height/2-105;
            ctx.fill(x, y, x+180, y+210, 0xFF0A0A0A);
            ctx.drawBorder(x, y, 180, 210, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y+10, -1);
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "NoFire", "Waypoint", "Hands Mod"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, noFire, waypointActive, viewModelActive};
            int[] k = {keyKA, keyTB, keyFB, keyAT, keyNF, keyWP, keyVM};
            for(int i=0; i<7; i++) {
                int iy = y+35+i*22;
                boolean h = mx>=x+10 && mx<=x+170 && my>=iy && my<=iy+18;
                ctx.fill(x+10, iy, x+170, iy+18, h ? 0xFF1A1A1A : 0xFF121212);
                String kN = k[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(k[i], 0);
                ctx.drawTextWithShadow(textRenderer, n[i] + " §7[" + (kN==null?"?":kN.toUpperCase()) + "]", x+15, iy+5, s[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i==0 || i==5 || i==6) ctx.drawTextWithShadow(textRenderer, "⚙", x+155, iy+5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-90, y = height/2-105;
            for(int i=0; i<7; i++) {
                int iy = y+35+i*22;
                if(mx>=x+150 && mx<=x+170 && my>=iy && my<=iy+18) {
                    if(i==0) client.setScreen(new ConfigScreen(this, "KA"));
                    if(i==5) client.setScreen(new ConfigScreen(this, "WP"));
                    if(i==6) client.setScreen(new ConfigScreen(this, "VM"));
                    return true;
                } else if(mx>=x+10 && mx<=x+150 && my>=iy && my<=iy+18) {
                    if(b == 0) {
                        if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot; if(i==2) fullbright=!fullbright;
                        if(i==3) autoTotem=!autoTotem; if(i==4) noFire=!noFire; if(i==5) waypointActive=!waypointActive;
                        if(i==6) viewModelActive=!viewModelActive;
                    } else client.setScreen(new BindScreen(this, i));
                    saveConfig(); return true;
                }
            }
            return false;
        }
    }

    public static class BindScreen extends Screen {
        private final Screen p; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.p = p; this.id = id; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0,0,width,height, 0xDD000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS KEY TO BIND", width/2, height/2, -1);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) k = GLFW.GLFW_KEY_UNKNOWN;
            if(id==0) keyKA=k; if(id==1) keyTB=k; if(id==2) keyFB=k; if(id==3) keyAT=k; if(id==4) keyNF=k; if(id==5) keyWP=k; if(id==6) keyVM=k;
            saveConfig(); client.setScreen(p); return true;
        }
    }

    public static class ConfigScreen extends Screen {
        private final Screen p; private final String t; private TextFieldWidget f1, f2, f3;
        public ConfigScreen(Screen p, String t) { super(Text.literal("")); this.p = p; this.t = t; }
        @Override
        protected void init() {
            f1 = new TextFieldWidget(textRenderer, width/2-50, height/2-45, 100, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, width/2-50, height/2-20, 100, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, width/2-50, height/2+5, 100, 16, Text.literal(""));
            if(t.equals("KA")){ f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange)); f3.setVisible(false); }
            if(t.equals("WP")){ f1.setText(String.valueOf(wpX)); f2.setText(String.valueOf(wpY)); f3.setText(String.valueOf(wpZ)); }
            if(t.equals("VM")){ f1.setText(String.valueOf(handX)); f2.setText(String.valueOf(handY)); f3.setText(String.valueOf(handZ)); }
            addSelectableChild(f1); addSelectableChild(f2); addSelectableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xF0000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "SETTINGS: " + t, width/2, height/2-75, 0xFF00AAFF);
            f1.render(ctx, mx, my, d); f2.render(ctx, mx, my, d); if(f3.isVisible()) f3.render(ctx, mx, my, d);
            if(t.equals("KA")) {
                renderCheck(ctx, "AutoRun", autoRun, height/2+30, mx, my);
                renderCheck(ctx, "AntiVelocity", antiVelocity, height/2+50, mx, my);
                renderCheck(ctx, "ScreenShake", screenShake, height/2+70, mx, my);
            }
        }
        private void renderCheck(DrawContext ctx, String n, boolean s, int y, int mx, int my) {
            boolean h = mx>=width/2-60 && mx<=width/2+60 && my>=y && my<=y+14;
            ctx.drawTextWithShadow(textRenderer, n + ": " + (s?"§aON":"§cOFF"), width/2-55, y, h ? -1 : 0xFFCCCCCC);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if(t.equals("KA") && mx>=width/2-60 && mx<=width/2+60) {
                if(my>=height/2+30 && my<=height/2+44) autoRun=!autoRun;
                if(my>=height/2+50 && my<=height/2+64) antiVelocity=!antiVelocity;
                if(my>=height/2+70 && my<=height/2+84) screenShake=!screenShake;
                return true;
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k==GLFW.GLFW_KEY_ESCAPE) {
                try {
                    if(t.equals("KA")){ kaRange=Double.parseDouble(f1.getText()); kaWallsRange=Double.parseDouble(f2.getText()); }
                    if(t.equals("WP")){ wpX=Double.parseDouble(f1.getText()); wpY=Double.parseDouble(f2.getText()); wpZ=Double.parseDouble(f3.getText()); }
                    if(t.equals("VM")){ handX=Float.parseFloat(f1.getText()); handY=Float.parseFloat(f2.getText()); handZ=Float.parseFloat(f3.getText()); }
                } catch(Exception ignored){}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    private boolean isPressed(long h, int k) {
        if(k==GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange+":"+kaWallsRange+":"+wpX+":"+wpY+":"+wpZ+":"+handX+":"+handY+":"+handZ+":"+autoRun+":"+keyKA+":"+keyTB+":"+keyFB+":"+keyAT+":"+keyNF+":"+keyWP+":"+keyVM+":"+antiVelocity+":"+screenShake);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if(p.length >= 18) {
                kaRange=Double.parseDouble(p[0]); kaWallsRange=Double.parseDouble(p[1]);
                wpX=Double.parseDouble(p[2]); wpY=Double.parseDouble(p[3]); wpZ=Double.parseDouble(p[4]);
                handX=Float.parseFloat(p[5]); handY=Float.parseFloat(p[6]); handZ=Float.parseFloat(p[7]);
                autoRun=Boolean.parseBoolean(p[8]);
                keyKA=Integer.parseInt(p[9]); keyTB=Integer.parseInt(p[10]); keyFB=Integer.parseInt(p[11]);
                keyAT=Integer.parseInt(p[12]); keyNF=Integer.parseInt(p[13]); keyWP=Integer.parseInt(p[14]); keyVM=Integer.parseInt(p[15]);
                antiVelocity=Boolean.parseBoolean(p[16]); screenShake=Boolean.parseBoolean(p[17]);
            }
        } catch (Exception ignored) {}
    }
}
