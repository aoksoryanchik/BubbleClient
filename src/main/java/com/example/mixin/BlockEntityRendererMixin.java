package com.example.mixin;

import com.example.ExampleMod; // Путь к твоему основному классу
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderer.class)
public class BlockEntityRendererMixin {

    @Inject(method = "rendersOutsideBoundingBox", at = @At("HEAD"), cancellable = true)
    private void onRendersOutsideBoundingBox(BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        // Если функция ESP включена в твоем моде, возвращаем true
        // Это заставляет игру думать, что блок всегда нужно рендерить
        if (ExampleMod.esp) { 
            cir.setReturnValue(true);
        }
    }
}

