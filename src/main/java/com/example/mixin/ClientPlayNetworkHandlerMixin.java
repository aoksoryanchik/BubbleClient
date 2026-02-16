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
    
    // ИСПРАВЛЕНО: Добавлен полный дескриптор метода для устранения ошибки "Unable to determine descriptor"
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket) {
            PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) packet;
            // Устанавливаем углы вращения, которые рассчитала киллаура
            accessor.setYaw(ExampleMod.serverYaw);
            accessor.setPitch(ExampleMod.serverPitch);
        }
    }
}
