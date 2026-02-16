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

    // В 1.21.4 этот метод часто меняет маппинг. Используем прямое указание.
    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        // Проверяем включена ли киллаура и найден ли таргет в твоем ExampleMod
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket movePacket) {
            try {
                // Кастим к аксессору, чтобы подменить Yaw и Pitch без палева [cite: 2026-02-08]
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                
                accessor.setYaw(ExampleMod.serverYaw);
                accessor.setPitch(ExampleMod.serverPitch);
                
                // Для AresMine/MainBlaze: сервер получит пакет с головой на цель, 
                // а визуально ты будешь смотреть куда хочешь.
            } catch (Exception e) {
                // Если вдруг маппинги аксессора кривые, это спасет от краша
            }
        }
    }
}
