package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.module.impl.waypoint.BlueprintModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-277차: 26.3 바닐라 [친구] 키(기본 O)가 설계도 창 키(기본 O)를 먹던 것. 키를 누르는 순간 바닐라가 처리하는
 * Minecraft#handleGlobalKeyPress(26.3에 생김 - 26.1/26.2엔 없어 require = 0)에서, 게임 화면이고 설계도 창 키와 같은 키면
 * "처리 안 함"으로 돌려보낸다. 그러면 친구 창이 안 뜨고 설계도 모듈이 틱에서 창을 연다. 전체 화면/스크린샷 키는 그 키를
 * 설계도 창 키로 정하지 않는 한 그대로다.
 */
@Mixin(Minecraft.class)
public abstract class BlueprintKeyMixin {

	@Inject(method = "handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void lunaslight$blueprintKey(InputConstants.Key key, boolean flag, CallbackInfoReturnable<Boolean> cir) {
		try {
			if (BlueprintModule.ownsGlobalKey(key)) {
				cir.setReturnValue(false);
			}
		} catch (Throwable ignored) {
		}
	}
}
