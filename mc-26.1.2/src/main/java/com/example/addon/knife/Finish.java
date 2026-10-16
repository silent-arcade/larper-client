package com.example.addon.knife;

/** CS2-style blade finishes. Model ids are "<knife>_<id>"; keep in sync with FINISHES in tools/gen-knife-models.js. */
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
