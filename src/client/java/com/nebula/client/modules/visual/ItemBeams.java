package com.nebula.client.modules.visual;

import com.nebula.client.gui.themes.Theme;
import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.NebulaPipelines;
import com.nebula.client.rendering.world.GlowBatch;
import com.nebula.client.rendering.world.SparkPool;
import com.nebula.client.rendering.world.WorldFrame;
import com.nebula.client.rendering.world.WorldRenderable;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.ColorSetting;
import com.nebula.client.settings.ModeSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Vertical light beams over dropped items, coloured by rarity, with a ground ring and slow
 * rising sparkles. Beams are depth-tested: they read as world light, not as a wallhack.
 */
public final class ItemBeams extends Module implements WorldRenderable {
	private static final int MAX_BEAMS = 64;
	/** Ring radius in the GLOW_RING shader is relative to the quad half-size (see JumpCircles). */
	private static final float RING_POS = 0.8F;
	private static final float RING_RADIUS = 0.34F;

	public final BoolSetting beams = add(new BoolSetting("Beams", "Vertical light beam over each item", true));
	public final NumberSetting height = add(new NumberSetting("Height", "Beam height", 1.4, 0.4, 4, 0.1, "m"));
	public final NumberSetting width = add(new NumberSetting("Width", "Beam width", 0.075, 0.02, 0.3, 0.005, "m"));
	public final BoolSetting groundRing = add(new BoolSetting("Ground ring", "Glowing ring at the item's feet", true));
	public final BoolSetting sparkles = add(new BoolSetting("Sparkles", "Slow sparks rising along the beam", true));
	public final BoolSetting onlyRare = add(new BoolSetting("Uncommon and above", "Ignore common items (blocks, food, ...)", true));
	public final ModeSetting colorMode = add(new ModeSetting("Color", "Beam colour", "Rarity", "Rarity", "Theme", "Custom"));
	public final ColorSetting customColor = add(new ColorSetting("Custom color", "Colour when not using rarity", 0xFF4DE1FF))
		.visibleWhen(() -> colorMode.is("Custom"));
	public final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 24, 6, 64, 1, "m"));

	private final SparkPool sparks = new SparkPool(96);
	private final RandomSource random = RandomSource.create();
	private final ItemEntity[] visible = new ItemEntity[MAX_BEAMS];
	private int visibleCount;

	public ItemBeams() {
		super("item_beams", "Item Beams", "Rarity-coloured light beams over dropped items", Category.VISUAL);
	}

	private boolean shown(ItemStack stack) {
		return !stack.isEmpty() && (!onlyRare.isOn() || stack.getRarity() != Rarity.COMMON);
	}

	private int colorOf(ItemStack stack) {
		return switch (colorMode.get()) {
			case "Theme" -> Theme.current().accent();
			case "Custom" -> customColor.argb();
			default -> {
				Integer rgb = stack.getRarity().color().getColor();
				yield rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb;
			}
		};
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !sparkles.isOn()) {
			return;
		}
		double max = range.floatValue() * range.floatValue();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ItemEntity item) || mc.player.distanceToSqr(e) > max || !shown(item.getItem())) {
				continue;
			}
			if (random.nextFloat() < 0.10F) {
				int c = ColorUtil.withAlpha(colorOf(item.getItem()), 0.9F);
				sparks.emit(e.getX() + (random.nextFloat() - 0.5F) * 0.3, e.getY() + 0.06,
					e.getZ() + (random.nextFloat() - 0.5F) * 0.3,
					(random.nextFloat() - 0.5F) * 0.08F, 0.55F + random.nextFloat() * 0.35F, (random.nextFloat() - 0.5F) * 0.08F,
					c, 0.9F + random.nextFloat() * 0.6F, 0.022F);
			}
		}
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		visibleCount = 0;
		double max = range.floatValue() * range.floatValue();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (visibleCount >= MAX_BEAMS) {
				break;
			}
			if (e instanceof ItemEntity item && f.camera().distanceToSqr(e.getX(), e.getY(), e.getZ()) <= max && shown(item.getItem())) {
				visible[visibleCount++] = item;
			}
		}
		if (visibleCount == 0) {
			return;
		}
		float t = f.seconds();
		float maxRange = range.floatValue();
		float beamHeight = height.floatValue();
		float halfWidth = width.floatValue() * 0.5F;
		float ringHalf = RING_RADIUS / RING_POS;

		if (beams.isOn()) {
			batch.begin(f);
			for (int i = 0; i < visibleCount; i++) {
				ItemEntity item = visible[i];
				int c = colorOf(item.getItem());
				float dist = (float) Math.sqrt(f.camera().distanceToSqr(item.getX(), item.getY(), item.getZ()));
				float fade = 1 - dist / maxRange;
				float pulse = 0.78F + 0.22F * Mth.sin(t * 2.2F + item.getId());
				float bob = Mth.sin((item.tickCount + f.partialTick()) * 0.12F + item.getId()) * 0.06F;
				float x = f.rx(item.getX());
				float y = f.ry(item.getY()) + 0.05F + bob;
				float z = f.rz(item.getZ());
				int bottom = ColorUtil.withAlpha(c, fade * fade * pulse);
				int top = ColorUtil.withAlpha(c, 0);
				batch.beam(x, y, z, halfWidth, beamHeight, bottom, top);
			}
			batch.end(NebulaPipelines.GLOW_RIBBON, 0.6F, 0, 0);
		}

		if (groundRing.isOn()) {
			batch.begin(f);
			for (int i = 0; i < visibleCount; i++) {
				ItemEntity item = visible[i];
				float dist = (float) Math.sqrt(f.camera().distanceToSqr(item.getX(), item.getY(), item.getZ()));
				float fade = 1 - dist / maxRange;
				float pulse = 0.7F + 0.3F * Mth.sin(t * 2.2F + item.getId());
				batch.flat(f.rx(item.getX()), f.ry(item.getY()) + 0.02F, f.rz(item.getZ()), ringHalf,
					ColorUtil.withAlpha(colorOf(item.getItem()), fade * pulse));
			}
			batch.end(NebulaPipelines.GLOW_RING, RING_POS, 0.055F, 0.10F);
		}

		if (sparkles.isOn()) {
			sparks.render(f, batch, -1.4F);
		}
	}
}
