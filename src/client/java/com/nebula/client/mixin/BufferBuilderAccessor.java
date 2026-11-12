package com.nebula.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes element pointers so custom (non-vanilla) vertex attributes can be written. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
	@Invoker("beginElement")
	long nebula$beginElement(VertexFormatElement element);
}
