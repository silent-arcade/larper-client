package com.example.addon.mixin;

import com.example.addon.modules.Aspect;
import com.mojang.blaze3d.ProjectionType;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 26.1 builds the world projection in Projection; stretch only the 3D (perspective) one, never the HUD
@Mixin(Projection.class)
public abstract class GameRendererAspectMixin {
    @Shadow private ProjectionType projectionType;

    @Inject(method = "getMatrix", at = @At("RETURN"))
    private void larper$stretch(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        if (projectionType != ProjectionType.PERSPECTIVE) return;
        Aspect aspect = Modules.get() == null ? null : Modules.get().get(Aspect.class);
        if (aspect == null || !aspect.isActive()) return;
        Matrix4f m = cir.getReturnValue();
        m.set(new Matrix4f().scaling(aspect.stretch(), 1, 1).mul(m));
    }
}
