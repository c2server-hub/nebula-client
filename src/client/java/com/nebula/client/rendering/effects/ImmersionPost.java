package com.nebula.client.rendering.effects;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.buffers.BlurConfigBuffer;
import com.nebula.client.rendering.buffers.ManagedTexture;
import net.minecraft.client.Minecraft;

import java.util.OptionalInt;

/**
 * Light post-process layers for the immersion modules (bloom + underwater refraction).
 * They read and write the main colour target only, so they work the same with Sodium or an Iris pack.
 */
public final class ImmersionPost {
    public static final ImmersionPost INSTANCE = new ImmersionPost();

    private final GaussianBlur bloomBlur = new GaussianBlur("immersion bloom");
    private final BlurConfigBuffer bloomConfig = new BlurConfigBuffer("immersion bloom composite");
    private final ManagedTexture waterCopy = new ManagedTexture("immersion underwater copy");
    private final BlurConfigBuffer waterConfig = new BlurConfigBuffer("immersion underwater");

    private ImmersionPost() {
    }

    public void bloom(float threshold, float strength, float radius) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView color = main.getColorTextureView();
        if (color == null || strength <= 0.001F) {
            return;
        }
        bloomBlur.ensureSize(main.width, main.height, true);
        bloomBlur.run(color, radius, 2);
        GpuBuffer config = bloomConfig.get(threshold, 0, strength);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(() -> "Nebula immersion bloom", color, OptionalInt.empty())) {
            pass.setPipeline(NebulaPipelines.BLOOM_COMPOSITE);
            pass.bindTexture("InSampler", bloomBlur.output(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("BlurConfig", config);
            pass.draw(0, 3);
        }
    }

    public void underwater(float time, float strength) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView color = main.getColorTextureView();
        if (color == null || strength <= 0.001F) {
            return;
        }
        waterCopy.ensureSize(main.width, main.height);
        GaussianBlur.pass(NebulaPipelines.DOWNSAMPLE, color, waterCopy.view(), null);
        GpuBuffer config = waterConfig.get(time % 600F, strength, 0);
        GaussianBlur.pass(NebulaPipelines.UNDERWATER, waterCopy.view(), color, config);
    }
}
