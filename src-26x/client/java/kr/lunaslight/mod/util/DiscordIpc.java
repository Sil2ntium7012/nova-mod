package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-125차(사용자: "디스코드에 플레이중 표시"): 디스코드 앱과 직접 통신하는 아주 작은 Rich Presence 클라이언트.
 *
 * <p>디스코드 데스크톱 앱은 로컬 IPC(윈도우: 이름 있는 파이프 {@code \\.\pipe\discord-ipc-N}, 맥/리눅스: 유닉스 소켓
 * {@code $XDG_RUNTIME_DIR/discord-ipc-N})로 요청을 받는다. 프레임 = [opcode int32 LE][길이 int32 LE][JSON UTF-8].
 * op 0 = 핸드셰이크({v:1, client_id}), op 1 = 명령(SET_ACTIVITY), op 2 = 닫기. 외부 라이브러리 없이 이것만 쓴다.
 *
 * <p>모든 입출력은 전용 스레드 하나에서만 한다(게임 스레드를 막지 않게). 디스코드가 꺼져 있으면 15초마다 다시 붙어 본다.
 * 실패해도 게임에는 아무 영향이 없다 - 조용히 로그 한 번.
 */
public final class DiscordIpc {
	private DiscordIpc() {
	}

	/** 런처와 같은 디스코드 애플리케이션(Luna's Light) - 포털의 Art Assets(luna_logo 등)를 같이 쓴다. */
	public static final String CLIENT_ID = "1543637746122235904";

	private static volatile JsonObject wanted;       // 보여 주고 싶은 활동(null = 지우기)
	private static volatile boolean dirty = true;
	private static volatile boolean running;
	private static volatile boolean connected;
	private static Thread worker;

	/** 지금 디스코드에 붙어 있는지. */
	public static boolean isConnected() {
		return connected;
	}

	/** 표시할 활동을 바꾼다(같은 내용이면 무시). null이면 지운다. 게임 스레드에서 불러도 된다. */
	public static synchronized void setActivity(JsonObject activity) {
		String now = activity == null ? null : activity.toString();
		String prev = wanted == null ? null : wanted.toString();
		if (now == null ? prev == null : now.equals(prev)) {
			return;
		}
		wanted = activity;
		dirty = true;
		start();
	}

	/** 스레드를 멈추고 활동을 지운다. */
	public static synchronized void stop() {
		wanted = null;
		dirty = true;
		running = false;
		if (worker != null) {
			worker.interrupt();
			worker = null;
		}
	}

	private static synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		worker = new Thread(DiscordIpc::loop, "Luna-DiscordIPC");
		worker.setDaemon(true);
		worker.start();
	}

	// ==================== 연결 루프 ====================

	private interface Pipe {
		void write(byte[] data) throws IOException;

		/** 딱 n바이트 읽는다(막힘). */
		void readFully(byte[] buf) throws IOException;

		void close();
	}

	private static void loop() {
		long nextTry = 0;
		Pipe pipe = null;
		while (running) {
			try {
				if (pipe == null) {
					if (System.currentTimeMillis() < nextTry) {
						Thread.sleep(500);
						continue;
					}
					pipe = open();
					if (pipe == null) {
						nextTry = System.currentTimeMillis() + 15_000L;
						continue;
					}
					JsonObject hs = new JsonObject();
					hs.addProperty("v", 1);
					hs.addProperty("client_id", CLIENT_ID);
					pipe.write(frame(0, hs.toString()));
					if (readFrame(pipe) != 1) {
						throw new IOException("handshake rejected");   // op 2 = 닫기(잘못된 앱 id 등)
					}
					connected = true;
					dirty = true;
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 디스코드 상태 표시 연결됨");
				}
				if (dirty) {
					dirty = false;
					JsonObject args = new JsonObject();
					args.addProperty("pid", ProcessHandle.current().pid());
					JsonObject act = wanted;
					if (act != null) {
						args.add("activity", act);
					}
					JsonObject cmd = new JsonObject();
					cmd.addProperty("cmd", "SET_ACTIVITY");
					cmd.add("args", args);
					cmd.addProperty("nonce", java.util.UUID.randomUUID().toString());
					pipe.write(frame(1, cmd.toString()));
					if (readFrame(pipe) == 2) {
						throw new IOException("closed by discord");
					}
				}
				Thread.sleep(250);
			} catch (InterruptedException ie) {
				break;
			} catch (Throwable t) {
				// 디스코드가 꺼졌거나 파이프가 끊김 - 잠시 뒤 다시
				if (connected) {
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 디스코드 연결 끊김 - 15초 뒤 다시 시도");
				}
				connected = false;
				if (pipe != null) {
					pipe.close();
				}
				pipe = null;
				nextTry = System.currentTimeMillis() + 15_000L;
			}
		}
		// 멈출 때: 활동 지우고 닫기
		if (pipe != null) {
			try {
				JsonObject args = new JsonObject();
				args.addProperty("pid", ProcessHandle.current().pid());
				JsonObject cmd = new JsonObject();
				cmd.addProperty("cmd", "SET_ACTIVITY");
				cmd.add("args", args);
				cmd.addProperty("nonce", java.util.UUID.randomUUID().toString());
				pipe.write(frame(1, cmd.toString()));
				pipe.write(frame(2, "{}"));
			} catch (Throwable ignored) {
			}
			pipe.close();
		}
		connected = false;
	}

	/** 응답 프레임 하나를 읽어 버리고 opcode만 돌려준다(요청마다 응답이 꼭 하나 온다 - 파이프가 차지 않게). */
	private static int readFrame(Pipe pipe) throws IOException {
		byte[] head = new byte[8];
		pipe.readFully(head);
		ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
		int op = h.getInt();
		int len = h.getInt();
		if (len < 0 || len > (1 << 20)) {
			throw new IOException("bad frame");
		}
		pipe.readFully(new byte[len]);
		return op;
	}

	private static byte[] frame(int op, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(op);
		b.putInt(body.length);
		b.put(body);
		return b.array();
	}

	/** discord-ipc-0 ~ 9 중 열리는 첫 번째. 없으면 null(디스코드 꺼짐). */
	private static Pipe open() {
		boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
		for (int i = 0; i < 10; i++) {
			try {
				if (windows) {
					RandomAccessFile raf = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
					return new Pipe() {
						@Override
						public void write(byte[] data) throws IOException {
							raf.write(data);
						}

						@Override
						public void readFully(byte[] buf) throws IOException {
							raf.readFully(buf);
						}

						@Override
						public void close() {
							try {
								raf.close();
							} catch (IOException ignored) {
							}
						}
					};
				}
				Path sock = unixSocketPath(i);
				if (sock == null) {
					continue;
				}
				SocketChannel ch = SocketChannel.open(java.net.UnixDomainSocketAddress.of(sock));
				return new Pipe() {
					@Override
					public void write(byte[] data) throws IOException {
						ByteBuffer b = ByteBuffer.wrap(data);
						while (b.hasRemaining()) {
							ch.write(b);
						}
					}

					@Override
					public void readFully(byte[] buf) throws IOException {
						ByteBuffer b = ByteBuffer.wrap(buf);
						while (b.hasRemaining()) {
							if (ch.read(b) < 0) {
								throw new IOException("closed");
							}
						}
					}

					@Override
					public void close() {
						try {
							ch.close();
						} catch (IOException ignored) {
						}
					}
				};
			} catch (Throwable ignored) {
				// 이 번호는 없음 - 다음
			}
		}
		return null;
	}

	private static Path unixSocketPath(int i) {
		String[] envs = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
		for (String e : envs) {
			String dir = System.getenv(e);
			if (dir != null && !dir.isEmpty()) {
				Path p = Path.of(dir, "discord-ipc-" + i);
				if (Files.exists(p)) {
					return p;
				}
			}
		}
		Path p = Path.of("/tmp", "discord-ipc-" + i);
		return Files.exists(p) ? p : null;
	}

	// ==================== 활동 JSON 만들기 ====================

	/** details/state/시작 시각/큰·작은 그림/버튼으로 활동 JSON을 만든다. 빈 값은 넣지 않는다. */
	public static JsonObject activity(String details, String state, long startEpochSec,
			String largeKey, String largeText, String smallKey, String smallText, String buttonLabel, String buttonUrl) {
		JsonObject a = new JsonObject();
		if (details != null && !details.isEmpty()) {
			a.addProperty("details", clip(details));
		}
		if (state != null && !state.isEmpty()) {
			a.addProperty("state", clip(state));
		}
		if (startEpochSec > 0) {
			JsonObject ts = new JsonObject();
			ts.addProperty("start", startEpochSec);
			a.add("timestamps", ts);
		}
		JsonObject assets = new JsonObject();
		if (largeKey != null) {
			assets.addProperty("large_image", largeKey);
			if (largeText != null) {
				assets.addProperty("large_text", clip(largeText));
			}
		}
		if (smallKey != null) {
			assets.addProperty("small_image", smallKey);
			if (smallText != null) {
				assets.addProperty("small_text", clip(smallText));
			}
		}
		if (assets.size() > 0) {
			a.add("assets", assets);
		}
		if (buttonLabel != null && buttonUrl != null) {
			JsonArray buttons = new JsonArray();
			JsonObject b = new JsonObject();
			b.addProperty("label", clip(buttonLabel));
			b.addProperty("url", buttonUrl);
			buttons.add(b);
			a.add("buttons", buttons);
		}
		a.addProperty("instance", false);
		return a;
	}

	/** 디스코드 글자 제한(2~128자). 한 글자짜리는 공백을 붙여 늘린다. */
	private static String clip(String s) {
		if (s.length() > 128) {
			return s.substring(0, 127) + "…";
		}
		return s.length() < 2 ? s + " " : s;
	}
}
