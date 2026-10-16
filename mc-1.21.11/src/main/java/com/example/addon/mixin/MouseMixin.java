package com.example.addon.mixin;

import com.example.addon.modules.SmoothCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void larper$smoothLook(ClientPlayerEntity player, double dx, double dy, Operation<Void> original) {
        SmoothCamera m = Modules.get().get(SmoothCamera.class);
        if (m == null || !m.isActive()) {
            original.call(player, dx, dy);
            return;
        }
        double[] d = m.filter(dx, dy);
        original.call(player, d[0], d[1]);
    }
}
