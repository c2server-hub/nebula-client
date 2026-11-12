package com.nebula.client.modules.visual;

import com.nebula.client.core.ClientManager;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Animated glow around the current combat target: a scanning ring column and orbiting comets. */
public final class TargetEsp extends Module implements WorldRenderable {
	private static final float RING_POS = 0.85F;
	private static final int RING_GHOSTS = 7;
	private static final int COMET_TAIL = 14;

	public final ModeSetting style = add(new ModeSetting("Style", "Visual style", "Both", "Ring", "Comets", "Both"));
	public final BoolSetting attackedOnly = add(new BoolSetting("Attacked only", "Only show for entities you hit (otherwise also the one you look at)", false));
	public final NumberSetting range = add(new NumberSetting("Range", "Max distance for looked-at targets", 8, 3, 32, 1, "m"))
		.visibleWhen(() -> !attackedOnly.isOn());
	public final NumberSetting speed = add(new NumberSetting("Speed", "Animation speed", 1.0, 0.2, 3, 0.05, "x"));
	public final NumberSetting comets = add(new NumberSetting("Comets", "Number of orbiting comets", 3, 1, 6, 1))
		.visibleWhen(() -> !style.is("Ring"));
	public final NumberSetting size = add(new NumberSetting("Size", "Comet size", 0.09, 0.03, 0.25, 0.01))
		.visibleWhen(() -> !style.is("Ring"));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom ESP colour", 0xFFFF5C8A)));

	private final Animation appear = new Animation(10);
	private @Nullable LivingEntity shown;

	public TargetEsp() {
		super("target_esp", "Target ESP", "Scanning glow ring and comets around your target", Category.VISUAL);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		LivingEntity target = attackedOnly.isOn()
			? ClientManager.get().targets().lastAttacked(3)
			: ClientManager.get().targets().current(range.get(), 3);
		if (target == mc.player) {
			target = null;
		}
		if (target != null) {
			shown = target;
		}
		float a = appear.update(target != null ? 1 : 0);
		LivingEntity e = shown;
		if (e == null || a < 0.01F || e.isRemoved()) {
			return;
		}
		float t = f.seconds() * speed.floatValue();
		double cx = Mth.lerp(f.partialTick(), e.xo, e.getX());
		double cy = Mth.lerp(f.partialTick(), e.yo, e.getY());
		double cz = Mth.lerp(f.partialTick(), e.zo, e.getZ());
		float h = e.getBbHeight();
		float r = e.getBbWidth() * 0.75F + 0.2F;
		float grow = 0.7F + 0.3F * Animation.easeOutBack(a);

		if (!style.is("Comets")) {
			batch.begin(f);
			for (int k = 0; k < RING_GHOSTS; k++) {
				// Ghost rings lag behind the scanning ring, forming a fading light column.
				float phase = t * 2.4F - k * 0.09F;
				float y = h * (0.5F + 0.47F * Mth.sin(phase));
				float fade = (1 - (float) k / RING_GHOSTS);
				int c = ColorUtil.withAlpha(color.color((float) k / RING_GHOSTS, f.seconds()), fade * fade * a);
				batch.flat(f.rx(cx), f.ry(cy + y), f.rz(cz), r * grow / RING_POS, c);
			}
			batch.end(NebulaPipelines.GLOW_RING, RING_POS, 0.035F, 0);
		}
		if (!style.is("Ring")) {
			int n = comets.intValue();
			batch.begin(f);
			for (int i = 0; i < n; i++) {
				for (int k = 0; k < COMET_TAIL; k++) {
					float tt = t - k * 0.022F;
					float angle = tt * 3.2F + i * Mth.TWO_PI / n;
					float y = h * (0.5F + 0.42F * Mth.sin(tt * 1.9F + i * 2.1F));
					float fall = 1 - (float) k / COMET_TAIL;
					int c = ColorUtil.withAlpha(color.color((float) i / n, f.seconds()), fall * a);
					batch.billboard(f.rx(cx + Mth.cos(angle) * r * grow), f.ry(cy + y), f.rz(cz + Mth.sin(angle) * r * grow),
						size.floatValue() * (0.35F + 0.65F * fall), c);
				}
			}
			batch.end(NebulaPipelines.GLOW_SPARK, 0, 0, 0);
		}
	}
}
