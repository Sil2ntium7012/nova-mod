package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.ShieldOffsetHook;

/**
 * 49-75차(2-3의 뒤 절반): <b>방패 위치</b> - 1인칭으로 든 방패를 위아래로 옮긴다.
 *
 * <p>사용자 요청 2-3 "방패·불 높이 조절"의 나머지 절반이다. 49-69차에 불만 하고 방패는
 * "거대한 메서드라 위험하다"며 미뤄 뒀는데, <b>실제로 재 보니 그 걱정이 틀렸다</b> -
 * 메서드 <b>안쪽</b>에 끼어들 때만 위험하고, <b>전체를 감싸는</b> 방식은 안전하다
 * ({@link ShieldOffsetHook} 주석에 왜 그런지 적어 뒀다).
 *
 * <p><b>0이면 바닐라와 완전히 같다.</b> 올리면 방패가 위로, 내리면 아래로 간다 -
 * 보통은 <b>내려서</b> 시야를 덜 가리게 쓴다. 방패를 든 손만 움직이고 <b>반대 손과 다른 아이템은
 * 그대로</b>다. 끝까지 올리면 약 4분의 1 블록이다.
 *
 * <p>딱 맞는 숫자는 시야각·화면 비율마다 달라서 <b>눈으로 맞추는 슬라이더</b>로 두었다.
 *
 * <p><b>성능</b>: 0이면 방패를 들어도 아무 일도 안 한다. 0이 아닐 때도 방패를 든 손에서만
 * 행렬을 한 번 밀었다 되돌린다.
 */
public class ShieldOffsetModule extends Module {

	private final IntSetting offset = register(new IntSetting(
			"offset", "방패 높이", "1인칭으로 든 방패를 위아래로 옮깁니다. 0이면 바닐라 그대로입니다.",
			0, -50, 50, 1));

	public ShieldOffsetModule() {
		super("shield_offset", "방패 위치", ModuleCategory.VIEW, "1인칭 방패 높이");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.UI);   // 2-3은 [UI 설정] 항목이다
		offset.onChange(this::apply);
	}

	/**
	 * <b>26.x에서는 카드가 잠긴다.</b> 거기서는 메서드 이름 자체가 {@code submitArmWithItem}으로
	 * 바뀌고 인자도 {@code SubmitNodeCollector}다(javap 실측 - 26.2 {@code ItemInHandRenderer}).
	 * 포팅 번역기는 이름이 같은 메서드만 옮길 수 있어서 이 믹스인은 26.x 트리에서 빠진다.
	 * 되지도 않는 슬라이더를 켜 두느니 <b>왜 못 쓰는지 보이게</b> 잠가 둔다(불 화면과 같은 이유).
	 */
	@Override
	public boolean isVersionSupported() {
		return super.isVersionSupported() && !kr.lunaslight.mod.util.LunaVersion.isWithin("26", null);
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		ShieldOffsetHook.offset = 0;
	}

	private void apply() {
		ShieldOffsetHook.offset = isEnabled() ? offset.get() : 0;
	}
}
