package com.nebula.client.rendering;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.nebula.client.NebulaClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/** All custom GPU pipelines and the vertex format used by the SDF shape engine. */
public final class NebulaPipelines {
	public static final VertexFormatElement RECT_DATA = registerElement(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
	public static final VertexFormatElement BORDER_COLOR = registerElement(VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR, 4);
	public static final VertexFormatElement SHAPE_PARAMS = registerElement(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
		.add("Position", VertexFormatElement.POSITION)
		.add("Color", VertexFormatElement.COLOR)
		.add("UV0", VertexFormatElement.UV0)
		.add("RectData", RECT_DATA)
		.add("BorderColor", BORDER_COLOR)
		.add("ShapeParams", SHAPE_PARAMS)
		.build();

	public static final RenderPipeline SHAPE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(id("pipeline/shape"))
		.withVertexShader(id("core/shape"))
		.withFragmentShader(id("core/shape"))
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	public static final RenderPipeline GLASS = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(id("pipeline/glass"))
		.withVertexShader(id("core/shape"))
		.withFragmentShader(id("core/glass"))
		.withSampler("Sampler0")
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	public static final RenderPipeline GAUSSIAN = post("gaussian", true).build();
	public static final RenderPipeline DOWNSAMPLE = post("downsample", false).build();
	public static final RenderPipeline COMPOSITE_ADD = post("composite", true).withBlend(BlendFunction.ADDITIVE).build();

	public static final RenderPipeline ORB = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(id("pipeline/orb"))
		.withVertexShader(id("core/orb"))
		.withFragmentShader(id("core/orb"))
		.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
		.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
		.withDepthWrite(true)
		.withCull(false)
		.build();

	/** Same shading, rendered into the emissive buffer against the scene depth without writing it. */
	public static final RenderPipeline ORB_EMISSIVE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(id("pipeline/orb_emissive"))
		.withVertexShader(id("core/orb"))
		.withFragmentShader(id("core/orb"))
		.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
		.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
		.withDepthWrite(false)
		.withCull(false)
		.build();

	public static final RenderPipeline ORB_HALO = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(id("pipeline/orb_halo"))
		.withVertexShader(id("core/orb_halo"))
		.withFragmentShader(id("core/orb_halo"))
		.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
		.withBlend(BlendFunction.ADDITIVE)
		.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
		.withDepthWrite(false)
		.withCull(false)
		.build();

	public static final RenderPipeline TRAIL = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(id("pipeline/trail"))
		.withVertexShader(id("core/trail"))
		.withFragmentShader(id("core/trail"))
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
		.withBlend(BlendFunction.ADDITIVE)
		.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
		.withDepthWrite(false)
		.withCull(false)
		.build();

	public static final List<RenderPipeline> ALL = List.of(SHAPE, GLASS, GAUSSIAN, DOWNSAMPLE, COMPOSITE_ADD, ORB, ORB_EMISSIVE, ORB_HALO, TRAIL);

	private static boolean verified;

	private NebulaPipelines() {
	}

	/**
	 * Compiles every pipeline once and logs the result, so a broken shader is reported instead of silently drawing nothing.
	 * Must only run after the resource reload, otherwise shader sources are not available yet.
	 */
	public static void verifyOnce() {
		if (verified || net.minecraft.client.Minecraft.getInstance().getOverlay() != null) {
			return;
		}
		verified = true;
		int ok = 0;
		for (RenderPipeline pipeline : ALL) {
			if (RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
				ok++;
			} else {
				NebulaClient.LOGGER.error("[Nebula] Shader pipeline {} failed to compile", pipeline.getLocation());
			}
		}
		NebulaClient.LOGGER.info("[Nebula] GPU pipelines compiled: {}/{}", ok, ALL.size());
	}

	private static RenderPipeline.Builder post(String name, boolean configUniform) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(id("pipeline/post_" + name))
			.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
			.withFragmentShader(id("post/" + name))
			.withSampler("InSampler")
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES);
		if (configUniform) {
			builder.withUniform("BlurConfig", UniformType.UNIFORM_BUFFER);
		}
		return builder;
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NebulaClient.MOD_ID, path);
	}

	/** Other mods (e.g. shader loaders) also register elements, so take the highest free slot instead of a fixed id. */
	private static VertexFormatElement registerElement(VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
		for (int id = VertexFormatElement.MAX_COUNT - 1; id > 6; id--) {
			if (VertexFormatElement.byId(id) == null) {
				return VertexFormatElement.register(id, 0, type, usage, count);
			}
		}
		throw new IllegalStateException("No free vertex format element slot for Nebula");
	}
}
