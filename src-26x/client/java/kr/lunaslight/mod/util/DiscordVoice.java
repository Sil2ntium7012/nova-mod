package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-732차(사용자가 전해 준 부탁: "너굴라이언트 쓸 때 왼쪽에 디스코드 뜨게 할 수 있나요, 말할 때 초록색 원 뜨는 거"): <b>디스코드 음성 표시</b>.
 *
 * <p>디스코드 앱의 로컬 RPC(이름 있는 파이프 discord-ipc-N, {@link DiscordIpc}와 같은 틀)에 따로 붙어서, 내가 들어가 있는 음성 채널의
 * 사람(이름, 프로필 사진, 마이크/헤드셋 꺼짐)과 지금 말하는 사람을 받는다. 음성 정보는 RPC 권한(rpc)이 있어야 읽을 수 있는데,
 * 일반 앱은 디스코드 승인 없이 이 권한을 받을 수 없다 - 그래서 디스코드가 방송용으로 내놓은 StreamKit 오버레이 앱의 id로 승인을 받고
 * (디스코드 창에 "StreamKit Overlay" 허용 창이 한 번 뜬다), 그 앱의 공개 토큰 교환 주소로 토큰을 받는다(Discover Overlay 등 공개 오버레이와 같은 방법).
 * 토큰은 config/lunaslight/discord-voice.json(내 컴퓨터에만). 소리는 받지 않는다 - 누가 말하는지(켜짐/꺼짐)만.
 *
 * <p>입출력은 전용 스레드 하나에서만 한다(윈도우 파이프는 읽기와 쓰기를 동시에 못 한다 - 이벤트를 받고, 필요할 때만 같은 스레드에서 보낸다).
 */
public final class DiscordVoice {
	private DiscordVoice() {
	}

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("lunaslight");
	private static final String CLIENT_ID = "207646673902501888";   // 디스코드 StreamKit 오버레이
	private static final String TOKEN_URL = "https://streamkit.discord.com/overlay/token";

	/** 음성 채널 한 사람. */
	public static final class Member {
		public final String id;
		public volatile String name;
		public volatile String avatar;
		public volatile boolean speaking, muted, deafened;
		public volatile long speakAt;

		Member(String id) {
			this.id = id;
		}
	}

	public static final Map<String, Member> MEMBERS = new ConcurrentHashMap<>();
	/** 들어온 순서(화면 줄 순서). */
	private static final List<String> ORDER = java.util.Collections.synchronizedList(new ArrayList<>());
	public static volatile String channelName;
	public static volatile String status = "꺼짐";
	public static volatile String selfId;

	/** 49-733차(디스코드 심화): 디스코드 알림(DM, 멘션 - 디스코드 앱이 띄우는 알림과 같은 것) {제목, 내용, 사진 주소, 받은 시각}. */
	public record Note(String title, String body, String icon, long at) {
	}

	public static final java.util.concurrent.ConcurrentLinkedQueue<Note> NOTES = new java.util.concurrent.ConcurrentLinkedQueue<>();
	/** 알림을 받을지(모듈 설정). */
	public static volatile boolean wantNotes = true;

	private static volatile boolean running;
	private static volatile boolean wantAuth;
	private static Thread worker;
	private static volatile Pipe pipe;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	/** 화면 순서대로 사람들. */
	public static List<Member> members() {
		List<Member> out = new ArrayList<>();
		synchronized (ORDER) {
			for (String id : ORDER) {
				Member m = MEMBERS.get(id);
				if (m != null) {
					out.add(m);
				}
			}
		}
		return out;
	}

	public static boolean hasToken() {
		return token() != null;
	}

	/** 켠다. 저장된 토큰이 없으면 auth가 true일 때만 디스코드에 허용 창을 띄운다. */
	public static synchronized void start(boolean auth) {
		if (auth) {
			wantAuth = true;
		}
		if (running) {
			if (auth) {
				restart();
			}
			return;
		}
		running = true;
		worker = new Thread(DiscordVoice::loop, "Luna-DiscordVoice");
		worker.setDaemon(true);
		worker.start();
	}

	public static synchronized void stop() {
		running = false;
		Pipe p = pipe;
		if (p != null) {
			p.close();
		}
		if (worker != null) {
			worker.interrupt();
			worker = null;
		}
		clear();
		status = "꺼짐";
	}

	private static void restart() {
		Pipe p = pipe;
		if (p != null) {
			p.close();   // 읽기가 끊기면 루프가 다시 붙는다
		}
	}

	private static void clear() {
		MEMBERS.clear();
		ORDER.clear();
		channelName = null;
	}

	// ==================== 토큰 저장 ====================

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("discord-voice.json");
	}

	private static String token() {
		try {
			Path f = file();
			if (!Files.exists(f)) {
				return null;
			}
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			return o.has("token") ? o.get("token").getAsString() : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static void saveToken(String tok) {
		try {
			Files.createDirectories(file().getParent());
			JsonObject o = new JsonObject();
			if (tok != null) {
				o.addProperty("token", tok);
			}
			Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("discordVoice:save", t);
		}
	}

	/** 연결 끊기(토큰 지움). */
	public static void forget() {
		saveToken(null);
		wantAuth = false;
		restart();
	}

	// ==================== 루프 ====================

	private static void loop() {
		long nextTry = 0;
		while (running) {
			try {
				if (System.currentTimeMillis() < nextTry) {
					Thread.sleep(500);
					continue;
				}
				String tok = token();
				if (tok == null && !wantAuth) {
					status = "디스코드 연결 필요 (설정에서 [연결])";
					nextTry = System.currentTimeMillis() + 2_000L;
					continue;
				}
				Pipe p = open();
				if (p == null) {
					status = "디스코드 앱이 꺼져 있습니다";
					nextTry = System.currentTimeMillis() + 15_000L;
					continue;
				}
				pipe = p;
				session(p, tok);
			} catch (InterruptedException ie) {
				break;
			} catch (Throwable t) {
				if (running) {
					LOG.info("[Nova] 디스코드 음성 표시 끊김: " + t.getMessage());
				}
			} finally {
				Pipe p = pipe;
				pipe = null;
				if (p != null) {
					p.close();
				}
				clear();
			}
			nextTry = System.currentTimeMillis() + 5_000L;
		}
	}

	private static void session(Pipe p, String tok) throws Exception {
		JsonObject hs = new JsonObject();
		hs.addProperty("v", 1);
		hs.addProperty("client_id", CLIENT_ID);
		p.write(frame(0, hs.toString()));
		status = "디스코드에 붙는 중";
		String subChannel = null;
		while (running) {
			JsonObject msg = read(p);
			if (msg == null) {
				throw new IOException("closed by discord");
			}
			String cmd = str(msg, "cmd"), evt = str(msg, "evt");
			JsonObject data = msg.has("data") && msg.get("data").isJsonObject() ? msg.getAsJsonObject("data") : null;
			if ("DISPATCH".equals(cmd) && "READY".equals(evt)) {
				if (tok != null) {
					JsonObject a = new JsonObject();
					a.addProperty("access_token", tok);
					send(p, "AUTHENTICATE", a, null);
				} else {
					authorize(p);
				}
				continue;
			}
			if ("ERROR".equals(evt)) {
				String why = data != null ? str(data, "message") : "";
				if ("AUTHENTICATE".equals(cmd)) {
					saveToken(null);   // 토큰이 만료됨 - 전에 허용했던 사람이니 다시 받는다(prompt none이라 보통 창 없이 지나감)
					tok = null;
					authorize(p);
					continue;
				}
				if ("AUTHORIZE".equals(cmd)) {
					wantAuth = false;
					status = "디스코드에서 허용하지 않았습니다";
					throw new IOException("authorize denied: " + why);
				}
				LOG.info("[Nova] 디스코드 음성 표시: " + cmd + " 실패 - " + why);
				continue;
			}
			if ("AUTHORIZE".equals(cmd) && data != null && data.has("code")) {
				String access = exchange(str(data, "code"));
				if (access == null) {
					status = "디스코드 토큰을 받지 못했습니다";
					throw new IOException("token exchange failed");
				}
				saveToken(access);
				tok = access;
				JsonObject a = new JsonObject();
				a.addProperty("access_token", access);
				send(p, "AUTHENTICATE", a, null);
				continue;
			}
			if ("AUTHENTICATE".equals(cmd) && data != null) {
				wantAuth = false;
				if (data.has("user") && data.get("user").isJsonObject()) {
					selfId = str(data.getAsJsonObject("user"), "id");
				}
				status = "연결됨";
				LOG.info("[Nova] 디스코드 음성 표시 연결됨");
				send(p, "SUBSCRIBE", new JsonObject(), "VOICE_CHANNEL_SELECT");
				send(p, "SUBSCRIBE", new JsonObject(), "NOTIFICATION_CREATE");   // 49-733차: DM, 멘션 알림
				send(p, "GET_SELECTED_VOICE_CHANNEL", new JsonObject(), null);
				continue;
			}
			if ("GET_SELECTED_VOICE_CHANNEL".equals(cmd)) {
				String id = data == null ? null : str(data, "id");
				if (subChannel != null && !subChannel.equals(id)) {
					channelSubs(p, "UNSUBSCRIBE", subChannel);
					subChannel = null;
				}
				clear();
				if (data != null && id != null) {
					channelName = str(data, "name");
					if (data.has("voice_states") && data.get("voice_states").isJsonArray()) {
						for (JsonElement e : data.getAsJsonArray("voice_states")) {
							if (e.isJsonObject()) {
								upsert(e.getAsJsonObject());
							}
						}
					}
					if (!id.equals(subChannel)) {
						channelSubs(p, "SUBSCRIBE", id);
						subChannel = id;
					}
					status = "연결됨";
				} else {
					status = "음성 채널에 없음";
				}
				continue;
			}
			if (!"DISPATCH".equals(cmd) || evt == null || data == null) {
				continue;
			}
			switch (evt) {
				case "VOICE_CHANNEL_SELECT" -> send(p, "GET_SELECTED_VOICE_CHANNEL", new JsonObject(), null);
				case "VOICE_STATE_CREATE", "VOICE_STATE_UPDATE" -> upsert(data);
				case "VOICE_STATE_DELETE" -> {
					String uid = data.has("user") && data.get("user").isJsonObject() ? str(data.getAsJsonObject("user"), "id") : null;
					if (uid != null) {
						MEMBERS.remove(uid);
						ORDER.remove(uid);
						if (uid.equals(selfId)) {
							clear();
						}
					}
				}
				case "NOTIFICATION_CREATE" -> {
					if (wantNotes) {
						String title = str(data, "title"), body = str(data, "body"), icon = str(data, "icon_url");
						if ((title != null && !title.isEmpty()) || (body != null && !body.isEmpty())) {
							NOTES.add(new Note(title == null ? "" : title, body == null ? "" : body, icon, System.currentTimeMillis()));
							while (NOTES.size() > 8) {
								NOTES.poll();
							}
						}
					}
				}
				case "SPEAKING_START", "SPEAKING_STOP" -> {
					Member m = MEMBERS.get(str(data, "user_id"));
					if (m != null) {
						m.speaking = "SPEAKING_START".equals(evt);
						m.speakAt = System.currentTimeMillis();
					}
				}
				default -> {
				}
			}
		}
	}

	private static void authorize(Pipe p) throws IOException {
		status = "디스코드 창에서 허용을 눌러 주십시오";
		JsonObject a = new JsonObject();
		a.addProperty("client_id", CLIENT_ID);
		JsonArray scopes = new JsonArray();
		scopes.add("rpc");
		scopes.add("messages.read");
		scopes.add("rpc.notifications.read");
		a.add("scopes", scopes);
		a.addProperty("prompt", "none");
		send(p, "AUTHORIZE", a, null);
	}

	private static void channelSubs(Pipe p, String cmd, String channelId) throws IOException {
		for (String e : new String[]{"VOICE_STATE_CREATE", "VOICE_STATE_UPDATE", "VOICE_STATE_DELETE", "SPEAKING_START", "SPEAKING_STOP"}) {
			JsonObject a = new JsonObject();
			a.addProperty("channel_id", channelId);
			send(p, cmd, a, e);
		}
	}

	/** voice_states 한 줄 → 사람. */
	private static void upsert(JsonObject vs) {
		if (!vs.has("user") || !vs.get("user").isJsonObject()) {
			return;
		}
		JsonObject u = vs.getAsJsonObject("user");
		String id = str(u, "id");
		if (id == null) {
			return;
		}
		Member m = MEMBERS.computeIfAbsent(id, Member::new);
		synchronized (ORDER) {
			if (!ORDER.contains(id)) {
				ORDER.add(id);
			}
		}
		String nick = str(vs, "nick");
		String gn = str(u, "global_name");
		m.name = nick != null && !nick.isEmpty() ? nick : gn != null && !gn.isEmpty() ? gn : str(u, "username");
		m.avatar = str(u, "avatar");
		if (vs.has("voice_state") && vs.get("voice_state").isJsonObject()) {
			JsonObject s = vs.getAsJsonObject("voice_state");
			m.muted = bool(s, "mute") || bool(s, "self_mute") || bool(s, "suppress");
			m.deafened = bool(s, "deaf") || bool(s, "self_deaf");
		}
	}

	/** StreamKit 공개 토큰 교환. */
	private static String exchange(String code) {
		try {
			JsonObject b = new JsonObject();
			b.addProperty("code", code);
			HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL)).timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(b.toString())).build();
			HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() / 100 != 2) {
				LOG.info("[Nova] 디스코드 음성 표시: 토큰 교환 " + r.statusCode());
				return null;
			}
			JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
			return o.has("access_token") ? o.get("access_token").getAsString() : null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("discordVoice:token", t);
			return null;
		}
	}

	// ==================== 프레임 ====================

	private static void send(Pipe p, String cmd, JsonObject args, String evt) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("cmd", cmd);
		o.add("args", args);
		if (evt != null) {
			o.addProperty("evt", evt);
		}
		o.addProperty("nonce", java.util.UUID.randomUUID().toString());
		p.write(frame(1, o.toString()));
	}

	private static JsonObject read(Pipe p) throws IOException {
		byte[] head = new byte[8];
		p.readFully(head);
		ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
		int op = h.getInt();
		int len = h.getInt();
		if (len < 0 || len > (4 << 20)) {
			throw new IOException("bad frame");
		}
		byte[] body = new byte[len];
		p.readFully(body);
		if (op == 2) {
			return null;   // 닫기
		}
		if (op == 3) {   // ping → pong
			p.write(frame(4, new String(body, StandardCharsets.UTF_8)));
			return new JsonObject();
		}
		try {
			JsonElement e = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		} catch (Throwable t) {
			return new JsonObject();
		}
	}

	private static byte[] frame(int op, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(op);
		b.putInt(body.length);
		b.put(body);
		return b.array();
	}

	private static String str(JsonObject o, String k) {
		return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}

	private static boolean bool(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
	}

	private interface Pipe {
		void write(byte[] data) throws IOException;

		void readFully(byte[] buf) throws IOException;

		void close();
	}

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
				Path sock = null;
				for (String e : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
					String dir = System.getenv(e);
					if (dir != null && !dir.isEmpty() && Files.exists(Path.of(dir, "discord-ipc-" + i))) {
						sock = Path.of(dir, "discord-ipc-" + i);
						break;
					}
				}
				if (sock == null && Files.exists(Path.of("/tmp", "discord-ipc-" + i))) {
					sock = Path.of("/tmp", "discord-ipc-" + i);
				}
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
			}
		}
		return null;
	}
}
