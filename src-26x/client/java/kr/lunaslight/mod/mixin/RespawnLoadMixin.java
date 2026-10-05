package kr.lunaslight.mod.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-294차(사용자: "서버에서 죽었는데 월드 로딩이 너무 오래 걸려" → "어 해줘"): 26.x는 부활/차원 이동 뒤 [지형 불러오는 중] 화면을
 * 내 발밑 칸이 "그려졌다"는 신호가 올 때까지 띄우고, 안 오면 30초 뒤에야 들여보낸다. 소듐(Sodium)을 쓰면 죽을 때 청크가 비워지면서
 * 이 신호가 안 와 매번 30초를 꽉 채웠다(로그: "Timed out while waiting for the client to load chunks", 소듐 이슈 #2584).
 * 바닐라 판정이 아직 아니어도, 1.5초가 지났고 내 발밑과 둘레 8청크를 서버에서 다 받았으면 들여보낸다(그리기는 들어간 뒤 바로 따라온다).
 * 26.1~26.3 공통(레코드 성분 player, level, timeoutAfter). 없으면 require = 0이라 조용히 안 붙는다.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForPlayerChunk")
public abstract class RespawnLoadMixin {

	@Shadow @Final private LocalPlayer player;
	@Shadow @Final private ClientLevel level;
	@Shadow @Final private long timeoutAfter;

	/** 바닐라 대기 시간(26.1~26.3: 30초). 시작 시각 = timeoutAfter - 이 값. */
	private static final long LUNASLIGHT$VANILLA_WAIT_MS = 30_000L;
	/** 이만큼은 바닐라 판정을 기다린다(소듐 없이 잘 될 때는 그쪽이 먼저 끝난다). */
	private static final long LUNASLIGHT$MIN_WAIT_MS = 1_500L;

	@Inject(method = "isReady", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$readyWhenChunksArrived(CallbackInfoReturnable<Boolean> cir) {
		try {
			if (Boolean.TRUE.equals(cir.getReturnValue()) || player == null || level == null) {
				return;
			}
			long now = System.nanoTime() / 1_000_000L;   // Util.getMillis()와 같은 시계(nanoTime 기준)
			if (now - (timeoutAfter - LUNASLIGHT$VANILLA_WAIT_MS) < LUNASLIGHT$MIN_WAIT_MS) {
				return;
			}
			int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!level.hasChunk(cx + dx, cz + dz)) {
						return;   // 아직 서버에서 안 온 청크가 있다 - 바닐라대로 기다린다
					}
				}
			}
			cir.setReturnValue(true);
		} catch (Throwable ignored) {
		}
	}
}
