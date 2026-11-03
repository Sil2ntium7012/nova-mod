package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaStats;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-511차: 통계 "버림" - 가방 화면에서 Q(THROW)와 들고 있던 것을 창 밖에 놓기(-999 칸), 26.3의 손 버리기(dropItem).
 * 우리 기능이 대신 버리는 것(작물 캐기 씨앗 정리 등)도 같은 길이라 같이 센다.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class StatsDropGameModeMixin {

	@Inject(method = "handleContainerInput", at = @At("HEAD"), require = 0)
	private void lunaslight$statsThrow(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		try {
			if (player == null || player.containerMenu == null) {
				return;
			}
			ItemStack carried = player.containerMenu.getCarried();
			if (input == ContainerInput.THROW && slotId >= 0 && slotId < player.containerMenu.slots.size() && (carried == null || carried.isEmpty())) {
				ItemStack st = player.containerMenu.slots.get(slotId).getItem();
				if (st != null && !st.isEmpty()) {
					LunaStats.noteDropped(st, button == 1 ? st.getCount() : 1);
				}
			} else if (input == ContainerInput.PICKUP && slotId == -999 && carried != null && !carried.isEmpty()) {
				LunaStats.noteDropped(carried, button == 0 ? carried.getCount() : 1);
			}
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"), require = 0)
	private void lunaslight$statsDrop263(LocalPlayer p, boolean fullStack, CallbackInfo ci) {
		try {
			ItemStack held = p == null ? null : p.getMainHandItem();
			if (held != null && !held.isEmpty()) {
				LunaStats.noteDropped(held, fullStack ? held.getCount() : 1);
			}
		} catch (Throwable ignored) {
		}
	}
}
