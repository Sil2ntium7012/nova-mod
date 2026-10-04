package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.EntityHideHook;
import kr.lunaslight.mod.module.setting.StringSetting;

/**
 * 49-59차(3-4): 엔티티 가리기 - 적어 둔 이름을 가진 엔티티를 아예 안 그린다.
 *
 * <p>대상은 <b>이름이 보이는 것</b>이다 - 이름표(모루로 붙인 이름)를 단 몹과 플레이어. 이름이 없는
 * 일반 몹은 가릴 기준 자체가 없어서 건드리지 않는다(종류로 가리는 건 다른 기능이다).
 *
 * <p>왜 "안 그리기"인가: 거점마다 세워 둔 이름표 아머스탠드, 상점 NPC 줄, 광고용 이름표가 시야를
 * 덮는 서버가 대상이다. 그리기를 중간에 취소하는 게 아니라 <b>바닐라가 "이걸 그릴까?"를 묻는 관문</b>에서
 * 막으므로 반쯤 그려지는 자리가 없고 비용도 거의 없다({@link EntityHideHook} 주석에 성능 근거).
 *
 * <p>내 캐릭터는 이름이 걸려도 안 숨긴다 - 3인칭에서 내가 사라지면 고장으로 보인다.
 */
public class EntityHideModule extends Module {

	private final StringSetting names = register(new StringSetting(
			"names", "이름", "이 이름을 가진 엔티티를 안 그립니다. 대소문자는 가리지 않습니다.", "").list());

	public EntityHideModule() {
		super("entity_hide", "엔티티 가리기", ModuleCategory.VIEW, "이름표 | 닉네임으로 엔티티 숨김");
		// 49-61차: 사용자 요청 목록 "3. 그래픽 설정"의 엔티티 가리기(3-4) → [그래픽] 설정 페이지.
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GRAPHICS);
		names.onChange(this::apply);
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
		EntityHideHook.names = null;
	}

	private void apply() {
		// 이름 자르기는 채팅 단어 필터와 같은 규칙(쉼표 · 소문자 · 빈 값이면 null)이라 그대로 쓴다.
		EntityHideHook.names = isEnabled() ? ChatState.parseWords(names.get()) : null;
	}
}
