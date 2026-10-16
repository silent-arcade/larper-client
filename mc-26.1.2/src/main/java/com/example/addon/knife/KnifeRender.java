package com.example.addon.knife;

import org.joml.Matrix4f;

/** Hands the current knife part's model-space transform from the held-item renderer to the item layer renderer. */
public final class KnifeRender {
    /** Non-null only while a knife part is being rendered (render thread only). */
    public static Matrix4f partMatrix;

    private KnifeRender() {
    }
}
