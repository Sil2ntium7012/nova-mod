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

	/**
	 * 49-473차(사용자: "내 리소스팩 우선 폰트 글 말고는 하지 말라고, 왜 핑도 제대로 안 되고 공격 게이지도 제대로 안 되고"): [글꼴만]이 켜져 있으면
	 * 서버 팩이 깔린 동안 내 팩(file/…)에서는 <b>글꼴과 글</b>(font/, textures/font/, lang/, texts/)만 쓰고 나머지 파일은 전부 없는 것처럼 숨긴다.
	 * 서버 팩에 없는 파일도 숨긴다 - 서버 셰이더, HUD(핑, 공격 게이지 …)는 바닐라 그림을 기준으로 짜여 있어 내 팩 그림이 섞이면 어긋난다.
	 */
	public static volatile java.util.function.BooleanSupplier fontOnly;
	private static volatile boolean serverLoaded;
	private static final String[] FONT_PREFIXES = {"font/", "textures/font/", "lang/", "texts/"};

	/**
	 * 49-482차(사용자 로그: "나갔다 오면 너굴마을 리소스팩이 사라지거나 다 빠짐" - 다른 사람 latest.log): [글꼴만]이 내 팩의 글꼴 JSON은 두고
	 * 그 글꼴이 쓰는 그림(예: Icons 팩의 icons:textures/tooltip/… - textures/font/ 밖)은 숨겨서 글꼴 만들기가 실패했고, 바닐라가
	 * "Default font failed to load"로 <b>서버 팩까지 전부 빼 버렸다</b>("Caught error loading resourcepacks, removing all selected").
	 * 그래서 내 팩 글꼴 JSON이 가리키는 파일(bitmap file, ttf file, unihex hex_file)은 어디 있든 같이 살린다.
	 */
	private static volatile Set<String> fontRefs = Set.of();

	private static void collectFontRefs(PackResources pr, String ns, Set<String> out) {
		pr.listResources(PackType.CLIENT_RESOURCES, ns, "font", (id, sup) -> {
			if (!id.getPath().endsWith(".json")) {
				return;
			}
			try (java.io.InputStream in = sup.get();
					java.io.Reader r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
				com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(r);
				if (!root.isJsonObject() || !root.getAsJsonObject().has("providers")) {
					return;
				}
				for (com.google.gson.JsonElement pe : root.getAsJsonObject().getAsJsonArray("providers")) {
					if (!pe.isJsonObject()) {
						continue;
					}
					com.google.gson.JsonObject po = pe.getAsJsonObject();
					String type = po.has("type") ? po.get("type").getAsString() : "";
					if (type.endsWith("bitmap") && po.has("file")) {
						out.add(ref(po.get("file").getAsString(), "textures/"));
					} else if (type.endsWith("ttf") && po.has("file")) {
						out.add(ref(po.get("file").getAsString(), "font/"));
					} else if (type.endsWith("unihex") && po.has("hex_file")) {
						out.add(ref(po.get("hex_file").getAsString(), ""));
					}
				}
			} catch (Throwable ignored) {
				// 못 읽는 글꼴 JSON은 바닐라가 알아서 건너뛴다
			}
		});
	}

	/** "icons:tooltip/x.png" + "textures/" → "icons:textures/tooltip/x.png". */
	private static String ref(String loc, String prefix) {
		int c = loc.indexOf(':');
		String ns = c < 0 ? "minecraft" : loc.substring(0, c);
		String path = c < 0 ? loc : loc.substring(c + 1);
		return ns + ":" + prefix + path;
	}

	private static boolean fontOnlyOn() {
		java.util.function.BooleanSupplier e = fontOnly;
		try {
			return e != null && e.getAsBoolean() && !PackOrderHook.off;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean fontPath(String path) {
		for (String f : FONT_PREFIXES) {
			if (path.startsWith(f)) {
				return true;
			}
		}
		return false;
	}
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
		boolean server = false;
		try {
			if (packs != null) {
				for (Object o : packs) {
					if (o instanceof PackResources pr && PackOrderHook.isServerPackId(pr.packId())) {
						server = true;
						break;
					}
				}
			}
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
		Set<String> refs = new HashSet<>();
		try {
			if (server && packs != null && fontOnlyOn()) {
				for (Object o : packs) {
					if (o instanceof PackResources pr && pr.packId() != null && pr.packId().startsWith("file/")) {
						for (String ns : pr.getNamespaces(PackType.CLIENT_RESOURCES)) {
							collectFontRefs(pr, ns, refs);
						}
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packUiGuard:fontRefs", t);
		}
		fontRefs = refs.isEmpty() ? Set.of() : refs;
		boolean was = serverLoaded;
		serverLoaded = server;
		if (server && !was && fontOnlyOn()) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 내 리소스팩 우선: 서버 팩이 있어 내 팩은 글꼴과 글만 씁니다");
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
		if ((s.isEmpty() && !serverLoaded) || self == null || id == null) {
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
		if (serverLoaded && fontOnlyOn()) {
			if (fontPath(id.getPath())) {
				return false;
			}
			String k = id.toString();
			Set<String> fr = fontRefs;
			return !(fr.contains(k) || (k.endsWith(".mcmeta") && fr.contains(k.substring(0, k.length() - 7))));
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
		return !serverUi.isEmpty() || (serverLoaded && fontOnlyOn());
	}
}
