package com.example.addon.mixin;

import com.example.addon.modules.Aspect;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererAspectMixin {
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"))
    private void larper$stretch(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        Aspect aspect = Modules.get() == null ? null : Modules.get().get(Aspect.class);
        if (aspect == null || !aspect.isActive()) return;
        Matrix4f m = cir.getReturnValue();
        m.set(new Matrix4f().scaling(aspect.stretch(), 1, 1).mul(m));
    }
}
