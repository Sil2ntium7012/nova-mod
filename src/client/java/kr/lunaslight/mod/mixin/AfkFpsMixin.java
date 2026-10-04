package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.AfkModule;
import kr.lunaslight.mod.util.LunaCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-201차(사용자: "잠수 모드 시 FPS 60 고정"): 1.21.2+의 마크는 가만히 있으면 스스로 프레임을 30(1분)/10(10분)으로 내린다
 * (InactivityFpsLimiter). [자리 비움] 기능이 켜져 있으면 그 두 경우와 우리 자리 비움일 때 60으로 둔다(최대 프레임 설정이
 * 60보다 낮으면 그 값). 창을 최소화했을 때(10)는 그대로 둔다. 이유 순서(0 없음, 1 최소화, 2 긴 자리 비움, 3 짧은 자리 비움,
 * 4 메뉴)는 1.21.11과 26.x 바이트코드에서 확인. 클래스는 이름 문자열로 잡아 옛 버전에선 "대상 없음" 경고만 남는다.
 */
@Mixin(targets = "net.minecraft.client.option.InactivityFpsLimiter")
public abstract class AfkFpsMixin {

	@Inject(method = "update", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$afk60(CallbackInfoReturnable<Integer> cir) {
		if (!AfkModule.enabledNow()) {
			return;
		}
		Object reason = LunaCompat.callNoArg(this, "getLimitReason");
		int ord = reason instanceof Enum<?> en ? en.ordinal() : -1;
		if (ord == 1) {
			return;   // 창 최소화 - 안 보이니 그대로
		}
		if (AfkModule.isAfkNow() || ord == 2 || ord == 3) {
			Object max = LunaCompat.getFieldValue(this, "maxFps");
			int cap = max instanceof Integer i && i > 0 ? i : AfkModule.AFK_FPS;
			cir.setReturnValue(Math.min(cap, AfkModule.AFK_FPS));
		}
	}
}
