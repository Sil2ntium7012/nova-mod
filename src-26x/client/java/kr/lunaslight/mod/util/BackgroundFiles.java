package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * 49-492차(사용자: "우리 직접 그린 기능 배경 전체랑 개별 모두 파일로 뽑아서 보내고, 그 파일 받아서 적용시키기 가능하게"): 직접 그린 HUD 배경을
 * 파일(.json)로 내보내고 가져온다.
 *
 * <pre>
 * { "nova_backgrounds": 1,
 *   "single": "그림",                                  ← 하나(지금 편집 중인 그림)만 내보낸 파일
 *   "modules": { "기능 id": { "name": "이름", "current": "그림", "slots": ["그림", "", ""] } } }   ← 전체
 * </pre>
 * 그림 = PixelArt.encode() 문자열. 파일 고르기/저장 창은 마크에 들어 있는 LWJGL tinyfd(윈도우 기본 창)를 리플렉션으로 쓰고, 없으면
 * config/lunaslight/backgrounds 폴더에 쓰고(가져오기는 그 폴더의 가장 최근 파일) 폴더를 연다. 창은 따로 도는 스레드에서 띄우고 결과는
 * 게임 스레드로 넘긴다.
 */
public final class BackgroundFiles {
	private BackgroundFiles() {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path folder() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("backgrounds");
	}

	// ==================== 만들기 ====================

	public static JsonObject single(String art) {
		JsonObject o = new JsonObject();
		o.addProperty("nova_backgrounds", 1);
		o.addProperty("single", art == null ? "" : art);
		return o;
	}

	public static JsonObject all() {
		JsonObject o = new JsonObject();
		o.addProperty("nova_backgrounds", 1);
		JsonObject mods = new JsonObject();
		for (Module m : ModuleManager.get().all()) {
			if (!m.hasHudStyle()) {
				continue;
			}
			String cur = m.hudPixelsRaw();
			JsonArray slots = new JsonArray();
			boolean any = cur != null && !cur.isEmpty();
			for (int i = 0; i < 3; i++) {
				String s = m.hudSlotRaw(i);
				slots.add(s == null ? "" : s);
				any |= s != null && !s.isEmpty();
			}
			if (!any) {
				continue;
			}
			JsonObject e = new JsonObject();
			e.addProperty("name", m.getDisplayName());
			e.addProperty("current", cur == null ? "" : cur);
			e.add("slots", slots);
			mods.add(m.getId(), e);
		}
		o.add("modules", mods);
		return o;
	}

	/** 전체 파일에 든 기능 수. */
	public static int moduleCount(JsonObject o) {
		return o != null && o.has("modules") && o.get("modules").isJsonObject() ? o.getAsJsonObject("modules").size() : 0;
	}

	/** 전체 적용(그림, 슬롯을 덮어씀). 적용한 기능 수. */
	public static int applyAll(JsonObject o) {
		int n = 0;
		if (o == null || !o.has("modules")) {
			return 0;
		}
		for (var en : o.getAsJsonObject("modules").entrySet()) {
			Module m = ModuleManager.get().find(en.getKey()).orElse(null);
			if (m == null || !m.hasHudStyle() || !en.getValue().isJsonObject()) {
				continue;
			}
			JsonObject e = en.getValue().getAsJsonObject();
			if (e.has("current")) {
				m.setHudPixelsRaw(valid(e.get("current").getAsString()));
			}
			if (e.has("slots") && e.get("slots").isJsonArray()) {
				JsonArray a = e.getAsJsonArray("slots");
				for (int i = 0; i < 3 && i < a.size(); i++) {
					m.setHudSlotRaw(i, valid(a.get(i).getAsString()));
				}
			}
			n++;
		}
		kr.lunaslight.mod.config.LunaClientConfig.save();
		return n;
	}

	/** 하나만 쓸 그림: single이면 그것, 전체 파일이면 그 기능의 지금 그림(없으면 첫 그림). */
	public static String pickOne(JsonObject o, String moduleId) {
		if (o == null) {
			return null;
		}
		if (o.has("single")) {
			return valid(o.get("single").getAsString());
		}
		if (o.has("modules")) {
			JsonObject mods = o.getAsJsonObject("modules");
			JsonElement e = moduleId == null ? null : mods.get(moduleId);
			if (e == null && mods.size() > 0) {
				e = mods.entrySet().iterator().next().getValue();
			}
			if (e != null && e.isJsonObject() && e.getAsJsonObject().has("current")) {
				return valid(e.getAsJsonObject().get("current").getAsString());
			}
		}
		return null;
	}

	private static String valid(String s) {
		return s == null || s.isEmpty() || PixelArt.decode(s) != null ? (s == null ? "" : s) : "";
	}

	// ==================== 파일 ====================

	/** 저장 창을 띄워 쓴다. done(메시지)은 게임 스레드에서. */
	public static void exportFile(JsonObject data, String baseName, Consumer<String> done) {
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmm"));
		String fileName = baseName + "-" + stamp + ".json";
		Thread t = new Thread(() -> {
			String msg;
			try {
				Files.createDirectories(folder());
				Path def = folder().resolve(fileName);
				String chosen = tinyfd("tinyfd_saveFileDialog", "Nova 배경 내보내기", def.toString());
				Path out = chosen == null ? (tinyfdOk ? null : def) : Path.of(chosen);
				if (out == null) {
					msg = null;   // 창에서 취소
				} else {
					if (!out.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
						out = out.resolveSibling(out.getFileName() + ".json");
					}
					Files.writeString(out, GSON.toJson(data), StandardCharsets.UTF_8);
					msg = "내보냈어요: " + out.getFileName();
					if (!tinyfdOk) {
						openFolder();
					}
				}
			} catch (Throwable e) {
				msg = "내보내지 못했어요: " + e.getMessage();
			}
			String m = msg;
			Minecraft.getInstance().execute(() -> done.accept(m));
		}, "Nova background export");
		t.setDaemon(true);
		t.start();
	}

	/** 열기 창으로 고른 파일을 읽는다(없으면 폴더의 가장 최근 .json). done(내용 또는 null, 메시지). */
	public static void importFile(java.util.function.BiConsumer<JsonObject, String> done) {
		Thread t = new Thread(() -> {
			JsonObject o = null;
			String msg = null;
			try {
				Files.createDirectories(folder());
				String chosen = tinyfd("tinyfd_openFileDialog", "Nova 배경 가져오기", folder().toString() + java.io.File.separator);
				Path in = chosen != null ? Path.of(chosen) : (tinyfdOk ? null : newest());
				if (in == null) {
					msg = tinyfdOk ? null : "backgrounds 폴더에 가져올 파일(.json)을 넣어 주세요";
					if (!tinyfdOk) {
						openFolder();
					}
				} else {
					JsonElement e = JsonParser.parseString(Files.readString(in, StandardCharsets.UTF_8));
					if (e.isJsonObject() && e.getAsJsonObject().has("nova_backgrounds")) {
						o = e.getAsJsonObject();
					} else {
						msg = "Nova 배경 파일이 아니에요";
					}
				}
			} catch (Throwable e) {
				msg = "읽지 못했어요: " + e.getMessage();
			}
			JsonObject r = o;
			String m = msg;
			Minecraft.getInstance().execute(() -> done.accept(r, m));
		}, "Nova background import");
		t.setDaemon(true);
		t.start();
	}

	private static Path newest() throws Exception {
		try (var s = Files.list(folder())) {
			return s.filter(p -> p.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
				.max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
		}
	}

	private static void openFolder() {
		// 26.3에는 Util.OS.openPath가 없어서 운영체제 명령으로 연다
		try {
			String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
			String path = folder().toAbsolutePath().toString();
			if (os.startsWith("windows")) {
				new ProcessBuilder("explorer.exe", path).start();
			} else if (os.contains("mac")) {
				new ProcessBuilder("open", path).start();
			} else {
				new ProcessBuilder("xdg-open", path).start();
			}
		} catch (Throwable ignored) {
		}
	}

	/** tinyfd를 썼는지(못 쓰면 폴더 방식). */
	private static volatile boolean tinyfdOk;

	/** tinyfd 저장/열기 창. 취소면 null. tinyfd가 없으면 tinyfdOk=false로 null. */
	private static String tinyfd(String method, String title, String defaultPath) {
		try {
			Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
			Class<?> pb = Class.forName("org.lwjgl.PointerBuffer");
			Object r;
			if (method.equals("tinyfd_saveFileDialog")) {
				r = c.getMethod(method, CharSequence.class, CharSequence.class, pb, CharSequence.class)
					.invoke(null, title, defaultPath, null, null);
			} else {
				r = c.getMethod(method, CharSequence.class, CharSequence.class, pb, CharSequence.class, boolean.class)
					.invoke(null, title, defaultPath, null, null, false);
			}
			tinyfdOk = true;
			return r == null ? null : r.toString();
		} catch (Throwable t) {
			tinyfdOk = false;
			LunaCompat.warnOnce("bgFiles:tinyfd", t);
			return null;
		}
	}
}
