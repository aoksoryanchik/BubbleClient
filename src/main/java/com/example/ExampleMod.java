package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.component.DataComponentTypes;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ExampleMod implements ModInitializer {

    public static boolean autoBuyActive = false;
    public static String targetName = "";
    public static List<String> targetEnchants = new ArrayList<>();
    public static long maxPrice = 0;

    private boolean isBuying = false;
    private long lastRefreshTime = 0;

    @Override
    public void onInitialize() {
        // Перехват команды
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".b ")) {
                parseSmartCommand(message.substring(3));
                return false; 
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !autoBuyActive) return;
            
            if (InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_J) && client.currentScreen == null) {
                client.setScreen(new AutoBuyMenu());
            }

            if (client.currentScreen instanceof GenericContainerScreen menu) {
                String title = menu.getTitle().getString().toLowerCase();
                if (title.contains("аукцион") || title.contains("auction") || title.contains("поиск") || title.contains("search")) {
                    if (!isBuying) scanAndRefresh(client, menu);
                } 
                else if (title.contains("подтверждение") || title.contains("покупка")) {
                    confirmPurchase(client, menu);
                }
            }
        });
    }

    private void parseSmartCommand(String input) {
        try {
            // Формат: Название, чары чары чары цена
            String[] firstSplit = input.split(",", 2);
            
            if (firstSplit.length < 2) {
                // Если нет запятой вообще (простой поиск предмета по цене)
                String[] parts = input.trim().split("\\s+");
                targetName = parts[0].toLowerCase();
                maxPrice = Long.parseLong(parts[parts.length - 1]);
                targetEnchants.clear();
            } else {
                // Название — всё до первой запятой
                targetName = firstSplit[0].trim().toLowerCase();
                
                // Вторая часть — чары и цена
                String remaining = firstSplit[1].trim();
                String[] parts = remaining.split("\\s+");
                
                // Цена — всегда последнее слово
                maxPrice = Long.parseLong(parts[parts.length - 1]);
                
                targetEnchants.clear();
                // Всё остальное между запятой и ценой — это чары
                StringBuilder currentEnchant = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    String part = parts[i].toLowerCase();
                    
                    // Если часть - это число (уровень чар), приклеиваем к текущей чаре и сохраняем
                    if (part.matches("\\d+")) {
                        currentEnchant.append(" ").append(part);
                        targetEnchants.add(convertDigitsToRoman(currentEnchant.toString().trim()));
                        currentEnchant = new StringBuilder();
                    } else {
                        // Если это слово (название чары)
                        if (currentEnchant.length() > 0) {
                            // Если предыдущее слово не закончилось числом, значит это была чара 1 уровня без цифры
                            targetEnchants.add(currentEnchant.toString().trim());
                            currentEnchant = new StringBuilder();
                        }
                        currentEnchant.append(part);
                    }
                }
                // Если осталась чара без уровня в конце
                if (currentEnchant.length() > 0) targetEnchants.add(currentEnchant.toString().trim());
            }

            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fНастроено!"), false);
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§7Предмет: §a" + targetName), false);
            if (!targetEnchants.isEmpty()) MinecraftClient.getInstance().player.sendMessage(Text.literal("§7Ищу чары: §e" + targetEnchants), false);
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§7Макс. цена: §6" + maxPrice + "$"), false);
            
        } catch (Exception e) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cОшибка! Формат: .b Название, чары уровень чары уровень цена"), false);
        }
    }

    private String convertDigitsToRoman(String input) {
        String[] roman = {"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
        for (int i = 10; i >= 1; i--) {
            if (input.endsWith(" " + i)) {
                return input.substring(0, input.length() - String.valueOf(i).length()).trim() + " " + roman[i-1];
            }
        }
        return input;
    }

    private void scanAndRefresh(MinecraftClient client, GenericContainerScreen menu) {
        for (int i = 0; i < 45; i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (stack.getName().getString().toLowerCase().contains(targetName) && checkLore(stack)) {
                executeClick(client, menu, i);
                return;
            }
        }

        if (!isBuying && System.currentTimeMillis() - lastRefreshTime > 1300) {
            client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 49, 0, SlotActionType.PICKUP, client.player);
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    private boolean checkLore(ItemStack stack) {
        var loreComp = stack.get(DataComponentTypes.LORE);
        if (loreComp == null) return targetEnchants.isEmpty();
        String lore = loreComp.toString().toLowerCase();

        for (String enchant : targetEnchants) {
            if (!lore.contains(enchant)) return false;
        }

        if (lore.contains("$")) {
            try {
                String pStr = lore.substring(lore.lastIndexOf("$") + 1).replaceAll("[^0-9]", "");
                return Long.parseLong(pStr) <= maxPrice;
            } catch (Exception e) { return false; }
        }
        return false;
    }

    private void executeClick(MinecraftClient client, GenericContainerScreen menu, int slot) {
        isBuying = true;
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(600, 900));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
            } catch (Exception ignored) {}
        }).start();
    }

    private void confirmPurchase(MinecraftClient client, GenericContainerScreen menu) {
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(300, 500));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 10, 0, SlotActionType.PICKUP, client.player);
                Thread.sleep(1500);
                isBuying = false;
            } catch (Exception ignored) {}
        }).start();
    }

    // GUI Оставлено без изменений для стабильности
    public static class AutoBuyMenu extends Screen {
        public AutoBuyMenu() { super(Text.literal("AutoBuy")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x85000000);
            int x = width/2 - 80, y = height/2 - 40;
            ctx.fill(x, y, x + 160, y + 80, 0xFF101010);
            ctx.drawBorder(x, y, 160, 80, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bFT-AUTOBUY", width/2, y + 15, -1);
            boolean h = mx >= x + 30 && mx <= x + 130 && my >= y + 45 && my <= y + 65;
            ctx.fill(x + 30, y + 45, x + 130, y + 65, h ? 0xFF333333 : 0xFF222222);
            ctx.drawCenteredTextWithShadow(client.textRenderer, autoBuyActive ? "§aON" : "§cOFF", width/2, y + 51, -1);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            int x = width/2 - 80, y = height/2 - 40;
            if (mx >= x + 30 && mx <= x + 130 && my >= y + 45 && my <= y + 65) {
                autoBuyActive = !autoBuyActive;
                return true;
            }
            return super.mouseClicked(mx, my, b);
        }
    }
}

