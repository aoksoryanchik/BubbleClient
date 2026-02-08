package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
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
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false, esp = false, viewModel = true;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, screenShake = true, tbCrits = true;
    public static boolean isDestructed = false; 
    
    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float shakeIntensity = 0.5f;
    // Правая рука
    public static float vmX = 0.3f, vmY = -0.2f, vmZ = -0.4f;
    // Левая рука
    public static float vmLX = -0.3f, vmLY = -0.2f, vmLZ = -0.4f;

    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN, keyESP = GLFW.GLFW_KEY_UNKNOWN, keyVM = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || isDestructed) return;
            
            long h = client.getWindow().getHandle();

            if (InputUtil.isKeyPressed(h, GLFW.GLFW_KEY_RIGHT_SHIFT) && InputUtil.isKeyPressed(h, GLFW.GLFW_KEY_DELETE)) {
                selfDestruct();
                return;
            }

            // МЕНЮ НА 0 (НОЛЬ)
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());

            for (Entity e : client.world.getEntities()) {
                if (e instanceof PlayerEntity && e != client.player) {
                    e.setGlowing(esp && !isDestructed);
                }
            }

            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
                if (isPressed(h, keyESP)) { esp = !esp; sendNotify("ESP", esp); }
                if (isPressed(h, keyVM)) { viewModel = !viewModel; sendNotify("HandView", viewModel); }
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) client.player.setSprinting(true);

            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });

        WorldRenderEvents.LAST.register(context -> {
            if (waypointActive && !isDestructed) {
                renderWaypoint(context.matrixStack(), context.camera(), context.consumers());
            }
        });
    }

    private void selfDestruct() {
        isDestructed = true;
        killaura = false; triggerbot = false; fullbright = false; waypointActive = false; esp = false;
        autoTotem = false; autoRun = false; antiVelocity = false;
        try { Files.deleteIfExists(Paths.get(CONFIG_FILE)); } catch (IOException ignored) {}
    }

    private void handleAutoTotem(MinecraftClient client) {
        // Условие: меньше 6 HP (3 сердца)
        if (client.player.getHealth() <= 6.0f) {
            if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                for (int i = 0; i < 45; i++) {
                    if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, client.player);
                        break;
                    }
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
            float randomHeight = 0.35f + random.nextFloat() * 0.3f;
            Vec3d targetPos = target.getPos().add(0, target.getHeight() * randomHeight, 0);
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
        float speed = 0.3f;
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(tYaw - player.getYaw()), -speed * 50, speed * 50));
        player.setPitch(player.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(tPitch - player.getPitch()), -speed * 50, speed * 50));
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
        if (MinecraftClient.getInstance().player != null && !isDestructed) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + module + " : " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    private void renderWaypoint(MatrixStack ms, net.minecraft.client.render.Camera camera, VertexConsumerProvider vcp) {
        if (!waypointActive || vcp == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        double d = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
        ms.push();
        ms.translate(wpX - camera.getPos().x, wpY - camera.getPos().y + 1.5, wpZ - camera.getPos().z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        float s = (float) Math.max(0.02, d * 0.012);
        ms.scale(-s, -s, s);
        client.textRenderer.draw("§b[!] TARGET", -client.textRenderer.getWidth("§b[!] TARGET") / 2f, 0, -1, false, ms.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
        ms.pop();
    }

    public static class BubbleMenu extends Screen {
        private String bindingModule = null;
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width / 2 - 90, y = height / 2 - 110;
            ctx.fill(x, y, x + 180, y + 200, 0xFF050505);
            ctx.drawBorder(x, y, 180, 200, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 10, -1);
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "ESP", "HandView"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, waypointActive, esp, viewModel};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyWP, keyESP, keyVM};
            for (int i = 0; i < n.length; i++) {
                int iy = y + 35 + i * 22;
                boolean h = mx >= x + 10 && mx <= x + 170 && my >= iy && my <= iy + 18;
                ctx.fill(x + 10, iy, x + 170, iy + 18, h ? 0xFF1A1A1A : 0xFF101010);
                String keyName = (bindingModule != null && bindingModule.equals(n[i])) ? "..." : (keys[i] == -1 ? "NONE" : GLFW.glfwGetKeyName(keys[i], 0));
                if (keyName == null && keys[i] != -1) keyName = "K" + keys[i];
                ctx.drawTextWithShadow(textRenderer, n[i] + " §7[" + keyName + "]", x + 15, iy + 5, s[i] ? 0xFF00FF00 : 0xFFFF3333);
                if (i == 0 || i == 1 || i == 4 || i == 6) ctx.drawTextWithShadow(textRenderer, "⚙", x + 160, iy + 5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2 - 90, y = height / 2 - 110;
            for (int i = 0; i < 7; i++) {
                int iy = y + 35 + i * 22;
                if (mx >= x + 10 && mx <= x + 170 && my >= iy && my <= iy + 18) {
                    String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "ESP", "HandView"};
                    if (b == 1) { // ПКМ для бинда
                        bindingModule = n[i];
                        return true;
                    }
                    if (mx >= x + 155) { // Шестеренка
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        if (i == 1) client.setScreen(new TriggerSettings(this));
                        if (i == 4) client.setScreen(new WaypointSettings(this));
                        if (i == 6) client.setScreen(new HandSettings(this));
                    } else { // Обычный клик
                        if (i == 0) killaura = !killaura; if (i == 1) triggerbot = !triggerbot;
                        if (i == 2) fullbright = !fullbright; if (i == 3) autoTotem = !autoTotem;
                        if (i == 4) waypointActive = !waypointActive; if (i == 5) esp = !esp;
                        if (i == 6) viewModel = !viewModel;
                        saveConfig();
                    }
                    return true;
                }
            }
            return false;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (bindingModule != null) {
                if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_DELETE) k = -1;
                if (bindingModule.equals("KillAura")) keyKA = k;
                if (bindingModule.equals("TriggerBot")) keyTB = k;
                if (bindingModule.equals("FullBright")) keyFB = k;
                if (bindingModule.equals("AutoTotem")) keyAT = k;
                if (bindingModule.equals("Waypoint")) keyWP = k;
                if (bindingModule.equals("ESP")) keyESP = k;
                if (bindingModule.equals("HandView")) keyVM = k;
                bindingModule = null; saveConfig(); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class HandSettings extends Screen {
        private final Screen p; private TextFieldWidget fX, fY, fZ, fLX, fLY, fLZ;
        public HandSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int xR = width / 2 - 60, xL = width / 2 + 30, y = height / 2 - 40;
            fX = new TextFieldWidget(textRenderer, xR, y, 40, 14, Text.literal(""));
            fY = new TextFieldWidget(textRenderer, xR, y + 25, 40, 14, Text.literal(""));
            fZ = new TextFieldWidget(textRenderer, xR, y + 50, 40, 14, Text.literal(""));
            fLX = new TextFieldWidget(textRenderer, xL, y, 40, 14, Text.literal(""));
            fLY = new TextFieldWidget(textRenderer, xL, y + 25, 40, 14, Text.literal(""));
            fLZ = new TextFieldWidget(textRenderer, xL, y + 50, 40, 14, Text.literal(""));
            fX.setText(String.valueOf(vmX)); fY.setText(String.valueOf(vmY)); fZ.setText(String.valueOf(vmZ));
            fLX.setText(String.valueOf(vmLX)); fLY.setText(String.valueOf(vmLY)); fLZ.setText(String.valueOf(vmLZ));
            addDrawableChild(fX); addDrawableChild(fY); addDrawableChild(fZ);
            addDrawableChild(fLX); addDrawableChild(fLY); addDrawableChild(fLZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bVIEW MODEL (RIGHT | LEFT)", width / 2, height / 2 - 70, -1);
            ctx.drawTextWithShadow(textRenderer, "X:", width / 2 - 80, height / 2 - 37, -1);
            ctx.drawTextWithShadow(textRenderer, "Y:", width / 2 - 80, height / 2 - 12, -1);
            ctx.drawTextWithShadow(textRenderer, "Z:", width / 2 - 80, height / 2 + 13, -1);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    vmX = Float.parseFloat(fX.getText()); vmY = Float.parseFloat(fY.getText()); vmZ = Float.parseFloat(fZ.getText());
                    vmLX = Float.parseFloat(fLX.getText()); vmLY = Float.parseFloat(fLY.getText()); vmLZ = Float.parseFloat(fLZ.getText());
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // --- Копируем остальные классы настроек из твоего старого кода без изменений ---
    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2, f3;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int x = width / 2 + 25;
            f1 = new TextFieldWidget(textRenderer, x, height / 2 - 45, 45, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height / 2 - 20, 45, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height / 2 + 5, 45, 16, Text.literal(""));
            f1.setText(String.format("%.1f", kaRange));
            f2.setText(String.format("%.1f", kaWallsRange));
            f3.setText(String.format("%.1f", shakeIntensity));
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width / 2, y = height / 2;
            ctx.fill(x - 115, y - 95, x + 115, y + 105, 0xFF0A0A0A);
            ctx.drawBorder(x - 115, y - 95, 230, 200, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKILL AURA", x, y - 85, -1);
            ctx.drawTextWithShadow(textRenderer, "Дистанция:", x - 105, y - 41, -1);
            ctx.drawTextWithShadow(textRenderer, "Стены:", x - 105, y - 16, -1);
            ctx.drawTextWithShadow(textRenderer, "Тряска:", x - 105, y + 9, -1);
            drawChk(ctx, "Авто-Бег", autoRun, y + 35, mx, my);
            drawChk(ctx, "Анти-Отдача", antiVelocity, y + 50, mx, my);
        }
        private void drawChk(DrawContext ctx, String n, boolean s, int y, int mx, int my) {
            boolean h = mx >= width / 2 - 60 && mx <= width / 2 + 60 && my >= y && my <= y + 12;
            ctx.drawCenteredTextWithShadow(textRenderer, n + ": " + (s ? "§aВКЛ" : "§cВЫКЛ"), width / 2, y, h ? 0xFFCCCCCC : -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2, y = height / 2;
            if (mx >= x - 60 && mx <= x + 60) {
                if (my >= y + 35 && my <= y + 47) autoRun = !autoRun;
                if (my >= y + 50 && my <= y + 62) antiVelocity = !antiVelocity;
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(f1.getText().replace(",", "."));
                    kaWallsRange = Double.parseDouble(f2.getText().replace(",", "."));
                    shakeIntensity = Float.parseFloat(f3.getText().replace(",", "."));
                } catch (Exception ignored) {}
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
            int x = width / 2, y = height / 2;
            ctx.fill(x - 80, y - 40, x + 80, y + 40, 0xFF0A0A0A);
            ctx.drawBorder(x - 80, y - 40, 160, 80, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bTRIGGER BOT", x, y - 30, -1);
            boolean h = mx >= x - 70 && mx <= x + 70 && my >= y && my <= y + 12;
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), x, y, h ? 0xFFCCCCCC : -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (mx >= width / 2 - 70 && mx <= width / 2 + 70 && my >= height / 2 && my <= height / 2 + 12) { tbCrits = !tbCrits; saveConfig(); return true; }
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
            int x = width / 2 + 20;
            f1 = new TextFieldWidget(textRenderer, x, height / 2 - 45, 50, 16, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, x, height / 2 - 20, 50, 16, Text.literal(""));
            f3 = new TextFieldWidget(textRenderer, x, height / 2 + 5, 50, 16, Text.literal(""));
            f1.setText(String.valueOf((int)wpX)); f2.setText(String.valueOf((int)wpY)); f3.setText(String.valueOf((int)wpZ));
            addDrawableChild(f1); addDrawableChild(f2); addDrawableChild(f3);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width / 2, y = height / 2;
            ctx.fill(x - 115, y - 90, x + 115, y + 90, 0xFF0A0A0A);
            ctx.drawBorder(x - 115, y - 90, 230, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bWAYPOINT", x, y - 80, -1);
            f1.render(ctx, mx, my, d); f2.render(ctx, mx, my, d); f3.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX = Double.parseDouble(f1.getText()); wpY = Double.parseDouble(f2.getText()); wpZ = Double.parseDouble(f3.getText()); } catch (Exception ignored){}
                saveConfig(); client.setScreen(p); return true;
            }
            return f1.keyPressed(k, s, m) || f2.keyPressed(k, s, m) || f3.keyPressed(k, s, m);
        }
    }

    public static void saveConfig() {
        if (isDestructed) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + vmX + ":" + vmY + ":" + vmZ + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + shakeIntensity + ":" + antiVelocity + ":" + tbCrits + ":" + vmLX + ":" + vmLY + ":" + vmLZ + ":" + keyESP + ":" + keyVM);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 17) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                wpX = Double.parseDouble(p[2]); wpY = Double.parseDouble(p[3]); wpZ = Double.parseDouble(p[4]);
                vmX = Float.parseFloat(p[5]); vmY = Float.parseFloat(p[6]); vmZ = Float.parseFloat(p[7]);
                autoRun = Boolean.parseBoolean(p[8]);
                keyKA = Integer.parseInt(p[9]); keyTB = Integer.parseInt(p[10]); keyFB = Integer.parseInt(p[11]);
                keyAT = Integer.parseInt(p[12]); keyWP = Integer.parseInt(p[13]);
                shakeIntensity = Float.parseFloat(p[14]);
                antiVelocity = Boolean.parseBoolean(p[15]);
                tbCrits = Boolean.parseBoolean(p[16]);
                if (p.length >= 22) {
                    vmLX = Float.parseFloat(p[17]); vmLY = Float.parseFloat(p[18]); vmLZ = Float.parseFloat(p[19]);
                    keyESP = Integer.parseInt(p[20]); keyVM = Integer.parseInt(p[21]);
                }
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
