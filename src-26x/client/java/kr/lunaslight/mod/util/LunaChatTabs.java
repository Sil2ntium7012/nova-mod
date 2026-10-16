package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaIcons;
import kr.lunaslight.mod.network.LunaChatPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 49-360차: LUNA FESTIVAL 서버 채팅 채널 탭(26.x 전용 - 서버가 Paper 26.1.2).
 *
 * <p>서버는 줄마다 맨 앞에 글자 없는 텍스트를 붙이고 그 글꼴을 {@code luna:chtag/<kind>}로 둔다
 * (kind = say/team/admin/whisper/notice/system, 꼬리표 없는 줄은 system). 상태는 {@code luna:chat} 패킷.
 * 꼬리표나 상태 패킷을 한 번이라도 받은 연결에서만 켜지고, 다른 서버는 바닐라 그대로다.
 *
 * <p>거르기는 바닐라 {@code ChatComponent.setVisibleMessageFilter}(채팅 제한용 필터)에 얹는다 - 바닐라 필터와
 * AND로 묶은 {@link TabFilter}를 끼운다(ChatTabsMixin이 바닐라가 새 필터를 넣을 때도 다시 감싼다).
 * 다른 탭 줄은 버리지 않으니 탭을 바꾸면 그 기록이 그대로 보인다.
 */
public final class LunaChatTabs {
	private LunaChatTabs() {
	}

	public enum Tab {
		ALL("전체"), WHISPER("귓속말"), TEAM("팀"), NOTICE("공지"), SAY("일반"), SYSTEM("시스템");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int GOLD = 0xFFE8AA2C;
	private static final int TEXT_ON = 0xFFFFFFFF;
	private static final int TEXT_OFF = 0xFFC4C7D0;
	private static final int BADGE = 0xFFE62632;
	private static final int TAB_H = 13;
	private static final int TAB_PAD = 5;
	private static final int TAB_GAP = 2;
	private static final int ITEM_H = 12;

	public static volatile ChatComponent chat;
	private static volatile boolean active;
	private static Tab cur = Tab.ALL;
	private static final int[] unread = new int[Tab.values().length];
	private static String say = "say";
	private static boolean teamAvail;
	private static boolean adminAvail;
	private static Predicate<GuiMessage> base = m -> true;
	private static boolean dropdown;
	private static Object lastConnection;
	/** 다른 기능(이름 얼굴·강조)이 Text를 다시 짜면 빈 꼬리표가 빠질 수 있어, 넣은 줄 → 종류를 따로 기억(키는 같은 객체). */
	private static final Map<Component, String> KINDS = new com.google.common.collect.MapMaker().weakKeys().makeMap();

	// ==================== 준비 ====================

	public static void init() {
		try {
			PayloadTypeRegistry.clientboundPlay().register(LunaChatPayload.TYPE, LunaChatPayload.CODEC);
			ClientPlayNetworking.registerGlobalReceiver(LunaChatPayload.TYPE, (p, ctx) -> onState(p.json()));
		} catch (Throwable t) {
			LunaClientMod.LOGGER.info("[Nova] 채팅 탭 패킷(luna:chat) 등록 실패 - 꼬리표로만 켭니다: " + t);
		}
		try {
			ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
				if (!(screen instanceof ChatScreen)) {
					return;
				}
				dropdown = false;
				ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !click(s, e.x(), e.y(), e.button()));
				ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !key(s, e.key(), e.modifiers()));
			});
		} catch (Throwable t) {
			LunaClientMod.LOGGER.info("[Nova] 채팅 탭 화면 훅 등록 실패: " + t);
		}
		try {
			ClientTickEvents.END_CLIENT_TICK.register(LunaChatTabs::tick);
		} catch (Throwable t) {
			LunaClientMod.LOGGER.info("[Nova] 채팅 탭 틱 등록 실패: " + t);
		}
	}

	/** 49-362차(사용자: "내 서버에서만 있어야 하는 기능"): 내 서버(Module.isMyServer)일 때만 켠다(꼬리표, 패킷을 보내는 다른 서버는 무시). */
	private static boolean mine() {
		return kr.lunaslight.mod.module.Module.novaServerNow();
	}

	private static void tick(Minecraft mc) {
		if (active && !mine()) {
			reset();
		}
		Object conn = mc.getConnection();
		if (conn != lastConnection) {
			boolean leaving = lastConnection != null;
			lastConnection = conn;
			if (leaving) {
				reset();   // 나갔거나 다른 서버로 옮김(처음 붙을 땐 그사이 온 꼬리표를 지우지 않게 건드리지 않는다)
			}
		}
	}

	private static void reset() {
		boolean was = active;
		active = false;
		cur = Tab.ALL;
		Arrays.fill(unread, 0);
		say = "say";
		teamAvail = false;
		adminAvail = false;
		dropdown = false;
		if (was) {
			refresh();
		}
	}

	// ==================== 꼬리표 ====================

	private static String tagOf(Component c, int depth) {
		if (c == null || depth > 8) {
			return null;
		}
		try {
			if (c.getStyle().getFont() instanceof FontDescription.Resource r) {
				String id = r.id().toString();
				if (id.startsWith("luna:chtag/")) {
					return id.substring("luna:chtag/".length());
				}
			}
			for (Component s : c.getSiblings()) {
				String k = tagOf(s, depth + 1);
				if (k != null) {
					return k;
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static String kindOf(Component c) {
		String k = tagOf(c, 0);
		if (k == null && c != null) {
			k = KINDS.get(c);
		}
		return k == null ? "system" : k;
	}

	private static Tab tabOf(String kind) {
		switch (kind) {
			case "say":
			case "admin":
				return Tab.SAY;
			case "team":
				return Tab.TEAM;
			case "whisper":
				return Tab.WHISPER;
			case "notice":
				return Tab.NOTICE;
			default:
				return Tab.SYSTEM;
		}
	}

	/** ChatHudMixin: 서버에서 온 원문(꾸미기 전). 꼬리표면 켜고, 지금 탭에 안 보일 줄이면 그 탭 안 읽은 수 +1. 종류를 돌려준다. */
	public static String onIncoming(ChatComponent component, Component original) {
		if (component != null) {
			chat = component;
		}
		String tag = tagOf(original, 0);
		if (tag != null && mine()) {
			active = true;
		}
		if (!active) {
			return null;
		}
		String kind = tag == null ? "system" : tag;
		Tab t = tabOf(kind);
		if (cur != Tab.ALL && cur != t) {
			unread[t.ordinal()]++;
		}
		return kind;
	}

	/** ChatHudMixin: 꾸민 뒤 실제로 채팅에 들어가는 Text에 종류를 묶어 둔다. */
	public static void remember(Component stored, String kind) {
		if (stored != null && kind != null) {
			KINDS.put(stored, kind);
		}
	}

	// ==================== 거르기 ====================

	private static final class TabFilter implements Predicate<GuiMessage> {
		final Predicate<GuiMessage> inner;

		TabFilter(Predicate<GuiMessage> inner) {
			this.inner = inner;
		}

		@Override
		public boolean test(GuiMessage m) {
			return inner.test(m) && visible(m);
		}
	}

	/** ChatTabsMixin: 바닐라가 넣는 필터를 탭 필터로 감싼다. */
	public static Predicate<GuiMessage> wrap(ChatComponent component, Predicate<GuiMessage> p) {
		if (component != null) {
			chat = component;
		}
		if (p instanceof TabFilter) {
			return p;
		}
		base = p == null ? m -> true : p;
		return new TabFilter(base);
	}

	private static boolean visible(GuiMessage m) {
		if (!active || cur == Tab.ALL) {
			return true;
		}
		try {
			return tabOf(kindOf(m.content())) == cur;
		} catch (Throwable t) {
			return true;
		}
	}

	private static void refresh() {
		ChatComponent c = chat;
		if (c == null) {
			return;
		}
		try {
			c.setVisibleMessageFilter(new TabFilter(base));
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:refresh", t);
		}
	}

	private static void select(Tab t, boolean fromUser) {
		cur = t;
		if (t == Tab.ALL) {
			Arrays.fill(unread, 0);
		} else {
			unread[t.ordinal()] = 0;
		}
		refresh();
		if (fromUser) {
			if (t == Tab.SAY && !"say".equals(say)) {
				command("채널 일반");
			} else if (t == Tab.TEAM && !"team".equals(say)) {
				command("채널 팀");
			}
		}
	}

	private static void command(String cmd) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.getConnection() != null) {
				mc.getConnection().sendCommand(cmd);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:command", t);
		}
	}

	// ==================== 상태 패킷 ====================

	public static void onState(String json) {
		if (!mine()) {
			return;   // 49-362차: 내 서버에서만
		}
		try {
			JsonElement el = JsonParser.parseString(json);
			if (!el.isJsonObject()) {
				return;
			}
			JsonObject o = el.getAsJsonObject();
			if (o.has("say") && o.get("say").isJsonPrimitive()) {
				say = o.get("say").getAsString();
			}
			if (o.has("team") && o.get("team").isJsonPrimitive()) {
				teamAvail = o.get("team").getAsBoolean();
			}
			if (o.has("admin") && o.get("admin").isJsonPrimitive()) {
				adminAvail = o.get("admin").getAsBoolean();
			}
			boolean was = active;
			active = true;
			if (cur == Tab.TEAM && !teamAvail) {
				select(Tab.ALL, false);
			} else if (!was) {
				refresh();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:state", t);
		}
	}

	// ==================== 채팅 상자 (사용자 사진: 위 탭 줄 + 어두운 상자 + 아래 채널 상자·입력칸) ====================

	private static final int PANEL = 0xCC0B0C10;
	private static final int PANEL_EDGE = 0x40FFFFFF;
	private static final int ROW_LINE = 0x26FFFFFF;
	private static final int BOX_FILL = 0xFF16171C;
	private static final int BOX_EDGE = 0xFF8C8E96;
	private static final int GEAR_W = 14;
	/** 상자가 보일 때 채팅 줄 아래 여백(바닐라 40 → 입력칸 바로 위). ChatTabsMixin이 바꿔 끼운다. */
	public static final int PANEL_BOTTOM_MARGIN = 17;

	/** 채팅창(ChatScreen)이 열려 있고 이 서버에서 탭이 켜졌나 - 상자를 그리고 바닐라 줄 배경·입력칸 배경을 끈다. */
	public static boolean panelShowing() {
		if (!active) {
			return false;
		}
		try {
			return LunaCompat.screenOf(Minecraft.getInstance()) instanceof ChatScreen;
		} catch (Throwable t) {
			return false;
		}
	}

	private static List<Tab> tabs() {
		List<Tab> l = new ArrayList<>();
		for (Tab t : Tab.values()) {
			if (t != Tab.TEAM || teamAvail) {
				l.add(t);
			}
		}
		return l;
	}

	private static String channelLabel(String ch) {
		switch (ch) {
			case "team":
				return "팀";
			case "admin":
				return "관리자";
			default:
				return "일반";
		}
	}

	private static List<String> channels() {
		List<String> l = new ArrayList<>();
		l.add("say");
		if (teamAvail) {
			l.add("team");
		}
		if (adminAvail) {
			l.add("admin");
		}
		return l;
	}

	private static int boxW(Font f) {
		return LunaDraw.width(f, "관리자") + 18;
	}

	private static int tabW(Font f, Tab t) {
		return LunaDraw.width(f, t.label) + TAB_PAD * 2;
	}

	/** 상자 자리 {x, 탭 줄 y, 폭}. 채팅 줄 영역(바닐라 계산: 배율·줄 간격·getLinesPerPage) 바로 위가 탭 줄. */
	private static int[] panel(Minecraft mc, Font f, int sh) {
		double scale = 1.0;
		double spacing = 0.0;
		int lines = 10;
		int chatW = 320;
		try {
			scale = mc.options.chatScale().get();
			spacing = mc.options.chatLineSpacing().get();
			chatW = ChatComponent.getWidth(mc.options.chatWidth().get());
			ChatComponent c = chat;
			if (c != null) {
				lines = c.getLinesPerPage();
			}
		} catch (Throwable ignored) {
		}
		if (scale <= 0) {
			scale = 1.0;
		}
		int lh = (int) (9.0 * (spacing + 1.0));
		int k = (int) Math.floor((sh - PANEL_BOTTOM_MARGIN) / scale);
		int top = (int) Math.floor((k - lines * lh) * scale);
		int need = 6 + GEAR_W;
		for (Tab t : tabs()) {
			need += tabW(f, t) + TAB_GAP;
		}
		int x = 2;
		int w = Math.max((int) Math.ceil(chatW + 8 * scale) - 2, need);
		int y = Math.max(1, top - TAB_H - 2);
		return new int[]{x, y, w};
	}

	/** {탭 순번, x, y, w} 목록. */
	private static List<int[]> layout(Font f, int[] p) {
		List<int[]> out = new ArrayList<>();
		int x = p[0] + 3;
		for (Tab t : tabs()) {
			int w = tabW(f, t);
			out.add(new int[]{t.ordinal(), x, p[1], w});
			x += w + TAB_GAP;
		}
		return out;
	}

	private static int gearX(int[] p) {
		return p[0] + p[2] - GEAR_W;
	}

	private static EditBox input(Screen s) {
		for (GuiEventListener l : s.children()) {
			if (l instanceof EditBox e) {
				return e;
			}
		}
		return null;
	}

	/** ChatTabsMixin(ChatScreen.extractRenderState 맨 앞): 채팅 줄보다 먼저 상자·탭 줄·채널 상자를 깐다. */
	public static void drawPanel(Screen s, GuiGraphicsExtractor g, int mx, int my) {
		if (!active) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Font f = mc.font;
			int sh = s.height;
			int[] p = panel(mc, f, sh);
			int px = p[0];
			int py = p[1];
			int pw = p[2];
			int bw = boxW(f);
			int bx = px + 2;
			int by = sh - 14;

			// 입력칸: 채널 상자 오른쪽 ~ 상자 오른쪽 끝
			EditBox in = input(s);
			if (in != null) {
				int ix = bx + bw + 4;
				int iw = Math.max(20, px + pw - ix - 2);
				if (in.getX() != ix || in.getWidth() != iw) {
					in.setX(ix);
					in.setWidth(iw);
				}
			}

			// 바탕 상자 + 테두리
			g.fill(px, py, px + pw, sh - 1, PANEL);
			g.fill(px, py - 1, px + pw, py, PANEL_EDGE);
			g.fill(px, sh - 1, px + pw, sh, PANEL_EDGE);
			g.fill(px - 1, py - 1, px, sh, PANEL_EDGE);
			g.fill(px + pw, py - 1, px + pw + 1, sh, PANEL_EDGE);
			g.fill(px, py + TAB_H, px + pw, py + TAB_H + 1, ROW_LINE);   // 탭 줄 아래 가는 선

			// 탭 줄: 고른 탭만 금색 칸, 나머지는 글씨만
			for (int[] r : layout(f, p)) {
				Tab t = Tab.values()[r[0]];
				int x = r[1];
				int y = r[2];
				int w = r[3];
				boolean sel = t == cur;
				boolean hover = !sel && mx >= x && mx < x + w && my >= y && my < y + TAB_H;
				if (sel) {
					g.fill(x, y, x + w, y + TAB_H, GOLD);
				} else if (hover) {
					g.fill(x, y, x + w, y + TAB_H, 0x22FFFFFF);
				}
				LunaDraw.text(g, f, t.label, x + TAB_PAD, LunaDraw.textY(y, TAB_H), sel || hover ? TEXT_ON : TEXT_OFF);
				int n = unread[t.ordinal()];
				if (n > 0 && !sel) {
					String b = n > 9 ? "9+" : String.valueOf(n);
					int bh = 9;
					int bwid = Math.max(8, LunaDraw.width(f, b) + 4);
					int ax = x + w - bwid + 2;
					int ay = y - 3;
					LunaDraw.roundRect(g, ax, ay, bwid, bh, 1, BADGE);
					LunaDraw.text(g, f, b, ax + (bwid - LunaDraw.width(f, b) + 1) / 2, LunaDraw.textY(ay, bh), TEXT_ON);
				}
			}

			// 톱니(채팅 설정)
			int gx = gearX(p);
			boolean gh = mx >= gx && mx < gx + GEAR_W && my >= py && my < py + TAB_H;
			LunaIcons.draw(g, f, LunaIcons.SETTINGS, gx + (GEAR_W - 10) / 2, LunaDraw.iconY(py, TAB_H), gh ? TEXT_ON : TEXT_OFF);

			// 채널 상자: 회색 테두리 + 어두운 칸 + 깔때기 ▼
			boolean boxHover = mx >= bx && mx < bx + bw && my >= by && my < by + 12;
			g.fill(bx, by, bx + bw, by + 12, BOX_EDGE);
			g.fill(bx + 1, by + 1, bx + bw - 1, by + 11, boxHover || dropdown ? 0xFF24262E : BOX_FILL);
			LunaDraw.text(g, f, channelLabel(say), bx + 4, LunaDraw.textY(by, 12), TEXT_ON);
			int fx = bx + bw - 11;
			int fy = by + 3;
			g.fill(fx, fy, fx + 7, fy + 1, TEXT_ON);
			g.fill(fx + 1, fy + 1, fx + 6, fy + 2, TEXT_ON);
			g.fill(fx + 2, fy + 2, fx + 5, fy + 3, TEXT_ON);
			g.fill(fx + 3, fy + 3, fx + 4, fy + 6, TEXT_ON);
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:panel", t);
		}
	}

	/** ChatTabsMixin(ChatScreen.extractRenderState 맨 끝): 채널 목록(채팅 글씨보다 위 층). */
	public static void drawOverlay(Screen s, GuiGraphicsExtractor g, int mx, int my) {
		if (!active || !dropdown) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Font f = mc.font;
			int sh = s.height;
			int bw = boxW(f);
			int bx = panel(mc, f, sh)[0] + 2;
			int by = sh - 14;
			g.nextStratum();
			List<String> ch = channels();
			int dy = by - 1 - ch.size() * ITEM_H;
			g.fill(bx, dy - 1, bx + bw, by - 1 + 1, BOX_EDGE);
			g.fill(bx + 1, dy, bx + bw - 1, by - 1, BOX_FILL);
			for (int i = 0; i < ch.size(); i++) {
				int iy = dy + i * ITEM_H;
				boolean h = mx >= bx && mx < bx + bw && my >= iy && my < iy + ITEM_H;
				if (h) {
					g.fill(bx + 1, iy, bx + bw - 1, iy + ITEM_H, 0xFF2C2F38);
				}
				boolean now = ch.get(i).equals(say);
				LunaDraw.text(g, f, channelLabel(ch.get(i)), bx + 4, LunaDraw.textY(iy, ITEM_H), now ? GOLD : TEXT_ON);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:overlay", t);
		}
	}

	/** 처리했으면 true(바닐라로 안 넘김). */
	private static boolean click(Screen s, double mx, double my, int button) {
		if (!active || button != 0) {
			return false;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Font f = mc.font;
			int sh = s.height;
			int[] p = panel(mc, f, sh);
			int bw = boxW(f);
			int bx = p[0] + 2;
			int by = sh - 14;
			if (dropdown) {
				List<String> ch = channels();
				int dy = by - 1 - ch.size() * ITEM_H;
				dropdown = false;
				if (mx >= bx && mx < bx + bw && my >= dy && my < dy + ch.size() * ITEM_H) {
					String pick = ch.get(Math.min(ch.size() - 1, (int) ((my - dy) / ITEM_H)));
					if (!pick.equals(say)) {
						command("채널 " + channelLabel(pick));
					}
					return true;
				}
				if (mx >= bx && mx < bx + bw && my >= by && my < by + 12) {
					return true;   // 상자를 다시 누르면 닫기만
				}
			} else if (mx >= bx && mx < bx + bw && my >= by && my < by + 12) {
				dropdown = true;
				return true;
			}
			int gx = gearX(p);
			if (mx >= gx && mx < gx + GEAR_W && my >= p[1] && my < p[1] + TAB_H) {
				LunaCompat.setScreen(new net.minecraft.client.gui.screens.options.ChatOptionsScreen(s, mc.options));
				return true;
			}
			for (int[] r : layout(f, p)) {
				if (mx >= r[1] && mx < r[1] + r[3] && my >= r[2] && my < r[2] + TAB_H) {
					select(Tab.values()[r[0]], true);
					return true;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:click", t);
		}
		return false;
	}

	/** Ctrl+←/→ = 앞/뒤 탭(입력칸이 비었을 때만 - 글을 쓰는 중엔 단어 이동 그대로). Esc = 채널 목록 닫기. */
	private static boolean key(Screen s, int key, int mods) {
		if (!active) {
			return false;
		}
		if (key == 256 && dropdown) {
			dropdown = false;
			return true;
		}
		if ((mods & 2) == 0 || (key != 262 && key != 263)) {
			return false;
		}
		EditBox in = input(s);
		if (in != null && !in.getValue().isEmpty()) {
			return false;
		}
		List<Tab> l = tabs();
		int i = Math.max(0, l.indexOf(cur));
		i = (i + (key == 262 ? 1 : -1) + l.size()) % l.size();
		select(l.get(i), true);
		return true;
	}
}
