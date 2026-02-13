package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget; // Для совместимости, если потребуется, но мы рисуем вручную
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class ExampleMod implements ModInitializer {

    // --- ГЛОБАЛЬНЫЕ НАСТРОЙКИ ---
    public static boolean killaura = false;
    public static boolean triggerbot = false;
    public static boolean fullbright = false;
    public static boolean waypointActive = false;
    public static boolean autoTotem = true;
    public static boolean autoRun = true;
    public static boolean antiVelocity = true;
    public static boolean tbCrits = true;
    
    public static double kaRange = 3.8D;
    public static double kaWallsRange = 3.0D;
    public static double wpX = 0.0D;
    public static double wpY = 64.0D;
    public static double wpZ = 0.0D;
    public static float shakeIntensity = 0.5F;
    
    // Клавиши (-1 = не назначено)
    public static int keyKA = -1;
    public static int keyTB = -1;
    public static int keyFB = -1;
    public static int keyAT = -1;
    public static int keyWP = -1;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private static final Random rnd = new Random();
    public static PlayerEntity auraTarget = null;

    @Override
    public void onInitialize() {
        loadConfig();

        // Основной цикл клиента (работает каждый тик)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            long windowHandle = client.getWindow().getHandle();

            // Открытие меню на клавишу U (код 48 -> GLFW_KEY_U)
            if (isPressed(windowHandle, GLFW.GLFW_KEY_U) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            // Обработка биндов (если не в меню)
            if (client.currentScreen == null) {
                if (isPressed(windowHandle, keyKA)) { killaura = !killaura; sendNotify(client, "KillAura", killaura); }
                if (isPressed(windowHandle, keyTB)) { triggerbot = !triggerbot; sendNotify(client, "TriggerBot", triggerbot); }
                if (isPressed(windowHandle, keyFB)) { fullbright = !fullbright; sendNotify(client, "FullBright", fullbright); }
                if (isPressed(windowHandle, keyAT)) { autoTotem = !autoTotem; sendNotify(client, "AutoTotem", autoTotem); }
                if (isPressed(windowHandle, keyWP)) { waypointActive = !waypointActive; sendNotify(client, "Waypoint", waypointActive); }
            }

            // Логика функций
            if (fullbright) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            }
            
            if (autoTotem) handleAutoTotem(client);
            
            // AutoRun: бежим, если бьем кого-то в киллауре
            if (autoRun && killaura && auraTarget != null && auraTarget.isAlive()) {
                client.player.setSprinting(true);
            }
            
            if (killaura) { 
                runAura(client); 
            } else { 
                auraTarget = null; 
            }
            
            if (triggerbot) runTrigger(client);
            
            // AntiVelocity: убираем отбрасывание
            if (antiVelocity && client.player.hurtTime > 0) {
                client.player.setVelocity(0.0D, client.player.getVelocity().y, 0.0D);
            }
        });

        // Отрисовка на экране (HUD)
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!waypointActive) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            
            double dist = client.player.getPos().distanceTo(new Vec3d(wpX, wpY, wpZ));
            float yawToTarget = (float) Math.toDegrees(Math.atan2(wpZ - client.player.getZ(), wpX - client.player.getX())) - 90.0F;
            float angleDiff = MathHelper.wrapDegrees(yawToTarget - client.player.getYaw());
            
            String arrow = (Math.abs(angleDiff) < 10.0F) ? "↑" : ((angleDiff > 0.0F) ? "→" : "←");
            String text = String.format("WP: %.0f %.0f %.0f | %s | Dist: %.1f", wpX, wpY, wpZ, arrow, dist);
            
            drawContext.drawText(client.textRenderer, text, drawContext.getScaledWindowWidth() / 2, 10, -1, true);
        });
    }

    // --- ЛОГИКА ФУНКЦИЙ ---

    private void handleAutoTotem(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        
        // Если в левой руке нет тотема
        if (client.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            // Ищем тотем в инвентаре
            for (int i = 0; i < 45; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    // Перекладываем в левую руку (слот 45)
                    // i < 9 - это хотбар, иначе основной инвентарь
                    int slot = (i < 9) ? (i + 36) : i;
                    client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, slot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, 45, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
                    break;
                }
            }
        }
    }

    private void runAura(MinecraftClient client) {
        auraTarget = null;
        double bestDist = Double.MAX_VALUE;
        
        if (client.world == null || client.player == null) return;

        for (PlayerEntity p : client.world.getPlayers()) {
            // Убрана проверка !p.isInvisible() -> теперь бьет невидимок
            if (p == client.player || !p.isAlive() || p.isSpectator()) continue;
            
            double d = client.player.distanceTo(p);
            
            // Проверка дистанции и стен
            if (d > kaRange || d >= bestDist) continue;
            if (!client.player.canSee(p) && d > kaWallsRange) continue;
            
            bestDist = d;
            auraTarget = p;
        }
        
        if (auraTarget != null) {
            // Легкий джиттер (тряска) прицела
            double offset = (rnd.nextDouble() - 0.5D) * 0.1D;
            Vec3d tPos = auraTarget.getPos().add(offset, auraTarget.getHeight() * (0.4D + rnd.nextDouble() * 0.3D), offset);
            
            lookAt(client.player, tPos);
            
            // Удар при полной зарядке атаки
            if (client.player.getAttackCooldownProgress(0.0F) >= 1.0F) {
                client.interactionManager.attackEntity(client.player, auraTarget);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runTrigger(MinecraftClient client) {
        if (client.player == null) return;
        
        double reach = kaRange;
        HitResult hit = client.crosshairTarget;
        
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (entity instanceof PlayerEntity && entity.isAlive()) {
                // Если включены криты - ждем полной зарядки (1.0), иначе чуть меньше (0.92)
                if (client.player.getAttackCooldownProgress(0.0F) >= (tbCrits ? 1.0F : 0.92F)) {
                    client.interactionManager.attackEntity(client.player, entity);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private void lookAt(PlayerEntity player, Vec3d target) {
        Vec3d diff = target.subtract(player.getEyePos());
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.y, dist));
        
        // Плавный поворот
        player.setYaw(player.getYaw() + MathHelper.wrapDegrees(tYaw - player.getYaw()));
        player.setPitch(player.getPitch() + MathHelper.wrapDegrees(tPitch - player.getPitch()));
    }

    private void sendNotify(MinecraftClient client, String module, boolean state) {
        if (client.player != null) {
            String color = state ? "§aON" : "§cOFF";
            client.player.sendMessage(Text.of("§b[Bubble] §f" + module + ": " + color), true);
        }
    }

    // Проверка нажатия клавиши (защита от зажатия)
    private boolean isPressed(long handle, int key) {
        if (key == -1) return false;
        boolean pressed = InputUtil.isKeyPressed(handle, key);
        if (pressed && !keyStates[key]) {
            keyStates[key] = true;
            return true;
        }
        if (!pressed) keyStates[key] = false;
        return false;
    }

    // --- КОНФИГ (Сохранение/Загрузка) ---

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange + ":" + kaRange + ":" + kaWallsRange + ":" + wpX + ":" + wpY + ":0:0:0:" + wpZ + ":" + autoRun + ":" + keyKA + ":" + keyTB + ":" + keyFB + ":" + keyAT + ":" + keyWP + ":" + shakeIntensity + ":" + antiVelocity + ":" + tbCrits);
        } catch (Exception ignored) {}
    }

    public void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE));
            if (lines.isEmpty()) return;
            String[] p = lines.get(0).split(":");
            if (p.length >= 17) {
                kaRange = Double.parseDouble(p[0]);
                kaWallsRange = Double.parseDouble(p[2]);
                wpX = Double.parseDouble(p[3]);
                wpY = Double.parseDouble(p[4]);
                wpZ = Double.parseDouble(p[8]);
                autoRun = Boolean.parseBoolean(p[9]);
                keyKA = Integer.parseInt(p[10]);
                keyTB = Integer.parseInt(p[11]);
                keyFB = Integer.parseInt(p[12]);
                keyAT = Integer.parseInt(p[13]);
                keyWP = Integer.parseInt(p[14]);
                shakeIntensity = Float.parseFloat(p[15]);
                antiVelocity = Boolean.parseBoolean(p[16]);
                if (p.length > 17) tbCrits = Boolean.parseBoolean(p[17]);
            }
        } catch (Exception ignored) {}
    }

    // --- GUI (МЕНЮ) ---

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.of("Bubble")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            int centerX = width / 2;
            int centerY = height / 2;
            
            // Фон меню
            context.fill(centerX - 90, centerY - 105, centerX + 90, centerY + 155, -16448251);
            context.drawBorder(centerX - 90, centerY - 105, 180, 260, -16733441);
            
            context.drawCenteredTextWithShadow(textRenderer, "BUBBLE CLIENT", centerX, centerY - 95, -1);
            
            String[] names = { "KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint" };
            boolean[] states = { killaura, triggerbot, fullbright, autoTotem, waypointActive };
            
            for (int i = 0; i < 5; i++) {
                int iy = centerY - 70 + i * 25;
                boolean hovered = (mouseX >= centerX - 80 && mouseX <= centerX + 80 && mouseY >= iy && mouseY <= iy + 20);
                
                // Кнопка
                context.fill(centerX - 80, iy, centerX + 80, iy + 20, hovered ? -15066598 : -15724528);
                
                // Текст модуля
                context.drawText(textRenderer, names[i], centerX - 75, iy + 6, states[i] ? 0xFF00FF00 : 0xFFFFFFFF, true);
                
                // Стрелочка настроек
                context.drawText(textRenderer, ">>", centerX + 65, iy + 6, -1, true);
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int centerX = width / 2;
            int centerY = height / 2;
            
            for (int i = 0; i < 5; i++) {
                int iy = centerY - 70 + i * 25;
                
                // Если кликнули по кнопке
                if (mouseX >= centerX - 80 && mouseX <= centerX + 80 && mouseY >= iy && mouseY <= iy + 20) {
                    
                    // Правый клик или клик по стрелочкам (зона справа) -> настройки
                    if (button == 1 || mouseX >= centerX + 60) {
                        if (i == 0) client.setScreen(new KillAuraSettings(this));
                        if (i == 4) client.setScreen(new WaypointSettings(this));
                        // Для остальных - биндер
                        if (i != 0 && i != 4) client.setScreen(new BindScreen(this, i));
                        return true;
                    }
                    
                    // Левый клик -> переключение
                    if (button == 0) {
                        if (i == 0) killaura = !killaura;
                        if (i == 1) triggerbot = !triggerbot;
                        if (i == 2) fullbright = !fullbright;
                        if (i == 3) autoTotem = !autoTotem;
                        if (i == 4) waypointActive = !waypointActive;
                        saveConfig();
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    // --- НАСТРОЙКИ КИЛЛАУРЫ ---
    public static class KillAuraSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget rangeField;
        private TextFieldWidget wallRangeField;

        public KillAuraSettings(Screen parent) {
            super(Text.of("KA Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int x = width / 2;
            int y = height / 2;
            
            rangeField = new TextFieldWidget(textRenderer, x + 25, y - 70, 45, 14, Text.of(""));
            rangeField.setText(String.valueOf(kaRange));
            
            wallRangeField = new TextFieldWidget(textRenderer, x + 25, y - 50, 45, 14, Text.of(""));
            wallRangeField.setText(String.valueOf(kaWallsRange));
            
            addDrawableChild(rangeField);
            addDrawableChild(wallRangeField);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            int x = width / 2;
            int y = height / 2;
            
            // Фон
            context.fill(x - 110, y - 95, x + 110, y + 95, -16448251);
            context.drawBorder(x - 110, y - 95, 220, 190, -16733441);
            
            context.drawCenteredTextWithShadow(textRenderer, "KA SETTINGS", x, y - 88, -1);
            
            context.drawText(textRenderer, "Range:", x - 100, y - 67, -1, true);
            context.drawText(textRenderer, "Walls:", x - 100, y - 47, -1, true);
            
            // Кнопки булевых значений
            drawBtn(context, x - 100, y - 25, 200, 14, "AutoRun: " + autoRun, mouseX, mouseY);
            drawBtn(context, x - 100, y - 5, 200, 14, "AntiVelocity: " + antiVelocity, mouseX, mouseY);
            
            context.drawCenteredTextWithShadow(textRenderer, "PRESETS (CFG)", x, y + 20, -1);
            drawBtn(context, x - 100, y + 35, 200, 14, "MineBlaze (3.1/0.0)", mouseX, mouseY);
            drawBtn(context, x - 100, y + 55, 200, 14, "AresMine (3.8/3.0)", mouseX, mouseY);
            
            super.render(context, mouseX, mouseY, delta);
        }

        private void drawBtn(DrawContext context, int x, int y, int w, int h, String text, int mx, int my) {
            boolean hv = (mx >= x && mx <= x + w && my >= y && my <= y + h);
            context.fill(x, y, x + w, y + h, hv ? -14540254 : -15658735);
            context.drawCenteredTextWithShadow(textRenderer, text, x + w / 2, y + 3, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = width / 2;
            int y = height / 2;
            
            if (mouseX >= x - 100 && mouseX <= x + 100) {
                if (mouseY >= y - 25 && mouseY <= y - 11) autoRun = !autoRun;
                if (mouseY >= y - 5 && mouseY <= y + 9) antiVelocity = !antiVelocity;
                
                // Пресеты
                if (mouseY >= y + 35 && mouseY <= y + 49) {
                    kaRange = 3.1D; kaWallsRange = 0.0D;
                    rangeField.setText("3.1"); wallRangeField.setText("0.0");
                }
                if (mouseY >= y + 55 && mouseY <= y + 69) {
                    kaRange = 3.8D; kaWallsRange = 3.0D;
                    rangeField.setText("3.8"); wallRangeField.setText("3.0");
                }
                saveConfig();
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void close() {
            try {
                kaRange = Double.parseDouble(rangeField.getText());
                kaWallsRange = Double.parseDouble(wallRangeField.getText());
            } catch (Exception e) { /* игнор ошибок парсинга */ }
            saveConfig();
            client.setScreen(parent);
        }
    }

    // --- НАСТРОЙКИ ВЕЙПОИНТОВ ---
    public static class WaypointSettings extends Screen {
        private final Screen parent;
        private TextFieldWidget fX, fY, fZ;

        public WaypointSettings(Screen parent) {
            super(Text.of("WP Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int x = width / 2 + 20;
            int y = height / 2;
            fX = new TextFieldWidget(textRenderer, x, y - 45, 50, 16, Text.of("")); fX.setText(String.valueOf((int)wpX));
            fY = new TextFieldWidget(textRenderer, x, y - 20, 50, 16, Text.of("")); fY.setText(String.valueOf((int)wpY));
            fZ = new TextFieldWidget(textRenderer, x, y + 5, 50, 16, Text.of("")); fZ.setText(String.valueOf((int)wpZ));
            addDrawableChild(fX); addDrawableChild(fY); addDrawableChild(fZ);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            int x = width / 2;
            int y = height / 2;
            
            context.fill(x - 115, y - 90, x + 115, y + 90, -16448251);
            context.drawBorder(x - 115, y - 90, 230, 180, -16733441);
            
            context.drawCenteredTextWithShadow(textRenderer, "COORDINATES", x, y - 80, -1);
            context.drawText(textRenderer, "X:", x - 100, y - 41, -1, true);
            context.drawText(textRenderer, "Y:", x - 100, y - 16, -1, true);
            context.drawText(textRenderer, "Z:", x - 100, y + 9, -1, true);
            
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public void close() {
            try {
                wpX = Double.parseDouble(fX.getText());
                wpY = Double.parseDouble(fY.getText());
                wpZ = Double.parseDouble(fZ.getText());
            } catch (Exception ignored) {}
            saveConfig();
            client.setScreen(parent);
        }
    }

    // --- ЭКРАН БИНДА КЛАВИШ ---
    public static class BindScreen extends Screen {
        private final Screen parent;
        private final int moduleId;

        public BindScreen(Screen parent, int moduleId) {
            super(Text.of("Bind"));
            this.parent = parent;
            this.moduleId = moduleId;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            context.drawCenteredTextWithShadow(textRenderer, "Press ANY Key (ESC to clear)", width / 2, height / 2, -1);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setKey(-1);
            } else {
                setKey(keyCode);
            }
            saveConfig();
            client.setScreen(parent);
            return true;
        }

        private void setKey(int k) {
            if (moduleId == 0) keyKA = k;
            else if (moduleId == 1) keyTB = k;
            else if (moduleId == 2) keyFB = k;
            else if (moduleId == 3) keyAT = k;
            else if (moduleId == 4) keyWP = k;
        }
    }
}

