package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ZipListIndex;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-200차: 서버 리소스팩(zip) 파일 목록 뽑기를 색인으로 빠르게({@link ZipListIndex}). 파일 이름을 뒤섞은 큰 팩을 받을 때
 * 게임이 20초 가까이 멈춰 서버(대기열)가 연결을 끊던 문제. 실패하면 바닐라가 그대로 처리한다.
 */
@Mixin(net.minecraft.server.packs.FilePackResources.class)
public abstract class ZipListFastMixin {

	@Inject(method = "listResources", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$fastList(PackType type, String namespace, String path, PackResources.ResourceOutput output,
			CallbackInfo ci) {
		if (ZipListIndex.list(this, type.getDirectory(), namespace, path, output)) {
			ci.cancel();
		}
	}
}
