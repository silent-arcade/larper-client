package com.example.addon.cinematic;

public interface CineState {
    float larper$time();

    void larper$setTime(float time);

    boolean larper$angel();

    void larper$setAngel(boolean angel);

    float larper$wingTime();

    void larper$setWingTime(float time);

    float larper$wingSize();

    boolean larper$wingHalo();

    int larper$wingColor();

    int larper$haloColor();

    void larper$setWings(float time, float size, boolean halo, int wingColor, int haloColor);

    boolean larper$self();

    void larper$setSelf(boolean self);
}
