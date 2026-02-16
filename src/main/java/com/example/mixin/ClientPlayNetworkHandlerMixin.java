package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // В 1.21.4 мы используем такую сигнатуру, чтобы Mixin точно зацепился
    @Inject(
    method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", 
    at = @At("HEAD"), 
    cancellable = true,
    require = 1 // Это заставит билд упасть, если метод не найден, а не крашить игру потом
)
private void onSendPacket(net.minecraft.network.packet.Packet<?> packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
    // Твой код подмены углов [cite: 2026-02-08]
}
