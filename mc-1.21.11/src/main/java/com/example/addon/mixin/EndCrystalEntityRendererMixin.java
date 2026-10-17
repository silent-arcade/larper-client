package com.example.addon.mixin;

import com.example.addon.modules.CrystalFX;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Crystal FX: a theme-coloured outline around every crystal and the crystal itself recoloured
@Mixin(EndCrystalEntityRenderer.class)
public abstract class EndCrystalEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V", at = @At("TAIL"))
    private void larper$outline(EndCrystalEntity crystal, EndCrystalEntityRenderState state, float delta, CallbackInfo ci) {
        CrystalFX fx = Modules.get().get(CrystalFX.class);
        if (fx != null && fx.isActive() && fx.outline()) state.outlineColor = fx.colorFor((int) (crystal.getX() * 7 + crystal.getZ() * 13)) | 0xFF000000;
    }

    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private <S> void larper$tint(OrderedRenderCommandQueue queue, Model<? super S> model, S state, MatrixStack matrices, RenderLayer layer, int light, int overlay, int outline,
                                 ModelCommandRenderer.CrumblingOverlayCommand crumbling, Operation<Void> original) {
        CrystalFX fx = Modules.get().get(CrystalFX.class);
        if (fx == null || !fx.isActive() || !fx.recolor()) {
            original.call(queue, model, state, matrices, layer, light, overlay, outline, crumbling);
            return;
        }
        int tint = state instanceof EndCrystalEntityRenderState s ? fx.colorFor((int) (s.x * 7 + s.z * 13)) : fx.colorFor(0);
        queue.submitModel(model, state, matrices, layer, light, overlay, tint | 0xFF000000, null, outline, crumbling);
    }
}
