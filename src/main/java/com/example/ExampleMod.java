package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.VertexConsumerProvider;
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
import java.util.Random;

public class ExampleMod implements ModInitializer {
    // Состояния модулей (все на месте)
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false, esp = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true;
    
    // Настройки
    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float shakeIntensity = 0.5f;

    // Клавиши
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            // Открытие меню на G или 0
            if ((isPressed(h, GLFW.GLFW_KEY_G) || isPressed(h, GLFW.GLFW_KEY_0)) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }

            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) client.player.setSprinting(true);

            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(client.player.getVelocity().x * 0.6, client.player.getVelocity().y, client.player.getVelocity().z * 0.6);
            }

            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });

        WorldRenderEvents.LAST.register(this::renderWaypoint);
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
            // ИСПРАВЛЕНИЕ: Создаем финальную копию для лямбды, чтобы билд не падал
            final PlayerEntity finalTarget = target; 
            Vec3d targetPos = finalTarget.getPos().add(0, finalTarget.getHeight() * 0.5, 0);
            updateRotations(client.player, targetPos, 100.0f);

            double reach = kaRange;
            Vec3d eye = client.player.getEyePos();
            Vec3d look = client.player.getRotationVec(1.0F).multiply(reach);
            Box box = finalTarget.getBoundingBox().expand(0.1);
            
            // Здесь была ошибка компиляции в логах Github
            EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e == finalTarget, reach * reach);

            if (client.player.getAttackCooldownProgress(0) >= 1.0f && hit != null) {
                client.interactionManager.attackEntity(client.player, finalTarget);
                client.player.swingHand(Hand.MAIN_HAND);
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
            if (client.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0f : 0.92f)) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void updateRotations(PlayerEntity player, Vec3d target, float speed) {
        Vec3d diff = target.subtract(player.getEyePos());
        double dXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, dXZ));
        
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(tYaw - player.getYaw()), -speed, speed));
        player.setPitch(player.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(tPitch - player.getPitch()), -speed, speed));
    }

    private void sendNotify(String module, boolean state) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + module + " : " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    private void renderWaypoint(WorldRenderContext context) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        double d = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
        MatrixStack ms = context.matrixStack();
        ms.push();
        ms.translate(wpX - context.camera().getPos().x, wpY - context.camera().getPos().y + 1.5, wpZ - context.camera().getPos().z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
        float s = (float) Math.max(0.02, d * 0.012);
        ms.scale(-s, -s, s);
        VertexConsumerProvider vcp = context.consumers();
        if (vcp != null) {
            String text = "§b[!] TARGET";
            client.textRenderer.draw(text, -client.textRenderer.getWidth(text) / 2f, 0, -1, false, ms.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
        }
        ms.pop();
    }

    // --- GUI ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int nx, int ny, float d) {
            ctx.fill(0, 0, width, height, 0x90000000); 
            int x = width/2 - 90, y = height/2 - 105;
            ctx.fill(x, y, x + 180, y + 170, 0xFF050505);
            ctx.drawBorder(x, y, 180, 170, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 10, -1);
            
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "Anti-Velocity"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, waypointActive, antiVelocity};
            int[] k = {keyKA, keyTB, keyFB, keyAT, keyWP, -1};

            for(int i = 0; i < n.length; i++) {
                int iy = y + 35 + i * 22;
                boolean h = nx >= x + 10 && nx <= x + 170 && ny >= iy && ny <= iy + 18;
                ctx.fill(x + 10, iy, x + 170, iy + 18, h ? 0xFF1A1A1A : 0xFF101010);
                String kn = (k[i] == GLFW.GLFW_KEY_UNKNOWN || k[i] == -1) ? "" : " §7[" + GLFW.glfwGetKeyName(k[i], 0).toUpperCase() + "]";
                ctx.drawTextWithShadow(textRenderer, n[i] + kn, x + 15, iy + 5, s[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i == 0 || i == 1 || i == 4) ctx.drawTextWithShadow(textRenderer, "⚙", x + 155, iy + 5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double nx, double ny, int b) {
            int x = width/2 - 90, y = height/2 - 105;
            for(int i = 0; i < 6; i++) {
                int iy = y + 35 + i * 22;
                if(nx >= x + 150 && nx <= x + 170 && ny >= iy && ny <= iy + 18) {
                    if(i == 0) client.setScreen(new KillAuraSettings(this));
                    if(i == 1) client.setScreen(new TriggerSettings(this));
                    if(i == 4) client.setScreen(new WaypointSettings(this));
                    return true;
                } else if(nx >= x + 10 && nx <= x + 150 && ny >= iy && ny <= iy + 18) {
                    if(i == 0) killaura = !killaura; if(i == 1) triggerbot = !triggerbot;
                    if(i == 2) fullbright = !fullbright; if(i == 3) autoTotem = !autoTotem;
                    if(i == 4) waypointActive = !waypointActive; if(i == 5) antiVelocity = !antiVelocity;
                    saveConfig(); return true;
                }
            }
            return false;
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2 + 25;
            f1 = new TextFieldWidget(textRenderer, x, height/2 - 45, 45, 14, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2 - 20, 45, 14, Text.literal(""));
            f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange));
            addDrawableChild(f1); addDrawableChild(f2);
        }
        @Override
        public void render(DrawContext ctx, int nx, int ny, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x - 110, y - 90, x + 110, y + 90, 0xFF0A0A0A);
            ctx.drawBorder(x - 110, y - 90, 220, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKILL AURA SETTINGS", x, y - 80, -1);
            ctx.drawTextWithShadow(textRenderer, "Reach:", x - 100, y - 42, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", x - 100, y - 17, -1);
            
            int cx = x - 235;
            ctx.fill(cx, y - 90, cx + 115, y + 90, 0xFF0A0A0A);
            ctx.drawBorder(cx, y - 90, 115, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bCONFIGS", cx + 57, y - 80, -1);
            drawCfgBtn(ctx, "AresMine", cx + 7, y - 45, nx, ny);
            drawCfgBtn(ctx, "MineBlaze", cx + 7, y - 20, nx, ny);
            drawCfgBtn(ctx, "FunTime", cx + 7, y + 5, nx, ny);
            super.render(ctx, nx, ny, d);
        }
        private void drawCfgBtn(DrawContext ctx, String n, int x, int y, int mx, int my) {
            boolean h = mx >= x && mx <= x + 100 && my >= y && my <= y + 18;
            ctx.fill(x, y, x + 100, y + 18, h ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x + 50, y + 5, h ? 0xFF00AAFF : -1);
        }
        @Override
        public boolean mouseClicked(double nx, double ny, int b) {
            int x = width/2, y = height/2, cx = x - 235;
            if (nx >= cx + 7 && nx <= cx + 107) {
                if (ny >= y - 45 && ny <= y - 27) { kaRange = 3.6; kaWallsRange = 3.0; updateFields(); return true; }
                if (ny >= y - 20 && ny <= y - 2) { kaRange = 3.9; kaWallsRange = 3.2; updateFields(); return true; }
                if (ny >= y + 5 && ny <= y + 23) { kaRange = 3.1; kaWallsRange = 2.4; updateFields(); return true; }
            }
            return super.mouseClicked(nx, ny, b);
        }
        private void updateFields() { f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange)); saveConfig(); }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(f1.getText());
                    kaWallsRange = Double.parseDouble(f2.getText());
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen p;
        public TriggerSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x - 80, y - 40, x + 80, y + 40, 0xFF0A0A0A);
            ctx.drawBorder(x - 80, y - 40, 160, 80, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bTRIGGER BOT", x, y - 30, -1);
            boolean h = mx >= x - 70 && mx <= x + 70 && my >= y && my <= y + 12;
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), x, y + 5, h ? 0xFF00AAFF : -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x - 70 && mx <= x + 70 && my >= y && my <= y + 12) { tbCrits = !tbCrits; saveConfig(); return true; }
            return false;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(p); return true; }
            return false;
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2, f3;
        public WaypointSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width/2 + 20;
            f1 = new TextFieldWidget(textRenderer, x, height/2 - 45, 50, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height/2 - 20, 50, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height/2 + 5, 50, 16, Text.literal(""));
            f1.setText(String.valueOf((int)wpX)); f2.setText(String.valueOf((int)wpY)); f3.setText(String.valueOf((int)wpZ));
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x - 110, y - 90, x + 110, y + 90, 0xFF0A0A0A);
            ctx.drawBorder(x - 110, y - 90, 220, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bWAYPOINT", x, y - 80, -1);
            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int n) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    wpX = Double.parseDouble(f1.getText()); wpY = Double.parseDouble(f2.getText()); wpZ = Double.parseDouble(f3.getText());
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, n);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + antiVelocity + ":" + tbCrits);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 13) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                wpX = Double.parseDouble(p[2]); wpY = Double.parseDouble(p[3]); wpZ = Double.parseDouble(p[4]);
                autoRun = Boolean.parseBoolean(p[5]);
                keyKA = Integer.parseInt(p[6]); keyTB = Integer.parseInt(p[7]); keyFB = Integer.parseInt(p[8]);
                keyAT = Integer.parseInt(p[9]); keyWP = Integer.parseInt(p[10]);
                antiVelocity = Boolean.parseBoolean(p[11]); tbCrits = Boolean.parseBoolean(p[12]);
            }
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k == GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }
}

