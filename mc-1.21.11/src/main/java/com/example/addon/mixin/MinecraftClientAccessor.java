package com.example.addon.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    /** Ticks until right-click can use/place again (vanilla sets 4 after each use). */
    @Accessor("itemUseCooldown")
    int larper$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void larper$setItemUseCooldown(int ticks);
}
