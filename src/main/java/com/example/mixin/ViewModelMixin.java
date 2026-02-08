package com.example.mixin;

import com.example.ExampleMod;
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
    private void onRenderItem(float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        
        if (!ExampleMod.isDestructed && ExampleMod.viewModel) {
            // Используем переменные vmLX, vmLY, vmLZ из ExampleMod
            if (hand == Hand.MAIN_HAND) {
                matrices.translate(0.45f, -0.35f, -0.7f); // Твои старые координаты
            } else {
                matrices.translate(ExampleMod.vmLX, ExampleMod.vmLY, ExampleMod.vmLZ);
            }

            // Логика Animation 1 из меню
            if (ExampleMod.anim1 && hand == Hand.MAIN_HAND) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15f));
                if (swingProgress > 0) {
                    float f = (float) Math.sin(Math.sqrt(swingProgress) * Math.PI);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -35f));
                }
            }
        }
    }
}
