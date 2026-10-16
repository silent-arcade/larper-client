package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    // CS:GO butterfly viewmodel: drawn first so the hotbar, crosshair and chat stay on top of it
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void larper$drawViewmodel(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.isActive()) knife.renderViewmodel(context);
    }

    @Inject(method = "extractItemHotbar", at = @At("TAIL"))
    private void larper$drawKnife(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null) return;
        for (int i = 0; i < 9; i++) {
            if (!knife.showsInHotbar(i)) continue;
            // Same position vanilla uses for hotbar items
            int x = context.guiWidth() / 2 - 90 + i * 20 + 2;
            int y = context.guiHeight() - 16 - 3;
            context.item(knife.stack(), x, y);
        }
    }

    // Custom Crosshair draws its own, so hide the vanilla one while it's on
    @Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void larper$hideCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        var cc = Modules.get().get(com.example.addon.modules.CustomCrosshair.class);
        if (cc != null && cc.replacesVanilla()) ci.cancel();
    }
}
