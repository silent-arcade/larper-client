package com.example.addon.mixin;

import com.example.addon.modules.HandAura;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import com.example.addon.cinematic.AngelEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void larper$angel(net.minecraft.entity.PlayerLikeEntity entity, net.minecraft.client.render.entity.state.PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(entity instanceof AngelEntity angel) || !(state instanceof BipedEntityRenderState b)) return;
        b.bodyYaw = angel.facing;
        b.displayName = null;
        b.pitch = -25;
        b.relativeHeadYaw = 0;
        b.limbSwingAmplitude = 0;
        b.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        b.outlineColor = 0xFFF2C6;
        b.equippedChestStack = ItemStack.EMPTY;
    }

    @WrapOperation(method = "renderArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"))
    private void larper$tintArm(OrderedRenderCommandQueue queue, ModelPart part, MatrixStack matrices, RenderLayer layer, int light, int overlay, Sprite sprite, Operation<Void> original) {
        var pm = Modules.get().get(com.example.addon.modules.PlayerModel.class);
        var avatar = pm == null ? null : pm.firstPersonAvatar();
        if (avatar != null && ((PlayerEntityRenderer) (Object) this).getModel() instanceof net.minecraft.client.render.entity.model.BipedEntityModel<?> model
            && avatar.renderArm(matrices, queue, model, part == model.rightArm, light)) return;
        HandAura aura = Modules.get().get(HandAura.class);
        int color = aura == null ? 0 : aura.handColor();
        if (color == 0) original.call(queue, part, matrices, layer, light, overlay, sprite);
        else queue.submitModelPart(part, matrices, layer, light, overlay, sprite, color, null);
    }
}
