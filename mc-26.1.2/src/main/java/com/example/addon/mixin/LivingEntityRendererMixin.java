package com.example.addon.mixin;

import com.example.addon.cinematic.AngelEntity;
import com.example.addon.cinematic.CinePose;
import com.example.addon.cinematic.CineState;
import com.example.addon.modules.CinematicDeath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Unique
    private ItemStackRenderState larper$item;

    @Unique
    private ItemStackRenderState larper$item() {
        if (larper$item == null) larper$item = new ItemStackRenderState();
        return larper$item;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void larper$cinematicState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        CineState cs = (CineState) state;
        cs.larper$setSelf(entity == Minecraft.getInstance().player);
        cs.larper$setTime(-1);
        cs.larper$setAngel(false);
        cs.larper$setWingTime(-1);
        com.example.addon.modules.Wings wings = Modules.get().get(com.example.addon.modules.Wings.class);
        if (wings != null && wings.isActive() && wings.halo() && entity == Minecraft.getInstance().player) {
            cs.larper$setWings(wings.time(), 1, true, -1, wings.haloColor());
        }
        com.example.addon.modules.SpinBot spin = Modules.get().get(com.example.addon.modules.SpinBot.class);
        if (spin != null && spin.isActive() && entity == Minecraft.getInstance().player) spin.apply(state);
        com.example.addon.modules.TargetStrafe strafe = Modules.get().get(com.example.addon.modules.TargetStrafe.class);
        if (strafe != null && strafe.isActive() && entity == Minecraft.getInstance().player) strafe.apply(state, tickDelta);
        CinematicDeath cd = Modules.get().get(CinematicDeath.class);
        if (cd == null || !cd.isActive()) return;

        if (entity instanceof AngelEntity angel) {
            float t = angel.time(tickDelta);
            cs.larper$setTime(t);
            cs.larper$setAngel(true);
            return;
        }

        float t = cd.time(entity);
        if (t < 0) return;
        cs.larper$setTime(t);
        state.bodyRot = cd.deathYaw(entity);
        CinePose.state(state, t);
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void larper$cinematicBody(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        CineState cs = (CineState) state;
        if (cs.larper$time() >= 0 && !cs.larper$angel()) CinePose.body(state, matrices, cs.larper$time());
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", shift = At.Shift.AFTER))
    private void larper$cinematicExtras(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
        CineState cs = (CineState) state;
        if (cs.larper$self()) {
            var pm = Modules.get().get(com.example.addon.modules.PlayerModel.class);
            var avatar = pm == null ? null : pm.avatar();
            if (avatar != null && ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel() instanceof net.minecraft.client.model.HumanoidModel<?> biped) {
                larper$pose(biped, state);
                avatar.render(matrices, queue, biped, state.lightCoords);
            }
        }
        if (cs.larper$wingTime() >= 0) {
            com.example.addon.aura.HandTint.itemColor = cs.larper$haloColor();
            if (cs.larper$wingHalo()) larper$halo(state, matrices, queue, cs.larper$wingTime());
            com.example.addon.aura.HandTint.itemColor = 0;
        }
        float t = cs.larper$time();
        if (t < 0) return;
        if (cs.larper$angel()) {
            larper$halo(state, matrices, queue, t);
            larper$wings(state, matrices, queue, t, 1.6f);
            return;
        }
        CinematicDeath cd = Modules.get().get(CinematicDeath.class);
        ItemStack stack = cd == null ? ItemStack.EMPTY : cd.swordStack();
        if (stack.isEmpty()) return;

        boolean biped = state instanceof HumanoidRenderState;
        float push = Math.min(1, t / 0.12f);
        float y = biped ? 0.385f : 1.501f - state.boundingBoxHeight * 0.6f;
        float z = -(biped ? 0.22f : state.boundingBoxWidth * 0.5f + 0.1f) - 0.25f * (1 - push);
        matrices.pushPose();
        matrices.translate(biped ? -0.12f : -0.08f, y, z);
        matrices.mulPose(Axis.YP.rotationDegrees(-15));
        matrices.mulPose(Axis.XP.rotationDegrees(40));
        matrices.mulPose(Axis.ZP.rotationDegrees(45));
        matrices.scale(0.75f, 0.75f, 0.75f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemModelResolver().updateForTopItem(larper$item(), stack, ItemDisplayContext.NONE, mc.level, null, 0);
        larper$item().submit(matrices, queue, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        matrices.popPose();
    }

    @Unique
    private static ItemStack HALO;

    @Unique
    private ItemStackRenderState larper$wing;
    @Unique
    private static ItemStack WING;

    @Unique
    private void larper$wings(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, float t, float size) {
        double phase = t * 2.6;
        float beat = (float) (Math.sin(phase) + 0.3 * Math.sin(2 * phase));
        Minecraft mc = Minecraft.getInstance();
        if (larper$wing == null) larper$wing = new ItemStackRenderState();
        if (WING == null) WING = com.example.addon.knife.KnifeType.modelStack("angel_wing");
        mc.getItemModelResolver().updateForTopItem(larper$wing, WING, ItemDisplayContext.NONE, mc.level, null, 0);
        for (int side = -1; side <= 1; side += 2) {
            matrices.pushPose();
            float back = 22 - 10 * Math.max(-1, Math.min(1, beat / 1.3f));
            matrices.translate(-side * 0.11f, 0.06f, 0.3f);
            matrices.mulPose(Axis.YP.rotationDegrees(side > 0 ? 180 + back : -back));
            matrices.mulPose(Axis.ZP.rotationDegrees(180 + 12 * beat));
            matrices.mulPose(Axis.XP.rotationDegrees(-12));
            matrices.scale(size, size, size);
            matrices.translate(0.4375f, -0.375f, 0);
            larper$wing.submit(matrices, queue, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
            matrices.popPose();
        }
    }

    @Unique
    private void larper$halo(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, float t) {
        matrices.pushPose();
        matrices.translate(0, -0.78f - (float) Math.sin(t * 2.2) * 0.025f, 0);
        matrices.mulPose(Axis.YP.rotationDegrees(t * 40));
        Minecraft mc = Minecraft.getInstance();
        if (HALO == null) HALO = com.example.addon.knife.KnifeType.modelStack("halo");
        mc.getItemModelResolver().updateForTopItem(larper$item(), HALO, ItemDisplayContext.NONE, mc.level, null, 0);
        larper$item().submit(matrices, queue, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        matrices.popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private static void larper$pose(net.minecraft.client.model.HumanoidModel model, LivingEntityRenderState state) {
        model.setupAnim(state);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void larper$hideSelf(SubmitNodeCollector queue, net.minecraft.client.model.Model<?> model, Object state, PoseStack matrices, net.minecraft.client.renderer.rendertype.RenderType layer,
                                 int light, int overlay, int color, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int outline,
                                 net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling,
                                 com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (state instanceof CineState cs && cs.larper$self()) {
            var pm = Modules.get().get(com.example.addon.modules.PlayerModel.class);
            if (pm != null && pm.hidesPlayer()) return;
        }
        original.call(queue, model, state, matrices, layer, light, overlay, color, sprite, outline, crumbling);
    }
}
