package com.example.addon.mixin;

import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// OreSim reads the height range an ore generates in
@Mixin(HeightRangePlacementModifier.class)
public interface HeightRangePlacementModifierAccessor {
    @Accessor("height")
    HeightProvider larper$getHeight();
}
