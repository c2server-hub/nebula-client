package com.nebula.client.core;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Tracks the entity under the crosshair and the last entity the local player attacked. */
public final class TargetTracker {
	private final List<Consumer<LivingEntity>> attackListeners = new ArrayList<>();
	private @Nullable LivingEntity lastAttacked;
	private long lastAttackTime;

	void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && entity instanceof LivingEntity living) {
				lastAttacked = living;
				lastAttackTime = System.currentTimeMillis();
				for (Consumer<LivingEntity> l : attackListeners) {
					l.accept(living);
				}
			}
			return InteractionResult.PASS;
		});
	}

	public void onAttack(Consumer<LivingEntity> listener) {
		attackListeners.add(listener);
	}

	public @Nullable LivingEntity lastAttacked(double holdSeconds) {
		if (lastAttacked == null || !lastAttacked.isAlive() || lastAttacked.isRemoved()
			|| System.currentTimeMillis() - lastAttackTime > holdSeconds * 1000) {
			return null;
		}
		return lastAttacked;
	}

	public @Nullable LivingEntity looked(double range) {
		Minecraft mc = Minecraft.getInstance();
		Entity e = mc.crosshairPickEntity;
		if (e instanceof LivingEntity living && mc.player != null && living.isAlive() && living.distanceTo(mc.player) <= range) {
			return living;
		}
		return null;
	}

	/** Looked-at entity first, otherwise the recent attack target. */
	public @Nullable LivingEntity current(double range, double holdSeconds) {
		LivingEntity looked = looked(range);
		return looked != null ? looked : lastAttacked(holdSeconds);
	}
}
