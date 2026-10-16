package com.example.addon.mixin;

import com.example.addon.cinematic.CineState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements CineState {
    @Unique
    private float larper$time = -1;
    @Unique
    private boolean larper$angel;
    @Unique
    private float larper$wingTime = -1, larper$wingSize = 1;
    @Unique
    private boolean larper$wingHalo;
    @Unique
    private int larper$wingColor = -1, larper$haloColor = -1;

    @Override
    public float larper$time() {
        return larper$time;
    }

    @Override
    public void larper$setTime(float time) {
        larper$time = time;
    }

    @Override
    public boolean larper$angel() {
        return larper$angel;
    }

    @Override
    public void larper$setAngel(boolean angel) {
        larper$angel = angel;
    }

    @Override
    public float larper$wingTime() {
        return larper$wingTime;
    }

    @Override
    public void larper$setWingTime(float time) {
        larper$wingTime = time;
    }

    @Override
    public float larper$wingSize() {
        return larper$wingSize;
    }

    @Override
    public boolean larper$wingHalo() {
        return larper$wingHalo;
    }

    @Override
    public void larper$setWings(float time, float size, boolean halo, int wingColor, int haloColor) {
        larper$wingTime = time;
        larper$wingSize = size;
        larper$wingHalo = halo;
        larper$wingColor = wingColor;
        larper$haloColor = haloColor;
    }

    @Override
    public int larper$wingColor() {
        return larper$wingColor;
    }

    @Override
    public int larper$haloColor() {
        return larper$haloColor;
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean larper$self;

    @Override
    public boolean larper$self() {
        return larper$self;
    }

    @Override
    public void larper$setSelf(boolean self) {
        larper$self = self;
    }
}
