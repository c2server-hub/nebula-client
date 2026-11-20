package com.nebula.client.rendering;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;

/**
 * Full-screen "lite post-process" layers drawn in the GUI pass with custom blend modes.
 * They only touch the finished frame, so they stack on top of Sodium / Iris instead of replacing anything.
 */
public final class ScreenFx {
    private ScreenFx() {
    }

    /** dst = dst * colour (colour grading, depth darkening). Corners: top colour / bottom colour. */
    public static void multiply(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        ShapeElement.builder(x, y, w, h)
            .pipeline(NebulaPipelines.SHAPE_MULTIPLY, TextureSetup.noTexture())
            .fill(top | 0xFF000000, top | 0xFF000000, bottom | 0xFF000000, bottom | 0xFF000000)
            .submit(g);
    }

    /** dst = dst + colour * alpha (lift, glare, light rays). */
    public static void add(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        ShapeElement.builder(x, y, w, h)
            .radius(radius)
            .pipeline(NebulaPipelines.SHAPE_ADDITIVE, TextureSetup.noTexture())
            .fill(color)
            .submit(g);
    }

    /** Additive four-corner gradient (tl, tr, br, bl). */
    public static void add(GuiGraphics g, float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        ShapeElement.builder(x, y, w, h)
            .pipeline(NebulaPipelines.SHAPE_ADDITIVE, TextureSetup.noTexture())
            .fill(tl, tr, br, bl)
            .submit(g);
    }

    /** Additive gaussian-soft blob / rectangle. */
    public static void addSoft(GuiGraphics g, float x, float y, float w, float h, float radius, float softness, int color, float intensity) {
        ShapeElement.builder(x, y, w, h)
            .radius(radius)
            .pipeline(NebulaPipelines.SHAPE_ADDITIVE, TextureSetup.noTexture())
            .fill(color)
            .soft(softness, intensity, false)
            .submit(g);
    }

    /** Additive soft shape with a four-corner gradient (used for fading light shafts). */
    public static void addSoft(GuiGraphics g, float x, float y, float w, float h, float softness, float intensity, int tl, int tr, int br, int bl) {
        ShapeElement.builder(x, y, w, h)
            .pipeline(NebulaPipelines.SHAPE_ADDITIVE, TextureSetup.noTexture())
            .fill(tl, tr, br, bl)
            .soft(softness, intensity, false)
            .submit(g);
    }
}
