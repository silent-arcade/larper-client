package com.example.addon.mixin;

import com.example.addon.modules.SkinChanger;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin()Lnet/minecraft/entity/player/SkinTextures;", at = @At("RETURN"), cancellable = true)
    private void larper$skin(CallbackInfoReturnable<SkinTextures> cir) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        SkinChanger sc = Modules.get() == null ? null : Modules.get().get(SkinChanger.class);
        if (sc == null || !sc.isActive()) return;
        SkinTextures swapped = sc.apply(cir.getReturnValue());
        if (swapped != null) cir.setReturnValue(swapped);
    }
}
