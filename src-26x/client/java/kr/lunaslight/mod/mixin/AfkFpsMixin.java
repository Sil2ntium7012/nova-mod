package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.AfkModule;
import kr.lunaslight.mod.util.LunaCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-201차(26.x 판, 사용자: "잠수 모드 시 FPS 60 고정"): FramerateLimitTracker가 가만히 있을 때 내리는 30/10을 [자리 비움]이
 * 켜져 있으면 60으로(최대 프레임이 더 낮으면 그 값). 최소화(이유 순서 1)는 그대로. 순서는 바이트코드에서 확인
 * (0 없음, 1 최소화, 2 긴 자리 비움, 3 짧은 자리 비움, 4 메뉴).
 */
@Mixin(targets = "com.mojang.blaze3d.platform.FramerateLimitTracker")
public abstract class AfkFpsMixin {

	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$afk60(CallbackInfoReturnable<Integer> cir) {
		if (!AfkModule.enabledNow()) {
			return;
		}
		Object reason = LunaCompat.callNoArg(this, "getThrottleReason");
		int ord = reason instanceof Enum<?> en ? en.ordinal() : -1;
		if (ord == 1) {
			return;
		}
		if (AfkModule.isAfkNow() || ord == 2 || ord == 3) {
			Object max = LunaCompat.getFieldValue(this, "framerateLimit");
			int cap = max instanceof Integer i && i > 0 ? i : AfkModule.AFK_FPS;
			cir.setReturnValue(Math.min(cap, AfkModule.AFK_FPS));
		}
	}
}
