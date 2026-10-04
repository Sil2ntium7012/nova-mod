package kr.lunaslight.mod.util;

import net.minecraft.text.Text;

/**
 * 49-48차: 자막 꾸미기 - 바닐라 자막을 <b>다시 그리지 않고</b> 바닐라가 쓰는 색만 바꿔 끼운다.
 *
 * 자막을 통째로 우리가 그리면 "어떤 소리가 들리는 범위인지" 고르는 일과 방향 화살표(◀ ▶)까지 전부
 * 다시 구현해야 하고, 그 내부 구조가 버전마다 달라(1.16 시대엔 entry에 위치, 1.21.11은 소리 목록)
 * 어느 한 버전에서는 반드시 어긋난다. 그래서 <b>색만</b> 가로챈다 - 위치·화살표·사라지는 속도는
 * 전부 바닐라 그대로고, 우리는 보이는 색만 입힌다.
 *
 * <p>49-53차(4-30): "화살표 색" 설정을 빼고 <b>소리 종류별 색</b>을 넣었다. 한 줄을 그리는 순서가
 * (배경 → 화살표 → 자막 글자)라서, 자막 Text가 인자로 올라오는 순간({@link #note})에 그 줄의 종류를
 * 잡아 두고 바로 뒤에 오는 색 인자에 쓴다. 화살표는 글자보다 <i>먼저</i> 그려져 그 시점에는 아직
 * 종류를 모르므로 종류 색을 입히지 않고 [글자 색] 그대로 둔다(억지로 맞추려면 자막 목록을 매 프레임
 * 리플렉션으로 미리 읽어야 해서 안 한다).
 *
 * <p>49-53차에 같이 고친 것 - (1) 예전에는 바닐라가 넘긴 색을 통째로 갈아 끼워서 자막이 <b>서서히
 * 사라지는 효과</b>가 죽어 있었다. 이제 알파는 바닐라 것을 살리고 RGB만 바꾼다. (2) 1.15.2~1.19.4는
 * 믹스인이 가리키는 메서드가 틀려 있어서 이 기능이 <b>아무것도 안 하고 있었다</b>(자세한 건
 * SubtitleStyleMixin 주석).
 */
public final class SubtitleHook {
	private SubtitleHook() {
	}

	// ==================== 종류별 색(고정 팔레트) ====================
	// 종류마다 색 설정을 하나씩 두면 설정이 네 개 더 늘어나 화면이 지저분해진다. 어두운 배경에서
	// 서로 잘 구분되는 값으로 고정해 두고, 설정은 "켜고 끄기" 하나만 둔다.
	// 49-76차(6-13, 사용자: "중립적인 소리(트름, 먹는 소리 등)는 노란색으로 구별 확실하게 - 적대적, 중립, 플레이어,
	// 블록, 기타"): 생물 하나였던 칸을 셋으로 나눴다. 적대 몹은 이름 목록으로 판단하고(아래 HOSTILE_NAMES),
	// 그 밖의 생물·트름·먹기·마시기는 전부 중립이다. 플레이어는 subtitles.entity.player.* 중 트름을 뺀 것.
	/** 적대적(좀비·스켈레톤·크리퍼…) */
	public static final int HOSTILE = 0xFFFF6B6B;
	/** 중립(소·돼지·주민, 트름·먹기·마시기, 모르는 생물 전부) - 노랑 */
	public static final int NEUTRAL = 0xFFFFD84D;
	/** 플레이어(다침·공격·레벨 업·낙하…) */
	public static final int PLAYER = 0xFF9BD1FF;
	/** 블록(subtitles.block.*) */
	public static final int BLOCK = 0xFFA9D973;
	/** 기타(아이템·날씨·주변·음악·UI) */
	public static final int OTHER = 0xFFC8CCD2;

	private static final String[] HOSTILE_NAMES = {
		"zombie", "skeleton", "creeper", "spider", "enderman", "witch", "blaze", "ghast", "slime", "magma_cube",
		"phantom", "drowned", "husk", "stray", "wither_skeleton", "pillager", "vindicator", "evoker", "ravager",
		"vex", "guardian", "elder_guardian", "shulker", "silverfish", "endermite", "hoglin", "zoglin",
		"piglin_brute", "warden", "breeze", "bogged", "creaking", "wither", "ender_dragon", "zombie_villager",
		"cave_spider", "illusioner", "zombified_piglin", "zombie_pigman", "giant"
	};

	public static volatile boolean active;
	/** 0이면 바닐라 색 그대로. */
	public static volatile int background;
	public static volatile int text;
	/** 소리 종류에 따라 글자 색을 다르게 할지(1.16+ 전용 - 1.15.2 자막은 번역 키가 없는 문자열이다). */
	public static volatile boolean byKind;

	// 아래 셋은 렌더 스레드에서만 만진다(자막을 그리는 곳은 한 군데뿐).
	private static int kind;
	private static Text cachedText;
	private static int cachedKind;

	/** 자막 글자 Text가 인자로 올라온 순간 - 그 줄의 종류를 잡아 둔다(값은 그대로 돌려준다). */
	public static Text note(Text value) {
		kind = kindOf(value);
		return value;
	}

	private static int kindOf(Text value) {
		if (!active || !byKind || value == null) {
			return 0;
		}
		if (value == cachedText) {
			return cachedKind;   // 같은 자막이 사라질 때까지 여러 프레임 그려지므로 거의 항상 여기서 끝난다
		}
		int found = 0;
		try {
			String key = LunaCompat.translationKeyOf(value);
			if (key != null && key.startsWith("subtitles.")) {
				String rest = key.substring("subtitles.".length());
				if (rest.startsWith("entity.")) {
					String mob = rest.substring("entity.".length());
					int dot = mob.indexOf('.');
					String name = dot < 0 ? mob : mob.substring(0, dot);
					String action = dot < 0 ? "" : mob.substring(dot + 1);
					if ("player".equals(name) && !action.startsWith("burp")) {
						found = PLAYER;
					} else if (isHostile(name)) {
						found = HOSTILE;
					} else {
						found = NEUTRAL;           // 소·돼지·주민, 트름(player.burp)·먹기·마시기(generic.*), 모르는 생물
					}
				} else if (rest.startsWith("block.")) {
					found = BLOCK;
				} else {
					found = OTHER;                 // item · weather · ambient · particle · music · ui …
				}
			}
		} catch (Throwable ignored) {
		}
		cachedText = value;
		cachedKind = found;
		return found;
	}

	private static boolean isHostile(String name) {
		for (String h : HOSTILE_NAMES) {
			if (h.equals(name)) {
				return true;
			}
		}
		return false;
	}

	public static int background(int vanilla) {
		return active && background != 0 ? background : vanilla;
	}

	/** 자막 글자. 종류를 알면 종류 색, 아니면 [글자 색]. */
	public static int text(int vanilla) {
		if (!active) {
			return vanilla;
		}
		return blend(vanilla, kind != 0 ? kind : text);
	}

	/** 방향 화살표(◀ ▶). 글자보다 먼저 그려져 종류를 모르므로 [글자 색] 그대로. */
	public static int arrow(int vanilla) {
		kind = 0;   // 다음 줄로 넘어가는 지점 - 앞줄 종류가 새어 나가지 않게 여기서 지운다
		if (!active) {
			return vanilla;
		}
		return blend(vanilla, text);
	}

	/**
	 * 바닐라 색의 <b>알파(서서히 사라지는 정도)</b>는 살리고 RGB만 우리 색으로 바꾼다.
	 * 알파가 거의 0인 값은 바닐라 TextRenderer가 "불투명"으로 치므로 여기서도 같게 본다.
	 */
	private static int blend(int vanilla, int base) {
		if (base == 0) {
			return vanilla;
		}
		int va = (vanilla >>> 24) & 0xFF;
		if (va < 4) {
			va = 0xFF;
		}
		int ba = (base >>> 24) & 0xFF;
		return ((va * ba / 0xFF) << 24) | (base & 0xFFFFFF);
	}
}
