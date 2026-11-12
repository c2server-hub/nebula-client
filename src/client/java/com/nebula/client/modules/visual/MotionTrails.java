package com.nebula.client.modules.visual;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.world.TrailRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

public final class MotionTrails extends Module {
	private static final int CAPACITY = 256;

	public final NumberSetting duration = add(new NumberSetting("Duration", "How long the trail lingers", 1.2, 0.2, 4, 0.05, "s"));
	public final ModeSetting style = add(new ModeSetting("Style", "Ribbon shape", "Ribbon", "Ribbon", "Ground", "Halo"));
	public final NumberSetting width = add(new NumberSetting("Width", "Ribbon height/width", 0.55, 0.05, 1.8, 0.05, "m"));
	public final ModeSetting colorMode = add(new ModeSetting("Color mode", "Trail colouring", "Gradient", "Gradient", "Rainbow", "Theme"));
	public final ColorSetting headColor = add(new ColorSetting("Head color", "Colour near the player", 0xFF4D8DFF))
		.visibleWhen(() -> colorMode.is("Gradient"));
	public final ColorSetting tailColor = add(new ColorSetting("Tail color", "Colour at the end", 0xFFA45CFF))
		.visibleWhen(() -> colorMode.is("Gradient"));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", "Trail brightness", 0.85, 0.1, 1, 0.05));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Render while in first person", true));

	// Ring buffer of world positions with timestamps.
	private final double[] hx = new double[CAPACITY], hy = new double[CAPACITY], hz = new double[CAPACITY];
	private final long[] ht = new long[CAPACITY];
	private int head = -1;
	private int size;
	// Scratch arrays handed to the renderer.
	private final float[] px = new float[CAPACITY], py = new float[CAPACITY], pz = new float[CAPACITY], ages = new float[CAPACITY];

	public MotionTrails() {
		super("motion_trails", "Motion Trails", "Animated light ribbon following your movement", Category.VISUAL);
	}

	@Override
	protected void onDisable() {
		size = 0;
		head = -1;
	}

	public void render(TrailRenderer renderer, RenderTarget target, Matrix4fc view, Vec3 camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		long now = System.nanoTime();
		double x = Mth.lerp(partialTick, player.xo, player.getX());
		double y = Mth.lerp(partialTick, player.yo, player.getY());
		double z = Mth.lerp(partialTick, player.zo, player.getZ());
		record(x, y, z, now);
		if (mc.options.getCameraType().isFirstPerson() && !firstPerson.isOn()) {
			return;
		}
		long maxAge = (long) (duration.get() * 1_000_000_000L);
		int n = 0;
		// Newest first; the live position is always the head so the ribbon is attached to the player.
		for (int k = 0; k < size && n < CAPACITY; k++) {
			int i = Math.floorMod(head - k, CAPACITY);
			long age = now - ht[i];
			if (age > maxAge) {
				break;
			}
			ages[n] = (float) age / maxAge;
			px[n] = (float) (hx[i] - camera.x);
			py[n] = (float) (hy[i] - camera.y);
			pz[n] = (float) (hz[i] - camera.z);
			n++;
		}
		float w = width.floatValue();
		float bottom, top;
		switch (style.get()) {
			case "Ground" -> {
				bottom = 0.02F;
				top = 0.02F + w * 0.15F;
			}
			case "Halo" -> {
				bottom = player.getBbHeight() + 0.05F;
				top = bottom + w * 0.25F;
			}
			default -> {
				bottom = 0.15F;
				top = 0.15F + w;
			}
		}
		int headC, tailC;
		switch (colorMode.get()) {
			case "Rainbow" -> {
				float hue = (now / 1_000_000_000F * 0.15F) % 1F;
				headC = ColorUtil.hsv(hue, 0.7F, 1, 255);
				tailC = ColorUtil.hsv((hue + 0.3F) % 1F, 0.7F, 1, 255);
			}
			case "Theme" -> {
				var t = com.nebula.client.gui.themes.Theme.current();
				headC = t.accent();
				tailC = t.accent2();
			}
			default -> {
				headC = headColor.argb();
				tailC = tailColor.argb();
			}
		}
		renderer.render(target, view, px, py, pz, ages, n, bottom, top, headC, tailC, opacity.floatValue());
	}

	private void record(double x, double y, double z, long now) {
		if (size > 0) {
			double dx = x - hx[head], dy = y - hy[head], dz = z - hz[head];
			boolean moved = dx * dx + dy * dy + dz * dz > 0.0025;
			boolean stale = now - ht[head] > 40_000_000L;
			if (!moved && !stale) {
				return;
			}
			if (dx * dx + dy * dy + dz * dz > 64) {
				size = 0; // teleported: start a fresh trail instead of drawing a streak across the world
			}
		}
		head = (head + 1) % CAPACITY;
		hx[head] = x;
		hy[head] = y;
		hz[head] = z;
		ht[head] = now;
		size = Math.min(size + 1, CAPACITY);
	}
}
