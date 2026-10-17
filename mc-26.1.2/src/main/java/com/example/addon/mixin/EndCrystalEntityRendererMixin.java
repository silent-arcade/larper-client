package com.example.addon.mixin;

import com.example.addon.modules.CrystalFX;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Crystal FX: a theme-coloured outline around every crystal and the crystal itself recoloured
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("TAIL"))
    private void larper$outline(EndCrystal crystal, EndCrystalRenderState state, float delta, CallbackInfo ci) {
        CrystalFX fx = Modules.get().get(CrystalFX.class);
        if (fx != null && fx.isActive() && fx.outline()) state.outlineColor = fx.colorFor((int) (crystal.getX() * 7 + crystal.getZ() * 13)) | 0xFF000000;
    }

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private <S> void larper$tint(SubmitNodeCollector queue, Model<? super S> model, S state, PoseStack matrices, Identifier texture, int light, int overlay, int outline,
                                 ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
        CrystalFX fx = Modules.get().get(CrystalFX.class);
        if (fx == null || !fx.isActive() || !fx.recolor()) {
            original.call(queue, model, state, matrices, texture, light, overlay, outline, crumbling);
            return;
        }
        int tint = state instanceof EndCrystalRenderState s ? fx.colorFor((int) (s.x * 7 + s.z * 13)) : fx.colorFor(0);
        queue.submitModel(model, state, matrices, model.renderType(texture), light, overlay, tint | 0xFF000000, null, outline, crumbling);
    }
}
