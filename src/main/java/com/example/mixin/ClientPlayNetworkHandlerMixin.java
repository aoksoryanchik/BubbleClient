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

    // Используем универсальный селектор для 1.21.4
    @Inject(method = "*", at = @At("HEAD"), cancellable = true)
    private void onAnyPacket(Packet<?> packet, CallbackInfo ci) {
        // Проверяем, что это именно пакет движения и аура включена [cite: 2026-02-08]
        if (packet instanceof PlayerMoveC2SPacket movePacket && ExampleMod.killaura && ExampleMod.targetFound) {
            try {
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                accessor.setYaw(ExampleMod.serverYaw);
                accessor.setPitch(ExampleMod.serverPitch);
            } catch (Exception ignored) {}
        }
    }
}
