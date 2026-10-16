package com.example.addon.mixin;

import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// OreSim reads the height range an ore generates in
@Mixin(HeightRangePlacement.class)
public interface HeightRangePlacementModifierAccessor {
    @Accessor("height")
    HeightProvider larper$getHeight();
}
