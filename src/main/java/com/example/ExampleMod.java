package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoBuyMod implements ModInitializer {

    public static boolean autoBuyActive = false;
    public static String targetName = "";
    public static List<String> targetEnchants = new ArrayList<>();
    public static long maxPrice = 0;

    private boolean isBuying = false;
    private long lastRefreshTime = 0;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Меню на J
            if (InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_J) && client.currentScreen == null) {
                client.setScreen(new AutoBuyMenu());
            }

            // Логика работы в меню AH
            if (autoBuyActive && client.currentScreen instanceof GenericContainerScreen menu) {
                String title = menu.getTitle().getString().toLowerCase();
                
                // Если мы в главном меню аукциона
                if (title.contains("аукцион") || title.contains("auction")) {
                    if (!isBuying) scanAndRefresh(client, menu);
                } 
                // Если открылось окно "Подтверждение покупки" (как на твоем фото)
                else if (title.contains("подтверждение")) {
                    confirmPurchase(client, menu);
                }
            }
        });

        ClientReceiveMessageEvents.ALLOW_SEND.register(message -> {
            if (message.startsWith(".b ")) {
                parseCommand(message.substring(3));
                return false; 
            }
            return true;
        });
    }

    private void parseCommand(String input) {
        try {
            String[] parts = input.split(" ");
            maxPrice = Long.parseLong(parts[parts.length - 1]);
            targetName = parts[0].toLowerCase();
            targetEnchants.clear();
            for (int i = 1; i < parts.length - 1; i++) {
                targetEnchants.add(parts[i].toLowerCase());
            }
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fПоиск: §a" + targetName + " §fдо §6" + maxPrice + "$"), false);
        } catch (Exception e) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cОшибка! Пример: .b Меч Острота 50000"), false);
        }
    }

    private void scanAndRefresh(MinecraftClient client, GenericContainerScreen menu) {
        boolean found = false;
        // Сканируем товары (обычно слоты 0-44)
        for (int i = 0; i < 45; i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (stack.getName().getString().toLowerCase().contains(targetName) && checkLoreAndPrice(stack)) {
                executeInitialClick(client, menu, i);
                found = true;
                break;
            }
        }

        // Если не нашли и прошло 1.5 сек — жмем кнопку "Обновить" (слот 49: 6 вниз, 5 вправо)
        if (!found && !isBuying && System.currentTimeMillis() - lastRefreshTime > 1500) {
            client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 49, 0, SlotActionType.PICKUP, client.player);
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    private boolean checkLoreAndPrice(ItemStack stack) {
        if (!stack.hasNbt() || !stack.getNbt().contains("display")) return false;
        String lore = stack.getNbt().getCompound("display").get("Lore").toString().toLowerCase();

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
                // Задержка перед первым кликом (выбор товара)
                Thread.sleep(ThreadLocalRandom.current().nextLong(800, 1200));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
            } catch (Exception ignored) {}
        }).start();
    }

    private void confirmPurchase(MinecraftClient client, GenericContainerScreen menu) {
        // Кликаем по любой зеленой панели слева. Слот 10 — это центр левой зоны.
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(400, 700));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 10, 0, SlotActionType.PICKUP, client.player);
                Thread.sleep(1000);
                isBuying = false; // Сбрасываем флаг после покупки
            } catch (Exception ignored) {}
        }).start();
    }

    // --- GUI ---
    public static class AutoBuyMenu extends Screen {
        public AutoBuyMenu() { super(Text.literal("AutoBuy")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            ctx.fill(0, 0, width, height, 0x85000000);
            int x = width/2 - 80, y = height/2 - 40;
            ctx.fill(x, y, x + 160, y + 80, 0xFF101010);
            ctx.drawBorder(x, y, 160, 80, 0xFF00AAFF);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bFUNTIME AUTOBUY", width/2, y + 15, -1);
            boolean h = mx >= x + 30 && mx <= x + 130 && my >= y + 45 && my <= y + 65;
            ctx.fill(x + 30, y + 45, x + 130, y + 65, h ? 0xFF333333 : 0xFF222222);
            ctx.drawCenteredTextWithShadow(client.textRenderer, autoBuyActive ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН", width/2, y + 51, -1);
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

