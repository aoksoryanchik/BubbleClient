package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    // --- MODULES ---
    public static boolean killaura = false, triggerbot = false, autoTotem = true, antiVelocity = true; // Combat
    public static boolean esp = false, fullbright = false; // Visuals
    public static boolean elytraSwap = true, fastPearl = true, autoFarm = false; // Binds/Utility
    public static boolean autoRun = true;

    // --- SETTINGS ---
    public static double kaRange = 3.8, kawallsRange = 3.0;
    public static String friendsRaw = "";
    public static List<String> friendsList = new ArrayList<>();
    
    // AutoFarm Settings
    public static String farmBlocksRaw = "";
    public static List<String> farmBlockList = new ArrayList<>();
    private static BlockPos currentMineTarget = null;

    // --- KEYS ---
    public static int keyKA = -1, keyTB = -1, keyAT = -1;
    public static int keyESP = -1, keyFB = -1;
    public static int keyES = GLFW.GLFW_KEY_C, keyFP = GLFW.GLFW_KEY_V, keyAF = -1;

    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_v4_config.txt";
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        loadConfig();

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderESP);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long win = client.getWindow().getHandle();

            // Menu on "0"
            if (isPressed(win, GLFW.GLFW_KEY_0) && client.currentScreen == null) {
                client.setScreen(new MainMenu());
            }

            // Keybinds check
            if (client.currentScreen == null) {
                if (isPressed(win, keyKA)) { killaura = !killaura; notify(client, "KillAura", killaura); }
                if (isPressed(win, keyTB)) { triggerbot = !triggerbot; notify(client, "TriggerBot", triggerbot); }
                if (isPressed(win, keyAT)) { autoTotem = !autoTotem; notify(client, "AutoTotem", autoTotem); }
                if (isPressed(win, keyESP)) { esp = !esp; notify(client, "ESP", esp); }
                if (isPressed(win, keyFB)) { fullbright = !fullbright; notify(client, "FullBright", fullbright); }
                if (isPressed(win, keyAF)) { autoFarm = !autoFarm; notify(client, "AutoFarm", autoFarm); currentMineTarget = null; }
                
                if (fastPearl && isPressed(win, keyFP)) throwPearl(client);
                if (elytraSwap && isPressed(win, keyES)) swapElytra(client);
            }

            // Active Modules
            if (fullbright) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
            if (autoTotem) checkTotem(client);
            if (killaura) runAura(client);
            if (triggerbot) runTrigger(client);
            if (autoFarm) runAutoFarm(client);

            // AntiVelocity (MineBlaze Legit Fix)
            if (antiVelocity && client.player.hurtTime == 9 && !client.player.isDead()) {
                 Vec3d v = client.player.getVelocity();
                 client.player.setVelocity(v.x * 0.6, v.y, v.z * 0.6); // 60% velocity retention
            }
        });
    }

    // --- LOGIC ---

    private void runAutoFarm(MinecraftClient c) {
        if (currentMineTarget != null) {
            BlockState state = c.world.getBlockState(currentMineTarget);
            String name = state.getBlock().getName().getString();
            double dist = c.player.squaredDistanceTo(currentMineTarget.toCenterPos());
            
            boolean valid = false;
            for (String s : farmBlockList) {
                if (!s.isEmpty() && name.toLowerCase().contains(s)) { valid = true; break; }
            }
            
            if (c.world.isAir(currentMineTarget) || !valid || dist > 49) {
                currentMineTarget = null;
                c.options.forwardKey.setPressed(false);
                c.options.attackKey.setPressed(false);
            }
        }

        if (currentMineTarget == null) {
            BlockPos bestPos = null;
            double bestDist = Double.MAX_VALUE;
            int radius = 5; 

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = c.player.getBlockPos().add(x, y, z);
                        BlockState state = c.world.getBlockState(pos);
                        if (c.world.isAir(pos)) continue;
                        
                        String name = state.getBlock().getName().getString().toLowerCase();
                        for (String targetName : farmBlockList) {
                            if (!targetName.isEmpty() && name.contains(targetName)) {
                                double d = c.player.squaredDistanceTo(pos.toCenterPos());
                                if (d < bestDist) { bestDist = d; bestPos = pos; }
                            }
                        }
                    }
                }
            }
            currentMineTarget = bestPos;
        }

        if (currentMineTarget != null) {
            Vec3d targetCenter = currentMineTarget.toCenterPos();
            double dx = targetCenter.x - c.player.getX();
            double dy = targetCenter.y - c.player.getEyeY();
            double dz = targetCenter.z - c.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            float yaw = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90.0F;
            float pitch = (float) -(Math.atan2(dy, dist) * 57.29577951308232);
            c.player.setYaw(yaw); c.player.setPitch(pitch);

            if (Math.sqrt(c.player.squaredDistanceTo(targetCenter)) > 3.5) {
                c.options.forwardKey.setPressed(true);
                c.options.attackKey.setPressed(false);
            } else {
                c.options.forwardKey.setPressed(false);
                c.interactionManager.updateBlockBreakingProgress(currentMineTarget, Direction.UP);
                c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runAura(MinecraftClient c) {
        PlayerEntity target = null; double dist = Double.MAX_VALUE;
        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player || !p.isAlive() || p.isSpectator() || p.getAbilities().invulnerable) continue;
            if (friendsList.contains(p.getName().getString().toLowerCase())) continue;
            double d = c.player.distanceTo(p);
            if (d <= kaRange) {
                if (c.player.canSee(p) || d <= kawallsRange) {
                    if (d < dist) { dist = d; target = p; }
                }
            }
        }

        if (target != null) {
            if (autoRun) c.player.setSprinting(true);
            Vec3d diff = target.getPos().add(0, target.getHeight() * 0.7, 0).subtract(c.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
            float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
            
            c.player.setYaw(lerpAngle(c.player.getYaw(), yaw, 1.0f)); 
            c.player.setPitch(lerpAngle(c.player.getPitch(), pitch, 1.0f));

            boolean rising = !c.player.isOnGround() && c.player.getVelocity().y > 0.05;
            // MineBlaze Safe Delay: 0.93 + random
            if (c.player.getAttackCooldownProgress(0) >= (0.93f + random.nextFloat() * 0.05f)) {
                 if (rising && dist < 3.0) return; 
                 c.interactionManager.attackEntity(c.player, target);
                 c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private float lerpAngle(float start, float end, float speed) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + diff * speed;
    }

    private void renderESP(WorldRenderContext context) {
        if (!esp) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        MatrixStack ms = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
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
            drawBox(buffer, ms.peek().getPositionMatrix(), p.getWidth() / 2 + 0.05f, p.getHeight() + 0.05f, 1f, 0f, 0.6f, 1f);
            ms.pop();
        }
        consumers.draw(RenderLayer.getLines());
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableBlend();
    }

    private void drawBox(VertexConsumer b, Matrix4f m, float w, float h, float r, float g, float bl, float a) {
        line(b, m, -w, 0, 0, w, 0, 0, r, g, bl, a); line(b, m, -w, h, 0, w, h, 0, r, g, bl, a);
        line(b, m, -w, 0, 0, -w, h, 0, r, g, bl, a); line(b, m, w, 0, 0, w, h, 0, r, g, bl, a);
    }
    private void line(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.vertex(m, x1, y1, z1).color(r, g, bl, a).normal(0, 1, 0);
        b.vertex(m, x2, y2, z2).color(r, g, bl, a).normal(0, 1, 0);
    }

    private void throwPearl(MinecraftClient c) {
        int pS = findItem(c, Items.ENDER_PEARL);
        if (pS != -1) runInteract(c, pS);
    }
    private void swapElytra(MinecraftClient c) {
        boolean wear = c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = c.player.getInventory().getStack(i);
            if (wear ? (s.isOf(Items.NETHERITE_CHESTPLATE) || s.isOf(Items.DIAMOND_CHESTPLATE)) : s.isOf(Items.ELYTRA)) { slot = i; break; }
        }
        if (slot != -1) {
            int invS = slot < 9 ? slot + 36 : slot;
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, invS, 0, SlotActionType.PICKUP, c.player);
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, c.player);
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, invS, 0, SlotActionType.PICKUP, c.player);
        }
    }
    private int findItem(MinecraftClient c, net.minecraft.item.Item item) {
        for (int i = 9; i < 36; i++) if (c.player.getInventory().getStack(i).isOf(item)) return i;
        for (int i = 0; i < 9; i++) if (c.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private void runInteract(MinecraftClient c, int slot) {
        int old = c.player.getInventory().selectedSlot;
        if (slot < 9) {
            c.player.getInventory().selectedSlot = slot; c.interactionManager.interactItem(c.player, Hand.MAIN_HAND); c.player.getInventory().selectedSlot = old;
        } else {
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, slot, old, SlotActionType.SWAP, c.player);
            c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
            c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, slot, old, SlotActionType.SWAP, c.player);
        }
    }
    private void runTrigger(MinecraftClient c) {
        if (c.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p) {
            if (p.isAlive() && !friendsList.contains(p.getName().getString().toLowerCase()) && c.player.getAttackCooldownProgress(0) == 1.0f) {
                c.interactionManager.attackEntity(c.player, p); c.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
    private void checkTotem(MinecraftClient c) {
        if (!c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 45; i++) {
                if (c.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    int s = (i < 9) ? (i + 36) : i;
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, s, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, c.player);
                    c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, s, 0, SlotActionType.PICKUP, c.player);
                    break;
                }
            }
        }
    }
    private void notify(MinecraftClient c, String m, boolean s) {
        c.player.sendMessage(Text.literal("§b[Bubble] §f" + m + ": " + (s ? "§aON" : "§cOFF")), true);
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
    public static void updateFarm(String r) {
        farmBlocksRaw = r; farmBlockList.clear();
        if (r != null && !r.isEmpty()) Arrays.stream(r.split(",")).map(String::trim).map(String::toLowerCase).forEach(farmBlockList::add);
    }
    public static void saveConfig() {
        try (PrintWriter w = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            w.println(kaRange+":"+kawallsRange+":"+autoRun+":"+keyKA+":"+keyTB+":"+keyAT+":"+friendsRaw+":"+esp+":"+keyESP+":"+fullbright+":"+keyFB+":"+elytraSwap+":"+keyES+":"+fastPearl+":"+keyFP+":"+antiVelocity+":"+autoFarm+":"+keyAF+":"+farmBlocksRaw);
        } catch (Exception ignored) {}
    }
    public static void loadConfig() {
        if (!Files.exists(Paths.get(CONFIG_FILE))) return;
        try {
            String[] p = Files.readAllLines(Paths.get(CONFIG_FILE)).get(0).split(":", -1);
            if (p.length >= 19) {
                kaRange=Double.parseDouble(p[0]); kawallsRange=Double.parseDouble(p[1]); autoRun=Boolean.parseBoolean(p[2]);
                keyKA=Integer.parseInt(p[3]); keyTB=Integer.parseInt(p[4]); keyAT=Integer.parseInt(p[5]); updateFriends(p[6]);
                esp=Boolean.parseBoolean(p[7]); keyESP=Integer.parseInt(p[8]); fullbright=Boolean.parseBoolean(p[9]);
                keyFB=Integer.parseInt(p[10]); elytraSwap=Boolean.parseBoolean(p[11]); keyES=Integer.parseInt(p[12]);
                fastPearl=Boolean.parseBoolean(p[13]); keyFP=Integer.parseInt(p[14]); antiVelocity=Boolean.parseBoolean(p[15]);
                autoFarm=Boolean.parseBoolean(p[16]); keyAF=Integer.parseInt(p[17]); updateFarm(p[18]);
            }
        } catch (Exception ignored) {}
    }

    // --- GUI ---
    
    public static class MainMenu extends Screen {
        public MainMenu() { super(Text.literal("Bubble")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, "BUBBLE MENU", cx, cy - 60, 0x00CCFF);
            drawBtn(ctx, cx - 105, cy - 20, "Combat", mx, my);
            drawBtn(ctx, cx - 35, cy - 20, "Visuals", mx, my);
            drawBtn(ctx, cx + 35, cy - 20, "Binds", mx, my);
        }
        private void drawBtn(DrawContext ctx, int x, int y, String n, int mx, int my) {
            boolean h = mx >= x && mx <= x + 70 && my >= y && my <= y + 20;
            ctx.fill(x, y, x + 70, y + 20, h ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x + 35, y + 6, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            if (mx >= cx-105 && mx <= cx-35 && my >= cy-20 && my <= cy) client.setScreen(new CategoryScreen(0, this));
            if (mx >= cx-35 && mx <= cx+35 && my >= cy-20 && my <= cy) client.setScreen(new CategoryScreen(1, this));
            if (mx >= cx+35 && mx <= cx+105 && my >= cy-20 && my <= cy) client.setScreen(new CategoryScreen(2, this));
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class CategoryScreen extends Screen {
        private final Screen parent; private final int category;
        private final String[] combat = {"KillAura", "TriggerBot", "AutoTotem"};
        private final String[] visuals = {"ESP", "FullBright"};
        private final String[] binds = {"ElytraSwap", "FastPearl", "AutoFarm"};
        public CategoryScreen(int cat, Screen p) { super(Text.literal("Cat")); this.category = cat; this.parent = p; }
        
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            String t = category == 0 ? "Combat" : category == 1 ? "Visuals" : "Binds";
            ctx.drawCenteredTextWithShadow(textRenderer, t, cx, cy - 80, 0x00CCFF);
            String[] list = category == 0 ? combat : category == 1 ? visuals : binds;
            for (int i = 0; i < list.length; i++) {
                int iy = cy - 50 + i * 24;
                boolean s = getState(list[i]);
                boolean h = mx >= cx - 60 && mx <= cx + 60 && my >= iy && my <= iy + 20;
                ctx.fill(cx - 60, iy, cx + 60, iy + 20, h ? 0xEE404040 : 0xEE202020);
                ctx.drawText(textRenderer, list[i], cx - 55, iy + 6, s ? 0x00FF00 : 0xFFFFFF, true);
            }
        }
        private boolean getState(String n) {
            switch(n) {
                case "KillAura": return killaura; case "TriggerBot": return triggerbot; case "AutoTotem": return autoTotem;
                case "ESP": return esp; case "FullBright": return fullbright;
                case "ElytraSwap": return elytraSwap; case "FastPearl": return fastPearl; case "AutoFarm": return autoFarm;
            } return false;
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            String[] list = category == 0 ? combat : category == 1 ? visuals : binds;
            for (int i = 0; i < list.length; i++) {
                int iy = cy - 50 + i * 24;
                if (mx >= cx - 60 && mx <= cx + 60 && my >= iy && my <= iy + 20) {
                    String n = list[i];
                    if (b == 1) { // Right Click
                        if (n.equals("KillAura")) client.setScreen(new KillAuraSettings(this));
                        else if (n.equals("AutoFarm")) client.setScreen(new AutoFarmSettings(this));
                        else client.setScreen(new BindScreen(this, getId(n)));
                        return true;
                    }
                    if (b == 0) { toggle(n); saveConfig(); return true; }
                }
            }
            return super.mouseClicked(mx, my, b);
        }
        @Override public void close() { client.setScreen(parent); }
        private void toggle(String n) {
            switch(n) {
                case "KillAura": killaura=!killaura; break; case "TriggerBot": triggerbot=!triggerbot; break;
                case "AutoTotem": autoTotem=!autoTotem; break; case "ESP": esp=!esp; break;
                case "FullBright": fullbright=!fullbright; break; case "ElytraSwap": elytraSwap=!elytraSwap; break;
                case "FastPearl": fastPearl=!fastPearl; break; case "AutoFarm": autoFarm=!autoFarm; currentMineTarget=null; break;
            }
        }
        private int getId(String n) {
            switch(n) {
                case "KillAura": return 0; case "TriggerBot": return 1; case "AutoTotem": return 3;
                case "ESP": return 4; case "FullBright": return 2; case "ElytraSwap": return 6;
                case "FastPearl": return 5; case "AutoFarm": return 7;
            } return -1;
        }
    }

    public static class KillAuraSettings extends Screen {
        private final Screen parent; private TextFieldWidget rF, wF, fF;
        private ButtonWidget bindBtn;
        public KillAuraSettings(Screen parent) { super(Text.literal("KA")); this.parent = parent; }
        @Override
        protected void init() {
            int cx = width / 2, cy = height / 2;
            // Range Field
            rF = new TextFieldWidget(textRenderer, cx - 100, cy - 65, 90, 16, Text.literal(""));
            rF.setText(String.valueOf(kaRange));
            // Walls Field
            wF = new TextFieldWidget(textRenderer, cx + 10, cy - 65, 90, 16, Text.literal(""));
            wF.setText(String.valueOf(kawallsRange));
            // Friends Field
            fF = new TextFieldWidget(textRenderer, cx - 100, cy - 25, 200, 16, Text.literal("Friends"));
            fF.setText(friendsRaw);
            
            // BIND BUTTON INSIDE SETTINGS
            bindBtn = ButtonWidget.builder(Text.literal("Bind Key: " + getKeyName(keyKA)), button -> {
                client.setScreen(new BindScreen(this, 0));
            }).dimensions(cx - 50, cy + 45, 100, 20).build();

            addDrawableChild(rF); addDrawableChild(wF); addDrawableChild(fF); addDrawableChild(bindBtn);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, "KILLAURA SETTINGS", cx, cy - 90, 0x00CCFF);
            ctx.drawText(textRenderer, "Range:", cx - 100, cy - 77, -1, true);
            ctx.drawText(textRenderer, "Walls Range:", cx + 10, cy - 77, -1, true);
            ctx.drawText(textRenderer, "Friends (comma separated):", cx - 100, cy - 37, -1, true);
            
            drawCheck(ctx, cx - 100, cy + 5, "AutoRun", autoRun, mx, my);
            drawCheck(ctx, cx + 10, cy + 5, "AntiVelocity", antiVelocity, mx, my);
        }
        private void drawCheck(DrawContext ctx, int x, int y, String n, boolean s, int mx, int my) {
            boolean h = mx >= x && mx <= x + 90 && my >= y && my <= y + 20;
            ctx.fill(x, y, x + 90, y + 20, h ? 0xEE404040 : 0xEE202020);
            ctx.drawCenteredTextWithShadow(textRenderer, n, x + 45, y + 6, s ? 0x00FF00 : 0xFFFFFF);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int cx = width / 2, cy = height / 2;
            if (mx >= cx - 100 && mx <= cx - 10 && my >= cy + 5 && my <= cy + 25) { autoRun = !autoRun; return true; }
            if (mx >= cx + 10 && mx <= cx + 100 && my >= cy + 5 && my <= cy + 25) { antiVelocity = !antiVelocity; return true; }
            return super.mouseClicked(mx, my, b);
        }
        @Override public void close() {
            try { kaRange = Double.parseDouble(rF.getText()); kawallsRange = Double.parseDouble(wF.getText()); } catch (Exception ignored) {}
            updateFriends(fF.getText()); saveConfig(); client.setScreen(parent);
        }
        private String getKeyName(int k) { return k == -1 ? "NONE" : InputUtil.fromKeyCode(k, 0).getLocalizedText().getString(); }
    }

    public static class AutoFarmSettings extends Screen {
        private final Screen parent; private TextFieldWidget bF; private ButtonWidget bindBtn;
        public AutoFarmSettings(Screen parent) { super(Text.literal("Farm")); this.parent = parent; }
        @Override
        protected void init() {
            int cx = width / 2, cy = height / 2;
            bF = new TextFieldWidget(textRenderer, cx - 100, cy - 10, 200, 20, Text.literal("Blocks"));
            bF.setText(farmBlocksRaw); bF.setMaxLength(256);
            
            bindBtn = ButtonWidget.builder(Text.literal("Bind Key: " + getKeyName(keyAF)), button -> {
                client.setScreen(new BindScreen(this, 7));
            }).dimensions(cx - 50, cy + 20, 100, 20).build();

            addDrawableChild(bF); addDrawableChild(bindBtn);
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            int cx = width / 2, cy = height / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, "BLOCKS TO MINE (Comma separated)", cx, cy - 30, -1);
            ctx.drawCenteredTextWithShadow(textRenderer, "Example: Алмазная руда, Золотая руда", cx, cy + 50, 0xAAAAAA);
        }
        @Override public void close() {
            updateFarm(bF.getText()); saveConfig(); client.setScreen(parent);
        }
        private String getKeyName(int k) { return k == -1 ? "NONE" : InputUtil.fromKeyCode(k, 0).getLocalizedText().getString(); }
    }

    public static class BindScreen extends Screen {
        private final Screen parent; private final int id;
        public BindScreen(Screen parent, int id) { super(Text.literal("Bind")); this.parent = parent; this.id = id; }
        @Override
        public boolean keyPressed(int k, int s, int n) {
            int v = (k == GLFW.GLFW_KEY_ESCAPE) ? -1 : k;
            if (id == 0) keyKA = v; else if (id == 1) keyTB = v; else if (id == 2) keyFB = v;
            else if (id == 3) keyAT = v; else if (id == 4) keyESP = v; else if (id == 5) keyFP = v;
            else if (id == 6) keyES = v; else if (id == 7) keyAF = v;
            saveConfig(); client.setScreen(parent); return true;
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            renderBackground(ctx, mx, my, delta);
            ctx.drawCenteredTextWithShadow(textRenderer, "PRESS ANY KEY", width / 2, height / 2, 0x00CCFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "Escape to unbind", width / 2, height / 2 + 20, 0xAAAAAA);
        }
    }
}

