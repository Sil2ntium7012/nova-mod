package kr.lunaslight.mod.util;

import java.util.Map;

/**
 * 49-533차(사용자: "목탄 리소스팩을 만들어 달라니까, 요리용 석탄의 그림"): 너굴마을 리소스팩의 요리용 석탄 그림(ng:item/gem/cook_charcoal)을 바꿔 넘긴다.
 * NeogulKeyArrowsMixin이 getResource와 listResources에서 부른다.
 *
 * <p>49-700차(사용자 사진: 노바가 그린 불씨 석탄 - "이거 리팩 숯으로 해 줄 수 있나"): 노바 그림 대신 지금 켜 둔 리소스팩의 숯 그림
 * (minecraft:textures/item/charcoal.png)을 그대로 쓴다. 숯을 바꾸는 리소스팩을 켜 두면 요리용 석탄도 같이 바뀐다.
 */
public final class NeogulTextures {
	private NeogulTextures() {
	}

	/** 팩 경로(네임스페이스:경로) → 대신 쓸 그림 경로(minecraft 네임스페이스). */
	private static final Map<String, String> REPLACE = Map.of(
		"ng:textures/item/gem/cook_charcoal.png", "textures/item/charcoal.png");

	/** 바꿀 그림이면 대신 쓸 minecraft 그림 경로, 아니면 null. */
	public static String replacement(String namespace, String path) {
		return REPLACE.get(namespace + ":" + path);
	}
}
