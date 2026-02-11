package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ExampleMod implements ModInitializer {

    private static boolean active = false;
    private static String targetName = "";
    private static List<String> targetEnchants = new ArrayList<>();
    private static long maxPrice = 0;
    
    private boolean isBuying = false;
    private long lastRefresh = 0;

    @Override
    public void onInitialize() {
        // Управление через чат
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.toLowerCase().trim();
            if (msg.equals(".b on")) {
                active = true;
                log("§aВключен");
                return false;
            }
            if (msg.equals(".b off")) {
                active = false;
                log("§cВыключен");
                return false;
            }
            if (msg.startsWith(".b ")) {
                parseCmd(message.substring(3));
                return false;
            }
            return true;
        });

        // Основная логика (максимально облегченная)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || client.player == null || isBuying) return;

            if (client.currentScreen instanceof GenericContainerScreen menu) {
                String title = menu.getTitle().getString().toLowerCase();
                
                // Проверка только заголовков аукциона
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
                maxPrice = Long.parseLong(p[p.length - 1]);
                targetEnchants.clear();
            } else {
                targetName = split[0].trim().toLowerCase();
                String[] p = split[1].trim().split("\\s+");
                maxPrice = Long.parseLong(p[p.length - 1]);
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
            log("§fИщу: §a" + targetName + " §7| §e" + targetEnchants + " §7| §6" + maxPrice + "$");
            active = true;
        } catch (Exception e) { log("§cОшибка формата!"); }
    }

    private String toRoman(String in) {
        String[] r = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
        for (int i = 10; i >= 1; i--) {
            if (in.endsWith(" " + i)) return in.substring(0, in.length() - String.valueOf(i).length()).trim() + " " + r[i-1];
        }
        return in;
    }

    private void scan(MinecraftClient client, GenericContainerScreen menu) {
        for (int i = 0; i < 45; i++) {
            ItemStack s = menu.getScreenHandler().getSlot(i).getStack();
            if (!s.isEmpty() && s.getName().getString().toLowerCase().contains(targetName)) {
                if (checkLore(s)) {
                    isBuying = true;
                    int slot = i;
                    new Thread(() -> {
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextLong(650, 950));
                            client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
                        } catch (Exception ignored) {}
                    }).start();
                    return;
                }
            }
        }
        // Обновление (слот 49)
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
        if (lore.contains("$")) {
            try {
                String p = lore.substring(lore.lastIndexOf("$") + 1).replaceAll("[^0-9]", "");
                return Long.parseLong(p) <= maxPrice;
            } catch (Exception e) { return false; }
        }
        return false;
    }

    private void confirm(MinecraftClient client, GenericContainerScreen menu) {
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(350, 550));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 10, 0, SlotActionType.PICKUP, client.player);
                Thread.sleep(1200);
                isBuying = false;
            } catch (Exception ignored) { isBuying = false; }
        }).start();
    }

    private void log(String m) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] " + m), false);
    }
}

