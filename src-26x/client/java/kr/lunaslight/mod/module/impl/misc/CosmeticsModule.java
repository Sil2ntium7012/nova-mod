package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.InfoSetting;

/**
 * 49-271차: <b>코스메틱</b> 설정 페이지(키바인드 아래). 사용자: "키바인드 아래쪽에 하나 만들어서 인게임 망토, 인게임 UI, 날개 이런 거
 * 장착된 거 확인할 수 있고 교체는 클라이언트에서 할 거니까 따로 교체는 못 하게".
 * 런처가 넘긴 착용 정보를 <b>보여 주기만</b> 한다(InfoSetting - 누를 것도, 저장할 것도 없다). 화면 스킨 고르기도 [UI]에서 뺐다.
 */
public class CosmeticsModule extends Module {

	private static final String[][] CAPES = {
		{"red", "빨강 망토"}, {"orange", "주황 망토"}, {"yellow", "노랑 망토"}, {"green", "초록 망토"}, {"blue", "파랑 망토"},
		{"navy", "남색 망토"}, {"purple", "보라 망토"}, {"black", "검정 망토"}, {"gray", "회색 망토"}, {"white", "흰색 망토"}};

	public CosmeticsModule() {
		super("cosmetics", "착용 중", ModuleCategory.FEATURE, "바꾸기는 Nova Client 런처에서");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.COSMETICS);
		alwaysOn();
		register(new InfoSetting("cape", "인게임 망토", CosmeticsModule::cape));
		register(new InfoSetting("ui_skin", "인게임 UI", () -> {
			kr.lunaslight.mod.util.LunaTheme.Skin s = kr.lunaslight.mod.util.LunaTheme.skin();
			return s == null || s == kr.lunaslight.mod.util.LunaTheme.Skin.DEFAULT ? "기본" : s.toString();
		}));
		register(new InfoSetting("wings", "날개", () -> kr.lunaslight.mod.util.NovaWings.nameOf(kr.lunaslight.mod.util.NovaWings.mineKey())));
		register(new InfoSetting("theme", "테마", () -> kr.lunaslight.mod.util.LunaSocial.cosmeticThemeName));
		register(new InfoSetting("color", "포인트 색", () -> kr.lunaslight.mod.util.LunaSocial.cosmeticColorName));
		register(new InfoSetting("how", "바꾸기", () -> "Nova Client 런처에서"));
	}

	private static String cape() {
		String k = kr.lunaslight.mod.util.NovaCapes.mine();
		if (k == null) {
			return null;
		}
		for (String[] c : CAPES) {
			if (c[0].equals(k)) {
				return c[1];
			}
		}
		return k;
	}
}
