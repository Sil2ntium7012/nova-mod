package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ServerPackRescue;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 49-477차: 서버가 보낸 리소스팩을 기억({@link ServerPackRescue}). */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerPackRescueMixin {

	@Inject(method = "handleResourcePackPush(Lnet/minecraft/network/protocol/common/ClientboundResourcePackPushPacket;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$rememberPack(ClientboundResourcePackPushPacket p, CallbackInfo ci) {
		try {
			ServerPackRescue.onPush(p.id(), p.url(), p.hash());
		} catch (Throwable ignored) {
		}
	}
}
