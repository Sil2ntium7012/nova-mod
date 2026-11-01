package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.LunaVersion;
import kr.lunaslight.mod.util.PackOrderHook;

/**
 * 49-73차(1-7): <b>내 리소스팩 우선</b> - 서버가 준 팩을 내 팩 <b>아래로</b> 내린다.
 *
 * <p>사용자 요청 1-7: "리소스팩 우선순위 강제". 서버에 들어가면 서버 팩이 내 팩 위에 얹혀서
 * 쓰던 글꼴·아이콘·UI가 서버 것으로 덮인다. 켜면 <b>내 팩이 이긴다</b>.
 *
 * <p><b>서버 팩을 끄는 게 아니다.</b> 서버 팩은 그대로 살아 있고, 내 팩이 갖고 있지 <b>않은</b>
 * 그림·소리는 여전히 서버 것이 나온다. 겹치는 것만 내 것이 이긴다 - 그게 "우선순위"다.
 *
 * <p><b>바뀌는 시점</b>: 팩 목록은 리소스를 새로 읽을 때만 만들어진다. 켠 뒤 <b>서버에 다시 들어가거나
 * F3+T</b>를 눌러야 반영된다.
 *
 * <p><b>1.15.2에서는 카드가 잠긴다</b> - 그 버전에는 팩 목록을 한 번에 만드는 자리
 * ({@code createResourcePacks})가 아예 없다(javap 실측).
 *
 * <p><b>성능</b>: 꺼져 있으면 비용이 0이고, 켜져 있어도 <b>리소스를 새로 읽을 때 한 번</b> 목록을
 * 훑을 뿐이다(보통 열 개 안쪽).
 */
public class PackPriorityModule extends Module {

	private final BooleanSetting world = register(new BooleanSetting(
			"world", "월드 팩 포함", "월드에 딸려 오는 리소스팩도 같이 내립니다.", false));
	// 49-157차(사용자: "내 리소스팩 우선은 특수문자 다 빼 - 글이랑 숫자만, 서버 기호는 서버 것"): FontSplitHook 참고.
	private final BooleanSetting symbolsServer = register(new BooleanSetting(
			"symbols_server", "서버 기호", "켜면 내 글꼴 팩은 글자와 숫자에만 쓰고, 기호와 아이콘 글자는 서버 팩(없으면 기본) 것을 씁니다. 1.20 이상.", true));

	// 49-473차(사용자: "내 리소스팩 우선 폰트 글 말고는 하지 말라고"): PackUiGuard.fontOnly 참고. 켜져 있으면 서버 화면, 서버 작물은 볼 것도 없이 서버 것.
	private final BooleanSetting fontOnly = register(new BooleanSetting(
			"font_only", "글꼴만", "켜면 서버 팩이 있을 때 내 팩은 글꼴과 글만 씁니다. 그림, 셰이더, 소리, 블록은 서버 것(없으면 기본)이라 서버 HUD, 핑, 공격 게이지가 안 깨집니다.", true));

	// 49-452차(사용자: 너굴마을에서 내 팩을 위로 올리면 서버 HUD가 옆으로 밀리고 기호가 깨짐): PackUiGuard 참고.
	private final BooleanSetting serverUi = register(new BooleanSetting(
			"server_ui", "서버 화면", "켜면 서버 팩에도 있는 화면 그림(체력, 배고픔, 보스바 등)과 글자 셰이더는 서버 것을 씁니다. 서버 HUD가 밀리거나 깨지지 않게 합니다.", true));

	// 49-461차(사용자: 내 리팩 우선이면 너굴마을 작물이 바닐라로 보임): PackUiGuard.cropsEnabled 참고.
	private final BooleanSetting serverCrops = register(new BooleanSetting(
			"server_crops", "서버 작물", "켜면 서버 팩이 바꾼 작물과 풀 그림, 서버가 만든 블록(소리 블록, 철사 등)은 서버 것을 씁니다. 서버 작물이 바닐라로 보이지 않게 합니다.", true));

	public PackPriorityModule() {
		super("pack_priority", "내 리소스팩 우선", ModuleCategory.FEATURE,
				"서버 팩보다 내 팩을 위에");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);   // 1-7은 [일반 설정] 항목이다
		world.onChange(this::apply);
		kr.lunaslight.mod.util.FontSplitHook.enabled = () -> isEnabled() && isVersionSupported() && symbolsServer.get()
				&& LunaVersion.isWithin("1.20", null);
		kr.lunaslight.mod.util.PackUiGuard.enabled = () -> isEnabled() && isVersionSupported() && serverUi.get();
		kr.lunaslight.mod.util.PackUiGuard.fontOnly = () -> isEnabled() && isVersionSupported() && fontOnly.get();
		kr.lunaslight.mod.util.PackUiGuard.cropsEnabled = () -> isEnabled() && isVersionSupported() && serverCrops.get();
	}

	@Override
	public boolean isVersionSupported() {
		return super.isVersionSupported() && LunaVersion.isWithin("1.16", null);
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		PackOrderHook.off = true;
	}

	private void apply() {
		PackOrderHook.includeWorld = world.get();
		// 세부 값을 먼저 채우고 마지막에 켠다
		PackOrderHook.off = !isEnabled() || !isVersionSupported();
	}
}
