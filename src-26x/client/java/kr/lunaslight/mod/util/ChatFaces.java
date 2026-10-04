package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 49-81차(4-48): <b>채팅 얼굴</b> - 채팅 줄 앞에 그 사람 머리(스킨 얼굴 8×8)를 붙인다.
 *
 * <h3>어떻게 - 줄 위치를 다시 계산하지 않는다</h3>
 * 채팅 줄이 화면 어디에 그려지는지는 버전마다 다르게 계산되고(스크롤·페이드·줄 간격·1.21.11의 Backend 개편),
 * 그걸 흉내 내면 한 줄만 어긋나도 얼굴이 엉뚱한 줄에 붙는다. 대신 <b>메시지 자체에 자리를 심는다</b>:
 * <ol>
 *   <li>메시지가 채팅에 들어올 때({@code ChatHudMixin}) 보낸 사람을 알아내면, 맨 앞에 <b>띄우개 글자</b>
 *       {@code U+E0F0}을 붙인다. 이 글자는 <b>아무것도 안 그리고 10px만 차지</b>한다:
 *       1.20+는 {@code lunaslight:chatface} 글꼴("space" 프로바이더), 1.16~1.19.4는 {@code chatface_bmp}
 *       (비트맵 9×8 - "space"가 없는 시대라, 맨 오른쪽 아래 픽셀 하나를 알파 1/255로 찍어 폭만 9로 잰다.
 *       그 픽셀은 글자 셰이더·알파 테스트가 버린다). 그 Style의 insertion(보이지 않는 꼬리표)에
 *       {@code luna-face:<이름>}을 넣는다.</li>
 *   <li>바닐라가 줄을 그릴 때(1.20~1.21.10: {@code DrawContext#drawTextWithShadow(OrderedText)},
 *       1.21.11+: {@code ChatHud$Hud#text}, 1.16~1.19.4: {@code ChatHud#render} 안의
 *       {@code TextRenderer#drawWithShadow(MatrixStack, OrderedText|StringRenderable, …)} Redirect)
 *       그 줄의 <b>첫 글자</b>가 띄우개면 꼬리표에서 이름을 꺼내 띄우개 자리에 얼굴을 그린다.
 *       줄이 길어 접히면 둘째 줄부터는 띄우개가 없으니 얼굴도 없다.</li>
 * </ol>
 * 그래서 줄 목록·스크롤·페이드를 하나도 안 건드린다 - 바닐라가 어디에 그리든 얼굴은 그 줄 앞에 붙는다.
 *
 * <p><b>49-208차</b>: 띄우개는 이제 줄 맨 앞이 아니라 <b>메시지 안 플레이어 이름마다 그 앞</b>에 끼운다
 * ({@link #decorate}). 줄 훅은 첫 글자만이 아니라 줄 전체를 훑어 띄우개마다 앞 글자 폭만큼 옮겨 얼굴을 그린다
 * ({@code ChatFaceLines}, compat/chatface-legacy). 1.16 · 1.16.1(compat/chatface-legacy16)은 예전처럼 줄 맨 앞 하나.
 *
 * <h3>보낸 사람은 어떻게 아나</h3>
 * 바닐라 채팅은 번역 텍스트 {@code chat.type.text} = [보낸사람, 내용]이라 0번 인자가 답이다. 서버가 통째로
 * 꾸민 줄(EssentialsChat 등, 번역 키 없음)은 보낸 사람 칸이 없으므로 <b>탭 목록 이름이 줄 앞쪽에 그대로
 * 나오는지</b>로 찾는다(가장 앞에 나오는 이름, 같은 자리면 긴 이름). 못 찾으면 얼굴 없이 그대로 - 지어내지 않는다.
 * 얼굴은 탭 목록의 스킨이라 그 사람이 탭 목록에 없어도 얼굴이 없다.
 *
 * <h3>버전</h3>
 * 1.16 ~ 최신. 49-81차엔 1.20+만이었는데(shim DrawContext에 drawTexture가 없고 "space" 글꼴도 없다고),
 * 49-82차에 shim에 drawTexture를 넣고(compat/legacy-era0|1) 띄우개를 비트맵으로 바꿔 1.16까지 내렸다.
 * <b>1.15.2만 안 된다</b>: 채팅 줄이 String이라 Style(꼬리표)이 붙을 자리가 없고 withFont도 없다 → 카드를 잠근다.
 */
public final class ChatFaces {
	private ChatFaces() {
	}

	public static final int SPACER = 0xE0F0;
	public static final String TAG = "luna-face:";
	public static final int FACE = 8;
	public static final int ADVANCE = 10;

	/** 모듈이 켜져 있고 이 버전에서 되는가. 모듈이 세팅한다 - 꺼져 있으면 채팅 한 줄당 비용 0. */
	public static volatile boolean enabled;

	private static Object spacerStyleBase;   // 글꼴만 입힌 Style(insertion은 메시지마다)
	private static boolean spacerResolved;

	/** 띄우개가 한 번이라도 붙었는가. 아니면 줄 훅이 글자를 훑지 않는다(1.20~1.21.10은 화면의 모든 글자 그리기를 지나간다). */
	public static volatile boolean anyDecorated;

	/**
	 * 49-208차(사용자: "얼굴을 플레이어 이름 바로 앞에, 한 줄에 여러 명이면 사람마다"): 메시지 안의 <b>플레이어 이름마다</b>
	 * 그 바로 앞에 띄우개를 끼운다. 이름이 {@code <이름>}처럼 괄호에 싸여 있으면 괄호 앞에 끼운다.
	 * 이름 목록은 탭 목록 이름(3자 이상) + 번역 키에서 찾은 보낸 사람. 1.16 · 1.16.1은 줄 훅이 첫 글자만 보므로
	 * 예전처럼 줄 맨 앞에 하나만 붙인다. 못 끼우면(이름이 글자로 안 나옴 등) 보낸 사람 얼굴을 줄 맨 앞에.
	 */
	public static Component decorate(Component message) {
		if (!enabled || message == null) {
			return message;
		}
		try {
			if (!resolveSpacer()) {
				return message;
			}
			String sender = senderOf(message);
			if (LunaVersion.isWithin("1.16.2", null)) {
				Component inline = decorateNames(message, sender);
				if (inline != null) {
					anyDecorated = true;
					return inline;
				}
			}
			if (sender == null) {
				return message;
			}
			Component spacer = spacerFor(sender);
			if (spacer == null) {
				return message;
			}
			Component joined = LunaCompat.joinTexts(spacer, message);
			if (joined == null) {
				return message;
			}
			anyDecorated = true;
			return joined;
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatFace:decorate", t);
			return message;
		}
	}

	private static boolean resolveSpacer() {
		if (!spacerResolved) {
			spacerResolved = true;
			try {
				// 1.20+: "space" 프로바이더 / 그 전: 비트맵(loom-common.gradle이 안 쓰는 쪽 json을 jar에서 뺀다)
				spacerStyleBase = LunaCompat.styleWithFontNamed(LunaVersion.isWithin("1.20", null) ? "chatface" : "chatface_bmp");
			} catch (Throwable t) {
				spacerStyleBase = null;
			}
		}
		return spacerStyleBase != null;
	}

	private static Component spacerFor(String name) {
		Object style = LunaCompat.styleWithInsertion(spacerStyleBase, TAG + name);
		return LunaCompat.styledText(new String(Character.toChars(SPACER)), style);
	}

	/** 이름마다 띄우개를 끼운 새 Component. 끼울 이름이 없거나 실패하면 null. 서버가 준 색·클릭/호버는 조각 Style로 그대로 산다. */
	private static Component decorateNames(Component message, String sender) {
		java.util.List<Object> styles = new java.util.ArrayList<>();
		java.util.List<String> texts = new java.util.ArrayList<>();
		if (!LunaCompat.visitStyled(message, (st, s) -> {
			styles.add(st);
			texts.add(s);
		}) || texts.isEmpty()) {
			return null;
		}
		StringBuilder all = new StringBuilder();
		for (String s : texts) {
			all.append(s);
		}
		String plain = all.toString();
		String lower = plain.toLowerCase(java.util.Locale.ROOT);

		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
		for (String n : LunaCompat.playerListNames(Minecraft.getInstance())) {
			if (n.length() >= 3) {
				names.add(n);   // 두 글자 이름은 아무 단어에나 걸린다
			}
		}
		if (sender != null && sender.matches("[A-Za-z0-9_]{1,16}")) {
			names.add(sender);
		}
		if (names.isEmpty()) {
			return null;
		}
		java.util.TreeMap<Integer, String> at = new java.util.TreeMap<>();
		for (String n : names) {
			String ln = n.toLowerCase(java.util.Locale.ROOT);
			for (int i = lower.indexOf(ln); i >= 0; i = lower.indexOf(ln, i + 1)) {
				if (isWordAt(lower, i, ln.length())) {
					at.putIfAbsent(anchorOf(plain, i), n);
				}
			}
		}
		if (at.isEmpty()) {
			return null;
		}

		java.util.List<Component> parts = new java.util.ArrayList<>();
		java.util.Iterator<java.util.Map.Entry<Integer, String>> it = at.entrySet().iterator();
		java.util.Map.Entry<Integer, String> next = it.next();
		int pos = 0;
		for (int c = 0; c < texts.size(); c++) {
			String s = texts.get(c);
			Object st = styles.get(c);
			int end = pos + s.length();
			int cut = 0;
			String codes = "";
			while (next != null && next.getKey() < end) {
				int k = Math.max(cut, next.getKey() - pos);
				String seg = s.substring(cut, k);
				if (!seg.isEmpty() && !addPiece(parts, codes + seg, st)) {
					return null;
				}
				Component spacer = spacerFor(next.getValue());
				if (spacer == null) {
					return null;
				}
				parts.add(spacer);
				codes = PlayerStateHook.activeCodes(s.substring(0, k));   // 잘린 뒤쪽도 앞쪽 § 색을 이어받게
				cut = k;
				next = it.hasNext() ? it.next() : null;
			}
			String rest = s.substring(cut);
			if (!rest.isEmpty() && !addPiece(parts, codes + rest, st)) {
				return null;
			}
			pos = end;
		}
		return LunaCompat.joinTexts(parts.toArray(new Component[0]));
	}

	private static boolean addPiece(java.util.List<Component> parts, String content, Object style) {
		Component t = LunaCompat.styledLiteral(content, style);
		if (t == null) {
			return false;
		}
		parts.add(t);
		return true;
	}

	/** lower의 i에서 길이 len짜리가 낱말로 나오는가(앞뒤가 영문/숫자/_가 아님, 앞이 § 코드면 통과). */
	private static boolean isWordAt(String lower, int i, int len) {
		boolean okL = i == 0 || !isNameChar(lower.charAt(i - 1)) || (i >= 2 && lower.charAt(i - 2) == '\u00a7');
		int e = i + len;
		boolean okR = e >= lower.length() || !isNameChar(lower.charAt(e));
		return okL && okR;
	}

	private static boolean isNameChar(char c) {
		return c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z');
	}

	/** 띄우개 자리: 이름 바로 앞이 여는 괄호(<, [, ()면 괄호 앞, 아니면 이름 앞. 사이의 § 코드는 건너뛰고 본다. */
	private static int anchorOf(String plain, int i) {
		int j = i;
		while (j >= 2 && plain.charAt(j - 2) == '\u00a7') {
			j -= 2;
		}
		if (j >= 1 && "<[(".indexOf(plain.charAt(j - 1)) >= 0) {
			return j - 1;
		}
		return i;
	}

	/** 보낸 사람 이름. 번역 키 → 탭 목록 이름 순. 모르면 null. (49-83차: 멘션 알림도 같은 판단을 쓴다 - public) */
	public static String senderOf(Component message) {
		try {
			for (LunaCompat.TranslatablePart part : LunaCompat.translatableParts(message)) {
				String key = part.key();
				if (key == null || !(key.startsWith("chat.type.text") || key.startsWith("chat.type.emote")
						|| key.startsWith("chat.type.team"))) {
					continue;
				}
				Object[] args = part.args();
				if (args != null && args.length > 0) {
					String n = plain(args[0]);
					if (n != null && !n.isEmpty()) {
						return n;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		// 서버가 꾸민 줄: 탭 목록 이름이 앞쪽 48자 안에 통째로 나오면 그 사람
		try {
			String plain = message.getString();
			if (plain == null || plain.isEmpty()) {
				return null;
			}
			String head = plain.length() > 48 ? plain.substring(0, 48) : plain;
			String best = null;
			int bestAt = Integer.MAX_VALUE;
			for (String name : LunaCompat.playerListNames(Minecraft.getInstance())) {
				if (name.length() < 3) {
					continue;   // 두 글자 이름은 아무 단어에나 걸린다
				}
				int at = indexOfWord(head, name);
				if (at >= 0 && (at < bestAt || (at == bestAt && best != null && name.length() > best.length()))) {
					best = name;
					bestAt = at;
				}
			}
			return best;
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** 앞뒤가 글자·숫자가 아닌 자리에서 name이 나오는 첫 위치. */
	private static int indexOfWord(String text, String name) {
		int from = 0;
		while (true) {
			int at = text.indexOf(name, from);
			if (at < 0) {
				return -1;
			}
			boolean okBefore = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
			int end = at + name.length();
			boolean okAfter = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
			if (okBefore && okAfter) {
				return at;
			}
			from = at + 1;
		}
	}

	private static String plain(Object arg) {
		if (arg instanceof String s) {
			return s.trim();
		}
		if (arg instanceof Component t) {
			try {
				String s = t.getString();
				return s == null ? null : s.trim();
			} catch (Throwable ignored) {
				return null;
			}
		}
		return arg == null ? null : String.valueOf(arg).trim();
	}

	// ==================== 그리기(줄 훅이 부른다) ====================

	/** 꼬리표에서 이름을 꺼낸다. 띄우개 줄이 아니면 null. */
	public static String nameFromInsertion(String insertion) {
		return insertion != null && insertion.startsWith(TAG) && insertion.length() > TAG.length()
			? insertion.substring(TAG.length()) : null;
	}

	/** (x, y)에 그 사람 얼굴 8×8. alpha는 그 줄의 글자 투명도(0~255). */
	public static void draw(net.minecraft.client.gui.GuiGraphicsExtractor ctx, String name, int x, int y, int alpha) {
		if (!enabled || ctx == null || name == null) {
			return;
		}
		try {
			Identifier skin = LunaCompat.playerSkinByName(Minecraft.getInstance(), name);
			if (skin == null) {
				return;
			}
			int a = Math.max(0, Math.min(255, alpha));
			if (a < 8) {
				return;   // 다 사라진 줄에는 그리지 않는다(바닐라도 글자를 안 그린다)
			}
			kr.lunaslight.mod.gui.LunaGfx.drawPlayerFace(ctx, skin, x, y, FACE, (a << 24) | 0xFFFFFF);
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatFace:draw", t);
		}
	}

	/** 미리보기용: 내 얼굴 + "<이름> 내용" 한 줄. */
	public static void drawSample(net.minecraft.client.gui.GuiGraphicsExtractor ctx, Minecraft client, int x, int y, String line) {
		try {
			Identifier skin = LunaCompat.playerSkinTexture(client);
			if (skin != null) {
				kr.lunaslight.mod.gui.LunaGfx.drawPlayerFace(ctx, skin, x, y, FACE, 0xFFFFFFFF);
			}
			LunaCompat.drawHudText(ctx, client.font, line, x + ADVANCE, y, 0xFFFFFFFF);
		} catch (Throwable ignored) {
		}
	}
}
