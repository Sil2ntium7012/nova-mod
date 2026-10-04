package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.GlintHook;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-42차: 인챈트 반짝임 - 스텁을 실제로.
 *  · 숨김: ItemStack#hasGlint를 false로(GlintMixin) → 인벤토리·손·갑옷 전부.
 *  · 세기·속도: 1.19.3+에 있는 바닐라 옵션(GameOptions.glintStrength/glintSpeed)을 여기서 조절(설정 화면 슬라이더로).
 *    그 아래 버전은 옵션 자체가 없어 숨김만 된다. 색은 글린트 텍스처/셰이더를 갈아야 해서(버전마다 다름) 빼놓음.
 */
public class GlintControlModule extends Module {

	private final BooleanSetting hideGlint = register(new BooleanSetting(
			"hide_glint", "반짝임 숨김", "인챈트와 포션의 반짝임을 아예 그리지 않습니다.", false));

	private final IntSetting strength = register(new IntSetting(
			"strength", "세기", "반짝임의 밝기(%)입니다. 1.19.3 이상에서만 동작합니다.", 75, 0, 100, 5).unit("%"));

	private final IntSetting speed = register(new IntSetting(
			"speed", "속도", "반짝임이 흐르는 속도(%)입니다. 1.19.3 이상에서만 동작합니다.", 50, 0, 100, 5).unit("%"));

	private int lastStrength = -1, lastSpeed = -1;

	public GlintControlModule() {
		super("glint_control", "인챈트 반짝임", ModuleCategory.VIEW, "반짝임 숨기기 | 세기 | 속도");
	}

	@Override
	protected void onEnable() {
		lastStrength = -1;
		lastSpeed = -1;
	}

	@Override
	protected void onDisable() {
		GlintHook.hide = false;
		applyOption("getGlintStrength", 0.75);
		applyOption("getGlintSpeed", 0.5);
	}

	@Override
	public void onTick() {
		GlintHook.hide = isEnabled() && hideGlint.get();
		if (strength.get() != lastStrength) {
			lastStrength = strength.get();
			applyOption("getGlintStrength", lastStrength / 100.0);
		}
		if (speed.get() != lastSpeed) {
			lastSpeed = speed.get();
			applyOption("getGlintSpeed", lastSpeed / 100.0);
		}
	}

	/** GameOptions.getGlintStrength()/getGlintSpeed() → SimpleOption#setValue(Double). 옵션이 없는 버전은 조용히 무시. */
	private void applyOption(String getter, double value) {
		try {
			if (client == null || client.options == null) {
				return;
			}
			Object opt = LunaCompat.callNoArg(client.options, getter);
			if (opt == null) {
				return;
			}
			java.lang.reflect.Method set = LunaCompat.getMethodCompat(opt.getClass(), "setValue", Object.class);
			set.setAccessible(true);
			set.invoke(opt, value);
		} catch (Throwable t) {
			LunaCompat.warnOnce("glint:" + getter, t);
		}
	}
}
