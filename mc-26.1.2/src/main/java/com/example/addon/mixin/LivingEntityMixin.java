package com.example.addon.mixin;

import com.example.addon.modules.CinematicDeath;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void larper$holdPoof(byte status, CallbackInfo ci) {
        if (status != 60) return;
        CinematicDeath cd = Modules.get() == null ? null : Modules.get().get(CinematicDeath.class);
        if (cd != null && cd.isActive() && cd.inCinematic(((LivingEntity) (Object) this).getId())) ci.cancel();
    }
}
