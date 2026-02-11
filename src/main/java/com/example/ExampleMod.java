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
        // БЛОКИРОВКА И ПАРСИНГ СООБЩЕНИЙ (Чтобы не отправлялись в чат)
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".b ")) {
                parseCommand(message.substring(3));
                return false; // Это "съедает" сообщение, оно не идет на сервер
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Меню на J
            if (InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_J) && client.currentScreen == null) {
                client.setScreen(new AutoBuyMenu());
            }

            // Работа с AH
            if (autoBuyActive && client.currentScreen instanceof GenericContainerScreen menu) {
                String title = menu.getTitle().getString().toLowerCase();
                
                if (title.contains("аукцион") || title.contains("auction") || title.contains("список")) {
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

    private void scanAndRefresh(MinecraftClient client, GenericContainerScreen menu) {
        boolean found = false;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = menu.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (stack.getName().getString().toLowerCase().contains(targetName)) {
                if (checkLoreAndEnchants(stack)) {
                    executeInitialClick(client, menu, i);
                    found = true;
                    break;
                }
            }
        }

        // Кнопка обновить (слот 49)
        if (!found && !isBuying && System.currentTimeMillis() - lastRefreshTime > 1200) {
            client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 49, 0, SlotActionType.PICKUP, client.player);
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    private boolean checkLoreAndEnchants(ItemStack stack) {
        // В 1.21.4 получаем лор через компоненты
        var loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent == null) return false;
        
        String fullLore = loreComponent.toString().toLowerCase();

        // Проверка чар
        for (String enchant : targetEnchants) {
            if (!fullLore.contains(enchant)) return false;
        }

        // Поиск цены на FunTime (после знака $)
        long price = -1;
        if (fullLore.contains("$")) {
            try {
                String pricePart = fullLore.substring(fullLore.lastIndexOf("$") + 1);
                price = Long.parseLong(pricePart.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {}
        }
        return price != -1 && price <= maxPrice;
    }

    private void executeInitialClick(MinecraftClient client, GenericContainerScreen menu, int slot) {
        isBuying = true;
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(700, 1100));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, client.player);
            } catch (Exception ignored) {}
        }).start();
    }

    private void confirmPurchase(MinecraftClient client, GenericContainerScreen menu) {
        // Слот 10 — любая зеленая панель слева
        new Thread(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(350, 600));
                client.interactionManager.clickSlot(menu.getScreenHandler().syncId, 10, 0, SlotActionType.PICKUP, client.player);
                Thread.sleep(1500);
                isBuying = false;
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
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§bFT-AUTOBUY 1.21.4", width/2, y + 15, -1);
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

