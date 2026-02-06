package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class ExampleMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onRenderItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (ExampleMod.viewModelActive) {
            // Применяем смещение из настроек меню
            matrices.translate(ExampleMod.handX, ExampleMod.handY, ExampleMod.handZ);
        }
    }
}
