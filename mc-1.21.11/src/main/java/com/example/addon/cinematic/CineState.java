package com.example.addon.cinematic;

/** Extra fields on LivingEntityRenderState (added by a mixin) so the renderer and model know about cinematic deaths. */
public interface CineState {
    /** Seconds into the death cinematic (or the angel's life), -1 when none. */
    float larper$time();

    void larper$setTime(float time);

    boolean larper$angel();

    void larper$setAngel(boolean angel);

    /** Wings module on you: animation time in seconds, or -1 when off. */
    float larper$wingTime();

    void larper$setWingTime(float time);

    float larper$wingSize();

    boolean larper$wingHalo();

    /** ARGB tints for the wings and halo (white = their own colours). */
    int larper$wingColor();

    int larper$haloColor();

    void larper$setWings(float time, float size, boolean halo, int wingColor, int haloColor);
}
