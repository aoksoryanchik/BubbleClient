package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class ExampleMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void onRenderItem(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, net.minecraft.client.network.ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (ExampleMod.viewModelActive) {
            matrices.translate(ExampleMod.handX, ExampleMod.handY, ExampleMod.handZ);
        }
    }
}
