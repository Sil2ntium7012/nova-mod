package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.InfoSetting;

/**
 * 49-271차: <b>코스메틱</b> 설정 페이지(키바인드 아래). 사용자: "키바인드 아래쪽에 하나 만들어서 인게임 망토, 인게임 UI, 날개 이런 거
 * 장착된 거 확인할 수 있고 교체는 클라이언트에서 할 거니까 따로 교체는 못 하게".
 * 런처가 넘긴 착용 정보를 보여 준다(InfoSetting). 화면 스킨 고르기도 [UI]에서 뺐다.
 *
 * <p>49-282차(사용자: "코스메틱에 바꾸기 이딴 거 없애", "바꾸는 건 안 돼도 비활성화는 가능하게"): [바꾸기] 줄을 뺐고, 망토, 인게임 UI,
 * 날개마다 [쓰기] 스위치를 둔다. 끄면 게임 안에서만 안 보인다(런처의 착용은 그대로 - 다시 켜면 돌아온다).
 * 망토 = NovaCapes.textureFor(내 것), 날개 = NovaWings.keyFor(내 것), UI = LunaTheme.setSkinOff.
 */
public class CosmeticsModule extends Module {

	private static CosmeticsModule instance;

	private static final String[][] CAPES = {
		{"red", "빨강 망토"}, {"orange", "주황 망토"}, {"yellow", "노랑 망토"}, {"green", "초록 망토"}, {"blue", "파랑 망토"},
		{"navy", "남색 망토"}, {"purple", "보라 망토"}, {"black", "검정 망토"}, {"gray", "회색 망토"}, {"white", "흰색 망토"}};

	private final BooleanSetting useCape;
	private final BooleanSetting useUi;
	private final BooleanSetting useWings;

	public CosmeticsModule() {
		super("cosmetics", "착용 중", ModuleCategory.FEATURE, "바꾸기는 Nova Client 런처에서");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.COSMETICS);
		alwaysOn();
		instance = this;
		register(new InfoSetting("cape", "인게임 망토", CosmeticsModule::cape));
		useCape = register(new BooleanSetting("use_cape", "망토 쓰기", "끄면 내 노바 망토를 게임에서 안 보이게 합니다. 런처에서 낀 것은 그대로입니다.", true));
		register(new InfoSetting("ui_skin", "인게임 UI", () -> {
			kr.lunaslight.mod.util.LunaTheme.Skin s = kr.lunaslight.mod.util.LunaTheme.chosenSkin();
			return s == null || s == kr.lunaslight.mod.util.LunaTheme.Skin.DEFAULT || !kr.lunaslight.mod.util.LunaTheme.skinOwned(s) ? "기본" : s.toString();
		}));
		useUi = register(new BooleanSetting("use_ui", "인게임 UI 쓰기", "끄면 설정 화면을 기본 모습으로 봅니다. 런처에서 낀 것은 그대로입니다.", true));
		useUi.onChange(this::applyUi);
		register(new InfoSetting("wings", "날개", () -> kr.lunaslight.mod.util.NovaWings.nameOf(kr.lunaslight.mod.util.NovaWings.mineKey())));
		useWings = register(new BooleanSetting("use_wings", "날개 쓰기", "끄면 내 날개를 안 그립니다. 런처에서 낀 것은 그대로입니다.", true));
		register(new InfoSetting("theme", "테마", () -> kr.lunaslight.mod.util.LunaSocial.cosmeticThemeName));
		register(new InfoSetting("color", "포인트 색", () -> kr.lunaslight.mod.util.LunaSocial.cosmeticColorName));
	}

	private void applyUi() {
		try {
			kr.lunaslight.mod.util.LunaTheme.setSkinOff(!useUi.get());
		} catch (Throwable ignored) {
		}
	}

	@Override
	public void onTick() {
		applyUi();   // 설정 파일을 읽은 직후에도 맞도록(값이 같으면 아무것도 안 한다)
	}

	/** 내 노바 망토를 그릴지. */
	public static boolean capeOn() {
		CosmeticsModule m = instance;
		return m == null || m.useCape.get();
	}

	/** 내 날개를 그릴지. */
	public static boolean wingsOn() {
		CosmeticsModule m = instance;
		return m == null || m.useWings.get();
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
