package com.example.addon.mixin;

import com.example.addon.cinematic.CineState;
import com.example.addon.modules.Wings;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
        at = @At("HEAD"), cancellable = true)
    private void larper$hideElytra(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!(state instanceof CineState cs) || !cs.larper$self()) return;
        Wings wings = Modules.get().get(Wings.class);
        if (wings != null && wings.hidesElytra()) ci.cancel();
    }
}
