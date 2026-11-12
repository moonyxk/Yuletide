package dev.yuletide;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class MinecraftCompat {
	private MinecraftCompat() {
	}

	public static void setInvulnerable(Entity entity) {
		entity.setPermanentlyInvulnerable(true);
	}

	public static void drop(ServerPlayer player, ItemStack stack) {
		player.drop(stack, false, Prediction.PREDICTED);
	}
}
