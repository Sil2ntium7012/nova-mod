package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.TodoList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-473차: 할 일 목록({@link TodoList}) - 블록을 캤을 때, 생물을 때렸을 때. */
@Mixin(MultiPlayerGameMode.class)
public abstract class TodoGameModeMixin {

	@Unique
	private String lunaslight$breaking;
	@Unique
	private BlockState lunaslight$breakState;   // 49-572차: 허수아비 확률(NeogulScareRate)

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), require = 0)
	private void lunaslight$todoBreakHead(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		lunaslight$breaking = null;
		lunaslight$breakState = null;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				BlockState st = mc.level.getBlockState(pos);
				if (!st.isAir()) {
					lunaslight$breaking = st.getBlock().getName().getString();
					lunaslight$breakState = st;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"), require = 0)
	private void lunaslight$todoBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && lunaslight$breaking != null) {
			TodoList.onBreak(lunaslight$breaking);
			kr.lunaslight.mod.util.NeogulScareRate.onBreak(lunaslight$breakState);
		}
		lunaslight$breaking = null;
		lunaslight$breakState = null;
	}

	@Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$todoAttack(Player player, Entity target, CallbackInfo ci) {
		TodoList.onAttack(target);
	}
}
