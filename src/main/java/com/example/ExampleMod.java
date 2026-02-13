package com.example;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.class_1268;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1675;
import net.minecraft.class_1713;
import net.minecraft.class_1802;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_3532;
import net.minecraft.class_364;
import net.minecraft.class_3675;
import net.minecraft.class_3966;
import net.minecraft.class_437;
import net.minecraft.class_9779;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    public static boolean killaura = false, triggerbot = false, fullbright = false, waypointActive = false;
    public static boolean autoTotem = true;
    public static boolean autoRun = true;
    public static boolean antiVelocity = true;
    public static boolean tbCrits = true;
    public static double kaRange = 3.8D, kaWallsRange = 3.0D;
    public static double wpX = 0.0D, wpY = 64.0D, wpZ = 0.0D;
    public static float shakeIntensity = 0.5F;
    public static int keyKA = -1, keyTB = -1, keyFB = -1, keyAT = -1, keyWP = -1;
    private static final boolean[] keyStates = new boolean[512];
    private static final String CONFIG_FILE = "bubble_config.txt";
    private static final Random rnd = new Random();
    public static class_1657 auraTarget = null;

    @Override
    public void onInitialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.field_1724 == null || client.field_1687 == null) return;
            long h = client.method_22683().method_4490();

            // Открытие меню на клавишу U (код 48)
            if (isPressed(h, 48) && client.field_1755 == null) {
                client.method_1507(new BubbleMenu());
            }

            if (client.field_1755 == null) {
                if (isPressed(h, keyKA)) { killaura = !killaura; sendNotify("KillAura", killaura); }
                if (isPressed(h, keyTB)) { triggerbot = !triggerbot; sendNotify("TriggerBot", triggerbot); }
                if (isPressed(h, keyFB)) { fullbright = !fullbright; sendNotify("FullBright", fullbright); }
                if (isPressed(h, keyAT)) { autoTotem = !autoTotem; sendNotify("AutoTotem", autoTotem); }
                if (isPressed(h, keyWP)) { waypointActive = !waypointActive; sendNotify("Waypoint", waypointActive); }
            }

            if (fullbright) {
                client.field_1724.method_6092(new class_1293(class_1294.field_5925, 1000, 0, false, false));
            }
            if (autoTotem) handleAutoTotem(client);
            if (autoRun && killaura && auraTarget != null && auraTarget.method_5805()) {
                client.field_1724.method_5728(true);
            }
            if (killaura) { runAura(client); } else { auraTarget = null; }
            if (triggerbot) runTrigger(client);
            if (antiVelocity && client.field_1724.field_6235 > 0) {
                client.field_1724.method_18800(0.0D, (client.field_1724.method_18798()).field_1351, 0.0D);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!waypointActive) return;
            class_310 client = class_310.method_1551();
            if (client.field_1724 == null) return;
            double dist = client.field_1724.method_19538().method_1022(new class_243(wpX, wpY, wpZ));
            float yawToTarget = (float) Math.toDegrees(Math.atan2(wpZ - client.field_1724.method_23321(), wpX - client.field_1724.method_23317())) - 90.0F;
            float angleDiff = class_3532.method_15393(yawToTarget - client.field_1724.method_36454());
            String arrow = (Math.abs(angleDiff) < 10.0F) ? "↑" : ((angleDiff > 0.0F) ? "→" : "←");
            String text = String.format("WP: %.0f %.0f %.0f | %s | Dist: %.1f", wpX, wpY, wpZ, arrow, dist);
            drawContext.method_25300(client.field_1772, text, drawContext.method_51421() / 2, 10, -1);
        });
    }

    private void handleAutoTotem(class_310 client) {
        if (client.field_1724.method_6079().method_7909() != class_1802.field_8288) {
            for (int i = 0; i < 45; i++) {
                if (client.field_1724.method_31548().method_5438(i).method_7909() == class_1802.field_8288) {
                    client.field_1761.method_2906(client.field_1724.field_7512.field_7763, (i < 9) ? (i + 36) : i, 0, class_1713.field_7791, client.field_1724);
                    break;
                }
            }
        }
    }

    private void runAura(class_310 client) {
        auraTarget = null;
        double bestDist = Double.MAX_VALUE;
        for (class_1657 p : client.field_1687.method_18456()) {
            if (p == client.field_1724 || !p.method_5805() || p.method_7337()) continue;
            double d = client.field_1724.method_5739((class_1297) p);
            if (d > kaRange || d >= bestDist || (!client.field_1724.method_6057((class_1297) p) && d > kaWallsRange)) continue;
            bestDist = d;
            auraTarget = p;
        }
        if (auraTarget != null) {
            double offset = (rnd.nextDouble() - 0.5D) * 0.1D;
            class_243 tPos = auraTarget.method_19538().method_1031(offset, auraTarget.method_17682() * (0.4D + rnd.nextDouble() * 0.3D), offset);
            updateRotations(client.field_1724, tPos, 100.0F);
            if (client.field_1724.method_7261(0.0F) >= 1.0F) {
                client.field_1761.method_2918(client.field_1724, auraTarget);
                client.field_1724.method_6104(class_1268.field_5808);
            }
        }
    }

    private void runTrigger(class_310 client) {
        double reach = kaRange;
        class_243 eye = client.field_1724.method_33571();
        class_243 look = client.field_1724.method_5828(1.0F).method_1021(reach);
        class_238 box = client.field_1724.method_5829().method_1009(look.field_1352, look.field_1351, look.field_1350).method_1014(1.0D);
        class_3966 hit = class_1675.method_18075(client.field_1724, eye, eye.method_1019(look), box, e -> (e instanceof class_1657 && e.method_5805()), reach * reach);
        if (hit != null && hit.method_17782() instanceof class_1657 target) {
            if (client.field_1724.method_7261(0.0F) >= (tbCrits ? 1.0F : 0.92F)) {
                client.field_1761.method_2918(client.field_1724, target);
                client.field_1724.method_6104(class_1268.field_5808);
            }
        }
    }

    private void updateRotations(class_1657 player, class_243 target, float speed) {
        class_243 diff = target.method_1020(player.method_33571());
        float tYaw = (float) Math.toDegrees(Math.atan2(diff.field_1350, diff.field_1352)) - 90.0F;
        float tPitch = (float) -Math.toDegrees(Math.atan2(diff.field_1351, Math.sqrt(diff.field_1352 * diff.field_1352 + diff.field_1350 * diff.field_1350)));
        player.method_36456(player.method_36454() + class_3532.method_15363(class_3532.method_15393(tYaw - player.method_36454()), -speed, speed));
        player.method_36457(player.method_36455() + class_3532.method_15363(class_3532.method_15393(tPitch - player.method_36455()), -speed, speed));
    }

    private void sendNotify(String m, boolean s) {
        if (class_310.method_1551().field_1724 != null) {
            class_310.method_1551().field_1724.method_7353(class_2561.method_43470(m + ": " + (s ? "§aON" : "§cOFF")), true);
        }
    }

    private boolean isPressed(long h, int k) {
        if (k == -1) return false;
        boolean d = class_3675.method_15987(h, k);
        if (d && !keyStates[k]) { keyStates[k] = true; return true; }
        if (!d) keyStates[k] = false;
        return false;
    }

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

    public static class BubbleMenu extends class_437 {
        public BubbleMenu() { super(class_2561.method_43470("Bubble")); }
        @Override
        public void method_25394(class_332 ctx, int mx, int my, float d) {
            ctx.method_25294(0, 0, this.field_22789, this.field_22790, -1879048192);
            int x = this.field_22789 / 2 - 90, y = this.field_22790 / 2 - 105;
            ctx.method_25294(x, y, x + 180, y + 155, -16448251);
            ctx.method_49601(x, y, 180, 155, -16733441);
            ctx.method_25300(this.field_22793, "BUBBLE CLIENT", this.field_22789 / 2, y + 10, -1);
            String[] n = { "KillAura", "TriggerBot", "FullBright", "AutoTotem", "Waypoint" };
            boolean[] s = { killaura, triggerbot, fullbright, autoTotem, waypointActive };
            for (int i = 0; i < 5; i++) {
                int iy = y + 35 + i * 22;
                boolean h = (mx >= x + 10 && mx <= x + 170 && my >= iy && my <= iy + 18);
                ctx.method_25294(x + 10, iy, x + 170, iy + 18, h ? -15066598 : -15724528);
                ctx.method_25303(this.field_22793, n[i], x + 15, iy + 5, s[i] ? -16711936 : -1);
                ctx.method_25303(this.field_22793, ">>", x + 155, iy + 5, -1);
            }
        }
        @Override
        public boolean method_25402(double mx, double my, int b) {
            int x = this.field_22789 / 2 - 90, y = this.field_22790 / 2 - 105;
            for (int i = 0; i < 5; i++) {
                int iy = y + 35 + i * 22;
                if (mx >= (x + 150) && mx <= (x + 170) && my >= iy && my <= (iy + 18)) {
                    if (i == 0) this.field_22787.method_1507(new KillAuraSettings(this));
                    if (i == 4) this.field_22787.method_1507(new WaypointSettings(this));
                    return true;
                }
                if (mx >= (x + 10) && mx <= (x + 150) && my >= iy && my <= (iy + 18)) {
                    if (b == 0) {
                        if (i == 0) killaura = !killaura;
                        if (i == 1) triggerbot = !triggerbot;
                        if (i == 2) fullbright = !fullbright;
                        if (i == 3) autoTotem = !autoTotem;
                        if (i == 4) waypointActive = !waypointActive;
                    } else {
                        this.field_22787.method_1507(new BindScreen(this, i));
                    }
                    saveConfig();
                    return true;
                }
            }
            return false;
        }
    }

    public static class KillAuraSettings extends class_437 {
        private final class_437 p;
        private class_342 f1, f2;
        public KillAuraSettings(class_437 p) { super(class_2561.method_43470("KA Settings")); this.p = p; }
        @Override
        protected void method_25426() {
            int x = this.field_22789 / 2 + 25;
            this.f1 = new class_342(this.field_22793, x, this.field_22790 / 2 - 70, 45, 14, class_2561.method_43470(""));
            this.f2 = new class_342(this.field_22793, x, this.field_22790 / 2 - 50, 45, 14, class_2561.method_43470(""));
            this.f1.method_1852(String.valueOf(kaRange));
            this.f2.method_1852(String.valueOf(kaWallsRange));
            method_25429(this.f1); method_25429(this.f2);
        }
        @Override
        public void method_25394(class_332 ctx, int mx, int my, float d) {
            ctx.method_25294(0, 0, this.field_22789, this.field_22790, -1879048192);
            int x = this.field_22789 / 2;
            ctx.method_25294(x - 110, this.field_22790 / 2 - 95, x + 110, this.field_22790 / 2 + 95, -16448251);
            ctx.method_49601(x - 110, this.field_22790 / 2 - 95, 220, 190, -16733441);
            ctx.method_25300(this.field_22793, "KA SETTINGS", x, this.field_22790 / 2 - 88, -1);
            ctx.method_25303(this.field_22793, "Range:", x - 100, this.field_22790 / 2 - 67, -1);
            ctx.method_25303(this.field_22793, "Walls:", x - 100, this.field_22790 / 2 - 47, -1);
            this.f1.method_25394(ctx, mx, my, d);
            this.f2.method_25394(ctx, mx, my, d);
            drawBtn(ctx, x - 100, this.field_22790 / 2 - 25, 200, 14, "AutoRun: " + autoRun, mx, my);
            drawBtn(ctx, x - 100, this.field_22790 / 2 - 5, 200, 14, "AntiVelocity: " + antiVelocity, mx, my);
            ctx.method_25300(this.field_22793, "CFG SERVERS", x, this.field_22790 / 2 + 20, -1);
            drawBtn(ctx, x - 100, this.field_22790 / 2 + 35, 200, 14, "MineBlaze", mx, my);
            drawBtn(ctx, x - 100, this.field_22790 / 2 + 55, 200, 14, "AresMine", mx, my);
        }
        private void drawBtn(class_332 ctx, int x, int y, int w, int h, String t, int mx, int my) {
            boolean hv = (mx >= x && mx <= x + w && my >= y && my <= y + h);
            ctx.method_25294(x, y, x + w, y + h, hv ? -14540254 : -15658735);
            ctx.method_25300(this.field_22793, t, x + w / 2, y + 3, -1);
        }
        @Override
        public boolean method_25402(double mx, double my, int b) {
            int x = this.field_22789 / 2;
            if (mx >= (x - 100) && mx <= (x + 100)) {
                if (my >= (this.field_22790 / 2 - 25) && my <= (this.field_22790 / 2 - 11)) autoRun = !autoRun;
                if (my >= (this.field_22790 / 2 - 5) && my <= (this.field_22790 / 2 + 9)) antiVelocity = !antiVelocity;
                if (my >= (this.field_22790 / 2 + 35) && my <= (this.field_22790 / 2 + 49)) { kaRange = 3.1D; kaWallsRange = 0.0D; f1.method_1852("3.1"); f2.method_1852("0.0"); }
                if (my >= (this.field_22790 / 2 + 55) && my <= (this.field_22790 / 2 + 69)) { kaRange = 3.8D; kaWallsRange = 3.0D; f1.method_1852("3.8"); f2.method_1852("3.0"); }
                saveConfig(); return true;
            }
            return super.method_25402(mx, my, b);
        }
        @Override
        public boolean method_25404(int k, int s, int m) {
            if (k == 256) {
                try { kaRange = Double.parseDouble(f1.method_1882()); kaWallsRange = Double.parseDouble(f2.method_1882()); } catch (Exception ignored) {}
                saveConfig(); this.field_22787.method_1507(p); return true;
            }
            return super.method_25404(k, s, m);
        }
    }

    public static class WaypointSettings extends class_437 {
        private final class_437 p;
        private class_342 f1, f2, f3;
        public WaypointSettings(class_437 p) { super(class_2561.method_43470("Waypoints")); this.p = p; }
        @Override
        protected void method_25426() {
            int x = this.field_22789 / 2 + 20;
            f1 = new class_342(this.field_22793, x, this.field_22790 / 2 - 45, 50, 16, class_2561.method_43470(""));
            f2 = new class_342(this.field_22793, x, this.field_22790 / 2 - 20, 50, 16, class_2561.method_43470(""));
            f3 = new class_342(this.field_22793, x, this.field_22790 / 2 + 5, 50, 16, class_2561.method_43470(""));
            f1.method_1852(String.valueOf((int)wpX)); f2.method_1852(String.valueOf((int)wpY)); f3.method_1852(String.valueOf((int)wpZ));
            method_25429(f1); method_25429(f2); method_25429(f3);
        }
        @Override
        public void method_25394(class_332 ctx, int mx, int my, float d) {
            ctx.method_25294(0, 0, this.field_22789, this.field_22790, -1879048192);
            int x = this.field_22789 / 2;
            ctx.method_25294(x - 115, this.field_22790 / 2 - 90, x + 115, this.field_22790 / 2 + 90, -16448251);
            ctx.method_49601(x - 115, this.field_22790 / 2 - 90, 230, 180, -16733441);
            ctx.method_25300(this.field_22793, "WP SETTINGS", x, this.field_22790 / 2 - 80, -1);
            ctx.method_25303(this.field_22793, "X:", x - 100, this.field_22790 / 2 - 41, -1);
            ctx.method_25303(this.field_22793, "Y:", x - 100, this.field_22790 / 2 - 16, -1);
            ctx.method_25303(this.field_22793, "Z:", x - 100, this.field_22790 / 2 + 9, -1);
            f1.method_25394(ctx, mx, my, d); f2.method_25394(ctx, mx, my, d); f3.method_25394(ctx, mx, my, d);
        }
        @Override
        public boolean method_25404(int k, int s, int m) {
            if (k == 256) {
                try { wpX = Double.parseDouble(f1.method_1882()); wpY = Double.parseDouble(f2.method_1882()); wpZ = Double.parseDouble(f3.method_1882()); } catch (Exception ignored) {}
                saveConfig(); this.field_22787.method_1507(p); return true;
            }
            return super.method_25404(k, s, m);
        }
    }

    public static class BindScreen extends class_437 {
        private final class_437 p;
        private final int id;
        public BindScreen(class_437 p, int id) { super(class_2561.method_43470("Bind")); this.p = p; this.id = id; }
        @Override
        public void method_25394(class_332 ctx, int mx, int my, float d) {
            ctx.method_25294(0, 0, this.field_22789, this.field_22790, -301989888);
            ctx.method_25300(this.field_22793, "PRESS ANY KEY (ESC TO CLEAR)", this.field_22789 / 2, this.field_22790 / 2, -1);
        }
        @Override
        public boolean method_25404(int k, int s, int m) {
            if (k == 256) k = -1;
            if (id == 0) keyKA = k; else if (id == 1) keyTB = k; else if (id == 2) keyFB = k; else if (id == 3) keyAT = k; else if (id == 4) keyWP = k;
            saveConfig(); this.field_22787.method_1507(p); return true;
        }
    }
}

