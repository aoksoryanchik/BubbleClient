package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExampleMod implements ModInitializer {

    private static boolean active = false;
    private static String targetName = "";
    private static List<String> targetEnchants = new ArrayList<>();
    private static long maxPrice = 0;
    
    private boolean isBuying = false;
    private long lastRefresh = 0;

    @Override
    public void onInitialize() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.toLowerCase().trim();
            
            // Включение и выключение строго по командам
            if (msg.equals(".b on")) {
                active = true;
                log("§aАвтовыкуп включен");
                return false;
            }
            if (msg.equals(".b off")) {
                active = false;
                log("§cАвтовыкуп выключен");
                return false;
            }
            
            // Настройка (теперь БЕЗ автоматического включения)
            if (msg.startsWith(".b ")) {
                parseCmd(message.substring(3));
                return false;
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || client.player == null || isBuying) return;

            Screen screen = client.currentScreen;
            if (screen instanceof HandledScreen<?> menu) {
                String title = screen.getTitle().getString().toLowerCase();
                
                if (title.contains("аукцион") || title.contains("поиск") || title.contains("search")) {
                    scan(client, menu);
                } 
                else if (title.contains("подтверждение") || title.contains("покупка")) {
                    confirm(client, menu);
                }
            }
        });
    }

    private void parseCmd(String input) {
        try {
            String[] split = input.split(",", 2);
            if (split.length < 2) {
                String[] p = input.trim().split("\\s+");
                targetName = p[0].toLowerCase();
                maxPrice = Long.parseLong(p[p.length - 1].replaceAll("[^0-9]", ""));
                targetEnchants.clear();
            } else {
                targetName = split[0].trim().toLowerCase();
                String[] p = split[1].trim().split("\\s+");
                maxPrice = Long.parseLong(p[p.length - 1].replaceAll("[^0-9]", ""));
                targetEnchants.clear();
                
                String currentEnc = "";
                for (int i = 0; i < p.length - 1; i++) {
                    String s = p[i].toLowerCase();
                    if (s.matches("\\d+")) {
                        targetEnchants.add(toRoman(currentEnc + " " + s));
                        currentEnc = "";
                    } else {
                        if (!currentEnc.isEmpty()) targetEnchants.add(currentEnc);
                        currentEnc = s;
                    }
                }
                if (!currentEnc.isEmpty()) targetEnchants.add(currentEnc);
            }
            log("§fДанные обновлены. Предмет: §a" + targetName + " §7| До: §6" + maxPrice + "$");
            log("§7Статус модуля: " + (active ? "§aРАБОТАЕТ" : "§cВЫКЛЮЧЕН (введите .b on)"));
        } catch (Exception e) { log("§cОшибка! Пример: .b Меч, острота 5 50000"); }
    }

    private String toRoman(String in) {
        String[] r = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
        for (int i = 10; i >= 1; i--) {
            if (in.endsWith(" " + i)) return in.substring(0, in.length() - String.valueOf(i).length()).trim() + " " + r[i-1];
        }
        return in;
    }

    private void scan(MinecraftClient client, HandledScreen<?> menu) {
        for (int i = 0; i < 45; i++) {
            ItemStack s = menu.getScreenHandler().getSlot(i).getStack();
            if (s.isEmpty()) continue;

            if (s.getName().getString().toLowerCase().contains(targetName) && checkLore(s)) {
                isBuying = true;
                int slot = i;
                new Thread(() -> {
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(650, 950));
                        client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
                    } catch (Exception e) { isBuying = false; }
                }).start();
                return;
            }
        }
        if (System.currentTimeMillis() - lastRefresh > 1400) {
            client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 49, 0, SlotActionType.PICKUP, client.player);
            lastRefresh = System.currentTimeMillis();
        }
    }

    private boolean checkLore(ItemStack s) {
        var l = s.get(DataComponentTypes.LORE);
        if (l == null) return targetEnchants.isEmpty();
        String lore = l.toString().toLowerCase();

        for (String e : targetEnchants) if (!lore.contains(e)) return false;

        try {
            // Очистка лора от визуальных разделителей для точного поиска цены
            String cleanLore = lore.replaceAll("(\\d)[. ](\\d)", "$1$2"); 
            Pattern pattern = Pattern.compile("(\\d+)"); 
            Matcher matcher = pattern.matcher(cleanLore);
            
            long lastFoundPrice = -1;
            while (matcher.find()) {
                lastFoundPrice = Long.parseLong(matcher.group(1));
            }
            return lastFoundPrice != -1 && lastFoundPrice <= maxPrice;
        } catch (Exception ignored) {}
        return false;
    }

    private void confirm(MinecraftClient client, HandledScreen<?> menu) {
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(350, 550));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 10, 0, SlotActionType.PICKUP, client.player);
                Thread.sleep(2000);
            } catch (Exception ignored) {} finally {
                isBuying = false;
            }
        }).start();
    }

    private void log(String m) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] " + m), false);
    }
}

