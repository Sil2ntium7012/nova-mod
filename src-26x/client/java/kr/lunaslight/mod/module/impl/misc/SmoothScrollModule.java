package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.FloatSetting;
import kr.lunaslight.mod.util.SmoothScrollState;

/**
 * 49-24차 재작성: "휠 매끄럽게". 목록 위젯(설정/서버 목록/월드 목록/리소스팩/조작/모드 목록 등 EntryListWidget
 * 계열)의 스크롤을 EntryListScrollMixin/ScrollableWidgetScrollMixin + SmoothScrollState가 지수 보간한다.
 * 휠 이벤트 자체는 건드리지 않아(49-23차 방식 폐기) 어떤 화면에서도 스크롤이 막히지 않는다.
 * 49-32차: 크리에이티브 인벤토리는 목록 위젯을 안 써서 따로 CreativeScrollMixin +
 * CreativeScrollState가 맡는다(휠 값을 프레임마다 나눠 넣는 방식).
 */
public class SmoothScrollModule extends Module {

	private final FloatSetting speed = register(new FloatSetting(
			"speed", "속도", "클수록 빨리 따라오고, 작을수록 더 미끄러집니다.", 14f, 4f, 30f, 1f));

	// 49-41차(사용자): 핫바 선택 테두리도 휠에 맞춰 미끄러지게(HotbarSelectionMixin + HotbarSelectionHook)
	private final kr.lunaslight.mod.module.setting.BooleanSetting hotbar = register(new kr.lunaslight.mod.module.setting.BooleanSetting(
			"hotbar", "핫바 선택 표시", "핫바의 선택 테두리가 칸 사이를 미끄러지듯 이동합니다.", true));

	public SmoothScrollModule() {
		super("smooth_scroll", "부드러운 휠", ModuleCategory.FEATURE, "목록/크리에이티브 스크롤을 부드럽게");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		sync();
	}

	@Override
	protected void onDisable() {
		SmoothScrollState.enabled = false;
		kr.lunaslight.mod.util.HotbarSelectionHook.enabled = false;
	}

	@Override
	public void onTick() {
		sync();
	}

	private void sync() {
		SmoothScrollState.enabled = isEnabled();
		SmoothScrollState.speed = speed.get();
		kr.lunaslight.mod.util.HotbarSelectionHook.enabled = isEnabled() && hotbar.get();
		kr.lunaslight.mod.util.HotbarSelectionHook.speed = speed.get() * 1.3f;
	}
}
