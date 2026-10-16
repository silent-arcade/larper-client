package com.example.addon.mixin;

import com.example.addon.modules.CinematicDeath;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin {
    @Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true)
    private void larper$holdBody(int entityId, Entity.RemovalReason reason, CallbackInfo ci) {
        CinematicDeath cd = Modules.get() == null ? null : Modules.get().get(CinematicDeath.class);
        if (cd != null && cd.hold(entityId, reason)) ci.cancel();
    }
}
