package com.example.addon.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float larper$getFov(Camera camera, float tickProgress, boolean changingFov);

    @Invoker("setPostProcessor")
    void larper$setPostProcessor(net.minecraft.util.Identifier id);
}
