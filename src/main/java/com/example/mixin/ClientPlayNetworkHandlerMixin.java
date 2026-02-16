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

    // Используем маппинг, который подходит специально для 1.21.4
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket movePacket) {
            try {
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                // Устанавливаем углы для обхода античитов [cite: 2026-02-08]
                accessor.setYaw(ExampleMod.serverYaw);
                accessor.setPitch(ExampleMod.serverPitch);
            } catch (Exception ignored) {
                // Предотвращаем краш при ошибке каста
            }
        }
    }
}
