package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

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

	/** 녹화 중이면 지난 초. */
	public static double elapsed() {
		return recording ? (System.currentTimeMillis() - since) / 1000.0 : 0;
	}

	/** 키를 눌렀을 때. 런처에게 시작/정지를 부탁한다. 부탁을 못 남겼으면 이유 문자열, 됐으면 null. */
	public static String toggle(Minecraft client) {
		if (!available()) {
			return "Nova Client 런처로 실행해야 녹화할 수 있습니다";
		}
		if (!recording && !ready) {
			return "런처가 ffmpeg를 아직 받는 중입니다 - 잠시 뒤 다시";
		}
		try {
			JsonObject o = new JsonObject();
			o.addProperty("ts", System.currentTimeMillis());
			o.addProperty("action", recording ? "stop" : "start");
			o.addProperty("fps", 60);
			o.addProperty("height", height);
			Path p = gameDir().resolve(".luna-record.json");
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
			lastPollMs = 0;   // 곧바로 상태를 다시 읽는다
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
			error = o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null;
			since = o.has("since") && !o.get("since").isJsonNull() ? o.get("since").getAsLong() : since;
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
