package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.ChatFaces;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>채팅</b> - 채팅 관련 기능을 한 모듈로 모았다.
 *
 * <p>49-121차(사용자: "채팅 강조·채팅 숨기기·채팅 정리를 '채팅' 하나로 합치고 개별 삭제"): 예전엔 [일반]에
 * 채팅 / 채팅 강조 / 채팅 숨기기가 따로, [기능]에 채팅 정리가 따로였다. 넷을 이 한 모듈로 합쳤다.
 *
 * <ul>
 *   <li><b>기본</b> - 시각 표시([HH:mm]) · 무제한 기록(100→30000줄) · 채팅창 Ctrl+F 검색.</li>
 *   <li><b>채팅 강조</b>(옛 MessageColorizer) - 내 이름·키워드가 든 줄에서 그 말만 색칠 + 알림음 + 멘션 알림음(띠링).</li>
 *   <li><b>채팅 숨기기</b>(옛 ChatFilter) - 발전 과제 메시지 · 특정 텍스트가 든 줄을 채팅에 안 넣는다.</li>
 *   <li><b>채팅 정리</b>(옛 ChatTidy) - 반복 줄 ×N · 빈 줄 · 구분선 치우기.</li>
 * </ul>
 *
 * <p>실제 판단·그리기는 대부분 {@link ChatState}·ChatHudMixin에서 하고, 이 모듈은 설정을 그쪽에 연결한다.
 * 강조 색칠 로직만 여기(highlight/recolor)에서 하고 {@link ChatState#highlighter}로 넘긴다.
 */
public class ChatEnhancementsModule extends Module {

	public record TimestampedMessage(String timeLabel, String content) {
		public String toDisplayString() {
			return "§7[" + timeLabel + "]§r " + content;
		}
	}

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	// ==================== 기본 ====================
	private final BooleanSetting showTimestamps = register(new BooleanSetting(
			"show_timestamps", "시각 표시", "채팅 줄 앞에 받은 시각을 [시:분]으로 표시합니다.", true));
	private final ColorSetting timestampColor = register(new ColorSetting(
			"timestamp_color", "시각 색", "시각 글자의 색입니다.", 0xFFAAAAAA));
	private final BooleanSetting unlimitedHistory = register(new BooleanSetting(
			"unlimited_history", "무제한 채팅", "채팅 기록 100줄 제한을 풀어 30000줄까지 남깁니다.", true));
	private final BooleanSetting search = register(new BooleanSetting(
			"search", "채팅 검색", "채팅창에서 Ctrl+F로 지난 채팅을 검색합니다.", true));
	// 49-251차(사용자: "채팅 올라올 때 애니메이션, 그 모드처럼"): 새 줄이 아래에서 부드럽게 밀고 올라온다(util.ChatAnim)
	private final BooleanSetting chatAnim = register(new BooleanSetting(
			"chat_anim", "올라오는 애니메이션", "새 채팅이 오면 채팅창이 아래에서 부드럽게 올라옵니다.", true));

	// ==================== 채팅 강조(옛 MessageColorizer) ====================
	private final BooleanSetting highlightOn = register(new BooleanSetting(
			"highlight", "채팅 강조", "내 이름/키워드가 든 줄에서 그 말을 색칠합니다.", true));
	private final ColorSetting highlightColor = register(new ColorSetting(
			"highlight_color", "강조 색", "내 이름/키워드에 입히는 색입니다.", 0xFFFFD75E));
	private final StringSetting keywords = register(new StringSetting(
			"keywords", "키워드", "강조할 말을 쓰고 [추가]. 내 이름은 항상 강조됩니다.", "").list());
	private final BooleanSetting highlightSound = register(new BooleanSetting(
			"highlight_sound", "강조 알림음", "강조된 채팅이 오면 짧은 알림음을 냅니다.", true));
	private final BooleanSetting mentionAlert = register(new BooleanSetting(
			"mention_alert", "멘션 알림음", "내 이름이 불리면 '띠링' 소리를 냅니다. 연달아 불려도 처음 한 번만 울립니다.", true));

	// ==================== 채팅 숨기기(옛 ChatFilter) ====================
	private final BooleanSetting hideOn = register(new BooleanSetting(
			"hide", "채팅 숨김", "원치 않는 채팅 줄을 채팅에 아예 안 넣습니다.", false));
	private final BooleanSetting hideAdvancements = register(new BooleanSetting(
			"hide_advancements", "발전 과제 메시지", "누가 발전 과제를 달성했다는 채팅을 숨깁니다.", true));
	// 49-121차(사용자: "채팅 숨기기에 이름 입력 X 텍스트 입력 O"): 칩 목록 대신 그냥 텍스트 한 줄.
	private final StringSetting hideText = register(new StringSetting(
			"hide_text", "숨길 텍스트", "이 텍스트가 든 채팅을 숨깁니다. 여러 개는 쉼표로. 대소문자는 가리지 않습니다.", ""));

	// ==================== 채팅 정리(옛 ChatTidy) ====================
	private final BooleanSetting tidyOn = register(new BooleanSetting(
			"tidy", "채팅 정리", "읽을 게 없는 줄(반복/빈 줄/구분선)을 치웁니다.", false));
	private final BooleanSetting tidyRepeat = register(new BooleanSetting(
			"tidy_repeat", "반복 줄 병합", "같은 문장이 짧은 사이에 또 오면 앞 줄에 ×N. 플레이어 채팅은 그대로.", true));
	private final IntSetting tidySeconds = register(new IntSetting(
			"tidy_seconds", "반복 판정 시간", "이 시간(초) 안에 같은 문장이 또 오면 반복으로 봅니다.", 10, 3, 60, 1).unit("초"));
	private final BooleanSetting tidyBlank = register(new BooleanSetting(
			"tidy_blank", "빈 줄 숨김", "서버가 간격을 띄우려고 넣는 빈 줄을 치웁니다.", true));
	private final BooleanSetting tidyDivider = register(new BooleanSetting(
			"tidy_divider", "구분선 숨김", "───── 처럼 같은 문자만 늘어선 줄을 치웁니다.", false));

	// ==================== 49-157차: 유저 차단(옛 PlayerBlockModule, [일반]에 따로 있던 것) ====================
	// 사용자: "일반 설정이랑 기능에 겹치는 거 한 곳만" - 채팅 숨기기와 유저 차단이 [기능 > 채팅]과 [일반 > 유저 차단]
	// 두 곳에 나뉘어 있었다. 이제 [채팅] 안의 [차단] 묶음 하나다(설정 값은 LunaClientConfig가 한 번 옮겨 준다).
	private final BooleanSetting blockOn = register(new BooleanSetting(
			"block", "유저 차단", "차단한 사람이 보낸 채팅과 귓속말을 채팅에 안 넣습니다.", false));
	private final StringSetting blockPlayers = register(new StringSetting(
			"block_players", "차단한 사람", "이름을 쓰고 [추가]. 대소문자는 가리지 않습니다.", "").list());
	private final BooleanSetting blockAnyLine = register(new BooleanSetting(
			"block_any_line", "이름 포함 줄",
			"서버가 직접 꾸민 채팅은 보낸 사람을 알 수 없어, 켜면 그 이름이 보이는 줄을 모두 숨깁니다(남이 말한 줄도 사라집니다).",
			false));
	private final BooleanSetting blockHideSkin = register(new BooleanSetting(
			"block_hide_skin", "캐릭터 숨김", "차단한 사람의 캐릭터를 화면에 안 그립니다.", false));

	// ---- 상태 ----
	private final List<TimestampedMessage> history = Collections.synchronizedList(new ArrayList<>());
	private boolean prevSearchCombo;
	private boolean listenerRegistered;
	private final java.util.function.UnaryOperator<Text> hl = this::highlight;

	private static final Pattern STRIP_CODES = Pattern.compile("§.");
	private static final Pattern LEADING_TAGS = Pattern.compile("^\\s*(?:[\\[(][^\\])]*[\\])]\\s*)*<?\\s*");
	private static final long SOUND_GAP_MS = 1200;
	private Pattern pattern;
	private String patternKey;
	private long lastSound;
	/** 49-130차: 멘션 '띠링'은 처음 한 번만 - 마지막 멘션에서 이만큼 조용했을 때만 다시 울린다. */
	private static final long MENTION_QUIET_MS = 10_000;
	private long lastMentionAt;

	public ChatEnhancementsModule() {
		super("chat_enhancements", "채팅", ModuleCategory.FEATURE, "시각/기록/검색 | 강조 | 숨기기 | 정리 | 차단");
		// 49-121차(사용자: "채팅이 일반/기능에 둘 다 있으면 안 되지 기능에만 있게"): settingsPage(GENERAL) 제거.
		// 이제 [일반]에 안 나오고 [기능] 그리드에만 카드로 나온다(byCategory는 getPage()==null만 포함).
		defaultEnabled(true);
		showTimestamps.withColor(timestampColor);
		highlightOn.withColor(highlightColor);
		// 49-124차(사용자: "채팅이나 이런 거 지금 한 박스 안에 너무 많아 좀 나눠야 해"): 설정을 이름 붙은
		// 묶음(박스)으로 나눈다 - 기본 / 강조 / 숨기기 / 정리. (색·위치 설정은 자동으로 스타일 묶음으로 간다.)
		showTimestamps.group("기본");
		unlimitedHistory.group("기본");
		search.group("기본");
		highlightOn.group("강조");
		keywords.group("강조");
		highlightSound.group("강조");
		mentionAlert.group("강조");
		hideOn.group("숨김");
		hideAdvancements.group("숨김");
		hideText.group("숨김");
		tidyOn.group("정리");
		tidyRepeat.group("정리");
		tidySeconds.group("정리");
		tidyBlank.group("정리");
		tidyDivider.group("정리");
		blockOn.group("차단");
		blockPlayers.group("차단");
		blockAnyLine.group("차단");
		blockHideSkin.group("차단");
		// 하위 설정은 각 묶음이 꺼져 있으면 흐리게 잠근다.
		keywords.disabledWhen(() -> !highlightOn.get());
		highlightSound.disabledWhen(() -> !highlightOn.get());
		mentionAlert.disabledWhen(() -> !highlightOn.get());
		hideAdvancements.disabledWhen(() -> !hideOn.get());
		hideText.disabledWhen(() -> !hideOn.get());
		tidyRepeat.disabledWhen(() -> !tidyOn.get());
		tidySeconds.disabledWhen(() -> !tidyOn.get());
		tidyBlank.disabledWhen(() -> !tidyOn.get());
		tidyDivider.disabledWhen(() -> !tidyOn.get());
		blockPlayers.disabledWhen(() -> !blockOn.get());
		blockAnyLine.disabledWhen(() -> !blockOn.get());
		blockHideSkin.disabledWhen(() -> !blockOn.get());
	}

	@Override
	protected void onEnable() {
		if (!listenerRegistered) {
			listenerRegistered = true;
			// 49-32차: 모듈이 꺼져 있어도 기록은 남긴다(검색은 언제든 되어야 하므로).
			// 49-216차: Fabric 이벤트 대신 ChatEvents(시대별 두 벌) - 1.14.4~1.19.2에서도 이 모듈이 들어간다
			kr.lunaslight.mod.util.ChatEvents.register(this::captureMessage);
		}
		ChatState.clearTidy();
		sync();
	}

	@Override
	protected void onDisable() {
		ChatState.timestamps = false;
		kr.lunaslight.mod.util.ChatAnim.enabled = false;
		ChatState.unlimitedHistory = false;
		ChatState.highlighter = null;
		ChatState.hideAdvancements = false;
		ChatState.blockedWords = null;
		ChatState.tidy = false;
		ChatState.clearTidy();
		ChatState.blockedPlayers = null;
		ChatState.blockAnyLine = false;
		kr.lunaslight.mod.util.EntityHideHook.blockedPlayers = null;
	}

	/** 모든 설정을 ChatState에 연결한다(매 틱). */
	private void sync() {
		ChatState.timestamps = showTimestamps.get();
		kr.lunaslight.mod.util.ChatAnim.enabled = chatAnim.get();
		ChatState.unlimitedHistory = unlimitedHistory.get();
		ChatState.timestampColor = timestampColor.getArgb() & 0x00FFFFFF;
		// 강조
		ChatState.highlighter = highlightOn.get() ? hl : null;
		// 숨기기
		ChatState.hideAdvancements = hideOn.get() && hideAdvancements.get();
		ChatState.blockedWords = hideOn.get() ? ChatState.parseWords(hideText.get()) : null;
		// 정리
		ChatState.tidyRepeat = tidyRepeat.get();
		ChatState.tidyRepeatSeconds = tidySeconds.get();
		ChatState.tidyBlank = tidyBlank.get();
		ChatState.tidyDivider = tidyDivider.get();
		ChatState.tidy = tidyOn.get() && (tidyRepeat.get() || tidyBlank.get() || tidyDivider.get());
		// 차단(49-157차)
		String[] blocked = blockOn.get() ? ChatState.parseWords(blockPlayers.get()) : null;
		ChatState.blockedPlayers = blocked;
		ChatState.blockAnyLine = blockOn.get() && blockAnyLine.get();
		kr.lunaslight.mod.util.EntityHideHook.blockedPlayers = blockOn.get() && blockHideSkin.get() ? blocked : null;
	}

	@Override
	public void onTick() {
		sync();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!search.get() || kr.lunaslight.mod.util.WindowAccess.of(mc) == null) {
			prevSearchCombo = false;
			return;
		}
		boolean chatOpen = mc.currentScreen != null && mc.currentScreen.getClass() == ChatScreen.class;
		boolean combo = chatOpen
				&& (LunaCompat.isKeyPressed(mc, GLFW.GLFW_KEY_LEFT_CONTROL) || LunaCompat.isKeyPressed(mc, GLFW.GLFW_KEY_RIGHT_CONTROL))
				&& LunaCompat.isKeyPressed(mc, GLFW.GLFW_KEY_F);
		if (combo && !prevSearchCombo) {
			LunaCompat.openScreenReflectively("kr.lunaslight.mod.gui.ChatSearchScreen", mc.currentScreen);
		}
		prevSearchCombo = combo;
	}

	private void captureMessage(Text message) {
		history.add(new TimestampedMessage(LocalTime.now().format(TIME_FORMAT), message.getString()));
		int limit = unlimitedHistory.get() ? ChatState.UNLIMITED_LINES : 500;
		while (history.size() > limit) {
			history.remove(0);
		}
	}

	/** 검색 화면용 전체 히스토리(오래된 것 → 최신 순). */
	public List<TimestampedMessage> getHistory() {
		synchronized (history) {
			return new ArrayList<>(history);
		}
	}

	// ==================== 강조: 매칭 ====================

	private Pattern pattern() {
		String me = LunaCompat.sessionName(client);
		String key = me + " " + keywords.get();
		if (pattern != null && key.equals(patternKey)) {
			return pattern;
		}
		List<String> words = new ArrayList<>();
		if (me != null && !me.isEmpty() && !"플레이어".equals(me)) {
			words.add(me);
		}
		String raw = keywords.get();
		if (raw != null) {
			for (String w : raw.split(",")) {
				String t = w.trim();
				if (!t.isEmpty() && !words.contains(t)) {
					words.add(t);
				}
			}
		}
		if (words.isEmpty()) {
			pattern = null;
		} else {
			StringBuilder sb = new StringBuilder("(?<!(?<!§)[\\p{L}\\p{N}_])(?:");
			for (int i = 0; i < words.size(); i++) {
				if (i > 0) {
					sb.append('|');
				}
				sb.append(Pattern.quote(words.get(i)));
			}
			sb.append(')');
			pattern = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		}
		patternKey = key;
		return pattern;
	}

	/** 내가 보낸 줄인가(이름이 맨 앞). */
	private boolean isOwnLine(String plain) {
		String me = LunaCompat.sessionName(client);
		if (me == null || me.isEmpty()) {
			return false;
		}
		Matcher m = LEADING_TAGS.matcher(plain);
		int start = m.find() ? m.end() : 0;
		return plain.regionMatches(true, start, me, 0, me.length());
	}

	/** 줄에 내 이름이 들어 있는가(대소문자 무시). */
	private boolean mentionsMe(String plain) {
		String me = LunaCompat.sessionName(client);
		return me != null && !me.isEmpty() && plain.toLowerCase(java.util.Locale.ROOT).contains(me.toLowerCase(java.util.Locale.ROOT));
	}

	// ==================== 강조: 변환 ====================

	private Text highlight(Text message) {
		Pattern p = pattern();
		if (p == null) {
			return message;
		}
		String plain = STRIP_CODES.matcher(message.getString()).replaceAll("");
		if (plain.isEmpty() || !p.matcher(plain).find() || isOwnLine(plain)) {
			return message;
		}
		// 49-130차(사용자: "채팅 멘션은 화면에 뜨는 게 아니라 소리 - 띠링. 연속으로 안 들리게"): 화면 카드를 없애고
		// 내 이름이 불리면 띠링 한 번. 연달아 불리는 동안(마지막 멘션 뒤 10초 안)은 다시 안 울린다.
		// 이름이 아닌 키워드만 걸린 줄은 예전처럼 '강조 알림음'(짧은 핑).
		long now = System.currentTimeMillis();
		if (mentionAlert.get() && mentionsMe(plain)) {
			if (now - lastMentionAt > MENTION_QUIET_MS) {
				LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_CHIME", 1.5f);
			}
			lastMentionAt = now;
			lastSound = now;
		} else if (highlightSound.get() && now - lastSound > SOUND_GAP_MS) {
			lastSound = now;
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_PLING", 1.35f);
		}
		Text rebuilt = recolor(message, p);
		return rebuilt != null ? rebuilt : message;
	}

	private Text recolor(Text message, Pattern p) {
		List<Object[]> segments = new ArrayList<>();
		if (!LunaCompat.visitStyled(message, (style, s) -> segments.add(new Object[]{style, s}))) {
			return null;
		}
		Text root = LunaCompat.textLiteral("");
		if (root == null) {
			return null;
		}
		Method append = LunaCompat.findMethod(root.getClass(), "append", Text.class);
		if (append == null) {
			return null;
		}
		int rgb = highlightColor.getArgb() & 0x00FFFFFF;
		try {
			Object cur = root;
			for (Object[] seg : segments) {
				Object style = seg[0];
				String s = (String) seg[1];
				if (s == null || s.isEmpty()) {
					continue;
				}
				Matcher m = p.matcher(s);
				int last = 0;
				while (m.find()) {
					if (m.start() == m.end()) {
						continue;
					}
					if (m.start() > last) {
						cur = add(append, cur, s.substring(last, m.start()), style, codesBefore(s, last, true));
					}
					cur = add(append, cur, s.substring(m.start(), m.end()),
						LunaCompat.styleWithRgb(style, rgb), codesBefore(s, m.start(), false));
					last = m.end();
				}
				if (last < s.length()) {
					cur = add(append, cur, s.substring(last), style, codesBefore(s, last, true));
				}
			}
			return cur instanceof Text t ? t : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object add(Method append, Object cur, String piece, Object style, String prefix) throws Exception {
		Text t = LunaCompat.styledLiteral(prefix.isEmpty() ? piece : prefix + piece, style);
		if (t == null) {
			return cur;
		}
		Object next = append.invoke(cur, t);
		return next != null ? next : cur;
	}

	private static String codesBefore(String s, int end, boolean withColor) {
		if (end <= 0 || s.indexOf('§') < 0) {
			return "";
		}
		char colorCode = 0;
		StringBuilder formats = new StringBuilder();
		for (int i = 0; i + 1 < end; i++) {
			if (s.charAt(i) != '§') {
				continue;
			}
			char c = Character.toLowerCase(s.charAt(i + 1));
			i++;
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
				colorCode = c;
				formats.setLength(0);
			} else if (c >= 'k' && c <= 'o') {
				if (formats.indexOf(String.valueOf(c)) < 0) {
					formats.append(c);
				}
			} else if (c == 'r') {
				colorCode = 0;
				formats.setLength(0);
			}
		}
		StringBuilder out = new StringBuilder();
		if (withColor && colorCode != 0) {
			out.append('§').append(colorCode);
		}
		for (int i = 0; i < formats.length(); i++) {
			out.append('§').append(formats.charAt(i));
		}
		return out.toString();
	}
	// ==================== 미리보기 ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (client == null || kr.lunaslight.mod.util.WindowAccess.of(client) == null) {
			return;
		}
		if (!isPreviewBoxed()) {
			return;   // 49-130차: 멘션은 소리만 - 게임 화면에 그릴 것이 없다
		}
		// 설정 미리보기 상자: 시각 표시 + 이름 강조를 함께 보여 준다.
		String me = client.player != null ? client.player.getName().getString() : "Sil2tium";
		// 49-195차(사용자: "채팅 미리보기 이상함 - 내용이 앞뒤가 안 맞음"): 예전엔 나를 부르는 말 다음에 "내가 게임에 참여했습니다"가
		// 와서 순서가 거꾸로였다. 실제 채팅 흐름대로 - 친구가 들어오고, 인사하고, 나를 부른다(마지막 줄 = 멘션 강조).
		String[] lines = {"§eAlex님이 게임에 참여했습니다", "<Alex> ㅎㅇ", "<Steve> " + me + " 여기 와봐"};
		String[] times = {"12:34", "12:34", "12:35"};
		int mention = 2;
		int x = previewX() + 6;
		int y = previewY() + previewH() - 6 - lines.length * 10;
		int w = previewW() - 12;
		context.fill(x - 2, y - 2, x + w, y + lines.length * 10, 0x80000000);
		int tc = timestampColor.getArgb() | 0xFF000000;
		int hlColor = highlightColor.getArgb() | 0xFF000000;
		for (int i = 0; i < lines.length; i++) {
			int tx = x;
			int ly = y + i * 10;
			if (showTimestamps.get()) {
				String stamp = "[" + times[i] + "] ";
				LunaCompat.drawHudText(context, client.textRenderer, stamp, tx, ly, tc);
				tx += LunaCompat.getTextWidth(client.textRenderer, stamp);
			}
			// 나를 부른 줄의 내 이름만 강조 색으로(강조 켜져 있을 때).
			if (i == mention && highlightOn.get()) {
				String pre = "<Steve> ";
				LunaCompat.drawHudText(context, client.textRenderer, pre, tx, ly, 0xFFFFFFFF);
				tx += LunaCompat.getTextWidth(client.textRenderer, pre);
				LunaCompat.drawHudText(context, client.textRenderer, me, tx, ly, hlColor);
				tx += LunaCompat.getTextWidth(client.textRenderer, me);
				LunaCompat.drawHudText(context, client.textRenderer, " 여기 와봐", tx, ly, 0xFFFFFFFF);
			} else {
				LunaCompat.drawHudText(context, client.textRenderer, lines[i], tx, ly, 0xFFFFFFFF);
			}
		}
	}
}
