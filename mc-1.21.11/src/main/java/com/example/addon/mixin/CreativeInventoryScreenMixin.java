package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The creative screen overrides onMouseClick without calling super, so it needs its own hook
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin {
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void larper$clickKnife(Slot slot, int slotId, int button, SlotActionType action, CallbackInfo ci) {
        Knife knife = Modules.get().get(Knife.class);
        if (knife != null && knife.onSlotClick(slot, action)) ci.cancel();
    }
}
