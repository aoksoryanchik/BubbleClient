package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screen.TitleScreen;

@Mixin(TitleScreen.class)
public class ExampleMixin {
    // Оставляем пустым. Вся логика теперь в ExampleMod.java
}
