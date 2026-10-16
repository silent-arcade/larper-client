package com.example.addon.mixin;

import com.example.addon.modules.HandAura;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import com.example.addon.cinematic.AngelEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void larper$angel(net.minecraft.world.entity.Avatar entity, net.minecraft.client.renderer.entity.state.AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(entity instanceof AngelEntity angel) || !(state instanceof HumanoidRenderState b)) return;
        b.bodyRot = angel.facing;
        b.nameTag = null;
        b.xRot = -25;
        b.yRot = 0;
        b.walkAnimationSpeed = 0;
        b.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        b.outlineColor = 0xFFF2C6;
        b.chestEquipment = ItemStack.EMPTY;
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void larper$tintArm(SubmitNodeCollector queue, ModelPart part, PoseStack matrices, RenderType layer, int light, int overlay, TextureAtlasSprite sprite, Operation<Void> original) {
        var pm = Modules.get().get(com.example.addon.modules.PlayerModel.class);
        var avatar = pm == null ? null : pm.firstPersonAvatar();
        if (avatar != null && ((AvatarRenderer<?>) (Object) this).getModel() instanceof net.minecraft.client.model.HumanoidModel<?> model
            && avatar.renderArm(matrices, queue, model, part == model.rightArm, light)) return;
        HandAura aura = Modules.get().get(HandAura.class);
        int color = aura == null ? 0 : aura.handColor();
        if (color == 0) original.call(queue, part, matrices, layer, light, overlay, sprite);
        else queue.submitModelPart(part, matrices, layer, light, overlay, sprite, color, null);
    }
}
