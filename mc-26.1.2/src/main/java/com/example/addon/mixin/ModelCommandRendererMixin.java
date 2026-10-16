package com.example.addon.mixin;

import com.example.addon.cinematic.CinePose;
import com.example.addon.cinematic.CineState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Models are posed when the render queue runs, after each model (zombie arms, etc.) set its own angles
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelCommandRendererMixin {
    @WrapOperation(method = "renderModel(Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private void larper$cinematicLimbs(Model<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        if (!(model instanceof HumanoidModel<?> biped) || !(state instanceof CineState cs) || cs.larper$time() < 0) return;
        if (cs.larper$angel()) CinePose.angel(biped, cs.larper$time());
        else CinePose.limbs(biped, cs.larper$time());
    }
}
