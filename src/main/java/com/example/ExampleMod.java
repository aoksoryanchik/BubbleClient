package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
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
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true;

    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private static final Random rnd = new Random();
    public static PlayerEntity auraTarget = null;

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) handleAutoTotem(client);
            
            if (autoRun && killaura && auraTarget != null && auraTarget.isAlive()) {
                client.player.setSprinting(true);
            }

            if (killaura) runAura(client); else auraTarget = null;
            if (triggerbot) runTrigger(client);

            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(0, client.player.getVelocity().y, 0);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            float yawToTarget = (float) Math.toDegrees(Math.atan2(wpZ - client.player.getZ(), wpX - client.player.getX())) - 90f;
            float angleDiff = MathHelper.wrapDegrees(yawToTarget - client.player.getYaw());
            String arrow = Math.abs(angleDiff) < 10 ? "§a↑" : (angleDiff > 0 ? "§f→" : "§f←");
            String text = String.format("§f%.0f, %.0f, %.0f  %s  §b%.1fm", wpX, wpY, wpZ, arrow, dist);
            drawContext.drawCenteredTextWithShadow(client.textRenderer, text, drawContext.getScaledWindowWidth() / 2, 10, -1);
        });
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

    private void runAura(MinecraftClient client) {
        auraTarget = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isInvisible() || p.isCreative()) continue;
            double d = client.player.distanceTo(p);
            if (d <= kaRange && d < bestDist) {
                if (!client.player.canSee(p) && d > kaWallsRange) continue;
                bestDist = d; auraTarget = p;
            }
        }
        if (auraTarget != null) {
            // Предикт и наводка
            Vec3d targetVec = auraTarget.getPos().add(
                (auraTarget.getX() - auraTarget.prevX) * 2.0,
                auraTarget.getHeight() * 0.5,
                (auraTarget.getZ() - auraTarget.prevZ) * 2.0
            );
            updateRotations(client.player, targetVec, 180.0f);
            
            if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                // Дополнительная проверка на дистанцию перед ударом (чтобы не миссать)
                if (client.player.distanceTo(auraTarget) <= kaRange) {
                    client.interactionManager.attackEntity(client.player, auraTarget);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        double reach = kaRange;
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f).multiply(reach);
        Box box = client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e.isAlive(), reach * reach);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0f : 0.92f)) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void updateRotations(PlayerEntity player, Vec3d target, float speed) {
        Vec3d diff = target.subtract(player.getEyePos());
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(tYaw - player.getYaw()), -speed, speed));
        player.setPitch(player.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(tPitch - player.getPitch()), -speed, speed));
    }

    private void sendNotify(String m, boolean s) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + m + ": " + (s ? "§aON" : "§cOFF")), true);
        }
    }

    private boolean isPressed(long h, int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false;
        return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":0:0:0:" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + 0.5f + ":" + antiVelocity + ":" + tbCrits);
        } catch (Exception ignored) {}
    }

    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 17) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                wpX = Double.parseDouble(p[2]); wpY = Double.parseDouble(p[3]); wpZ = Double.parseDouble(p[4]);
                autoRun = Boolean.parseBoolean(p[8]);
                keyKA = Integer.parseInt(p[9]); keyTB = Integer.parseInt(p[10]); keyFB = Integer.parseInt(p[11]);
                keyAT = Integer.parseInt(p[12]); keyWP = Integer.parseInt(p[13]);
                antiVelocity = Boolean.parseBoolean(p[15]);
                tbCrits = Boolean.parseBoolean(p[16]);
            }
        } catch (Exception ignored) {}
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2-90, y = height/2-105;
            ctx.fill(x, y, x+180, y+155, 0xFF050505);
            ctx.drawBorder(x, y, 180, 155, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bBUBBLE CLIENT", width/2, y+10, -1);
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] k = {keyKA, keyTB, keyFB, keyAT, keyWP};
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                boolean h = mx>=x+10 && mx<=x+170 && my>=iy && my<=iy+18;
                ctx.fill(x+10, iy, x+170, iy+18, h ? 0xFF1A1A1A : 0xFF101010);
                String kn = k[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(k[i], 0).toUpperCase();
                ctx.drawTextWithShadow(textRenderer, n[i] + " §7[" + kn + "]", x+15, iy+5, s[i] ? 0xFF00FF00 : 0xFFFFFFFF);
                if(i==0 || i==4) ctx.drawTextWithShadow(textRenderer, "⚙", x+155, iy+5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-90, y = height/2-105;
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                if(mx>=x+150 && mx<=x+170 && my>=iy && my<=iy+18) {
                    if(i==0) client.setScreen(new KillAuraSettings(this));
                    if(i==4) client.setScreen(new WaypointSettings(this));
                    return true;
                } else if(mx>=x+10 && mx<=x+150 && my>=iy && my<=iy+18) {
                    if(b==0) {
                        if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot; if(i==2) fullbright=!fullbright;
                        if(i==3) autoTotem=!autoTotem; if(i==4) waypointActive=!waypointActive;
                    } else { client.setScreen(new BindScreen(this, i)); }
                    saveConfig(); return true;
                }
            }
            return false;
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p;
        private TextFieldWidget f1, f2;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2+25;
            f1 = new TextFieldWidget(textRenderer, x, height/2-70, 45, 14, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2-50, 45, 14, Text.literal(""));
            f1.setMaxLength(5); f2.setMaxLength(5);
            f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange));
            addSelectableChild(f1); addSelectableChild(f2);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2;
            ctx.fill(x-110, height/2-95, x+110, height/2+95, 0xFF050505);
            ctx.drawBorder(x-110, height/2-95, 220, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKA SETTINGS", x, height/2-88, -1);
            ctx.drawTextWithShadow(textRenderer, "Range:", x-100, height/2-67, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", x-100, height/2-47, -1);
            f1.render(ctx, mx, my, d); f2.render(ctx, mx, my, d);
            drawBtn(ctx, x-100, height/2-25, 200, 14, "AutoRun: " + (autoRun ? "§aON" : "§cOFF"), mx, my);
            drawBtn(ctx, x-100, height/2-5, 200, 14, "AntiVelocity: " + (antiVelocity ? "§aON" : "§cOFF"), mx, my);
            ctx.drawCenteredTextWithShadow(textRenderer, "§7--- CFG SERVERS ---", x, height/2+20, -1);
            drawBtn(ctx, x-100, height/2+35, 200, 14, "MineBlaze", mx, my);
            drawBtn(ctx, x-100, height/2+55, 200, 14, "AresMine", mx, my);
        }
        private void drawBtn(DrawContext ctx, int x, int y, int w, int h, String t, int mx, int my) {
            boolean hv = mx>=x && mx<=x+w && my>=y && my<=y+h;
            ctx.fill(x, y, x+w, y+h, hv ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, t, x+w/2, y+3, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            f1.setFocused(f1.mouseClicked(mx, my, b));
            f2.setFocused(f2.mouseClicked(mx, my, b));
            int x = width/2;
            if(mx>=x-100 && mx<=x+100) {
                if(my>=height/2-25 && my<=height/2-11) autoRun = !autoRun;
                if(my>=height/2-5 && my<=height/2+9) antiVelocity = !antiVelocity;
                if(my>=height/2+35 && my<=height/2+49) { kaRange=3.1; kaWallsRange=0.0; autoRun=true; antiVelocity=false; f1.setText("3.1"); f2.setText("0.0"); }
                if(my>=height/2+55 && my<=height/2+69) { kaRange=3.8; kaWallsRange=3.0; autoRun=true; antiVelocity=true; f1.setText("3.8"); f2.setText("3.0"); }
                saveConfig(); return true;
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean charTyped(char chr, int m) {
            f1.charTyped(chr, m); f2.charTyped(chr, m);
            return true;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange=Double.parseDouble(f1.getText()); kaWallsRange=Double.parseDouble(f2.getText()); } catch(Exception ignored){}
                saveConfig(); client.setScreen(p); return true;
            }
            f1.keyPressed(k, s, m); f2.keyPressed(k, s, m);
            return super.keyPressed(k, s, m);
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p;
        private TextFieldWidget f1, f2, f3;
        public WaypointSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2+20;
            f1 = new TextFieldWidget(textRenderer, x, height/2-45, 50, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2-20, 50, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height/2+5, 50, 16, Text.literal(""));
            f1.setText(String.valueOf((int)wpX)); f2.setText(String.valueOf((int)wpY)); f3.setText(String.valueOf((int)wpZ));
            addSelectableChild(f1); addSelectableChild(f2); addSelectableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2;
            ctx.fill(x-115, height/2-90, x+115, height/2+90, 0xFF050505);
            ctx.drawBorder(x-115, height/2-90, 230, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bWAYPOINT", x, height/2-80, -1);
            ctx.drawTextWithShadow(textRenderer, "X:", x-100, height/2-41, -1);
            ctx.drawTextWithShadow(textRenderer, "Y:", x-100, height/2-16, -1);
            ctx.drawTextWithShadow(textRenderer, "Z:", x-100, height/2+9, -1);
            f1.render(ctx, mx, my, d); f2.render(ctx, mx, my, d); f3.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            f1.setFocused(f1.mouseClicked(mx, my, b));
            f2.setFocused(f2.mouseClicked(mx, my, b));
            f3.setFocused(f3.mouseClicked(mx, my, b));
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean charTyped(char chr, int m) {
            f1.charTyped(chr, m); f2.charTyped(chr, m); f3.charTyped(chr, m);
            return true;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX=Double.parseDouble(f1.getText()); wpY=Double.parseDouble(f2.getText()); wpZ=Double.parseDouble(f3.getText()); } catch(Exception ignored){}
                saveConfig(); client.setScreen(p); return true;
            }
            f1.keyPressed(k, s, m); f2.keyPressed(k, s, m); f3.keyPressed(k, s, m);
            return super.keyPressed(k, s, m);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen p; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.p = p; this.id = id; }
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS KEY", width/2, height/2, -1);
        }
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) k = GLFW.GLFW_KEY_UNKNOWN;
            if(id==0) keyKA=k; if(id==1) keyTB=k; if(id==2) keyFB=k; if(id==3) keyAT=k; if(id==4) keyWP=k;
            saveConfig(); client.setScreen(p); return true;
        }
    }
}

