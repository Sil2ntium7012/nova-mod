package kr.lunaslight.mod.util;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 49-452차(사용자: "너굴마을 리소스팩 보고 하던가 … 리팩 끼면 리팩이 옆으로 밀린다니까, 이상한 네모 폰트도 안 되고"):
 * [내 리소스팩 우선]으로 내 팩이 서버 팩 위에 올라가도 <b>서버 화면(UI)은 서버 것</b>을 쓰게 한다.
 *
 * <p>너굴마을 팩을 실제로 열어 내 팩들과 겹치는 파일을 비교한 결과: 겹치는 건 글자 셰이더
 * ({@code shaders/core/rendertype_text*.vsh/fsh})와 HUD 그림({@code textures/gui/sprites/hud/…} 체력, 배고픔, 경험치, 보스바)뿐이었다.
 * 너굴마을은 글자 셰이더로 HUD 글자를 제자리에 옮기고, 바닐라 체력/배고픔 그림을 비워 자기 HUD를 그린다. 내 팩(예: PalCraft-Resource)의
 * 글자 셰이더가 위에 오면 서버 HUD 글자가 옆으로 밀리고 기호가 이상하게 나오며, 바닐라 하트가 다시 보인다.
 *
 * <p>그래서 리소스를 새로 읽을 때 서버 팩(id "server/…")에 있는 {@code shaders/…}, {@code textures/gui/…} 파일 목록을 모아 두고,
 * 내 팩(id "file/…")에서는 <b>그 파일만</b> 없는 것처럼 숨긴다({@code mixin/PackUiGuardMixin} - FilePackResources, PathPackResources).
 * 서버 팩에 없는 파일, 블록/아이템/소리/글꼴은 그대로 내 팩이 이긴다. 글꼴 기호는 {@link FontSplitHook}이 따로 맡는다.
 * [내 리소스팩 우선]이 꺼져 있으면(서버 팩이 원래 위) 아무것도 안 한다.
 */
public final class PackUiGuard {
	private PackUiGuard() {
	}

	/** PackPriorityModule이 끼운다(내 리소스팩 우선 켜짐 + [서버 화면은 서버 것]). */
	public static volatile java.util.function.BooleanSupplier enabled;
	/**
	 * 49-461차(사용자: "내 리팩 우선으로 하면 작물들도 제대로 안 보이고 마크 기본으로 보이던데"): 너굴마을은 바닐라 작물 그림(beetroots_stage 등)을
	 * 자기 작물(벼, 고추 …) 그림으로 바꿔 쓰고, 커스텀 블록은 note_block/tripwire/chorus_plant 블록 상태로 만든다. 내 팩(예: SPBR - 바닐라
	 * 모양 그림 + _n/_s)이 위에 오면 작물이 바닐라로 보였다. [서버 작물]이 켜져 있으면 이런 파일도 서버 것을 쓴다.
	 */
	public static volatile java.util.function.BooleanSupplier cropsEnabled;

	private static final java.util.regex.Pattern CROP = java.util.regex.Pattern.compile(
		"(?:^|/)(?:wheat|carrots|potatoes|beetroots|melon_stem|pumpkin_stem|attached_melon_stem|attached_pumpkin_stem|sweet_berry_bush|cocoa"
			+ "|nether_wart|torchflower_crop|pitcher_crop|sugar_cane|bamboo_stage|cave_vines|kelp|cactus|short_grass|tall_grass|fern|large_fern"
			+ "|note_block|tripwire|chorus_plant|chorus_flower|brown_mushroom_block|red_mushroom_block|mushroom_stem)[^/]*$");
	private static final String[] CROP_PREFIXES = {"textures/block", "blockstates", "models/block"};

	private static final String[] PREFIXES = {"shaders", "textures/gui"};
	private static volatile Set<String> serverUi = Set.of();

	private static boolean cropsOn() {
		java.util.function.BooleanSupplier e = cropsEnabled;
		try {
			return e != null && e.getAsBoolean() && !PackOrderHook.off;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean on() {
		java.util.function.BooleanSupplier e = enabled;
		try {
			return e != null && e.getAsBoolean() && !PackOrderHook.off;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 팩 목록이 정해진 직후(PackOrderMixin): 서버 팩의 UI 파일 목록을 새로 모은다. */
	public static void prepare(List<?> packs) {
		Set<String> out = new HashSet<>();
		try {
			boolean ui = on();
			boolean crops = cropsOn();
			if (packs != null && (ui || crops)) {
				for (Object o : packs) {
					if (!(o instanceof PackResources pr) || !PackOrderHook.isServerPackId(pr.packId())) {
						continue;
					}
					for (String ns : pr.getNamespaces(PackType.CLIENT_RESOURCES)) {
						if (ui) {
							for (String prefix : PREFIXES) {
								pr.listResources(PackType.CLIENT_RESOURCES, ns, prefix, (id, s) -> out.add(id.toString()));
							}
						}
						if (crops) {
							for (String prefix : CROP_PREFIXES) {
								pr.listResources(PackType.CLIENT_RESOURCES, ns, prefix, (id, s) -> {
									if (CROP.matcher(id.getPath()).find()) {
										out.add(id.toString());
									}
								});
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packUiGuard", t);
		}
		boolean changed = !out.equals(serverUi);
		serverUi = out.isEmpty() ? Set.of() : out;
		if (changed && !out.isEmpty()) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 내 리소스팩 우선: 서버 화면 파일 " + out.size() + "개는 서버 것을 씁니다");
		}
	}

	/** 내 팩(file/…)의 이 파일을 숨길까(서버 팩에 같은 UI 파일이 있으면). */
	public static boolean hides(PackResources self, Identifier id) {
		Set<String> s = serverUi;
		if (s.isEmpty() || self == null || id == null) {
			return false;
		}
		String pid;
		try {
			pid = self.packId();
		} catch (Throwable t) {
			return false;
		}
		if (pid == null || !pid.startsWith("file/")) {
			return false;
		}
		String key = id.toString();
		if (s.contains(key)) {
			return true;
		}
		// 그림의 .mcmeta(애니메이션 등)는 서버에 그 그림이 있으면 같이 숨긴다(서버 그림에 내 애니메이션이 붙지 않게)
		if (key.endsWith(".mcmeta") && s.contains(key.substring(0, key.length() - ".mcmeta".length()))) {
			return true;
		}
		// 49-461차: 셰이더용 짝 그림(이름_n.png, 이름_s.png)도 서버에 그 그림이 있으면 숨긴다(서버 그림에 내 요철/반사가 어긋나게 붙지 않게)
		if (key.endsWith("_n.png") || key.endsWith("_s.png")) {
			return s.contains(key.substring(0, key.length() - 6) + ".png");
		}
		return false;
	}

	public static boolean active() {
		return !serverUi.isEmpty();
	}
}
