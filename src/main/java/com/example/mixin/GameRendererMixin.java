package com.example.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 1001)
public class GameRendererMixin {
    // Используем remap = false и несколько имен для надежности
    @Inject(method = {"loadPostProcessor", "method_3167", "loadShader"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void onBlur(Identifier id, CallbackInfo ci) {
        if (id != null && id.getPath().contains("blur")) {
            ci.cancel();
        }
    }
}
