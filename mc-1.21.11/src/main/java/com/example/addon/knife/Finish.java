package com.example.addon.knife;

public enum Finish {
    Vanilla(null),
    Fade("fade"),
    MarbleFade("marble_fade"),
    TigerTooth("tiger_tooth"),
    Ruby("ruby"),
    Sapphire("sapphire"),
    Emerald("emerald"),
    Night("night");

    private final String id;

    Finish(String id) {
        this.id = id;
    }

    String apply(String model) {
        return id == null ? model : model + "_" + id;
    }
}
