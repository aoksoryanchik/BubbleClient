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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
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
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static double kaRange = 3.8, kaWallsRange = 3.0, wpX = 0, wpY = 64, wpZ = 0;
    public static boolean kaAutoRun = true;
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
            if (isPressed(h, menuKey) && client.currentScreen == null) client.setScreen(new BubbleMenu());

            if (client.currentScreen == null) {
                if (isPressed(h, killauraKey)) toggle(client, "Killaura", !killaura);
                if (isPressed(h, triggerbotKey)) toggle(client, "Triggerbot", !triggerbot);
                if (isPressed(h, fbKey)) toggle(client, "FullBright", !fullbright);
                if (isPressed(h, wpKey)) toggle(client, "Waypoint", !waypointActive);
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (killaura) runKillaura(client);
            if (triggerbot && !killaura) runTriggerbot(client);
        });

        WorldRenderEvents.LAST.register(context -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            MatrixStack matrices = context.matrixStack();
            Vec3d camPos = context.camera().getPos();
            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));

            matrices.push();
            // Смещаем к координатам метки
            matrices.translate(wpX - camPos.x, (wpY - camPos.y) + 1.5, wpZ - camPos.z);
            
            // Поворот к игроку
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(context.camera().getPitch()));
            
            // НОВАЯ ЛОГИКА РАЗМЕРА: 
            // Метка всегда видна: и вблизи, и вдали.
            float scale = (float) (dist * 0.01); 
            if (scale < 0.03f) scale = 0.03f; // Не дает стать слишком мелкой вблизи
            if (scale > 0.8f) scale = 0.8f;   // Не дает стать гигантской на 10к блоков
            
            matrices.scale(-scale, -scale, scale);

            VertexConsumerProvider consumers = context.consumers();
            if (consumers != null) {
                Matrix4f posMat = matrices.peek().getPositionMatrix();
                String t1 = "§b[!] ЦЕЛЬ §f(" + (int)dist + "m)";
                String t2 = "§7" + (int)wpX + " " + (int)wpY + " " + (int)wpZ;
                
                // Рендерим текст с приоритетом над блоками
                client.textRenderer.draw(t1, -client.textRenderer.getWidth(t1)/2f, 0, -1, false, posMat, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
                client.textRenderer.draw(t2, -client.textRenderer.getWidth(t2)/2f, 10, -1, false, posMat, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            }
            matrices.pop();
        });
    }

    // --- Дальнейший код (Toggle, Config, Menu, Settings) остается без изменений, чтобы сохранить твою структуру ---

    private void toggle(MinecraftClient c, String n, boolean s) {
        if(n.equals("Killaura")) killaura = s;
        if(n.equals("Triggerbot")) triggerbot = s;
        if(n.equals("FullBright")) fullbright = s;
        if(n.equals("Waypoint")) waypointActive = s;
        saveConfig();
        c.player.sendMessage(Text.literal("§bBubble §8» §f" + n + ": " + (s ? "§aON" : "§cOFF")), true);
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println("kaRange:" + kaRange); w.println("kaWallsRange:" + kaWallsRange);
            w.println("kaAutoRun:" + kaAutoRun); w.println("wpX:" + wpX);
            w.println("wpY:" + wpY); w.println("wpZ:" + wpZ);
            w.println("kaKey:" + killauraKey); w.println("tbKey:" + triggerbotKey);
            w.println("fbKey:" + fbKey); w.println("wpKey:" + wpKey);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            for (String l : lines) {
                String[] p = l.split(":");
                if (p[0].equals("kaRange")) kaRange = Double.parseDouble(p[1]);
                if (p[0].equals("kaWallsRange")) kaWallsRange = Double.parseDouble(p[1]);
                if (p[0].equals("kaAutoRun")) kaAutoRun = Boolean.parseBoolean(p[1]);
                if (p[0].equals("wpX")) wpX = Double.parseDouble(p[1]);
                if (p[0].equals("wpY")) wpY = Double.parseDouble(p[1]);
                if (p[0].equals("wpZ")) wpZ = Double.parseDouble(p[1]);
                if (p[0].equals("kaKey")) killauraKey = Integer.parseInt(p[1]);
                if (p[0].equals("tbKey")) triggerbotKey = Integer.parseInt(p[1]);
                if (p[0].equals("fbKey")) fbKey = Integer.parseInt(p[1]);
                if (p[0].equals("wpKey")) wpKey = Integer.parseInt(p[1]);
            }
        } catch (Exception ignored) {}
    }

    private boolean isPressed(long h, int k) {
        if (k <= 0 || k >= 512) return false;
        boolean down = InputUtil.isKeyPressed(h, k);
        if (down && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!down) keyStates[k] = false;
        return false;
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

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 80, y = height/2 - 60;
            context.fill(x, y, x + 160, y + 120, 0xFF121212);
            context.drawBorder(x, y, 160, 120, 0xFF00AAFF);
            context.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y + 6, -1);
            drawBtn(context, x+10, y+25, "KillAura", killaura, killauraKey, "ka", mx, my, true);
            drawBtn(context, x+10, y+50, "TriggerBot", triggerbot, triggerbotKey, "tb", mx, my, false);
            drawBtn(context, x+10, y+75, "FullBright", fullbright, fbKey, "fb", mx, my, false);
            drawBtn(context, x+10, y+100, "Waypoint", waypointActive, wpKey, "wp", mx, my, true);
        }
        private void drawBtn(DrawContext ctx, int x, int y, String n, boolean on, int k, String id, int mx, int my, boolean s) {
            boolean h = mx >= x && mx <= x + 140 && my >= y && my <= y + 16;
            String kn = bindingFor.equals(id) ? "???" : GLFW.glfwGetKeyName(k, 0);
            if (kn == null) kn = "K_" + k;
            ctx.fill(x, y, x + 140, y + 16, h ? 0xFF252525 : 0xFF181818);
            ctx.fill(x + 2, y + 4, x + 10, y + 12, on ? 0xFF00FF00 : 0xFFFF0000);
            ctx.drawTextWithShadow(textRenderer, n + " §7[" + kn.toUpperCase() + "]", x + 15, y + 4, -1);
            if (s) {
                boolean hs = mx >= x + 125 && mx <= x + 140 && my >= y && my <= y + 16;
                ctx.drawTextWithShadow(textRenderer, hs ? "§f⚙" : "§b⚙", x + 130, y + 4, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 80, y = height/2 - 60;
            if (mx >= x + 125 && mx <= x + 150) {
                if (my >= y + 25 && my <= y + 41 && b == 1) { client.setScreen(new KillAuraSettings(this)); return true; }
                if (my >= y + 100 && my <= y + 116 && b == 1) { client.setScreen(new WaypointSettings(this)); return true; }
            }
            if (mx >= x + 10 && mx <= x + 125) {
                if (my >= y + 25 && my <= y + 41) handle(b, "ka");
                if (my >= y + 50 && my <= y + 66) handle(b, "tb");
                if (my >= y + 75 && my <= y + 91) handle(b, "fb");
                if (my >= y + 100 && my <= y + 116) handle(b, "wp");
            }
            return super.mouseClicked(mx, my, b);
        }
        private void handle(int b, String id) {
            if (b == 0) {
                if(id.equals("ka")) killaura = !killaura;
                if(id.equals("tb")) triggerbot = !triggerbot;
                if(id.equals("fb")) fullbright = !fullbright;
                if(id.equals("wp")) waypointActive = !waypointActive;
                saveConfig();
            } else if (b == 1) bindingFor = id;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (!bindingFor.isEmpty()) {
                if (bindingFor.equals("ka")) killauraKey = k;
                if (bindingFor.equals("tb")) triggerbotKey = k;
                if (bindingFor.equals("fb")) fbKey = k;
                if (bindingFor.equals("wp")) wpKey = k;
                bindingFor = ""; saveConfig(); return true;
            }
            if (k == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
            return super.keyPressed(k, s, m);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen p; private TextFieldWidget rF, wF;
        public KillAuraSettings(Screen p) { super(Text.literal("KA")); this.p = p; }
        @Override
        protected void init() {
            rF = new TextFieldWidget(textRenderer, width/2 + 10, height/2 - 32, 40, 12, Text.literal(""));
            rF.setText(String.valueOf(kaRange));
            wF = new TextFieldWidget(textRenderer, width/2 + 10, height/2 - 12, 40, 12, Text.literal(""));
            wF.setText(String.valueOf(kaWallsRange));
            addSelectableChild(rF); addSelectableChild(wF);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2 - 60, y = height/2 - 60;
            ctx.fill(x, y, x + 150, y + 110, 0xFF121212);
            ctx.drawBorder(x, y, 150, 110, 0xFF00AAFF);
            int px = x - 90;
            ctx.fill(px, y, px + 85, y + 110, 0xFF121212);
            ctx.drawBorder(px, y, 85, 110, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§eAresmine", px + 42, y + 15, -1);
            boolean hA = mx >= px+10 && mx <= px+75 && my >= y+40 && my <= y+56;
            ctx.fill(px+10, y+40, px+75, y+56, hA ? 0xFF252525 : 0xFF181818);
            ctx.drawCenteredTextWithShadow(textRenderer, "Поставить", px + 42, y + 44, -1);
            ctx.drawTextWithShadow(textRenderer, "Range:", x+10, y+25, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", x+10, y+45, -1);
            ctx.drawTextWithShadow(textRenderer, "Auto Run:", x+10, y+70, -1);
            if (kaAutoRun) ctx.drawTextWithShadow(textRenderer, "✔", x+120, y+70, 0xFF00FF00);
            rF.render(ctx, mx, my, d); wF.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 60, y = height/2 - 60;
            int px = x - 90;
            if (mx >= px+10 && mx <= px+75 && my >= y+40 && my <= y+56) {
                kaRange = 3.8; kaWallsRange = 3.0; kaAutoRun = true;
                rF.setText("3.8"); wF.setText("3.0"); saveConfig(); return true;
            }
            if (mx >= x+120 && mx <= x+135 && my >= y+70 && my <= y+85) { kaAutoRun = !kaAutoRun; saveConfig(); return true; }
            rF.setFocused(rF.isMouseOver(mx, my)); wF.setFocused(wF.isMouseOver(mx, my));
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(p); return true; }
            rF.keyPressed(k, s, m); wF.keyPressed(k, s, m);
            try { kaRange = Double.parseDouble(rF.getText()); kaWallsRange = Double.parseDouble(wF.getText()); saveConfig(); } catch (Exception ignored) {}
            return super.keyPressed(k, s, m);
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen p; private TextFieldWidget eX, eY, eZ;
        public WaypointSettings(Screen p) { super(Text.literal("WP")); this.p = p; }
        @Override
        protected void init() {
            eX = new TextFieldWidget(textRenderer, width/2 - 30, height/2 - 30, 60, 12, Text.literal(""));
            eX.setText(String.valueOf(wpX));
            eY = new TextFieldWidget(textRenderer, width/2 - 30, height/2 - 10, 60, 12, Text.literal(""));
            eY.setText(String.valueOf(wpY));
            eZ = new TextFieldWidget(textRenderer, width/2 - 30, height/2 + 10, 60, 12, Text.literal(""));
            eZ.setText(String.valueOf(wpZ));
            addSelectableChild(eX); addSelectableChild(eY); addSelectableChild(eZ);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bКоординаты метки", width/2, height/2 - 50, -1);
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
}

