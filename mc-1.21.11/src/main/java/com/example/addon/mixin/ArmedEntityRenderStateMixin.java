package com.example.addon.mixin;

import com.example.addon.modules.Knife;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {
    @WrapOperation(method = "updateRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getStackInArm(Lnet/minecraft/util/Arm;)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack larper$knifeInThirdPerson(LivingEntity entity, Arm arm, Operation<ItemStack> original) {
        ItemStack stack = original.call(entity, arm);
        if (arm != entity.getMainArm()) return stack;
        Knife knife = Modules.get().get(Knife.class);
        if (knife == null || (!stack.isEmpty() && !knife.replaces(stack))) return stack;
        return knife.showsInThirdPerson(entity) ? knife.stack() : stack;
    }
}
