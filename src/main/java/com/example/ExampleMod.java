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
    public static float vmX = 0.3f, vmY = -0.2f, vmZ = -0.4f;
    public static float vmLX = -0.3f, vmLY = -0.2f, vmLZ = -0.4f;

    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyWP = -1, keyESP = -1, keyVM = -1;

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

            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) client.setScreen(new BubbleMenu());

            // ESP Logic - Принудительное обновление каждый тик
            for (Entity e : client.world.getEntities()) {
                if (e instanceof PlayerEntity && e != client.player) {
                    e.setGlowing(esp);
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
            
            // Улучшенный Автототем (срабатывает чуть раньше для пинга)
            if (autoTotem && client.player.getHealth() <= 8.0f) handleAutoTotem(client);
            
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
        if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    int slot = i < 9 ? i + 36 : i;
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 45, SlotActionType.SWAP, client.player);
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
            // Рандомная точка удара (голова/грудь)
            float randomHeight = 0.4f + random.nextFloat() * 0.4f;
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

        // SMOOTH ROTATIONS (0.2f = 20% доводки в тик)
        float smooth = 0.22f;
        float deltaYaw = MathHelper.wrapDegrees(tYaw - player.getYaw());
        float deltaPitch = tPitch - player.getPitch();

        player.setYaw(player.getYaw() + deltaYaw * smooth);
        player.setPitch(player.getPitch() + deltaPitch * smooth);

        if (screenShake) {
            player.setYaw(player.getYaw() + (random.nextFloat() - 0.5f) * (shakeIntensity / 2));
            player.setPitch(player.getPitch() + (random.nextFloat() - 0.5f) * (shakeIntensity / 2));
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

    // --- GUI БЕЗ РАЗМЫТИЯ ---

    public static class BubbleMenu extends Screen {
        private String bindingModule = null;
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x95000000); // Обычный темный фон
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
                ctx.fill(x + 10, iy, x + 170, iy + 18, h ? 0xFF222222 : 0xFF111111);
                String keyName = (bindingModule != null && bindingModule.equals(n[i])) ? "..." : (keys[i] == -1 ? "NONE" : GLFW.glfwGetKeyName(keys[i], 0));
                ctx.drawTextWithShadow(textRenderer, n[i] + " §7[" + keyName + "]", x + 15, iy + 5, s[i] ? 0xFF00FF00 : 0xFFFF3333);
                if (i == 0 || i == 6) ctx.drawTextWithShadow(textRenderer, "⚙", x + 160, iy + 5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2 - 90, y = height / 2 - 110;
            for (int i = 0; i < 7; i++) {
                int iy = y + 35 + i * 22;
                if (mx >= x + 10 && mx <= x + 170 && my >= iy && my <= iy + 18) {
                    if (b == 1) { bindingModule = new String[]{"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint", "ESP", "HandView"}[i]; return true; }
                    if (mx >= x + 155) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        if (i == 6) client.setScreen(new HandSettings(this));
                    } else {
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
                if (k == GLFW.GLFW_KEY_ESCAPE) k = -1;
                if (bindingModule.equals("KillAura")) keyKA = k;
                if (bindingModule.equals("AutoTotem")) keyAT = k;
                if (bindingModule.equals("ESP")) keyESP = k;
                if (bindingModule.equals("HandView")) keyVM = k;
                bindingModule = null; saveConfig(); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget f1, f2;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            f1 = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 40, 40, 14, Text.literal(""));
            f2 = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 20, 40, 14, Text.literal(""));
            f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange));
            addDrawableChild(f1); addDrawableChild(f2);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xCC000000);
            int x = width / 2, y = height / 2;
            ctx.fill(x - 120, y - 90, x + 120, y + 80, 0xFF0A0A0A);
            ctx.drawBorder(x - 120, y - 90, 240, 170, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKILL AURA", x, y - 80, -1);
            
            ctx.drawTextWithShadow(textRenderer, "Range:", x - 20, y - 37, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", x - 20, y - 17, -1);
            
            // Серверные конфиги
            String[] cfgs = {"AresMine", "MineBlaze", "FunTime"};
            for (int i = 0; i < 3; i++) {
                int cy = y - 40 + i * 25;
                ctx.fill(x - 110, cy, x - 40, cy + 18, 0xFF151515);
                ctx.drawCenteredTextWithShadow(textRenderer, cfgs[i], x - 75, cy + 5, 0xFF00AAFF);
            }
            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            for (int i = 0; i < 3; i++) {
                int cy = y - 40 + i * 25;
                if (mx >= x - 110 && mx <= x - 40 && my >= cy && my <= cy + 18) {
                    if (i == 0) { kaRange = 3.7; kaWallsRange = 3.0; } // Ares
                    if (i == 1) { kaRange = 3.4; kaWallsRange = 2.8; } // Blaze
                    if (i == 2) { kaRange = 3.1; kaWallsRange = 2.5; } // Fun
                    f1.setText(String.valueOf(kaRange)); f2.setText(String.valueOf(kaWallsRange));
                    return true;
                }
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange = Double.parseDouble(f1.getText()); kaWallsRange = Double.parseDouble(f2.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class HandSettings extends Screen {
        private final Screen p; private TextFieldWidget rX, rY, rZ, lX, lY, lZ;
        public HandSettings(Screen p) { super(Text.literal("")); this.p = p; }
        @Override
        protected void init() {
            int xR = width/2 - 70, xL = width/2 + 10, y = height/2 - 30;
            rX = new TextFieldWidget(textRenderer, xR, y, 35, 14, Text.literal(""));
            rY = new TextFieldWidget(textRenderer, xR, y+20, 35, 14, Text.literal(""));
            rZ = new TextFieldWidget(textRenderer, xR, y+40, 35, 14, Text.literal(""));
            lX = new TextFieldWidget(textRenderer, xL, y, 35, 14, Text.literal(""));
            lY = new TextFieldWidget(textRenderer, xL, y+20, 35, 14, Text.literal(""));
            lZ = new TextFieldWidget(textRenderer, xL, y+40, 35, 14, Text.literal(""));
            rX.setText(String.valueOf(vmX)); rY.setText(String.valueOf(vmY)); rZ.setText(String.valueOf(vmZ));
            lX.setText(String.valueOf(vmLX)); lY.setText(String.valueOf(vmLY)); lZ.setText(String.valueOf(vmLZ));
            addDrawableChild(rX); addDrawableChild(rY); addDrawableChild(rZ);
            addDrawableChild(lX); addDrawableChild(lY); addDrawableChild(lZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xCC000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bRight Hand      §fLeft Hand", width/2, height/2 - 50, -1);
            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    vmX = Float.parseFloat(rX.getText()); vmY = Float.parseFloat(rY.getText()); vmZ = Float.parseFloat(rZ.getText());
                    vmLX = Float.parseFloat(lX.getText()); vmLY = Float.parseFloat(lY.getText()); vmLZ = Float.parseFloat(lZ.getText());
                } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":" + vmX + ":" + vmY + ":" + vmZ + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + shakeIntensity + ":" + antiVelocity + ":" + tbCrits + ":" + vmLX + ":" + vmLY + ":" + vmLZ + ":" + keyESP + ":" + keyVM);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":");
            if (p.length >= 22) {
                kaRange = Double.parseDouble(p[0]); kaWallsRange = Double.parseDouble(p[1]);
                vmX = Float.parseFloat(p[5]); vmY = Float.parseFloat(p[6]); vmZ = Float.parseFloat(p[7]);
                keyKA = Integer.parseInt(p[9]); keyAT = Integer.parseInt(p[12]);
                vmLX = Float.parseFloat(p[17]); vmLY = Float.parseFloat(p[18]); vmLZ = Float.parseFloat(p[19]);
                keyESP = Integer.parseInt(p[20]); keyVM = Integer.parseInt(p[21]);
            }
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k <= 0) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }
}

