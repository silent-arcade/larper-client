package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public abstract class DrawContextMixin {
    @ModifyVariable(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"), argsOnly = true)
    private ItemStack larper$knifeIcon(ItemStack stack) {
        if (stack.isEmpty() || Modules.get() == null) return stack;
        Knife knife = Modules.get().get(Knife.class);
        return knife != null && knife.replacesIcon(stack) ? knife.stack() : stack;
    }
}
