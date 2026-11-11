package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.NeogulMineFeed;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-630차(사용자: "곡괭이 2칸씩 캐는데 들어오는 게 1개씩"): 서버가 칸을 공기로 바꾼 소식 - 범위 곡괭이가 같이 부순 옆 칸을 센다
 * (NeogulMineFeed.onBlockGone). 끝(TAIL)에서 받아서 네트워크 스레드에서 한 번, 메인 스레드에서 또 한 번 불리지 않는다.
 */
@Mixin(ClientPacketListener.class)
public abstract class NeogulBlockGoneMixin {

	@Inject(method = "handleBlockUpdate", at = @At("TAIL"), require = 0)
	private void lunaslight$blockGone(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		try {
			if (packet.getBlockState().isAir()) {
				NeogulMineFeed.onBlockGone(packet.getPos());
			}
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"), require = 0)
	private void lunaslight$blocksGone(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		try {
			packet.runUpdates((pos, state) -> {
				if (state.isAir()) {
					NeogulMineFeed.onBlockGone(pos.immutable());
				}
			});
		} catch (Throwable ignored) {
		}
	}
}
