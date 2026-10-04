package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.LunaClientMod;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 49-23차: 게임 안 "소셜"(친구 · 프로필 전환) 데이터. 런처가 실행 직전에 .luna-launch.json에 실어 보낸
 * 런처 계정 id/닉네임, Supabase REST 주소/anon 키, 등록된 마인크래프트 계정(uuid/이름/액세스 토큰)을 읽는다
 * (런처 main.js collectSocialBridgeForMod 참고). 런처 밖 실행/개발 환경이면 available()이 false.
 *
 *  친구 목록: 런처 friends:list와 같은 쿼리(friends 테이블 + site_presence로 온라인/자리비움/오프라인 90초/5분 창).
 *  프로필 전환: Session을 그 버전 생성자에 맞춰 리플렉션으로 만들어 MinecraftClient.session(+userApiService,
 *  profileKeys - 되는 버전만)을 갈아끼움. 이미 접속 중인 서버에는 영향 없고 다음 접속부터 새 계정.
 */
public final class LunaSocial {
	private LunaSocial() {
	}

	public record Account(String uuid, String name, String accessToken) {
	}

	/**
	 * 친구 한 명. 49-27차: 접속 중인 서버 주소(server)·서버 표시 이름(serverName)·마인크래프트 닉네임(mcName)이
	 * site_presence에 있으면 같이 담는다(없으면 빈 문자열 - 참가/귓속말 버튼이 안 뜸).
	 */
	public record Friend(String id, String accountId, String name, String presence, String status,
			String server, String serverName, String mcName) {
		public boolean canJoin() {
			return server != null && !server.isEmpty() && !"offline".equals(presence);
		}

		public String whisperName() {
			return mcName != null && !mcName.isEmpty() ? mcName : name;
		}

		public String place() {
			if ("offline".equals(presence)) {
				return "";
			}
			if (serverName != null && !serverName.isEmpty()) {
				return serverName;
			}
			return server == null ? "" : server;
		}
	}

	private static boolean loaded;
	private static String siteId, siteName, supabaseUrl, anonKey, activeUuid;
	private static final List<Account> ACCOUNTS = new ArrayList<>();
	private static HttpClient http;

	private static final long ONLINE_WINDOW_MS = 90L * 1000L;
	private static final long AWAY_WINDOW_MS = 5L * 60L * 1000L;

	private static String launcherVersion;

	/** 런처 버전(없으면 null). */
	public static String launcherVersion() {
		return launcherVersion;
	}

	@SuppressWarnings("deprecation") // new JsonParser().parse - 1.16(gson 2.8.0)엔 parseString이 없어 일부러 옛 API
	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path p = null;
		try {
			p = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".luna-launch.json");
			if (!Files.exists(p)) {
				return;
			}
			String raw = Files.readString(p, StandardCharsets.UTF_8);
			// 사용자 요청: 계정 토큰이 게임 폴더에 남지 않게 읽자마자 파일 삭제(런처가 다음 실행 때 다시 씀).
			// 개발 환경(gradlew runClient)은 파일 없이도 도니 삭제해도 지장 없음.
			deleteLaunchFile(p);
			JsonElement root = new JsonParser().parse(raw);
			if (root == null || !root.isJsonObject()) {
				return;
			}
			JsonObject o = root.getAsJsonObject();
			// 49-29차: 런처에서 장착한 색(치장품) - 있으면 GUI/HUD 기본색이 이 색을 따라간다
			// 49-40차: 배경 테마(런처 "테마" 상품 - 블랙 & 화이트/핑크/아쿠아/스카이/마인크래프트/메탈)도 같이 받는다:
			//   cosmetics.theme = { id, mode, name } (mode = 런처 style.css의 data-color-theme 값). 색상 상품을 따로
			//   장착하지 않았으면 테마의 기본 포인트색을 쓴다(LunaTheme.refresh).
			try {
				String accent = null;
				String mode = null;
				if (o.has("cosmetics") && o.get("cosmetics").isJsonObject()) {
					JsonObject cos = o.getAsJsonObject("cosmetics");
					accent = firstNonEmpty(str(cos, "accent"), str(cos, "color"), str(cos, "themeColor"));
					cosmeticColorName = firstNonEmpty(str(cos, "name")); // 49-44차: 프로필 화면 표시용
					// 49-240차: 노바 망토 cosmetics.cape = { id, key, name, c1, c2 } (끼었을 때만)
					if (cos.has("cape") && cos.get("cape").isJsonObject()) {
						JsonObject cp = cos.getAsJsonObject("cape");
						NovaCapes.setMine(firstNonEmpty(str(cp, "key"), str(cp, "id")));
					}
					if (cos.has("theme") && cos.get("theme").isJsonObject()) {
						JsonObject th = cos.getAsJsonObject("theme");
						mode = firstNonEmpty(str(th, "mode"), str(th, "id"));
						cosmeticThemeName = firstNonEmpty(str(th, "name"));
					}
					if (mode == null || mode.isEmpty()) {
						mode = firstNonEmpty(str(cos, "mode"), str(cos, "themeMode"), str(cos, "theme"));
					}
				}
				if (accent == null || accent.isEmpty()) {
					accent = firstNonEmpty(str(o, "accent"), str(o, "themeColor"));
				}
				LunaTheme.Base base = LunaTheme.Base.fromLauncherMode(mode);
				if (base != null) {
					LunaTheme.setLauncherBase(base);
				}
				int parsed = parseColor(accent);
				if (parsed != 0) {
					LunaTheme.setLauncherAccent(parsed);
				}
			} catch (Throwable ignored) {
			}
			launcherVersion = str(o, "launcherVersion");
			// 49-213차: 처음 설정 - 고른 값을 남길 런처 폴더 파일, 설정을 마친 뒤 들어갈 서버(런처가 바로 접속하지 않고 넘겨 줌)
			setupPrefsPath = str(o, "setupPrefsPath");
			pendingServer = str(o, "pendingServer");
			pendingServerName = str(o, "pendingServerName");
			logoUrl = str(o, "logoUrl");   // 49-207차: 디스코드 상태 그림 주소(런처가 알려 줌)   // 49-143차: 타이틀 화면 "Nova Client x.y.z"
			if (o.has("site") && o.get("site").isJsonObject()) {
				JsonObject site = o.getAsJsonObject("site");
				siteId = str(site, "id");
				siteName = str(site, "name");
				// 49-74차(5-9): 닉네임으로 친구를 찾으려면 Luna Site 주소가 필요하다. 런처가 실어 보내면
				// 그걸 쓰고, 옛 런처면 아래 기본값(siteApi())으로 간다 - 그래서 런처를 안 고쳐도 동작한다.
				siteApiBase = str(site, "api");
			}
			if (o.has("supabase") && o.get("supabase").isJsonObject()) {
				JsonObject sb = o.getAsJsonObject("supabase");
				supabaseUrl = str(sb, "url");
				anonKey = str(sb, "anonKey");
				presenceIdCol = str(sb, "presenceIdColumn");   // 49-207차: site_presence의 계정 id 컬럼 이름(런처가 알려 줌)
			}
			activeUuid = str(o, "activeUuid");
			if (o.has("accounts") && o.get("accounts").isJsonArray()) {
				for (JsonElement e : o.get("accounts").getAsJsonArray()) {
					if (!e.isJsonObject()) {
						continue;
					}
					JsonObject a = e.getAsJsonObject();
					String uuid = str(a, "uuid");
					String name = str(a, "name");
					String token = str(a, "accessToken");
					if (uuid != null && name != null && token != null) {
						ACCOUNTS.add(new Account(uuid, name, token));
					}
				}
			}
		} catch (Throwable t) {
			LunaClientMod.LOGGER.warn("[Nova] 소셜 정보(.luna-launch.json) 읽기 실패", t);
			if (p != null) {
				deleteLaunchFile(p); // 읽기에 실패해도 토큰 파일은 남기지 않음
			}
		}
	}

	private static volatile String setupPrefsPath;
	private static volatile String pendingServer;
	private static volatile String pendingServerName;

	/** 49-213차: 처음 설정 공용 기록 파일(런처 폴더). 옛 런처면 null. */
	public static String setupPrefsPath() {
		return setupPrefsPath;
	}

	/** 49-213차: 처음 설정을 마친 뒤 들어갈 서버 "주소:포트". 없으면 null. */
	public static String pendingServer() {
		return pendingServer;
	}

	public static String pendingServerName() {
		return pendingServerName;
	}

	public static void clearPendingServer() {
		pendingServer = null;
	}

	private static void deleteLaunchFile(Path p) {
		try {
			if (Files.deleteIfExists(p)) {
				LunaClientMod.LOGGER.info("[Nova] 실행 정보 파일(.luna-launch.json) 읽은 뒤 삭제");
			}
		} catch (Throwable t) {
			// 삭제 실패(잠금 등) - 다음 실행 때 런처가 덮어씀. 덮어쓰기 전까지 남으므로 한 번 더 시도.
			try {
				p.toFile().deleteOnExit();
			} catch (Throwable ignored) {
			}
			LunaClientMod.LOGGER.warn("[Nova] .luna-launch.json 삭제 실패 - 게임 종료 시 다시 시도", t);
		}
	}

	private static String str(JsonObject o, String key) {
		try {
			JsonElement e = o.get(key);
			return e == null || e.isJsonNull() ? null : e.getAsString();
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** 런처가 소셜 정보를 실어 보냈는지(친구 조회 가능). */
	public static boolean available() {
		load();
		return supabaseUrl != null && anonKey != null;
	}

	/** 런처 계정 로그인 상태인지(친구 목록은 런처 계정 단위). */
	public static boolean signedIn() {
		load();
		return available() && siteId != null;
	}

	public static String siteName() {
		load();
		return siteName == null ? "게스트" : siteName;
	}

	public static List<Account> accounts() {
		load();
		return ACCOUNTS;
	}

	public static String activeUuid() {
		load();
		return activeUuid;
	}

	// ---------------------------------------------------------------- 친구

	private static HttpClient http() {
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
		}
		return http;
	}

	@SuppressWarnings("deprecation")
	private static CompletableFuture<JsonElement> rest(String pathAndQuery) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(supabaseUrl + "/rest/v1" + pathAndQuery))
				.timeout(Duration.ofSeconds(10))
				.header("apikey", anonKey)
				.header("Authorization", "Bearer " + anonKey)
				.header("Accept", "application/json")
				.GET().build();
		return http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() / 100 != 2) {
				throw new RuntimeException("Supabase " + res.statusCode() + ": " + res.body());
			}
			return new JsonParser().parse(res.body());
		});
	}

	/**
	 * 49-47차(사용자: "게임 내 귓속말은 마크 귓속말이 아니라 클라이언트 귓속말을 말한 거였어"):
	 * 런처와 같은 Supabase `whispers` 표에 직접 쓰고 읽는다(런처 main.js의 whisper:send / whisper:list와
	 * 같은 표·같은 anon 키). 게임 채팅으로는 아무것도 보내지 않는다.
	 */
	private static CompletableFuture<JsonElement> restPost(String pathAndQuery, String jsonBody) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(supabaseUrl + "/rest/v1" + pathAndQuery))
				.timeout(Duration.ofSeconds(10))
				.header("apikey", anonKey)
				.header("Authorization", "Bearer " + anonKey)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
		return http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() / 100 != 2) {
				throw new RuntimeException("Supabase " + res.statusCode() + ": " + res.body());
			}
			String body = res.body();
			return body == null || body.isEmpty() ? null : new JsonParser().parse(body);
		});
	}

	/** 귓속말 한 줄. fromMe = 내가 보낸 것. */
	public record Whisper(boolean fromMe, String text, String at) {
	}

	/** 그 친구와 주고받은 최근 대화(오래된 순, 최대 60줄). */
	public static CompletableFuture<List<Whisper>> fetchWhispers(String otherId) {
		load();
		if (!signedIn() || otherId == null || otherId.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		String me = siteId;
		String q = "/whispers?or=(and(sender_uuid.eq." + me + ",receiver_uuid.eq." + otherId + "),"
			+ "and(sender_uuid.eq." + otherId + ",receiver_uuid.eq." + me + "))"
			+ "&select=sender_uuid,message,created_at&order=created_at.desc&limit=60";
		return rest(q).thenApply(el -> {
			List<Whisper> out = new ArrayList<>();
			if (el != null && el.isJsonArray()) {
				for (JsonElement e : el.getAsJsonArray()) {
					if (!e.isJsonObject()) {
						continue;
					}
					JsonObject o = e.getAsJsonObject();
					String sender = str(o, "sender_uuid");
					out.add(new Whisper(normalize(sender).equals(normalize(me)), str(o, "message"), str(o, "created_at")));
				}
			}
			java.util.Collections.reverse(out); // 조회는 최신순, 화면은 오래된 순
			return out;
		}).exceptionally(t -> {
			LunaCompat.warnOnce("whisper:list", t);
			return List.of();
		});
	}

	/** 귓속말 보내기. 런처와 같은 규칙(친구끼리만, 500자). */
	public static CompletableFuture<Boolean> sendWhisper(Friend friend, String text) {
		load();
		if (!signedIn() || friend == null || text == null) {
			return CompletableFuture.completedFuture(false);
		}
		String msg = text.trim();
		if (msg.isEmpty()) {
			return CompletableFuture.completedFuture(false);
		}
		if (msg.length() > 500) {
			msg = msg.substring(0, 500);
		}
		JsonObject o = new JsonObject();
		o.addProperty("sender_uuid", siteId);
		o.addProperty("sender_name", siteName == null ? "" : siteName);
		o.addProperty("receiver_uuid", friend.accountId());
		o.addProperty("receiver_name", friend.name());
		o.addProperty("message", msg);
		return restPost("/whispers", o.toString()).thenApply(x -> true).exceptionally(t -> {
			LunaCompat.warnOnce("whisper:send", t);
			return false;
		});
	}

	/**
	 * 49-74차(5-9): POST 말고 <b>PATCH·DELETE</b>도 보내는 자리. 헤더·오류 처리는 {@link #restPost}와 똑같다.
	 *
	 * <p>런처 main.js의 {@code supabaseFetch}가 하는 일과 <b>같은 키·같은 주소</b>다 - 런처가 anon 키만으로
	 * friends 표에 쓰고 지우고 있으니(friends:add/remove/block), 모드도 <b>같은 권한</b>으로 된다.
	 * 그래서 이 기능에는 <b>Supabase 정책(RLS)을 새로 넣을 필요가 없다</b> - 예전 메모가 틀렸다.
	 */
	private static CompletableFuture<JsonElement> restSend(String method, String pathAndQuery, String jsonBody) {
		HttpRequest.BodyPublisher body = jsonBody == null
			? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder(URI.create(supabaseUrl + "/rest/v1" + pathAndQuery))
				.timeout(Duration.ofSeconds(10))
				.header("apikey", anonKey)
				.header("Authorization", "Bearer " + anonKey)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.method(method, body).build();
		return http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() / 100 != 2) {
				throw new RuntimeException("Supabase " + res.statusCode() + ": " + res.body());
			}
			String text = res.body();
			return text == null || text.isEmpty() ? null : new JsonParser().parse(text);
		});
	}

	/**
	 * 49-240차: 남의 노바 망토 - nova_player_tiers.cosmetic_cape(key, 안 끼면 null). uuid는 대시 있는 꼴/없는 꼴 둘 다로
	 * 묻고 결과는 대시 없는 소문자 uuid → key.
	 */
	public static CompletableFuture<java.util.Map<String, String>> fetchCapes(java.util.Collection<String> ids) {
		load();
		if (!available() || ids == null || ids.isEmpty()) {
			return CompletableFuture.completedFuture(java.util.Collections.emptyMap());
		}
		StringBuilder in = new StringBuilder();
		for (String id : ids) {
			String n = normalize(id);
			if (n.isEmpty()) {
				continue;
			}
			if (in.length() > 0) {
				in.append(',');
			}
			in.append('"').append(n).append("\",\"").append(dashed(n)).append('"');
		}
		return rest("/nova_player_tiers?mc_uuid=in." + enc("(" + in + ")") + "&select=mc_uuid,cosmetic_cape").thenApply(arr -> {
			java.util.Map<String, String> out = new java.util.HashMap<>();
			if (arr != null && arr.isJsonArray()) {
				for (JsonElement e : arr.getAsJsonArray()) {
					if (!e.isJsonObject()) {
						continue;
					}
					JsonObject r = e.getAsJsonObject();
					String u = normalize(str(r, "mc_uuid"));
					String k = r.has("cosmetic_cape") && !r.get("cosmetic_cape").isJsonNull() ? str(r, "cosmetic_cape") : "";
					if (!u.isEmpty()) {
						out.put(u, k == null ? "" : k);
					}
				}
			}
			return out;
		});
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- 친구 추가·삭제·차단(49-74차, 5-9)
	//
	// 런처(main.js)의 friends:add / friends:remove / friends:block과 **같은 표·같은 순서**로 움직인다.
	// 런처 코드를 그대로 옮긴 게 아니라, 런처가 실제로 보내는 요청을 하나씩 확인하고 맞춘 것이다:
	//
	//   1) 닉네임 → 런처 계정 id : Luna Site의 /api/app/account/social {by:"nickname"} (세션 토큰 필요 없음)
	//   2) 이미 있는 관계인지   : /friends?or=(and(...),and(...))&select=*
	//   3) 없으면 POST(pending) / 상대가 먼저 보냈으면 PATCH(accepted) / 차단이면 PATCH·POST(blocked)
	//   4) 삭제·거절·요청취소  : DELETE /friends?id=eq.<id> (셋 다 같은 한 줄 지우기다)
	//
	// **모드가 런처보다 덜 하는 것**: 프로필 팝업(스킨·게시글)은 열지 않는다 - 게임 안에서 볼 화면이 없다.

	/** Luna Site 런처 API 주소. 런처가 실어 보내면 그걸, 아니면 런처와 같은 기본값. */
	private static String siteApi() {
		// 49-207차: 사이트 주소는 런처가 실어 보낸 값만 쓴다(모드 안에 주소를 박아 두지 않음).
		return siteApiBase == null ? "" : siteApiBase;
	}

	private static String siteApiBase;
	private static String presenceIdCol;
	private static String logoUrl;

	/** 49-207차: 디스코드 상태에 쓸 그림 주소(런처가 안 주면 null - 그림 없이 표시). */
	public static String logoUrl() {
		return logoUrl == null || logoUrl.isEmpty() ? null : logoUrl;
	}

	/** 내 런처 계정 id(로그인 안 됐으면 null). */
	public static String myId() {
		load();
		return siteId;
	}

	/**
	 * 닉네임 → 런처 계정 {id, 닉네임}. 못 찾으면 null.
	 * 런처 friends:add가 쓰는 것과 <b>같은 엔드포인트</b>다(POST, 본문 {"by":"nickname","nickname":...}).
	 */
	@SuppressWarnings("deprecation")
	private static CompletableFuture<String[]> resolveNickname(String nickname) {
		JsonObject body = new JsonObject();
		body.addProperty("by", "nickname");
		body.addProperty("nickname", nickname);
		HttpRequest req = HttpRequest.newBuilder(URI.create(siteApi() + "/api/app/account/social"))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
		return http().sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			if (res.statusCode() / 100 != 2) {
				return null;
			}
			JsonElement el = new JsonParser().parse(res.body());
			if (el == null || !el.isJsonObject()) {
				return null;
			}
			JsonObject o = el.getAsJsonObject();
			if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
				return null;
			}
			String id = str(o, "id");
			if (id == null || id.isEmpty()) {
				return null;
			}
			String nick = str(o, "nickname");
			return new String[]{id, nick == null ? nickname : nick};
		}).exceptionally(t -> {
			LunaCompat.warnOnce("social:resolve", t);
			return null;
		});
	}

	/** 나와 상대 사이의 friends 행(없으면 null). {id, requester_uuid, status} */
	private static CompletableFuture<String[]> relationWith(String otherId) {
		String me = siteId;
		String q = "/friends?or=(and(requester_uuid.eq." + enc(me) + ",addressee_uuid.eq." + enc(otherId) + "),"
			+ "and(requester_uuid.eq." + enc(otherId) + ",addressee_uuid.eq." + enc(me) + "))&select=id,requester_uuid,status";
		return rest(q).thenApply(el -> {
			if (el == null || !el.isJsonArray() || el.getAsJsonArray().size() == 0) {
				return null;
			}
			JsonObject r = el.getAsJsonArray().get(0).getAsJsonObject();
			return new String[]{str(r, "id"), str(r, "requester_uuid"), str(r, "status")};
		});
	}

	/**
	 * 친구 요청 보내기. 성공하면 <b>사용자에게 보여 줄 한 줄</b>, 실패하면 이유.
	 * 상대가 이미 나한테 요청을 보내 둔 상태면 <b>바로 친구가 된다</b>(런처와 같은 규칙).
	 */
	public static CompletableFuture<String> addFriend(String nickname) {
		load();
		if (!signedIn()) {
			return CompletableFuture.completedFuture("런처에서 노바 계정으로 먼저 로그인하세요");
		}
		String nick = nickname == null ? "" : nickname.trim();
		if (nick.isEmpty()) {
			return CompletableFuture.completedFuture("닉네임을 입력하세요");
		}
		if (siteName != null && nick.equalsIgnoreCase(siteName)) {
			return CompletableFuture.completedFuture("자기 자신은 추가할 수 없어요");
		}
		return resolveNickname(nick).thenCompose(found -> {
			if (found == null) {
				return CompletableFuture.completedFuture("그런 닉네임의 노바 계정이 없어요");
			}
			if (sameId(found[0], siteId)) {
				return CompletableFuture.completedFuture("자기 자신은 추가할 수 없어요");
			}
			return relationWith(found[0]).thenCompose(rel -> {
				if (rel != null) {
					if ("accepted".equals(rel[2])) {
						return CompletableFuture.completedFuture("이미 친구예요");
					}
					if ("blocked".equals(rel[2])) {
						return CompletableFuture.completedFuture("차단된 상대예요");
					}
					if (sameId(rel[1], found[0])) {
						// 상대가 먼저 보내 둔 요청 - 수락으로 바꾼다
						JsonObject patch = new JsonObject();
						patch.addProperty("status", "accepted");
						return restSend("PATCH", "/friends?id=eq." + enc(rel[0]), patch.toString())
							.thenApply(x -> found[1] + " 님과 친구가 됐습니다");
					}
					return CompletableFuture.completedFuture("이미 요청을 보냈어요");
				}
				JsonObject row = new JsonObject();
				row.addProperty("requester_uuid", siteId);
				row.addProperty("requester_name", siteName == null ? "" : siteName);
				row.addProperty("addressee_uuid", found[0]);
				row.addProperty("addressee_name", found[1]);
				row.addProperty("status", "pending");
				return restPost("/friends", row.toString())
					.thenApply(x -> found[1] + " 님에게 친구 요청을 보냈습니다");
			});
		}).exceptionally(t -> {
			LunaCompat.warnOnce("social:addFriend", t);
			return "보내지 못했습니다 - 잠시 뒤 다시";
		});
	}

	/**
	 * 친구 삭제(= 요청 거절 · 요청 취소와 <b>같은 동작</b> - 셋 다 friends 한 줄을 지우는 것이다).
	 * 성공하면 null, 실패하면 이유.
	 */
	public static CompletableFuture<String> removeFriend(String rowId) {
		load();
		if (!signedIn() || rowId == null || rowId.isEmpty()) {
			return CompletableFuture.completedFuture("지울 수 없습니다");
		}
		return restSend("DELETE", "/friends?id=eq." + enc(rowId), null)
			.thenApply(x -> (String) null)
			.exceptionally(t -> {
				LunaCompat.warnOnce("social:removeFriend", t);
				return "지우지 못했습니다";
			});
	}

	/**
	 * 차단. 런처와 같게 <b>줄을 지우지 않고</b> status=blocked로 바꾸고 requester를 <b>항상 나</b>로 맞춘다
	 * (그래야 "내가 차단함"과 "상대가 나를 차단함"을 구분할 수 있다). 성공하면 null.
	 *
	 * <p>Supabase의 friends.status에 옛 CHECK 제약이 남아 있으면 여기서 실패한다 - 런처도 같은 조건이라
	 * 런처에서 차단이 되면 여기서도 된다.
	 */
	public static CompletableFuture<String> blockFriend(Friend friend) {
		load();
		if (!signedIn() || friend == null) {
			return CompletableFuture.completedFuture("차단할 수 없습니다");
		}
		JsonObject patch = new JsonObject();
		patch.addProperty("requester_uuid", siteId);
		patch.addProperty("requester_name", siteName == null ? "" : siteName);
		patch.addProperty("addressee_uuid", friend.accountId());
		patch.addProperty("addressee_name", friend.name());
		patch.addProperty("status", "blocked");
		return restSend("PATCH", "/friends?id=eq." + enc(friend.id()), patch.toString())
			.thenApply(x -> (String) null)
			.exceptionally(t -> {
				LunaCompat.warnOnce("social:blockFriend", t);
				return "차단하지 못했습니다";
			});
	}

	/**
	 * 나한테 <b>들어온</b> 친구 요청(아직 수락 안 한 것). 게임 안에서 추가만 되고 수락은 런처에서만
	 * 되면 반쪽이라 같이 넣었다.
	 */
	public static CompletableFuture<List<Friend>> fetchRequests() {
		load();
		if (!signedIn()) {
			return CompletableFuture.completedFuture(List.of());
		}
		return rest("/friends?addressee_uuid=eq." + enc(siteId)
				+ "&status=eq.pending&select=id,requester_uuid,requester_name")
			.thenApply(el -> {
				List<Friend> out = new ArrayList<>();
				if (el != null && el.isJsonArray()) {
					for (JsonElement e : el.getAsJsonArray()) {
						if (!e.isJsonObject()) {
							continue;
						}
						JsonObject r = e.getAsJsonObject();
						String name = str(r, "requester_name");
						out.add(new Friend(str(r, "id"), str(r, "requester_uuid"), name == null ? "?" : name,
								"offline", "", "", "", ""));
					}
				}
				return out;
			}).exceptionally(t -> {
				LunaCompat.warnOnce("social:requests", t);
				return List.of();
			});
	}

	/** 들어온 요청 수락. 성공하면 null. */
	public static CompletableFuture<String> acceptRequest(String rowId) {
		load();
		if (!signedIn() || rowId == null || rowId.isEmpty()) {
			return CompletableFuture.completedFuture("수락할 수 없습니다");
		}
		JsonObject patch = new JsonObject();
		patch.addProperty("status", "accepted");
		return restSend("PATCH", "/friends?id=eq." + enc(rowId), patch.toString())
			.thenApply(x -> (String) null)
			.exceptionally(t -> {
				LunaCompat.warnOnce("social:accept", t);
				return "수락하지 못했습니다";
			});
	}

	/** 수락된 친구 목록(온라인 순). 로그인 안 됐으면 빈 목록. */
	public static CompletableFuture<List<Friend>> fetchFriends() {
		load();
		if (!signedIn()) {
			return CompletableFuture.completedFuture(List.of());
		}
		String me = siteId;
		return rest("/friends?or=(requester_uuid.eq." + enc(me) + ",addressee_uuid.eq." + enc(me)
				+ ")&status=eq.accepted&select=id,requester_uuid,addressee_uuid,requester_name,addressee_name")
			.thenCompose(rows -> {
				List<String[]> basic = new ArrayList<>(); // id, otherId, otherName
				if (rows != null && rows.isJsonArray()) {
					for (JsonElement e : rows.getAsJsonArray()) {
						if (!e.isJsonObject()) {
							continue;
						}
						JsonObject r = e.getAsJsonObject();
						boolean meRequester = sameId(str(r, "requester_uuid"), me);
						String otherId = meRequester ? str(r, "addressee_uuid") : str(r, "requester_uuid");
						String otherName = meRequester ? str(r, "addressee_name") : str(r, "requester_name");
						basic.add(new String[]{str(r, "id"), otherId, otherName == null ? "?" : otherName});
					}
				}
				if (basic.isEmpty()) {
					return CompletableFuture.completedFuture(List.<Friend>of());
				}
				StringBuilder in = new StringBuilder();
				for (String[] b : basic) {
					if (in.length() > 0) {
						in.append(',');
					}
					in.append(b[1]);
				}
				// 49-27차: 서버 주소·마크 닉네임 컬럼이 있으면 같이 받으려고 select=*(컬럼이 없어도 400이 안 남)
				String idCol = presenceIdCol;
				return (idCol == null || idCol.isEmpty()
						? CompletableFuture.completedFuture((JsonElement) null)
						: rest("/site_presence?" + idCol + "=in.(" + enc(in.toString()) + ")&select=*"))
					.exceptionally(t -> null)
					.thenApply(pres -> {
						Map<String, String[]> presence = new HashMap<>(); // id -> {presence, status, server, serverName, mcName}
						long now = System.currentTimeMillis();
						if (pres != null && pres.isJsonArray()) {
							for (JsonElement e : pres.getAsJsonArray()) {
								if (!e.isJsonObject()) {
									continue;
								}
								JsonObject p = e.getAsJsonObject();
								String id = str(p, idCol);
								long updated = parseIso(str(p, "updated_at"));
								long elapsed = now - updated;
								String state = elapsed < ONLINE_WINDOW_MS ? "online" : elapsed < AWAY_WINDOW_MS ? "away" : "offline";
								String status = state.equals("offline") ? "" : (str(p, "status_text") == null ? "" : str(p, "status_text"));
								String server = state.equals("offline") ? "" : firstNonEmpty(str(p, "server_address"), str(p, "server"));
								String serverName = firstNonEmpty(str(p, "server_name"), str(p, "world_name"));
								String mcName = firstNonEmpty(str(p, "mc_name"), str(p, "minecraft_name"), str(p, "player_name"));
								presence.put(normalize(id), new String[]{state, status, server, serverName, mcName});
							}
						}
						List<Friend> out = new ArrayList<>();
						for (String[] b : basic) {
							String[] ps = presence.get(normalize(b[1]));
							out.add(new Friend(b[0], b[1], b[2],
									ps == null ? "offline" : ps[0], ps == null ? "" : ps[1],
									ps == null ? "" : ps[2], ps == null ? "" : ps[3], ps == null ? "" : ps[4]));
						}
						out.sort((a, c) -> Integer.compare(rank(a.presence()), rank(c.presence())));
						return out;
					});
			});
	}

	/** "#RRGGBB" / "RRGGBB" / "0xAARRGGBB" → ARGB(못 읽으면 0). */
	private static int parseColor(String raw) {
		if (raw == null) {
			return 0;
		}
		String t = raw.trim().replace("#", "").replace("0x", "").replace("0X", "");
		if (t.length() != 6 && t.length() != 8) {
			return 0;
		}
		try {
			long v = Long.parseLong(t, 16);
			return t.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static String firstNonEmpty(String... values) {
		for (String v : values) {
			if (v != null && !v.isEmpty()) {
				return v;
			}
		}
		return "";
	}

	private static int rank(String presence) {
		return switch (presence) {
			case "online" -> 0;
			case "away" -> 1;
			default -> 2;
		};
	}

	private static String normalize(String id) {
		return id == null ? "" : id.replace("-", "").toLowerCase();
	}

	private static boolean sameId(String a, String b) {
		return a != null && b != null && normalize(a).equals(normalize(b));
	}

	private static long parseIso(String s) {
		if (s == null) {
			return 0L;
		}
		try {
			return java.time.OffsetDateTime.parse(s.replace(' ', 'T')).toInstant().toEpochMilli();
		} catch (Throwable ignored) {
			try {
				return java.time.Instant.parse(s).toEpochMilli();
			} catch (Throwable ignored2) {
				return 0L;
			}
		}
	}

	// ---------------------------------------------------------------- 내 접속 정보 알리기(49-27차)
	// 모드는 Supabase에 직접 쓰지 않는다(anon 키로는 RLS에 막힘). 대신 게임 폴더에 .luna-presence.json을
	// 남기고, 같이 떠 있는 런처가 그걸 읽어 site_presence(server_address/server_name/mc_name)에 올린다.
	// 런처가 아직 그 기능을 안 붙였어도 파일만 남을 뿐 게임에는 아무 영향이 없다.

	/**
	 * 49-44차: 런처 상점에서 장착한 치장품 이름(색 상품 / 테마 상품). 프로필 화면이 "아직 준비 중"
	 * 대신 실제로 장착한 것을 보여 주는 데 쓴다. 장착한 게 없으면 null - 없는 걸 지어내지 않는다.
	 */
	public static volatile String cosmeticColorName;
	public static volatile String cosmeticThemeName;

	private static long lastPresenceNanos;
	private static String lastPresenceServer = "\u0000";
	private static boolean lastPresenceAfk;

	public static void publishPresence(Minecraft client) {
		if (client == null || !available()) {
			return;
		}
		String server = client.level == null ? "" : serverAddress(client);
		long now = System.nanoTime();
		// 49-201차: 자리 비움(afk)도 올린다 - 같은 서버의 루나 유저가 탭리스트와 머리 위에 Zzz로 본다. 바뀌면 바로 쓴다.
		boolean afk = kr.lunaslight.mod.module.impl.render.AfkModule.isAfkNow();
		boolean changed = !server.equals(lastPresenceServer) || afk != lastPresenceAfk;
		if (!changed && now - lastPresenceNanos < 20_000_000_000L) {
			return; // 20초마다 한 번(또는 서버나 자리 비움이 바뀔 때 즉시)
		}
		lastPresenceNanos = now;
		lastPresenceServer = server;
		lastPresenceAfk = afk;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("ts", System.currentTimeMillis());
			o.addProperty("accountId", siteId == null ? "" : siteId);
			o.addProperty("mcName", LunaCompat.sessionName(client));
			o.addProperty("mcUuid", currentUuid(client));
			o.addProperty("server", server);
			o.addProperty("serverName", client.level == null ? "" : String.valueOf(LunaCompat.currentServerLabel(client)));
			o.addProperty("singleplayer", LunaCompat.isSinglePlayer(client));
			o.addProperty("afk", afk);
			Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".luna-presence.json");
			Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("presence:write", t);
		}
	}

	// ==================== 49-124차: 탭 목록 루나 유저 표시(페더식 배지) ====================
	// 같은 서버에 있는, 루나를 쓰는(런처가 site_presence에 올린) 플레이어의 마크 닉네임을 모아 둔다.
	// PlayerListHudMixin이 이 목록을 보고 핑 앞에 루나 별을 그린다. anon 키 읽기라 로그인 없이도 동작하지만,
	// site_presence 읽기 권한(RLS)이 열려 있어야 남의 행까지 보인다(친구 목록도 같은 표를 읽으므로 대개 열려 있다).
	private static volatile java.util.Set<String> serverLunaNames = java.util.Collections.emptySet();
	/** 49-201차: 그중 자리 비움인 사람(site_presence.game_afk). */
	private static volatile java.util.Set<String> serverAfkNames = java.util.Collections.emptySet();
	private static long lastBadgePollMs;

	/** 49-201차: 이 마크 닉네임이 지금 서버에서 자리 비움인 루나 유저인지(소문자 비교). */
	public static boolean isAfkLunaPlayer(String mcName) {
		return mcName != null && !serverAfkNames.isEmpty()
				&& serverAfkNames.contains(mcName.toLowerCase(java.util.Locale.ROOT));
	}

	/** 49-209차: 나 자신(이 게임의 마크 닉네임, 소문자). 서버에 있을 때만. 내 별은 런처 업로드를 기다리지 않는다. */
	private static volatile String selfLunaName;

	/** 이 마크 닉네임이 지금 서버에서 루나를 쓰는 사람인지(소문자 비교). */
	public static boolean isLunaPlayerOnServer(String mcName) {
		if (mcName == null) {
			return false;
		}
		String lower = mcName.toLowerCase(java.util.Locale.ROOT);
		return lower.equals(selfLunaName) || (!serverLunaNames.isEmpty() && serverLunaNames.contains(lower));
	}

	/**
	 * 49-209차: 글자 안에 낱말로 들어 있는 루나 유저 닉네임(나 포함). 없으면 null. 앞뒤가 영문/숫자/_가 아니어야 하고
	 * (한글 조사·칭호는 붙어도 된다), 바로 앞이 § 색 코드면 통과. 탭 꾸미기 플러그인의 가짜 칸에서 진짜 이름을 찾는 데 쓴다.
	 */
	public static String lunaNameIn(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		java.util.Set<String> names = serverLunaNames;
		String me = selfLunaName;
		if (names.isEmpty() && me == null) {
			return null;
		}
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		if (me != null && hasWord(lower, me)) {
			return me;
		}
		for (String n : names) {
			if (hasWord(lower, n)) {
				return n;
			}
		}
		return null;
	}

	private static boolean hasWord(String lower, String name) {
		if (name.length() < 3) {
			return false;
		}
		for (int i = lower.indexOf(name); i >= 0; i = lower.indexOf(name, i + 1)) {
			boolean okL = i == 0 || !nameChar(lower.charAt(i - 1)) || (i >= 2 && lower.charAt(i - 2) == '\u00a7');
			int e = i + name.length();
			boolean okR = e >= lower.length() || !nameChar(lower.charAt(e));
			if (okL && okR) {
				return true;
			}
		}
		return false;
	}

	private static boolean nameChar(char c) {
		return c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z');
	}

	/** 15초마다 site_presence에서 같은 서버·최근 접속한 루나 유저의 닉네임을 모은다. */
	public static void pollServerBadges(Minecraft client) {
		if (client == null || client.level == null) {
			serverLunaNames = java.util.Collections.emptySet();
			serverAfkNames = java.util.Collections.emptySet();
			selfLunaName = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBadgePollMs < 15_000L) {
			return;
		}
		lastBadgePollMs = now;
		String myServer = serverAddress(client);
		if (myServer == null || myServer.isEmpty()) {   // 싱글이면 배지 없음
			serverLunaNames = java.util.Collections.emptySet();
			serverAfkNames = java.util.Collections.emptySet();
			selfLunaName = null;
			return;
		}
		try {
			String me = LunaCompat.sessionName(client);
			selfLunaName = me == null || me.isEmpty() ? null : me.toLowerCase(java.util.Locale.ROOT);
		} catch (Throwable ignored) {
		}
		if (!available()) {   // 사이트 정보를 못 받았으면 남의 별은 모른다(내 별만)
			serverLunaNames = java.util.Collections.emptySet();
			serverAfkNames = java.util.Collections.emptySet();
			return;
		}
		String sinceIso = java.time.Instant.ofEpochMilli(now - AWAY_WINDOW_MS).toString();
		rest("/site_presence?updated_at=gte." + enc(sinceIso) + "&select=*")
				.exceptionally(t -> null)
				.thenAccept(arr -> {
					if (arr == null || !arr.isJsonArray()) {
						return;
					}
					java.util.Set<String> names = new java.util.HashSet<>();
					java.util.Set<String> afkNames = new java.util.HashSet<>();
					long t = System.currentTimeMillis();
					for (JsonElement e : arr.getAsJsonArray()) {
						if (!e.isJsonObject()) {
							continue;
						}
						JsonObject p = e.getAsJsonObject();
						if (t - parseIso(str(p, "updated_at")) >= AWAY_WINDOW_MS) {
							continue;   // 너무 오래된 접속(오프라인)
						}
						// 49-209차(사용자: "클라들끼리 별 뜨는 게 안 보이는 경우가 있어"): 예전엔 서버 주소 글자가 똑같아야 했다 -
						// 같은 서버라도 사람마다 친 주소가 다르면(대소문자 말고도 :25565, play. 접두, IP) 별이 안 떴다.
						// 탭 목록에 있는 사람만 별을 받으니 "지금 어느 서버든 게임 중인 루나 유저 이름"이면 충분하다.
						String server = firstNonEmpty(str(p, "server_address"), str(p, "server"));
						if (server.isEmpty()) {
							continue;   // 싱글이거나 게임 밖
						}
						String mc = firstNonEmpty(str(p, "mc_name"), str(p, "minecraft_name"), str(p, "player_name"));
						if (!mc.isEmpty()) {
							names.add(mc.toLowerCase(java.util.Locale.ROOT));
							try {
								if (p.has("game_afk") && !p.get("game_afk").isJsonNull() && p.get("game_afk").getAsBoolean()) {
									afkNames.add(mc.toLowerCase(java.util.Locale.ROOT));
								}
							} catch (Throwable ignored) {
							}
						}
					}
					serverLunaNames = names;
					serverAfkNames = afkNames;
				});
	}

	/** 접속 중인 서버 주소(싱글이면 빈 문자열 - 친구가 참가할 수 없음). */
	public static String serverAddress(Minecraft client) {
		if (LunaCompat.isSinglePlayer(client)) {
			return "";
		}
		try {
			Object entry = LunaCompat.invokeNoArg(client, "getCurrentServerEntry");
			if (entry != null) {
				Object addr = LunaCompat.findField(entry.getClass(), "address").get(entry);
				if (addr instanceof String str) {
					return str;
				}
			}
		} catch (Throwable ignored) {
		}
		return "";
	}

	/** 친구가 있는 서버로 접속. 실패하면 주소를 클립보드에 복사하고 false. */
	public static boolean join(Minecraft client, Friend friend) {
		if (friend == null || !friend.canJoin()) {
			return false;
		}
		if (LunaCompat.joinServer(client, friend.server(), friend.place())) {
			return true;
		}
		LunaCompat.copyToClipboard(client, friend.server());
		return false;
	}

	/** 친구에게 귓속말(채팅창을 /msg 닉네임 으로 열어 둠). 게임 안에서만. */
	public static boolean whisper(Minecraft client, Friend friend) {
		if (client == null || client.level == null || friend == null) {
			return false;
		}
		return LunaCompat.openChatWith(client, "/msg " + friend.whisperName() + " ");
	}

	// ---------------------------------------------------------------- 프로필(계정) 전환

	/** 현재 게임 세션의 uuid(대시 없음). */
	public static String currentUuid(Minecraft client) {
		try {
			Object session = LunaCompat.invokeNoArg(client, "getSession");
			if (session != null) {
				Object u = LunaCompat.invokeNoArg(session, "getUuidOrNull");
				if (u == null) {
					u = LunaCompat.invokeNoArg(session, "getUuid");
				}
				if (u != null) {
					return normalize(u.toString());
				}
			}
		} catch (Throwable ignored) {
		}
		return activeUuid == null ? "" : normalize(activeUuid);
	}

	/** 대시 없는 uuid → 대시 있는 표준형(복사해서 붙여 넣을 때는 이쪽이 쓸모 있다). 길이가 안 맞으면 그대로. */
	public static String dashed(String undashed) {
		String s = normalize(undashed);
		if (s.length() != 32) {
			return undashed;
		}
		return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
			+ s.substring(16, 20) + "-" + s.substring(20);
	}

	private static UUID toUuid(String undashed) {
		String s = normalize(undashed);
		if (s.length() != 32) {
			return UUID.fromString(undashed);
		}
		return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
				+ s.substring(16, 20) + "-" + s.substring(20));
	}

	/**
	 * 게임 세션을 다른 등록 계정으로 교체. 성공하면 null, 실패하면 이유 문자열.
	 *  Session 생성자: (String,String,String,String) 1.16 / (String,String,String,Optional,Optional,AccountType) 1.19~1.20.1 /
	 *  (String,UUID,String,Optional,Optional,AccountType) 1.20.2~1.21.10 / (String,UUID,String,Optional,Optional) 1.21.11
	 */
	public static String switchAccount(Minecraft client, Account account) {
		if (client == null || account == null) {
			return "계정 정보가 없습니다";
		}
		if (client.level != null) {
			return "서버나 월드에서 나온 뒤에 바꿀 수 있습니다";
		}
		try {
			Class<?> sessionClass = LunaCompat.classOrNull("net.minecraft.client.User");
			if (sessionClass == null) {
				sessionClass = LunaCompat.classForName("net.minecraft.client.util.Session"); // ≤1.20.1
			}
			Object newSession = buildSession(sessionClass, account);
			if (newSession == null) {
				return "이 버전에서는 세션을 만들 수 없습니다";
			}
			Field sessionField = LunaCompat.findField(Minecraft.class, "session");
			if (sessionField == null) {
				return "세션 정보를 찾지 못했습니다";
			}
			sessionField.setAccessible(true);
			sessionField.set(client, newSession);
			activeUuid = account.uuid();

			// 1.19+: 채팅 서명/사용자 API 서비스도 새 토큰으로(안 되면 그냥 넘어감 - 접속 자체는 세션만으로 됨)
			try {
				Field authField = LunaCompat.findField(Minecraft.class, "authenticationService");
				Field userApiField = LunaCompat.findField(Minecraft.class, "userApiService");
				if (authField != null && userApiField != null) {
					Object auth = authField.get(client);
					Method create = auth == null ? null : LunaCompat.findMethod(auth.getClass(), "createUserApiService", String.class);
					if (create != null) {
						Object userApi = create.invoke(auth, account.accessToken());
						if (userApi != null) {
							userApiField.setAccessible(true);
							userApiField.set(client, userApi);
							Field keysField = LunaCompat.findField(Minecraft.class, "profileKeys");
							Class<?> keysClass = LunaCompat.classOrNull("net.minecraft.client.multiplayer.ProfileKeyPairManager");
							if (keysClass == null) {
								keysClass = LunaCompat.classOrNull("net.minecraft.client.util.ProfileKeys"); // 1.20.1
							}
							if (keysField != null && keysClass != null) {
								for (Method m : keysClass.getMethods()) {
									if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 3
											&& LunaCompat.nameMatches(keysClass, "create", m.getName())) {
										Object keys = m.invoke(null, userApi, newSession, client.gameDirectory.toPath().resolve("profilekeys"));
										keysField.setAccessible(true);
										keysField.set(client, keys);
										break;
									}
								}
							}
						}
					}
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("social:userApi", t);
			}
			LunaClientMod.LOGGER.info("[Nova] 프로필 전환: " + account.name());
			return null;
		} catch (Throwable t) {
			LunaClientMod.LOGGER.warn("[Nova] 프로필 전환 실패", t);
			return "전환하지 못했습니다: " + t.getClass().getSimpleName();
		}
	}

	private static Object buildSession(Class<?> sessionClass, Account a) throws Exception {
		for (Constructor<?> c : sessionClass.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length == 4 && p[0] == String.class && p[1] == String.class && p[2] == String.class && p[3] == String.class) {
				return c.newInstance(a.name(), normalize(a.uuid()), a.accessToken(), "msa");
			}
		}
		for (Constructor<?> c : sessionClass.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length == 6 && p[0] == String.class && p[2] == String.class && p[3] == Optional.class && p[4] == Optional.class) {
				Object type = msaType(p[5]);
				Object uuidArg = p[1] == UUID.class ? toUuid(a.uuid()) : normalize(a.uuid());
				return c.newInstance(a.name(), uuidArg, a.accessToken(), Optional.empty(), Optional.empty(), type);
			}
		}
		for (Constructor<?> c : sessionClass.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length == 5 && p[0] == String.class && p[2] == String.class && p[3] == Optional.class && p[4] == Optional.class) {
				Object uuidArg = p[1] == UUID.class ? toUuid(a.uuid()) : normalize(a.uuid());
				return c.newInstance(a.name(), uuidArg, a.accessToken(), Optional.empty(), Optional.empty());
			}
		}
		return null;
	}

	/** Session.AccountType.MSA(enum). */
	private static Object msaType(Class<?> enumClass) {
		if (!enumClass.isEnum()) {
			return null;
		}
		for (Object v : enumClass.getEnumConstants()) {
			if (((Enum<?>) v).name().equals("MSA")) {
				return v;
			}
		}
		Object[] values = enumClass.getEnumConstants();
		return values.length > 0 ? values[values.length - 1] : null;
	}
}
