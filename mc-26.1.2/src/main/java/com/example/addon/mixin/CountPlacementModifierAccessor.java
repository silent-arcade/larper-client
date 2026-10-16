package com.example.addon.mixin;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CountPlacement.class)
public interface CountPlacementModifierAccessor {
    @Accessor("count")
    IntProvider larper$getCount();
}
