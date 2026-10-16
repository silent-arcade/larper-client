package com.example.addon.mixin;

import com.example.addon.aura.HandTint;
import com.example.addon.knife.KnifeRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateMixin {
    // Right after the item's display transform, coordinates are model units: apply the knife part's transform there
    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V", shift = At.Shift.AFTER))
    private void larper$applyKnifePart(PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outline, CallbackInfo ci) {
        if (KnifeRender.partMatrix != null) matrices.last().mulPose(KnifeRender.partMatrix);
    }

    // Hand aura: tint every quad of the first-person held item with the aura color
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"))
    private void larper$tintItem(SubmitNodeCollector queue, PoseStack matrices, ItemDisplayContext ctx, int light, int overlay, int outline,
                                 int[] tints, List<BakedQuad> quads, ItemStackRenderState.FoilType glint, Operation<Void> original) {
        int color = HandTint.itemColor;
        if (color == 0) original.call(queue, matrices, ctx, light, overlay, outline, tints, quads, glint);
        else original.call(queue, matrices, ctx, light, overlay, outline, new int[]{color}, HandTint.tintable(quads), glint);
    }
}
