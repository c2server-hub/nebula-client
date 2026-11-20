package com.nebula.client.modules.visual;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SparkPool;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Dropped items and experience orbs get a soft aura that pulses with the item's own bobbing
 * animation, a faint contact pad on the ground and the occasional rising spark. Colours follow
 * the item's rarity by default: white, yellow, aqua and purple for common to epic.
 */
public final class ItemGlow extends Module implements WorldRenderable {
	private static final int MAX_ITEMS = 48;
	private static final int MAX_ORBS = 16;

	public final NumberSetting range = add(new NumberSetting("Range", "Items tracked around you", 20, 4, 48, 1, "m"));
	public final NumberSetting maxItems = add(new NumberSetting("Max items", "Items glowing at once", 20, 4, MAX_ITEMS, 1));
	public final NumberSetting maxOrbs = add(new NumberSetting("Max XP orbs", "Orbs glowing at once", 10, 0, MAX_ORBS, 1));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "Glow strength", 1.0, 0.2, 2, 0.05));
	public final NumberSetting size = add(new NumberSetting("Size", "Halo size", 1.0, 0.5, 2, 0.05, "x"));
	public final BoolSetting rarityColors = add(new BoolSetting("Rarity colors", "Common white, uncommon yellow, rare aqua, epic purple", true));
	public final BoolSetting xpOrbs = add(new BoolSetting("XP orbs", "Glow on experience orbs", true));
	public final BoolSetting sparkles = add(new BoolSetting("Sparkles", "Occasional spark rising from items", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom colour", 0xFF8FB4FF)));

	private final RandomSource random = RandomSource.create();
	private final SparkPool sparks = new SparkPool(96);
	// Candidate arrays, filled during the entity walk and consumed by the draw pass.
	private final double[] ix = new double[MAX_ITEMS], iy = new double[MAX_ITEMS], iz = new double[MAX_ITEMS];
	private final double[] ox = new double[MAX_ORBS], oy = new double[MAX_ORBS], oz = new double[MAX_ORBS];
	private final int[] ic = new int[MAX_ITEMS];
	private int items, orbs;
	private long lastNanos;

	public ItemGlow() {
		super("item_glow", "Item Glow", "Pulsing rarity auras on dropped items and XP", Category.WORLD, true, false);
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		var self = mc.player;
		var level = mc.level;
		if (self == null || level == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9F, 0.1F);
		lastNanos = now;
		float seconds = now / 1e9F;
		double rangeSq = range.floatValue() * range.floatValue();
		items = 0;
		orbs = 0;
		int itemCap = maxItems.intValue();
		int orbCap = maxOrbs.intValue();
		for (var e : level.entitiesForRendering()) {
			double dx = e.getX() - self.getX();
			double dy = e.getY() - self.getY();
			double dz = e.getZ() - self.getZ();
			if (dx * dx + dy * dy + dz * dz > rangeSq) {
				continue;
			}
			if (e instanceof ItemEntity item && items < itemCap && !item.getItem().isEmpty()) {
				ix[items] = Mth.lerp(f.partialTick(), item.xo, item.getX());
				iy[items] = Mth.lerp(f.partialTick(), item.yo, item.getY());
				iz[items] = Mth.lerp(f.partialTick(), item.zo, item.getZ());
				ic[items] = itemColor(item.getItem());
				items++;
			} else if (orbCap > 0 && e instanceof ExperienceOrb orb && orbs < orbCap) {
				ox[orbs] = Mth.lerp(f.partialTick(), orb.xo, orb.getX());
				oy[orbs] = Mth.lerp(f.partialTick(), orb.yo, orb.getY());
				oz[orbs] = Mth.lerp(f.partialTick(), orb.zo, orb.getZ());
				orbs++;
			}
		}
		if (items == 0 && orbs == 0) {
			return;
		}

		batch.begin(f);
		float s = size.floatValue();
		for (int i = 0; i < items; i++) {
			float bob = Mth.sin((seconds * 2 + i * 1.7F) * 0.5F) * 0.08F;
			float pulse = 0.75F + 0.25F * Mth.sin(seconds * 2.4F + i * 2.1F);
			float b = brightness.floatValue() * pulse;
			// Contact pad lying on the ground.
			batch.flat(f.rx(ix[i]), f.ry(iy[i] + 0.04), f.rz(iz[i]), 0.34F * s, ColorUtil.withAlpha(ic[i], 0.28F * b));
			// Aura following the bobbing item.
			batch.billboard(f.rx(ix[i]), f.ry(iy[i] + 0.32 + bob), f.rz(iz[i]), (0.30F + 0.05F * pulse) * s, ColorUtil.withAlpha(ic[i], 0.55F * b));
		}
		for (int i = 0; i < orbs; i++) {
			float pulse = 0.7F + 0.3F * Mth.sin(seconds * 4 + i * 2.6F);
			int c = ColorUtil.mix(0xFF9BE15D, 0xFFE3F26B, 0.5F + 0.5F * Mth.sin(seconds * 2 + i));
			batch.billboard(f.rx(ox[i]), f.ry(oy[i] + 0.15), f.rz(oz[i]), 0.22F * s, ColorUtil.withAlpha(c, 0.6F * brightness.floatValue() * pulse));
		}
		batch.end(NebulaPipelines.GLOW_RING, 0.82F, 0.3F, 0.35F);

		if (sparkles.isOn() && items > 0) {
			for (int i = 0; i < items; i++) {
				if (random.nextFloat() < dt * 1.4F) {
					float spread = (random.nextFloat() - 0.5F) * 0.3F;
					sparks.emit(ix[i] + spread, iy[i] + 0.15, iz[i] + spread, (random.nextFloat() - 0.5F) * 0.15F, 0.45F + random.nextFloat() * 0.2F,
							(random.nextFloat() - 0.5F) * 0.15F, ColorUtil.brighten(ic[i], 0.35F), 0.65F, 0.05F);
				}
			}
			sparks.render(f, batch, -0.6F);
		}
	}

	private int itemColor(ItemStack stack) {
		if (rarityColors.isOn()) {
			return switch (stack.getRarity()) {
				case UNCOMMON -> 0xFFE8D44D;
				case RARE -> 0xFF4FD9FF;
				case EPIC -> 0xFFC97AFF;
				default -> 0xFFE3E8F2;
			};
		}
		return color.color(0.4F, System.nanoTime() / 1e9F);
	}
}
