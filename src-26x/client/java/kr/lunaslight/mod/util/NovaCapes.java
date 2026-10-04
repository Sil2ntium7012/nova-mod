package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.CapeSmoothModule;
import net.minecraft.resources.Identifier;

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
	private static final long TTL = 60_000L;

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
				selfId = norm(LunaSocial.currentUuid(net.minecraft.client.Minecraft.getInstance()));
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
			uid = norm(((net.minecraft.world.entity.Entity) entity).getUUID().toString());
		} catch (Throwable t) {
			return null;
		}
		String key;
		if (uid.equals(selfId())) {
			key = mine;
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

	/** 모인 uuid를 한 번에 조회(2초에 한 번, 최대 60명). */
	private static void maybeFetch() {
		long now = System.currentTimeMillis();
		if (inFlight || WANT.isEmpty() || now - lastFetch < 2000L || !LunaSocial.available()) {
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
					FETCHED.put(u, err == null ? t : t - TTL + 15_000L);   // 실패면 15초 뒤 다시
				}
				inFlight = false;
			});
		} catch (Throwable t) {
			inFlight = false;
		}
	}

	/** 26.x: 렌더 상태의 스킨에서 망토/겉날개 칸만 노바 망토로 바꾼다(바닐라 망토 모델과 흔들림 그대로). */
	public static void applyTo(Object entity, net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
		if (state == null || state.skin == null) {
			return;
		}
		Identifier tex = textureFor(entity);
		if (tex == null) {
			return;
		}
		net.minecraft.world.entity.player.PlayerSkin sk = state.skin;
		net.minecraft.core.ClientAsset.Texture t = ASSETS.computeIfAbsent(tex, k -> new net.minecraft.core.ClientAsset.ResourceTexture(k, k));
		if (sk.cape() == t && sk.elytra() == t) {
			return;
		}
		state.skin = new net.minecraft.world.entity.player.PlayerSkin(sk.body(), t, t, sk.model(), sk.secure());
	}

	private static final Map<Identifier, net.minecraft.core.ClientAsset.Texture> ASSETS = new ConcurrentHashMap<>();
}
