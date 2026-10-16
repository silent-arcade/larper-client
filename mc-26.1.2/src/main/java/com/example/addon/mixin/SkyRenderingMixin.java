package com.example.addon.mixin;

import com.example.addon.modules.CustomSky;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRenderingMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void larper$hideSunMoon(CallbackInfo ci) {
        CustomSky sky = Modules.get() == null ? null : Modules.get().get(CustomSky.class);
        if (sky != null && sky.hidesSunMoon()) ci.cancel();
    }
}
