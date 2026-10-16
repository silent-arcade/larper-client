package com.example.addon.mixin;

import com.example.addon.aura.HandTint;
import com.example.addon.knife.KnifeRender;
import com.example.addon.knife.KnifeType;
import com.example.addon.modules.HandAura;
import com.example.addon.modules.Knife;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private boolean larper$renderingParts;

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack larper$swapInKnife(ItemStack stack, @Local(argsOnly = true) Hand hand) {
        if (hand != Hand.MAIN_HAND) return stack;
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null || (!stack.isEmpty() && !knife.replaces(stack))) return stack;
        return knife.shouldHold() ? knife.stack() : stack;
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void larper$hideForViewmodel(net.minecraft.client.network.AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND) return;
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.showsViewmodel()) ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("HEAD"), cancellable = true)
    private void larper$renderKnife(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        HandAura aura = Modules.get().get(HandAura.class);
        HandTint.itemColor = ctx.isFirstPerson() && aura != null && !KnifeType.isKnife(stack) && !stack.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING) ? aura.itemColor() : 0;

        if (larper$renderingParts || !ctx.isFirstPerson() || !KnifeType.isKnife(stack)) return;
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null || !knife.isActive()) return;

        KnifeType type = knife.type();
        float[] pose = knife.pose();
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        Knife.applyHandPose(matrices, pose, left);

        larper$renderingParts = true;
        try {
            for (KnifeType.Part part : type.parts) {
                KnifeRender.partMatrix = type.partMatrix(part, pose);
                ((HeldItemRenderer) (Object) this).renderItem(entity, part.stack(knife.finish()), ctx, matrices, queue, light);
            }
        } finally {
            KnifeRender.partMatrix = null;
            HandTint.itemColor = 0;
            larper$renderingParts = false;
        }
        ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("RETURN"))
    private void larper$clearTint(CallbackInfo ci) {
        HandTint.itemColor = 0;
    }
}
