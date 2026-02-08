package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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
    private void onRenderItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!ExampleMod.isDestructed && ExampleMod.viewModel) {
            
            // Если включен пресет Animation 1
            if (ExampleMod.anim1 && hand == Hand.MAIN_HAND) {
                // 1. Отводим меч подальше и делаем чуть меньше
                matrices.translate(0.1f, -0.1f, -0.2f); // Смещение (X, Y, Z)
                matrices.scale(0.8f, 0.8f, 0.8f);      // Масштаб (меньше на 20%)

                // 2. Наклон "на себя" (Old-School Style)
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10f));

                // 3. Плавная анимация удара
                if (swingProgress > 0) {
                    float f = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
                    // Вместо резкого рывка — плавное вращение по дуге
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -35f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 20f));
                    matrices.translate(0f, f * 0.1f, 0f);
                }
            } 
            // Обычные ручные настройки (если Animation 1 выключена)
            else {
                if (hand == Hand.MAIN_HAND) {
                    matrices.translate(ExampleMod.vmX, ExampleMod.vmY, ExampleMod.vmZ);
                } else {
                    matrices.translate(ExampleMod.vmLX, ExampleMod.vmLY, ExampleMod.vmLZ);
                }

                if (swingProgress > 0) {
                    float f = (float) Math.sin(swingProgress * Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -30F));
                }
            }
        }
    }
}
