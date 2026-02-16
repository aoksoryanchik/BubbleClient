package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // Используем ModifyVariable для прямого изменения пакета перед отправкой
    @ModifyVariable(method = "sendPacket", at = @At("HEAD"), argsOnly = true)
    private net.minecraft.network.packet.Packet<?> modifyPacket(net.minecraft.network.packet.Packet<?> packet) {
        if (ExampleMod.killaura && ExampleMod.targetFound && packet instanceof PlayerMoveC2SPacket movePacket) {
            try {
                PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) movePacket;
                // Устанавливаем серверные углы для обхода AresMine/MainBlaze [cite: 2026-02-08]
                accessor.setYaw(ExampleMod.serverYaw);
                accessor.setPitch(ExampleMod.serverPitch);
            } catch (Exception ignored) {}
        }
        return packet;
    }
}
