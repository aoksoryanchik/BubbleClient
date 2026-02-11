package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public class AutoBuyMod implements ModInitializer {

    // Состояние функции
    public static boolean autoBuyActive = false;
    
    // Параметры поиска
    public static String targetName = "";
    public static List<String> targetEnchants = new ArrayList<>();
    public static long maxPrice = 0;

    private long lastBuyTime = 0;
    private boolean isBuying = false;

    @Override
    public void onInitialize() {
        // 1. Открытие меню на J
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_J) && client.currentScreen == null) {
                client.setScreen(new AutoBuyMenu());
            }

            // 2. Логика сканирования аукциона
            if (autoBuyActive && client.currentScreen instanceof GenericContainerScreen menu) {
                scanAuction(client, menu);
            }
        });

        // 3. Парсинг команды из чата (.b Название Чары Цена)
        ClientReceiveMessageEvents.ALLOW_SEND.register(message -> {
            if (message.startsWith(".b ")) {
                parseCommand(message.substring(3));
                return false; // Не отправлять команду в реальный чат сервера
            }
            return true;
        });
    }

    private void parseCommand(String input) {
        try {
            // Пример: .b Незеритовый нагрудник Защита 4 500000
            String[] parts = input.split(" ");
            maxPrice = Long.parseLong(parts[parts.length - 1]);
            targetName = parts[0];
            targetEnchants.clear();
            for (int i = 1; i < parts.length - 1; i++) {
                targetEnchants.add(parts[i].toLowerCase());
            }
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§b[AutoBuy] §fИщу: §a" + targetName + " §fдо §6" + maxPrice), false);
        } catch (Exception e) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§cОшибка! Формат: .b Название Чары Цена"), false);
        }
    }

    private void scanAuction(MinecraftClient client, GenericContainerScreen menu) {
        if (isBuying) return;

        for (int i = 0; i < menu.getScreenHandler().slots.size(); i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            String itemName = stack.getName().getString();
            if (itemName.contains(targetName)) {
                if (checkLoreAndPrice(stack)) {
                    executePurchase(client, menu, i);
                    break;
                }
            }
        }
    }

    private boolean checkLoreAndPrice(ItemStack stack) {
        String fullLore = "";
        if (stack.hasNbt() && stack.getNbt().contains("display")) {
            fullLore = stack.getNbt().getCompound("display").toString().toLowerCase();
        }
        
        // Проверка чар
        for (String enchant : targetEnchants) {
            if (!fullLore.contains(enchant)) return false;
        }

        // Проверка цены (ищем число в лоре предмета)
        // Внимание: на разных серверах цена пишется по-разному, это базовый поиск числа
        long priceInLore = extractPrice(fullLore);
        return priceInLore <= maxPrice && priceInLore != -1;
    }

    private long extractPrice(String lore) {
        // Упрощенный поиск цены: ищем "Цена: 1000" или подобные вхождения
        try {
            String cleaned = lore.replaceAll("[^0-9]", " ");
            String[] nums = cleaned.trim().split("\\s+");
            return Long.parseLong(nums[nums.length - 1]);
        } catch (Exception e) { return -1; }
    }

    private void executePurchase(MinecraftClient client, GenericContainerScreen menu, int slot) {
        isBuying = true;
        
        // Задержка 1.1 - 1.5 сек для обхода античита
        long delay = ThreadLocalRandom.current().nextLong(1100, 1550);
        
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                // Клик по предмету
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
                Thread.sleep(300);
                // Клик подтверждения (обычно в центре меню появляется кнопка)
                // На многих серверах подтверждение — это слот 11 или 13
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 11, 0, SlotActionType.PICKUP, client.player);
                
                isBuying = false;
                client.player.sendMessage(Text.literal("§b[AutoBuy] §aПредмет куплен!"), false);
            } catch (InterruptedException e) { e.printStackTrace(); }
        }).start();
    }

    // --- МЕНЮ НА КЛАВИШУ J ---
    public static class AutoBuyMenu extends Screen {
        public AutoBuyMenu() { super(Text.literal("AutoBuy")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x80000000);
            int x = width/2 - 75, y = height/2 - 40;
            
            ctx.fill(x, y, x + 150, y + 80, 0xFF151515);
            ctx.drawBorder(x, y, 150, 80, 0xFF00AAFF);
            
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bAutoBuy Settings", width/2, y + 10, -1);
            
            boolean hover = mx >= x + 25 && mx <= x + 125 && my >= y + 40 && my <= y + 60;
            ctx.fill(x + 25, y + 40, x + 125, y + 60, hover ? 0xFF303030 : 0xFF202020);
            ctx.drawCenteredTextWithShadow(client.textRenderer, autoBuyActive ? "§aON" : "§cOFF", width/2, y + 46, -1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int x = width/2 - 75, y = height/2 - 40;
            if (mx >= x + 25 && mx <= x + 125 && my >= y + 40 && my <= y + 60) {
                autoBuyActive = !autoBuyActive;
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }
}

