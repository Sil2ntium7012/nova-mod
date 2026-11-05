package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.NeogulStorageModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-571차: 너굴 창고를 바꾸는 동안(NeogulStorageModule.switchUntil) 서버의 "창 닫기"를 받지 않는다 - 다음 창고 창이 바로 이 창을 갈아 끼워서
 * 중간에 월드가 비치거나 마우스가 가운데로 튀지 않는다. 새 창이 안 오면 모듈이 2.5초 뒤 닫는다.
 */
@Mixin(ClientPacketListener.class)
public abstract class NeogulContainerCloseMixin {

	@Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$holdClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
		try {
			if (NeogulStorageModule.holdClose()) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
