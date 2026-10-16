package com.example.addon.mixin;

import com.example.addon.aura.HandTint;
import com.example.addon.knife.KnifeRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/json/Transformation;apply(ZLnet/minecraft/client/util/math/MatrixStack$Entry;)V", shift = At.Shift.AFTER))
    private void larper$applyKnifePart(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, int outline, CallbackInfo ci) {
        if (KnifeRender.partMatrix != null) matrices.peek().multiplyPositionMatrix(KnifeRender.partMatrix);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V"))
    private void larper$tintItem(OrderedRenderCommandQueue queue, MatrixStack matrices, ItemDisplayContext ctx, int light, int overlay, int outline,
                                 int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint, Operation<Void> original) {
        int color = HandTint.itemColor;
        if (color == 0) original.call(queue, matrices, ctx, light, overlay, outline, tints, quads, layer, glint);
        else original.call(queue, matrices, ctx, light, overlay, outline, new int[]{color}, HandTint.tintable(quads), layer, glint);
    }
}
