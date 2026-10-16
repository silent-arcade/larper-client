package com.example.addon.mixin;

import com.example.addon.cinematic.CinePose;
import com.example.addon.cinematic.CineState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelCommandRenderer.class)
public abstract class ModelCommandRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setAngles(Ljava/lang/Object;)V"))
    private void larper$cinematicLimbs(Model<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        if (!(model instanceof BipedEntityModel<?> biped) || !(state instanceof CineState cs) || cs.larper$time() < 0) return;
        if (cs.larper$angel()) CinePose.angel(biped, cs.larper$time());
        else CinePose.limbs(biped, cs.larper$time());
    }
}
