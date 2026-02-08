package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screen.TitleScreen;

@Mixin(TitleScreen.class)
public class ExampleMixin {
    // Чистый класс без инъекций, чтобы не ломать билд
}
