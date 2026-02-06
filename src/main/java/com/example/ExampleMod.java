package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExampleMod implements ModInitializer {
    // Состояния функций
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false, autoTotem = true, noFire = true, viewModelActive = true;
    
    // Параметры
    public static double kaRange = 3.8, kaWallsRange = 3.0, wpX = 0, wpY = 64, wpZ = 0;
    public static boolean kaAutoRun = true;
    
    // ViewModel (Руки)
    public static float handX = 0.0f, handY = 0.0f, handZ = 0.0f;

    // Клавиши
    public static int killauraKey = GLFW.GLFW_KEY_P, triggerbotKey = GLFW.GLFW_KEY_R, fbKey = GLFW.GLFW_KEY_M, wpKey = GLFW.GLFW_KEY_V, menuKey = GLFW.GLFW_KEY_0;
    public static String bindingFor = "";
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long h = client.getWindow().getHandle();
            
            // Открытие меню
            if (isPressed(h, menuKey) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Хоткеи вне меню
            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) toggle(client, "Killaura", !killaura);
                if (isPressed(h, triggerbotKey)) toggle(client, "Triggerbot", !triggerbot);
                if (isPressed(h, fbKey)) toggle(client, "FullBright", !fullbright);
                if (isPressed(h, wpKey)) toggle(client, "Waypoint", !waypointActive);
            }

            // Фоновая работа функций
            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) runAutoTotem(client);
            if (noFire && client.player.isOnFire()) client.player.setExtinguished(true);
            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        // 3D Метка (до 400 метров)
        WorldRenderEvents.LAST.register(context -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (dist > 400) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = context.camera().getPos();
            matrices.push();
            matrices.translate(wpX - camPos.x, (wpY - camPos.y) + 1.5, wpZ - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            
            float scale = (float) Math.max(0.03, dist * 0.01);
            matrices.scale(-scale, -scale, scale);

            VertexConsumerProvider consumers = context.consumers();
            if (consumers != null) {
                Matrix4f posMat = matrices.peek().getPositionMatrix();
                String t = "§b[!] ИВЕНТ §f(" + (int)dist + "m)";
                client.textRenderer.draw(t, -client.textRenderer.getWidth(t)/2f, 0, -1, false, posMat, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            matrices.pop();
        });

        // HUD Навигатор (если дальше 400м)
        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (dist > 400) {
                String info = String.format("§b➔ EVENT: §f[%.0f, %.0f, %.0f] §e(%.0f m)", wpX, wpY, wpZ, dist);
                drawContext.drawCenteredTextWithShadow(client.textRenderer, info, drawContext.getScaledWindowWidth() / 2, 10, -1);
            }
        });
    }

    private void runAutoTotem(MinecraftClient client) {
        // Если здоровья меньше 2 сердец и в левой руке не тотем
        if (client.player.getHealth() <= 4.0f && client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    int slot = i < 9 ? i + 36 : i;
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 45, SlotActionType.SWAP, client.player);
                    break;
                }
            }
        }
    }

    private void runKillaura(MinecraftClient client) {
        PlayerEntity target = null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && p.isAlive()) {
                double d = client.player.distanceTo(p);
                if (d <= (client.player.canSee(p) ? kaRange : kaWallsRange)) { target = p; break; }
            }
        }
        if (target != null) {
            if (kaAutoRun && client.player.input.movementForward > 0) client.player.setSprinting(true);
            client.player.setYaw((float) Math.toDegrees(Math.atan2(target.getZ() - client.player.getZ(), target.getX() - client.player.getX())) - 90);
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTriggerbot(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult res && res.getEntity() instanceof PlayerEntity) {
            if (client.player.getAttackCooldownProgress(0) >= 0.9f) {
                client.interactionManager.attackEntity(client.player, res.getEntity());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void toggle(MinecraftClient c, String n, boolean s) {
        if(n.equals("Killaura")) killaura = s;
        if(n.equals("Triggerbot")) triggerbot = s;
        if(n.equals("FullBright")) fullbright = s;
        if(n.equals("Waypoint")) waypointActive = s;
        saveConfig();
        c.player.sendMessage(Text.literal("§bBubble §8» §f" + n + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    // --- СИСТЕМА МЕНЮ ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 90;
            ctx.fill(x, y, x + 160, y + 180, 0xFF121212);
            ctx.drawBorder(x, y, 160, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            
            drawBtn(ctx, x+10, y+25, "KillAura", killaura, "ka", mx, my, true);
            drawBtn(ctx, x+10, y+45, "TriggerBot", triggerbot, "tb", mx, my, false);
            drawBtn(ctx, x+10, y+65, "AutoTotem", autoTotem, "at", mx, my, false);
            drawBtn(ctx, x+10, y+85, "NoFire", noFire, "nf", mx, my, false);
            drawBtn(ctx, x+10, y+105, "FullBright", fullbright, "fb", mx, my, false);
            drawBtn(ctx, x+10, y+125, "Waypoint", waypointActive, "wp", mx, my, true);
            drawBtn(ctx, x+10, y+145, "ViewModel", viewModelActive, "vm", mx, my, true);
        }

        private void drawBtn(DrawContext ctx, int x, int y, String n, boolean on, String id, int mx, int my, boolean settings) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            ctx.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            ctx.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            ctx.drawTextWithShadow(textRenderer, n, x + 15, y + 4, -1);
            if (settings) {
                boolean hs = mx >= x + 125 && mx <= x + 140 && my >= y && my <= y + 16;
                ctx.drawTextWithShadow(textRenderer, hs ? "§f⚙" : "§b⚙", x + 130, y + 4, -1);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 80, y = height/2 - 90;
            // Клики по шестеренкам (ПКМ)
            if (b == 1 || (b == 0 && mx >= x + 125)) {
                if (my >= y + 25 && my <= y + 41) client.setScreen(new KillAuraSettings(this));
                if (my >= y + 125 && my <= y + 141) client.setScreen(new WaypointSettings(this));
                if (my >= y + 145 && my <= y + 161) client.setScreen(new HandSettings(this));
            } 
            // Обычные включения
            else if (mx >= x + 10 && mx <= x + 125) {
                if (my >= y + 25 && my <= y + 41) killaura = !killaura;
                if (my >= y + 45 && my <= y + 61) triggerbot = !triggerbot;
                if (my >= y + 65 && my <= y + 81) autoTotem = !autoTotem;
                if (my >= y + 85 && my <= y + 101) noFire = !noFire;
                if (my >= y + 105 && my <= y + 121) fullbright = !fullbright;
                if (my >= y + 125 && my <= y + 141) waypointActive = !waypointActive;
                if (my >= y + 145 && my <= y + 161) viewModelActive = !viewModelActive;
            }
            saveConfig();
            return super.mouseClicked(mx, my, b);
        }
    }

    // --- ОКНА НАСТРОЕК ---

    public static class HandSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget fX, fY, fZ;
        public HandSettings(Screen p) { super(Text.literal("Hands")); this.parent = p; }
        @Override
        protected void init() {
            fX = new TextFieldWidget(textRenderer, width/2 - 30, height/2 - 40, 60, 12, Text.literal("")); fX.setText(String.valueOf(handX));
            fY = new TextFieldWidget(textRenderer, width/2 - 30, height/2 - 20, 60, 12, Text.literal("")); fY.setText(String.valueOf(handY));
            fZ = new TextFieldWidget(textRenderer, width/2 - 30, height/2, 60, 12, Text.literal("")); fZ.setText(String.valueOf(handZ));
            addSelectableChild(fX); addSelectableChild(fY); addSelectableChild(fZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "ViewModel X / Y / Z", width/2, height/2 - 60, -1);
            fX.render(ctx, mx, my, delta); fY.render(ctx, mx, my, delta); fZ.render(ctx, mx, my, delta);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { handX = Float.parseFloat(fX.getText()); handY = Float.parseFloat(fY.getText()); handZ = Float.parseFloat(fZ.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget eX, eY, eZ;
        public WaypointSettings(Screen p) { super(Text.literal("WP")); this.p = p; }
        @Override
        protected void init() {
            eX = new TextFieldWidget(textRenderer, width/2 - 30, height/2 - 30, 60, 12, Text.literal("")); eX.setText(String.valueOf(wpX));
            eY = new TextFieldWidget(textRenderer, width/2 - 30, height/2 - 10, 60, 12, Text.literal("")); eY.setText(String.valueOf(wpY));
            eZ = new TextFieldWidget(textRenderer, width/2 - 30, height/2 + 10, 60, 12, Text.literal("")); eZ.setText(String.valueOf(wpZ));
            addSelectableChild(eX); addSelectableChild(eY); addSelectableChild(eZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "Координаты метки", width/2, height/2 - 50, -1);
            eX.render(ctx, mx, my, d); eY.render(ctx, mx, my, d); eZ.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { wpX = Double.parseDouble(eX.getText()); wpY = Double.parseDouble(eY.getText()); wpZ = Double.parseDouble(eZ.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget rF, wF;
        public KillAuraSettings(Screen p) { super(Text.literal("KA")); this.p = p; }
        @Override
        protected void init() {
            rF = new TextFieldWidget(textRenderer, width/2 + 10, height/2 - 20, 40, 12, Text.literal("")); rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, width/2 + 10, height/2, 40, 12, Text.literal("")); wF.setText(String.valueOf(kaWallsRange));
            addSelectableChild(rF); addSelectableChild(wF);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawTextWithShadow(textRenderer, "Range:", width/2-40, height/2-20, -1);
            ctx.drawTextWithShadow(textRenderer, "Through Walls:", width/2-40, height/2, -1);
            rF.render(ctx, mx, my, d); wF.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { kaRange = Double.parseDouble(rF.getText()); kaWallsRange = Double.parseDouble(wF.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ ---

    private boolean isPressed(long h, int k) {
        if (k <= 0 || k >= 512) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false; return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println("kaRange:" + kaRange); w.println("wpX:" + wpX); w.println("wpY:" + wpY); w.println("wpZ:" + wpZ);
            w.println("handX:" + handX); w.println("handY:" + handY); w.println("handZ:" + handZ);
            w.println("autoTotem:" + autoTotem); w.println("noFire:" + noFire);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            for (String l : lines) {
                String[] p = l.split(":");
                if (p[0].equals("kaRange")) kaRange = Double.parseDouble(p[1]);
                if (p[0].equals("wpX")) wpX = Double.parseDouble(p[1]);
                if (p[0].equals("wpY")) wpY = Double.parseDouble(p[1]);
                if (p[0].equals("wpZ")) wpZ = Double.parseDouble(p[1]);
                if (p[0].equals("handX")) handX = Float.parseFloat(p[1]);
                if (p[0].equals("handY")) handY = Float.parseFloat(p[1]);
                if (p[0].equals("handZ")) handZ = Float.parseFloat(p[1]);
                if (p[0].equals("autoTotem")) autoTotem = Boolean.parseBoolean(p[1]);
                if (p[0].equals("noFire")) noFire = Boolean.parseBoolean(p[1]);
            }
        } catch (Exception ignored) {}
    }
}

