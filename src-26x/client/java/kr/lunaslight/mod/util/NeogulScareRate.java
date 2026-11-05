package kr.lunaslight.mod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-572차(사용자: "허수아비 확률 계산기 넣어서 농사한 개수 비례 현재 본인 허수아비 등장 확률 계속 측정해 줘, 나온 것만으로도 해 줘야 해"):
 * <b>허수아비 확률 재기</b>.
 *
 * <p>너굴마을에서 다 자란 작물을 캘 때마다 1번(TodoGameModeMixin.destroyBlock), "허수아비가 나타났습니다" 채팅이 오면 허수아비 1번을 센다.
 * 확률 = 나온 허수아비 ÷ 캔 작물 - 내가 실제로 본 것만으로 잰다(스킬 "이리 오너라"로 오른 확률도 그대로 들어간다).
 * 저장: config/lunaslight/neogul-scarecrow.json. 다 자란 작물만 센다(위키: 밀, 벼(beetroots), 감자, 당근, 고추(nether_wart), 호박, 수박 - 벼와 고추는 age 3, 밀/감자/당근은 7, 호박/수박은 그대로).
 */
public final class NeogulScareRate {
	private NeogulScareRate() {
	}

	public static long harvests, scares, sinceLast;
	/** 이번 접속에서. */
	public static long sessHarvests, sessScares;
	private static boolean loaded, registered, dirty;
	private static long lastSave;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			ChatState.addPlainListener(NeogulScareRate::onChat);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> save());
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:init", t);
		}
	}

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-scarecrow.json");
	}

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path p = file();
			if (Files.exists(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
				harvests = o.has("harvests") ? o.get("harvests").getAsLong() : 0;
				scares = o.has("scares") ? o.get("scares").getAsLong() : 0;
				sinceLast = o.has("sinceLast") ? o.get("sinceLast").getAsLong() : 0;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:load", t);
		}
	}

	public static synchronized void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("harvests", harvests);
			o.addProperty("scares", scares);
			o.addProperty("sinceLast", sinceLast);
			Path p = file();
			Files.createDirectories(p.getParent());
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:save", t);
		}
	}

	public static synchronized void reset() {
		load();
		harvests = scares = sinceLast = sessHarvests = sessScares = 0;
		dirty = true;
		save();
	}

	/** TodoGameModeMixin: 블록을 캤다(캐기 전 상태). */
	public static void onBreak(BlockState st) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (st == null || !NeogulData.onNeogul(mc) || !matureCrop(st)) {
				return;
			}
			synchronized (NeogulScareRate.class) {
				load();
				harvests++;
				sinceLast++;
				sessHarvests++;
				dirty = true;
				long now = System.currentTimeMillis();
				if (now - lastSave > 30_000L) {
					lastSave = now;
					save();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:break", t);
		}
	}

	private static void onChat(String line) {
		if (line == null || !line.contains("허수아비") || !line.replace(" ", "").contains("나타났습니다")) {
			return;
		}
		if (line.contains("[전체]") || line.contains("[질문]") || line.contains("명)")) {
			return;   // 남이 채팅에 쓴 글
		}
		try {
			if (!NeogulData.onNeogul(Minecraft.getInstance())) {
				return;
			}
		} catch (Throwable ignored) {
			return;
		}
		synchronized (NeogulScareRate.class) {
			load();
			scares++;
			sessScares++;
			sinceLast = 0;
			dirty = true;
			save();
		}
	}

	/** 다 자란 작물인지(블록 상태 글자로 - 버전 무관). */
	static boolean matureCrop(BlockState st) {
		String s = String.valueOf(st);
		int a = s.indexOf('{'), b = s.indexOf('}');
		String id = a >= 0 && b > a ? s.substring(a + 1, b) : s;
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.equals("pumpkin") || path.equals("melon")) {
			return true;
		}
		int max;
		switch (path) {
			case "beetroots", "nether_wart" -> max = 3;   // 위키(허수아비): 밀, 벼, 감자, 당근, 고추, 호박, 수박
			case "wheat", "carrots", "potatoes" -> max = 7;
			default -> {
				return false;
			}
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("age=(\\d+)").matcher(s);
		return m.find() && Integer.parseInt(m.group(1)) >= max;
	}

	/** 지금까지 잰 확률(0~1). 허수아비가 아직 없으면 -1. */
	public static double rate() {
		load();
		return harvests > 0 && scares > 0 ? scares / (double) harvests : -1;
	}
}
