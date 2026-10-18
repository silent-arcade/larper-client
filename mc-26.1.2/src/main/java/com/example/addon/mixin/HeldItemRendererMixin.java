package com.example.addon.mixin;

import com.example.addon.aura.HandTint;
import com.example.addon.knife.KnifeRender;
import com.example.addon.knife.KnifeType;
import com.example.addon.modules.HandAura;
import com.example.addon.modules.Knife;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private boolean larper$renderingParts;

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack larper$swapInKnife(ItemStack stack, @Local(argsOnly = true) InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return stack;
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null || (!stack.isEmpty() && !knife.replaces(stack))) return stack;
        return knife.shouldHold() ? knife.stack() : stack;
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void larper$hideForViewmodel(net.minecraft.client.player.AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND) return;
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.showsViewmodel()) ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("HEAD"), cancellable = true)
    private void larper$renderKnife(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci) {
        HandAura aura = Modules.get().get(HandAura.class);
        HandTint.itemColor = ctx.firstPerson() && aura != null && !KnifeType.isKnife(stack) && !stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) ? com.example.addon.modules.ShaderHands.itemTint(aura) : 0;

        if (larper$renderingParts || !ctx.firstPerson() || !KnifeType.isKnife(stack)) return;
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
                ((ItemInHandRenderer) (Object) this).renderItem(entity, part.stack(knife.finish()), ctx, matrices, queue, light);
            }
        } finally {
            KnifeRender.partMatrix = null;
            HandTint.itemColor = 0;
            larper$renderingParts = false;
        }
        ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("RETURN"))
    private void larper$clearTint(CallbackInfo ci) {
        HandTint.itemColor = 0;
    }
}
