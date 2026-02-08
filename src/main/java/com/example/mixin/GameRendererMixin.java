package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "loadPostProcessor", at = @At("HEAD"), cancellable = true)
    private void onBlur(Identifier id, CallbackInfo ci) {
        // Убираем размытие, если наше меню открыто
        if (id.getPath().contains("blur")) {
            ci.cancel();
        }
    }
}

