package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.SoundFilterHook;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-68차(4-9): <b>거슬리는 소리</b> - 듣기 싫은 소리만 골라 끈다.
 *
 * <p>사용자 요청 4-9: "거슬리는 소리 제거".
 *
 * <p><b>왜 미리 정해 둔 항목을 뒀나</b>: 소리를 끄려면 그 소리의 id를 알아야 하는데
 * ({@code minecraft:ambient.cave}) 보통은 그걸 알 방법이 없다. 그래서 대부분이 끄고 싶어 하는 것
 * 다섯 개는 <b>스위치로</b> 두고, 나머지는 직접 적게 했다.
 *
 * <p><b>왜 "전부 끄기"가 없나</b>: 그건 마인크래프트 설정의 소리 볼륨이 이미 하는 일이다.
 * 여기 있어야 할 이유가 없는 칸은 안 만든다.
 *
 * <p><b>앞부분만 맞으면 끈다.</b> {@code entity.villager}라고 적으면 주민이 내는 소리가 전부 들어간다 -
 * 주민 소리만 해도 열 개가 넘어서 하나하나 적는 건 무리다.
 *
 * <p><b>성능</b>: 꺼져 있으면 소리 한 번당 비용이 사실상 0이다({@link SoundFilterHook#off} 한 줄).
 */
public class SoundFilterModule extends Module {

	private final BooleanSetting cave = register(new BooleanSetting(
			"cave", "동굴 소리", "동굴에서 나는 '으스스한' 소리를 끕니다.", true));
	private final BooleanSetting portal = register(new BooleanSetting(
			"portal", "차원문 소리", "네더 차원문의 웅웅거리는 소리를 끕니다.", true));
	private final BooleanSetting villager = register(new BooleanSetting(
			"villager", "주민 소리", "주민의 '흠' 소리를 끕니다.", false));
	private final BooleanSetting phantom = register(new BooleanSetting(
			"phantom", "팬텀 소리", "팬텀의 울음소리를 끕니다.", false));
	private final BooleanSetting thunder = register(new BooleanSetting(
			"thunder", "천둥소리", "번개가 칠 때의 굉음을 끕니다(번개 자체는 그대로).", false));
	// 49-167차(사용자: "불 소리도 추가")
	private final BooleanSetting fire = register(new BooleanSetting(
			"fire", "불 소리", "불이 타는 소리를 끕니다(화로, 모닥불 포함).", false));

	private final StringSetting custom = register(new StringSetting(
			"custom", "직접 지정",
			"끌 소리의 이름 앞부분입니다. 예: block.note_block, entity.creeper", "").list());

	public SoundFilterModule() {
		super("sound_filter", "소리", ModuleCategory.FEATURE, "듣기 싫은 소리만 끄기");
		// 49-76차(6-4, 사용자: "거슬리는 소리 기능은 소리로 이름 바꾸고 기능에서 일반 설정으로 이동")
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);
		cave.onChange(this::apply);
		portal.onChange(this::apply);
		villager.onChange(this::apply);
		phantom.onChange(this::apply);
		thunder.onChange(this::apply);
		fire.onChange(this::apply);
		custom.onChange(this::apply);
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		SoundFilterHook.off = true;
		SoundFilterHook.prefixes = null;
	}

	private void apply() {
		if (!isEnabled()) {
			onDisable();
			return;
		}
		List<String> presets = new ArrayList<>(6);
		if (cave.get()) {
			presets.add("ambient.cave");
		}
		if (portal.get()) {
			presets.add("block.portal");
		}
		if (villager.get()) {
			presets.add("entity.villager");
		}
		if (phantom.get()) {
			presets.add("entity.phantom");
		}
		if (thunder.get()) {
			presets.add("entity.lightning_bolt.thunder");
		}
		if (fire.get()) {
			presets.add("block.fire");
			presets.add("block.campfire");
			presets.add("block.blastfurnace.fire");
			presets.add("block.furnace.fire");
			presets.add("block.smoker.smoke");
		}
		String[] list = SoundFilterHook.parse(custom.get(), presets.toArray(new String[0]));
		SoundFilterHook.prefixes = list;
		// 목록을 먼저 채우고 나서 켠다 - 반대로 하면 한순간 빈 목록으로 도는 프레임이 생긴다
		SoundFilterHook.off = list == null;
	}
}
