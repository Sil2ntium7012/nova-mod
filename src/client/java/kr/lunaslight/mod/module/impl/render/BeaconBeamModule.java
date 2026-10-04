package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;

/**
 * 49-55차(3-3): 신호기 빛 끄기 - 신호기에서 하늘로 뻗는 빛기둥을 안 그린다.
 *
 * <p>설정이 하나도 없다. 켜면 안 보이고, 끄면 보인다 - 그게 전부인 기능에 설정을 붙이면 화면만
 * 지저분해진다.
 *
 * <p>신호기가 많은 서버(거점마다 세워 두는 곳)에서는 빛기둥이 시야를 가리고 반투명 면이 겹쳐
 * 프레임도 떨어진다. 블록 자체와 효과는 그대로고 <b>보이는 기둥만</b> 사라진다.
 */
public class BeaconBeamModule extends Module {

	private static volatile boolean hidden;

	public BeaconBeamModule() {
		super("beacon_beam", "신호기 빛 끄기", ModuleCategory.VIEW, "신호기 빛기둥 숨김");
		// 49-61차: 사용자 요청 목록 "3. 그래픽 설정"의 신호기 빛 끄기(3-3) → [그래픽] 설정 페이지.
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GRAPHICS);
	}

	@Override
	protected void onEnable() {
		hidden = true;
	}

	@Override
	public void onTick() {
		hidden = isEnabled();
	}

	@Override
	protected void onDisable() {
		hidden = false;
	}

	/** BeaconBeamMixin이 프레임마다 보는 값. */
	public static boolean isHidden() {
		return hidden;
	}
}
