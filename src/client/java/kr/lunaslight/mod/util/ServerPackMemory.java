package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * 49-144차(사용자: "리소스팩 기억 기능 - 클라이언트에서 서버로 켜면 그 서버에서 받은 리소스팩을 미리 적용, 들어가서
 * 적용되는 거 말고. 대신 리소스팩 업데이트됐을 수도 있으니까 체크"): <b>서버 리소스팩 기억</b>.
 *
 * <ol>
 *   <li><b>기억</b>: 서버가 리소스팩을 보내면(주소, SHA-1) 바닐라가 받은 파일을 찾아
 *       {@code config/lunaslight/server-packs/}에 서버별로 복사해 둔다.</li>
 *   <li><b>미리 적용</b>: 런처의 서버 모드(= 실행 인수 {@code --quickPlayMultiplayer} / {@code --server})로 켜지면,
 *       옵션 파일(options.txt)을 읽기 <b>전에</b> 그 서버의 기억된 팩을 resourcepacks/에 넣고 켜진 팩 목록 맨 위에 올린다.
 *       그래서 첫 로딩 때 이미 서버 팩이 적용돼 있다.</li>
 *   <li><b>들어갈 때 확인</b>: 접속 중 서버가 팩을 보내면 네티 통로에 끼워 둔 처리기가 SHA-1을 본다.
 *       미리 적용한 팩과 같으면 바닐라에 넘기지 않고 서버에 "받음 + 적용 완료"만 답한다(다시 로딩 없음).
 *       다르면(서버가 팩을 바꿈) 바닐라가 평소대로 받아 적용하고, 새 팩을 다시 기억한다(다음 실행부터 새 팩).</li>
 * </ol>
 * 믹스인 없이 리플렉션 + 네티 처리기(프록시)만 쓴다 - 40개 버전의 패킷 이름이 다 달라서다.
 * 바닐라와 우리가 켠 팩은 게임을 끌 때까지 그대로 켜져 있고, 다음 실행 때 옵션에서 지운다.
 */
public final class ServerPackMemory {
	private ServerPackMemory() {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String PREFIX = "luna-server-";

	/** 서버 키 → 기억한 팩들(보낸 순서). */
	private static final Map<String, List<Pack>> INDEX = new ConcurrentHashMap<>();
	private static boolean indexLoaded;
	/** 이번 실행에 미리 켠 서버 키와 SHA-1들. */
	private static String preHost;
	private static final Set<String> PRE_HASHES = ConcurrentHashMap.newKeySet();
	/** 받는 중이라 파일을 찾아 기억해야 할 팩. */
	private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();
	/** 서버 키 → 이번 접속에서 서버가 보낸 SHA-1들(예전 팩 정리용). */
	private static final Map<String, Set<String>> OFFERED = new ConcurrentHashMap<>();
	private static final Map<String, Long> OFFERED_AT = new ConcurrentHashMap<>();
	private static WeakReference<Object> hooked = new WeakReference<>(null);
	private static volatile boolean enabled = true;

	public static final class Pack {
		String hash = "";
		String url = "";
		String file = "";
	}

	private record Pending(String host, String url, String hash, long at) {
	}

	public static void setEnabled(boolean on) {
		enabled = on;
	}

	// ==================== 경로 / 키 ====================

	private static Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	private static Path storeDir() {
		return gameDir().resolve("config").resolve("lunaslight").resolve("server-packs");
	}

	/** "mcng.kr:25565" → "mcng.kr". 파일 이름에 쓸 수 있는 글자만. */
	static String hostKey(String address) {
		if (address == null) {
			return null;
		}
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.endsWith(":25565")) {
			a = a.substring(0, a.length() - 6);
		}
		if (a.endsWith(".")) {
			a = a.substring(0, a.length() - 1);
		}
		a = a.replaceAll("[^a-z0-9._-]", "_");
		return a.isEmpty() ? null : a;
	}

	// ==================== 기억 파일 ====================

	@SuppressWarnings("deprecation")
	private static synchronized void loadIndex() {
		if (indexLoaded) {
			return;
		}
		indexLoaded = true;
		Path f = storeDir().resolve("index.json");
		if (!Files.exists(f)) {
			return;
		}
		try {
			JsonElement root = new JsonParser().parse(Files.readString(f, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
				List<Pack> list = new ArrayList<>();
				if (e.getValue().isJsonArray()) {
					for (JsonElement pe : e.getValue().getAsJsonArray()) {
						JsonObject po = pe.getAsJsonObject();
						Pack p = new Pack();
						p.hash = po.has("hash") ? po.get("hash").getAsString() : "";
						p.url = po.has("url") ? po.get("url").getAsString() : "";
						p.file = po.has("file") ? po.get("file").getAsString() : "";
						if (!p.file.isEmpty() && Files.exists(storeDir().resolve(p.file))) {
							list.add(p);
						}
					}
				}
				if (!list.isEmpty()) {
					INDEX.put(e.getKey(), new CopyOnWriteArrayList<>(list));
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:load", t);
		}
	}

	private static synchronized void saveIndex() {
		try {
			Files.createDirectories(storeDir());
			JsonObject root = new JsonObject();
			for (Map.Entry<String, List<Pack>> e : INDEX.entrySet()) {
				JsonArray arr = new JsonArray();
				for (Pack p : e.getValue()) {
					JsonObject po = new JsonObject();
					po.addProperty("hash", p.hash);
					po.addProperty("url", p.url);
					po.addProperty("file", p.file);
					arr.add(po);
				}
				root.add(e.getKey(), arr);
			}
			Files.writeString(storeDir().resolve("index.json"), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:save", t);
		}
	}

	/** 기억한 서버 수(설정 화면 안내용). */
	public static int rememberedCount() {
		loadIndex();
		return INDEX.size();
	}

	/** 기억을 전부 지운다(파일까지). */
	public static void forgetAll() {
		loadIndex();
		INDEX.clear();
		try (Stream<Path> s = Files.list(storeDir())) {
			s.forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (Throwable ignored) {
				}
			});
		} catch (Throwable ignored) {
		}
		saveIndex();
	}

	// ==================== 1. 실행할 때: 미리 적용 ====================

	/** 실행 인수에서 바로 들어갈 서버("--quickPlayMultiplayer host:port" / "--server host --port n"). 없으면 null. */
	static String launchTarget() {
		try {
			String[] args = FabricLoader.getInstance().getLaunchArguments(true);
			String server = null;
			String port = null;
			for (int i = 0; i + 1 < args.length; i++) {
				String a = args[i];
				if ("--quickPlayMultiplayer".equals(a)) {
					return args[i + 1];
				}
				if ("--server".equals(a)) {
					server = args[i + 1];
				}
				if ("--port".equals(a)) {
					port = args[i + 1];
				}
			}
			if (server != null) {
				return port == null ? server : server + ":" + port;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:args", t);
		}
		return null;
	}

	/**
	 * 모드 초기화 때(옵션 파일을 읽기 전) 한 번. 예전 실행에서 넣은 우리 팩은 늘 먼저 뺀다.
	 * 켜져 있고 서버 모드로 실행됐으면 그 서버의 기억된 팩을 넣고 켠다.
	 */
	public static void startup(boolean on) {
		enabled = on;
		try {
			loadIndex();
			Path packsDir = gameDir().resolve("resourcepacks");
			List<String> ours = new ArrayList<>();
			String target = on ? hostKey(launchTarget()) : null;
			List<Pack> packs = target == null ? null : INDEX.get(target);
			// 지난번에 넣은 복사본 정리
			if (Files.isDirectory(packsDir)) {
				try (Stream<Path> s = Files.list(packsDir)) {
					s.filter(p -> p.getFileName().toString().startsWith(PREFIX)).forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (Throwable ignored) {
						}
					});
				}
			}
			if (packs != null && !packs.isEmpty()) {
				Files.createDirectories(packsDir);
				int i = 0;
				for (Pack p : packs) {
					Path src = storeDir().resolve(p.file);
					if (!Files.exists(src)) {
						continue;
					}
					String name = PREFIX + target + "-" + (i++) + ".zip";
					Files.copy(src, packsDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
					ours.add("file/" + name);
					if (!p.hash.isEmpty()) {
						PRE_HASHES.add(p.hash.toLowerCase(Locale.ROOT));
					}
				}
				if (!ours.isEmpty()) {
					preHost = target;
				}
			}
			rewriteOptions(ours);
			if (preHost != null) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 리소스팩 기억: " + preHost + " 팩 " + ours.size()
					+ "개를 미리 적용합니다");
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:startup", t);
		}
	}

	/** options.txt의 resourcePacks/incompatibleResourcePacks에서 우리 팩을 빼고, ours를 맨 위(끝)에 넣는다. */
	@SuppressWarnings("deprecation")
	private static void rewriteOptions(List<String> ours) throws java.io.IOException {
		Path opt = gameDir().resolve("options.txt");
		if (!Files.exists(opt)) {
			if (ours.isEmpty()) {
				return;
			}
			JsonArray arr = new JsonArray();
			arr.add("vanilla");
			ours.forEach(arr::add);
			JsonArray inc = new JsonArray();
			ours.forEach(inc::add);
			Files.writeString(opt, "resourcePacks:" + arr + "\nincompatibleResourcePacks:" + inc + "\n", StandardCharsets.UTF_8);
			return;
		}
		List<String> lines = Files.readAllLines(opt, StandardCharsets.UTF_8);
		boolean sawPacks = false;
		boolean sawInc = false;
		boolean changed = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean packs = line.startsWith("resourcePacks:");
			boolean inc = line.startsWith("incompatibleResourcePacks:");
			if (!packs && !inc) {
				continue;
			}
			String key = packs ? "resourcePacks:" : "incompatibleResourcePacks:";
			JsonArray arr;
			try {
				JsonElement el = new JsonParser().parse(line.substring(key.length()));
				arr = el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
			} catch (Throwable t) {
				arr = new JsonArray();
			}
			JsonArray out = new JsonArray();
			for (JsonElement e : arr) {
				String v = e.getAsString();
				if (!v.startsWith("file/" + PREFIX)) {
					out.add(v);
				}
			}
			ours.forEach(out::add);
			String nl = key + out;
			if (!nl.equals(line)) {
				lines.set(i, nl);
				changed = true;
			}
			sawPacks |= packs;
			sawInc |= inc;
		}
		if (!ours.isEmpty() && !sawPacks) {
			JsonArray arr = new JsonArray();
			arr.add("vanilla");
			ours.forEach(arr::add);
			lines.add("resourcePacks:" + arr);
			changed = true;
		}
		if (!ours.isEmpty() && !sawInc) {
			JsonArray inc = new JsonArray();
			ours.forEach(inc::add);
			lines.add("incompatibleResourcePacks:" + inc);
			changed = true;
		}
		if (changed) {
			Files.write(opt, lines, StandardCharsets.UTF_8);
		}
	}

	// ==================== 2. 접속 중: 네티 처리기 ====================

	/** 매 틱: 지금 연결에 처리기를 한 번 끼우고, 받는 중인 팩을 찾아 기억한다. */
	public static void tick(MinecraftClient client) {
		if (!enabled || client == null) {
			return;
		}
		try {
			Object conn = currentConnection(client);
			if (conn != null && hooked.get() != conn) {
				if (install(conn)) {
					hooked = new WeakReference<>(conn);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:hook", t);
		}
		processPending();
	}

	private static Object currentConnection(MinecraftClient client) {
		Object screen = client.currentScreen;
		if (screen != null) {
			Object c = LunaCompat.getFieldValue(screen, "connection");
			if (c != null && LunaCompat.getFieldValue(c, "channel") != null) {
				return c;
			}
		}
		Object handler = LunaCompat.callNoArg(client, "getNetworkHandler");
		if (handler == null) {
			handler = LunaCompat.callNoArg(client, "getConnection");
		}
		if (handler != null) {
			Object c = LunaCompat.callNoArg(handler, "getConnection");
			if (c != null && LunaCompat.getFieldValue(c, "channel") != null) {
				return c;
			}
		}
		return null;
	}

	private static boolean install(Object conn) throws Exception {
		Object channel = LunaCompat.getFieldValue(conn, "channel");
		if (channel == null) {
			return false;
		}
		Class<?> chCls = Class.forName("io.netty.channel.Channel", false, channel.getClass().getClassLoader());
		Object pipeline = chCls.getMethod("pipeline").invoke(channel);
		Class<?> pipeCls = Class.forName("io.netty.channel.ChannelPipeline", false, channel.getClass().getClassLoader());
		Class<?> handlerCls = Class.forName("io.netty.channel.ChannelHandler", false, channel.getClass().getClassLoader());
		Class<?> inboundCls = Class.forName("io.netty.channel.ChannelInboundHandler", false, channel.getClass().getClassLoader());
		if (pipeCls.getMethod("get", String.class).invoke(pipeline, "luna_packmem") != null) {
			return true;
		}
		// 바닐라 연결 객체(ClientConnection)가 들어 있는 칸 - 보통 "packet_handler"
		String before = null;
		@SuppressWarnings("unchecked")
		List<String> names = (List<String>) pipeCls.getMethod("names").invoke(pipeline);
		for (String n : names) {
			if (pipeCls.getMethod("get", String.class).invoke(pipeline, n) == conn) {
				before = n;
				break;
			}
		}
		if (before == null) {
			before = names.contains("packet_handler") ? "packet_handler" : null;
		}
		if (before == null) {
			return false;
		}
		Object proxy = Proxy.newProxyInstance(inboundCls.getClassLoader(), new Class<?>[]{inboundCls}, new Handler(conn));
		pipeCls.getMethod("addBefore", String.class, String.class, handlerCls).invoke(pipeline, before, "luna_packmem", proxy);
		return true;
	}

	/** ChannelInboundHandler 프록시: 리소스팩 보내기 패킷만 보고 나머지는 그대로 다음으로. */
	private static final Map<String, Method> FIRE = new ConcurrentHashMap<>();

	private static final class Handler implements InvocationHandler {
		private final Object conn;

		Handler(Object conn) {
			this.conn = conn;
		}

		@Override
		public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
			String name = m.getName();
			switch (name) {
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "LunaPackMemoryHandler";
				case "handlerAdded":
				case "handlerRemoved":
					return null;
				default:
					break;
			}
			Object ctx = args[0];
			if ("channelRead".equals(name)) {
				Object msg = args[1];
				try {
					if (isPackOffer(msg) && handleOffer(conn, msg)) {
						return null;   // 삼킴: 미리 적용한 팩과 같다
					}
				} catch (Throwable t) {
					LunaCompat.warnOnce("packmem:read", t);
				}
			}
			// 공개 인터페이스(ChannelHandlerContext)의 fireXxx로 넘긴다 - 구현 클래스는 패키지 전용이라 직접 못 부른다.
			// 49-151차: 패킷마다 찾지 않게 메서드를 한 번만 찾아 둔다(싱글플레이는 초당 패킷 수천 개).
			Method fm = FIRE.get(name);
			if (fm == null) {
				String fire = "fire" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
				Class<?> ctxIface = Class.forName("io.netty.channel.ChannelHandlerContext", false, ctx.getClass().getClassLoader());
				for (Method c : ctxIface.getMethods()) {
					if (c.getName().equals(fire) && c.getParameterCount() == args.length - 1) {
						fm = c;
						break;
					}
				}
				if (fm == null) {
					return null;
				}
				FIRE.put(name, fm);
			}
			if (args.length == 2) {
				fm.invoke(ctx, args[1]);
			} else {
				fm.invoke(ctx);
			}
			return null;
		}
	}

	// ---- 패킷 이름(yarn 1.15~1.21.11 / mojmap 26.x)

	private static final String[] OFFER_CLASSES = {
		"net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket",
		"net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket",
		"net.minecraft.client.network.packet.ResourcePackSendS2CPacket",
		"net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket",
		"net.minecraft.network.protocol.game.ClientboundResourcePackPacket"};
	private static final String[] STATUS_CLASSES = {
		"net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket",
		"net.minecraft.network.packet.c2s.play.ResourcePackStatusC2SPacket",
		"net.minecraft.server.network.packet.ResourcePackStatusC2SPacket",
		"net.minecraft.network.protocol.common.ServerboundResourcePackPacket",
		"net.minecraft.network.protocol.game.ServerboundResourcePackPacket"};
	private static final String[] PACKET_IFACES = {
		"net.minecraft.network.packet.Packet",
		"net.minecraft.network.Packet",
		"net.minecraft.network.protocol.Packet"};

	private static Class<?> offerClass;
	private static boolean offerResolved;

	private static boolean isPackOffer(Object msg) {
		if (msg == null) {
			return false;
		}
		if (!offerResolved) {
			offerResolved = true;
			offerClass = firstClass(OFFER_CLASSES);
		}
		return offerClass != null && offerClass.isInstance(msg);
	}

	private static Class<?> firstClass(String[] names) {
		for (String n : names) {
			Class<?> c = LunaCompat.classOrNull(n);
			if (c != null) {
				return c;
			}
		}
		return null;
	}

	private static String str(Object o, String... methods) {
		for (String m : methods) {
			Object v = LunaCompat.callNoArg(o, m);
			if (v instanceof String s) {
				return s;
			}
		}
		return "";
	}

	/** 서버가 팩을 보냄. 미리 적용한 팩과 같으면 서버에 답만 하고 true(바닐라에 안 넘김). */
	private static boolean handleOffer(Object conn, Object msg) {
		String url = str(msg, "url", "getURL", "getUrl");
		String hash = str(msg, "hash", "getSHA1", "getHash").toLowerCase(Locale.ROOT);
		Object idObj = LunaCompat.callNoArg(msg, "id");
		UUID id = idObj instanceof UUID u ? u : null;
		String host = hostKey(LunaCompat.currentServerAddress(MinecraftClient.getInstance()));
		if (host == null) {
			host = preHost;
		}
		if (host != null && !hash.isEmpty()) {
			OFFERED.computeIfAbsent(host, k -> ConcurrentHashMap.newKeySet()).add(hash);
			OFFERED_AT.put(host, System.currentTimeMillis());
		}
		boolean same = host != null && host.equals(preHost) && !hash.isEmpty() && PRE_HASHES.contains(hash);
		if (same && reply(conn, id)) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 리소스팩 기억: 미리 적용한 팩과 같아서 다시 받지 않음 (" + hash + ")");
			return true;
		}
		if (host != null) {
			PENDING.add(new Pending(host, url, hash, System.currentTimeMillis()));
		}
		return false;
	}

	/** 서버에 "받음(→ 내려받음) → 적용 완료"를 보낸다. 하나라도 못 만들면 false(그럼 바닐라에 맡긴다). */
	private static boolean reply(Object conn, UUID id) {
		Class<?> statusPkt = firstClass(STATUS_CLASSES);
		Class<?> packetIface = firstClass(PACKET_IFACES);
		if (statusPkt == null || packetIface == null) {
			return false;
		}
		Class<?> statusEnum = null;
		for (Class<?> c : statusPkt.getDeclaredClasses()) {
			if (c.isEnum()) {
				statusEnum = c;
				break;
			}
		}
		if (statusEnum == null) {
			return false;
		}
		Method send = null;
		for (Class<?> c = conn.getClass(); c != null && send == null; c = c.getSuperclass()) {
			List<String> cand = LunaCompat.memberNameCandidates(c, "send");
			for (Method m : c.getDeclaredMethods()) {
				if (cand.contains(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(packetIface)) {
					m.setAccessible(true);
					send = m;
					break;
				}
			}
		}
		if (send == null) {
			return false;
		}
		String[] steps = id != null
			? new String[]{"ACCEPTED", "DOWNLOADED", "SUCCESSFULLY_LOADED"}
			: new String[]{"ACCEPTED", "SUCCESSFULLY_LOADED"};
		List<Object> packets = new ArrayList<>();
		for (String st : steps) {
			Object status = enumConst(statusEnum, st);
			Object pkt = status == null ? null : newStatus(statusPkt, statusEnum, id, status);
			if (pkt == null) {
				return false;
			}
			packets.add(pkt);
		}
		try {
			for (Object p : packets) {
				send.invoke(conn, p);
			}
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:send", t);
			return false;
		}
	}

	/** 바닐라 순서: SUCCESSFULLY_LOADED 0, DECLINED 1, FAILED_DOWNLOAD 2, ACCEPTED 3, DOWNLOADED 4. */
	private static Object enumConst(Class<?> e, String name) {
		try {
			java.lang.reflect.Field f = LunaCompat.getFieldCompat(e, name);
			f.setAccessible(true);
			Object v = f.get(null);
			if (v != null) {
				return v;
			}
		} catch (Throwable ignored) {
		}
		Object[] all = e.getEnumConstants();
		int idx = switch (name) {
			case "SUCCESSFULLY_LOADED" -> 0;
			case "ACCEPTED" -> 3;
			case "DOWNLOADED" -> 4;
			default -> -1;
		};
		return idx >= 0 && all != null && idx < all.length ? all[idx] : null;
	}

	private static Object newStatus(Class<?> pkt, Class<?> statusEnum, UUID id, Object status) {
		for (Constructor<?> c : pkt.getDeclaredConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			try {
				c.setAccessible(true);
				if (p.length == 2 && p[0] == UUID.class && p[1] == statusEnum && id != null) {
					return c.newInstance(id, status);
				}
				if (p.length == 1 && p[0] == statusEnum && id == null) {
					return c.newInstance(status);
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	// ==================== 3. 기억: 바닐라가 받은 파일 찾기 ====================

	private static long lastScan;

	private static void processPending() {
		long now = System.currentTimeMillis();
		if (now - lastScan < 2000) {
			return;
		}
		lastScan = now;
		for (Pending p : PENDING) {
			if (now - p.at() < 2500) {
				continue;
			}
			Path f = findDownloaded(p);
			if (f != null) {
				remember(p, f);
				PENDING.remove(p);
			} else if (now - p.at() > 180_000L) {
				PENDING.remove(p);   // 끝내 못 찾음(거절했거나 받기 실패)
			}
		}
		// 이번 접속에서 서버가 안 보낸 예전 팩은 정리(보낸 지 1분 지나 받을 게 다 끝났을 때)
		for (Map.Entry<String, Long> e : OFFERED_AT.entrySet()) {
			String host = e.getKey();
			boolean busy = PENDING.stream().anyMatch(p -> p.host().equals(host));
			if (!busy && now - e.getValue() > 60_000L) {
				Set<String> offered = OFFERED.getOrDefault(host, Set.of());
				List<Pack> list = INDEX.get(host);
				if (list != null && list.removeIf(p -> !p.hash.isEmpty() && !offered.contains(p.hash))) {
					saveIndex();
				}
				OFFERED_AT.remove(host);
				OFFERED.remove(host);
			}
		}
	}

	/** 바닐라 서버 팩 폴더(1.20.3+ downloads/, 그 전 server-resource-packs/)에서 그 팩 파일. */
	private static Path findDownloaded(Pending p) {
		List<Path> roots = List.of(gameDir().resolve("downloads"), gameDir().resolve("server-resource-packs"));
		Path best = null;
		long bestTime = 0;
		for (Path root : roots) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			try (Stream<Path> s = Files.walk(root, 3)) {
				for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
					String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
					if (n.endsWith(".json") || n.endsWith(".log")) {
						continue;
					}
					long mod = Files.getLastModifiedTime(f).toMillis();
					if (!p.hash().isEmpty()) {
						if (n.equals(p.hash()) || (mod >= p.at() - 600_000L && p.hash().equals(sha1(f)))) {
							return f;
						}
					} else if (mod >= p.at() - 5000 && mod > bestTime) {
						best = f;
						bestTime = mod;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return best;
	}

	private static String sha1(Path f) {
		try (InputStream in = Files.newInputStream(f)) {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buf = new byte[65536];
			int r;
			while ((r = in.read(buf)) > 0) {
				md.update(buf, 0, r);
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Throwable t) {
			return "";
		}
	}

	private static void remember(Pending p, Path file) {
		try {
			loadIndex();
			Files.createDirectories(storeDir());
			String hash = p.hash().isEmpty() ? sha1(file) : p.hash();
			List<Pack> list = INDEX.computeIfAbsent(p.host(), k -> new CopyOnWriteArrayList<>());
			for (Pack old : list) {
				if (old.hash.equals(hash)) {
					return;   // 이미 기억함
				}
			}
			// 같은 주소의 옛 팩(= 서버가 팩을 갱신)은 바꾼다
			list.removeIf(old -> !old.url.isEmpty() && old.url.equals(p.url()));
			String name = p.host() + "-" + hash.substring(0, Math.min(12, hash.length())) + ".zip";
			Files.copy(file, storeDir().resolve(name), StandardCopyOption.REPLACE_EXISTING);
			Pack pk = new Pack();
			pk.hash = hash;
			pk.url = p.url() == null ? "" : p.url();
			pk.file = name;
			list.add(pk);
			// 목록에서 빠진 옛 파일 정리
			Set<String> keep = new HashSet<>();
			INDEX.values().forEach(l -> l.forEach(x -> keep.add(x.file)));
			try (Stream<Path> s = Files.list(storeDir())) {
				s.filter(x -> x.getFileName().toString().endsWith(".zip") && !keep.contains(x.getFileName().toString()))
					.forEach(x -> {
						try {
							Files.deleteIfExists(x);
						} catch (Throwable ignored) {
						}
					});
			}
			saveIndex();
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 리소스팩 기억: " + p.host() + " 팩을 기억했습니다 (" + hash + ")");
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:remember", t);
		}
	}

	/** 설정 화면 안내: 기억한 서버 목록. */
	public static Map<String, Integer> summary() {
		loadIndex();
		Map<String, Integer> out = new LinkedHashMap<>();
		INDEX.forEach((k, v) -> out.put(k, v.size()));
		return out;
	}
}
