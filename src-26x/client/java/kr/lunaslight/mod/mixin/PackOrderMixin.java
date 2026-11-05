package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.PackOrderHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 49-73차(1-7): 리소스팩 목록이 <b>다 만들어진 뒤</b> 서버 팩만 내 팩 아래로 내린다.
 *
 * <p>고르는 자리를 {@code createResourcePacks()}의 <b>RETURN</b>으로 잡은 이유: 여기가
 * "최종 목록"이 한 번에 손에 들어오는 <b>유일한 지점</b>이다. 만드는 도중에 끼어들면 어떤 팩이
 * 아직 안 들어왔는지 알 수 없다.
 *
 * <p>판단과 재배열은 전부 {@link PackOrderHook}에 있다 - 거기 주석에 <b>왜 이 자리가 조심스러운지</b>와
 * 지킨 규칙 넷을 적어 뒀다. 요약하면 <b>개수를 안 바꾸고, 서버 팩 하나만 옮기고, 조금이라도 이상하면
 * 원래 목록을 그대로 둔다.</b>
 *
 * <p>{@code createResourcePacks()}는 <b>1.16.5부터</b> 있다(1.15.2에는 없다 - javap 실측).
 * {@code require = 0}이라 없는 버전에서는 조용히 빠지고, 모듈도 그 버전에서 카드를 잠근다.
 */
@Mixin(net.minecraft.server.packs.repository.PackRepository.class)
public abstract class PackOrderMixin {

	@Inject(method = "openAllSelected()Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$packOrder(CallbackInfoReturnable<List<?>> cir) {
		if (PackOrderHook.off) {
			kr.lunaslight.mod.util.PackUiGuard.prepare(null);   // 49-452차: 꺼져 있으면 숨기는 것도 없음
			return;
		}
		List<?> current = cir.getReturnValue();
		// 49-566차(사용자: "내 리소스팩 우선 끄는 게 아니라 문제가 없게, 폰트 쓰는 사람이 얼마나 많은데"): [글꼴만]이면 서버 팩을 내리지 않는다.
		// 팩 순서는 바닐라 그대로(서버 팩이 맨 위 = 노바 없을 때와 같은 화면)라 서버 HUD, 셰이더, 그림이 밀리지 않고, 내 팩의 글꼴만
		// FontSplitMixin이 글꼴 목록 안에서 위로 올린다(기호는 서버 것 - FontSplitHook). 49-563차의 "너굴마을에서는 안 함"은 이걸로 대신한다.
		if (kr.lunaslight.mod.util.PackUiGuard.fontOnlyWanted()) {
			kr.lunaslight.mod.util.PackUiGuard.prepare(current);
			return;
		}
		List<Object> fixed = PackOrderHook.reorder(current);
		if (fixed != null) {
			cir.setReturnValue(fixed);
		}
		// 49-452차: 서버 팩의 UI 파일(셰이더, textures/gui)은 내 팩에서 숨겨 서버 것이 나오게
		kr.lunaslight.mod.util.PackUiGuard.prepare(fixed != null ? fixed : current);
	}

}
