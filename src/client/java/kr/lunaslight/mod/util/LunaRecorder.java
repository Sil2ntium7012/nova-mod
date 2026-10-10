package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-76차(6-5): <b>녹화</b> - 런처에게 "찍어 달라 / 그만"을 부탁하고, 런처가 알려 주는 상태를 읽는다.
 *
 * <h3>49-72차(GIF)를 버린 이유</h3>
 * 사용자 요청은 <b>60fps · 720p/1080p · 길이 제한 없음 · 12시간마다 새 파일</b>이다. GIF는 프레임마다
 * 256색으로 줄이고 통째로 저장하므로 1080p60이면 <b>1분에 수 GB</b>다. 될 수가 없다. 사용자가 고른 길은
 * <b>런처가 ffmpeg를 받아 두는 것</b>.
 *
 * <h3>왜 모드가 프레임을 넘기지 않고 런처가 창을 찍나</h3>
 * 모드 안에서 화면을 GPU에서 읽어 오면(glReadPixels) 그 순간 렌더가 멈춘다 - 1080p60이면 프레임이
 * 눈에 띄게 떨어진다. 그건 "프레임 드랍 없음"과 정면으로 부딪친다. ffmpeg의 gdigrab은 윈도우가 이미
 * 합성해 둔 창 그림을 <b>런처가 띄운 별도 프로세스</b>에서 가져간다. 게임 프로세스에는 아무 비용이 없다.
 * 대가는 ffmpeg 프로세스의 CPU(코어 하나쯤)인데, 그건 게임 스레드가 아니다.
 *
 * <h3>흐름</h3>
 * <ol>
 *   <li>키를 누르면 게임 폴더에 {@code .luna-record.json} {action:"start", height, fps:60}을 쓴다.</li>
 *   <li>런처가 0.5초마다 그 파일을 보고 ffmpeg를 띄운다. 12시간마다 파일을 끊는 것도 ffmpeg가 한다.</li>
 *   <li>런처가 {@code .luna-record-state.json} {recording, since, dir, error, ready}를 쓴다.
 *       모드는 그걸 0.5초마다 읽어 <b>빨간 점</b>을 그린다. 상태는 런처가 말해 주는 것만 믿는다 -
 *       "시작했다"고 내가 정하지 않는다(ffmpeg가 안 떴는데 점이 깜빡이면 그게 가짜다).</li>
 * </ol>
 *
 * <p>런처 없이 켠 게임(개발 환경 등)에서는 상태 파일이 없다 → {@link #available()}가 false고 HUD가
 * "전용 런처로 실행해야 녹화할 수 있습니다"를 보여 준다.
 *
 * <p>영상은 {@code <게임 폴더>/clips/clip_<시각>.mp4}(49-212차, 런처가 정함). 스크린샷 보관함(49-67차)이 그 폴더도 훑는다.
 * <b>소리는 안 담긴다</b> - 윈도우 시스템 소리를 잡으려면 장치 이름이 필요한데 PC마다 다르고, 대부분
 * 꺼져 있다(스테레오 믹스). 지어내지 않고 안 담는다고 적어 둔다.
 */
public final class LunaRecorder {
	private LunaRecorder() {
	}

	private static final long POLL_MS = 500L;

	/** 720 또는 1080. 모듈이 설정에서 넣어 준다. */
	public static volatile int height = 1080;

	// 런처가 알려 주는 상태
	public static volatile boolean recording;
	public static volatile boolean ready;
	public static volatile String error;
	private static volatile long errorAt;

	/** 49-320차(사용자: "녹화가 끊겼습니다 메시지가 너무 오래가"): 오류는 온 뒤 5초만 보여 준다(런처 상태 파일엔 다음 녹화 전까지 남아 있다). */
	public static String recentError() {
		String l = localError;
		if (l != null && System.currentTimeMillis() - localErrorAt < 5000L) {
			return l;
		}
		String e = error;
		return e != null && System.currentTimeMillis() - errorAt < 5000L ? e : null;
	}
	private static volatile long since;
	private static volatile boolean stateFileExists;

	private static long lastPollMs;
	private static boolean reading;
	private static Thread worker;

	/**
	 * 런처 브리지가 살아 있는가. 상태 파일은 런처가 게임을 켤 때 만들고 끌 때 지운다 - 있으면 런처가 보고 있는 것이다.
	 * (시각으로 거르지 않는다 - 런처는 상태가 바뀔 때만 쓰므로 조용한 동안 오래된 파일이 정상이다.)
	 */
	public static boolean available() {
		return stateFileExists;
	}

	// ---- 49-322차(사용자: "녹화 버튼이랑 시간이 안 맞고, 눌렀을 때 켜짐/꺼짐이 바로바로 안 떠, 끝냈을 때 저장했다는 메시지"):
	// 런처가 ffmpeg를 띄우고 상태 파일을 쓰기까지 1~2초 걸려(창 찾기 + 0.5초 확인) 그동안 HUD가 아무 반응이 없었고, 시간도 그만큼 늦게
	// 시작했다. 이제 키를 누른 순간 우리 쪽에서 먼저 "녹화 중"(시간은 누른 때부터) / "저장하는 중"으로 바꾸고, 런처 답을 기다린다.
	private static final int NONE = 0, STARTING = 1, STOPPING = 2;
	private static volatile int pending = NONE;
	private static volatile long pendingAt;
	/** 키를 누른 때(시간은 여기서부터 센다). 0이면 런처의 시작 시각. */
	private static volatile long pressedAt;
	/** 런처가 알려 준 방금 저장한 파일 이름과 그때. */
	public static volatile String saved;
	private static volatile long savedShownAt;
	private static volatile String lastSaved;
	/** 시작 요청이 이 시간 안에 답이 없으면 실패로 본다. */
	private static final long START_TIMEOUT_MS = 8000L;
	private static volatile String localError;
	private static volatile long localErrorAt;

	/** 화면에 "녹화 중"으로 보일지(누르자마자 켜짐, 끄기를 누르자마자 꺼짐). */
	public static boolean showRecording() {
		checkTimeout();
		if (pending == STARTING) {
			return true;
		}
		if (pending == STOPPING) {
			return false;
		}
		return recording;
	}

	/** 끄기를 누르고 런처가 파일을 닫는 중인가. */
	public static boolean saving() {
		return pending == STOPPING && System.currentTimeMillis() - pendingAt < 15000L;
	}

	/** 방금 저장한 파일 이름(5초 동안만). 없으면 null. */
	public static String recentSaved() {
		String f = saved;
		return f != null && System.currentTimeMillis() - savedShownAt < 5000L ? f : null;
	}

	private static void checkTimeout() {
		if (pending == STARTING && !recording && System.currentTimeMillis() - pendingAt > START_TIMEOUT_MS) {
			pending = NONE;
			pressedAt = 0;
			localError = "녹화를 시작하지 못했습니다(런처 로그 참고)";
			localErrorAt = System.currentTimeMillis();
		}
	}

	/** 녹화 중이면 지난 초(키를 누른 때부터). */
	public static double elapsed() {
		if (!showRecording()) {
			return 0;
		}
		long from = pressedAt > 0 ? pressedAt : since;
		return Math.max(0, (System.currentTimeMillis() - from) / 1000.0);
	}

	/** 키를 눌렀을 때. 런처에게 시작/정지를 부탁한다. 부탁을 못 남겼으면 이유 문자열, 됐으면 null. */
	public static String toggle(MinecraftClient client) {
		if (!available()) {
			return "Nova Client 런처로 실행해야 녹화할 수 있습니다";
		}
		boolean on = showRecording();
		if (!on && !ready) {
			return "런처가 ffmpeg를 아직 받는 중입니다 - 잠시 뒤 다시";
		}
		if (pending == STOPPING && saving()) {
			return "저장하는 중입니다 - 잠시만요";
		}
		try {
			JsonObject o = new JsonObject();
			o.addProperty("ts", System.currentTimeMillis());
			o.addProperty("action", on ? "stop" : "start");
			o.addProperty("fps", 60);
			o.addProperty("height", height);
			Path p = gameDir().resolve(".luna-record.json");
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
			lastPollMs = 0;   // 곧바로 상태를 다시 읽는다
			long now = System.currentTimeMillis();
			pending = on ? STOPPING : STARTING;
			pendingAt = now;
			if (!on) {
				pressedAt = now;
				localError = null;
				saved = null;
			}
			return null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("record:request", t);
			return "부탁을 남기지 못했습니다";
		}
	}

	/** HUD가 매 프레임 부른다. 0.5초에 한 번 배경 스레드에서 상태 파일을 읽는다. */
	public static void poll() {
		long now = System.currentTimeMillis();
		if (reading || now - lastPollMs < POLL_MS) {
			return;
		}
		lastPollMs = now;
		reading = true;
		Thread t = worker;
		if (t == null || !t.isAlive()) {
			worker = t = new Thread(LunaRecorder::readOnce, "luna-recorder");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		} else {
			reading = false;
		}
	}

	@SuppressWarnings("deprecation")
	private static void readOnce() {
		try {
			Path p = gameDir().resolve(".luna-record-state.json");
			if (!Files.exists(p)) {
				stateFileExists = false;
				recording = false;
				return;
			}
			JsonElement el = new JsonParser().parse(Files.readString(p, StandardCharsets.UTF_8));
			if (el == null || !el.isJsonObject()) {
				return;
			}
			JsonObject o = el.getAsJsonObject();
			stateFileExists = true;
			recording = bool(o, "recording");
			ready = bool(o, "ready") || recording;
			String err = o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null;
			if (err != null && !err.equals(error)) {
				errorAt = System.currentTimeMillis();   // 49-320차: 새 오류가 온 때
			}
			error = err;
			since = o.has("since") && !o.get("since").isJsonNull() ? o.get("since").getAsLong() : since;
			// 49-322차: 런처 답과 맞춰 본다 - 키를 누른 뒤에 런처가 쓴 상태(ts)만 답으로 친다(전에 남은 오류로 바로 꺼지지 않게)
			long ts = o.has("ts") && !o.get("ts").isJsonNull() ? o.get("ts").getAsLong() : 0L;
			boolean fresh = ts >= pendingAt - 200L;
			String sv = o.has("saved") && !o.get("saved").isJsonNull() ? o.get("saved").getAsString() : null;
			if (pending == STARTING && fresh && (recording || err != null)) {
				pending = NONE;
				if (!recording) {
					pressedAt = 0;
					localError = err;   // 시작하자마자 실패 - 오류를 5초 보여 준다
					localErrorAt = System.currentTimeMillis();
				}
			} else if (pending == STOPPING && fresh && !recording) {
				pending = NONE;
				pressedAt = 0;
				if (sv == null && err == null) {
					saved = "";   // 파일 이름을 안 알려 주는 옛 런처 - "저장됨"만
					savedShownAt = System.currentTimeMillis();
				}
			}
			if (sv != null && !sv.equals(lastSaved)) {
				lastSaved = sv;
				saved = sv;
				savedShownAt = System.currentTimeMillis();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("record:state", t);
		} finally {
			reading = false;
		}
	}

	private static boolean bool(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static Path gameDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
	}
}
