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

	// 49-492차(사용자: "소리 끄는 기능을 따로 기능으로 바꾸고, 거기 안에 기본적으로 낚시, 블록 설치 파괴나 기본적인 거 등등 모두 넣어 줘"):
	// 자주 거슬리는 기본 소리를 스위치로(기본은 꺼 둠 - 켜면 그 소리가 안 난다). * 는 가운데 아무거나(SoundFilterHook).
	private final BooleanSetting fishing = register(new BooleanSetting(
			"fishing", "낚시 소리", "찌 던지기, 물 튀김, 걷기 소리를 끕니다.", false));
	private final BooleanSetting place = register(new BooleanSetting(
			"place", "블록 설치 소리", "블록을 놓을 때 나는 소리를 끕니다.", false));
	private final BooleanSetting breakS = register(new BooleanSetting(
			"break", "블록 부수기 소리", "블록이 부서질 때 나는 소리를 끕니다.", false));
	private final BooleanSetting hit = register(new BooleanSetting(
			"hit", "블록 캐는 소리", "블록을 캐는 동안 톡톡 나는 소리를 끕니다.", false));
	private final BooleanSetting step = register(new BooleanSetting(
			"step", "발소리", "걷거나 뛸 때 발소리를 끕니다(나와 다른 생물 모두).", false));
	private final BooleanSetting pickup = register(new BooleanSetting(
			"pickup", "줍는 소리", "아이템, 경험치 구슬을 주울 때 '뽁' 소리를 끕니다.", false));
	private final BooleanSetting levelup = register(new BooleanSetting(
			"levelup", "레벨 업 소리", "경험치 레벨이 오를 때 소리를 끕니다.", false));
	private final BooleanSetting eat = register(new BooleanSetting(
			"eat", "먹고 마시는 소리", "먹기, 마시기, 트림 소리를 끕니다.", false));
	private final BooleanSetting chest = register(new BooleanSetting(
			"chest", "상자 여닫는 소리", "상자, 통, 엔더 상자, 셜커 상자를 여닫는 소리를 끕니다.", false));
	private final BooleanSetting door = register(new BooleanSetting(
			"door", "문 소리", "문, 다락문, 울타리 문을 여닫는 소리를 끕니다.", false));
	private final BooleanSetting attack = register(new BooleanSetting(
			"attack", "공격 소리", "휘두르기, 치명타, 밀치기 같은 공격 소리를 끕니다.", false));
	private final BooleanSetting rain = register(new BooleanSetting(
			"rain", "빗소리", "비 오는 소리를 끕니다.", false));
	private final BooleanSetting water = register(new BooleanSetting(
			"water", "물 소리", "물속 소리, 흐르는 물, 헤엄치기 소리를 끕니다.", false));
	private final BooleanSetting click = register(new BooleanSetting(
			"click", "버튼 누르는 소리", "화면 버튼을 누를 때 딸깍 소리를 끕니다.", false));
	private final BooleanSetting animals = register(new BooleanSetting(
			"animals", "가축 울음소리", "소, 돼지, 양, 닭이 우는 소리를 끕니다.", false));

	private final StringSetting custom = register(new StringSetting(
			"custom", "직접 지정",
			"끌 소리의 이름 앞부분입니다. 예: block.note_block, entity.creeper", "").list());

	public SoundFilterModule() {
		// 49-76차: 일반 설정으로 옮겼던 것을 49-492차에 다시 기능으로(사용자: "소리 끄는 기능을 따로 기능으로")
		super("sound_filter", "소리 끄기", ModuleCategory.FEATURE, "낚시, 블록 설치/부수기, 발소리 … 듣기 싫은 소리만 끄기");
		cave.onChange(this::apply);
		portal.onChange(this::apply);
		villager.onChange(this::apply);
		phantom.onChange(this::apply);
		thunder.onChange(this::apply);
		fire.onChange(this::apply);
		for (BooleanSetting b : new BooleanSetting[]{fishing, place, breakS, hit, step, pickup, levelup, eat, chest, door, attack, rain, water, click, animals}) {
			b.onChange(this::apply);
		}
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
		if (fishing.get()) {
			presets.add("entity.fishing_bobber");
		}
		if (place.get()) {
			presets.add("block.*.place");
		}
		if (breakS.get()) {
			presets.add("block.*.break");
		}
		if (hit.get()) {
			presets.add("block.*.hit");
		}
		if (step.get()) {
			presets.add("block.*.step");
			presets.add("entity.*.step");
		}
		if (pickup.get()) {
			presets.add("entity.item.pickup");
			presets.add("entity.experience_orb.pickup");
		}
		if (levelup.get()) {
			presets.add("entity.player.levelup");
		}
		if (eat.get()) {
			presets.add("entity.generic.eat");
			presets.add("entity.generic.drink");
			presets.add("entity.player.burp");
		}
		if (chest.get()) {
			presets.add("block.chest.");
			presets.add("block.barrel.");
			presets.add("block.ender_chest.");
			presets.add("block.shulker_box.");
			presets.add("block.copper_chest");
		}
		if (door.get()) {
			presets.add("block.*door.open");
			presets.add("block.*door.close");
			presets.add("block.fence_gate");
			presets.add("block.*fence_gate.");
		}
		if (attack.get()) {
			presets.add("entity.player.attack");
		}
		if (rain.get()) {
			presets.add("weather.rain");
		}
		if (water.get()) {
			presets.add("ambient.underwater");
			presets.add("block.water.ambient");
			presets.add("entity.player.swim");
			presets.add("entity.player.splash");
		}
		if (click.get()) {
			presets.add("ui.button.click");
		}
		if (animals.get()) {
			presets.add("entity.cow.");
			presets.add("entity.pig.");
			presets.add("entity.sheep.");
			presets.add("entity.chicken.");
		}
		String[] list = SoundFilterHook.parse(custom.get(), presets.toArray(new String[0]));
		SoundFilterHook.prefixes = list;
		// 목록을 먼저 채우고 나서 켠다 - 반대로 하면 한순간 빈 목록으로 도는 프레임이 생긴다
		SoundFilterHook.off = list == null;
	}
}
