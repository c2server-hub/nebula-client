package com.nebula.client.modules.immersion;

import com.nebula.client.modules.Category;
import com.nebula.client.modules.Module;
import com.nebula.client.rendering.Gradient;
import com.nebula.client.rendering.RenderUtils;
import com.nebula.client.rendering.RoundedRenderer;
import com.nebula.client.settings.BoolSetting;
import com.nebula.client.settings.KeybindSetting;
import com.nebula.client.settings.NumberSetting;
import com.nebula.client.utilities.Animation;
import com.nebula.client.utilities.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;

/** Context-aware HUD: elements slide away and come back when they matter. */
public final class DiegeticHud extends Module {
    public enum Group { HOTBAR, HEALTH, FOOD, AIR, XP, MOUNT, EFFECTS }

    public final BoolSetting hideHotbar = add(new BoolSetting("Auto-hide hotbar", "Hotbar slides away when not used", true));
    public final BoolSetting hideBars = add(new BoolSetting("Auto-hide bars", "Health / food / XP appear only when they change or are low", true));
    public final BoolSetting hideEffects = add(new BoolSetting("Auto-hide effects", "Status effect icons appear only on change", false));
    public final NumberSetting showTime = add(new NumberSetting("Show time", "How long an element stays after a change", 3.5, 1, 10, 0.5, "s"));
    public final NumberSetting lowHealth = add(new NumberSetting("Low health", "Health under which bars stay visible", 10, 1, 20, 1));
    public final NumberSetting lowFood = add(new NumberSetting("Low food", "Food under which the bar stays visible", 8, 1, 20, 1));
    public final BoolSetting damageEdges = add(new BoolSetting("Low health edges", "Pulsing red screen edges when health is low", true));
    public final KeybindSetting peekKey = add(new KeybindSetting("Peek key", "Hold to show the whole HUD + compass", GLFW.GLFW_KEY_LEFT_ALT));
    public final BoolSetting compass = add(new BoolSetting("Compass", "Compass strip on demand (peek key or compass in hand)", true));
    public final BoolSetting coords = add(new BoolSetting("Coordinates", "Coordinates on demand", true));
    public final BoolSetting discreetTooltips = add(new BoolSetting("Discreet tooltips", "Smaller held-item name", true));

    private final EnumMap<Group, Long> shownAt = new EnumMap<>(Group.class);
    private final EnumMap<Group, Animation> anim = new EnumMap<>(Group.class);
    private ItemStack lastStack = ItemStack.EMPTY;
    private float lastHealth = -1, lastXp = -1;
    private int lastArmor = -1, lastFood = -1, lastEffects = -1, lastLevel = -1;
    private final Animation compassAnim = new Animation(0F, 10F);

    public DiegeticHud() {
        super("diegetic_hud", "Diegetic HUD", "Contextual HUD, auto-hiding hotbar, on-demand compass", Category.IMMERSION, true, false);
        for (Group g : Group.values()) {
            shownAt.put(g, System.nanoTime());
            anim.put(g, new Animation(1F, 8F));
        }
    }

    public boolean discreetTooltips() {
        return isEnabled() && discreetTooltips.isOn();
    }

    private void show(Group... groups) {
        long now = System.nanoTime();
        for (Group g : groups) {
            shownAt.put(g, now);
        }
    }

    @Override
    public void onTick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        ItemStack held = p.getMainHandItem();
        if (!ItemStack.isSameItemSameComponents(held, lastStack)) {
            show(Group.HOTBAR);
        }
        lastStack = held.copy();
        float hp = p.getHealth();
        if (lastHealth >= 0 && Math.abs(hp - lastHealth) > 0.01F) {
            show(Group.HEALTH);
        }
        if (p.hurtTime > 0) {
            show(Group.HEALTH, Group.FOOD, Group.HOTBAR);
        }
        lastHealth = hp;
        int armor = p.getArmorValue();
        if (armor != lastArmor) {
            show(Group.HEALTH);
        }
        lastArmor = armor;
        int food = p.getFoodData().getFoodLevel();
        if (food != lastFood) {
            show(Group.FOOD);
        }
        lastFood = food;
        if (p.experienceProgress != lastXp || p.experienceLevel != lastLevel) {
            show(Group.XP);
        }
        lastXp = p.experienceProgress;
        lastLevel = p.experienceLevel;
        int effects = p.getActiveEffects().size();
        if (effects != lastEffects) {
            show(Group.EFFECTS);
        }
        lastEffects = effects;
        if (p.isPassenger()) {
            show(Group.MOUNT);
        }
    }

    private boolean peeking() {
        Minecraft mc = Minecraft.getInstance();
        return peekKey.isBound() && mc.screen == null && GLFW.glfwGetKey(mc.getWindow().handle(), peekKey.key()) == GLFW.GLFW_PRESS;
    }

    private boolean forced(Group g, LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChatScreen || peeking()) {
            return true;
        }
        return switch (g) {
            case HOTBAR -> !hideHotbar.isOn();
            case EFFECTS -> !hideEffects.isOn();
            case HEALTH -> !hideBars.isOn() || p.getHealth() <= lowHealth.floatValue();
            case FOOD -> !hideBars.isOn() || p.getFoodData().getFoodLevel() <= lowFood.intValue() || p.getHealth() <= lowHealth.floatValue();
            case AIR -> true; // vanilla only draws it when needed
            case XP -> !hideBars.isOn();
            case MOUNT -> true;
        };
    }

    /** 1 = fully visible, 0 = hidden. */
    public float visibility(Group g) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!isEnabled() || p == null) {
            return 1F;
        }
        boolean visible = forced(g, p) || (System.nanoTime() - shownAt.get(g)) / 1.0E9F < showTime.floatValue();
        return anim.get(g).update(visible ? 1F : 0F);
    }

    public void renderOverlay(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();

        if (damageEdges.isOn() && p.getHealth() <= lowHealth.floatValue() && p.getHealth() > 0) {
            float danger = 1F - p.getHealth() / Math.max(1F, lowHealth.floatValue());
            float t = (System.nanoTime() % 100_000_000_000L) / 1.0E9F;
            float speed = 2.5F + danger * 3F;
            float pulse = 0.55F + 0.45F * Mth.sin(t * speed);
            float a = (0.18F + 0.32F * danger) * pulse * ImmersionHooks.global();
            int red = 0xFFB01818;
            int on = ColorUtil.withAlpha(red, a);
            int off = ColorUtil.withAlpha(red, 0F);
            float e = Math.min(w, h) * 0.22F;
            RoundedRenderer.gradient(g, 0, 0, w, e, 0, Gradient.vertical(on, off));
            RoundedRenderer.gradient(g, 0, h - e, w, e, 0, Gradient.vertical(off, on));
            RoundedRenderer.gradient(g, 0, 0, e, h, 0, Gradient.horizontal(on, off));
            RoundedRenderer.gradient(g, w - e, 0, e, h, 0, Gradient.horizontal(off, on));
        }

        boolean compassInHand = p.getMainHandItem().is(Items.COMPASS) || p.getOffhandItem().is(Items.COMPASS)
            || p.getMainHandItem().is(Items.RECOVERY_COMPASS);
        float c = compassAnim.update((compass.isOn() || coords.isOn()) && (peeking() || compassInHand) ? 1F : 0F);
        if (c < 0.01F) {
            return;
        }
        float y = 6F - (1F - c) * 24F;
        int text = ColorUtil.withAlpha(0xFFFFFFFF, c);
        int dim = ColorUtil.withAlpha(0xFFB8C0CC, c * 0.8F);
        if (compass.isOn()) {
            float bw = 180F;
            float bx = w / 2F - bw / 2F;
            RoundedRenderer.rect(g, bx, y, bw, 16, 6, ColorUtil.withAlpha(0xFF10131A, c * 0.45F));
            float bearing = Mth.positiveModulo(p.getYRot() + 180F, 360F);
            String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            RenderUtils.scissor(g, bx, y, bw, 16);
            for (int i = 0; i < 16; i++) {
                float deg = i * 22.5F;
                float diff = Mth.wrapDegrees(deg - bearing);
                if (Math.abs(diff) > 70) {
                    continue;
                }
                float px = w / 2F + diff * (bw / 140F);
                if (i % 2 == 0) {
                    String n = names[i / 2];
                    RenderUtils.centered(g, n, px, y + 4, n.equals("N") ? ColorUtil.withAlpha(0xFFFF6B6B, c) : text, RenderUtils.NFont.TEXT, 1F);
                } else {
                    RoundedRenderer.rect(g, px - 0.5F, y + 6, 1, 4, 0, dim);
                }
            }
            g.disableScissor();
            RoundedRenderer.rect(g, w / 2F - 0.5F, y + 13, 1, 3, 0, text);
            y += 19;
        }
        if (coords.isOn()) {
            String s = String.format("%d  %d  %d", Mth.floor(p.getX()), Mth.floor(p.getY()), Mth.floor(p.getZ()));
            RenderUtils.centered(g, s, w / 2F, y + 2, dim, RenderUtils.NFont.TEXT, 0.9F);
        }
    }
}
