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
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false, autoTotem = true, noFire = true, viewModelActive = true;
    public static double kaRange = 3.8, kaWallsRange = 3.0, wpX = 0, wpY = 64, wpZ = 0;
    public static boolean kaAutoRun = true;
    public static float handX = 0.0f, handY = 0.0f, handZ = 0.0f;

    public static int killauraKey = GLFW.GLFW_KEY_P, triggerbotKey = GLFW.GLFW_KEY_R, fbKey = GLFW.GLFW_KEY_M, wpKey = GLFW.GLFW_KEY_V, menuKey = GLFW.GLFW_KEY_0;
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            long h = client.getWindow().getHandle();
            
            if (isPressed(h, menuKey) && client.currentScreen == null) client.setScreen(new BubbleMenu());

            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) toggle(client, "Killaura", !killaura);
                if (isPressed(h, fbKey)) toggle(client, "FullBright", !fullbright);
                if (isPressed(h, wpKey)) toggle(client, "Waypoint", !waypointActive);
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) runAutoTotem(client);
            
            // ИСПРАВЛЕНИЕ ОШИБКИ NOFIRE
            if (noFire && client.player.isOnFire()) {
                client.player.clearFireTicks(); 
            }
            
            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        WorldRenderEvents.LAST.register(context -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (dist > 400) return;

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(wpX - context.camera().getPos().x, (wpY - context.camera().getPos().y) + 1.5, wpZ - context.camera().getPos().z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            
            float scale = (float) Math.max(0.03, dist * 0.01);
            matrices.scale(-scale, -scale, scale);

            VertexConsumerProvider consumers = context.consumers();
            if (consumers != null) {
                Matrix4f posMat = matrices.peek().getPositionMatrix();
                String t = "§b[!] ИВЕНТ §f(" + (int)dist + "m)";
                // ИСПРАВЛЕНИЕ ОШИБКИ TEXTRENDERER
                client.textRenderer.draw(t, -client.textRenderer.getWidth(t)/2f, 0, -1, false, posMat, consumers, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            matrices.pop();
        });

        HudRenderCallback.EVENT.register((drawContext, tick) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            if (dist > 400) {
                String info = "§b➔ EVENT: §f" + (int)dist + "m";
                drawContext.drawCenteredTextWithShadow(client.textRenderer, info, drawContext.getScaledWindowWidth() / 2, 10, -1);
            }
        });
    }

    // --- Остальные методы (runAutoTotem, runKillaura, BubbleMenu и т.д.) без изменений ---
    // (Используй их из прошлого сообщения, там ошибок не было)

    private void runAutoTotem(MinecraftClient client) {
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
        if(n.equals("FullBright")) fullbright = s;
        if(n.equals("Waypoint")) waypointActive = s;
        saveConfig();
        c.player.sendMessage(Text.literal("§bBubble §8» §f" + n + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 90;
            ctx.fill(x, y, x + 160, y + 180, 0xFF121212);
            ctx.drawBorder(x, y, 160, 180, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            drawBtn(ctx, x+10, y+25, "KillAura", killaura, mx, my);
            drawBtn(ctx, x+10, y+45, "AutoTotem", autoTotem, mx, my);
            drawBtn(ctx, x+10, y+65, "NoFire", noFire, mx, my);
            drawBtn(ctx, x+10, y+85, "Waypoint", waypointActive, mx, my);
            drawBtn(ctx, x+10, y+105, "ViewModel", viewModelActive, mx, my);
            ctx.drawCenteredTextWithShadow(textRenderer, "§7[⚙] Настроить", width/2, y + 150, -1);
        }
        private void drawBtn(DrawContext ctx, int x, int y, String n, boolean on, int mx, int my) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            ctx.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            ctx.drawTextWithShadow(textRenderer, n, x + 15, y + 4, on ? 0xFF00FF00 : 0xFFFF0000);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 80, y = height/2 - 90;
            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y+25 && my <= y+41) killaura = !killaura;
                if (my >= y+45 && my <= y+61) autoTotem = !autoTotem;
                if (my >= y+65 && my <= y+81) noFire = !noFire;
                if (my >= y+85 && my <= y+101) waypointActive = !waypointActive;
                if (my >= y+105 && my <= y+121) viewModelActive = !viewModelActive;
                if (my >= y+145 && my <= y+170) client.setScreen(new HandSettings(this));
            }
            saveConfig(); return super.mouseClicked(mx, my, b);
        }
    }

    public static class HandSettings extends Screen {
        private final Screen p; private TextFieldWidget fX, fY, fZ;
        public HandSettings(Screen p) { super(Text.literal("H")); this.p = p; }
        @Override
        protected void init() {
            fX = new TextFieldWidget(textRenderer, width/2-20, height/2-40, 40, 12, Text.literal("")); fX.setText(String.valueOf(handX));
            fY = new TextFieldWidget(textRenderer, width/2-20, height/2-20, 40, 12, Text.literal("")); fY.setText(String.valueOf(handY));
            fZ = new TextFieldWidget(textRenderer, width/2-20, height/2, 40, 12, Text.literal("")); fZ.setText(String.valueOf(handZ));
            addSelectableChild(fX); addSelectableChild(fY); addSelectableChild(fZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "Руки: X / Y / Z", width/2, height/2-60, -1);
            fX.render(ctx, mx, my, d); fY.render(ctx, mx, my, d); fZ.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                try { handX = Float.parseFloat(fX.getText()); handY = Float.parseFloat(fY.getText()); handZ = Float.parseFloat(fZ.getText()); } catch (Exception ignored) {}
                saveConfig(); client.setScreen(p); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    private boolean isPressed(long h, int k) {
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false; return false;
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println("handX:" + handX); w.println("handY:" + handY); w.println("handZ:" + handZ);
            w.println("wpX:" + wpX); w.println("wpY:" + wpY); w.println("wpZ:" + wpZ);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            for (String l : lines) {
                String[] p = l.split(":");
                if (p[0].equals("handX")) handX = Float.parseFloat(p[1]);
                if (p[0].equals("handY")) handY = Float.parseFloat(p[1]);
                if (p[0].equals("handZ")) handZ = Float.parseFloat(p[1]);
                if (p[0].equals("wpX")) wpX = Double.parseDouble(p[1]);
                if (p[0].equals("wpY")) wpY = Double.parseDouble(p[1]);
                if (p[0].equals("wpZ")) wpZ = Double.parseDouble(p[1]);
            }
        } catch (Exception ignored) {}
    }
}

