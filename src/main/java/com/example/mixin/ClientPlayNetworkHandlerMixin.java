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

    @Inject(
        method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", 
        at = @At("HEAD"), 
        cancellable = true
    )
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        // Проверяем, включена ли киллаура и найден ли таргет в ExampleMod [cite: 2026-02-08]
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket movePacket) {
            try {
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                // Подменяем углы обзора в пакете на те, что вычислила аура [cite: 2026-02-08]
                accessor.setYaw(ExampleMod.serverYaw);
                accessor.setPitch(ExampleMod.serverPitch);
            } catch (Exception ignored) {
                // Игнорируем ошибки, чтобы игра не вылетала при неудачной попытке
            }
        }
    }
}
