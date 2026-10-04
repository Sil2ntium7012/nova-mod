package kr.lunaslight.mod.util;

import net.fabricmc.api.ClientModInitializer;

/**
 * 24-310차(런처 세션): 런처 상점에서 산 인게임 전용 UI(화면 스킨)를 연다.
 *
 * 런처가 실행 직전에 게임 폴더에 .luna-unlocks.json 을 쓴다:
 *   { "ts": 1234, "sig2": "<Ed25519(NovaSig|v1|unlocks|ts|cream|default)>", "uiSkins": ["cream"], "equippedUiSkin": "default" }
 * 서명이 맞으면 uiSkins 에 있는 스킨만 열고(LunaTheme.Skin 이름 소문자), 나머지는 기본으로 보인다.
 * 파일이 없거나 서명이 틀리면 기본 스킨만. 개발 실행(gradlew runClient)은 손대지 않음(전부 열림).
 * 24-316차: "equippedUiSkin" 이 있으면 런처에서 착용한 스킨을 [UI] > 화면 스킨(ui_skin) 설정에 그대로 넣는다
 * (안 끼었으면 "default"). 런처 착용이 기준이라 게임을 켤 때마다 맞춘다.
 * LunaClientMod 다음 client 엔트리포인트(fabric.mod.json)로 불린다. 마크 클래스는 안 써서 모든 버전 공용.
 */
public class ShopUnlocks implements ClientModInitializer {
	private static final java.util.Set<String> UI_SKINS = new java.util.HashSet<>();
	private static String equippedUiSkin;   // null = 옛 런처(손대지 않음)

	@Override
	public void onInitializeClient() {
		try {
			net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			if (loader.isDevelopmentEnvironment()) {
				return;
			}
			load(loader.getGameDir().resolve(".luna-unlocks.json"));
		} catch (Throwable ignored) {
		}
		apply();
	}

	/** 그 스킨을 가졌는지(기본은 늘 가짐). */
	public static boolean ownsUiSkin(String name) {
		return name == null || "default".equalsIgnoreCase(name) || UI_SKINS.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	private static void load(java.nio.file.Path p) {
		UI_SKINS.clear();
		equippedUiSkin = null;
		try {
			if (!java.nio.file.Files.exists(p)) {
				return;
			}
			String raw = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
			// 49-259차: 런처의 Ed25519 서명(sig2)으로 확인 - 서명 글자에 해금 목록과 착용 스킨이 들어 있어 내용만 바꿀 수 없다
			java.util.regex.Matcher ts = java.util.regex.Pattern.compile("\"ts\"\\s*:\\s*(\\d+)").matcher(raw);
			java.util.regex.Matcher sig = java.util.regex.Pattern.compile("\"sig2\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").matcher(raw);
			if (!ts.find() || !sig.find()) {
				return;
			}
			String eqName = "default";
			java.util.regex.Matcher eq = java.util.regex.Pattern.compile("\"equippedUiSkin\"\\s*:\\s*\"([A-Za-z0-9_\\-]+)\"").matcher(raw);
			if (eq.find()) {
				eqName = eq.group(1);
			}
			java.util.List<String> skins = new java.util.ArrayList<>();
			java.util.regex.Matcher arr = java.util.regex.Pattern.compile("\"uiSkins\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(raw);
			if (arr.find()) {
				java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([A-Za-z0-9_\\-]+)\"").matcher(arr.group(1));
				while (m.find()) {
					skins.add(m.group(1));
				}
			}
			if (!kr.lunaslight.mod.LunaClientMod.verifySig2("unlocks", Long.parseLong(ts.group(1)), String.join(",", skins) + "|" + eqName, sig.group(1))) {
				return;
			}
			equippedUiSkin = eqName.toLowerCase(java.util.Locale.ROOT);
			for (String sk : skins) {
				UI_SKINS.add(sk.toLowerCase(java.util.Locale.ROOT));
			}
		} catch (Throwable ignored) {
		}
	}

	private static void apply() {
		try {
			LunaTheme.setSkinOwnership(s -> s == LunaTheme.Skin.DEFAULT || UI_SKINS.contains(s.name().toLowerCase(java.util.Locale.ROOT)));
		} catch (Throwable ignored) {
			// refresh()가 아직 이를 때 실패해도 판별식은 이미 들어갔다(setSkinOwnership 첫 줄)
		}
		if (equippedUiSkin == null) {
			return;
		}
		LunaTheme.Skin want = LunaTheme.Skin.DEFAULT;
		for (LunaTheme.Skin s : LunaTheme.Skin.values()) {
			if (s.name().equalsIgnoreCase(equippedUiSkin) && (s == LunaTheme.Skin.DEFAULT || UI_SKINS.contains(equippedUiSkin))) {
				want = s;
			}
		}
		final LunaTheme.Skin chosen = want;
		try {
			// InterfaceStyleModule 이 매 틱 ui_skin 설정값으로 LunaTheme.setSkin 을 하므로 설정값 자체를 바꾼다
			kr.lunaslight.mod.module.ModuleManager.get().find("interface_style").ifPresent(m -> {
				for (kr.lunaslight.mod.module.setting.Setting<?> st : m.getSettings()) {
					if ("ui_skin".equals(st.getId()) && st instanceof kr.lunaslight.mod.module.setting.EnumSetting) {
						((kr.lunaslight.mod.module.setting.EnumSetting<?>) st).setIndex(chosen.ordinal());
					}
				}
			});
		} catch (Throwable ignored) {
		}
		try {
			LunaTheme.setSkin(chosen);
		} catch (Throwable ignored) {
		}
	}
}
