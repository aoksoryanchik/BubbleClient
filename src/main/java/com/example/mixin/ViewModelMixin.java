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
            // ПЛАВНАЯ АНИМАЦИЯ УДАРА (как на фото)
            if (swingProgress > 0) {
                float f = (float) Math.sin(swingProgress * Math.PI);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -30F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 15F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * -20F));
            }

            if (hand == Hand.MAIN_HAND) {
                matrices.translate(ExampleMod.vmX, ExampleMod.vmY, ExampleMod.vmZ);
            } else {
                matrices.translate(ExampleMod.vmLX, ExampleMod.vmLY, ExampleMod.vmLZ);
            }
        }
    }
}
