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
    private ItemStackRenderState larper$item; // created on first use: mixin field initialisers are not reliable here

    @Unique
    private ItemStackRenderState larper$item() {
        if (larper$item == null) larper$item = new ItemStackRenderState();
        return larper$item;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void larper$cinematicState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        CineState cs = (CineState) state;
        cs.larper$setTime(-1);
        cs.larper$setAngel(false);
        cs.larper$setWingTime(-1);
        com.example.addon.modules.Wings wings = Modules.get().get(com.example.addon.modules.Wings.class);
        // The Wings module draws its wings itself; only its halo is drawn here, on the player model
        if (wings != null && wings.isActive() && wings.halo() && entity == Minecraft.getInstance().player) {
            cs.larper$setWings(wings.time(), 1, true, -1, wings.haloColor());
        }
        CinematicDeath cd = Modules.get().get(CinematicDeath.class);
        if (cd == null || !cd.isActive()) return;

        if (entity instanceof AngelEntity angel) {
            float t = angel.time(tickDelta);
            cs.larper$setTime(t);
            cs.larper$setAngel(true);
            return; // the rest of the angel's look is set in PlayerEntityRendererMixin, after the player renderer
        }

        float t = cd.time(entity);
        if (t < 0) return;
        cs.larper$setTime(t);
        state.bodyRot = cd.deathYaw(entity); // the body stays the way it fell, no spinning
        CinePose.state(state, t);
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void larper$cinematicBody(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        CineState cs = (CineState) state;
        if (cs.larper$time() >= 0 && !cs.larper$angel()) CinePose.body(state, matrices, cs.larper$time());
    }

    // Right after the body model: the knife stuck in it, or the angel's halo. Same matrices, so they move with the body.
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", shift = At.Shift.AFTER))
    private void larper$cinematicExtras(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
        CineState cs = (CineState) state;
        if (cs.larper$wingTime() >= 0) { // Wings module on you
            // Halo colour via the same per-item tint Hand Aura uses (multiplies the texture: white = its own gold)
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

        // Model space here: y points down from the neck, the face is toward -z, the victim's right is -x
        boolean biped = state instanceof HumanoidRenderState;
        float push = Math.min(1, t / 0.12f); // it goes in fast
        float y = biped ? 0.385f : 1.501f - state.boundingBoxHeight * 0.6f;
        // Enters the front of the belly at the liver, angled down; the tip stops just short of the back
        float z = -(biped ? 0.22f : state.boundingBoxWidth * 0.5f + 0.1f) - 0.25f * (1 - push);
        matrices.pushPose();
        matrices.translate(biped ? -0.12f : -0.08f, y, z);
        matrices.mulPose(Axis.YP.rotationDegrees(-15));
        matrices.mulPose(Axis.XP.rotationDegrees(40)); // blade points back into the belly and down
        matrices.mulPose(Axis.ZP.rotationDegrees(45));
        matrices.scale(0.75f, 0.75f, 0.75f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemModelResolver().updateForTopItem(larper$item(), stack, ItemDisplayContext.NONE, mc.level, null, 0);
        larper$item().submit(matrices, queue, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        matrices.popPose();
    }

    @Unique
    private static ItemStack HALO; // created on first use: 26.1 items can't be made before the game has loaded them

    @Unique
    private ItemStackRenderState larper$wing;
    @Unique
    private static ItemStack WING;

    // Two feathered wings from the shoulder blades, spread back and slowly beating. Model space: y down, back is -z.
    @Unique
    private void larper$wings(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, float t, float size) {
        double phase = t * 2.6;
        float beat = (float) (Math.sin(phase) + 0.3 * Math.sin(2 * phase)); // quick downstroke, slow lift
        Minecraft mc = Minecraft.getInstance();
        if (larper$wing == null) larper$wing = new ItemStackRenderState();
        if (WING == null) WING = com.example.addon.knife.KnifeType.modelStack("angel_wing");
        mc.getItemModelResolver().updateForTopItem(larper$wing, WING, ItemDisplayContext.NONE, mc.level, null, 0);
        for (int side = -1; side <= 1; side += 2) {
            matrices.pushPose();
            // Spread wide (12-32 degrees back from straight out) and rooted 0.3 behind the body's centre line (the back is
            // at 0.125, arms swing to about 0.25), so even fully spread nothing reaches the body or the arms
            float back = 22 - 10 * Math.max(-1, Math.min(1, beat / 1.3f));
            matrices.translate(-side * 0.11f, 0.06f, 0.3f);
            matrices.mulPose(Axis.YP.rotationDegrees(side > 0 ? 180 + back : -back));
            matrices.mulPose(Axis.ZP.rotationDegrees(180 + 12 * beat));         // feathers hang down
            matrices.mulPose(Axis.XP.rotationDegrees(-12));
            matrices.scale(size, size, size);
            matrices.translate(0.4375f, -0.375f, 0); // the wing's shoulder joint (1, 14) to the origin
            larper$wing.submit(matrices, queue, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor); // glows with the body
            matrices.popPose();
        }
    }

    // A golden ring floating above the head (an item model, lit at full brightness), slowly turning
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
}
