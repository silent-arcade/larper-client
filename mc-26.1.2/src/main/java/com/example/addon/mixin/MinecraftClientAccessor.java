package com.example.addon.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("rightClickDelay")
    int larper$getItemUseCooldown();

    @Accessor("rightClickDelay")
    void larper$setItemUseCooldown(int ticks);
}
