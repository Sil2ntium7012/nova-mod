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

	/** 49-576차: 블록 우클릭(상자 열기) - 상자 기록이 명령어로 연 창을 상자로 적지 않게(ItemFinderModule.noteUse). */
	@Inject(method = "useItemOn", at = @At("HEAD"), require = 0)
	private void lunaslight$noteUse(net.minecraft.client.player.LocalPlayer player, net.minecraft.world.InteractionHand hand,
			net.minecraft.world.phys.BlockHitResult hit, CallbackInfoReturnable<?> cir) {
		try {
			if (hit != null) {
				kr.lunaslight.mod.module.impl.inventory.ItemFinderModule.noteUse(hit.getBlockPos());
			}
		} catch (Throwable ignored) {
		}
	}
}
