package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.ShieldRenderer;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * A fresnel energy bubble around your own player: nearly invisible head-on, lighting up towards
 * the rim, with a faint hexagonal tech pattern and an impact wave that sweeps the shell after
 * damage. Rendered by {@link ShieldRenderer} through its own sphere pass.
 */
public final class EnergyShield extends Module {
	public final NumberSetting radius = add(new NumberSetting("Radius", "Bubble width around the body", 1.0, 0.7, 1.6, 0.05, "m"));
	public final NumberSetting height = add(new NumberSetting("Height", "Bubble height multiplier", 1.35, 1.0, 1.9, 0.05, "x"));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", "Overall brightness", 0.9, 0.2, 2, 0.05));
	public final NumberSetting pattern = add(new NumberSetting("Pattern", "Hexagonal grid strength", 1.0, 0, 2, 0.05));
	public final NumberSetting flow = add(new NumberSetting("Flow", "Speed of the plasma drifting", 1.0, 0, 3, 0.05));
	public final BoolSetting pulse = add(new BoolSetting("Impact wave", "A wave sweeps the bubble when you take damage", true));
	public final BoolSetting firstPerson = add(new BoolSetting("First person", "Render while in first person (seen from inside)", false));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom shield colour", 0xFF4DC3FF)));

	private final Animation appear = new Animation(0, 9);
	private int lastHurt;
	private float pulseStart = -1000;
	private float lastSeconds;

	public EnergyShield() {
		super("energy_shield", "Energy Shield", "Fresnel energy bubble with an impact wave", Category.PLAYER);
	}

	@Override
	protected void onEnable() {
		appear.snap(0);
	}

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		// hurtTime spikes to 10 on damage; a rising edge restarts the impact wave.
		if (pulse.isOn() && p.hurtTime > 0 && lastHurt == 0) {
			pulseStart = lastSeconds;
		}
		lastHurt = p.hurtTime;
	}

	/** Called by WorldEffects with its shield renderer; {@code batch} is not used. */
	public void render(WorldFrame f, ShieldRenderer renderer) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || p.isInvisible() || p.isDeadOrDying() || (mc.options.getCameraType().isFirstPerson() && !firstPerson.isOn())) {
			return;
		}
		lastSeconds = f.seconds();
		float a = appear.update(1);
		if (a < 0.02F) {
			return;
		}
		double px = Mth.lerp(f.partialTick(), p.xo, p.getX());
		double py = Mth.lerp(f.partialTick(), p.yo, p.getY()) + p.getBbHeight() * 0.52;
		double pz = Mth.lerp(f.partialTick(), p.zo, p.getZ());
		float r = radius.floatValue();
		float phase = pulse.isOn() ? (f.seconds() - pulseStart) / 0.7F : -1;
		renderer.render(f.target(), f.view(), f.rx(px), f.ry(py), f.rz(pz), r, r * height.floatValue(), r,
				color.color(0.35F, f.seconds()), intensity.floatValue() * a, f.seconds() * flow.floatValue(),
				phase >= 0 && phase < 1 ? phase : -1, pattern.floatValue());
	}
}
