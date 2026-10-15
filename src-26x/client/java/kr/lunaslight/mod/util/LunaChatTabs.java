package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.gui.LunaDraw;
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
		ALL("전체"), SAY("일반"), TEAM("팀"), WHISPER("귓속말"), NOTICE("공지"), SYSTEM("시스템");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int GOLD = 0xFFE8AA2C;
	private static final int DARK = 0xD910121A;      // #10121A 85%
	private static final int DARK_HOVER = 0xE6232733;
	private static final int TEXT_ON = 0xFFFFFFFF;
	private static final int TEXT_OFF = 0xFFC4C7D0;
	private static final int BADGE = 0xFFE62632;
	private static final int TAB_H = 12;
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
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, td) -> render(s, g, mx, my));
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

	private static void tick(Minecraft mc) {
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
		if (tag != null) {
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

	// ==================== 탭 줄 / 채널 상자 ====================

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
		return LunaDraw.width(f, "관리자") + 16;
	}

	/** 열린 채팅창에서 채팅 영역 맨 위 y(화면 좌표). 바닐라 extractRenderState와 같은 계산(아래 여백 40, 배율, 줄 간격). */
	private static int chatTop(Minecraft mc, int sh) {
		double scale = 1.0;
		double spacing = 0.0;
		int lines = 10;
		try {
			scale = mc.options.chatScale().get();
			spacing = mc.options.chatLineSpacing().get();
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
		int k = (int) Math.floor((sh - 40) / scale);
		return (int) Math.floor((k - lines * lh) * scale);
	}

	/** {탭 순번, x, y, w} 목록. */
	private static List<int[]> layout(Minecraft mc, Font f, int sh) {
		List<int[]> out = new ArrayList<>();
		int y = Math.max(2, chatTop(mc, sh) - TAB_H - 3);
		int x = 2;
		for (Tab t : tabs()) {
			int w = LunaDraw.width(f, t.label) + TAB_PAD * 2;
			out.add(new int[]{t.ordinal(), x, y, w});
			x += w + TAB_GAP;
		}
		return out;
	}

	private static EditBox input(Screen s) {
		for (GuiEventListener l : s.children()) {
			if (l instanceof EditBox e) {
				return e;
			}
		}
		return null;
	}

	private static void render(Screen s, GuiGraphicsExtractor g, int mx, int my) {
		if (!active) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Font f = mc.font;
			int sw = s.width;
			int sh = s.height;
			int bw = boxW(f);

			// 입력칸을 채널 상자만큼 오른쪽으로(한 번만)
			EditBox in = input(s);
			if (in != null && in.getX() < 4 + bw + 2) {
				in.setX(4 + bw + 2);
				in.setWidth(Math.max(20, sw - (4 + bw + 2)));
			}

			// 탭 줄
			for (int[] r : layout(mc, f, sh)) {
				Tab t = Tab.values()[r[0]];
				int x = r[1];
				int y = r[2];
				int w = r[3];
				boolean sel = t == cur;
				boolean hover = mx >= x && mx < x + w && my >= y && my < y + TAB_H;
				LunaDraw.roundRect(g, x, y, w, TAB_H, 3, sel ? GOLD : (hover ? DARK_HOVER : DARK));
				LunaDraw.text(g, f, t.label, x + TAB_PAD, LunaDraw.textY(y, TAB_H), sel ? TEXT_ON : TEXT_OFF);
				int n = unread[t.ordinal()];
				if (n > 0 && !sel) {
					String b = n > 9 ? "9+" : String.valueOf(n);
					int bh = 9;
					int bwid = Math.max(bh, LunaDraw.width(f, b) + 4);
					int bx = x + w - bwid + 3;
					int by = y - 4;
					LunaDraw.pill(g, bx, by, bwid, bh, BADGE);
					LunaDraw.text(g, f, b, bx + (bwid - LunaDraw.width(f, b)) / 2, LunaDraw.textY(by, bh), TEXT_ON);
				}
			}

			// 채널 상자
			int bx = 2;
			int by = sh - 14;
			boolean boxHover = mx >= bx && mx < bx + bw && my >= by && my < by + 12;
			LunaDraw.roundRect(g, bx, by, bw, 12, 2, boxHover || dropdown ? DARK_HOVER : DARK);
			LunaDraw.text(g, f, channelLabel(say), bx + 4, LunaDraw.textY(by, 12), TEXT_ON);
			int ax = bx + bw - 9;
			int ay = by + 5;
			g.fill(ax, ay, ax + 5, ay + 1, TEXT_OFF);
			g.fill(ax + 1, ay + 1, ax + 4, ay + 2, TEXT_OFF);
			g.fill(ax + 2, ay + 2, ax + 3, ay + 3, TEXT_OFF);

			if (dropdown) {
				List<String> ch = channels();
				int dy = by - 2 - ch.size() * ITEM_H;
				LunaDraw.roundRect(g, bx, dy - 1, bw, ch.size() * ITEM_H + 2, 2, 0xF010121A);
				for (int i = 0; i < ch.size(); i++) {
					int iy = dy + i * ITEM_H;
					boolean h = mx >= bx && mx < bx + bw && my >= iy && my < iy + ITEM_H;
					if (h) {
						g.fill(bx + 1, iy, bx + bw - 1, iy + ITEM_H, DARK_HOVER);
					}
					boolean now = ch.get(i).equals(say);
					LunaDraw.text(g, f, channelLabel(ch.get(i)), bx + 4, LunaDraw.textY(iy, ITEM_H), now ? GOLD : TEXT_OFF);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatTabs:render", t);
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
			int bw = boxW(f);
			int bx = 2;
			int by = sh - 14;
			if (dropdown) {
				List<String> ch = channels();
				int dy = by - 2 - ch.size() * ITEM_H;
				dropdown = false;
				if (mx >= bx && mx < bx + bw && my >= dy && my < dy + ch.size() * ITEM_H) {
					String pick = ch.get((int) ((my - dy) / ITEM_H));
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
			for (int[] r : layout(mc, f, sh)) {
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
