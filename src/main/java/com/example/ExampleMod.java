package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.systems.RenderSystem;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * BUBBLE ABSOLUTE - FULL VERSION 1.21.4
 * Optimized for AresMine, MineBlaze, and MixerGrief
 */
public class ExampleMod implements ModInitializer {
    // Состояния модулей (Modules)
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = true;
    public static boolean esp = true;
    public static boolean autoTotem = true;
    public static boolean autoRun = true;
    public static boolean antiVelocity = true;
    public static boolean elytraSwap = true;
    public static boolean fastPearl = true;
    public static boolean smartCrits = true;
    
    // Режимы обхода (Bypass Modes)
    public static boolean isAres = false;
    public static boolean isBlaze = false;
    public static boolean isMixer = true;

    // Настройки Киллауры
    public static double kaRange = 3.3;
    public static double kawallsRange = 0.0;
    private static PlayerEntity currentTarget = null;
    private static final Random random = new Random();
    
    // Бинды (Keybinds)
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyESP = -1;
    public static int keyFP = GLFW.GLFW_KEY_Q;
    public static int keyES = GLFW.GLFW_KEY_C;
    public static int keyMenu = GLFW.GLFW_KEY_0; // Клавиша вызова меню

    // Друзья (Friends System)
    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";

    @Override
    public void onInitialize() {
        loadConfig();
        
        // Регистрация ESP рендера
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderESP);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long windowHandle = client.getWindow().getHandle();

            // Открытие главного меню
            if (isPressed(windowHandle, keyMenu) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            handleBinds(client, windowHandle);

            // Обработка активных модулей
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }
            
            if (autoTotem) checkTotem(client);
            
            if (autoRun && client.player.forwardSpeed > 0 && !client.player.isSneaking() && !client.player.horizontalCollision) {
                client.player.setSprinting(true);
            }
            
            runSilentAura(client);
            
            if (triggerbot) runTrigger(client);
            
            if (antiVelocity && client.player.hurtTime > 0) {
                // Мягкий анти-откид для обхода проверок
                client.player.setVelocity(client.player.getVelocity().multiply(0.6, 1.0, 0.6));
            }
        });
    }

    private void runSilentAura(MinecraftClient client) {
        if (!killaura) return;
        currentTarget = null;
        double dist = Double.MAX_VALUE;

        // Поиск ближайшей цели
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || friendsList.contains(p.getName().getString().toLowerCase())) continue;
            double d = client.player.distanceTo(p);
            double currentMax = client.player.canSee(p) ? kaRange : kawallsRange;
            if (d <= currentMax && d < dist) { 
                dist = d; 
                currentTarget = p; 
            }
        }

        if (currentTarget != null) {
            float cooldown = client.player.getAttackCooldownProgress(0.0f);
            // Рандомная задержка для обхода MixerGrief/Ares
            if (cooldown >= (0.92f + random.nextFloat() * 0.06f)) {
                float[] rotations = getRotations(currentTarget);
                
                // ВАЖНО: В 1.21.4 конструктор LookAndOnGround принимает 4 аргумента
                // (float yaw, float pitch, boolean onGround, boolean horizontalCollision)
                boolean onGround = client.player.isOnGround();
                boolean hCollision = client.player.horizontalCollision;

                // Отправка пакета поворота
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], onGround, hCollision));
                
                // Пакет атаки
                client.interactionManager.attackEntity(client.player, currentTarget);
                client.player.swingHand(Hand.MAIN_HAND);
                
                // Беспалевный возврат головы (Silent)
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(client.player.getYaw(), client.player.getPitch(), onGround, hCollision));
            }
        }
    }

    private float[] getRotations(PlayerEntity target) {
        Vec3d eyePos = MinecraftClient.getInstance().player.getEyePos();
        // Добавляем небольшой джиттер (потряхивание) для обхода античитов
        double jitterX = (random.nextDouble() - 0.5) * 0.1;
        double jitterZ = (random.nextDouble() - 0.5) * 0.1;
        
        Vec3d targetVec = target.getPos().add(jitterX, target.getHeight() * 0.5, jitterZ);
        Vec3d diff = targetVec.subtract(eyePos);
        
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        
        return new float[]{yaw, pitch};
    }

    private void runTrigger(MinecraftClient c) {
        if (c.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p) {
            if (p.isAlive() && !friendsList.contains(p.getName().getString().toLowerCase()) && c.player.getAttackCooldownProgress(0) >= 0.95f) {
                c.interactionManager.attackEntity(c.player, p);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void renderESP(WorldRenderContext context) {
        if (!esp) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        MatrixStack ms = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        RenderSystem.disableDepthTest();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive()) continue;
            ms.push();
            double x = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevX, p.getX()) - camPos.x;
            double y = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevY, p.getY()) - camPos.y;
            double z = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevZ, p.getZ()) - camPos.z;
            ms.translate(x, y, z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            
            drawBox(buffer, ms.peek().getPositionMatrix(), (float)p.getWidth()/2f, (float)p.getHeight(), 0.0f, 1.0f, 1.0f, 1.0f);
            ms.pop();
        }
        consumers.draw();
        RenderSystem.enableDepthTest();
    }

    private void drawBox(VertexConsumer b, Matrix4f m, float w, float h, float r, float g, float bl, float a) {
        // Отрисовка линий бокса
        line(b, m, -w, 0f, -w, w, 0f, -w, r, g, bl, a); line(b, m, w, 0f, -w, w, 0f, w, r, g, bl, a);
        line(b, m, w, 0f, w, -w, 0f, w, r, g, bl, a); line(b, m, -w, 0f, w, -w, 0f, -w, r, g, bl, a);
        line(b, m, -w, h, -w, w, h, -w, r, g, bl, a); line(b, m, w, h, -w, w, h, w, r, g, bl, a);
        line(b, m, w, h, w, -w, h, w, r, g, bl, a); line(b, m, -w, h, w, -w, h, -w, r, g, bl, a);
        line(b, m, -w, 0f, -w, -w, h, -w, r, g, bl, a); line(b, m, w, 0f, -w, w, h, -w, r, g, bl, a);
        line(b, m, w, 0f, w, w, h, w, r, g, bl, a); line(b, m, -w, 0f, w, -w, h, w, r, g, bl, a);
    }

    private void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, a).normal(0f, 1f, 0f);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a).normal(0f, 1f, 0f);
    }

    private void handleBinds(MinecraftClient client, long win) {
        if (client.currentScreen != null) return;
        if (isPressed(win, keyKA)) { killaura = !killaura; notify(client, "KA", killaura); }
        if (isPressed(win, keyTB)) { triggerbot = !triggerbot; notify(client, "TB", triggerbot); }
        if (isPressed(win, keyESP)) { esp = !esp; notify(client, "ESP", esp); }
        if (fastPearl && isPressed(win, keyFP)) throwPearl(client);
        if (elytraSwap && isPressed(win, keyES)) swapElytra(client);
    }

    private void checkTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        for (int i = 0; i < 45; i++) {
            if (client.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                int slot = (i < 9) ? (i + 36) : i;
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
                break;
            }
        }
    }

    private void swapElytra(MinecraftClient client) {
        int targetSlot = -1;
        ItemStack chestStack = client.player.getInventory().getArmorStack(2);
        boolean hasElytra = chestStack.isOf(Items.ELYTRA);
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (hasElytra) { 
                if (s.getItem().toString().contains("chestplate")) { targetSlot = i; break; } 
            } else if (s.isOf(Items.ELYTRA)) { 
                targetSlot = i; break; 
            }
        }
        if (targetSlot != -1) {
            int invSlot = targetSlot < 9 ? targetSlot + 36 : targetSlot;
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, invSlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, invSlot, 0, SlotActionType.PICKUP, client.player);
        }
    }

    private void throwPearl(MinecraftClient client) {
        int pearlSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { pearlSlot = i; break; }
        }
        if (pearlSlot != -1) {
            int oldSlot = client.player.getInventory().selectedSlot;
            client.player.getInventory().selectedSlot = pearlSlot;
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            client.player.getInventory().selectedSlot = oldSlot;
        }
    }

    private boolean isPressed(long handle, int key) {
        if (key == -1) return false;
        boolean pressed = InputUtil.isKeyPressed(handle, key);
        if (pressed && !keyStates[key]) { 
            keyStates[key] = true; return true; 
        }
        if (!pressed) keyStates[key] = false;
        return false;
    }

    private void notify(MinecraftClient c, String module, boolean state) {
        if (c.player != null) {
            c.player.sendMessage(Text.literal("§b[Bubble] §f" + module + ": " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kawallsRange + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + friendsRaw + ":" + esp + ":" + keyESP + ":" + keyFP + ":" + keyES + ":" + antiVelocity + ":" + fullbright + ":" + smartCrits + ":" + isMixer + ":" + isAres + ":" + isBlaze);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String line = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0);
            String[] p = line.split(":", -1);
            if (p.length >= 18) {
                kaRange = Double.parseDouble(p[0]); kawallsRange = Double.parseDouble(p[1]);
                autoRun = Boolean.parseBoolean(p[2]); keyKA = Integer.parseInt(p[3]);
                keyTB = Integer.parseInt(p[4]); keyFB = Integer.parseInt(p[5]);
                keyAT = Integer.parseInt(p[6]); friendsRaw = p[7];
                esp = Boolean.parseBoolean(p[8]); keyESP = Integer.parseInt(p[9]);
                keyFP = Integer.parseInt(p[10]); keyES = Integer.parseInt(p[11]);
                antiVelocity = Boolean.parseBoolean(p[12]); fullbright = Boolean.parseBoolean(p[13]); 
                smartCrits = Boolean.parseBoolean(p[14]); 
                isMixer = Boolean.parseBoolean(p[15]); isAres = Boolean.parseBoolean(p[16]); isBlaze = Boolean.parseBoolean(p[17]);
                friendsList.clear(); 
                if(!friendsRaw.isEmpty()) friendsList.addAll(Arrays.asList(friendsRaw.split(",")));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- GUI СИСТЕМА ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble Menu")); }
        
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 105, cy - 115, cx + 105, cy + 125, 0xEE000000); // Тень
            ctx.fill(cx - 100, cy - 110, cx + 100, cy + 120, 0xDD111111); // Основной фон
            
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE ABSOLUTE v1.21.4", cx, cy - 100, 0x00FFFF);
            
            String[] modules = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "FastPearl", "ElytraSwap", "Config Settings"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, esp, fastPearl, elytraSwap, false};
            
            for (int i = 0; i < modules.length; i++) {
                int iy = cy - 80 + i * 24;
                boolean hover = mx >= cx - 90 && mx <= cx + 90 && my >= iy && my <= iy + 20;
                ctx.fill(cx - 90, iy, cx + 90, iy + 20, hover ? 0x99444444 : 0x99222222);
                ctx.drawText(textRenderer, modules[i], cx - 85, iy + 6, states[i] ? 0x00FF00 : 0xFFFFFF, true);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            for (int i = 0; i < 8; i++) {
                int iy = cy - 80 + i * 24;
                if (mx >= cx - 90 && mx <= cx + 90 && my >= iy && my <= iy + 20) {
                    if (i == 0) client.setScreen(new KillAuraSettings(this));
                    else if (i == 1) triggerbot = !triggerbot;
                    else if (i == 2) fullbright = !fullbright;
                    else if (i == 3) autoTotem = !autoTotem;
                    else if (i == 4) esp = !esp;
                    else if (i == 5) fastPearl = !fastPearl;
                    else if (i == 6) elytraSwap = !elytraSwap;
                    else if (i == 7) client.setScreen(new GlobalSettings(this));
                    saveConfig(); return true;
                }
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override public boolean shouldPause() { return false; }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rangeField;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA Settings")); this.parent = parent; }
        
        protected void init() {
            int cx = width / 2, cy = height / 2;
            rangeField = new TextFieldWidget(textRenderer, cx - 40, cy - 60, 40, 14, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            addDrawableChild(rangeField);
        }

        public void render(DrawContext ctx, int mx, int my, float delta) {
            super.render(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.fill(cx - 100, cy - 80, cx + 100, cy + 80, 0xEE111111);
            ctx.drawCenteredTextWithShadow(textRenderer, "Aura Settings", cx, cy - 75, 0xFFFFFF);
            ctx.drawText(textRenderer, "Distance:", cx - 90, cy - 57, -1, true);
            
            drawCheck(ctx, cx - 90, cy, "MixerMode", isMixer, mx, my);
            drawCheck(ctx, cx - 90, cy + 20, "AresMode", isAres, mx, my);
            drawCheck(ctx, cx - 90, cy + 40, "BlazeMode", isBlaze, mx, my);
        }

        private void drawCheck(DrawContext ctx, int x, int y, String name, boolean state, int mx, int my) {
            boolean hover = mx >= x && mx <= x + 80 && my >= y && my <= y + 10;
            ctx.drawText(textRenderer, name + ": " + (state ? "§aYES" : "§cNO"), x, y, hover ? 0xAAAAAA : 0xFFFFFF, true);
        }

        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            if (mx >= cx - 90 && mx <= cx - 10) {
                if (my >= cy && my <= cy + 10) { isMixer = !isMixer; if(isMixer){isAres=false;isBlaze=false;} }
                if (my >= cy + 20 && my <= cy + 30) { isAres = !isAres; if(isAres){isMixer=false;isBlaze=false;} }
                if (my >= cy + 40 && my <= cy + 50) { isBlaze = !isBlaze; if(isBlaze){isMixer=false;isAres=false;} }
            }
            return super.mouseClicked(mx, my, b);
        }

        public void close() {
            try { kaRange = Double.parseDouble(rangeField.getText()); } catch (Exception ignored) {}
            saveConfig(); client.setScreen(parent);
        }
    }

    public static class GlobalSettings extends Screen {
        private final Screen parent;
        public GlobalSettings(Screen parent) { super(Text.literal("Global")); this.parent = parent; }
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x88000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "More features coming soon...", width/2, height/2, -1);
        }
        public boolean keyPressed(int k, int s, int m) { if(k == GLFW.GLFW_KEY_ESCAPE) client.setScreen(parent); return true; }
    }
}
