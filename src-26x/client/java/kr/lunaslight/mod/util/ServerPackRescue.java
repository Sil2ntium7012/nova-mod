package kr.lunaslight.mod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 49-477차(사용자: "나갔다 왔을 때 서버 리소스팩이 아예 다운로드가 안 되는 경우가 있어"): <b>서버 팩 다시 받기</b>.
 * 로그(10-31 19:42): 나갔다 바로 다시 들어가면 서버(프록시)가 리소스팩을 아예 안 보내는 때가 있다(팩 받기 줄이 하나도 없음) - 그러면
 * 서버 팩 없이 바닐라 그림으로 논다. 그래서 서버가 보낸 마지막 팩(서버 주소별 id, 주소, 해시)을 기억해 두고, 서버에 들어간 뒤 정해진
 * 시간(기본 15초) 동안 서버 팩이 하나도 안 켜져 있으면(로딩 중이 아닐 때) 그 팩을 이쪽에서 다시 받게 한다(바닐라 DownloadedPackSource.pushPack
 * + allowServerPacks - 받아 둔 파일과 해시가 같으면 다시 받지 않고 바로 씀). 한 번 접속에 한 번만.
 * 저장: config/lunaslight/server-pack-last.json. 서버 팩을 거절하도록 해 둔 서버는 건드리지 않는다.
 */
public final class ServerPackRescue {
	private ServerPackRescue() {
	}

	/** ServerPackRescueModule이 끼운다. */
	public static volatile boolean on = true;
	public static volatile int waitSeconds = 15;

	private static volatile Object pushConn;
	private static Object conn;
	private static int ticks;
	private static boolean done;
	private static JsonObject store;

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("server-pack-last.json");
	}

	private static synchronized JsonObject store() {
		if (store == null) {
			store = new JsonObject();
			try {
				Path f = file();
				if (Files.isRegularFile(f)) {
					store = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				}
			} catch (Throwable t) {
				store = new JsonObject();
			}
		}
		return store;
	}

	private static String server(Minecraft mc) {
		String a = LunaCompat.currentServerAddress(mc);
		return a == null ? "" : a.toLowerCase(java.util.Locale.ROOT).trim();
	}

	/** ServerPackRescueMixin: 서버가 팩을 보냈다(네티 스레드와 렌더 스레드에서 두 번 올 수 있다). */
	public static void onPush(UUID id, String url, String hash) {
		Minecraft mc = Minecraft.getInstance();
		pushConn = mc.getConnection();
		String s = server(mc);
		if (s.isEmpty() || id == null || url == null) {
			return;
		}
		synchronized (ServerPackRescue.class) {
			JsonObject o = new JsonObject();
			o.addProperty("id", id.toString());
			o.addProperty("url", url);
			o.addProperty("hash", hash == null ? "" : hash);
			JsonObject st = store();
			if (o.equals(st.get(s))) {
				return;
			}
			st.add(s, o);
			try {
				Path f = file();
				Files.createDirectories(f.getParent());
				Files.writeString(f, st.toString(), StandardCharsets.UTF_8);
			} catch (Throwable t) {
				LunaCompat.warnOnce("packRescue:save", t);
			}
		}
	}

	/** 매 틱(모듈). */
	public static void tick(Minecraft mc) {
		Object c = mc == null ? null : mc.getConnection();
		if (c != conn) {
			conn = c;
			ticks = 0;
			done = false;
		}
		if (!on || c == null || done || mc.player == null || mc.hasSingleplayerServer()) {
			return;
		}
		if (reloading(mc)) {
			return;   // 리소스 새로 읽는 중
		}
		if (++ticks < Math.max(5, waitSeconds) * 20) {
			return;
		}
		done = true;
		try {
			if (serverPackActive(mc)) {
				return;
			}
			String s = server(mc);
			JsonObject o;
			synchronized (ServerPackRescue.class) {
				o = store().has(s) ? store().getAsJsonObject(s) : null;
			}
			if (o == null) {
				return;
			}
			var sd = mc.getCurrentServer();
			if (sd != null && sd.getResourcePackStatus() == net.minecraft.client.multiplayer.ServerData.ServerPackStatus.DISABLED) {
				return;   // 서버 팩 끄기로 해 둔 서버
			}
			UUID id = UUID.fromString(o.get("id").getAsString());
			java.net.URL url = java.net.URI.create(o.get("url").getAsString()).toURL();
			String hash = o.get("hash").getAsString();
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 서버 리소스팩이 " + waitSeconds + "초 동안 안 와서(" + (pushConn == c ? "받았는데 안 켜짐" : "안 보냄")
				+ ") 지난번 팩을 다시 받습니다: " + s);
			mc.getDownloadedPackSource().pushPack(id, url, hash);
			mc.getDownloadedPackSource().allowServerPacks();
			LunaCompat.sendActionBar(mc, "§e[Nova] §f서버 리소스팩이 안 와서 다시 받는 중이에요");
		} catch (Throwable t) {
			LunaCompat.warnOnce("packRescue:push", t);
		}
	}

	private static java.lang.reflect.Method overlayGetter;
	private static boolean overlayLooked;

	/** 로딩 겹화면이 떠 있는가(26.1~26.2 Minecraft.getOverlay, 26.3은 없어서 false). */
	private static boolean reloading(Minecraft mc) {
		if (!overlayLooked) {
			overlayLooked = true;
			try {
				overlayGetter = Minecraft.class.getMethod("getOverlay");
			} catch (Throwable t) {
				overlayGetter = null;
			}
		}
		try {
			return overlayGetter != null && overlayGetter.invoke(mc) != null;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean serverPackActive(Minecraft mc) {
		try {
			for (String id : mc.getResourcePackRepository().getSelectedIds()) {
				if (PackOrderHook.isServerPackId(id)) {
					return true;
				}
			}
		} catch (Throwable t) {
			return true;   // 모르면 건드리지 않는다
		}
		return false;
	}
}
