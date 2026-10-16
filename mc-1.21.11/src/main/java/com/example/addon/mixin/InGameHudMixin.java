package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void larper$drawViewmodel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.isActive()) knife.renderViewmodel(context);
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void larper$drawKnife(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null) return;
        for (int i = 0; i < 9; i++) {
            if (!knife.showsInHotbar(i)) continue;
            int x = context.getScaledWindowWidth() / 2 - 90 + i * 20 + 2;
            int y = context.getScaledWindowHeight() - 16 - 3;
            context.drawItem(knife.stack(), x, y);
        }
    }

    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void larper$hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        var cc = Modules.get().get(com.example.addon.modules.CustomCrosshair.class);
        if (cc != null && cc.replacesVanilla()) ci.cancel();
    }
}
