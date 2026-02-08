package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, screenShake = true, tbCrits = true;
    public static boolean esp = true; // ESP по умолчанию включен
    public static boolean isDestructed = false;

    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float shakeIntensity = 0.5f;
    
    // Переменные для HandView (ViewModel) - ПОЛНЫЙ НАБОР ДЛЯ MIXIN
    public static float vmX = 0, vmY = 0, vmZ = 0;
    public static float vmLX = 0, vmLY = 0, vmLZ = 0;
    public static boolean viewModel = true;

    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || isDestructed) return;
            
            long h = client.getWindow().getHandle();
            if (isPressed(h, GLFW.GLFW_KEY_RIGHT_SHIFT) && isPressed(h, GLFW.GLFW_KEY_DELETE)) {
                selfDestruct();
                return;
            }

            if (isPressed(h, GLFW.GLFW_KEY_O) && client.currentScreen == null) client.setScreen(new BubbleMenu());

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

        WorldRenderEvents.LAST.register(context -> {
            if (isDestructed) return;
            if (waypointActive) renderWaypoint(context);
            if (esp) renderESP(context);
        });
    }

    private void selfDestruct() {
        isDestructed = true;
        killaura = false; triggerbot = false; fullbright = false; waypointActive = false; esp = false;
        try { Files.deleteIfExists(Paths.get(CONFIG_FILE)); } catch (IOException ignored) {}
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[System] Mod destructed."));
        }
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
            Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
            updateRotations(client.player, targetPos);
            if (client.player.getAttackCooldownProgress(0) >= 1.0f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void updateRotations(PlayerEntity player, Vec3d target) {
        Vec3d diff = target.subtract(player.getEyePos());
        double dXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, dXZ));

        if (screenShake) {
            tYaw += (random.nextFloat() - 0.5f) * shakeIntensity;
            tPitch += (random.nextFloat() - 0.5f) * shakeIntensity;
        }

        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(tYaw - player.getYaw()));
        player.setPitch(player.getPitch() + MathHelper.wrapDegrees(tPitch - player.getPitch()));
    }

    private void runTrigger(MinecraftClient client) {
        double reach = kaRange;
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f).multiply(reach);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, eye.add(look), client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0), (e) -> e instanceof PlayerEntity && e.isAlive(), reach * reach);
        
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0f : 0.95f)) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void renderESP(WorldRenderEvents.Context context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        for (Entity e : client.world.getEntities()) {
            if (e instanceof PlayerEntity && e != client.player && e.isAlive()) {
                renderBox(context.matrixStack(), e, context.camera().getPos());
            }
        }
    }

    private void renderBox(MatrixStack ms, Entity e, Vec3d cam) {
        MinecraftClient client = MinecraftClient.getInstance();
        ms.push();
        double x = e.prevX + (e.getX() - e.prevX) * client.getTickDelta() - cam.x;
        double y = e.prevY + (e.getY() - e.prevY) * client.getTickDelta() - cam.y;
        double z = e.prevZ + (e.getZ() - e.prevZ) * client.getTickDelta() - cam.z;
        ms.translate(x, y + e.getHeight() + 0.5, z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-client.gameRenderer.getCamera().getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(client.gameRenderer.getCamera().getPitch()));
        ms.scale(-0.025f, -0.025f, 0.025f);
        String text = "§d" + e.getName().getString() + " §f" + (int)((LivingEntity)e).getHealth() + "hp";
        client.textRenderer.draw(text, -client.textRenderer.getWidth(text)/2f, 0, -1, false, ms.peek().getPositionMatrix(), client.getBufferBuilders().getEntityVertexConsumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
        ms.pop();
    }

    private void renderWaypoint(WorldRenderEvents.Context context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        MatrixStack ms = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        ms.push();
        ms.translate(wpX - cam.x, wpY - cam.y + 1.5, wpZ - cam.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
        float s = (float) Math.max(0.02, client.player.distanceTo(new Vec3d(wpX, wpY, wpZ)) * 0.012);
        ms.scale(-s, -s, s);
        String text = "§b[!] TARGET";
        client.textRenderer.draw(text, -client.textRenderer.getWidth(text)/2f, 0, -1, false, ms.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
        ms.pop();
    }

    private void sendNotify(String mod, boolean state) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + mod + ": " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    // --- GUI СЕКЦИЯ ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x80000000);
            int x = width/2 - 90, y = height/2 - 105;
            ctx.fill(x, y, x + 180, y + 155, 0xFF050505);
            ctx.drawBorder(x, y, 180, 155, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 10, -1);
            
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "ESP"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, waypointActive, esp};
            int[] k = {keyKA, keyTB, keyFB, keyAT, keyWP, GLFW.GLFW_KEY_UNKNOWN};

            for (int i = 0; i < n.length; i++) {
                int iy = y + 35 + (i * 18);
                boolean h = mx >= x + 10 && mx <= x + 170 && my >= iy && my < iy + 14;
                ctx.fill(x + 10, iy, x + 170, iy + 14, h ? 0xFF1A1A1A : 0xFF101010);
                String kn = k[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : InputUtil.fromKeyCode(k[i], 0).getLocalizedText().getString().toUpperCase();
                ctx.drawTextWithShadow(textRenderer, n[i] + " §7[" + (s[i] ? "§aON" : "§cOFF") + "§7] §8(" + kn + ")", x + 15, iy + 3, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 90, y = height/2 - 105;
            for (int i = 0; i < 6; i++) {
                int iy = y + 35 + (i * 18);
                if (mx >= x + 10 && mx <= x + 170 && my >= iy && my < iy + 14) {
                    if (i == 0) client.setScreen(new KillAuraSettings(this));
                    if (i == 1) client.setScreen(new TriggerSettings(this));
                    if (i == 4) client.setScreen(new WaypointSettings(this));
                    if (b == 0) {
                        if (i == 2) fullbright = !fullbright;
                        if (i == 3) autoTotem = !autoTotem;
                        if (i == 5) esp = !esp;
                    } else {
                        client.setScreen(new BindScreen(this, i));
                    }
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
            f1 = new TextFieldWidget(textRenderer, x, height/2 - 45, 45, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2 - 20, 45, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height/2 + 5, 45, 16, Text.literal(""));
            f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange)); f3.setText(String.valueOf(shakeIntensity));
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int cx = width/2;
            ctx.fill(cx - 115, height/2 - 95, cx + 115, height/2 + 95, 0xFF0A0A0A);
            ctx.drawBorder(cx - 115, height/2 - 95, 230, 190, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKILL AURA", cx, height/2 - 85, -1);
            ctx.drawTextWithShadow(textRenderer, "Дистанция:", cx - 105, height/2 - 41, -1);
            ctx.drawTextWithShadow(textRenderer, "Стены:", cx - 105, height/2 - 16, -1);
            ctx.drawTextWithShadow(textRenderer, "Тряска:", cx - 105, height/2 + 9, -1);
            drawCfgBtn(ctx, "AresMine", cx + 10, height/2 + 45, mx, my);
            drawCfgBtn(ctx, "MineBlaze", cx + 10, height/2 + 20, mx, my);
            drawCfgBtn(ctx, "FunTime", cx + 10, height/2 + 70, mx, my);
            super.render(ctx, mx, my, d);
        }
        private void drawCfgBtn(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 80 && my >= y && my < y + 16;
            ctx.fill(x, y, x + 80, y + 16, h ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x + 40, y + 4, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width/2;
            if (mx >= cx + 10 && mx <= cx + 90) {
                if (my >= height/2 + 45 && my < height/2 + 61) { kaRange = 3.8; kaWallsRange = 3.0; shakeIntensity = 0.8f; autoRun = true; updateFields(); }
                if (my >= height/2 + 20 && my < height/2 + 36) { kaRange = 3.1; kaWallsRange = 0.0; shakeIntensity = 0.2f; autoRun = true; updateFields(); }
                if (my >= height/2 + 70 && my < height/2 + 86) { kaRange = 3.6; kaWallsRange = 3.0; shakeIntensity = 0.5f; autoRun = true; updateFields(); }
            }
            return super.mouseClicked(mx, my, b);
        }
        private void updateFields() {
            f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange)); f3.setText(String.valueOf(shakeIntensity));
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(f1.getText());
                    kaWallsRange = Double.parseDouble(f2.getText());
                    shakeIntensity = Float.parseFloat(f3.getText());
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen p; public TriggerSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x - 80, y - 40, x + 80, y + 40, 0xFF0A0A0A);
            ctx.drawBorder(x - 80, y - 40, 160, 80, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bTRIGGER BOT", x, y - 30, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), x, y, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (mx >= width/2 - 70 && mx <= width/2 + 70 && my >= height/2 - 10 && my <= height/2 + 10) {
                tbCrits = !tbCrits; saveConfig(); return true;
            }
            return false;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) { if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(p); return true; } return false; }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2, f3;
        public WaypointSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2 - 25;
            f1 = new TextFieldWidget(textRenderer, x, height/2 - 45, 50, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2 - 20, 50, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height/2 + 5, 50, 16, Text.literal(""));
            f1.setText(String.valueOf((int)wpX)); f2.setText(String.valueOf((int)wpY)); f3.setText(String.valueOf((int)wpZ));
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int cx = width/2;
            ctx.fill(cx - 115, height/2 - 90, cx + 115, height/2 + 90, 0xFF0A0A0A);
            ctx.drawBorder(cx - 115, height/2 - 90, 230, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bWAYPOINT", cx, height/2 - 80, -1);
            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX = Double.parseDouble(f1.getText()); wpY = Double.parseDouble(f2.getText()); wpZ = Double.parseDouble(f3.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen p; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.p = p; this.id = id; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS KEY", width/2, height/2 - 10, -1);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) k = GLFW.GLFW_KEY_UNKNOWN;
            if (id == 0) keyKA = k; if (id == 1) keyTB = k; if (id == 2) keyFB = k; if (id == 3) keyAT = k; if (id == 4) keyWP = k;
            saveConfig(); client.setScreen(p); return true;
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
            w.println(kaRange+":"+kaWallsRange+":"+wpX+":"+wpY+":"+wpZ+":0:0:0:"+autoRun+":"+keyKA+":"+keyTB+":"+keyFB+":"+keyAT+":"+keyWP+":"+shakeIntensity+":"+antiVelocity+":"+tbCrits);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 17) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                wpX = Double.parseDouble(p[2]); wpY = Double.parseDouble(p[3]); wpZ = Double.parseDouble(p[4]);
                autoRun = Boolean.parseBoolean(p[8]);
                keyKA = Integer.parseInt(p[9]); keyTB = Integer.parseInt(p[10]); keyFB = Integer.parseInt(p[11]);
                keyAT = Integer.parseInt(p[12]); keyWP = Integer.parseInt(p[13]);
                shakeIntensity = Float.parseFloat(p[14]); antiVelocity = Boolean.parseBoolean(p[15]);
                tbCrits = Boolean.parseBoolean(p[16]);
            }
        } catch (Exception ignored) {}
    }
}

