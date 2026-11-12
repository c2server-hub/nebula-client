package com.nebula.client.rendering;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nebula.client.mixin.BufferBuilderAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

/**
 * One SDF quad submitted into vanilla's deferred GUI render state, so it is batched, layered and
 * scissored exactly like vanilla widgets and text. Shape parameters travel as vertex attributes,
 * letting many differently shaped elements share a single draw call.
 */
public final class ShapeElement implements GuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup textures;
	private final Matrix3x2f pose;
	private final @Nullable ScreenRectangle scissor;
	private final @Nullable ScreenRectangle bounds;
	private final float cx, cy, halfW, halfH, radius, borderWidth, softness, intensity, hollow, opacity;
	private final float qx0, qy0, qx1, qy1;
	private final int fillTL, fillTR, fillBR, fillBL, borderTL, borderTR, borderBR, borderBL;

	private ShapeElement(Builder b, Matrix3x2f pose, @Nullable ScreenRectangle scissor) {
		this.pipeline = b.pipeline;
		this.textures = b.textures;
		this.pose = pose;
		this.scissor = scissor;
		this.cx = b.x + b.w * 0.5F;
		this.cy = b.y + b.h * 0.5F;
		this.halfW = b.w * 0.5F;
		this.halfH = b.h * 0.5F;
		this.radius = Math.max(0, b.radius);
		this.borderWidth = b.borderWidth;
		this.softness = b.softness;
		this.intensity = b.intensity;
		this.hollow = b.hollow ? 1 : 0;
		this.opacity = globalOpacity;
		// Room for one pixel of anti-aliasing, or for the whole gaussian tail of shadows/glows.
		float expand = b.softness > 0 ? b.softness * 1.6F + 1 : 1;
		float x0 = b.x + b.w * b.segStart;
		float x1 = b.x + b.w * b.segEnd;
		this.qx0 = b.segStart <= 0 ? x0 - expand : x0;
		this.qx1 = b.segEnd >= 1 ? x1 + expand : x1;
		float y0 = b.y + b.h * b.segYStart;
		float y1 = b.y + b.h * b.segYEnd;
		this.qy0 = b.segYStart <= 0 ? y0 - expand : y0;
		this.qy1 = b.segYEnd >= 1 ? y1 + expand : y1;
		this.fillTL = b.fill[0];
		this.fillTR = b.fill[1];
		this.fillBR = b.fill[2];
		this.fillBL = b.fill[3];
		this.borderTL = b.border[0];
		this.borderTR = b.border[1];
		this.borderBR = b.border[2];
		this.borderBL = b.border[3];
		int left = (int) Math.floor(qx0);
		int top = (int) Math.floor(qy0);
		ScreenRectangle rect = new ScreenRectangle(left, top, (int) Math.ceil(qx1) - left, (int) Math.ceil(qy1) - top).transformMaxBounds(pose);
		this.bounds = scissor != null ? scissor.intersection(rect) : rect;
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		vertex(consumer, qx0, qy0, fillTL, borderTL);
		vertex(consumer, qx0, qy1, fillBL, borderBL);
		vertex(consumer, qx1, qy1, fillBR, borderBR);
		vertex(consumer, qx1, qy0, fillTR, borderTR);
	}

	private void vertex(VertexConsumer consumer, float x, float y, int fill, int border) {
		consumer.addVertexWith2DPose(pose, x, y).setColor(fill).setUv(x - cx, y - cy);
		BufferBuilderAccessor builder = (BufferBuilderAccessor) consumer;
		long rect = builder.nebula$beginElement(NebulaPipelines.RECT_DATA);
		if (rect != -1L) {
			MemoryUtil.memPutFloat(rect, halfW);
			MemoryUtil.memPutFloat(rect + 4, halfH);
			MemoryUtil.memPutFloat(rect + 8, radius);
			MemoryUtil.memPutFloat(rect + 12, borderWidth);
		}
		long color = builder.nebula$beginElement(NebulaPipelines.BORDER_COLOR);
		if (color != -1L) {
			MemoryUtil.memPutInt(color, ARGB.toABGR(border));
		}
		long params = builder.nebula$beginElement(NebulaPipelines.SHAPE_PARAMS);
		if (params != -1L) {
			MemoryUtil.memPutFloat(params, softness);
			MemoryUtil.memPutFloat(params + 4, intensity);
			MemoryUtil.memPutFloat(params + 8, hollow);
			MemoryUtil.memPutFloat(params + 12, opacity);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return pipeline;
	}

	@Override
	public TextureSetup textureSetup() {
		return textures;
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return scissor;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return bounds;
	}

	// GUI submission only happens on the render thread and each builder is consumed by submit() immediately.
	private static final Builder BUILDER = new Builder();
	private static float globalOpacity = 1;

	/** Multiplies the opacity of everything submitted until reset; used for whole-screen fade animations. */
	public static void setGlobalOpacity(float opacity) {
		globalOpacity = Math.clamp(opacity, 0, 1);
	}

	public static float globalOpacity() {
		return globalOpacity;
	}

	static Builder builder(float x, float y, float w, float h) {
		return BUILDER.reset(x, y, w, h);
	}

	static final class Builder {
		RenderPipeline pipeline;
		TextureSetup textures;
		float x, y, w, h, radius, borderWidth, softness, intensity, segStart, segEnd, segYStart, segYEnd;
		boolean hollow;
		final int[] fill = new int[4];
		final int[] border = new int[4];

		Builder reset(float x, float y, float w, float h) {
			this.pipeline = NebulaPipelines.SHAPE;
			this.textures = TextureSetup.noTexture();
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.radius = 0;
			this.borderWidth = 0;
			this.softness = 0;
			this.intensity = 1;
			this.segStart = 0;
			this.segEnd = 1;
			this.segYStart = 0;
			this.segYEnd = 1;
			this.hollow = false;
			Arrays.fill(fill, 0);
			Arrays.fill(border, 0);
			return this;
		}

		Builder pipeline(RenderPipeline pipeline, TextureSetup textures) {
			this.pipeline = pipeline;
			this.textures = textures;
			return this;
		}

		Builder radius(float radius) {
			this.radius = radius;
			return this;
		}

		Builder fill(int color) {
			return fill(color, color, color, color);
		}

		Builder fill(int tl, int tr, int br, int bl) {
			fill[0] = tl;
			fill[1] = tr;
			fill[2] = br;
			fill[3] = bl;
			return this;
		}

		Builder border(float width, int color) {
			return border(width, color, color, color, color);
		}

		Builder border(float width, int tl, int tr, int br, int bl) {
			this.borderWidth = width;
			border[0] = tl;
			border[1] = tr;
			border[2] = br;
			border[3] = bl;
			return this;
		}

		Builder soft(float softness, float intensity, boolean hollow) {
			this.softness = softness;
			this.intensity = intensity;
			this.hollow = hollow;
			return this;
		}

		Builder intensity(float intensity) {
			this.intensity = intensity;
			return this;
		}

		Builder segment(float start, float end) {
			this.segStart = start;
			this.segEnd = end;
			return this;
		}

		Builder segmentY(float start, float end) {
			this.segYStart = start;
			this.segYEnd = end;
			return this;
		}

		void submit(GuiGraphics graphics) {
			if (w <= 0 || h <= 0) {
				return;
			}
			ShapeElement element = new ShapeElement(this, new Matrix3x2f(graphics.pose()), graphics.scissorStack.peek());
			if (element.bounds != null) {
				graphics.guiRenderState.submitGuiElement(element);
			}
		}
	}
}
