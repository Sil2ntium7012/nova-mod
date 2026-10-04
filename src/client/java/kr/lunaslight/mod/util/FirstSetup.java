package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.config.LunaClientConfig;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-213차(사용자: "클라이언트 처음 켰을 때, 서버로 켰더라도 이것 먼저 - 취향을 골라 설정을 바로 입혀 줘.
 * 한 번에 말고 몇 개씩 묶어서"): <b>처음 설정</b>.
 *
 * <h3>언제 뜨나</h3>
 * 이 컴퓨터에서 한 번도 고른 적이 없을 때(런처 폴더의 first-setup.json이 없을 때) 타이틀 화면 위에 뜬다.
 * 서버 모드로 켰으면 런처가 바로 접속하지 않고 그 서버를 {@code pendingServer}로 넘겨 주고(.luna-launch.json),
 * 설정을 마친 뒤 여기서 접속한다. 고른 값은 런처 폴더에 남아 <b>다른 프로필은 처음 켤 때 묻지 않고 그대로 입힌다</b>.
 *
 * <h3>무엇을 고르나</h3>
 * 글꼴, GUI 크기, 기능 보기(큰 박스/작은 박스/상세), HUD 배경(둥근/네모난/없음), 마우스 감도,
 * 화면 모드(창 화면/전체 화면/테두리 없는 전체 화면). 고르는 즉시 적용되어 바로 확인할 수 있다.
 *
 * <p>설정 화면(gui 패키지)이 없는 버전은 묻지 않고 넘어가며, 기다리던 서버가 있으면 바로 접속한다.
 */
public final class FirstSetup {
	private FirstSetup() {
	}

	public static final int WINDOWED = 0;
	public static final int FULLSCREEN = 1;
	public static final int BORDERLESS = 2;

	private static boolean checked;
	private static final String SCREEN_CLASS = "kr.lunaslight.mod.gui.LunaSetupScreen";

	// ==================== 파일 ====================

	private static Path marker() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("first_setup.json");
	}

	/** 런처 폴더의 공용 기록. 런처가 경로를 알려 주고, 옛 런처면 게임 폴더(profiles/이름)에서 두 단계 위. */
	private static Path global() {
		try {
			String p = LunaSocial.setupPrefsPath();
			if (p != null && !p.isEmpty()) {
				return Path.of(p);
			}
			Path game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toAbsolutePath();
			Path parent = game.getParent();
			if (parent != null && parent.getFileName() != null && "profiles".equalsIgnoreCase(parent.getFileName().toString())
					&& parent.getParent() != null) {
				return parent.getParent().resolve("first-setup.json");
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	private static JsonObject read(Path p) {
		try {
			if (p == null || !Files.exists(p)) {
				return null;
			}
			JsonElement e = new JsonParser().parse(Files.readString(p, StandardCharsets.UTF_8));
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static void write(Path p, JsonObject o) {
		try {
			if (p == null) {
				return;
			}
			Files.createDirectories(p.getParent());
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("firstSetup:write", t);
		}
	}

	// ==================== 틱 ====================

	/** 클라이언트 틱마다. 이미 했으면 바로 빠진다. */
	public static void tick(MinecraftClient client) {
		if (checked || client == null || client.options == null) {
			return;
		}
		try {
			if (Files.exists(marker())) {
				checked = true;
				return;
			}
			JsonObject prefs = read(global());
			if (prefs != null) {
				// 다른 프로필에서 이미 골랐다 - 묻지 않고 그대로 입힌다
				checked = true;
				apply(client, prefs);
				write(marker(), prefs);
				save(client);
				return;
			}
			Object screen = client.currentScreen;
			boolean atTitle = screen instanceof net.minecraft.client.gui.screen.TitleScreen
					|| (screen != null && screen.getClass().getName().endsWith(".LunaTitleScreen"));
			boolean inGame = screen == null && client.world != null;
			if (!atTitle && !inGame) {
				return;
			}
			checked = true;
			Class<?> cls;
			try {
				cls = Class.forName(SCREEN_CLASS);
			} catch (ClassNotFoundException noGui) {
				joinPending(client);
				return;
			}
			Object parentArg = screen;
			for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
				if (c.getParameterCount() == 1) {
					LunaCompat.setScreen(c.newInstance(parentArg));
					return;
				}
			}
			joinPending(client);
		} catch (Throwable t) {
			checked = true;
			LunaCompat.warnOnce("firstSetup:tick", t);
		}
	}

	// ==================== 지금 값 ====================

	public static JsonObject current(MinecraftClient client) {
		JsonObject o = new JsonObject();
		o.addProperty("font", enumIndex("interface_style", "font_v2"));
		o.addProperty("guiScale", guiScale(client));
		o.addProperty("tileView", LunaTheme.tileView());
		o.addProperty("hudShape", enumIndex("hud_background", "shape"));
		Double s = LunaCompat.getMouseSensitivity(client.options);
		o.addProperty("mouse", s == null ? 0.5 : s);
		o.addProperty("window", windowMode(client));
		return o;
	}

	// ==================== 하나씩 바로 적용 ====================

	public static void setFont(int index) {
		setEnum("interface_style", "font_v2", index);
	}

	public static void setTileView(int view) {
		LunaTheme.setTileView(view);
	}

	public static void setHudShape(int index) {
		setEnum("hud_background", "shape", index);
	}

	public static int guiScale(MinecraftClient client) {
		try {
			Object opts = client.options;
			Object so = LunaCompat.callNoArg(opts, "getGuiScale");
			if (so != null) {
				Object v = LunaCompat.callNoArg(so, "getValue");
				return v instanceof Integer i ? i : 0;
			}
			java.lang.reflect.Field f = LunaCompat.findField(opts.getClass(), "guiScale");
			if (f != null) {
				f.setAccessible(true);
				return f.getInt(opts);
			}
		} catch (Throwable ignored) {
		}
		return 0;
	}

	public static void setGuiScale(MinecraftClient client, int value) {
		try {
			Object opts = client.options;
			Object so = LunaCompat.callNoArg(opts, "getGuiScale");
			if (so != null) {
				LunaCompat.call1(so, "setValue", Integer.valueOf(value));
			} else {
				java.lang.reflect.Field f = LunaCompat.findField(opts.getClass(), "guiScale");
				if (f != null) {
					f.setAccessible(true);
					f.setInt(opts, value);
				}
			}
			LunaCompat.callNoArg(client, "onResolutionChanged");
		} catch (Throwable t) {
			LunaCompat.warnOnce("firstSetup:guiScale", t);
		}
	}

	public static void setMouse(MinecraftClient client, double value) {
		LunaCompat.setMouseSensitivity(client.options, Math.max(0.0, Math.min(1.0, value)));
	}

	/** 49-245차: 창 상태의 주인은 [창 모드](borderless_window) - 그쪽에 묻고 그쪽에 맡긴다. */
	public static int windowMode(MinecraftClient client) {
		return kr.lunaslight.mod.module.impl.render.BorderlessWindowModule.windowModeInt();
	}

	public static void setWindowMode(MinecraftClient client, int mode) {
		try {
			kr.lunaslight.mod.module.impl.render.BorderlessWindowModule.setWindowModeInt(mode);
		} catch (Throwable t) {
			LunaCompat.warnOnce("firstSetup:window", t);
		}
	}

	// ==================== 묶음 적용 / 마침 ====================

	public static void apply(MinecraftClient client, JsonObject p) {
		try {
			if (p.has("font")) {
				setFont(p.get("font").getAsInt());
			}
			if (p.has("tileView")) {
				setTileView(p.get("tileView").getAsInt());
			}
			if (p.has("hudShape")) {
				setHudShape(p.get("hudShape").getAsInt());
			}
			if (p.has("mouse")) {
				setMouse(client, p.get("mouse").getAsDouble());
			}
			if (p.has("guiScale")) {
				setGuiScale(client, p.get("guiScale").getAsInt());
			}
			if (p.has("window")) {
				setWindowMode(client, p.get("window").getAsInt());
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("firstSetup:apply", t);
		}
	}

	/** 설정 화면에서 [완료]. 고른 값은 이미 적용돼 있다 - 기록하고 저장하고, 기다리던 서버가 있으면 접속. */
	public static void finish(MinecraftClient client, JsonObject prefs) {
		prefs.addProperty("ts", System.currentTimeMillis());
		write(marker(), prefs);
		write(global(), prefs);
		save(client);
		joinPending(client);
	}

	private static void save(MinecraftClient client) {
		try {
			LunaClientConfig.save();
		} catch (Throwable ignored) {
		}
		try {
			LunaCompat.callNoArg(client.options, "write");
		} catch (Throwable ignored) {
		}
	}

	private static void joinPending(MinecraftClient client) {
		String server = LunaSocial.pendingServer();
		if (server == null || server.isEmpty()) {
			return;
		}
		LunaSocial.clearPendingServer();
		String label = LunaSocial.pendingServerName();
		LunaCompat.joinServer(client, server, label == null || label.isEmpty() ? server : label);
	}

	// ==================== 기능 설정 ====================

	private static int enumIndex(String moduleId, String settingId) {
		Setting<?> s = setting(moduleId, settingId);
		if (s instanceof EnumSetting<?> e) {
			Object v = e.get();
			return v instanceof Enum<?> en ? en.ordinal() : 0;
		}
		return 0;
	}

	private static void setEnum(String moduleId, String settingId, int index) {
		Setting<?> s = setting(moduleId, settingId);
		if (s instanceof EnumSetting<?> e) {
			e.setIndex(index);
		}
	}

	private static Setting<?> setting(String moduleId, String settingId) {
		Module m = ModuleManager.get().find(moduleId).orElse(null);
		if (m == null) {
			return null;
		}
		for (Setting<?> s : m.getSettings()) {
			if (settingId.equals(s.getId())) {
				return s;
			}
		}
		return null;
	}
}
