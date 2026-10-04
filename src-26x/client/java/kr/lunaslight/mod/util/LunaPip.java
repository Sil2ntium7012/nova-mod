package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 49-177차(사용자: "내가 보고 있는 동영상 플랫폼 마크 전체화면 하더라도 보이는 기능"): <b>보고 있는 영상</b>의 다리.
 *
 * <h3>왜 런처가 찍는가</h3>
 * 다른 프로그램의 창(브라우저의 유튜브, SOOP 등)을 찍는 건 운영체제 기능이고, 자바 표준엔 그 길이 없다.
 * 런처(Electron)는 크롬과 같은 방식(윈도우 그래픽 캡처)으로 <b>가려진 창도</b> 찍을 수 있다 - 마크가 전체 화면이라
 * 브라우저가 뒤에 깔려 있어도 된다(최소화만 안 되면). 그래서 런처가 창을 작게 찍어 PNG로 만들고,
 * 이 컴퓨터 안(127.0.0.1)의 소켓으로 모드에 흘려 준다. 모드는 받아서 텍스처로 올려 HUD에 그린다.
 *
 * <h3>주고받는 파일</h3>
 * <ul>
 *   <li>모드 → 런처: {@code .luna-pip.json} {ts, action:"start"|"stop", fps, maxW, window}(window = 고른 창 이름, 빈 값이면 자동)</li>
 *   <li>런처 → 모드: {@code .luna-pip-state.json} {ts, port, running, window, error, count, list}(list = 고를 수 있는 창 이름들, 2초마다)</li>
 *   <li>프레임: 소켓으로 [길이 4바이트(빅 엔디언)][PNG 또는 "NRAW"+폭+높이+RGBA 픽셀(49-195차)] 반복</li>
 * </ul>
 *
 * <h3>성능</h3>
 * PNG를 푸는 일(NativeImage.read)은 전부 소켓 스레드에서 한다(GL을 안 건드림). 렌더 스레드는 다 풀린 그림을
 * 텍스처로 올리기만 한다. 렌더가 느려 밀리면 옛 그림은 버리고 새 그림만 남긴다.
 */
public final class LunaPip {
	private LunaPip() {
	}

	private static final long POLL_MS = 1000L;
	private static final long STALE_MS = 7000L;

	public static volatile boolean launcherPresent;
	public static volatile boolean running;
	public static volatile String window = "";
	public static volatile String error;
	public static volatile int candidates;
	/** 49-179차: 고를 수 있는 창 이름(영상 사이트 창 먼저). 런처가 2초마다 새로 알려 준다. */
	public static volatile java.util.List<String> windows = java.util.Collections.emptyList();
	public static volatile long lastFrameMs;
	private static volatile int port;

	private static final AtomicReference<NativeImage> PENDING = new AtomicReference<>();

	private static long lastPollMs;
	private static volatile boolean reading;
	private static Thread stateWorker;
	private static volatile Thread frameWorker;
	private static volatile Socket socket;
	private static volatile int socketPort;
	private static volatile boolean wanted;

	/** 런처에게 시작(창 찍기)이나 정지를 부탁한다. */
	public static void request(boolean start, int fps, int maxW, String window) {
		request(start, fps, maxW, window, true, "normal");
	}

	/** 49-187차: crop = 영상이 나오는 부분만 잘라 달라, ratio = normal(16:9) | shorts(9:16) - 잘라 낼 모양. */
	public static void request(boolean start, int fps, int maxW, String window, boolean crop, String ratio) {
		request(start, fps, maxW, window, crop, ratio, "");
	}

	/** 49-189차: area = 직접 고른 영상 자리 "x,y,w,h"(창 전체를 0~1로 본 값, 빈 값이면 자동으로 찾기). */
	public static void request(boolean start, int fps, int maxW, String window, boolean crop, String ratio, String area) {
		wanted = start;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("ts", System.currentTimeMillis());
			o.addProperty("action", start ? "start" : "stop");
			o.addProperty("fps", fps);
			o.addProperty("maxW", maxW);
			o.addProperty("window", window == null ? "" : window);
			o.addProperty("crop", crop);
			o.addProperty("ratio", ratio == null ? "normal" : ratio);
			o.addProperty("area", area == null ? "" : area);
			o.addProperty("raw", rawSupported());   // 49-195차: 픽셀 그대로 받을 수 있으면 런처가 PNG로 싸지 않는다
			Files.writeString(gameDir().resolve(".luna-pip.json"), o.toString(), StandardCharsets.UTF_8);
			lastPollMs = 0;
		} catch (Throwable t) {
			LunaCompat.warnOnce("pip:request", t);
		}
		if (!start) {
			closeSocket();
			NativeImage old = PENDING.getAndSet(null);
			if (old != null) {
				old.close();
			}
		}
	}

	/** 새로 풀린 그림(없으면 null). 받은 쪽이 책임진다(텍스처로 올리거나 close). */
	public static NativeImage takeFrame() {
		return PENDING.getAndSet(null);
	}

	/** 매 프레임 불러도 된다 - 1초에 한 번만 상태 파일을 읽고, 필요하면 소켓을 잇는다. */
	public static void poll() {
		long now = System.currentTimeMillis();
		if (reading || now - lastPollMs < POLL_MS) {
			return;
		}
		lastPollMs = now;
		reading = true;
		Thread t = stateWorker;
		if (t == null || !t.isAlive()) {
			stateWorker = t = new Thread(LunaPip::readState, "luna-pip-state");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		} else {
			reading = false;
		}
	}

	@SuppressWarnings("deprecation")
	private static void readState() {
		try {
			Path p = gameDir().resolve(".luna-pip-state.json");
			if (!Files.exists(p)) {
				launcherPresent = false;
				running = false;
				return;
			}
			JsonElement el = new JsonParser().parse(Files.readString(p, StandardCharsets.UTF_8));
			if (el == null || !el.isJsonObject()) {
				return;
			}
			JsonObject o = el.getAsJsonObject();
			long ts = num(o, "ts");
			launcherPresent = System.currentTimeMillis() - ts < STALE_MS;
			running = launcherPresent && bool(o, "running");
			window = str(o, "window");
			error = o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null;
			candidates = (int) num(o, "count");
			java.util.List<String> list = new java.util.ArrayList<>();
			if (o.has("list") && o.get("list").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("list")) {
					try {
						list.add(e.getAsString());
					} catch (Throwable ignored) {
					}
				}
			}
			windows = java.util.Collections.unmodifiableList(list);
			port = (int) num(o, "port");
			if (wanted && running && port > 0 && (frameWorker == null || !frameWorker.isAlive() || socketPort != port)) {
				startReader(port);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("pip:state", t);
		} finally {
			reading = false;
		}
	}

	private static synchronized void startReader(int p) {
		closeSocket();
		socketPort = p;
		Thread t = new Thread(() -> readFrames(p), "luna-pip-frames");
		t.setDaemon(true);
		frameWorker = t;
		t.start();
	}

	// ==================== 49-195차: 받기와 풀기를 나눈다 ====================
	// 사용자: "영상 보기 다 좋은데 아주 약간 밀리는 느낌". 예전엔 한 스레드가 받고 → 풀고 → 다시 받았다. 푸는 동안 온 장은
	// 소켓에 줄 서서 기다렸다가 차례로 풀려서, 풀기가 조금만 느려도 그만큼 늘 늦은 장을 보게 됐다. 이제 받는 스레드는 받기만
	// 해서 "가장 새 장"만 남기고, 푸는 스레드가 그 가장 새 장만 푼다(중간 장은 버린다). 그리고 런처가 픽셀을 그대로 보내면
	// (NRAW) PNG를 풀 필요 자체가 없다 - 네이티브 그림 메모리에 바로 복사한다.

	private static final AtomicReference<byte[]> LATEST = new AtomicReference<>();
	private static final Object DECODE_LOCK = new Object();
	private static volatile Thread decodeWorker;

	private static void ensureDecoder() {
		Thread t = decodeWorker;
		if (t != null && t.isAlive()) {
			return;
		}
		t = new Thread(LunaPip::decodeLoop, "luna-pip-decode");
		t.setDaemon(true);
		decodeWorker = t;
		t.start();
	}

	private static void decodeLoop() {
		while (true) {
			byte[] buf;
			synchronized (DECODE_LOCK) {
				while ((buf = LATEST.getAndSet(null)) == null) {
					try {
						DECODE_LOCK.wait(1000);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
			NativeImage img = decode(buf);
			if (img == null) {
				continue;   // 한 장이 깨졌을 뿐 - 다음 장
			}
			NativeImage old = PENDING.getAndSet(img);
			if (old != null) {
				old.close();   // 렌더가 못 가져간 옛 그림은 버린다
			}
			lastFrameMs = System.currentTimeMillis();
		}
	}

	private static NativeImage decode(byte[] buf) {
		try {
			if (buf.length > 12 && buf[0] == 'N' && buf[1] == 'R' && buf[2] == 'A' && buf[3] == 'W') {
				int w = ((buf[4] & 0xFF) << 24) | ((buf[5] & 0xFF) << 16) | ((buf[6] & 0xFF) << 8) | (buf[7] & 0xFF);
				int h = ((buf[8] & 0xFF) << 24) | ((buf[9] & 0xFF) << 16) | ((buf[10] & 0xFF) << 8) | (buf[11] & 0xFF);
				return rawImage(buf, 12, w, h);
			}
			return NativeImage.read(new ByteArrayInputStream(buf));
		} catch (Throwable bad) {
			return null;
		}
	}

	private static volatile int rawState;   // 0 = 아직 모름, 1 = 됨, -1 = 안 됨

	/** 이 버전에서 NativeImage 메모리에 픽셀을 바로 쓸 수 있는가(한 번 재 본다). */
	public static boolean rawSupported() {
		if (rawState == 0) {
			NativeImage probe = null;
			try {
				probe = new NativeImage(1, 1, false);
				rawState = pointerOf(probe) != 0L ? 1 : -1;
			} catch (Throwable t) {
				rawState = -1;
			} finally {
				if (probe != null) {
					try {
						probe.close();
					} catch (Throwable ignored) {
					}
				}
			}
		}
		return rawState > 0;
	}

	private static long pointerOf(NativeImage img) {
		Object v = LunaCompat.getFieldValue(img, "pointer", "pixels");
		return v instanceof Long l ? l : 0L;
	}

	/** 49-195차: RGBA 바이트 → NativeImage(영상 보기의 게임 커서 그림에도 쓴다). 이 버전에서 안 되면 null. */
	public static NativeImage imageFromRgba(byte[] buf, int off, int w, int h) {
		if (!rawSupported()) {
			return null;
		}
		try {
			return rawImage(buf, off, w, h);
		} catch (Throwable t) {
			return null;
		}
	}

	/** RGBA 바이트 그대로 → NativeImage(기본 형식 RGBA = 메모리에 R,G,B,A 순서라 캔버스 getImageData와 같다). */
	private static NativeImage rawImage(byte[] buf, int off, int w, int h) {
		if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h * 4 > buf.length - off) {
			return null;
		}
		NativeImage img = new NativeImage(w, h, false);
		long ptr = pointerOf(img);
		if (ptr == 0L) {
			img.close();
			rawState = -1;
			return null;
		}
		org.lwjgl.system.MemoryUtil.memByteBuffer(ptr, w * h * 4).put(buf, off, w * h * 4);
		return img;
	}

	private static void readFrames(int p) {
		Socket s = new Socket();
		socket = s;
		ensureDecoder();
		try {
			s.connect(new InetSocketAddress("127.0.0.1", p), 1500);
			s.setTcpNoDelay(true);
			DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
			while (wanted && !s.isClosed()) {
				int len = in.readInt();
				if (len <= 0 || len > (40 << 20)) {
					break;
				}
				byte[] buf = new byte[len];
				in.readFully(buf);
				LATEST.set(buf);   // 풀지 못한 옛 장은 이걸로 덮여 버려진다
				synchronized (DECODE_LOCK) {
					DECODE_LOCK.notifyAll();
				}

			}
		} catch (Throwable ignored) {
			// 런처가 끊었거나(창 바꿈, 정지) 아직 준비 전 - 다음 상태 읽기에서 다시 잇는다
		} finally {
			try {
				s.close();
			} catch (Throwable ignored) {
			}
			if (socket == s) {
				socket = null;
				socketPort = 0;
			}
		}
	}

	private static void closeSocket() {
		Socket s = socket;
		socket = null;
		socketPort = 0;
		if (s != null) {
			try {
				s.close();
			} catch (Throwable ignored) {
			}
		}
	}

	private static long num(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0L;
		} catch (Throwable ignored) {
			return 0L;
		}
	}

	private static boolean bool(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static String str(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static Path gameDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
	}
}
