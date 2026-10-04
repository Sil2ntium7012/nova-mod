package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-74차(4-5): <b>지금 듣고 있는 노래</b> - 런처가 적어 준 것을 읽기만 한다.
 *
 * <h3>왜 모드가 직접 못 읽는가</h3>
 * "무슨 노래가 나오고 있나"는 <b>운영체제가 갖고 있는 정보</b>다(윈도우의 미디어 세션 -
 * Spotify·유튜브·윈도우 미디어 플레이어가 여기에 곡 정보를 올린다). 자바 표준에는 그걸 물어보는
 * 길이 <b>아예 없고</b>, 만들려면 네이티브 라이브러리를 동봉해야 한다 - 40개 버전에 얹을 짐으로는 너무 크다.
 *
 * <p>그래서 <b>이미 윈도우 프로그램인 런처</b>가 물어보고, 게임 폴더에 {@code .luna-now-playing.json}
 * 한 줄을 남긴다. 모드는 그 파일만 읽는다. 런처가 그 일을 안 하고 있으면(옛 런처, 런처 없이 실행,
 * 윈도우가 아닌 환경) 파일이 없고 - <b>그러면 아무것도 표시하지 않는다.</b> 없는 걸 지어내지 않는다.
 *
 * <h3>성능</h3>
 * 파일 읽기는 전부 <b>다른 스레드</b>에서 돈다. 렌더 스레드가 하는 일은 {@code volatile} 문자열
 * 두 개를 읽는 것뿐이다. 간격은 2초 - 곡이 바뀌는 속도엔 충분하고 디스크는 거의 안 건드린다
 * (파일이 3KB도 안 된다).
 *
 * <p><b>오래된 값은 버린다</b>: 런처가 꺼지면 파일은 남아 있는데 내용은 멈춘다. 그래서 파일 안의
 * {@code ts}(런처가 적은 시각)가 {@link #STALE_MS}보다 오래됐으면 <b>없는 것으로 친다</b> -
 * 껐다 켠 뒤에도 옛날 곡이 계속 떠 있는 일을 막는다.
 */
public final class LunaNowPlaying {
	private LunaNowPlaying() {
	}

	/** 이보다 오래된 기록은 버린다(런처가 멈췄거나 꺼진 것). */
	private static final long STALE_MS = 15_000L;
	/** 다시 읽는 간격. */
	private static final long POLL_MS = 2_000L;

	private static volatile String title = "";
	private static volatile String artist = "";
	private static volatile boolean playing;
	// 49-177차: 어느 플랫폼에서 나오는지(런처가 추정: melon, spotify, ytmusic, youtube, soop, 모르면 빈 문자열)
	private static volatile String source = "";

	private static long lastPollMs;
	private static boolean reading;
	private static Thread worker;

	/** 지금 재생 중인 곡 제목(없으면 빈 문자열). */
	public static String title() {
		return title;
	}

	/** 가수/채널 이름(없으면 빈 문자열). */
	public static String artist() {
		return artist;
	}

	/** 노래가 <b>재생 중</b>인지(일시정지는 false). */
	public static boolean playing() {
		return playing;
	}

	/** 플랫폼 이름표(melon, spotify, ytmusic, youtube, soop 중 하나, 모르면 빈 문자열). */
	public static String source() {
		return source;
	}

	/** 보여 줄 게 있는지. */
	public static boolean has() {
		return !title.isEmpty();
	}

	/**
	 * HUD가 매 프레임 부른다. 대부분의 호출은 <b>숫자 비교 한 번</b>에 끝나고, 2초에 한 번만
	 * 배경 스레드를 깨운다.
	 */
	public static void poll() {
		long now = System.currentTimeMillis();
		if (reading || now - lastPollMs < POLL_MS) {
			return;
		}
		lastPollMs = now;
		reading = true;
		Thread t = worker;
		if (t == null || !t.isAlive()) {
			// 스레드를 매번 새로 만들지 않고, 죽어 있을 때만 하나 만든다
			worker = t = new Thread(LunaNowPlaying::readOnce, "luna-now-playing");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		} else {
			reading = false;   // 앞 작업이 아직 도는 중 - 이번 차례는 건너뛴다
		}
	}

	@SuppressWarnings("deprecation") // new JsonParser().parse - 1.16(gson 2.8.0)엔 parseString이 없다
	private static void readOnce() {
		try {
			Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".luna-now-playing.json");
			if (!Files.exists(p)) {
				clear();
				return;
			}
			String raw = Files.readString(p, StandardCharsets.UTF_8);
			JsonElement el = new JsonParser().parse(raw);
			if (el == null || !el.isJsonObject()) {
				clear();
				return;
			}
			JsonObject o = el.getAsJsonObject();
			long ts = o.has("ts") && !o.get("ts").isJsonNull() ? o.get("ts").getAsLong() : 0L;
			if (System.currentTimeMillis() - ts > STALE_MS) {
				clear();          // 런처가 멈춘 뒤 남은 파일 - 옛 곡을 계속 띄우지 않는다
				return;
			}
			title = text(o, "title");
			artist = text(o, "artist");
			source = text(o, "source");
			playing = o.has("playing") && !o.get("playing").isJsonNull() && o.get("playing").getAsBoolean();
		} catch (Throwable t) {
			LunaCompat.warnOnce("nowPlaying:read", t);
			clear();
		} finally {
			reading = false;
		}
	}

	private static String text(JsonObject o, String key) {
		try {
			JsonElement e = o.get(key);
			if (e == null || e.isJsonNull()) {
				return "";
			}
			String s = e.getAsString();
			return s == null ? "" : s.trim();
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static void clear() {
		title = "";
		artist = "";
		source = "";
		playing = false;
	}
}
