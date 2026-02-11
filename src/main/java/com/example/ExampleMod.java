package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExampleMod implements ModInitializer {

    private static boolean active = false;
    private static String targetName = "";
    private static List<String> targetEnchants = new ArrayList<>();
    private static long maxPrice = 0;
    
    private boolean isBuying = false;
    private long lastActionTime = 0;

    @Override
    public void onInitialize() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String msg = message.toLowerCase().trim();
            if (msg.equals(".b on")) { active = true; log("§aON"); return false; }
            if (msg.equals(".b off")) { active = false; log("§cOFF"); return false; }
            if (msg.startsWith(".b ")) { parseCmd(message.substring(3)); return false; }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || client.player == null || client.currentScreen == null) {
                isBuying = false; // Сброс состояния вне меню
                return;
            }

            if (client.currentScreen instanceof HandledScreen<?> menu) {
                String title = menu.getTitle().getString().toLowerCase();
                long now = System.currentTimeMillis();

                // 1. Логика аукциона
                if (title.contains("аукцион") || title.contains("поиск") || title.contains("search")) {
                    if (isBuying) return;
                    
                    if (!scanSlots(client, menu)) {
                        // Если ничего не нашли, обновляем страницу (каждые 1.1 сек для обхода защиты)
                        if (now - lastActionTime > 1100) {
                            click(client, menu, 49);
                            lastActionTime = now;
                        }
                    }
                } 
                // 2. Логика подтверждения (максимально быстро)
                else if (title.contains("подтверждение") || title.contains("покупка")) {
                    if (now - lastActionTime > 300) { // Небольшая пауза для прогрузки GUI
                        click(client, menu, 10);
                        lastActionTime = now;
                        isBuying = false; // Готовы к следующему кругу
                    }
                }
            }
        });
    }

    private boolean scanSlots(MinecraftClient client, HandledScreen<?> menu) {
        // Проверяем только первые 45 слотов (товары)
        for (int i = 0; i < 45; i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (stack.getName().getString().toLowerCase().contains(targetName)) {
                if (checkLore(stack)) {
                    isBuying = true;
                    click(client, menu, i);
                    lastActionTime = System.currentTimeMillis();
                    return true;
                }
            }
        }
        return false;
    }

    private void click(MinecraftClient client, HandledScreen<?> menu, int slot) {
        client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
    }

    private boolean checkLore(ItemStack s) {
        var loreObj = s.get(DataComponentTypes.LORE);
        if (loreObj == null) return targetEnchants.isEmpty();
        String lore = loreObj.toString().toLowerCase();

        for (String e : targetEnchants) if (!lore.contains(e)) return false;

        try {
            // Агрессивная очистка строки от мусора
            String clean = lore.replaceAll("[^0-9$]", ""); 
            Pattern p = Pattern.compile("(\\d+)\\$|\\$(\\d+)");
            Matcher m = p.matcher(clean);
            
            long price = -1;
            if (m.find()) {
                String val = m.group(1) != null ? m.group(1) : m.group(2);
                price = Long.parseLong(val);
            } else {
                // Запасной вариант: берем последнее число
                String onlyDigits = lore.replaceAll("(\\d)[. ](\\d)", "$1$2").replaceAll("[^0-9]", " ");
                String[] words = onlyDigits.trim().split("\\s+");
                price = Long.parseLong(words[words.length - 1]);
            }
            return price > 0 && price <= maxPrice;
        } catch (Exception e) { return false; }
    }

    private void parseCmd(String input) {
        try {
            String[] split = input.split(",", 2);
            if (split.length < 2) {
                String[] p = input.trim().split("\\s+");
                targetName = p[0].toLowerCase();
                maxPrice = Long.parseLong(p[p.length-1].replaceAll("[^0-9]", ""));
            } else {
                targetName = split[0].trim().toLowerCase();
                String[] p = split[1].trim().split("\\s+");
                maxPrice = Long.parseLong(p[p.length-1].replaceAll("[^0-9]", ""));
                targetEnchants.clear();
                String cur = "";
                for (int i = 0; i < p.length-1; i++) {
                    if (p[i].matches("\\d+")) {
                        targetEnchants.add(toRoman(cur + " " + p[i]));
                        cur = "";
                    } else {
                        if (!cur.isEmpty()) targetEnchants.add(cur);
                        cur = p[i].toLowerCase();
                    }
                }
                if (!cur.isEmpty()) targetEnchants.add(cur);
            }
            log("§fЦель: §a" + targetName + " §7| §6" + maxPrice + "$");
        } catch (Exception e) { log("§cОшибка!"); }
    }

    private String toRoman(String in) {
        String[] r = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
        for (int i = 10; i >= 1; i--) {
            if (in.endsWith(" " + i)) return in.substring(0, in.length() - String.valueOf(i).length()).trim() + " " + r[i-1];
        }
        return in;
    }

    private void log(String m) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] " + m), false);
    }
}

