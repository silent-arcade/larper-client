package com.example.addon.mixin;

import com.example.addon.modules.SkinChanger;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {
    // Skin Changer: swap only your own skin (third person, first-person hand, inventory)
    @Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("RETURN"), cancellable = true)
    private void larper$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        if ((Object) this != Minecraft.getInstance().player) return;
        SkinChanger sc = Modules.get() == null ? null : Modules.get().get(SkinChanger.class);
        if (sc == null || !sc.isActive()) return;
        PlayerSkin swapped = sc.apply(cir.getReturnValue());
        if (swapped != null) cir.setReturnValue(swapped);
    }
}
