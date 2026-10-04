package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;

/**
 * 49-270차: <b>날개</b> - 런처 상점에서 산 노바 날개(첫 번째 = 나비 날개, 펄럭이는 애니메이션)를 몸 뒤에 그린다.
 * 내 날개는 런처 서명이 맞을 때만(산 사람만), 남의 날개는 Supabase 착용 정보로. 자세한 건 util/NovaWings.
 * 그리기: 1.16~1.21.11 월드 그리기 이벤트, 1.15.2 / 1.14.4 / 26.x는 월드 그리기 믹스인(BlueprintWorld*Mixin)이 이 모듈을 부른다.
 */
public class WingsModule extends Module {

	private static WingsModule instance;

	private final BooleanSetting others = register(new BooleanSetting(
			"others_wings", "다른 사람 날개", "다른 Nova 유저가 낀 노바 날개도 보여줍니다.", true));

	public WingsModule() {
		super("wings", "날개", ModuleCategory.VIEW, "런처에서 산 노바 날개");
		defaultEnabled(true);
		instance = this;
	}

	/** 켜져 있나(믹스인용). */
	public static boolean shown() {
		WingsModule m = instance;
		return m != null && m.isEnabled();
	}

	public static boolean othersShown() {
		WingsModule m = instance;
		return m == null || m.others.get();
	}

	@Override
	public void onWorldRender(Object context) {
		kr.lunaslight.mod.util.NovaWingsRender.draw(context, others.get(), false);
	}
}
