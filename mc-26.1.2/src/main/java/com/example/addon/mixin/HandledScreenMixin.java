package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    private Slot getHoveredSlot(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void larper$drawKnifeInSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.showsIn(slot)) context.item(knife.stack(), slot.x, slot.y);
    }

    @Inject(method = "extractCarriedItem", at = @At("RETURN"))
    private void larper$drawKnifeOnCursor(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.isActive() && knife.isOnCursor()) context.item(knife.stack(), mouseX - 8, mouseY - 8);
    }

    // Clicks on the knife are handled locally and never reach the server
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void larper$clickKnife(Slot slot, int slotId, int button, ContainerInput action, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.onSlotClick(slot, action)) ci.cancel();
    }

    // Drag-and-drop: releasing over another slot puts the knife there
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void larper$releaseKnife(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.onRelease(getHoveredSlot(click.x(), click.y()))) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void larper$closed(CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null) knife.cancelDrag();
    }
}
