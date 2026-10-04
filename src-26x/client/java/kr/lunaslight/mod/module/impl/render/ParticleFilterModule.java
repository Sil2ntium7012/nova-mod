package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.ParticleHook;

/**
 * 49-53차(3-2 · 3-7): 입자 끄기 - 눈에 거슬리거나 시야를 가리는 입자를 아예 안 만들게 한다.
 *
 * <p>"안 보이게" 가 아니라 <b>만들기 전에 막는</b> 방식이라 그만큼 가벼워진다
 * (자세한 내용은 {@link ParticleHook}). 지금 막을 수 있는 것은 둘이다:
 * <ul>
 *   <li><b>포션 효과</b> - 몸에서 올라오는 뽀글뽀글. 효과를 여러 개 걸면 시야를 꽤 가린다.</li>
 *   <li><b>떨어지는 방울</b> - 물·용암·꿀·흑요석 눈물·종유석. 동굴에서 특히 많다.</li>
 * </ul>
 *
 * <p><b>49-64차(3-1)에 [블록 파괴]를 더했다.</b> 이것만 다른 길로 만들어져서(addParticle을 안 지나간다)
 * 길목을 따로 막아야 했다 - 실측해 보니 메서드는 그대로인데 들고 있는 클래스가 1.21.9에 옮겨졌을 뿐이라,
 * 믹스인 두 벌을 두고 버전마다 맞는 쪽만 붙게 했다({@link ParticleHook} 주석의 표).
 *
 * <p><b>깰 때 나는 조각과는 다르다</b> - 곡괭이질 중에 튀는 조각은 다른 메서드고, 그건 "지금 이 블록을
 * 캐는 중"이라는 신호라서 건드리지 않았다. 여기서 없애는 건 <b>블록이 실제로 깨지는 순간</b>의 조각이다.
 */
public class ParticleFilterModule extends Module {

	private final BooleanSetting potion = register(new BooleanSetting(
			"potion", "포션 효과", "몸에서 올라오는 포션 입자를 없앱니다.", false));
	private final BooleanSetting drips = register(new BooleanSetting(
			"drips", "떨어지는 방울", "물/용암/꿀 등이 뚝뚝 떨어지는 입자를 없앱니다.", false));
	private final BooleanSetting blockBreak = register(new BooleanSetting(
			"block_break", "블록 파괴", "블록이 깨질 때 튀는 조각을 없앱니다. 캘 때 나는 조각은 그대로입니다.", false));

	public ParticleFilterModule() {
		super("particle_filter", "입자 끄기", ModuleCategory.VIEW, "거슬리는 입자 숨김");
		// 49-61차: 사용자 요청 목록 "3. 그래픽 설정"의 입자 끄기(3-2·3-7) → [그래픽] 설정 페이지.
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GRAPHICS);
		// 49-76차(6-9, 사용자: "입자 끄기는 따로 설정 안 켜도 개별 적용"): 카드의 켜기 없이 항목 셋이 각자 바로 먹는다.
		// 그래서 "포션 효과"의 기본값을 켬 → 끔으로 내렸다(카드가 늘 켜져 있으니 기본이 켬이면 아무 말 없이 입자가 사라진다).
		alwaysOn();
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	public void onTick() {
		apply();
	}

	@Override
	protected void onDisable() {
		ParticleHook.hidePotion = false;
		ParticleHook.hideDrips = false;
		ParticleHook.hideBlockBreak = false;
	}

	private void apply() {
		ParticleHook.hidePotion = potion.get();
		ParticleHook.hideDrips = drips.get();
		ParticleHook.hideBlockBreak = blockBreak.get();
	}
}
