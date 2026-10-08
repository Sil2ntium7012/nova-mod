package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.CapeSmoothModule;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-240차: 노바 망토(런처 상점 망토 10종, 파스텔). 런처 세션 인수인계(claude/nova-mod-wings-capes-handoff.md) 그대로:
 * <ul>
 *   <li>별도 모델 없이 <b>바닐라 망토 칸</b>의 텍스처만 바꾼다 - 바닐라 망토 모델, 흔들림(+ 망토 부드럽게), 겉날개까지 그대로.</li>
 *   <li>텍스처 = jar의 assets/lunaslight/textures/cosmetic/capes/&lt;key&gt;.png(64x32 바닐라 망토 UV).</li>
 *   <li>내 것 = .luna-launch.json cosmetics.cape.key, 남의 것 = Supabase nova_player_tiers.cosmetic_cape(묶어서 60초 캐시).</li>
 *   <li>바닐라 [망토 보이기](스킨 사용자 지정) 설정은 바닐라가 그대로 따진다.</li>
 * </ul>
 */
public final class NovaCapes {
	private NovaCapes() {
	}

	public static final String[] KEYS = {"red", "orange", "yellow", "green", "blue", "navy", "purple", "black", "gray", "white"};

	private static volatile String mine;
	private static final Map<String, String> OTHERS = new ConcurrentHashMap<>();
	private static final Map<String, Long> FETCHED = new ConcurrentHashMap<>();
	private static final Set<String> WANT = ConcurrentHashMap.newKeySet();
	private static volatile boolean inFlight;
	private static volatile long lastFetch;
	private static final Map<String, Identifier> IDS = new ConcurrentHashMap<>();
	private static String selfId = "";
	private static long selfAt;
	private static final long TTL = 10 * 60_000L;   // 10-08(Supabase 요청 수): 1분 → 10분, 없는 사람도 같이 기억

	/** 런처가 알려 준 내 노바 망토(key, 없으면 null). */
	public static void setMine(String key) {
		mine = valid(key);
	}

	public static String mine() {
		return mine;
	}

	private static String valid(String key) {
		if (key == null) {
			return null;
		}
		String k = key.trim().toLowerCase(Locale.ROOT);
		if (k.startsWith("cape-")) {
			k = k.substring(5);
		}
		for (String x : KEYS) {
			if (x.equals(k)) {
				return x;
			}
		}
		return null;
	}

	private static String norm(String id) {
		return id == null ? "" : id.replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static Identifier id(String key) {
		return IDS.computeIfAbsent(key, k -> kr.lunaslight.mod.gui.LunaGfx.id("textures/cosmetic/capes/" + k + ".png"));
	}

	private static String selfId() {
		long now = System.currentTimeMillis();
		if (now - selfAt > 5000L) {
			selfAt = now;
			try {
				selfId = norm(LunaSocial.currentUuid(net.minecraft.client.MinecraftClient.getInstance()));
			} catch (Throwable ignored) {
			}
		}
		return selfId;
	}

	/** 이 플레이어가 낀 노바 망토 텍스처(없으면 null). 남의 것은 모르면 조회를 예약한다. */
	public static Identifier textureFor(Object entity) {
		if (entity == null || !CapeSmoothModule.novaCapesShown()) {
			return null;
		}
		LunaSocial.load();
		String uid;
		try {
			uid = norm(((net.minecraft.entity.Entity) entity).getUuid().toString());
		} catch (Throwable t) {
			return null;
		}
		String key;
		if (uid.equals(selfId())) {
			key = kr.lunaslight.mod.module.impl.misc.CosmeticsModule.capeOn() ? mine : null;   // 49-282차: [코스메틱] 망토 쓰기
		} else {
			if (!CapeSmoothModule.othersCapesShown()) {
				return null;
			}
			key = OTHERS.get(uid);
			Long at = FETCHED.get(uid);
			if (at == null || System.currentTimeMillis() - at > TTL) {
				WANT.add(uid);
				maybeFetch();
			}
		}
		return key == null || key.isEmpty() ? null : id(key);
	}

	/** 모인 uuid를 한 번에 조회(5초에 한 번, 최대 60명). 10-08: 한 번 본 사람은 10분 동안 다시 안 묻는다. */
	private static void maybeFetch() {
		long now = System.currentTimeMillis();
		if (inFlight || WANT.isEmpty() || now - lastFetch < 5000L || !LunaSocial.available()) {
			return;
		}
		inFlight = true;
		lastFetch = now;
		List<String> batch = new ArrayList<>();
		for (String u : WANT) {
			batch.add(u);
			if (batch.size() >= 60) {
				break;
			}
		}
		WANT.removeAll(batch);
		try {
			LunaSocial.fetchCapes(batch).whenComplete((map, err) -> {
				long t = System.currentTimeMillis();
				for (String u : batch) {
					String k = map == null ? null : valid(map.get(u));
					OTHERS.put(u, k == null ? "" : k);
					FETCHED.put(u, err == null ? t : t - TTL + 60_000L);   // 실패면 15초 뒤 다시
				}
				inFlight = false;
			});
		} catch (Throwable t) {
			inFlight = false;
		}
	}

	/**
	 * 1.20.2+: 플레이어 스킨 묶음(SkinTextures 레코드)에서 망토/겉날개 칸만 노바 망토로 바꾼 사본. 버전마다 레코드 모양이 달라
	 * (1.20.2~1.21.8 = Identifier 칸 셋 + 주소 글자, 1.21.9+ = TextureAsset 칸 셋) 이름 대신 순서로 찾는다:
	 * 첫 칸(몸 텍스처)과 같은 타입인 칸 중 둘째 = 망토, 셋째 = 겉날개.
	 */
	public static Object patchSkin(Object entity, Object skin) {
		if (skin == null || !skin.getClass().isRecord()) {
			return skin;
		}
		Identifier tex = textureFor(entity);
		if (tex == null) {
			return skin;
		}
		Object[] cached = PATCHED.get(skin);
		if (cached != null && cached[0] == tex) {
			return cached[1];
		}
		try {
			Class<?> c = skin.getClass();
			java.lang.reflect.RecordComponent[] rc = c.getRecordComponents();
			Object[] vals = new Object[rc.length];
			Class<?>[] types = new Class<?>[rc.length];
			for (int i = 0; i < rc.length; i++) {
				java.lang.reflect.Method m = rc[i].getAccessor();
				m.setAccessible(true);
				vals[i] = m.invoke(skin);
				types[i] = rc[i].getType();
			}
			int seen = 0, capeIdx = -1, elyIdx = -1;
			for (int i = 0; i < rc.length; i++) {
				if (types[i] == types[0]) {
					seen++;
					if (seen == 2) {
						capeIdx = i;
					} else if (seen == 3) {
						elyIdx = i;
					}
				}
			}
			if (capeIdx < 0) {
				return skin;
			}
			Object val = types[0].isInstance(tex) ? tex : asset(types[0], tex);
			if (val == null) {
				return skin;
			}
			vals[capeIdx] = val;
			if (elyIdx >= 0) {
				vals[elyIdx] = val;
			}
			java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor(types);
			ctor.setAccessible(true);
			Object out = ctor.newInstance(vals);
			if (PATCHED.size() > 256) {
				PATCHED.clear();
			}
			PATCHED.put(skin, new Object[]{tex, out});
			return out;
		} catch (Throwable t) {
			LunaCompat.warnOnce("novaCape:skin", t);
			return skin;
		}
	}

	private static final Map<Object, Object[]> PATCHED = new ConcurrentHashMap<>();
	private static final Map<Identifier, Object> ASSETS = new ConcurrentHashMap<>();

	/** 1.21.9+: 망토 칸 타입(TextureAsset 인터페이스)을 구현하고 (Identifier, Identifier) 생성자가 있는 레코드로 감싼다. */
	private static Object asset(Class<?> iface, Identifier tex) {
		Object hit = ASSETS.get(tex);
		if (hit != null) {
			return hit;
		}
		Class<?> outer = iface.getDeclaringClass();
		if (outer == null) {
			return null;
		}
		for (Class<?> k : outer.getDeclaredClasses()) {
			if (!iface.isAssignableFrom(k) || k.isInterface()) {
				continue;
			}
			try {
				java.lang.reflect.Constructor<?> ctor = k.getDeclaredConstructor(Identifier.class, Identifier.class);
				ctor.setAccessible(true);
				Object v = ctor.newInstance(tex, tex);
				ASSETS.put(tex, v);
				return v;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}
}
