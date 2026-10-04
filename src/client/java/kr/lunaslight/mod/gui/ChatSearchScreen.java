package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.impl.chat.ChatEnhancementsModule;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 채팅 검색 - 49-21차 재작성(사용자: "따로 창이 뜨는 게 아니라 채팅에서 검색하면 그 관련 채팅만 보이는 거").
 *
 * 채팅창(ChatScreen)에서 Ctrl+F로 열리며, 생김새를 바닐라 채팅과 똑같이 맞춘다: 아래 입력줄 자리에
 * 검색 입력, 그 위에 채팅 줄 자리에 검색어가 들어간 줄만(최신이 아래). 휠로 스크롤, ESC로 채팅으로 복귀.
 * 별도 패널/제목 없음 - 채팅 그 자체가 필터된 것처럼 보이게.
 */
public class ChatSearchScreen extends LunaScreenBase {

	private final Screen parent;
	private String query = "";
	private int scroll;                 // 최신 기준 몇 줄 위로 스크롤했는지
	private List<String> matches = new ArrayList<>();

	// 49-66차(4-4 채팅 복사): 이번 프레임에 그린 줄의 자리(클릭 판정을 그리기와 같은 값으로).
	private int lastTopY = Integer.MIN_VALUE;   // 가장 위에 그린 줄의 y
	private int lastStart, lastEnd;             // matches에서 그린 구간 [start, end)
	private int copiedIndex = -1;
	private long copiedUntil;

	public ChatSearchScreen(Screen parent) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("채팅 검색"));
		this.parent = parent;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (this.client != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
		}
	}

	private int chatWidth() {
		return Math.min(width - 4, 320);
	}

	private int lineHeight() {
		return 9;
	}

	private int visibleLines() {
		return Math.max(1, (height - 40) / lineHeight());
	}

	/**
	 * 49-32차(사용자: "채팅 검색이 작동 안 하는 것 같다. 닉네임·내용·시간 모두 포함이어야 해"):
	 *  · 검색 대상을 [시각 + 원문] 전체로 넓혔다 - 원문에는 보낸 사람 닉네임이 들어 있으므로
	 *    닉네임·내용·시간이 한 번에 걸린다.
	 *  · 우리 기록이 비어 있으면(모듈을 켜기 전에 온 메시지 등) **바닐라 채팅에 남아 있는 줄**을
	 *    그대로 읽어 검색한다 - "검색해도 아무것도 안 나오는" 상태를 없앤다.
	 */
	private void refilter() {
		matches = new ArrayList<>();
		String q = query.trim().toLowerCase();

		List<String> pool = new ArrayList<>();
		var opt = ModuleManager.get().find("chat_enhancements");
		if (opt.isPresent() && opt.get() instanceof ChatEnhancementsModule chat) {
			for (ChatEnhancementsModule.TimestampedMessage m : chat.getHistory()) {
				pool.add(m.toDisplayString());
			}
		}
		if (pool.isEmpty()) {
			List<String> vanilla = LunaCompat.chatHudMessages(client);
			for (int i = vanilla.size() - 1; i >= 0; i--) {   // 오래된 것부터
				pool.add(vanilla.get(i));
			}
		}
		for (String line : pool) {
			if (q.isEmpty() || line.toLowerCase().contains(q)) {
				matches.add(line);
			}
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, matches.size() - visibleLines())));
	}

	@Override
	protected void init() {
		refilter();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int cw = chatWidth();
		int lh = lineHeight();
		int inputY = height - 14;

		// ---- 채팅 줄(바닐라 채팅과 같은 자리: 왼쪽 2px, 입력줄 위) ----
		int visible = visibleLines();
		int end = matches.size() - scroll;
		int start = Math.max(0, end - visible);
		int y = inputY - 4 - lh;
		lastStart = start;
		lastEnd = end;
		lastTopY = Integer.MIN_VALUE;
		long now = System.currentTimeMillis();
		for (int i = end - 1; i >= start; i--) {
			boolean hovered = LunaDraw.in(mouseX, mouseY, 2, y, cw, lh);
			boolean justCopied = i == copiedIndex && now < copiedUntil;
			String line = LunaDraw.ellipsizeFormatted(textRenderer, matches.get(i), cw - 6);
			// 49-66차(4-4): 마우스가 올라간 줄을 밝게 - "이 줄을 누르면 복사된다"가 보이게.
			ctx.fill(2, y, 2 + cw, y + lh, justCopied ? 0xA0184A2A : (hovered ? 0xA0000000 : 0x80000000));
			LunaCompat.drawHudText(ctx, textRenderer, justCopied ? "§a복사됨  §7" + line : line,
				4, y + 1, 0xFFFFFFFF);
			lastTopY = y;
			y -= lh;
		}
		if (matches.isEmpty()) {
			ctx.fill(2, y, 2 + cw, y + lh, 0x80000000);
			LunaCompat.drawHudText(ctx, textRenderer, query.isEmpty() ? "§7검색어를 입력하세요" : "§7일치하는 채팅 없음", 4, y + 1, 0xFFFFFFFF);
		}
		// 개수/스크롤 안내(오른쪽 위 작게)
		String info = matches.size() + "줄" + (scroll > 0 ? " | ↑" + scroll : "") + "  §7줄 클릭 = 복사 | ESC 닫기";
		int iw = LunaCompat.getTextWidth(textRenderer, info);
		ctx.fill(2 + cw - iw - 6, inputY - 4 - lh * (Math.max(1, end - start)) - lh - 2, 2 + cw, inputY - 4 - lh * (Math.max(1, end - start)) - 2, 0x66000000);
		LunaCompat.drawHudText(ctx, textRenderer, info, 2 + cw - iw - 3, inputY - 4 - lh * (Math.max(1, end - start)) - lh - 1, 0xFFDDDDDD);

		// ---- 입력줄(바닐라 채팅 입력과 동일 위치/크기) ----
		ctx.fill(2, inputY, width - 2, inputY + 12, 0x80000000);
		String shown = "§a검색§r: " + query;
		LunaCompat.drawHudText(ctx, textRenderer, shown, 4, inputY + 2, 0xFFFFFFFF);
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = 4 + LunaCompat.getTextWidth(textRenderer, shown);
			ctx.fill(cx, inputY + 2, cx + 1, inputY + 11, 0xFFFFFFFF);
		}
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		if (chr < 32) {
			return false;
		}
		query += chr;
		scroll = 0;
		refilter();
		return true;
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
				scroll = 0;
				refilter();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			scroll = Math.min(scroll + visibleLines(), Math.max(0, matches.size() - visibleLines()));
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			scroll = Math.max(0, scroll - visibleLines());
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			return true; // 아무것도 안 함(엔터로 실수로 닫히지 않게)
		}
		return false;
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		int step = verticalAmount > 0 ? 3 : -3;
		scroll = Math.max(0, Math.min(scroll + step, Math.max(0, matches.size() - visibleLines())));
		return true;
	}

	/**
	 * 49-66차(4-4 "채팅 복사"): <b>줄을 누르면 그 줄이 클립보드로</b> 간다.
	 *
	 * <p><b>왜 인게임 채팅창이 아니라 여기인가</b>: 바닐라 채팅창에서 "마우스 아래의 줄"을 알아내려면
	 * {@code ChatHud}의 줄 나눔·스크롤·배율 계산을 버전마다 다시 구현해야 한다(공개 API가 없다).
	 * 엉뚱한 줄이 복사되면 없느니만 못하다. 이 화면은 <b>우리가 그린 줄</b>이라 자리가 정확하고,
	 * 버전에 전혀 의존하지 않는다. 검색어가 비어 있으면 전체 기록이 그대로 나오므로 그냥 "채팅 기록"으로 쓰면 된다.
	 *
	 * <p>복사되는 건 <b>색 코드를 뺀 글자</b>다 - §a 같은 게 섞여 들어가면 어디에 붙여넣어도 지저분하다.
	 */
	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		int cw = chatWidth();
		int lh = lineHeight();
		if (lastTopY != Integer.MIN_VALUE && mouseX >= 2 && mouseX <= 2 + cw) {
			int drawn = lastEnd - lastStart;
			for (int n = 0; n < drawn; n++) {
				int y = lastTopY + n * lh;             // 위에서 아래로: 가장 위가 lastEnd-drawn
				int index = lastEnd - 1 - (drawn - 1 - n);
				if (mouseY >= y && mouseY < y + lh && index >= 0 && index < matches.size()) {
					LunaCompat.copyToClipboard(client, stripCodes(matches.get(index)));
					copiedIndex = index;
					copiedUntil = System.currentTimeMillis() + 1200;
					return true;
				}
			}
		}
		return true; // 나머지 클릭은 흡수(화면이 닫히지 않게)
	}

	/** §x 색 코드 제거. 클립보드에는 사람이 읽는 글자만 들어가야 한다. */
	private static String stripCodes(String text) {
		if (text == null || text.indexOf('\u00A7') < 0) {
			return text == null ? "" : text;
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u00A7' && i + 1 < text.length()) {
				i++;   // 코드 문자까지 건너뛴다
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
