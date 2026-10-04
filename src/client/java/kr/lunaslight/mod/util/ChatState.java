package kr.lunaslight.mod.util;

/**
 * 49-21차: 채팅 강화 모듈(ChatEnhancementsModule)이 갱신하고 ChatHudMixin이 읽는 정적 상태.
 * 채팅 모듈은 1.16~1.19.2 빌드에서 컴파일 제외(no_message_events)되므로 믹스인이 모듈 클래스를
 * 직접 참조하면 그 버전들이 깨진다 - ZoomState와 같은 이유로 분리.
 */
public final class ChatState {
	private ChatState() {
	}

	/**
	 * 49-53차(1-1): 발전 과제 달성 메시지를 채팅에 안 띄운다.
	 * 판단은 <b>번역 키</b>로 한다({@code chat.type.advancement.task/challenge/goal}) - 보이는 글자로
	 * 맞추면 언어를 바꾸는 순간 안 먹으므로.
	 */
	public static volatile boolean hideAdvancements = false;

	/**
	 * 49-54차(1-5): 이 단어가 들어간 채팅 줄을 버린다. 소문자로 미리 잘라 둔 배열이고, 쓸 게 없으면 null.
	 * 설정 문자열을 메시지마다 쪼개면 낭비라, 모듈이 설정이 바뀔 때만 만들어 넣는다.
	 */
	public static volatile String[] blockedWords;

	/** 49-54차: 설정 문자열("광고, 홍보") → 소문자 배열. 쓸 게 없으면 null. */
	public static String[] parseWords(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		java.util.List<String> out = new java.util.ArrayList<>(4);
		for (String w : raw.split(",")) {
			String t = w.trim().toLowerCase(java.util.Locale.ROOT);
			if (!t.isEmpty() && !out.contains(t)) {
				out.add(t);
			}
		}
		return out.isEmpty() ? null : out.toArray(new String[0]);
	}

	/**
	 * 49-58차(1-6): 차단한 사람. 소문자로 잘라 둔 이름 배열이고, 없으면 null.
	 */
	public static volatile String[] blockedPlayers;
	/** 번역 키가 없는(서버가 직접 꾸민) 줄에서도 이름만 보이면 숨길지. 기본 꺼짐 - 남이 그 이름을 말해도 사라지므로. */
	public static volatile boolean blockAnyLine;

	/**
	 * 49-58차: 이 메시지를 <b>차단한 사람이 보낸 것</b>으로 볼지.
	 *
	 * <p>바닐라 채팅·귓속말은 번역 텍스트라서 <b>보낸 사람이 0번 인자</b>에 따로 들어 있다
	 * ({@code chat.type.text} = [보낸사람, 내용], {@code commands.message.display.incoming} = [보낸사람, 내용]).
	 * 그래서 0번만 본다 - 내용까지 보면 남이 그 이름을 말하기만 해도 줄이 사라진다.
	 *
	 * <p>서버가 직접 꾸민 줄(번역 키가 없는 것)은 보낸 사람을 알 방법이 없다. 그건 [이름이 든 줄 전부]를
	 * 켰을 때만 글자 비교로 숨긴다 - 기본은 꺼 둔다(위 이유 그대로).
	 */
	// ==================== 49-133차: 들어온 채팅 줄 구독(글자만) ====================

	/** 들어온 채팅 줄을 색 코드를 뺀 글자로 받는 구독자들(너굴 추천 핫타임 등). 비어 있으면 비용 0. */
	private static final java.util.List<java.util.function.Consumer<String>> PLAIN_LISTENERS =
			new java.util.concurrent.CopyOnWriteArrayList<>();

	public static void addPlainListener(java.util.function.Consumer<String> listener) {
		if (listener != null) {
			PLAIN_LISTENERS.add(listener);
		}
	}

	/** §코드 제거. */
	public static String stripFormatting(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\u00A7' && i + 1 < s.length()) {
				i++;
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	// ==================== 49-67차(5-5): 귓속말 받음 알림 ====================

	/** 마지막으로 귓속말을 보낸 사람(없으면 null). WhisperAlertModule이 읽어 간다. */
	public static volatile String lastWhisperFrom;
	/** 그 귓속말이 온 시각. */
	public static volatile long lastWhisperAtMs;
	/** 알림이 떠 있는 동안 같은 사람에게 더 온 횟수(0이면 한 번). */
	public static volatile int lastWhisperRepeat;
	/** 알림 기능이 켜져 있을 때만 훑는다(꺼져 있으면 채팅 한 줄당 비용 0). */
	public static volatile boolean watchWhispers;

	/**
	 * 49-67차(5-5): 들어온 줄이 <b>나에게 온 귓속말</b>이면 보낸 사람을 적어 둔다.
	 *
	 * <p>판단은 {@link #isBlockedSender}와 같은 근거를 쓴다 - 바닐라 귓속말은 번역 키
	 * {@code commands.message.display.incoming}이고 <b>0번 인자가 보낸 사람</b>이다.
	 * 내가 보낸 귓속말({@code ...outgoing})은 당연히 알림 대상이 아니다.
	 *
	 * <p>서버가 직접 꾸민 귓속말(번역 키 없음)은 <b>알 방법이 없어서 건드리지 않는다</b> -
	 * "[귓속말]" 같은 글자를 넣은 줄을 전부 귓속말로 치면 일반 채팅에도 알림이 뜬다.
	 */
	public static void observe(net.minecraft.text.Text message) {
		if (message != null && !PLAIN_LISTENERS.isEmpty()) {
			try {
				String plain = stripFormatting(message.getString());
				for (java.util.function.Consumer<String> l : PLAIN_LISTENERS) {
					try {
						l.accept(plain);
					} catch (Throwable t) {
						kr.lunaslight.mod.util.LunaCompat.warnOnce("chatListener", t);
					}
				}
			} catch (Throwable ignored) {
			}
		}
		if (!watchWhispers || message == null) {
			return;
		}
		try {
			for (kr.lunaslight.mod.util.LunaCompat.TranslatablePart part
					: kr.lunaslight.mod.util.LunaCompat.translatableParts(message)) {
				String key = part.key();
				if (key == null || !key.startsWith("commands.message.display.incoming")) {
					continue;
				}
				Object[] args = part.args();
				String from = args != null && args.length > 0 ? plainOf(args[0]) : null;
				if (from == null || from.isEmpty()) {
					from = "누군가";
				}
				long now = System.currentTimeMillis();
				// "짧은 시간에 여러 번 와도 1번만": 같은 사람이 알림이 떠 있는 동안 더 보내면
				// 새 알림을 띄우지 않고 횟수만 올린다(×2, ×3 …).
				if (from.equals(lastWhisperFrom) && now - lastWhisperAtMs < REPEAT_WINDOW_MS) {
					lastWhisperRepeat++;
				} else {
					lastWhisperFrom = from;
					lastWhisperRepeat = 0;
				}
				lastWhisperAtMs = now;
				return;
			}
		} catch (Throwable ignored) {
		}
	}

	/** 같은 사람의 연속 귓속말을 한 알림으로 묶는 시간. 49-83차: 멘션 알림(MessageColorizerModule)도 같은 값을 쓴다. */
	public static final long REPEAT_WINDOW_MS = 8000;

	// ==================== 49-83차(4-21): 알림 카드 자리 나눠 쓰기 ====================
	/**
	 * 귓속말 알림 카드가 <b>이번 프레임에</b> 그려진 자리(x, y, w, h)와 그 시각. 멘션 알림 카드는 기본 자리가
	 * 같으므로(오른쪽 위 스택엔 빈 칸이 없다) 귓속말 카드가 떠 있으면 그 바로 아래로 비켜 그린다.
	 * 둘 다 잠깐 뜨는 카드라 겹치는 순간 자체가 드물지만, 겹치면 둘 다 못 읽는다.
	 */
	public static volatile int whisperCardX, whisperCardY, whisperCardW, whisperCardH;
	public static volatile long whisperCardAtMs;

	public static boolean isBlockedSender(net.minecraft.text.Text message) {
		String[] names = blockedPlayers;
		if (names == null || message == null) {
			return false;
		}
		try {
			boolean sawKey = false;
			for (kr.lunaslight.mod.util.LunaCompat.TranslatablePart part
					: kr.lunaslight.mod.util.LunaCompat.translatableParts(message)) {
				String key = part.key();
				if (key == null || !(key.startsWith("chat.type.") || key.startsWith("commands.message.display"))) {
					continue;
				}
				sawKey = true;
				Object[] args = part.args();
				if (args != null && args.length > 0 && matches(plainOf(args[0]), names)) {
					return true;
				}
			}
			if (!sawKey && blockAnyLine) {
				return matches(message.getString(), names);
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	private static boolean matches(String value, String[] names) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		String low = value.toLowerCase(java.util.Locale.ROOT);
		for (String n : names) {
			if (low.contains(n)) {
				return true;
			}
		}
		return false;
	}

	/** 번역 인자 하나를 글자로(문자열이거나 Text다). */
	private static String plainOf(Object arg) {
		if (arg == null) {
			return null;
		}
		if (arg instanceof String s) {
			return s;
		}
		if (arg instanceof net.minecraft.text.Text t) {
			try {
				return t.getString();
			} catch (Throwable ignored) {
				return null;
			}
		}
		return String.valueOf(arg);
	}

	/** 이 메시지를 채팅에 아예 안 넣을지. 믹스인이 메시지마다 부른다. */
	public static boolean shouldDrop(net.minecraft.text.Text message) {
		if (message == null) {
			return false;
		}
		try {
			if (hideAdvancements) {
				String key = kr.lunaslight.mod.util.LunaCompat.translationKeyOf(message);
				if (key != null && key.startsWith("chat.type.advancement")) {
					return true;
				}
			}
			if (isBlockedSender(message)) {
				return true;
			}
			String[] words = blockedWords;   // 한 번만 읽는다(판단 중에 다른 스레드가 바꿔도 흔들리지 않게)
			if (words != null) {
				String plain = message.getString().toLowerCase(java.util.Locale.ROOT);
				for (String w : words) {
					if (plain.contains(w)) {
						return true;
					}
				}
			}
			if (tidy && isClutter(message.getString())) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	// ==================== 49-76차(6-20): 반복 줄 "×N" 합치기 + 재진입 버그 수정 ====================
	//
	// ⚠️ 49-69차의 반복 숨기기에 **숨은 버그**가 있었다. 1.19.1+에서는 addMessage(Text)가 안에서
	// addMessage(Text, MessageSignatureData, MessageIndicator)를 다시 부른다(1.21.1 바이트코드 실측).
	// 두 입구에 다 믹스인이 걸려 있어서 **같은 줄이 shouldDrop을 두 번 지나갔고**, 첫 번째에서 "최근 줄"에
	// 넣은 것을 두 번째가 "반복"으로 보고 **시스템 메시지를 전부 버렸다**. 사용자가 "내가 말한 것 중에
	// 안 된 게 꽤 있다"고 한 것 중 하나였을 가능성이 크다.
	//
	// 고친 것: 두 입구가 같은 Text 객체를 들고 오면 두 번째는 판단을 건너뛴다(inFlight).
	//
	// 그리고 이제는 반복을 **버리지 않고 "×N"으로 합친다**. 이미 화면에 그려진 줄을 고치는 방법은
	// 두 가지가 있었는데, ChatHud의 줄 목록을 수술하는 쪽(버전마다 필드 이름·개수가 다른 private 목록)은
	// 위험해서 버렸고, **먼저 나간 Text 객체의 siblings에 "×N"을 붙이고 ChatHud.reset()으로 다시 접게 하는**
	// 쪽을 골랐다. reset()은 1.15.2부터 1.21.11까지 전부 public이고(javap 실측), siblings는 1.15.2부터
	// 변경 가능한 ArrayList다. ChatHud는 우리가 넘긴 Text 객체를 **그대로** 들고 있으므로 그 객체를 고치면
	// reset() 뒤에 화면도 바뀐다.
	//
	// **플레이어 채팅은 건드리지 않는다**(사용자: "단 플레이어 채팅은 X"). 1.19.1+에서는 서명 입구로
	// 바로 들어오는 것, 그 아래 버전에서는 번역 키가 chat.type.text/emote/team인 것을 플레이어 채팅으로 본다.
	// 서버 플러그인이 채팅을 통째로 다시 꾸민 경우는 **구분할 방법이 없다** - 그런 줄도 합쳐진다.

	/** gate()의 답: 그대로 둔다 / 버린다 / 버리고 ChatHud.reset()을 불러 달라(앞 줄에 ×N을 붙였다). */
	public static final int PASS = 0, DROP = 1, DROP_AND_RESET = 2;

	private static net.minecraft.text.Text inFlight;
	private static net.minecraft.text.Text lastKept;
	private static String lastPlain;
	private static long lastAt;
	private static int repeat;
	private static net.minecraft.text.Text marker;
	private static long lastResetAt;

	/**
	 * 믹스인 두 입구가 부른다. signedEntry = 1.19.1+의 (Text, 서명, 표시) 입구.
	 */
	public static int gate(net.minecraft.text.Text message, boolean signedEntry) {
		if (message == null) {
			return PASS;
		}
		try {
			if (signedEntry && inFlight == message) {
				inFlight = null;               // 한 인자 입구에서 이미 판단한 줄이 안에서 다시 온 것
				return PASS;
			}
			if (shouldDrop(message)) {
				return DROP;
			}
			boolean playerChat = signedEntry || looksLikePlayerChat(message);
			if (tidy && tidyRepeat && !playerChat) {
				int r = mergeRepeat(message);
				if (r != PASS) {
					return r;
				}
			}
			if (!signedEntry) {
				inFlight = message;
			}
		} catch (Throwable ignored) {
		}
		return PASS;
	}

	private static boolean looksLikePlayerChat(net.minecraft.text.Text message) {
		String key = kr.lunaslight.mod.util.LunaCompat.translationKeyOf(message);
		return key != null && (key.startsWith("chat.type.text") || key.startsWith("chat.type.emote")
			|| key.startsWith("chat.type.team"));
	}

	/**
	 * 49-208차: 채팅에 실제로 들어간 Text가 원문과 다른 객체가 되면(얼굴 띄우개를 이름마다 끼우느라 조각을 다시 엮음)
	 * " ×N"을 원문에 붙여도 화면에 안 나온다 - 들어간 쪽으로 바꿔 쥔다.
	 */
	public static void replaceKept(net.minecraft.text.Text original, net.minecraft.text.Text stored) {
		if (original != null && stored != null && original != stored && lastKept == original) {
			lastKept = stored;
		}
	}

	/** 앞 줄과 같으면 앞 줄에 " ×N"을 붙이고 이 줄은 버린다. */
	private static int mergeRepeat(net.minecraft.text.Text message) {
		String plain = message.getString();
		if (plain == null) {
			return PASS;
		}
		plain = plain.trim();
		if (plain.isEmpty()) {
			return PASS;
		}
		long now = System.currentTimeMillis();
		long window = tidyRepeatSeconds * 1000L;
		if (lastKept != null && plain.equals(lastPlain) && now - lastAt <= window) {
			repeat++;
			lastAt = now;                      // 계속 오는 동안 창을 미뤄 준다
			try {
				net.minecraft.text.Text next = kr.lunaslight.mod.util.LunaCompat.textLiteral(" §7×" + repeat);
				java.util.List<net.minecraft.text.Text> sib = lastKept.getSiblings();
				try {
					if (marker != null) {
						sib.remove(marker);
					}
					sib.add(next);
				} catch (UnsupportedOperationException immutable) {
					// 49-77차(사용자: "합치기 작동을 안 해"): 1.20.3+는 서버에서 온 Text를 코덱으로 풀면서 siblings가
					// **불변 목록**(ImmutableList / List.of())으로 들어온다 - add()가 여기서 터져 지금까지 반복 줄을
					// 그냥 버리기만 했다(×N이 안 붙음). 목록을 통째로 변경 가능한 복사본으로 바꿔 끼운다.
					// MutableText.siblings는 final이지만 인스턴스 필드라 setAccessible 뒤 set이 된다(1.15.2의
					// BaseText까지 findField가 상위 클래스를 훑는다). 한 번 바꿔 끼우면 다음부터는 위 add()가 그냥 된다.
					java.util.List<net.minecraft.text.Text> copy = new java.util.ArrayList<>(sib);
					if (marker != null) {
						copy.remove(marker);
					}
					copy.add(next);
					java.lang.reflect.Field f = kr.lunaslight.mod.util.LunaCompat.findField(lastKept.getClass(), "siblings");
					if (f == null) {
						throw immutable;
					}
					f.set(lastKept, copy);
				}
				marker = next;
			} catch (Throwable t) {
				kr.lunaslight.mod.util.LunaCompat.warnOnce("chatTidy:merge", t);
				return DROP;                   // 못 붙였어도 반복 줄은 안 보여 준다(49-69차 동작)
			}
			// 도배가 초당 수십 줄로 들어오면 reset()마다 전체 줄을 다시 접게 되어 무거워진다 - 0.1초에 한 번만
			if (now - lastResetAt < 100) {
				return DROP;
			}
			lastResetAt = now;
			return DROP_AND_RESET;
		}
		lastKept = message;
		lastPlain = plain;
		lastAt = now;
		repeat = 1;
		marker = null;
		return PASS;
	}

	// ==================== 49-69차(4-12): 채팅 정리 ====================
	// 판정은 전부 여기 한 곳에서 한다. 새 믹스인은 하나도 안 늘었다 - 이미 모든 줄이 shouldDrop을 지나간다.

	/** 이 묶음이 통째로 꺼져 있으면 줄당 비용이 0(첫 줄에서 빠져나간다). */
	public static volatile boolean tidy = false;
	public static volatile boolean tidyRepeat = true;
	public static volatile boolean tidyBlank = true;
	public static volatile boolean tidyDivider = false;
	/** 같은 줄을 몇 초 안에 다시 보면 "반복"으로 볼지. */
	public static volatile int tidyRepeatSeconds = 10;

	/**
	 * 이 줄을 "정리" 대상으로 볼지.
	 *
	 * <p>49-76차: 반복 줄 판정은 여기서 빠졌다 - {@link #mergeRepeat}가 "×N"으로 합친다.
	 * 여기는 빈 줄·구분선만 본다.
	 */
	private static boolean isClutter(String plain) {
		if (plain == null) {
			return false;
		}
		String trimmed = plain.trim();
		if (tidyBlank && trimmed.isEmpty()) {
			return true;                       // 서버가 간격 띄우려고 넣는 빈 줄
		}
		if (tidyDivider && isDivider(trimmed)) {
			return true;
		}
		return false;                          // 반복은 gate()/mergeRepeat()가 맡는다(49-76차)
	}

	/** ────────── 같은 꾸밈 문자만 여섯 자 이상 늘어선 줄(서버 구분선). */
	private static boolean isDivider(String s) {
		if (s.length() < 6) {
			return false;
		}
		char first = s.charAt(0);
		if ("-=~*_─━═＝·.".indexOf(first) < 0) {
			return false;
		}
		for (int i = 1; i < s.length(); i++) {
			if (s.charAt(i) != first) {
				return false;
			}
		}
		return true;
	}

	/** 모듈이 꺼질 때 최근 줄 기억을 비운다(다시 켜면 새로 시작). */
	public static void clearTidy() {
		lastKept = null;
		lastPlain = null;
		marker = null;
		repeat = 0;
	}

	/** 채팅 히스토리 제한 100줄 → 30000줄(켜면 고정, 조절 불가). */

	public static volatile boolean unlimitedHistory = false;
	/** 채팅 줄 앞에 [HH:mm] 시각 표시. */
	public static volatile boolean timestamps = false;

	/** 49-22차: 시각 표시 색(0xRRGGBB). */
	public static volatile int timestampColor = 0xAAAAAA;

	public static final int UNLIMITED_LINES = 30000;

	/**
	 * 49-42차: 채팅 강조(MessageColorizerModule)가 켜져 있을 때 걸어 두는 변환기. ChatHudMixin이 새 메시지가
	 * 들어오는 길목(시각 접두 붙이기 전)에서 한 번 호출한다. null이면 그대로 통과.
	 */
	public static volatile java.util.function.UnaryOperator<net.minecraft.text.Text> highlighter;

	public static net.minecraft.text.Text applyHighlight(net.minecraft.text.Text message) {
		java.util.function.UnaryOperator<net.minecraft.text.Text> h = highlighter;
		if (h == null || message == null) {
			return message;
		}
		try {
			net.minecraft.text.Text out = h.apply(message);
			return out != null ? out : message;
		} catch (Throwable ignored) {
			return message;
		}
	}
}
