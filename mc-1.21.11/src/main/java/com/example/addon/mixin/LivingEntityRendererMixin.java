package com.example.addon.mixin;

import com.example.addon.cinematic.AngelEntity;
import com.example.addon.cinematic.CinePose;
import com.example.addon.cinematic.CineState;
import com.example.addon.modules.CinematicDeath;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Unique
    private ItemRenderState larper$item; // created on first use: mixin field initialisers are not reliable here

    @Unique
    private ItemRenderState larper$item() {
        if (larper$item == null) larper$item = new ItemRenderState();
        return larper$item;
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void larper$cinematicState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        CineState cs = (CineState) state;
        cs.larper$setTime(-1);
        cs.larper$setAngel(false);
        cs.larper$setWingTime(-1);
        com.example.addon.modules.Wings wings = Modules.get().get(com.example.addon.modules.Wings.class);
        // The Wings module draws its wings itself; only its halo is drawn here, on the player model
        if (wings != null && wings.isActive() && wings.halo() && entity == MinecraftClient.getInstance().player) {
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
        state.bodyYaw = cd.deathYaw(entity); // the body stays the way it fell, no spinning
        CinePose.state(state, t);
    }

    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void larper$cinematicBody(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        CineState cs = (CineState) state;
        if (cs.larper$time() >= 0 && !cs.larper$angel()) CinePose.body(state, matrices, cs.larper$time());
    }

    // Right after the body model: the knife stuck in it, or the angel's halo. Same matrices, so they move with the body.
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V", shift = At.Shift.AFTER))
    private void larper$cinematicExtras(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
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
        boolean biped = state instanceof BipedEntityRenderState;
        float push = Math.min(1, t / 0.12f); // it goes in fast
        float y = biped ? 0.385f : 1.501f - state.height * 0.6f;
        // Enters the front of the belly at the liver, angled down; the tip stops just short of the back
        float z = -(biped ? 0.22f : state.width * 0.5f + 0.1f) - 0.25f * (1 - push);
        matrices.push();
        matrices.translate(biped ? -0.12f : -0.08f, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-15));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(40)); // blade points back into the belly and down
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
        matrices.scale(0.75f, 0.75f, 0.75f);
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getItemModelManager().clearAndUpdate(larper$item(), stack, ItemDisplayContext.NONE, mc.world, null, 0);
        larper$item().render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
        matrices.pop();
    }

    @Unique
    private static final ItemStack HALO = com.example.addon.knife.KnifeType.modelStack("halo");

    @Unique
    private ItemRenderState larper$wing;
    @Unique
    private static final ItemStack WING = com.example.addon.knife.KnifeType.modelStack("angel_wing");

    // Two feathered wings from the shoulder blades, spread back and slowly beating. Model space: y down, back is -z.
    @Unique
    private void larper$wings(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, float t, float size) {
        double phase = t * 2.6;
        float beat = (float) (Math.sin(phase) + 0.3 * Math.sin(2 * phase)); // quick downstroke, slow lift
        MinecraftClient mc = MinecraftClient.getInstance();
        if (larper$wing == null) larper$wing = new ItemRenderState();
        mc.getItemModelManager().clearAndUpdate(larper$wing, WING, ItemDisplayContext.NONE, mc.world, null, 0);
        for (int side = -1; side <= 1; side += 2) {
            matrices.push();
            // Spread wide (12-32 degrees back from straight out) and rooted 0.3 behind the body's centre line (the back is
            // at 0.125, arms swing to about 0.25), so even fully spread nothing reaches the body or the arms
            float back = 22 - 10 * Math.max(-1, Math.min(1, beat / 1.3f));
            matrices.translate(-side * 0.11f, 0.06f, 0.3f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side > 0 ? 180 + back : -back));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180 + 12 * beat));         // feathers hang down
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12));
            matrices.scale(size, size, size);
            matrices.translate(0.4375f, -0.375f, 0); // the wing's shoulder joint (1, 14) to the origin
            larper$wing.render(matrices, queue, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, state.outlineColor); // glows with the body
            matrices.pop();
        }
    }

    // A golden ring floating above the head (an item model, lit at full brightness), slowly turning
    @Unique
    private void larper$halo(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, float t) {
        matrices.push();
        matrices.translate(0, -0.78f - (float) Math.sin(t * 2.2) * 0.025f, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 40));
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getItemModelManager().clearAndUpdate(larper$item(), HALO, ItemDisplayContext.NONE, mc.world, null, 0);
        larper$item().render(matrices, queue, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, state.outlineColor);
        matrices.pop();
    }
}
