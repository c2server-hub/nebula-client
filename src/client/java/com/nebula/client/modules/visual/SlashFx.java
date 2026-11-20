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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * A crescent of glowing sparks sweeps in front of the player on every hand swing, alternating
 * direction left/right like a real blade arc. Detection rides the vanilla {@code swinging} field,
 * so it fires for your own attacks and (optionally) for every player in range.
 */
public final class SlashFx extends Module implements WorldRenderable {
	private static final int MAX_FLASH = 4;
	private static final float FLASH_SECONDS = 0.2F;

	public final NumberSetting count = add(new NumberSetting("Count", "Sparks in the arc", 16, 6, 30, 1));
	public final NumberSetting radius = add(new NumberSetting("Radius", "Arc size", 1.1, 0.6, 2.2, 0.05, "m"));
	public final NumberSetting speed = add(new NumberSetting("Speed", "Sweep speed", 3.2, 0.5, 8, 0.1));
	public final NumberSetting size = add(new NumberSetting("Size", "Spark size", 0.13, 0.04, 0.28, 0.005));
	public final NumberSetting range = add(new NumberSetting("Range", "How far other players' swings show", 14, 4, 32, 1, "m"));
	public final BoolSetting onOthers = add(new BoolSetting("On other players", "Their swings slash too", true));
	public final BoolSetting alternate = add(new BoolSetting("Alternate", "Flip the sweep direction each swing", true));
	public final BoolSetting flash = add(new BoolSetting("Flash", "Small ring burst at the centre", true));
	private final ColorChoice color = new ColorChoice(add(ColorChoice.modeSetting()), add(new ColorSetting("Color", "Custom slash colour", 0xFF9BE8FF)));

	private final RandomSource random = RandomSource.create();
	private final SparkPool sparks = new SparkPool(192);
	private final Map<Integer, Boolean> swinging = new HashMap<>();
	private final double[] wx = new double[MAX_FLASH], wy = new double[MAX_FLASH], wz = new double[MAX_FLASH];
	private final long[] flashBorn = new long[MAX_FLASH];
	private int nextFlash;
	private boolean flip = true;
	private final Vector3f look = new Vector3f();
	private final Vector3f right = new Vector3f();
	private final Vector3f up = new Vector3f();

	public SlashFx() {
		super("slash_fx", "Slash FX", "Crescent spark sweep on every hand swing", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		swinging.clear();
	}

	@Override
	public void renderWorld(WorldFrame f, GlowBatch batch) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer self = mc.player;
		if (self == null || mc.level == null) {
			return;
		}
		long now = System.nanoTime();
		float seconds = now / 1e9F;
		double rangeSq = range.floatValue() * range.floatValue();
		for (Player p : mc.level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			boolean sw = p.swinging;
			Boolean previous = swinging.put(p.getId(), sw);
			if (previous == null || !sw || previous) {
				continue; // Only rising edges.
			}
			boolean own = p == self;
			if (!own && (!onOthers.isOn() || p.distanceToSqr(self) > rangeSq)) {
				continue;
			}
			slash(p, seconds);
		}
		sparks.render(f, batch, -1.2F);
		if (!flash.isOn()) {
			return;
		}
		batch.begin(f);
		boolean any = false;
		for (int i = 0; i < MAX_FLASH; i++) {
			if (flashBorn[i] == 0) {
				continue;
			}
			float k = (now - flashBorn[i]) / 1e9F / FLASH_SECONDS;
			if (k >= 1) {
				flashBorn[i] = 0;
				continue;
			}
			batch.billboard(f.rx(wx[i]), f.ry(wy[i]), f.rz(wz[i]), (0.32F + 0.5F * k) / 0.8F,
					ColorUtil.withAlpha(color.color(0.5F, seconds), (1 - k) * (1 - k) * 0.9F));
			any = true;
		}
		if (any) {
			batch.end(NebulaPipelines.GLOW_RING, 0.8F, 0.05F, 0.15F);
		}
	}

	private void slash(Player p, float seconds) {
		flip = alternate.isOn() != flip; // XOR keeps alternating, or locks one direction when off.
		float yaw = (float) Math.toRadians(p.getYRot());
		float pitch = (float) Math.toRadians(p.getXRot());
		look.set(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		right.set(-look.z, 0, look.x).normalize();
		up.cross(right, look).normalize();
		float cx = (float) p.getX() + look.x * radius.floatValue() * 0.95F;
		float cy = (float) p.getEyeY() - 0.15F + look.y * radius.floatValue() * 0.95F;
		float cz = (float) p.getZ() + look.z * radius.floatValue() * 0.95F;
		int n = count.intValue();
		float r = radius.floatValue();
		float dir = flip ? 1 : -1;
		float spread = 1.05F; // ~120 degrees of arc.
		for (int i = 0; i < n; i++) {
			float t = n == 1 ? 0.5F : i / (float) (n - 1);
			float ang = (t - 0.5F) * 2 * spread;
			float sin = Mth.sin(ang), cos = Mth.cos(ang);
			float px = cx + right.x * sin * r * dir + up.x * (cos * 0.55F - 0.12F) * r;
			float py = cy + right.y * sin * r * dir + up.y * (cos * 0.55F - 0.12F) * r;
			float pz = cz + right.z * sin * r * dir + up.z * (cos * 0.55F - 0.12F) * r;
			// Tangent of the arc, plus a touch of outward throw.
			float tx = right.x * cos * dir - up.x * sin * 0.55F + look.x * 0.25F;
			float ty = right.y * cos * dir - up.y * sin * 0.55F + look.y * 0.25F;
			float tz = right.z * cos * dir - up.z * sin * 0.55F + look.z * 0.25F;
			float mag = speed.floatValue() * r * (0.8F + random.nextFloat() * 0.4F);
			sparks.emit(px, py, pz, tx * mag, ty * mag, tz * mag,
					color.color(t, seconds), 0.26F * (0.7F + random.nextFloat() * 0.6F), size.floatValue() * (0.8F + random.nextFloat() * 0.5F));
		}
		wx[nextFlash] = cx + look.x * 0.3F;
		wy[nextFlash] = cy + look.y * 0.3F;
		wz[nextFlash] = cz + look.z * 0.3F;
		flashBorn[nextFlash] = System.nanoTime();
		nextFlash = (nextFlash + 1) % MAX_FLASH;
	}
}
