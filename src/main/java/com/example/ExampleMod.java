package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderTickCounter;
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
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {
    // Состояния модулей
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, tbCrits = true;

    // Параметры
    public static double kaRange = 3.8, kaWallsRange = 3.0;
    public static double wpX = 0, wpY = 64, wpZ = 0;
    public static float shakeIntensity = 0.5f;
    
    // Клавиши
    public static int keyKA = GLFW.GLFW_KEY_UNKNOWN, keyTB = GLFW.GLFW_KEY_UNKNOWN, keyFB = GLFW.GLFW_KEY_UNKNOWN, 
                      keyAT = GLFW.GLFW_KEY_UNKNOWN, keyWP = GLFW.GLFW_KEY_UNKNOWN;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();
            
            // Открытие меню на 0
            if (isPressed(h, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }
            
            // Обработка горячих клавиш вне меню
            if (client.currentScreen == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }
            
            // Логика эффектов и функций
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && (client.player.forwardSpeed > 0 || killaura)) {
                client.player.setSprinting(true);
            }
            
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
        });

        HudRenderCallback.EVENT.register(this::renderWaypointArrow);
    }

    // --- ЛОГИКА КИЛЛАУРЫ (ОБХОД АНТИЧИТОВ) ---
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
            // Рандомизация точки взгляда для обхода проверок на "одинаковые ротации"
            double randX = (random.nextDouble() - 0.5) * 0.15;
            double randZ = (random.nextDouble() - 0.5) * 0.15;
            Vec3d targetPos = target.getPos().add(randX, target.getHeight() * (0.3 + random.nextDouble() * 0.5), randZ);
            
            faceVector(client.player, targetPos);

            // Raytrace проверка: бьем только если перекрестие реально на враге
            EntityHitResult hit = raycastEntity(client, kaRange);
            if (hit != null && hit.getEntity() == target) {
                // Динамическое КД: удар в диапазоне 92%-110% готовности для имитации человека
                float readyThreshold = 0.92f + random.nextFloat() * 0.18f;
                if (client.player.getAttackCooldownProgress(0) >= readyThreshold) {
                    client.interactionManager.attackEntity(client.player, target);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private void faceVector(PlayerEntity player, Vec3d target) {
        Vec3d diff = target.subtract(player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        
        // Мягкая установка поворота для стабильности прыжков
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(yaw - player.getYaw()));
        player.setPitch(player.getPitch() + MathHelper.wrapDegrees(pitch - player.getPitch()));
    }

    private EntityHitResult raycastEntity(MinecraftClient client, double range) {
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F).multiply(range);
        Box box = client.player.getBoundingBox().expand(look.x, look.y, look.z).expand(1.0);
        return ProjectileUtil.raycast(client.player, eye, eye.add(look), box, (e) -> e instanceof PlayerEntity && e.isAlive(), range * range);
    }

    // --- ОТОБРАЖЕНИЕ ВАЙПОИНТА ---
    private void renderWaypointArrow(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!waypointActive) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d targetVec = new Vec3d(wpX, wpY, wpZ).subtract(client.player.getPos());
        float targetYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90F;
        float yawDiff = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());

        int color = (Math.abs(yawDiff) < 15) ? 0xFF00AAFF : -1;
        
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(client.getWindow().getScaledWidth() / 2f, client.getWindow().getScaledHeight() / 2f - 30, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(yawDiff));
        ctx.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, 0, color);
        ms.pop();

        double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
        ctx.drawCenteredTextWithShadow(client.textRenderer, String.format("§f%.0fm", dist), client.getWindow().getScaledWidth() / 2, client.getWindow().getScaledHeight() / 2 - 45, -1);
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ---
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

    private void runTrigger(MinecraftClient client) {
        EntityHitResult hit = raycastEntity(client, kaRange);
        if (hit != null && hit.getEntity() instanceof PlayerEntity target) {
            if (client.player.getAttackCooldownProgress(0) >= (tbCrits ? 1.0f : 0.95f)) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void sendNotify(String module, boolean state) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[Bubble] §f" + module + ": " + (state ? "§aON" : "§cOFF")), true);
        }
    }

    private boolean isPressed(long h, int k) {
        if(k==GLFW.GLFW_KEY_UNKNOWN) return false;
        boolean d = InputUtil.isKeyPressed(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false; return false;
    }

    // --- ГРАФИЧЕСКИЙ ИНТЕРФЕЙС (GUI) ---
    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x90000000);
            int x = width/2-90, y = height/2-105;
            ctx.fill(x, y, x+180, y+155, 0xFF050505);
            ctx.drawBorder(x, y, 180, 155, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§b§lBUBBLE CLIENT", width/2, y+10, -1);
            
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, waypointActive};
            int[] keys = {keyKA, keyTB, keyFB, keyAT, keyWP};
            
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                boolean h = mx>=x+10 && mx<=x+170 && my>=iy && my<=iy+18;
                ctx.fill(x+10, iy, x+170, iy+18, h ? 0xFF1A1A1A : 0xFF101010);
                String kN = keys[i] == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(keys[i], 0).toUpperCase();
                ctx.drawTextWithShadow(textRenderer, names[i] + " §7[" + kN + "]", x+15, iy+5, states[i] ? 0xFF00FF00 : 0xFFFF3333);
                if(i==0 || i==1 || i==4) ctx.drawTextWithShadow(textRenderer, "⚙", x+155, iy+5, -1);
            }
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2-90, y = height/2-105;
            for(int i=0; i<5; i++) {
                int iy = y+35+i*22;
                if(mx>=x+150 && mx<=x+170 && my>=iy && my<=iy+18) {
                    if(i==0) client.setScreen(new KillAuraSettings(this));
                    if(i==1) client.setScreen(new TriggerSettings(this));
                    if(i==4) client.setScreen(new WaypointSettings(this));
                    return true;
                } else if(mx>=x+10 && mx<=x+150 && my>=iy && my<=iy+18) {
                    if(b == 0) {
                        if(i==0) killaura=!killaura; if(i==1) triggerbot=!triggerbot; if(i==2) fullbright=!fullbright;
                        if(i==3) autoTotem=!autoTotem; if(i==4) waypointActive=!waypointActive;
                    } else client.setScreen(new BindScreen(this, i));
                    saveConfig(); return true;
                }
            }
            return false;
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent; 
        private TextFieldWidget rangeField, wallsField;
        public KillAuraSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override
        protected void init() {
            rangeField = new TextFieldWidget(textRenderer, width/2 + 25, height/2-45, 45, 16, Text.literal(""));
            wallsField = new TextFieldWidget(textRenderer, width/2 + 25, height/2-20, 45, 16, Text.literal(""));
            rangeField.setText(String.valueOf(kaRange));
            wallsField.setText(String.valueOf(kaWallsRange));
            addDrawableChild(rangeField); addDrawableChild(wallsField);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            int x = width/2, y = height/2;
            ctx.fill(x-115, y-95, x+115, y+90, 0xFF0A0A0A);
            ctx.drawBorder(x-115, y-95, 230, 185, 0xFF00AAFF);
            ctx.drawTextWithShadow(textRenderer, "Range:", x-105, y-41, -1);
            ctx.drawTextWithShadow(textRenderer, "Walls:", x-105, y-16, -1);
            
            // Кнопка MineBlaze
            int cx = x-240;
            ctx.fill(cx, y-95, cx+120, y+90, 0xFF0A0A0A);
            ctx.drawBorder(cx, y-95, 120, 185, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "§bCONFIGS", cx+60, y-85, -1);
            boolean h = mx >= cx+10 && mx <= cx+110 && my >= y-20 && my <= y;
            ctx.fill(cx+10, y-20, cx+110, y, h ? 0xFF222222 : 0xFF111111);
            ctx.drawCenteredTextWithShadow(textRenderer, "MineBlaze", cx+60, y-15, h ? 0xFF00AAFF : -1);
            
            rangeField.render(ctx, mx, my, d);
            wallsField.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2, y = height/2;
            if (mx >= x-230 && mx <= x-130 && my >= y-20 && my <= y) {
                kaRange = 3.1; kaWallsRange = 0.0;
                rangeField.setText("3.1"); wallsField.setText("0.0");
                saveConfig(); return true;
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    kaRange = Double.parseDouble(rangeField.getText());
                    kaWallsRange = Double.parseDouble(wallsField.getText());
                } catch(Exception ignored){}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class TriggerSettings extends Screen {
        private final Screen parent;
        public TriggerSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "Only Crits: " + (tbCrits ? "§aON" : "§cOFF"), width/2, height/2, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, "Click to Toggle", width/2, height/2 + 20, 0xFFAAAAAA);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            tbCrits = !tbCrits; saveConfig(); return true;
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) client.setScreen(parent);
            return true;
        }
    }

    public static class WaypointSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget xF, yF, zF;
        public WaypointSettings(Screen p) { super(Text.literal("")); this.parent = p; }
        @Override
        protected void init() {
            xF = new TextFieldWidget(textRenderer, width/2-25, height/2-40, 50, 16, Text.literal(""));
            yF = new TextFieldWidget(textRenderer, width/2-25, height/2-15, 50, 16, Text.literal(""));
            zF = new TextFieldWidget(textRenderer, width/2-25, height/2 + 10, 50, 16, Text.literal(""));
            xF.setText(String.valueOf((int)wpX));
            yF.setText(String.valueOf((int)wpY));
            zF.setText(String.valueOf((int)wpZ));
            addDrawableChild(xF); addDrawableChild(yF); addDrawableChild(zF);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0xEE000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "X:", width/2-40, height/2-35, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, "Y:", width/2-40, height/2-10, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, "Z:", width/2-40, height/2 + 15, -1);
            xF.render(ctx, mx, my, d); yF.render(ctx, mx, my, d); zF.render(ctx, mx, my, d);
        }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) {
                try {
                    wpX = Double.parseDouble(xF.getText());
                    wpY = Double.parseDouble(yF.getText());
                    wpZ = Double.parseDouble(zF.getText());
                } catch(Exception ignored){}
                saveConfig(); client.setScreen(parent); return true;
            }
            return super.keyPressed(k, s, m);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen p, int id) { super(Text.literal("")); this.parent = p; this.id = id; }
        @Override
        public boolean keyPressed(int k, int s, int m) {
            if(k == GLFW.GLFW_KEY_ESCAPE) k = GLFW.GLFW_KEY_UNKNOWN;
            if(id==0) keyKA=k; if(id==1) keyTB=k; if(id==2) keyFB=k; if(id==3) keyAT=k; if(id==4) keyWP=k;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0,0,width,height, 0xEE000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS ANY KEY TO BIND", width/2, height/2, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, "ESC to Clear", width/2, height/2 + 20, 0xFFAAAAAA);
        }
    }

    // --- СИСТЕМА КОНФИГА ---
    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":" + wpZ + ":0:0:0:" + 
                      autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + 
                      shakeIntensity + ":" + antiVelocity + ":" + tbCrits);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            if (!lines.isEmpty()) {
                String[] p = lines.get(0).split(":");
                if(p.length >= 17) {
                    kaRange=Double.parseDouble(p[0]); kaWallsRange=Double.parseDouble(p[1]);
                    wpX=Double.parseDouble(p[2]); wpY=Double.parseDouble(p[3]); wpZ=Double.parseDouble(p[4]);
                    autoRun=Boolean.parseBoolean(p[8]);
                    keyKA=Integer.parseInt(p[9]); keyTB=Integer.parseInt(p[10]); keyFB=Integer.parseInt(p[11]);
                    keyAT=Integer.parseInt(p[12]); keyWP=Integer.parseInt(p[13]);
                    shakeIntensity=Float.parseFloat(p[14]); antiVelocity=Boolean.parseBoolean(p[15]);
                    tbCrits=Boolean.parseBoolean(p[16]);
                }
            }
        } catch (Exception ignored) {}
    }
}

