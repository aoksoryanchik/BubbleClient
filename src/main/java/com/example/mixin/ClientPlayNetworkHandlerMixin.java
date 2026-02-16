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

    // Внедряемся в любой метод, который принимает пакет. 
    // На 1.21.4 это самый безопасный способ избежать InvalidInjectionException.
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket movePacket) {
            try {
                // Используем наш аксессор для подмены углов [cite: 2026-02-08]
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                accessor.setYaw(ExampleMod.serverYaw);
                accessor.setPitch(ExampleMod.serverPitch);
            } catch (Exception ignored) {
                // Игнорируем ошибки, чтобы не крашить игру
            }
        }
    }
}
