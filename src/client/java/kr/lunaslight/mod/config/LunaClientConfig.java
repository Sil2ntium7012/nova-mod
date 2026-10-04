package kr.lunaslight.mod.config;

import com.google.gson.*;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/lunaslight/modules.json 하나에 전체 모듈의 on/off + 세부 설정값을 저장.
 * 구조:
 * {
 *   "cps_hud": { "enabled": true, "settings": { "position": {...}, "textColor": -1 } },
 *   ...
 * }
 */
public final class LunaClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private LunaClientConfig() {
	}

	/**
	 * 49-21차: HUD 기본 배치 버전. 저장 파일의 값이 이보다 낮으면 불러온 뒤 **모든 위치 설정만**
	 * 새 기본값으로 되돌린다(다른 설정은 그대로). 기본 배치를 다시 짤 때(사용자: "컴퓨터/FPS류는
	 * 왼쪽 위, 기능은 왼쪽 중간이나 핫바 근처") 이 숫자를 올리면 기존 사용자도 새 배치를 받는다.
	 */
	private static final int LAYOUT_VERSION = 8;   // 49-77차: 겹치던 기본 자리 정리(앵커별 스택) - 표는 claude/nova-mod-todo-20.md
	private static final String LAYOUT_KEY = "_layout_version";

	/**
	 * 49-37차: 글꼴 기본값 이전. [마크 기본]은 한글 글리프가 없어 어느 버전에서든 유니폰트(가늘고 긴 옛 글꼴)로
	 * 떨어지는데, 예전 기본값이 그거라 저장된 설정 파일마다 "MC"가 박혀 있다(사용자: "글씨가 여전히 이상하잖아").
	 * 저장 파일의 이 버전이 낮고 글꼴이 MC면 **한 번만** [마크 + 한글 픽셀]로 바꾼다 - 그 뒤 직접 MC를 고르면 유지.
	 */
	private static final int FONT_VERSION = 2;   // 49-39차: 2 = 체력 모듈(층 하트)로 성격이 바뀌어 한 번 켬
	private static final String FONT_KEY = "_font_version";

	/**
	 * 49-53차(4-37): 기본 키가 바뀌었을 때 한 번만 옮겨 주는 장치.
	 *
	 * <p>설정 파일에 저장된 값이 항상 기본값을 이기기 때문에, 기본 키만 바꾸면 이미 한 번이라도
	 * 게임을 켠 사람에게는 <b>영영 안 보인다</b>(HUD 위치는 LAYOUT_VERSION이 같은 일을 하고 있었는데
	 * 키에는 그런 게 없었다). 다만 사용자가 직접 지정해 둔 키를 빼앗으면 안 되므로,
	 * <b>아직 비어 있는(지정 안 된) 키만</b> 새 기본값으로 채운다.
	 */
	private static final int KEY_VERSION = 1;   // 49-53차: 핑 X · 시점 전환 H · 일괄정리 R · 버리기 G
	private static final String KEY_KEY = "_key_version";
	/** 49-157차: 한 번만 하는 기본값 바꾸기(아이템 찾기 기본 꺼짐)를 했는지. */
	private static final String DEFAULTS_KEY = "_defaults_version";
	private static final int DEFAULTS_VERSION = 2;

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("modules.json");
	}

	// 35차: JsonParser.parseReader(Reader)는 Gson 2.8.9(2021년)에 추가된 정적 메서드라 마인크래프트가
	// 그보다 오래된 Gson을 번들한 구버전에선 없을 수 있음 - 대신 deprecated된 인스턴스 메서드
	// new JsonParser().parse(Reader)를 사용(Gson 2.13.1까지도 계속 남아있는 걸 확인했고, 모든
	// 마인크래프트 번들 Gson 버전에서 안전하게 동작 - claude/nova-mod-todo.md 35차).
	@SuppressWarnings("deprecation")
	public static void load() {
		Path path = configFile();
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			for (Module module : ModuleManager.get().all()) {
				if (!root.has(module.getId())) {
					continue;
				}
				JsonObject moduleObj = root.getAsJsonObject(module.getId());
				if (moduleObj.has("enabled")) {
					module.setEnabledSilently(moduleObj.get("enabled").getAsBoolean());
				}
				if (moduleObj.has("settings")) {
					JsonObject settingsObj = moduleObj.getAsJsonObject("settings");
					for (Setting<?> setting : module.getSettings()) {
						if (settingsObj.has(setting.getId())) {
							setting.fromJson(settingsObj.get(setting.getId()));
						}
					}
				}
			}
			int savedLayout = root.has(LAYOUT_KEY) ? root.get(LAYOUT_KEY).getAsInt() : 1;
			if (savedLayout < LAYOUT_VERSION) {
				int reset = 0;
				for (Module module : ModuleManager.get().all()) {
					for (Setting<?> setting : module.getSettings()) {
						if (setting instanceof kr.lunaslight.mod.module.setting.PositionSetting) {
							setting.resetToDefault();
							reset++;
						}
					}
				}
				LunaClientMod.LOGGER.info("[Nova] HUD 기본 배치가 바뀌어(" + savedLayout + " → " + LAYOUT_VERSION
					+ ") 위치 설정 " + reset + "개를 새 기본값으로 되돌렸습니다.");
			}
			int savedKey = root.has(KEY_KEY) ? root.get(KEY_KEY).getAsInt() : 0;
			if (savedKey < KEY_VERSION) {
				int moved = 0;
				for (Module module : ModuleManager.get().all()) {
					for (Setting<?> setting : module.getSettings()) {
						if (setting instanceof kr.lunaslight.mod.module.setting.KeybindSetting kb && !kb.isBound()) {
							kb.resetToDefault();
							if (kb.isBound()) {
								moved++;
							}
						}
					}
				}
				if (moved > 0) {
					LunaClientMod.LOGGER.info("[Nova] 기본 키가 생겨서 비어 있던 키 " + moved + "개를 채웠습니다.");
				}
			}
			int savedFont = root.has(FONT_KEY) ? root.get(FONT_KEY).getAsInt() : 0;
			for (Module module : ModuleManager.get().all()) {
				if (savedFont < 1 && "interface_style".equals(module.getId())) {
					for (Setting<?> setting : module.getSettings()) {
						if ("font".equals(setting.getId()) && setting.getValue() instanceof Enum<?> e && "MC".equals(e.name())) {
							setting.fromJson(new com.google.gson.JsonPrimitive("MC_HANGUL"));
							LunaClientMod.LOGGER.info("[Nova] 글꼴 기본값이 바뀌어 [마크 기본] → [마크 + 한글 픽셀]로 한 번 옮겼습니다.");
						}
					}
				}
				// 49-39차: 체력 모듈이 "하트 옆 숫자"에서 "한 줄 색 층 하트"로 바뀜 - 예전에 꺼 둔 사람도 한 번 켜 준다.
				if (savedFont < 2 && "simple_health_hud".equals(module.getId()) && !module.isEnabled()) {
					module.setEnabledSilently(true);
					LunaClientMod.LOGGER.info("[Nova] 체력 모듈이 층 하트로 바뀌어 한 번 켰습니다.");
				}
			}
			// 49-89차(8-6·8-8): 옛 "내 키"(user_keybinds)·"명령어 키"(custom_keybinds)의 값을 새 [단축키]로 한 번 옮긴다.
			ModuleManager.get().find("shortcuts").ifPresent(m -> {
				if (m instanceof kr.lunaslight.mod.module.impl.misc.ShortcutsModule sc) {
					if (!root.has("shortcuts")) {
						int moved = 0;
						moved += importLegacyKeys(root, "user_keybinds", "key", "text", sc);
						moved += importLegacyKeys(root, "custom_keybinds", "slot%d_key", "slot%d_command", sc);
						if (moved > 0) {
							LunaClientMod.LOGGER.info("[Nova] 내 키·명령어 키 " + moved + "개를 [단축키]로 옮겼습니다.");
						}
					}
					sc.afterLoad();
				}
			});
			migrateMerged(root);
			// 49-157차(사용자: "아이템 찾기 기능 기본 비활성화"): 기본값을 끔으로 바꾸면서, 이미 켜진 채 저장된 것도 한 번 끈다.
			int savedDefaults = root.has(DEFAULTS_KEY) ? root.get(DEFAULTS_KEY).getAsInt() : 0;
			if (savedDefaults < 1) {
				ModuleManager.get().find("item_finder").ifPresent(m -> m.setEnabledSilently(false));
			}
			// 49-194차(사용자: "이런 느낌으로 블록 정보를 바꿔줘" - Jade 사진): 블록 정보 판의 기본 배경이 보라빛 판 + 보라 윤곽선 +
			// 둥근 모양으로 바뀌었다. 예전 기본값으로 저장된 배경 설정을 한 번 새 기본값으로 되돌린다.
			if (savedDefaults < 2) {
				ModuleManager.get().find("block_info_hud").ifPresent(m -> {
					for (Setting<?> setting : m.getSettings()) {
						if (setting.getId().startsWith("hud_bg") && !"hud_bg_pixels".equals(setting.getId())) {
							setting.resetToDefault();
						}
					}
				});
			}
			LunaClientMod.LOGGER.info("[Nova] 설정 불러오기 완료: " + path);
		} catch (Exception e) {
			LunaClientMod.LOGGER.error("[Nova] 설정 파일을 읽는 중 문제가 생겨서 기본값으로 시작합니다.", e);
		}
	}

	/**
	 * 49-157차(사용자: "일반 설정이랑 기능에 겹치는 거 한 곳만"): 합쳐서 없어진 기능의 설정을 새 자리로 한 번 옮긴다.
	 * 옛 id는 다음 저장 때 파일에서 사라지므로 두 번 옮겨지지 않는다(새 자리에 값이 이미 있으면 건드리지 않음).
	 *  · player_block(일반 > 유저 차단) → chat_enhancements의 block / block_players / block_any_line / block_hide_skin
	 *  · (49-179차) key_item_count의 held → 되살린 held_item_counter(손 아이템 개수)를 켬
	 */
	private static void migrateMerged(JsonObject root) {
		try {
			if (root.has("player_block") && !hasSetting(root, "chat_enhancements", "block_players")) {
				JsonObject pb = root.getAsJsonObject("player_block");
				JsonObject ps = pb.has("settings") ? pb.getAsJsonObject("settings") : new JsonObject();
				boolean on = pb.has("enabled") && pb.get("enabled").getAsBoolean();
				ModuleManager.get().find("chat_enhancements").ifPresent(m -> {
					setById(m, "block", new JsonPrimitive(on));
					if (ps.has("players")) {
						setById(m, "block_players", ps.get("players"));
					}
					if (ps.has("any_line")) {
						setById(m, "block_any_line", ps.get("any_line"));
					}
					if (ps.has("hide_skin")) {
						setById(m, "block_hide_skin", ps.get("hide_skin"));
					}
				});
				LunaClientMod.LOGGER.info("[Nova] [유저 차단] 설정을 [채팅 > 차단]으로 옮겼습니다.");
			}
			// 49-179차: [손 아이템 개수]를 다시 따로 된 기능으로 되살렸다 - 49-157차에 옮겨 둔 [소모품 개수]의 held가 켜져
			// 있었으면(옛 held_item_counter 값이 없을 때만) 되살린 기능을 켠다. held 값은 다음 저장 때 파일에서 사라진다.
			if (!root.has("held_item_counter") && hasSetting(root, "key_item_count", "held")) {
				JsonElement hv = root.getAsJsonObject("key_item_count").getAsJsonObject("settings").get("held");
				boolean on = false;
				try {
					on = hv != null && hv.isJsonPrimitive() && hv.getAsBoolean();
				} catch (Throwable ignored) {
				}
				if (on) {
					ModuleManager.get().find("held_item_counter").ifPresent(m -> m.setEnabledSilently(true));
					LunaClientMod.LOGGER.info("[Nova] [소모품 개수 > 손 아이템]을 [HUD > 손 아이템 개수]로 옮겼습니다.");
				}
			}
		} catch (Throwable t) {
			LunaClientMod.LOGGER.warn("[Nova] 합친 기능 설정 옮기기 실패", t);
		}
	}

	private static boolean hasSetting(JsonObject root, String moduleId, String settingId) {
		return root.has(moduleId) && root.getAsJsonObject(moduleId).has("settings")
			&& root.getAsJsonObject(moduleId).getAsJsonObject("settings").has(settingId);
	}

	private static void setById(Module m, String id, JsonElement value) {
		for (Setting<?> setting : m.getSettings()) {
			if (id.equals(setting.getId())) {
				setting.fromJson(value);
				return;
			}
		}
	}

	/** 옛 모듈 json의 (키 id 패턴, 글 id 패턴) 쌍을 1~16번까지 훑어 단축키로 넣는다. 패턴에 %d가 없으면 뒤에 번호를 붙인다. */
	private static int importLegacyKeys(JsonObject root, String moduleId, String keyPat, String textPat,
			kr.lunaslight.mod.module.impl.misc.ShortcutsModule sc) {
		if (!root.has(moduleId) || !root.getAsJsonObject(moduleId).has("settings")) {
			return 0;
		}
		JsonObject st = root.getAsJsonObject(moduleId).getAsJsonObject("settings");
		int moved = 0;
		for (int n = 1; n <= 16; n++) {
			String kid = keyPat.contains("%d") ? String.format(keyPat, n) : keyPat + n;
			String tid = textPat.contains("%d") ? String.format(textPat, n) : textPat + n;
			int key = -1;
			String text = null;
			try {
				if (st.has(kid)) {
					kr.lunaslight.mod.module.setting.KeybindSetting tmp = new kr.lunaslight.mod.module.setting.KeybindSetting("tmp", "", "", -1);
					tmp.fromJson(st.get(kid));
					key = tmp.getKeyCode();
				}
				if (st.has(tid) && st.get(tid).isJsonPrimitive()) {
					text = st.get(tid).getAsString();
				}
			} catch (Exception ignored) {
				continue;
			}
			if (key >= 0 || (text != null && !text.isBlank())) {
				sc.importLegacy(key, text);
				moved++;
			}
		}
		return moved;
	}

	public static void save() {
		Path path = configFile();
		JsonObject root = new JsonObject();
		root.addProperty(LAYOUT_KEY, LAYOUT_VERSION);
		root.addProperty(FONT_KEY, FONT_VERSION);
		root.addProperty(KEY_KEY, KEY_VERSION);
		root.addProperty(DEFAULTS_KEY, DEFAULTS_VERSION);
		for (Module module : ModuleManager.get().all()) {
			JsonObject moduleObj = new JsonObject();
			moduleObj.addProperty("enabled", module.isEnabled());
			JsonObject settingsObj = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				settingsObj.add(setting.getId(), setting.toJson());
			}
			moduleObj.add("settings", settingsObj);
			root.add(module.getId(), moduleObj);
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			LunaClientMod.LOGGER.error("[Nova] 설정 저장 실패", e);
		}
	}
}
