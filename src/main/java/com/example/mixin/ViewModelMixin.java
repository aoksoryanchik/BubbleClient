package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class ViewModelMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onRenderItem(
        AbstractClientPlayerEntity player, // Обязательный аргумент для новых версий
        float tickDelta, 
        float pitch, 
        Hand hand, 
        float swingProgress, 
        ItemStack item, 
        float equipProgress, 
        MatrixStack matrices, 
        VertexConsumerProvider vertexConsumers, 
        int light, 
        CallbackInfo ci
    ) {
        // Проверка: если мод не удален
        if (!ExampleMod.isDestructed) {
            
            // Если рука правая — берем vmX, vmY, vmZ
            if (hand == Hand.MAIN_HAND) {
                matrices.translate(ExampleMod.vmX, ExampleMod.vmY, ExampleMod.vmZ);
            } 
            // Если рука левая — берем vmLX, vmLY, vmLZ
            else {
                matrices.translate(ExampleMod.vmLX, ExampleMod.vmLY, ExampleMod.vmLZ);
            }
            
            // Здесь можно добавить вращение или масштаб, если захочешь в будущем
        }
    }
}
