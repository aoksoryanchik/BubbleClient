package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.network.AbstractClientPlayerEntity; // НОВЫЙ ИМПОРТ
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class ViewModelMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onRenderItem(
        AbstractClientPlayerEntity player, // ДОБАВИЛИ ЭТОГО ПАРНЯ (это и был косяк)
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
        
        // Если деструкт не нажат и ViewModel включен
        if (!ExampleMod.isDestructed && ExampleMod.viewModel) {
            
            // Двигаем руки по координатам из главного класса
            if (hand == Hand.MAIN_HAND) {
                matrices.translate(ExampleMod.vmX, ExampleMod.vmY, ExampleMod.vmZ);
            } else {
                matrices.translate(ExampleMod.vmLX, ExampleMod.vmLY, ExampleMod.vmLZ);
            }

            // Наклон "Animation 1"
            if (ExampleMod.anim1 && hand == Hand.MAIN_HAND) {
                // Базовый поворот для крутого вида
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10f));
                
                // Анимация при ударе (swing)
                if (swingProgress > 0) {
                    float f = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -35f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 20f));
                }
            }
        }
    }
}
