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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
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

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, esp = false;
    public static boolean autoTotem = true, autoRun = true, antiVelocity = true, elytraSwap = true, fastPearl = true;

    public static double kaRange = 3.8, kawallsRange = 3.0;
    public static int kaMode = 0; // 0: Ares, 1: Blaze, 2: MixerGrief
    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();

    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyESP = -1;
    public static int keyFP = GLFW.GLFW_KEY_V, keyES = GLFW.GLFW_KEY_C;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderESP);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long win = client.getWindow().getHandle();

            if (isPressed(win, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new BubbleMenu());
            }

            if (client.currentScreen == null) {
                if (fastPearl && isPressed(win, keyFP)) throwPearl(client);
                if (elytraSwap && isPressed(win, keyES)) swapElytra(client);
                if (isPressed(win, keyKA)) { killaura = !killaura; notify(client, "KillAura", killaura); }
                if (isPressed(win, keyTB)) { triggerbot = !triggerbot; notify(client, "TriggerBot", triggerbot); }
                if (isPressed(win, keyFB)) { fullbright = !fullbright; notify(client, "FullBright", fullbright); }
                if (isPressed(win, keyAT)) { autoTotem = !autoTotem; notify(client, "AutoTotem", autoTotem); }
                if (isPressed(win, keyESP)) { esp = !esp; notify(client, "ESP", esp); }
            }

            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);

            // Обновленный AntiVelocity (Легитный)
            if (antiVelocity && client.player.hurtTime > 0 && !client.player.isDead()) {
                double reduction = 0.4; 
                Vec3d velocity = client.player.getVelocity();
                client.player.setVelocity(velocity.x * reduction, velocity.y, velocity.z * reduction);
            }
        });
    }

    private void runAura(MinecraftClient c) {
        PlayerEntity target = null;
        double dist = Double.MAX_VALUE;

        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || p.isSpectator() || p.isInvisible() || p.getAbilities().invulnerable) continue;
            if (friendsList.contains(p.getName().getString().toLowerCase())) continue;
            double d = c.player.distanceTo(p);
            if (d <= (kaMode == 2 ? 3.1 : kaRange)) {
                if (c.player.canSee(p) || d <= kawallsRange) {
                    if (d < dist) { dist = d; target = p; }
                }
            }
        }

        if (target != null) {
            if (autoRun) c.player.setSprinting(true);

            // Наводка
            Vec3d tPos = target.getPos().add(0, target.getHeight() * 0.7, 0);
            Vec3d diff = tPos.subtract(c.player.getEyePos());
            float yawTo = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
            float pitchTo = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));

            if (kaMode == 2) { // MixerGrief: Плавная человеческая наводка
                c.player.setYaw(lerpAngle(c.player.getYaw(), yawTo, 0.45f));
                c.player.setPitch(lerpAngle(c.player.getPitch(), pitchTo, 0.45f));
            } else {
                c.player.setYaw(lerpAngle(c.player.getYaw(), yawTo, 0.8f));
                c.player.setPitch(lerpAngle(c.player.getPitch(), pitchTo, 0.8f));
            }

            // УМНАЯ КИЛЛАУРА (Smart Crits)
            boolean falling = c.player.fallDistance > 0 || (!c.player.isOnGround() && c.player.getVelocity().y < 0);
            boolean rising = !c.player.isOnGround() && c.player.getVelocity().y > 0.05;

            // Если в режиме Mixer и мы взлетаем — ждем падения для крита
            if (kaMode == 2 && rising && !c.player.isClimbing() && !c.player.isTouchingWater()) {
                return; 
            }

            // Проверка наведения (RayTrace) для Mixer
            boolean rayOK = true;
            if (kaMode == 2) {
                rayOK = (c.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == target);
            }

            if (rayOK && c.player.getAttackCooldownProgress(0) >= (0.92f + random.nextFloat() * 0.06f)) {
                c.interactionManager.attackEntity(c.player, target);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private float lerpAngle(float start, float end, float speed) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + diff * speed;
    }

    private void throwPearl(MinecraftClient c) {
        int pSlot = -1;
        // Поиск в хотбаре
        for (int i = 0; i < 9; i++) {
            if (c.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) { pSlot = i; break; }
        }
        // Если нет в хотбаре, ищем в инвентаре (умный свап)
        if (pSlot == -1) {
            for (int i = 9; i < 36; i++) {
                if (c.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) {
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, i, c.player.getInventory().selectedSlot, SlotActionType.SWAP, c.player);
                    c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, i, c.player.getInventory().selectedSlot, SlotActionType.SWAP, c.player);
                    return;
                }
            }
        } else {
            int old = c.player.getInventory().selectedSlot;
            c.player.getInventory().selectedSlot = pSlot;
            c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
            c.player.getInventory().selectedSlot = old;
        }
    }

    private void swapElytra(MinecraftClient c) {
        boolean hasElytra = c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = c.player.getInventory().getStack(i);
            if (hasElytra ? (s.isOf(Items.NETHERITE_CHESTPLATE) || s.isOf(Items.DIAMOND_CHESTPLATE)) : s.isOf(Items.ELYTRA)) {
                slot = i; break;
            }
        }
        if (slot != -1) {
            int invSlot = slot < 9 ? slot + 36 : slot;
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, invSlot, 0, SlotActionType.PICKUP, c.player);
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, c.player);
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, invSlot, 0, SlotActionType.PICKUP, c.player);
        }
    }

    private void runTrigger(MinecraftClient c) {
        if (c.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p) {
            if (p.isAlive() && !friendsList.contains(p.getName().getString().toLowerCase()) && c.player.getAttackCooldownProgress(0) >= 1.0f) {
                c.interactionManager.attackEntity(c.player, p);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void checkTotem(MinecraftClient c) {
        if (!c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 45; i++) {
                if (c.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int s = (i < 9) ? i + 36 : i;
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, s, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, s, 0, SlotActionType.PICKUP, c.player);
                    break;
                }
            }
        }
    }

    private void renderESP(WorldRenderContext context) {
        if (!esp) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null) return;
        MatrixStack ms = context.matrixStack();
        Vec3d camPos = context.camera().getPos();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        VertexConsumerProvider.Immediate consumers = c.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || p.isInvisible()) continue;
            ms.push();
            double x = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevX, p.getX()) - camPos.x;
            double y = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevY, p.getY()) - camPos.y;
            double z = MathHelper.lerp(context.tickCounter().getTickDelta(true), p.prevZ, p.getZ()) - camPos.z;
            ms.translate(x, y, z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-context.camera().getYaw()));
            
            float w = p.getWidth() / 2 + 0.05f;
            float h = p.getHeight() + 0.05f;
            drawBox(buffer, ms.peek().getPositionMatrix(), w, h, 1.0f, 0.0f, 0.6f, 1.0f);
            ms.pop();
        }
        consumers.draw(RenderLayer.getLines());
        RenderSystem.enableDepthTest();
    }

    private void drawBox(VertexConsumer b, Matrix4f m, float w, float h, float r, float g, float bl, float a) {
        line(b, m, -w, 0, 0, w, 0, 0, r, g, bl, a);
        line(b, m, -w, h, 0, w, h, 0, r, g, bl, a);
        line(b, m, -w, 0, 0, -w, h, 0, r, g, bl, a);
        line(b, m, w, 0, 0, w, h, 0, r, g, bl, a);
    }

    private void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, a).normal(0, 1, 0);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a).normal(0, 1, 0);
    }

    private void notify(MinecraftClient c, String n, boolean s) {
        c.player.sendMessage(Text.literal("§b[Bubble] §f" + n + ": " + (s ? "§aВКЛ" : "§cВЫКЛ")), true);
    }

    private boolean isPressed(long h, int k) {
        if (k == -1) return false;
        boolean p = InputUtil.isKeyPressed(h, k);
        if (p && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!p) keyStates[k] = false;
        return false;
    }

    public static void updateFriends(String r) {
        friendsRaw = r; friendsList.clear();
        if (r != null && !r.isEmpty()) Arrays.stream(r.split(",")).map(String::trim).map(String::toLowerCase).forEach(friendsList::add);
    }

    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange+":"+kawallsRange+":"+autoRun+":"+keyKA+":"+keyTB+":"+keyAT+":"+friendsRaw+":"+esp+":"+keyESP+":"+keyFP+":"+keyES+":"+antiVelocity+":"+kaMode);
        } catch (Exception ignored) {}
    }

    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":", -1);
            if (p.length >= 12) {
                kaRange=Double.parseDouble(p[0]); kawallsRange=Double.parseDouble(p[1]); autoRun=Boolean.parseBoolean(p[2]);
                keyKA=Integer.parseInt(p[3]); keyTB=Integer.parseInt(p[4]); keyAT=Integer.parseInt(p[5]); updateFriends(p[6]);
                esp=Boolean.parseBoolean(p[7]); keyESP=Integer.parseInt(p[8]); keyFP=Integer.parseInt(p[9]); keyES=Integer.parseInt(p[10]);
                antiVelocity=Boolean.parseBoolean(p[11]);
                if (p.length > 12) kaMode = Integer.parseInt(p[12]);
            }
        } catch (Exception ignored) {}
    }

    public static class BubbleMenu extends Screen {
        public BubbleMenu() { super(Text.literal("Bubble")); }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            int cx = width/2, cy = height/2;
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE 1.21.4", cx, cy-95, 0x00CCFF);
            String[] names = {"KillAura", "TriggerBot", "FullBright", "AutoTotem", "ESP", "FastPearl", "ElytraSwap"};
            boolean[] states = {killaura, triggerbot, fullbright, autoTotem, esp, fastPearl, elytraSwap};
            for (int i=0; i<names.length; i++) {
                int iy = cy-70 + i*24;
                boolean h = mx>=cx-85 && mx<=cx+85 && my>=iy && my<=iy+20;
                ctx.fill(cx-85, iy, cx+85, iy+20, h ? 0xEE404040 : 0xEE202020);
                ctx.drawText(textRenderer, names[i], cx-80, iy+6, states[i] ? 0x00FF00 : 0xFFFFFF, true);
            }
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int cx=width/2, cy=height/2;
            for (int i=0; i<7; i++) {
                int iy = cy-70 + i*24;
                if (mx>=cx-85 && mx<=cx+85 && my>=iy && my<=iy+20) {
                    if (b==1) { client.setScreen(i==0 ? new KillAuraSettings(this) : new BindScreen(this, i)); return true; }
                    if (b==0) {
                        if(i==0) killaura=!killaura; else if(i==1) triggerbot=!triggerbot; else if(i==2) fullbright=!fullbright;
                        else if(i==3) autoTotem=!autoTotem; else if(i==4) esp=!esp; else if(i==5) fastPearl=!fastPearl;
                        else if(i==6) elytraSwap=!elytraSwap;
                        saveConfig(); return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent; private TextFieldWidget rF, wF, fF;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA")); this.parent=parent; }
        @Override protected void init() {
            int cx = width/2, cy = height/2;
            rF = new TextFieldWidget(textRenderer, cx-40, cy-75, 40, 14, Text.literal("")); rF.setText(""+kaRange);
            wF = new TextFieldWidget(textRenderer, cx-40, cy-55, 40, 14, Text.literal("")); wF.setText(""+kawallsRange);
            fF = new TextFieldWidget(textRenderer, cx+15, cy-75, 100, 14, Text.literal("Friends")); fF.setText(friendsRaw);
            addDrawableChild(rF); addDrawableChild(wF); addDrawableChild(fF);
        }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            int cx = width/2, cy = height/2;
            ctx.fill(cx-120, cy-90, cx+130, cy+65, 0xDD101010);
            ctx.drawText(textRenderer, "Range:", cx-115, cy-72, -1, true);
            ctx.drawText(textRenderer, "Walls:", cx-115, cy-52, -1, true);
            drawBtn(ctx, cx-115, cy-20, "AresMine", mx, my);
            drawBtn(ctx, cx-30, cy-20, "MainBlaze", mx, my);
            drawBtn(ctx, cx+55, cy-20, "MixerGrief", mx, my);
            drawCheck(ctx, cx-95, cy+10, "AutoRun", autoRun, mx, my);
            drawCheck(ctx, cx+10, cy+10, "AntiVelocity", antiVelocity, mx, my);
        }
        private void drawBtn(DrawContext ctx, int x, int y, String n, int mx, int my) {
            boolean h = mx>=x && mx<=x+75 && my>=y && my<=y+15;
            ctx.fill(x, y, x+75, y+15, h ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x+37, y+4, -1);
        }
        private void drawCheck(DrawContext ctx, int x, int y, String n, boolean s, int mx, int my) {
            boolean h = mx>=x && mx<=x+85 && my>=y && my<=y+15;
            ctx.fill(x, y, x+85, y+15, h ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x+42, y+4, s ? 0x00FF00 : 0xFFFFFF);
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            int cx = width/2, cy = height/2;
            if (mx>=cx-115 && mx<=cx-40 && my>=cy-20 && my<=cy-5) { rF.setText("3.8"); wF.setText("3.1"); kaMode=0; return true; }
            if (mx>=cx-30 && mx<=cx+45 && my>=cy-20 && my<=cy-5) { rF.setText("4.0"); wF.setText("3.3"); kaMode=1; return true; }
            if (mx>=cx+55 && mx<=cx+130 && my>=cy-20 && my<=cy-5) { rF.setText("3.1"); wF.setText("0.0"); kaMode=2; return true; }
            if (mx>=cx-95 && mx<=cx-10 && my>=cy+10 && my<=cy+25) { autoRun=!autoRun; return true; }
            if (mx>=cx+10 && mx<=cx+95 && my>=cy+10 && my<=cy+25) { antiVelocity=!antiVelocity; return true; }
            return super.mouseClicked(mx, my, b);
        }
        @Override public void close() {
            try { kaRange=Double.parseDouble(rF.getText()); kawallsRange=Double.parseDouble(wF.getText()); } catch(Exception e){}
            updateFriends(fF.getText()); saveConfig(); client.setScreen(parent);
        }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen parent, int id) { super(Text.literal("Bind")); this.parent=parent; this.id=id; }
        @Override public boolean keyPressed(int k, int s, int n) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if (id==0) keyKA=v; else if(id==1) keyTB=v; else if(id==2) keyFB=v; else if(id==3) keyAT=v;
            else if(id==4) keyESP=v; else if(id==5) keyFP=v; else if(id==6) keyES=v;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            ctx.drawCenteredTextWithShadow(textRenderer, "НАЖМИ КЛАВИШУ", width/2, height/2, 0x00CCFF);
        }
    }
}

