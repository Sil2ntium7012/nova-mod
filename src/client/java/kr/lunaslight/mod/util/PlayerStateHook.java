package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.AfkModule;
import kr.lunaslight.mod.module.impl.render.CapeSmoothModule;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 49-201차: 플레이어 렌더 상태를 만든 직후(PlayerRenderStateMixin, 1.21.2+)에 두 가지를 얹는다.
 * <ul>
 *   <li>망토 흔들림을 부드럽게(CapeSmoothModule) - 상태의 망토 각도 세 값을 시간에 따라 천천히 따라가게.</li>
 *   <li>자리 비움인 루나 유저의 머리 위 이름 뒤에 회색 "Zzz"(AfkModule + LunaSocial 접속 정보).</li>
 * </ul>
 * 상태 클래스가 버전마다 달라(1.21.2+ PlayerEntityRenderState) 필드는 이름으로 찾는다. 망토 필드는 야른 이름이 없어
 * 중간 이름(field_53536~8)이다. 못 찾으면 조용히 아무것도 안 한다.
 */
public final class PlayerStateHook {
	private PlayerStateHook() {
	}

	private static final class Cape {
		final float[] v = new float[3];
		long t;
	}

	private static final Map<Entity, Cape> CAPES = new java.util.IdentityHashMap<>();   // 옛 버전은 getId 이름이 달라 개체 자체로
	private static int calls;
	private static Class<?> capeClass;
	private static Field[] capeFields;
	private static Class<?> nameClass;
	private static Field nameField;

	public static void after(Object entity, Object state) {
		if (!(entity instanceof Entity e) || state == null) {
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
			zzz(e, state);
		} catch (Throwable t) {
			LunaCompat.warnOnce("afkZzz", t);
		}
	}

	private static Field[] capeFieldsOf(Class<?> c) {
		if (capeClass != c) {
			capeClass = c;
			Field[] f = new Field[3];
			String[][] names = {{"field_53536", "capeFlap"}, {"field_53537", "capeLean"}, {"field_53538", "capeLean2"}};
			for (int i = 0; i < 3; i++) {
				for (String n : names[i]) {
					Field x = LunaCompat.findField(c, n);
					if (x != null && x.getType() == float.class) {
						x.setAccessible(true);
						f[i] = x;
						break;
					}
				}
			}
			capeFields = f[0] != null && f[1] != null && f[2] != null ? f : null;
		}
		return capeFields;
	}

	private static void smoothCape(Entity e, Object state) throws Exception {
		Field[] f = capeFieldsOf(state.getClass());
		if (f == null) {
			return;
		}
		long now = System.nanoTime();
		Cape c = CAPES.get(e);
		float[] target = {f[0].getFloat(state), f[1].getFloat(state), f[2].getFloat(state)};
		if (c == null || now - c.t > 500_000_000L) {
			if (c == null) {
				c = new Cape();
				CAPES.put(e, c);
			}
			System.arraycopy(target, 0, c.v, 0, 3);
			c.t = now;
		} else if (now - c.t > 500_000L) {   // 같은 프레임에 두 번 불리면(상속 다리 메서드) 한 번만 움직인다
			float dt = (now - c.t) / 1_000_000_000f;
			float k = 1f - (float) Math.exp(-CapeSmoothModule.rate() * dt);
			for (int i = 0; i < 3; i++) {
				c.v[i] += (target[i] - c.v[i]) * k;
			}
			c.t = now;
		}
		for (int i = 0; i < 3; i++) {
			f[i].setFloat(state, c.v[i]);
		}
		if (++calls % 600 == 0) {
			CAPES.values().removeIf(x -> now - x.t > 10_000_000_000L);
		}
	}

	private static Field nameFieldOf(Class<?> c) {
		if (nameClass != c) {
			nameClass = c;
			Field x = LunaCompat.findField(c, "displayName");
			if (x == null) {
				x = LunaCompat.findField(c, "nameTag");
			}
			if (x != null) {
				x.setAccessible(true);
			}
			nameField = x;
		}
		return nameField;
	}

	/** 머리 위 이름 뒤에 "Zzz"(자리 비움인 루나 유저, 또는 나 자신이 자리 비움일 때). */
	private static void zzz(Entity e, Object state) throws Exception {
		String name = e.getName() == null ? null : e.getName().getString();
		if (!isAfk(e, name)) {
			return;
		}
		Field f = nameFieldOf(state.getClass());
		if (f == null) {
			return;
		}
		Object cur = f.get(state);
		if (!(cur instanceof Text t) || t.getString().endsWith(" Zzz")) {
			return;
		}
		f.set(state, LunaCompat.join(t, LunaCompat.coloredText(" Zzz", 0xAAAAAA)));
	}

	/** 이 플레이어가 자리 비움인가(나 자신은 이 컴퓨터의 자리 비움 상태, 남은 루나 접속 정보). */
	public static boolean isAfk(Object entity, String name) {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		if (mc != null && entity != null && entity == mc.player) {
			return AfkModule.isAfkNow();
		}
		return LunaSocial.isAfkLunaPlayer(name);
	}

	/**
	 * 49-203차(사용자: "별 모양이 이름 바로 앞에 있어야지, 이름을 한 칸 뒤로"): 탭리스트 이름에 서버가 붙인 칭호/접두가
	 * 있으면 루나 별을 맨 앞이 아니라 <b>닉네임 바로 앞</b>에 끼우고, 별과 닉네임 사이를 한 칸 띄운다.
	 * 서버가 준 조각별 스타일(색, 클릭/호버)은 그대로 두고, 한 조각 안에 § 색 코드가 섞여 있으면 자른 뒤쪽에 이어 붙인다.
	 * 닉네임을 못 찾거나 맨 앞이면 null(호출부가 예전처럼 맨 앞에 붙인다).
	 */
	public static Text badgeBeforeName(Text original, String name, Text badge) {
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
			java.util.List<Text> parts = new java.util.ArrayList<>();
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
			return LunaCompat.join(parts.toArray(new Text[0]));
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
