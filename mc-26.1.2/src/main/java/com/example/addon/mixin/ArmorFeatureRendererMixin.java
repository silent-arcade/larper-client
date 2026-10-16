package com.example.addon.mixin;

import com.example.addon.cinematic.CineState;
import com.example.addon.modules.PlayerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void larper$hideArmor(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!(state instanceof CineState cs) || !cs.larper$self()) return;
        PlayerModel pm = Modules.get().get(PlayerModel.class);
        if (pm != null && pm.hidesArmor()) ci.cancel();
    }
}
