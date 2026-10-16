package com.example.addon.mixin;

import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// OreSim reads the 1-in-N chance of a rare ore feature
@Mixin(RarityFilterPlacementModifier.class)
public interface RarityFilterPlacementModifierAccessor {
    @Accessor("chance")
    int larper$getChance();
}
