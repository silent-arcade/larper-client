package com.example.addon.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    /** Ticks until right-click can use/place again (vanilla sets 4 after each use). */
    @Accessor("rightClickDelay")
    int larper$getItemUseCooldown();

    @Accessor("rightClickDelay")
    void larper$setItemUseCooldown(int ticks);
}
