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
    // Состояния модулей
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false, esp = false, viewModel = true;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, screenShake = true, tbCrits = true;
    public static boolean isDestructed = false; 
    
    // Настройки
    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float shakeIntensity = 0.5f;
    public static float vmX = 0.3f, vmY = -0.2f, vmZ = -0.4f;
    public static float vmLX = -0.3f, vmLY = -0.2f, vmLZ = -0.4f;

    // Клавиши
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

            // Самоуничтожение (Shift + Delete)
            if (InputUtil.isKeyPressed(h, GLFW.GLFW_KEY_RIGHT_SHIFT) && InputUtil.isKeyPressed(h, GLFW.GLFW_KEY_DELETE)) {
                selfDestruct();
                return;
            }

            // Открытие меню на "0"
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // ESP (Glow эффект)
            for (Entity e : client.world.getEntities()) {
                if (e instanceof PlayerEntity && e != client.player) {
                    e.setGlowing(esp);
                }
            }

            // Обработка биндов
            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
                if (isPressed(h, keyESP)) { esp = !esp; sendNotify("ESP", esp); }
                if (isPressed(h, keyVM)) { viewModel = !viewModel; sendNotify("HandView", viewModel); }
            }

            // Работа модулей
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            } else {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            
            if (autoTotem && client.player.getHealth() <= 8.0f) handleAutoTotem(client);
            
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) {
                client.player.setSprinting(true);
            }

            // AntiVelocity (упрощенный обход отдачи)
            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(client.player.getVelocity().x * 0.6, client.player.getVelocity().y, client.player.getVelocity().z * 0.6);
            }

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
        float smooth = 0.22f;
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(tYaw - player.getYaw()) * smooth);
        player.setPitch(player.getPitch() + (tPitch - player.getPitch()) * smooth);
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

    // --- GUI СЕКЦИЯ ---

    public static class BubbleMenu extends Screen {
        private String bindingModule = null;
        public BubbleMenu() { super(Text.literal("")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000); 
            int x = width / 2 - 95, y = height / 2 - 120;
            ctx.fill(x, y, x + 190, y + 245, 0xFF050505);
            ctx.drawBorder(x, y, 190, 245, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width / 2, y + 10, -1);
            
            // Список модулей (теперь их 8)
            String[] n = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "HandView", "AutoRun", "AntiVelocity"};
            boolean[] s = {killaura, triggerbot, fullbright, autoTotem, esp, viewModel, autoRun, antiVelocity};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyESP, keyVM, -1, -1};

            for (int i = 0; i < n.length; i++) {
                int iy = y + 35 + i * 24;
                boolean hovered = mx >= x + 10 && mx <= x + 180 && my >= iy && my <= iy + 20;
                ctx.fill(x + 10, iy, x + 180, iy + 20, hovered ? 0xFF222222 : 0xFF111111);
                
                String keyText = "";
                if (i < 6) { // Бинды только для первых 6
                    keyText = " §7[" + (bindingModule != null && bindingModule.equals(n[i]) ? "..." : (keys[i] == -1 ? "NONE" : GLFW.glfwGetKeyName(keys[i], 0))) + "]";
                }
                
                ctx.drawTextWithShadow(textRenderer, n[i] + keyText, x + 15, iy + 6, s[i] ? 0xFF00FF00 : 0xFFFF3333);
                
                // Значок настроек для Ауры и Рук
                if (i == 0 || i == 5) {
                    ctx.drawTextWithShadow(textRenderer, "⚙", x + 168, iy + 6, -1);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width / 2 - 95, y = height / 2 - 120;
            for (int i = 0; i < 8; i++) {
                int iy = y + 35 + i * 24;
                if (mx >= x + 10 && mx <= x + 180 && my >= iy && my <= iy + 20) {
                    // Установка бинда (правая кнопка мыши)
                    if (b == 1 && i < 6) {
                        bindingModule = new String[]{"KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "HandView"}[i];
                        return true;
                    }
                    // Переход в настройки (клик по шестеренке справа)
                    if (mx >= x + 160) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        if (i == 5) client.setScreen(new HandSettings(this));
                    } else {
                        // Простое переключение
                        switch(i) {
                            case 0: killaura = !killaura; break;
                            case 1: triggerbot = !triggerbot; break;
                            case 2: fullbright = !fullbright; break;
                            case 3: autoTotem = !autoTotem; break;
                            case 4: esp = !esp; break;
                            case 5: viewModel = !viewModel; break;
                            case 6: autoRun = !autoRun; break;
                            case 7: antiVelocity = !antiVelocity; break;
                        }
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
                if (bindingModule.equals("TriggerBot")) keyTB = k;
                if (bindingModule.equals("FullBright")) keyFB = k;
                if (bindingModule.equals("AutoTotem")) keyAT = k;
                if (bindingModule.equals("ESP")) keyESP = k;
                if (bindingModule.equals("HandView")) keyVM = k;
                bindingModule = null;
                saveConfig();
                return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rangeField, wallsField;

        public KillAuraSettings(Screen parent) { super(Text.literal("")); this.parent = parent; }

        @Override
        protected void init() {
            rangeField = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 40, 40, 14, Text.literal(""));
            wallsField = new TextFieldWidget(textRenderer, width/2 + 30, height/2 - 20, 40, 14, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            wallsField.setText(String.valueOf(kaWallsRange));
            addDrawableChild(rangeField);
            addDrawableChild(wallsField);
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xFF000000); // ЧЕРНЫЙ ФОН БЕЗ БЛЮРА
            int x = width / 2, y = height / 2;
            ctx.fill(x - 120, y - 90, x + 120, y + 85, 0xFF0A0A0A);
            ctx.drawBorder(x - 120, y - 90, 240, 175, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bKILL AURA CONFIG", x, y - 80, -1);
            
            ctx.drawTextWithShadow(textRenderer, "Range:", x - 25, y - 37, -1);
            ctx.drawTextWithShadow(textRenderer, "Through Walls:", x - 25, y - 17, -1);

            String[] cfgs = {"AresMine", "MineBlaze", "FunTime"};
            for (int i = 0; i < 3; i++) {
                int cy = y + 10 + i * 22;
                boolean h = mx >= x - 110 && mx <= x + 110 && my >= cy && my <= cy + 18;
                ctx.fill(x - 110, cy, x + 110, cy + 18, h ? 0xFF222222 : 0xFF151515);
                ctx.drawCenteredTextWithShadow(textRenderer, "Preset: " + cfgs[i], x, cy + 5, 0xFF00AAFF);
            }
            super.render(ctx, mx, my, d);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            for (int i = 0; i < 3; i++) {
                int cy = y + 10 + i * 22;
                if (mx >= x - 110 && mx <= x + 110 && my >= cy && my <= cy + 18) {
                    if (i == 0) { kaRange = 3.7; kaWallsRange = 3.0; } // Ares
                    if (i == 1) { kaRange = 3.4; kaWallsRange = 2.8; } // Blaze
                    if (i == 2) { kaRange = 3.1; kaWallsRange = 2.5; } // Fun
                    rangeField.setText(String.valueOf(kaRange));
                    wallsField.setText(String.valueOf(kaWallsRange));
                    return true;
                }
            }
            return super.mouseClicked(mx, my, b);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(rangeField.getText());
                    kaWallsRange = Double.parseDouble(wallsField.getText());
                } catch (Exception ignored) {}
                saveConfig();
                client.setScreen(parent);
                return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class HandSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rX, rY, rZ, lX, lY, lZ;

        public HandSettings(Screen parent) { super(Text.literal("")); this.parent = parent; }

        @Override
        protected void init() {
            int xR = width/2 - 75, xL = width/2 + 15, y = height/2 - 30;
            rX = new TextFieldWidget(textRenderer, xR, y, 40, 14, Text.literal(""));
            rY = new TextFieldWidget(textRenderer, xR, y+22, 40, 14, Text.literal(""));
            rZ = new TextFieldWidget(textRenderer, xR, y+44, 40, 14, Text.literal(""));
            lX = new TextFieldWidget(textRenderer, xL, y, 40, 14, Text.literal(""));
            lY = new TextFieldWidget(textRenderer, xL, y+22, 40, 14, Text.literal(""));
            lZ = new TextFieldWidget(textRenderer, xL, y+44, 40, 14, Text.literal(""));
            rX.setText(String.valueOf(vmX)); rY.setText(String.valueOf(vmY)); rZ.setText(String.valueOf(vmZ));
            lX.setText(String.valueOf(vmLX)); lY.setText(String.valueOf(vmLY)); lZ.setText(String.valueOf(vmLZ));
            addDrawableChild(rX); addDrawableChild(rY); addDrawableChild(rZ);
            addDrawableChild(lX); addDrawableChild(lY); addDrawableChild(lZ);
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xFF050505);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bRIGHT HAND        §fLEFT HAND", width/2, height/2 - 55, -1);
            ctx.drawTextWithShadow(textRenderer, "X:", width/2 - 95, height/2 - 27, -1);
            ctx.drawTextWithShadow(textRenderer, "Y:", width/2 - 95, height/2 - 5, -1);
            ctx.drawTextWithShadow(textRenderer, "Z:", width/2 - 95, height/2 + 17, -1);
            super.render(ctx, mx, my, d);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    vmX = Float.parseFloat(rX.getText()); vmY = Float.parseFloat(rY.getText()); vmZ = Float.parseFloat(rZ.getText());
                    vmLX = Float.parseFloat(lX.getText()); vmLY = Float.parseFloat(lY.getText()); vmLZ = Float.parseFloat(lZ.getText());
                } catch (Exception ignored) {}
                saveConfig();
                client.setScreen(parent);
                return true;
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
                kaRange = Double.parseDouble(p[0]);
                kaWallsRange = Double.parseDouble(p[1]);
                vmX = Float.parseFloat(p[5]); vmY = Float.parseFloat(p[6]); vmZ = Float.parseFloat(p[7]);
                autoRun = Boolean.parseBoolean(p[8]);
                keyKA = Integer.parseInt(p[9]); keyTB = Integer.parseInt(p[10]);
                keyFB = Integer.parseInt(p[11]); keyAT = Integer.parseInt(p[12]);
                keyWP = Integer.parseInt(p[13]);
                antiVelocity = Boolean.parseBoolean(p[15]);
                vmLX = Float.parseFloat(p[17]); vmLY = Float.parseFloat(p[18]); vmLZ = Float.parseFloat(p[19]);
                keyESP = Integer.parseInt(p[20]); keyVM = Integer.parseInt(p[21]);
            }
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k <= 0) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false; return false;
    }
}

