package com.example.addon.mixin;

import com.example.addon.cinematic.CineState;
import com.example.addon.modules.Wings;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingsLayer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
        at = @At("HEAD"), cancellable = true)
    private void larper$hideElytra(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!(state instanceof CineState cs) || !cs.larper$self()) return;
        Wings wings = Modules.get().get(Wings.class);
        if (wings != null && wings.hidesElytra()) ci.cancel();
    }
}
