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

    /**
     * Перехватываем отправку пакетов движения.
     * Если киллаура нашла цель, подменяем Yaw и Pitch в пакете на плавные (serverYaw/Pitch).
     */
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket movePacket) {
            // Используем интерфейс-аксессор для изменения приватных полей пакета
            PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
            
            accessor.setYaw(ExampleMod.serverYaw);
            accessor.setPitch(ExampleMod.serverPitch);
            
            // Теперь сервер получит плавные ротации, а твой прицел (от 1 лица) останется на месте
        }
    }
}
