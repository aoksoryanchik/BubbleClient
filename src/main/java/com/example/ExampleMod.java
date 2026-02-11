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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoBuyMod implements ModInitializer {

    public static boolean autoBuyActive = false;
    public static String targetName = "";
    public static List<String> targetEnchants = new ArrayList<>();
    public static long maxPrice = 0;

    private boolean isBuying = false;
    private long lastRefreshTime = 0;

    @Override
    public void onInitialize() {
        // Перехват команды .b
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".b ")) {
                parseCommand(message.substring(3));
                return false; 
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_J) && client.currentScreen == null) {
                client.setScreen(new AutoBuyMenu());
            }

            if (autoBuyActive && client.currentScreen instanceof GenericContainerScreen menu) {
                String title = menu.getTitle().getString().toLowerCase();
                
                // Добавлена проверка на меню поиска
                if (title.contains("аукцион") || title.contains("auction") || title.contains("поиск") || title.contains("search")) {
                    if (!isBuying) scanAndRefresh(client, menu);
                } 
                else if (title.contains("подтверждение") || title.contains("покупка")) {
                    confirmPurchase(client, menu);
                }
            }
        });
    }

    private void parseCommand(String input) {
        try {
            // Регулярка для разбора: .b Название Чары (через запятую) Цена
            // Пример: .b Алмазный нагрудник Защита 4, Прочность 3 500000
            Pattern pattern = Pattern.compile("^(.*)\\s+(.*)\\s+(\\d+)$");
            Matcher matcher = pattern.matcher(input.trim());

            if (matcher.find()) {
                targetName = matcher.group(1).toLowerCase().trim();
                String enchantsPart = matcher.group(2).toLowerCase();
                maxPrice = Long.parseLong(matcher.group(3));

                targetEnchants.clear();
                // Разбиваем чары по запятой и сразу конвертируем цифры
                for (String s : enchantsPart.split(",")) {
                    targetEnchants.add(convertDigitsToRoman(s.trim()));
                }

                MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fИщу: §a" + targetName), false);
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fЧары: §e" + targetEnchants.toString()), false);
                MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fЦена до: §6" + maxPrice + "$"), false);
            }
        } catch (Exception e) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cОшибка! Пример: .b Алмазный меч Острота 5, Заговор огня 2 50000"), false);
        }
    }

    // Конвертер: преобразует "защита 5" в "защита v"
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
        boolean found = false;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString().toLowerCase();
            if (name.contains(targetName)) {
                if (checkLoreAndEnchants(stack)) {
                    executeInitialClick(client, menu, i);
                    found = true;
                    break;
                }
            }
        }

        // Авто-обновление (слот 49)
        if (!found && !isBuying && System.currentTimeMillis() - lastRefreshTime > 1300) {
            client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 49, 0, SlotActionType.PICKUP, client.player);
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    private boolean checkLoreAndEnchants(ItemStack stack) {
        var loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) return false;
        String lore = loreComponent.toString().toLowerCase();

        for (String enchant : targetEnchants) {
            if (!lore.contains(enchant)) return false;
        }

        long price = -1;
        if (lore.contains("$")) {
            try {
                String pricePart = lore.substring(lore.lastIndexOf("$") + 1);
                price = Long.parseLong(pricePart.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {}
        }
        return price != -1 && price <= maxPrice;
    }

    private void executeInitialClick(MinecraftClient client, GenericContainerScreen menu, int slot) {
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
                Thread.sleep(1200);
                isBuying = false;
            } catch (Exception ignored) {}
        }).start();
    }

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

