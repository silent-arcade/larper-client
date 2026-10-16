package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    // Knife replace-items (e.g. your sword) show the knife icon in your hotbar and inventory. The count and
    // durability bar are drawn separately from the real stack, so they stay correct.
    @ModifyVariable(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"), argsOnly = true)
    private ItemStack larper$knifeIcon(ItemStack stack) {
        if (stack.isEmpty() || Modules.get() == null) return stack;
        Knife knife = Modules.get().get(Knife.class);
        return knife != null && knife.replacesIcon(stack) ? knife.stack() : stack;
    }
}
