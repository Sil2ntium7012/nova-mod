package kr.lunaslight.mod.util;

import java.io.InputStream;
import java.util.Map;

/**
 * 49-533차(사용자: "목탄 리소스팩을 만들어 달라니까, 요리용 석탄의 그림"): 너굴마을 리소스팩의 요리용 석탄 그림(ng:item/gem/cook_charcoal)은
 * 바닐라 숯 그림과 똑같다. 노바가 따로 그린 그림(숯 덩어리 + 갈라진 틈의 주황 불씨 + 불티, assets/lunaslight/neogul/cook_charcoal.png)으로
 * 바꿔 넘긴다. 팩에 그 파일이 있을 때만(=너굴마을 팩이 켜져 있을 때) 바꾼다 - NeogulKeyArrowsMixin이 getResource와 listResources에서 부른다.
 */
public final class NeogulTextures {
	private NeogulTextures() {
	}

	/** 팩 경로(네임스페이스:경로) → 노바 안의 그림. */
	private static final Map<String, String> REPLACE = Map.of(
		"ng:textures/item/gem/cook_charcoal.png", "/assets/lunaslight/neogul/cook_charcoal.png");

	private static final Map<String, byte[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	/** 바꿀 그림이면 그 PNG, 아니면 null. */
	public static byte[] replacement(String namespace, String path) {
		String key = namespace + ":" + path;
		String res = REPLACE.get(key);
		if (res == null) {
			return null;
		}
		byte[] hit = CACHE.get(key);
		if (hit != null) {
			return hit;
		}
		try (InputStream in = NeogulTextures.class.getResourceAsStream(res)) {
			if (in == null) {
				return null;
			}
			byte[] b = in.readAllBytes();
			CACHE.put(key, b);
			return b;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulTextures", t);
			return null;
		}
	}
}
