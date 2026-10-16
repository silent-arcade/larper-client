package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {
    // Third person (F5): show the knife in your own main hand. Only your client draws this.
    @WrapOperation(method = "extractArmedEntityRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemHeldByArm(Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack larper$knifeInThirdPerson(LivingEntity entity, HumanoidArm arm, Operation<ItemStack> original) {
        ItemStack stack = original.call(entity, arm);
        if (arm != entity.getMainArm()) return stack;
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null || (!stack.isEmpty() && !knife.replaces(stack))) return stack;
        return knife.showsInThirdPerson(entity) ? knife.stack() : stack;
    }
}
