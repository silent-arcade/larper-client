package com.example.addon.mixin;

import com.example.addon.modules.NoFog;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public abstract class FogRendererLarperMixin {
    @Unique private static final String WRITE = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";
    @Unique private static final float FAR = 1_000_000f;

    @Unique private static NoFog larper$noFog() {
        return Modules.get() == null ? null : Modules.get().get(NoFog.class);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float larper$environmentalStart(float v) {
        NoFog m = larper$noFog();
        return m != null && m.clearWorldFog() ? FAR : v;
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float larper$environmentalEnd(float v) {
        NoFog m = larper$noFog();
        return m != null && m.clearWorldFog() ? FAR + 1 : v;
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float larper$distanceStart(float v) {
        NoFog m = larper$noFog();
        return m != null && m.clearDistanceFog() ? FAR : v;
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float larper$distanceEnd(float v) {
        NoFog m = larper$noFog();
        return m != null && m.clearDistanceFog() ? FAR + 1 : v;
    }
}
