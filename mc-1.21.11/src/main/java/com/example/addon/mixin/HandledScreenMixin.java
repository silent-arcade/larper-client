package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    private Slot getSlotAt(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void larper$drawKnifeInSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.showsIn(slot)) context.drawItem(knife.stack(), slot.x, slot.y);
    }

    @Inject(method = "renderCursorStack", at = @At("RETURN"))
    private void larper$drawKnifeOnCursor(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.isActive() && knife.isOnCursor()) context.drawItem(knife.stack(), mouseX - 8, mouseY - 8);
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void larper$clickKnife(Slot slot, int slotId, int button, SlotActionType action, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.onSlotClick(slot, action)) ci.cancel();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void larper$releaseKnife(Click click, CallbackInfoReturnable<Boolean> cir) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.onRelease(getSlotAt(click.x(), click.y()))) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void larper$closed(CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null) knife.cancelDrag();
    }
}
