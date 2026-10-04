package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 49-27차: 세부 통계(사용자: "내가 마크 한 서버나 맵 기록 그리고 전체 마크 기록이 쌓이는 것").
 *
 * 전부 **내 컴퓨터에만** 쌓이고(config/lunaslight/stats/), 서버로 아무것도 보내지 않는다.
 * 범위는 전체(global)와 서버·맵별(worlds/&lt;키&gt;.json) 두 가지이며, 게임 중에는 두 곳에 동시에 더한다.
 *
 * 모으는 것:
 *  · 플레이타임 · AFK 시간(3분 이상 안 움직이면) · 세션 수 · 첫/마지막 플레이
 *  · 이동 거리와 시간(걷기 · 달리기 · 웅크림 · 수영 · 비행 · 겉날개 · 탈것)
 *  · 점프 · 웅크리기 시간 · 잃은 체력 · 최고/최저 높이
 *  · 죽은 횟수(사망 화면의 문구를 원인으로) · 처치 수(내가 때린 뒤 죽은 생명체의 종류별)
 *  · 아이템별 기록: 사용 · 버림 · 제작 · 캔 횟수. 인챈트나 이름이 다르면 **변형**으로 따로 센다
 *    (예: "다이아몬드 곡괭이 · 효율 V 내구성 III" / 서버 전용으로 꾸며진 곡괭이).
 *  · 주민 거래 · 상자 연 횟수 · 채팅 보낸 수
 *  · 마인크래프트 기본 통계(제작 · 주움 등)는 있으면 같이 읽어 보탠다.
 *
 * 저장은 30초마다(변경이 있을 때만) + 게임 종료 시. 백업/불러오기는 LunaStatsScreen에서.
 */
public final class LunaStats {
	private LunaStats() {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	// ==================== 자료 구조 ====================

	/** 아이템 하나(또는 변형 하나)의 기록. */
	public static final class ItemStat {
		public long used, dropped, crafted, mined, picked;
		public String name;   // 표시 이름(변형이면 그 이름)
		public String base;   // 기본 아이템 id

		public long total() {
			return used + dropped + crafted + mined + picked;
		}
	}

	/** 범위(전체 또는 서버/맵) 하나의 기록. */
	public static final class Scope {
		public String key = "global";
		public String label = "전체";
		/** 49-32차: 싱글플레이면 saves/ 아래 폴더 이름(서버는 빈 값). 맵을 구분하는 열쇠. */
		public String folder = "";
		/** 49-32차: 싱글 맵인데 세이브 폴더가 사라졌으면 true → 목록에 "이름(삭제됨)". */
		public boolean missing;
		public long playMs, afkMs, sneakMs;
		public long sessions, jumps, deaths, kills, trades, chestsOpened, chatSent;
		public double damageTaken;      // 잃은 체력(하트 반칸 = 1)
		public long firstSeen, lastSeen;
		// 49-142차: 통계 확장(사용자가 고른 전투, 채굴/농사/낚시, 플레이 습관, 채팅/소셜/서버)
		public long hits, playerKills, playerDeaths, bestLifeMs, lifeMs, longestSessionMs, chatReceived, mentions;
		/** 요일(월 = 0) × 시(0~23) 플레이 ms. 자리 비움은 뺀다. */
		public final long[] hourMs = new long[168];
		/** 날짜("2026-09-26") → 그날 플레이 ms. 연속 접속일 계산용. */
		public final Map<String, Long> days = new LinkedHashMap<>();
		/** 탭 목록에 함께 있던 플레이어 → 함께 있던 ms. */
		public final Map<String, Long> peers = new LinkedHashMap<>();
		public final Map<String, Long> deathCauses = new LinkedHashMap<>();
		public final Map<String, Long> killTypes = new LinkedHashMap<>();
		public final Map<String, Double> distance = new LinkedHashMap<>();  // 모드 → m
		public final Map<String, Long> travelMs = new LinkedHashMap<>();    // 모드 → ms
		public final Map<String, ItemStat> items = new LinkedHashMap<>();
		/** 49-28차: 상호작용(바닐라 CUSTOM 통계에서 - 상자 열기 · 제작대 · 화로 · 낚시 · 번식 …). */
		public final Map<String, Long> interactions = new LinkedHashMap<>();
		/** 49-28차: 달별 기록("2026-09" → 요약값). 연도는 이걸 합쳐서 만든다. */
		public final Map<String, Period> months = new LinkedHashMap<>();

		public Period month(String key) {
			return months.computeIfAbsent(key, k -> new Period());
		}

		/** 지금 달의 기록. */
		public Period thisMonth() {
			return month(monthKey(System.currentTimeMillis()));
		}

		public ItemStat item(String key) {
			return items.computeIfAbsent(key, k -> new ItemStat());
		}

		public double totalDistance() {
			double d = 0;
			for (double v : distance.values()) {
				d += v;
			}
			return d;
		}

		/** 49-32차: 목록에 보여줄 이름. 지워진 싱글 맵이면 "이름(삭제됨)". */
		public String displayLabel() {
			return missing ? label + "(삭제됨)" : label;
		}
	}

	/** 49-28차: 한 달(또는 한 해)치 요약. 무거워지지 않게 굵직한 값만 담는다. */
	public static final class Period {
		public long playMs, afkMs, deaths, kills, jumps, trades, chests, mined, crafted, interactions;
		public double distance, damageTaken;

		public void add(Period o) {
			playMs += o.playMs;
			afkMs += o.afkMs;
			deaths += o.deaths;
			kills += o.kills;
			jumps += o.jumps;
			trades += o.trades;
			chests += o.chests;
			mined += o.mined;
			crafted += o.crafted;
			interactions += o.interactions;
			distance += o.distance;
			damageTaken += o.damageTaken;
		}
	}

	/** "2026-09" 형태의 달 키. */
	public static String monthKey(long ms) {
		java.time.LocalDate d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
		return String.format(Locale.ROOT, "%04d-%02d", d.getYear(), d.getMonthValue());
	}

	public static String yearOf(String monthKey) {
		return monthKey.length() >= 4 ? monthKey.substring(0, 4) : monthKey;
	}

	/** 이동 방식. */
	public static final String[] MODES = {"walk", "sprint", "sneak", "swim", "fly", "elytra", "vehicle"};

	public static String modeName(String mode) {
		return switch (mode) {
			case "walk" -> "걷기";
			case "sprint" -> "달리기";
			case "sneak" -> "웅크려 이동";
			case "swim" -> "헤엄치기";
			case "fly" -> "비행";
			case "elytra" -> "겉날개";
			case "vehicle" -> "탈것";
			default -> mode;
		};
	}

	// ==================== 상태 ====================

	private static final Scope GLOBAL = new Scope();
	private static Scope world;
	private static String worldKey;
	private static boolean globalLoaded;
	private static boolean dirty;
	private static long lastSaveNanos;

	// 틱 추적용
	private static double lastX, lastY, lastZ;
	private static boolean hasLastPos;
	private static float lastHealth = -1;
	private static boolean wasOnGround = true;
	private static boolean wasDead;
	private static long lastActivityMs;
	private static float lastYaw, lastPitch;
	private static final Map<Integer, Long> ENGAGED = new LinkedHashMap<>(); // 내가 때린 엔티티 id → 시각
	private static final Map<Integer, String> ENGAGED_TYPE = new LinkedHashMap<>();
	private static Object lastScreen;
	private static long sessionStartMs;
	private static ItemStack lastMainHand = ItemStack.EMPTY;
	private static int lastHeldCount = -1;

	private static final long AFK_AFTER_MS = 180_000L;

	// ==================== 파일 ====================

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("stats");
	}

	private static Path globalFile() {
		return dir().resolve("global.json");
	}

	private static Path worldFile(String key) {
		return dir().resolve("worlds").resolve(key + ".json");
	}

	/**
	 * 49-32차: 서버는 주소, 싱글은 세이브 폴더로 파일 이름을 만든다.
	 * 예전에는 한글 이름이 전부 "_"로 뭉개져서 맵끼리 기록이 섞였다 → 뒤에 짧은 지문을 붙여 구분.
	 */
	private static String keyOf(Minecraft client) {
		String source = sourceOf(client);
		if (source == null) {
			return null;
		}
		String base = sanitizeKey(source.substring(3));
		return (base.isEmpty() ? "w" : base) + "-" + fingerprint(source);
	}

	/** "sp:&lt;폴더&gt;" 또는 "mp:&lt;주소&gt;". 어느 쪽도 아니면 null. */
	private static String sourceOf(Minecraft client) {
		String folder = LunaCompat.currentWorldFolder(client);
		if (folder != null && !folder.isEmpty()) {
			return "sp:" + folder;
		}
		String label = LunaCompat.currentServerLabel(client);
		if (label == null || label.isEmpty()) {
			return null;
		}
		return "mp:" + label;
	}

	/** 49-31차 이전 파일 이름(월드 이름을 그대로 뭉갠 것). 기록 옮겨오기용. */
	private static String legacyKeyOf(Minecraft client) {
		String label = LunaCompat.currentServerLabel(client);
		if (label == null || label.isEmpty()) {
			return null;
		}
		return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static String sanitizeKey(String s) {
		String t = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_")
				.replaceAll("_+", "_").replaceAll("^_|_$", "");
		return t.length() > 24 ? t.substring(0, 24) : t;
	}

	/** 이름이 겹쳐도 파일이 안 겹치게 붙이는 짧은 지문. */
	private static String fingerprint(String s) {
		long h = 1125899906842597L;
		for (int i = 0; i < s.length(); i++) {
			h = 31 * h + s.charAt(i);
		}
		return String.format(Locale.ROOT, "%08x", (int) (h ^ (h >>> 32)));
	}

	/** 예전 방식으로 저장돼 있던 기록을 새 파일로 옮긴다(맵 하나일 때만 안전하게). */
	private static void migrateLegacy(Minecraft client, Scope into) {
		try {
			String old = legacyKeyOf(client);
			if (old == null || old.equals(into.key)) {
				return;
			}
			Path f = worldFile(old);
			if (!Files.exists(f)) {
				return;
			}
			Scope prev = loadScope(f, into.key, into.label);
			String label = into.label;
			copyInto(prev, into);
			into.key = worldKey;
			into.label = label;
			Files.deleteIfExists(f);
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:migrate", t);
		}
	}

	// ==================== 수집 ====================

	/** 매 클라이언트 틱(LunaClientMod에서 호출). */
	public static void tick(Minecraft client) {
		try {
			ensureGlobal();
			if (client == null || client.player == null || client.level == null) {
				if (world != null) {
					saveNow();
					world = null;
					worldKey = null;
					hasLastPos = false;
					lastHealth = -1;
				}
				return;
			}
			String key = keyOf(client);
			if (key != null && !key.equals(worldKey)) {
				saveNow();
				worldKey = key;
				String label = LunaCompat.currentServerLabel(client);
				boolean isNew = !Files.exists(worldFile(key));
				world = loadScope(worldFile(key), key, label);
				if (isNew) {
					migrateLegacy(client, world);
				}
				String folder = LunaCompat.currentWorldFolder(client);
				world.folder = folder == null ? "" : folder;
				if (label != null && !label.isEmpty()) {
					world.label = label;   // 맵 이름을 바꿨으면 따라간다
				}
				world.missing = false;
				world.sessions++;
				GLOBAL.sessions++;
				sessionStartMs = System.currentTimeMillis();
				hasLastPos = false;
				lastHealth = -1;
				markActivity();
			}
			long now = System.currentTimeMillis();
			each(s -> {
				if (s.firstSeen == 0) {
					s.firstSeen = now;
				}
				s.lastSeen = now;
			});

			// ---- 플레이타임 / AFK ----
			boolean afk = now - lastActivityMs > AFK_AFTER_MS;
			each(s -> {
				s.playMs += 50;
				Period m = s.thisMonth();
				m.playMs += 50;
				if (afk) {
					s.afkMs += 50;
					m.afkMs += 50;
				}
			});

			// 49-142차: 습관(요일 × 시, 날짜별) + 가장 긴 접속 + 함께 있던 사람
			if (!afk) {
				java.time.LocalDateTime lt = java.time.LocalDateTime.now();
				int slot = (lt.getDayOfWeek().getValue() - 1) * 24 + lt.getHour();
				String day = lt.toLocalDate().toString();
				each(s -> {
					s.hourMs[slot] += 50;
					s.days.merge(day, 50L, Long::sum);
				});
			}
			long sess = now - sessionStartMs;
			each(s -> {
				if (sess > s.longestSessionMs) {
					s.longestSessionMs = sess;
				}
			});
			hookChat();
			if (++peerTick >= 100) {
				peerTick = 0;
				trackPeers(client);
			}

			trackMovement(client, afk);
			trackHealthAndDeath(client);
			trackJumpSneak(client);
			trackKills(client);
			trackScreens(client);
			trackHeldItem(client);
			syncVanilla(client);

			dirty = true;
			long nanos = System.nanoTime();
			if (nanos - lastSaveNanos > 30_000_000_000L) {
				lastSaveNanos = nanos;
				saveNow();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:tick", t);
		}
	}

	private static int peerTick;
	private static boolean chatHooked;
	private static String myName;
	private static boolean wasAttacking;

	/** 5초마다: 탭 목록에 같이 있는 사람들에게 5초씩(내 이름 빼고). 너무 커지지 않게 400명까지만 새로 받는다. */
	private static void trackPeers(Minecraft client) {
		try {
			myName = LunaCompat.sessionName(client);
			List<String> names = LunaCompat.playerListNames(client);
			if (names.size() > 200) {
				return;   // 큰 서버 로비는 "같이 논 사람"이 아니다
			}
			for (String n : names) {
				if (n == null || n.isEmpty() || n.equals(myName)) {
					continue;
				}
				each(s -> {
					if (s.peers.containsKey(n) || s.peers.size() < 400) {
						s.peers.merge(n, 5000L, Long::sum);
					}
				});
			}
		} catch (Throwable ignored) {
		}
	}

	private static void hookChat() {
		if (chatHooked) {
			return;
		}
		chatHooked = true;
		try {
			ChatState.addPlainListener(LunaStats::onChatReceived);
		} catch (Throwable ignored) {
		}
	}

	/** 받은 채팅 한 줄(색 코드 뺀 글자). 내 이름이 들어 있고 내가 보낸 줄이 아니면 "불림"으로 센다. */
	public static void onChatReceived(String plain) {
		if (plain == null || plain.isEmpty()) {
			return;
		}
		String me = myName;
		boolean mine = me != null && !me.isEmpty() && (plain.startsWith("<" + me + ">") || plain.startsWith(me + ":")
			|| plain.contains(" " + me + ":") || plain.contains(me + " :") || plain.contains(me + " »") || plain.contains(me + " >"));
		if (mine) {
			return;
		}
		boolean mention = me != null && !me.isEmpty() && plain.contains(me);
		each(s -> {
			s.chatReceived++;
			if (mention) {
				s.mentions++;
			}
		});
		dirty = true;
	}

	private static void markActivity() {
		lastActivityMs = System.currentTimeMillis();
	}

	private interface ScopeAction {
		void run(Scope s);
	}

	private static void each(ScopeAction action) {
		action.run(GLOBAL);
		if (world != null) {
			action.run(world);
		}
	}

	// ---- 이동 ----

	private static void trackMovement(Minecraft client, boolean afk) {
		double x = client.player.getX();
		double y = client.player.getY();
		double z = client.player.getZ();
		float yaw = LunaCompat.getYaw(client.player);
		float pitch = LunaCompat.getPitch(client.player);
		if (!hasLastPos) {
			hasLastPos = true;
			lastX = x;
			lastY = y;
			lastZ = z;
			lastYaw = yaw;
			lastPitch = pitch;
			return;
		}
		double dx = x - lastX;
		double dz = z - lastZ;
		double dy = y - lastY;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		boolean moved = horizontal > 0.001 || Math.abs(dy) > 0.001;
		boolean looked = Math.abs(yaw - lastYaw) > 0.5f || Math.abs(pitch - lastPitch) > 0.5f;
		if (moved || looked) {
			markActivity();
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		lastYaw = yaw;
		lastPitch = pitch;

		if (horizontal < 0.0005 && Math.abs(dy) < 0.0005) {
			return;
		}
		String mode = movementMode(client);
		double dist = mode.equals("elytra") || mode.equals("fly")
				? Math.sqrt(dx * dx + dy * dy + dz * dz) : horizontal;
		each(s -> {
			s.distance.merge(mode, dist, Double::sum);
			s.travelMs.merge(mode, 50L, Long::sum);
			s.thisMonth().distance += dist;
		});
	}

	private static String movementMode(Minecraft client) {
		try {
			if (client.player.isPassenger()) {
				return "vehicle";
			}
			Object flying = LunaCompat.invokeNoArg(client.player, "isFallFlying");
			if (flying instanceof Boolean b && b) {
				return "elytra";
			}
			Object abilities = LunaCompat.invokeNoArg(client.player, "getAbilities");
			if (abilities != null) {
				java.lang.reflect.Field f = LunaCompat.findField(abilities.getClass(), "flying");
				if (f != null && Boolean.TRUE.equals(f.get(abilities))) {
					return "fly";
				}
			}
			Object swimming = LunaCompat.invokeNoArg(client.player, "isSwimming");
			if (swimming instanceof Boolean b && b) {
				return "swim";
			}
			Object touching = LunaCompat.invokeNoArg(client.player, "isTouchingWater");
			if (touching instanceof Boolean b && b) {
				return "swim";
			}
			if (client.player.isShiftKeyDown()) {
				return "sneak";
			}
			Object sprint = LunaCompat.invokeNoArg(client.player, "isSprinting");
			if (sprint instanceof Boolean b && b) {
				return "sprint";
			}
		} catch (Throwable ignored) {
		}
		return "walk";
	}

	// ---- 체력 / 죽음 ----

	private static void trackHealthAndDeath(Minecraft client) {
		float health = client.player.getHealth();
		if (lastHealth >= 0 && health < lastHealth) {
			double lost = lastHealth - health;
			each(s -> {
				s.damageTaken += lost;
				s.thisMonth().damageTaken += lost;
			});
		}
		lastHealth = health;

		boolean dead = health <= 0f;
		if (dead && !wasDead) {
			each(s -> {
				s.deaths++;
				s.thisMonth().deaths++;
				s.lifeMs = 0;
			});
		} else if (!dead) {
			each(s -> {
				s.lifeMs += 50;
				if (s.lifeMs > s.bestLifeMs) {
					s.bestLifeMs = s.lifeMs;
				}
			});
		}
		if (dead) {
			String cause = deathCause(client);
			if (cause != null && !wasDead) {
				each(s -> s.deathCauses.merge(cause, 1L, Long::sum));
				// 49-142차: 원인 문구에 탭 목록의 다른 플레이어 이름이 있으면 PvP 죽음
				boolean pvp = false;
				for (String n : LunaCompat.playerListNames(client)) {
					if (n != null && n.length() > 2 && !n.equals(myName) && cause.contains(n)) {
						pvp = true;
						break;
					}
				}
				if (pvp) {
					each(s -> s.playerDeaths++);
				}
			}
		}
		wasDead = dead;
	}

	/** 사망 화면의 문구("…에게 살해당함")를 원인으로. 못 읽으면 null. */
	private static String deathCause(Minecraft client) {
		try {
			Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
			if (screen == null || !screen.getClass().getName().contains("DeathScreen")) {
				return null;
			}
			for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
				if (net.minecraft.network.chat.Component.class.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					Object v = f.get(screen);
					if (v instanceof net.minecraft.network.chat.Component t) {
						String s = t.getString();
						if (s != null && !s.isEmpty()) {
							return s;
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	// ---- 점프 / 웅크리기 ----

	private static void trackJumpSneak(Minecraft client) {
		try {
			boolean onGround = LunaCompat.isOnGround(client.player); // 49-36차: 1.15.2는 필드 onGround
			if (wasOnGround && !onGround) {
				Object vel = LunaCompat.invokeNoArg(client.player, "getVelocity");
				double vy = 0;
				if (vel != null) {
					Object yv = LunaCompat.invokeNoArg(vel, "getY");
					if (yv instanceof Number n) {
						vy = n.doubleValue();
					}
				}
				if (vy > 0.1) {
					each(s -> {
						s.jumps++;
						s.thisMonth().jumps++;
					});
				}
			}
			wasOnGround = onGround;
			if (client.player.isShiftKeyDown()) {
				each(s -> s.sneakMs += 50);
			}
		} catch (Throwable ignored) {
		}
	}

	// ---- 처치 ----

	private static void trackKills(Minecraft client) {
		try {
			long now = System.currentTimeMillis();
			// 내가 지금 때리고 있는 대상 기록(공격 키를 누른 채 엔티티를 조준 중)
			Object target = client.hitResult;
			boolean attacking = false;
			if (target != null && client.options != null) {
				Object type = LunaCompat.invokeNoArg(target, "getType");
				if (type instanceof Enum<?> e && "ENTITY".equals(e.name())
						&& LunaCompat.isAttackPressed(client)) {
					Object entity = LunaCompat.invokeNoArg(target, "getEntity");
					if (entity instanceof net.minecraft.world.entity.Entity ent) {
						int entId = LunaCompat.entityNetworkId(ent); // 49-36차: getId(1.17+) / getEntityId(≤1.16)
						ENGAGED.put(entId, now);
						attacking = true;
						ENGAGED_TYPE.put(entId, LunaCompat.entityTypeId(ent));
					}
				}
			}
			// 49-142차: 공격 횟수 = 엔티티를 조준한 채 공격을 누른 순간(누르고 있는 동안은 한 번)
			if (attacking && !wasAttacking) {
				each(s -> s.hits++);
			}
			wasAttacking = attacking;
			if (ENGAGED.isEmpty()) {
				return;
			}
			List<Integer> done = new ArrayList<>();
			for (Map.Entry<Integer, Long> e : ENGAGED.entrySet()) {
				if (now - e.getValue() > 8000) {
					done.add(e.getKey());
					continue;
				}
				net.minecraft.world.entity.Entity ent = client.level.getEntity(e.getKey());
				boolean dead = ent == null || !ent.isAlive();
				if (dead) {
					String type = ENGAGED_TYPE.getOrDefault(e.getKey(), "unknown");
					each(s -> {
						s.kills++;
						s.thisMonth().kills++;
						s.killTypes.merge(type, 1L, Long::sum);
						if (type.endsWith("player")) {
							s.playerKills++;
						}
					});
					done.add(e.getKey());
				}
			}
			for (Integer id : done) {
				ENGAGED.remove(id);
				ENGAGED_TYPE.remove(id);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:kills", t);
		}
	}

	// ---- 화면(상자 · 거래) ----

	private static void trackScreens(Minecraft client) {
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (screen == lastScreen) {
			trackTrades(client, screen);
			return;
		}
		lastScreen = screen;
		if (screen == null) {
			lastTradeUses = -1;
			return;
		}
		markActivity();
		String name = screen.getClass().getName();
		if (name.contains("MerchantScreen")) {
			lastTradeUses = tradeUses(client);
		} else if (LunaCompat.isHandledScreen(screen)) { // 49-36차: 1.15.2 ContainerScreen/container 필드
			try {
				if (LunaCompat.currentScreenHandler(client.player) != LunaCompat.playerScreenHandler(client.player)) {
					each(s -> {
						s.chestsOpened++;
						s.thisMonth().chests++;
					});
				}
			} catch (Throwable ignored) {
			}
		}
	}

	private static int lastTradeUses = -1;

	private static void trackTrades(Minecraft client, Object screen) {
		if (screen == null || !screen.getClass().getName().contains("MerchantScreen")) {
			return;
		}
		int uses = tradeUses(client);
		if (uses < 0) {
			return;
		}
		if (lastTradeUses >= 0 && uses > lastTradeUses) {
			int diff = uses - lastTradeUses;
			each(s -> {
				s.trades += diff;
				s.thisMonth().trades += diff;
			});
		}
		lastTradeUses = uses;
	}

	/** 지금 열린 거래 화면의 "사용된 거래 횟수" 합계(못 읽으면 -1). */
	private static int tradeUses(Minecraft client) {
		try {
			Object handler = LunaCompat.currentScreenHandler(client.player);
			Object offers = LunaCompat.invokeNoArg(handler, "getRecipes");
			if (!(offers instanceof Iterable<?> it)) {
				return -1;
			}
			int total = 0;
			for (Object o : it) {
				Object uses = LunaCompat.invokeNoArg(o, "getUses");
				if (uses instanceof Number n) {
					total += n.intValue();
				}
			}
			return total;
		} catch (Throwable ignored) {
			return -1;
		}
	}

	// ---- 손에 든 아이템(사용 · 내구도) ----

	private static void trackHeldItem(Minecraft client) {
		try {
			ItemStack heldRaw = client.player.getMainHandItem();
			final ItemStack held = heldRaw == null ? ItemStack.EMPTY : heldRaw;
			boolean same = !lastMainHand.isEmpty() && !held.isEmpty()
					&& lastMainHand.getItem() == held.getItem();
			if (same) {
				// 내구도가 닳았으면 그만큼 "사용"으로
				int before = lastMainHand.getDamageValue();
				int after = held.getDamageValue();
				if (after > before) {
					final String key = variantKey(held);
					final String name = displayName(held);
					final String base = baseId(held);
					final int diff = after - before;
					each(s -> {
						ItemStat st = s.item(key);
						st.name = name;
						st.base = base;
						st.used += diff;
					});
				}
				// 개수가 줄었으면(블록 놓기·먹기 등) 그만큼 사용. Q(버리기)를 누른 중이면 "버림"으로.
				int cnt = held.getCount();
				final boolean dropping = LunaCompat.isDropKeyPressed(client);
				if (lastHeldCount > 0 && cnt < lastHeldCount) {
					final int diff = lastHeldCount - cnt;
					final String key = variantKey(held);
					final String name = displayName(held);
					final String base = baseId(held);
					each(s -> {
						ItemStat st = s.item(key);
						st.name = name;
						st.base = base;
						if (dropping) {
							st.dropped += diff;
						} else {
							st.used += diff;
						}
					});
				}
				lastHeldCount = cnt;
			} else {
				lastHeldCount = held.isEmpty() ? -1 : held.getCount();
			}
			lastMainHand = held.isEmpty() ? ItemStack.EMPTY : held.copy();
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:held", t);
		}
	}

	// ---- 마인크래프트 기본 통계에서 제작·주움 보태기 ----
	// 우리 쪽에서 셀 수 없는 두 가지(제작 · 주움)만 바닐라 통계(StatHandler)에서 읽어 **늘어난 만큼만** 더한다.
	// 싱글플레이는 항상 최신이고, 서버는 통계 화면을 연 뒤(서버가 보내 준 뒤)부터 반영된다.
	private static final Map<String, Integer> VANILLA_BASE = new LinkedHashMap<>();
	private static int vanillaTick;

	/**
	 * 49-67차(5-13): <b>지금 이 순간의 마인크래프트 상호작용 통계</b>를 그대로 읽어 돌려준다
	 * (누적·차이 계산 없음). 통계 화면의 [상호작용] 탭이 이걸 쓴다.
	 *
	 * <p>{@link #syncVanilla}는 "이전에 본 값보다 늘어난 만큼"만 더하는데, 그러면 모드를 깔기 전에
	 * 쌓여 있던 숫자가 영영 안 들어온다("상호작용이 안 불러와진다"의 원인). 상호작용은 우리가 세는
	 * 값이 아니라 마인크래프트가 이미 세어 둔 값이므로, <b>보여 줄 때는 원본을 그대로 읽는 게 맞다.</b>
	 *
	 * <p>월드 밖이거나 통계를 못 읽으면 빈 맵을 돌려준다(부르는 쪽이 저장된 값으로 돌아간다).
	 */
	public static Map<String, Long> readVanillaInteractions(Minecraft client) {
		Map<String, Long> out = new LinkedHashMap<>();
		try {
			if (client == null || client.player == null) {
				return out;
			}
			Object handler = LunaCompat.invokeNoArg(client.player, "getStatHandler");
			if (handler == null) {
				return out;
			}
			java.lang.reflect.Field mapField = LunaCompat.findField(handler.getClass(), "statMap");
			if (mapField == null) {
				return out;
			}
			mapField.setAccessible(true);
			Object map = mapField.get(handler);
			if (!(map instanceof Map<?, ?> m)) {
				return out;
			}
			Class<?> statsClass = LunaCompat.classOrNull("net.minecraft.stats.Stats");
			java.lang.reflect.Field customField = statsClass == null ? null : LunaCompat.findField(statsClass, "CUSTOM");
			Object custom = customField == null ? null : customField.get(null);
			if (custom == null) {
				return out;
			}
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (!(e.getValue() instanceof Number n) || n.longValue() <= 0) {
					continue;
				}
				Object stat = e.getKey();
				if (LunaCompat.invokeNoArg(stat, "getType") != custom) {
					continue;
				}
				Object idObj = LunaCompat.invokeNoArg(stat, "getValue");
				String cid = idObj == null ? null : idObj.toString();
				String label = cid == null ? null : INTERACTION_NAMES.get(cid);
				if (label != null) {
					out.merge(label, n.longValue(), Long::sum);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:liveInteractions", t);
		}
		return out;
	}

	private static void syncVanilla(Minecraft client) {
		if (++vanillaTick % 100 != 0) {
			return; // 5초마다
		}
		try {
			Object handler = LunaCompat.invokeNoArg(client.player, "getStatHandler");
			if (handler == null) {
				return;
			}
			java.lang.reflect.Field mapField = LunaCompat.findField(handler.getClass(), "statMap");
			if (mapField == null) {
				return;
			}
			mapField.setAccessible(true);
			Object map = mapField.get(handler);
			if (!(map instanceof Map<?, ?> m)) {
				return;
			}
			Class<?> statsClass = LunaCompat.classOrNull("net.minecraft.stats.Stats");
			if (statsClass == null) {
				return;
			}
			Object crafted = LunaCompat.findField(statsClass, "CRAFTED").get(null);
			Object picked = LunaCompat.findField(statsClass, "PICKED_UP").get(null);
			java.lang.reflect.Field customField = LunaCompat.findField(statsClass, "CUSTOM");
			Object custom = customField == null ? null : customField.get(null);
			for (Map.Entry<?, ?> e : m.entrySet()) {
				Object stat = e.getKey();
				Object value = e.getValue();
				if (!(value instanceof Number n)) {
					continue;
				}
				Object type = LunaCompat.invokeNoArg(stat, "getType");
				boolean isCrafted = type == crafted;
				boolean isPicked = type == picked;
				boolean isCustom = custom != null && type == custom;
				if (isCustom) {
					// 49-28차: 상호작용(상자 열기 · 제작대 · 화로 · 낚시 · 번식 · 잠 …)은 바닐라가 이미 세고 있다
					Object idObj = LunaCompat.invokeNoArg(stat, "getValue");
					String cid = idObj == null ? null : idObj.toString();
					String label = cid == null ? null : INTERACTION_NAMES.get(cid);
					if (label != null) {
						String bucket = "x:" + cid;
						int nowVal = n.intValue();
						Integer prev = VANILLA_BASE.put(bucket, nowVal);
						if (prev != null && nowVal > prev) {
							final int diff = nowVal - prev;
							final String lab = label;
							each(sc -> {
								sc.interactions.merge(lab, (long) diff, Long::sum);
								sc.thisMonth().interactions += diff;
							});
						}
					}
					continue;
				}
				if (!isCrafted && !isPicked) {
					continue;
				}
				Object item = LunaCompat.invokeNoArg(stat, "getValue");
				String id = itemIdOf(item);
				if (id == null) {
					continue;
				}
				String bucket = (isCrafted ? "c:" : "p:") + id;
				int now = n.intValue();
				Integer before = VANILLA_BASE.put(bucket, now);
				if (before == null || now <= before) {
					continue; // 처음 본 값은 기준점만 잡음(예전 기록을 통째로 더하지 않게)
				}
				final int diff = now - before;
				final String key = id;
				each(s -> {
					ItemStat st = s.item(key);
					if (st.base == null) {
						st.base = key;
					}
					if (isCrafted) {
						st.crafted += diff;
						s.thisMonth().crafted += diff;
					} else {
						st.picked += diff;
					}
				});
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:vanilla", t);
		}
	}

	/** 49-28차: 보여 줄 상호작용 통계와 한글 이름(마인크래프트 기본 통계 id 기준). */
	private static final Map<String, String> INTERACTION_NAMES = new LinkedHashMap<>();

	static {
		INTERACTION_NAMES.put("minecraft:open_chest", "상자 열기");
		INTERACTION_NAMES.put("minecraft:open_barrel", "통 열기");
		INTERACTION_NAMES.put("minecraft:open_shulker_box", "셜커 상자 열기");
		INTERACTION_NAMES.put("minecraft:open_enderchest", "엔더 상자 열기");
		INTERACTION_NAMES.put("minecraft:trigger_trapped_chest", "덫 상자 작동");
		INTERACTION_NAMES.put("minecraft:interact_with_crafting_table", "제작대 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_furnace", "화로 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_blast_furnace", "용광로 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_smoker", "훈연기 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_anvil", "모루 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_grindstone", "숫돌 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_smithing_table", "대장장이 탁자 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_stonecutter", "석재 절단기 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_loom", "베틀 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_cartography_table", "제도대 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_brewingstand", "양조기 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_beacon", "신호기 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_lectern", "독서대 사용");
		INTERACTION_NAMES.put("minecraft:interact_with_campfire", "모닥불 사용");
		INTERACTION_NAMES.put("minecraft:inspect_dropper", "공급기 열기");
		INTERACTION_NAMES.put("minecraft:inspect_hopper", "깔때기 열기");
		INTERACTION_NAMES.put("minecraft:inspect_dispenser", "발사기 열기");
		INTERACTION_NAMES.put("minecraft:enchant_item", "인챈트");
		INTERACTION_NAMES.put("minecraft:fish_caught", "낚은 물고기");
		INTERACTION_NAMES.put("minecraft:animals_bred", "동물 번식");
		INTERACTION_NAMES.put("minecraft:talked_to_villager", "주민과 대화");
		INTERACTION_NAMES.put("minecraft:traded_with_villager", "주민과 거래");
		INTERACTION_NAMES.put("minecraft:sleep_in_bed", "침대에서 잠");
		INTERACTION_NAMES.put("minecraft:bell_ring", "종 울리기");
		INTERACTION_NAMES.put("minecraft:play_record", "음반 재생");
		INTERACTION_NAMES.put("minecraft:play_noteblock", "소리 블록 연주");
		INTERACTION_NAMES.put("minecraft:tune_noteblock", "소리 블록 조율");
		INTERACTION_NAMES.put("minecraft:pot_flower", "화분에 심기");
		INTERACTION_NAMES.put("minecraft:use_cauldron", "가마솥 사용");
		INTERACTION_NAMES.put("minecraft:fill_cauldron", "가마솥 채우기");
		INTERACTION_NAMES.put("minecraft:clean_armor", "갑옷 세탁");
		INTERACTION_NAMES.put("minecraft:clean_banner", "깃발 세탁");
		INTERACTION_NAMES.put("minecraft:clean_shulker_box", "셜커 상자 세탁");
		INTERACTION_NAMES.put("minecraft:eat_cake_slice", "케이크 먹기");
		INTERACTION_NAMES.put("minecraft:damage_dealt", "준 피해");
		INTERACTION_NAMES.put("minecraft:damage_blocked_by_shield", "방패로 막은 피해");
		INTERACTION_NAMES.put("minecraft:damage_absorbed", "흡수한 피해");
		INTERACTION_NAMES.put("minecraft:raid_win", "습격 승리");
		INTERACTION_NAMES.put("minecraft:target_hit", "표적 명중");
		INTERACTION_NAMES.put("minecraft:drop", "버린 아이템");
		INTERACTION_NAMES.put("minecraft:leave_game", "게임 나가기");
	}

	/** 준 피해처럼 1/10 하트 단위로 세는 항목인지(표시할 때 나눠 준다). */
	public static boolean isDamageInteraction(String label) {
		return label.endsWith("피해");
	}

	private static String itemIdOf(Object itemOrBlock) {
		try {
			if (itemOrBlock instanceof net.minecraft.world.item.Item item) {
				net.minecraft.resources.Identifier id = LunaCompat.getItemId(item);
				return id == null ? null : id.toString();
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	// ==================== 외부에서 더하는 기록 ====================

	/** 블록을 캤을 때(BlocksPerSecond와 같은 이벤트에서). */
	public static void onBlockMined(String blockId, String displayName) {
		if (blockId == null) {
			return;
		}
		each(s -> {
			ItemStat st = s.item(blockId);
			st.name = displayName == null ? blockId : displayName;
			st.base = blockId;
			st.mined++;
			s.thisMonth().mined++;
		});
		dirty = true;
	}

	/** 아이템을 버렸을 때. */
	public static void onItemDropped(ItemStack stack, int count) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		String key = variantKey(stack);
		String name = displayName(stack);
		String base = baseId(stack);
		each(s -> {
			ItemStat st = s.item(key);
			st.name = name;
			st.base = base;
			st.dropped += count;
		});
		dirty = true;
	}

	/** 아이템을 제작했을 때(제작 결과를 집었을 때). */
	public static void onItemCrafted(ItemStack stack, int count) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		String key = variantKey(stack);
		String name = displayName(stack);
		String base = baseId(stack);
		each(s -> {
			ItemStat st = s.item(key);
			st.name = name;
			st.base = base;
			st.crafted += count;
		});
		dirty = true;
	}

	/** 채팅을 보냈을 때. */
	public static void onChatSent() {
		each(s -> s.chatSent++);
		dirty = true;
	}

	// ==================== 아이템 키(변형 구분) ====================

	private static String baseId(ItemStack stack) {
		net.minecraft.resources.Identifier id = LunaCompat.getItemId(stack.getItem());
		return id == null ? "unknown" : id.toString();
	}

	private static String displayName(ItemStack stack) {
		try {
			return stack.getHoverName().getString();
		} catch (Throwable ignored) {
			return baseId(stack);
		}
	}

	/**
	 * 같은 종류라도 인챈트/이름이 다르면 다른 키로(사용자: "다이아몬드 곡괭이 - 맵에서 무슨 스펙의 곡,
	 * 서버 전용으로 꾸며진 곡 이런 식으로 도구마다도 나눠"). 아무 특징이 없으면 기본 id 그대로.
	 */
	private static String variantKey(ItemStack stack) {
		String base = baseId(stack);
		StringBuilder sb = new StringBuilder();
		try {
			String name = displayName(stack);
			String plain = LunaCompat.translate(stack.getItem().getDescriptionId());
			if (plain != null && !plain.equals(name)) {
				sb.append(name);
			}
			List<LunaCompat.EnchantInfo> ench = LunaCompat.enchantments(stack);
			for (LunaCompat.EnchantInfo e : ench) {
				sb.append('|').append(e.id()).append(e.level());
			}
		} catch (Throwable ignored) {
		}
		if (sb.length() == 0) {
			return base;
		}
		return base + "#" + Integer.toHexString(sb.toString().hashCode());
	}

	// ==================== 저장 / 불러오기 ====================

	private static void ensureGlobal() {
		if (globalLoaded) {
			return;
		}
		globalLoaded = true;
		Scope loaded = loadScope(globalFile(), "global", "전체");
		copyInto(loaded, GLOBAL);
	}

	private static void copyInto(Scope from, Scope to) {
		to.key = from.key;
		to.label = from.label;
		to.folder = from.folder;
		to.playMs = from.playMs;
		to.afkMs = from.afkMs;
		to.sneakMs = from.sneakMs;
		to.sessions = from.sessions;
		to.jumps = from.jumps;
		to.deaths = from.deaths;
		to.kills = from.kills;
		to.trades = from.trades;
		to.chestsOpened = from.chestsOpened;
		to.chatSent = from.chatSent;
		to.damageTaken = from.damageTaken;
		to.firstSeen = from.firstSeen;
		to.lastSeen = from.lastSeen;
		to.hits = from.hits;
		to.playerKills = from.playerKills;
		to.playerDeaths = from.playerDeaths;
		to.bestLifeMs = from.bestLifeMs;
		to.lifeMs = from.lifeMs;
		to.longestSessionMs = from.longestSessionMs;
		to.chatReceived = from.chatReceived;
		to.mentions = from.mentions;
		System.arraycopy(from.hourMs, 0, to.hourMs, 0, to.hourMs.length);
		to.days.clear();
		to.days.putAll(from.days);
		to.peers.clear();
		to.peers.putAll(from.peers);
		to.deathCauses.clear();
		to.deathCauses.putAll(from.deathCauses);
		to.killTypes.clear();
		to.killTypes.putAll(from.killTypes);
		to.distance.clear();
		to.distance.putAll(from.distance);
		to.travelMs.clear();
		to.travelMs.putAll(from.travelMs);
		to.items.clear();
		to.items.putAll(from.items);
		to.interactions.clear();
		to.interactions.putAll(from.interactions);
		to.months.clear();
		to.months.putAll(from.months);
	}

	@SuppressWarnings("deprecation")
	private static Scope loadScope(Path file, String key, String label) {
		Scope s = new Scope();
		s.key = key;
		s.label = label == null ? key : label;
		if (!Files.exists(file)) {
			return s;
		}
		try (java.io.BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject o = new JsonParser().parse(r).getAsJsonObject();
			readScope(o, s);
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:load:" + key, t);
		}
		return s;
	}

	static void readScope(JsonObject o, Scope s) {
		s.label = str(o, "label", s.label);
		s.folder = str(o, "folder", s.folder);
		s.playMs = num(o, "playMs");
		s.afkMs = num(o, "afkMs");
		s.sneakMs = num(o, "sneakMs");
		s.sessions = num(o, "sessions");
		s.jumps = num(o, "jumps");
		s.deaths = num(o, "deaths");
		s.kills = num(o, "kills");
		s.trades = num(o, "trades");
		s.chestsOpened = num(o, "chestsOpened");
		s.chatSent = num(o, "chatSent");
		s.damageTaken = dnum(o, "damageTaken");
		s.firstSeen = num(o, "firstSeen");
		s.lastSeen = num(o, "lastSeen");
		s.hits = num(o, "hits");
		s.playerKills = num(o, "playerKills");
		s.playerDeaths = num(o, "playerDeaths");
		s.bestLifeMs = num(o, "bestLifeMs");
		s.lifeMs = num(o, "lifeMs");
		s.longestSessionMs = num(o, "longestSessionMs");
		s.chatReceived = num(o, "chatReceived");
		s.mentions = num(o, "mentions");
		if (o.has("hourMs") && o.get("hourMs").isJsonArray()) {
			com.google.gson.JsonArray a = o.getAsJsonArray("hourMs");
			for (int i = 0; i < Math.min(a.size(), s.hourMs.length); i++) {
				try {
					s.hourMs[i] = a.get(i).getAsLong();
				} catch (Throwable ignored) {
				}
			}
		}
		readLongMap(o, "days", s.days);
		readLongMap(o, "peers", s.peers);
		readLongMap(o, "deathCauses", s.deathCauses);
		readLongMap(o, "interactions", s.interactions);
		if (o.has("months") && o.get("months").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("months").entrySet()) {
				JsonObject mo = e.getValue().getAsJsonObject();
				Period p = s.month(e.getKey());
				p.playMs = num(mo, "playMs");
				p.afkMs = num(mo, "afkMs");
				p.deaths = num(mo, "deaths");
				p.kills = num(mo, "kills");
				p.jumps = num(mo, "jumps");
				p.trades = num(mo, "trades");
				p.chests = num(mo, "chests");
				p.mined = num(mo, "mined");
				p.crafted = num(mo, "crafted");
				p.interactions = num(mo, "interactions");
				p.distance = dnum(mo, "distance");
				p.damageTaken = dnum(mo, "damageTaken");
			}
		}
		readLongMap(o, "killTypes", s.killTypes);
		readDoubleMap(o, "distance", s.distance);
		readLongMap(o, "travelMs", s.travelMs);
		if (o.has("items") && o.get("items").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("items").entrySet()) {
				JsonObject io = e.getValue().getAsJsonObject();
				ItemStat st = s.item(e.getKey());
				st.used = num(io, "used");
				st.dropped = num(io, "dropped");
				st.crafted = num(io, "crafted");
				st.mined = num(io, "mined");
				st.picked = num(io, "picked");
				st.name = str(io, "name", e.getKey());
				st.base = str(io, "base", e.getKey());
			}
		}
	}

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
	}

	private static long num(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0L;
		} catch (Throwable ignored) {
			return 0L;
		}
	}

	private static double dnum(JsonObject o, String k) {
		try {
			return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0d;
		} catch (Throwable ignored) {
			return 0d;
		}
	}

	private static void readLongMap(JsonObject o, String k, Map<String, Long> out) {
		if (!o.has(k) || !o.get(k).isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(k).entrySet()) {
			try {
				out.put(e.getKey(), e.getValue().getAsLong());
			} catch (Throwable ignored) {
			}
		}
	}

	private static void readDoubleMap(JsonObject o, String k, Map<String, Double> out) {
		if (!o.has(k) || !o.get(k).isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(k).entrySet()) {
			try {
				out.put(e.getKey(), e.getValue().getAsDouble());
			} catch (Throwable ignored) {
			}
		}
	}

	static JsonObject writeScope(Scope s) {
		JsonObject o = new JsonObject();
		o.addProperty("key", s.key);
		o.addProperty("label", s.label);
		o.addProperty("folder", s.folder);
		o.addProperty("playMs", s.playMs);
		o.addProperty("afkMs", s.afkMs);
		o.addProperty("sneakMs", s.sneakMs);
		o.addProperty("sessions", s.sessions);
		o.addProperty("jumps", s.jumps);
		o.addProperty("deaths", s.deaths);
		o.addProperty("kills", s.kills);
		o.addProperty("trades", s.trades);
		o.addProperty("chestsOpened", s.chestsOpened);
		o.addProperty("chatSent", s.chatSent);
		o.addProperty("damageTaken", s.damageTaken);
		o.addProperty("firstSeen", s.firstSeen);
		o.addProperty("lastSeen", s.lastSeen);
		o.addProperty("hits", s.hits);
		o.addProperty("playerKills", s.playerKills);
		o.addProperty("playerDeaths", s.playerDeaths);
		o.addProperty("bestLifeMs", s.bestLifeMs);
		o.addProperty("lifeMs", s.lifeMs);
		o.addProperty("longestSessionMs", s.longestSessionMs);
		o.addProperty("chatReceived", s.chatReceived);
		o.addProperty("mentions", s.mentions);
		com.google.gson.JsonArray hours = new com.google.gson.JsonArray();
		for (long v : s.hourMs) {
			hours.add(v);
		}
		o.add("hourMs", hours);
		o.add("days", longMap(s.days));
		o.add("peers", longMap(s.peers));
		o.add("deathCauses", longMap(s.deathCauses));
		o.add("interactions", longMap(s.interactions));
		JsonObject months = new JsonObject();
		for (Map.Entry<String, Period> e : s.months.entrySet()) {
			Period p = e.getValue();
			JsonObject mo = new JsonObject();
			mo.addProperty("playMs", p.playMs);
			mo.addProperty("afkMs", p.afkMs);
			mo.addProperty("deaths", p.deaths);
			mo.addProperty("kills", p.kills);
			mo.addProperty("jumps", p.jumps);
			mo.addProperty("trades", p.trades);
			mo.addProperty("chests", p.chests);
			mo.addProperty("mined", p.mined);
			mo.addProperty("crafted", p.crafted);
			mo.addProperty("interactions", p.interactions);
			mo.addProperty("distance", p.distance);
			mo.addProperty("damageTaken", p.damageTaken);
			months.add(e.getKey(), mo);
		}
		o.add("months", months);
		o.add("killTypes", longMap(s.killTypes));
		JsonObject dist = new JsonObject();
		for (Map.Entry<String, Double> e : s.distance.entrySet()) {
			dist.addProperty(e.getKey(), e.getValue());
		}
		o.add("distance", dist);
		o.add("travelMs", longMap(s.travelMs));
		JsonObject items = new JsonObject();
		for (Map.Entry<String, ItemStat> e : s.items.entrySet()) {
			ItemStat st = e.getValue();
			JsonObject io = new JsonObject();
			io.addProperty("used", st.used);
			io.addProperty("dropped", st.dropped);
			io.addProperty("crafted", st.crafted);
			io.addProperty("mined", st.mined);
			io.addProperty("picked", st.picked);
			if (st.name != null) {
				io.addProperty("name", st.name);
			}
			if (st.base != null) {
				io.addProperty("base", st.base);
			}
			items.add(e.getKey(), io);
		}
		o.add("items", items);
		return o;
	}

	private static JsonObject longMap(Map<String, Long> map) {
		JsonObject o = new JsonObject();
		for (Map.Entry<String, Long> e : map.entrySet()) {
			o.addProperty(e.getKey(), e.getValue());
		}
		return o;
	}

	/** 지금까지 모은 걸 파일에 씀(변경이 있을 때만). */
	public static void saveNow() {
		if (!dirty) {
			return;
		}
		dirty = false;
		try {
			Files.createDirectories(dir().resolve("worlds"));
			write(globalFile(), writeScope(GLOBAL));
			if (world != null && worldKey != null) {
				write(worldFile(worldKey), writeScope(world));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:save", t);
		}
	}

	private static void write(Path file, JsonObject o) throws java.io.IOException {
		try (java.io.BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(o, w);
		}
	}

	// ==================== 조회 ====================

	public static Scope global() {
		ensureGlobal();
		return GLOBAL;
	}

	/** 지금 접속 중인 서버/맵의 기록(없으면 null). */
	public static Scope current() {
		return world;
	}

	/** 저장된 서버/맵 목록(파일 이름 = 키). */
	public static List<Scope> worlds() {
		List<Scope> out = new ArrayList<>();
		try {
			Path dir = dir().resolve("worlds");
			if (!Files.isDirectory(dir)) {
				return out;
			}
			try (java.util.stream.Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) {
					String name = p.getFileName().toString();
					if (!name.endsWith(".json")) {
						continue;
					}
					String key = name.substring(0, name.length() - 5);
					if (world != null && key.equals(worldKey)) {
						out.add(world);
						continue;
					}
					Scope s = loadScope(p, key, key);
					// 49-32차: 싱글 맵인데 세이브 폴더가 없어졌으면 "(삭제됨)"으로 보여 준다.
					s.missing = !s.folder.isEmpty()
							&& !LunaCompat.worldFolderExists(Minecraft.getInstance(), s.folder);
					out.add(s);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:worlds", t);
		}
		out.sort((a, b) -> Long.compare(b.playMs, a.playMs));
		return out;
	}

	// ==================== 백업 / 불러오기 ====================

	/**
	 * 통계를 파일 하나로 내보냄(전체 + 서버별). 같은 마인크래프트 계정(uuid)에서만 다시 불러올 수 있게
	 * uuid를 같이 적는다 - "컴퓨터를 바꿀 때만" 쓰는 용도.
	 */
	public static Path exportTo(Minecraft client, Path file) throws java.io.IOException {
		saveNow();
		JsonObject root = new JsonObject();
		root.addProperty("format", "stats-backup");   // 49-212차: 내보내는 파일 안에도 루나/노바 없음
		root.addProperty("version", 1);
		root.addProperty("exportedAt", System.currentTimeMillis());
		root.addProperty("uuid", LunaSocial.currentUuid(client));
		root.addProperty("player", LunaCompat.sessionName(client));
		root.add("global", writeScope(GLOBAL));
		JsonObject worlds = new JsonObject();
		for (Scope s : worlds()) {
			worlds.add(s.key, writeScope(s));
		}
		root.add("worlds", worlds);
		Files.createDirectories(file.getParent());
		try (java.io.BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(root, w);
		}
		return file;
	}

	/**
	 * 백업 파일 한 줄 요약(확인창에 보여 줄 용도). 우리 형식이 아니거나 못 읽으면 null.
	 */
	@SuppressWarnings("deprecation")
	public static String previewOf(Path file) {
		try (java.io.BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = new JsonParser().parse(r).getAsJsonObject();
			if (!isBackupFormat(str(root, "format", ""))) {
				return null;
			}
			Scope g = new Scope();
			if (root.has("global")) {
				readScope(root.getAsJsonObject("global"), g);
			}
			int worldCount = root.has("worlds") && root.get("worlds").isJsonObject()
					? root.getAsJsonObject("worlds").entrySet().size() : 0;
			return formatHours(g.playMs) + " · 서버 " + worldCount + "곳";
		} catch (Throwable t) {
			return null;
		}
	}

	/** 이 백업이 내 계정 것인지(다른 계정 기록을 섞지 않기 위해). */
	@SuppressWarnings("deprecation")
	public static boolean isMine(Minecraft client, Path file) {
		try (java.io.BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = new JsonParser().parse(r).getAsJsonObject();
			String fileUuid = str(root, "uuid", "");
			String myUuid = LunaSocial.currentUuid(client);
			return fileUuid != null && !fileUuid.isEmpty() && myUuid != null
					&& fileUuid.replace("-", "").equalsIgnoreCase(myUuid.replace("-", ""));
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * ⚠️ <b>덮어쓰기</b>. 파일의 기록으로 지금 기록을 통째로 <b>바꾼다</b>(합산이 아니다).
	 *
	 * <p><b>49-53차(5-12) - 이게 버그였다.</b> 예전 {@code importFrom}은 {@code merge()}로 <b>더했다</b>.
	 * 그래서 불러오기를 두 번 누르면 모든 수치가 두 배가 됐고, 한 시간짜리 백업을 새 싱글플레이에서
	 * 불러오면 그 한 시간이 지금 기록 위에 그대로 얹혔다(사용자가 "통계가 조작된다"고 알려 준 것이
	 * 정확히 이 동작이다). 불러오기는 "그 파일 상태로 되돌리기"라야 뜻이 통하므로 교체로 바꿨다.
	 *
	 * <p>되돌릴 수 없는 작업이라 <b>덮어쓰기 직전에 지금 기록을 자동 백업</b>해 돌아갈 길을 남긴다.
	 * 백업에 없는 서버·맵 기록은 지운다 - 그래야 "전체를 이 파일로" 라는 말이 맞는다.
	 */
	@SuppressWarnings("deprecation")
	public static String replaceFrom(Minecraft client, Path file) {
		try {
			if (!Files.exists(file)) {
				return "파일을 찾지 못했습니다";
			}
			JsonObject root;
			try (java.io.BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				root = new JsonParser().parse(r).getAsJsonObject();
			}
			if (!isBackupFormat(str(root, "format", ""))) {
				return "Nova 통계 백업 파일이 아닙니다";
			}
			if (!isMine(client, file)) {
				return "다른 계정의 백업입니다 · 같은 마인크래프트 계정에서만 불러올 수 있습니다";
			}

			// ① 지금 기록을 먼저 백업(덮어쓰기는 되돌릴 수 없으므로)
			String rollback = null;
			try {
				String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
						.format(java.time.LocalDateTime.now());
				rollback = exportTo(client, dir().resolve("덮어쓰기전_" + stamp + ".json"))
						.getFileName().toString();
			} catch (Throwable ignored) {
				// 백업에 실패해도 덮어쓰기 자체는 진행한다(사용자가 이미 확인했으므로)
			}

			ensureGlobal();

			// ② 전체
			Scope g = new Scope();
			if (root.has("global")) {
				readScope(root.getAsJsonObject("global"), g);
			}
			g.key = "global";
			g.label = "전체";
			copyInto(g, GLOBAL);

			// ③ 서버·맵 - 파일에 있는 것으로 갈아 끼우고, 파일에 없는 것은 지운다
			Path worldsDir = dir().resolve("worlds");
			Files.createDirectories(worldsDir);
			java.util.Set<String> keep = new java.util.HashSet<>();
			int worldCount = 0;
			if (root.has("worlds") && root.get("worlds").isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("worlds").entrySet()) {
					Scope in = new Scope();
					in.key = e.getKey();
					readScope(e.getValue().getAsJsonObject(), in);
					Path f = worldFile(e.getKey());
					write(f, writeScope(in));
					keep.add(f.getFileName().toString());
					if (world != null && e.getKey().equals(worldKey)) {
						copyInto(in, world);
					}
					worldCount++;
				}
			}
			try (java.util.stream.Stream<Path> olds = Files.list(worldsDir)) {
				for (Path p : olds.toList()) {
					String n = p.getFileName().toString();
					if (n.endsWith(".json") && !keep.contains(n)) {
						Files.deleteIfExists(p);
					}
				}
			}
			// 지금 접속해 있는 서버가 백업에 없으면 메모리 기록도 비운다
			// (안 비우면 바로 다음 저장 때 방금 지운 파일이 되살아난다)
			if (world != null && worldKey != null
					&& !keep.contains(worldFile(worldKey).getFileName().toString())) {
				Scope empty = new Scope();
				empty.key = world.key;
				empty.label = world.label;
				empty.folder = world.folder;
				copyInto(empty, world);
			}

			dirty = true;
			saveNow();
			return "덮어썼습니다 · 서버 " + worldCount + "곳"
					+ (rollback == null ? "" : " · 이전 기록은 " + rollback);
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:replace", t);
			return "불러오지 못했습니다: " + t.getClass().getSimpleName();
		}
	}

	// ==================== 표시용 ====================

	/** 밀리초 → "12시간" / "3일 5시간"(사용자: 플레이타임은 시간으로만). */
	/** 49-151차(사용자: "플레이한 시간 소수점 1자리까지"): 6분 이상이면 "46.2시간", 그 아래는 "N분". */
	public static String formatHours(long ms) {
		if (ms < 360_000L) {
			return (ms / 60_000L) + "분";
		}
		return String.format(java.util.Locale.ROOT, "%.1f시간", ms / 3_600_000.0);
	}

	public static String formatDuration(long ms) {
		long hours = ms / 3_600_000L;
		long minutes = (ms % 3_600_000L) / 60_000L;
		if (hours > 0) {
			return hours + "시간 " + minutes + "분";
		}
		return minutes + "분";
	}

	public static String formatDistance(double meters) {
		if (meters >= 1000) {
			return String.format(Locale.ROOT, "%.1fkm", meters / 1000);
		}
		return String.format(Locale.ROOT, "%.0f블록", meters);
	}

	/** 49-212차: 통계 백업 형식 표시 - 새 이름(stats-backup)과 옛 이름(luna-stats, nova-stats) 모두 받는다. */
	private static boolean isBackupFormat(String format) {
		return "stats-backup".equals(format) || "luna-stats".equals(format) || "nova-stats".equals(format);
	}
}
