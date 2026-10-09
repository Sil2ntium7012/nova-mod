package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.CapeSmoothModule;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 49-201차(26.x 판): 플레이어 렌더 상태를 만든 직후(PlayerRenderStateMixin)에 - 망토 흔들림 부드럽게(CapeSmoothModule),
 * 자리 비움이면 고개, 망토, 머리 위 AFK(49-312차). 26.x는 필드 이름이 그대로 보여(capeFlap, capeLean, capeLean2,
 * nameTag) 리플렉션 없이 쓴다.
 */
public final class PlayerStateHook {
	private PlayerStateHook() {
	}

	private static final class Cape {
		final float[] v = new float[3];
		long t;
	}

	private static final Map<Entity, Cape> CAPES = new IdentityHashMap<>();
	private static int calls;

	public static void after(Entity e, AvatarRenderState state) {
		if (e == null || state == null) {
			return;
		}
		try {
			if (CapeSmoothModule.on()) {
				smoothCape(e, state);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("capeSmooth", t);
		}
		try {
			NovaCapes.applyTo(e, state);   // 49-240차: 노바 망토
		} catch (Throwable t) {
			LunaCompat.warnOnce("novaCape", t);
		}
		try {
			afkLook(e, state);
		} catch (Throwable t) {
			LunaCompat.warnOnce("afkLook", t);
		}
	}

	private static void smoothCape(Entity e, AvatarRenderState state) {
		long now = System.nanoTime();
		Cape c = CAPES.get(e);
		float[] target = {state.capeFlap, state.capeLean, state.capeLean2};
		if (c == null || now - c.t > 500_000_000L) {
			if (c == null) {
				c = new Cape();
				CAPES.put(e, c);
			}
			System.arraycopy(target, 0, c.v, 0, 3);
			c.t = now;
		} else if (now - c.t > 500_000L) {
			float dt = (now - c.t) / 1_000_000_000f;
			float k = 1f - (float) Math.exp(-CapeSmoothModule.rate() * dt);
			for (int i = 0; i < 3; i++) {
				c.v[i] += (target[i] - c.v[i]) * k;
			}
			c.t = now;
		}
		state.capeFlap = c.v[0];
		state.capeLean = c.v[1];
		state.capeLean2 = c.v[2];
		if (++calls % 600 == 0) {
			CAPES.values().removeIf(x -> now - x.t > 10_000_000_000L);
		}
	}

	/**
	 * 49-312차(사용자: "AFK가 되면 고개 떨구기 + 머리 위 AFK 표시 + 날개, 망토 같은 치장은 안 보이게. 내 화면이랑 남들 화면 둘 다",
	 * "같은 행동을 반복하고 있으면 고개는 떨구지 말고"): AFK면 망토를 끄고(바닐라 망토 포함 - 노바 망토는 NovaCapes가 이미 뺐다)
	 * 이름표 뒤에 회색 AFK, 잠듦이면 고개를 아래로. 판단은 {@link AfkWatch}.
	 */
	private static void afkLook(Entity e, AvatarRenderState state) {
		String name = e.getName() == null ? null : e.getName().getString();
		if (!AfkWatch.afk(e, name)) {
			return;
		}
		state.showCape = false;
		if (AfkWatch.droop(e, name)) {
			state.xRot = AfkWatch.DROOP_PITCH;
		}
		// 49-317차: 서버 점수 줄이 없으면 AFK를 이름 아래 따로 한 줄로(머리 바로 위 - 이름과 섞이지 않게), 있으면 이름 뒤에 붙인다
		if (state.nameTag != null && state.scoreText == null) {
			state.scoreText = AfkWatch.tag();
		} else if (state.nameTag != null) {
			state.nameTag = AfkWatch.label(state.nameTag);
		}
	}

	/** 이 플레이어가 자리 비움인가(49-312차: AfkWatch로). */
	public static boolean isAfk(Object entity, String name) {
		return AfkWatch.afk(entity, name);
	}

	/**
	 * 49-203차(사용자: "별 모양이 이름 바로 앞에 있어야지, 이름을 한 칸 뒤로"): 탭리스트 이름에 서버가 붙인 칭호/접두가
	 * 있으면 루나 별을 맨 앞이 아니라 <b>닉네임 바로 앞</b>에 끼우고, 별과 닉네임 사이를 한 칸 띄운다.
	 * 서버가 준 조각별 스타일(색, 클릭/호버)은 그대로 두고, 한 조각 안에 § 색 코드가 섞여 있으면 자른 뒤쪽에 이어 붙인다.
	 * 닉네임을 못 찾거나 맨 앞이면 null(호출부가 예전처럼 맨 앞에 붙인다).
	 */
	public static Component badgeBeforeName(Component original, String name, Component badge) {
		if (original == null || name == null || name.isEmpty() || badge == null) {
			return null;
		}
		try {
			java.util.List<Object> styles = new java.util.ArrayList<>();
			java.util.List<String> texts = new java.util.ArrayList<>();
			if (!LunaCompat.visitStyled(original, (st, s) -> {
				styles.add(st);
				texts.add(s);
			}) || texts.isEmpty()) {
				return null;
			}
			StringBuilder all = new StringBuilder();
			for (String s : texts) {
				all.append(s);
			}
			int at = findName(all.toString(), name);
			if (at <= 0) {
				return null;
			}
			java.util.List<Component> parts = new java.util.ArrayList<>();
			int pos = 0;
			boolean inserted = false;
			for (int i = 0; i < texts.size(); i++) {
				String s = texts.get(i);
				Object st = styles.get(i);
				int end = pos + s.length();
				if (!inserted && at >= pos && at < end) {
					int cut = at - pos;
					String head = s.substring(0, cut);
					if (!head.isEmpty()) {
						parts.add(LunaCompat.styledLiteral(head, st));
					}
					parts.add(badge);
					parts.add(LunaCompat.textLiteral(" "));
					parts.add(LunaCompat.styledLiteral(activeCodes(head) + s.substring(cut), st));
					inserted = true;
				} else {
					parts.add(LunaCompat.styledLiteral(s, st));
				}
				pos = end;
			}
			if (!inserted) {
				return null;
			}
			return LunaCompat.join(parts.toArray(new Component[0]));
		} catch (Throwable t) {
			LunaCompat.warnOnce("badgeBeforeName", t);
			return null;
		}
	}

	/** 닉네임 위치(대소문자 무시). 앞뒤가 영문/숫자/_가 아닌 자리를 먼저 찾고, 없으면 처음 나온 자리. 없으면 -1. */
	private static int findName(String plain, String name) {
		String lp = plain.toLowerCase(java.util.Locale.ROOT);
		String ln = name.toLowerCase(java.util.Locale.ROOT);
		int first = -1;
		for (int i = lp.indexOf(ln); i >= 0; i = lp.indexOf(ln, i + 1)) {
			if (first < 0) {
				first = i;
			}
			boolean okL = i == 0 || !isNameChar(lp.charAt(i - 1)) || (i >= 2 && lp.charAt(i - 2) == '§');
			int e = i + ln.length();
			boolean okR = e >= lp.length() || !isNameChar(lp.charAt(e));
			if (okL && okR) {
				return i;
			}
		}
		return first;
	}

	private static boolean isNameChar(char c) {
		return c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z');
	}

	/** 앞 조각 끝에서 살아 있는 § 코드(마지막 색 + 그 뒤 굵게/기울임 등). 색이나 §r이 나오면 서식은 초기화. */
	static String activeCodes(String head) {   // 49-208차: ChatFaces도 쓴다
		String color = "";
		StringBuilder fmt = new StringBuilder();
		for (int i = 0; i + 1 < head.length(); i++) {
			if (head.charAt(i) != '§') {
				continue;
			}
			char c = Character.toLowerCase(head.charAt(i + 1));
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
				color = "§" + c;
				fmt.setLength(0);
			} else if (c == 'r') {
				color = "";
				fmt.setLength(0);
			} else if (c >= 'k' && c <= 'o') {
				fmt.append('§').append(c);
			}
			i++;
		}
		return color + fmt;
	}
}
