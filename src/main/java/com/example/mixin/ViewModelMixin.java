package com.example.mixin;

import com.example.ExampleMod; // Импорт твоего главного класса
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class ViewModelMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onRenderItem(float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        
        // ПРОВЕРКА: Если мод не удален и HandView включен
        if (!ExampleMod.isDestructed && ExampleMod.viewModel) {
            
            // Если в меню включена та самая "Animation 1"
            if (ExampleMod.anim1 && hand == Hand.MAIN_HAND) {
                // Тот самый наклон и уменьшение (как на скринах)
                matrices.translate(0.45f, -0.35f, -0.7f); 
                matrices.scale(0.8f, 0.8f, 0.8f);

                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10f));

                // Плавность удара
                if (swingProgress > 0) {
                    float f = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -35f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 20f));
                }
            } 
        }
    }
}
