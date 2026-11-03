package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaStats;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-511차: 통계 "버림" - Q로 손에 든 것을 버림(26.1~26.2 LocalPlayer.drop). 26.3은 StatsDropGameModeMixin.dropItem. */
@Mixin(LocalPlayer.class)
public abstract class StatsDropMixin {

	@Inject(method = "drop(Z)Z", at = @At("HEAD"), require = 0)
	private void lunaslight$statsDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
		try {
			ItemStack held = ((LocalPlayer) (Object) this).getMainHandItem();
			if (held != null && !held.isEmpty()) {
				LunaStats.noteDropped(held, fullStack ? held.getCount() : 1);
			}
		} catch (Throwable ignored) {
		}
	}
}
