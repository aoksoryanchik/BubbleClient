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

    @Override
    public void onInitialize() {
        // Открытие меню на J
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_J) && client.currentScreen == null) {
                client.setScreen(new AutoBuyMenu());
            }

            // Сканирование AH (только если открыт сундук/меню аукциона)
            if (autoBuyActive && client.currentScreen instanceof GenericContainerScreen menu) {
                if (!isBuying) scanAuction(client, menu);
            }
        });

        // Команда в чате: .b Название Чары Цена
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
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fНастроено: §a" + targetName + " §fдо §6" + maxPrice + "$"), false);
        } catch (Exception e) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cОшибка! Используй: .b Название Чары Цена"), false);
        }
    }

    private void scanAuction(MinecraftClient client, GenericContainerScreen menu) {
        // Проходим по слотам (обычно в AH это первые 45 слотов)
        for (int i = 0; i < 54; i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            String itemName = stack.getName().getString().toLowerCase();
            
            // Если название совпадает
            if (itemName.contains(targetName)) {
                if (checkLoreAndPrice(stack)) {
                    executePurchase(client, menu, i);
                    break;
                }
            }
        }
    }

    private boolean checkLoreAndPrice(ItemStack stack) {
        if (!stack.hasNbt()) return false;
        
        // Получаем весь лор предмета
        String loreData = stack.getNbt().getCompound("display").get("Lore").toString().toLowerCase();

        // 1. Проверяем чары (должны быть все из списка)
        for (String enchant : targetEnchants) {
            if (!loreData.contains(enchant)) return false;
        }

        // 2. Ищем цену на FunTime (формат обычно: Цена: $ 1,000,000)
        long price = -1;
        try {
            // Ищем индекс знака доллара или слова "Цена"
            if (loreData.contains("$")) {
                String pricePart = loreData.substring(loreData.lastIndexOf("$") + 1);
                // Очищаем от мусора, оставляем только цифры
                String cleanPrice = pricePart.replaceAll("[^0-9]", "");
                if (!cleanPrice.isEmpty()) price = Long.parseLong(cleanPrice);
            }
        } catch (Exception ignored) {}

        return price != -1 && price <= maxPrice;
    }

    private void executePurchase(MinecraftClient client, GenericContainerScreen menu, int slot) {
        isBuying = true;
        
        // Рандомная задержка 1200 - 1600 мс (античит FunTime не спалит)
        long delay = ThreadLocalRandom.current().nextLong(1200, 1600);
        
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                // Первый клик по предмету на аукционе
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
                
                // Задержка перед подтверждением (имитация движения мышки в центр)
                Thread.sleep(400);
                
                // На FunTime подтверждение обычно в слотах 11 и 15 (зеленое стекло)
                // Кликаем по слоту подтверждения (проверь в игре, обычно это 11 или 13)
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 11, 0, SlotActionType.PICKUP, client.player);
                
                client.player.sendMessage(Text.literal("§b[AutoBuy] §aПопытка покупки совершена!"), false);
                
                Thread.sleep(1000); // Кулдаун чтобы не спамить
                isBuying = false;
            } catch (InterruptedException e) {
                isBuying = false;
            }
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

