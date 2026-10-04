package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaSocial;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * 49-23차: 타이틀 화면 [소셜] - Luna's Light 친구 목록 + 등록된 마인크래프트 계정(프로필) 전환.
 * 데이터는 LunaSocial(런처가 .luna-launch.json에 실어 보낸 런처 계정/등록 계정 정보 + Supabase 친구 조회).
 *
 * 49-25차: 가운데 창 대신 **옆에 붙는 서랍**으로("소셜은 가운데 창이 아니라 오른쪽 창 열리게, 프로필은 분리").
 *  - MODE_FRIENDS: 오른쪽에서 미끄러져 나오는 친구 패널(타이틀 오른쪽 위 [소셜] 버튼).
 *  - MODE_PROFILE: 왼쪽에서 나오는 내 프로필 패널(타이틀 왼쪽 위 얼굴 버튼) - 큰 얼굴 + 닉네임 + 계정 전환 목록.
 * 뒤에는 부모(타이틀) 화면을 그대로 그리고 어둡게 덮는다. 패널 밖을 누르거나 ESC로 닫힘.
 */
public class LunaSocialScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseScrolled0(mouseX * k, mouseY * k, verticalAmount);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaKeyPressed0(keyCode, scanCode, modifiers);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaCharTyped0(chr);
		} finally {
			lunaV.exit(e);
		}
	}

	public static final int MODE_FRIENDS = 0;
	public static final int MODE_PROFILE = 1;

	// 49-165차(사용자: "소셜, 프로필 UI가 너무 마크 모드스러워 - 버튼과 입력칸 모양, 픽셀, 크기"): 런처 귓속말 창을 기준으로
	// 다시 잡았다. 패널 260, 줄 높이 30/40, 입력칸 24, 버튼은 채운 보라(테마색)에 흰 글자, 둥근 정도 6(설정 화면과 같은 규격).
	private static final int PANEL_W = 260;
	private static final int MARGIN = 8;
	private static final int ROW_H = 30;
	private static final int PAD = 14;          // 패널 안쪽 여백
	private static final int FIELD_H = 24;      // 입력칸 높이
	private static final int BTN_H = 24;        // 채운 버튼 높이
	private static final int CLOSE_SZ = 22;     // 오른쪽 위 × 버튼

	private final Screen parent;
	private final int mode;
	private int px, py, ph;
	private float scroll;
	private int maxScroll;

	private List<LunaSocial.Friend> friends;
	private boolean loading;
	private String error;
	private long lastFetchNanos;
	private String notice;
	private long noticeUntil;

	// 49-76차(6-12-2, 사용자: "프로필에서 UUID나 닉네임 복사 기능 안 됨"): 49-64차(5-10)가 만든 복사는
	// LunaProfileScreen에 들어갔는데, **그 화면을 여는 곳이 하나도 없었다**(타이틀의 얼굴 칩은 이 화면의
	// MODE_PROFILE을 연다). 죽은 화면에 기능을 넣고 "했다"고 적은 것이다. 여기에 다시 넣고 그 파일은 지웠다.
	private int copiedRow = -1;       // 0 = 닉네임, 1 = UUID
	private long copiedUntil;

	// ==================== 49-74차(5-9): 인게임 친구 추가·삭제·차단 ====================
	// 예전 메모에 "런처 API가 필요하다"고 적어 뒀는데 **틀렸다**. 런처 main.js의 friends:add/remove/block은
	// 모드가 이미 갖고 있는 것과 **똑같은 anon 키**로 Supabase friends 표에 직접 쓰고 있었다. 그래서
	// 런처를 고치지 않아도, Supabase 정책을 새로 넣지 않아도 게임 안에서 그대로 된다(LunaSocial 주석 참고).
	//
	// 화면 폭이 250이라 버튼을 네 개 늘어놓으면 이름이 잘린다. 49-90차(8-18, 사용자: "삭제·차단은 우클릭으로 +
	// 재차 확인"): [관리] 연필과 "정말?" 두 번 누르기를 없애고, 친구 줄을 **우클릭**하면 작은 메뉴(귓속말·참가·
	// 삭제·차단)가 뜨고, 삭제·차단은 {@link LunaConfirm} 창이 한 번 더 묻는다.
	private List<LunaSocial.Friend> requests;
	private final StringBuilder addDraft = new StringBuilder();
	private boolean addFocus;
	// 49-176차(사용자: "귓속말 입력은 마우스로 해당 입력란 클릭해야 적을 수 있고 적는 곳은 색이 있으면 안되지"):
	// 대화 입력칸도 눌러야 입력된다(엔터로도 입력칸을 켤 수 있음). 입력칸은 테마색 없이 무채색.
	private boolean chatFocus;
	private boolean adding;
	private final LunaConfirm confirm = new LunaConfirm();
	/** 우클릭 메뉴가 떠 있는 친구(없으면 null)와 메뉴 왼쪽 위. */
	private LunaSocial.Friend menuFor;
	private int menuX, menuY;
	private static final int MENU_W = 92;
	private static final int MENU_ITEM_H = 18;

	/** 목록에 그리는 한 줄. request면 "들어온 친구 요청"(수락·거절), 아니면 친구. */
	private record Row(LunaSocial.Friend f, boolean request) {
	}

	// ==================== 49-47차: 클라이언트 귓속말 ====================
	// 사용자: "게임 내 귓속말은 마크 귓속말이 아니라 클라이언트 귓속말을 말한 거였어".
	// 예전엔 채팅창을 "/msg 닉네임 "으로 열어 줬다(=게임 안 귓속말, 서버가 있어야 하고 마크 닉네임 기준).
	// 이제 런처와 같은 표(whispers)에 직접 쓰고 읽는다 - 타이틀 화면에서도, 서버가 달라도 된다.
	private LunaSocial.Friend chatWith;
	private List<LunaSocial.Whisper> chatLines;
	private boolean chatLoading;
	private final StringBuilder draft = new StringBuilder();
	private long chatFetchedAt;
	private float chatScroll;
	private int chatMaxScroll;

	public LunaSocialScreen(Screen parent) {
		this(parent, MODE_FRIENDS);
	}

	public LunaSocialScreen(Screen parent, int mode) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral(mode == MODE_PROFILE ? "프로필" : "소셜"));
		this.parent = parent;
		this.mode = mode;
		LunaDraw.resetAnim("social");
		if (mode == MODE_FRIENDS) {
			refresh();
		}
	}

	private void refresh() {
		if (!LunaSocial.signedIn()) {
			friends = List.of();
			return;
		}
		loading = true;
		error = null;
		lastFetchNanos = System.nanoTime();
		// 49-74차: 들어온 친구 요청도 같이 읽는다(추가만 되고 수락은 런처에서만 되면 반쪽이다)
		LunaSocial.fetchRequests().whenComplete((list, t) -> {
			if (minecraft != null) {
				minecraft.execute(() -> requests = t != null || list == null ? List.of() : list);
			}
		});
		LunaSocial.fetchFriends().whenComplete((list, t) -> {
			// 렌더 스레드에서 반영
			if (minecraft != null) {
				minecraft.execute(() -> {
					loading = false;
					if (t != null) {
						error = "친구 목록을 불러오지 못했습니다";
						LunaCompat.warnOnce("social:friends", t);
					} else {
						friends = list;
					}
				});
			}
		});
	}

	// ==================== 49-47차: 귓속말 대화 ====================

	private void openChat(LunaSocial.Friend f) {
		addFocus = false;
		menuFor = null;
		chatWith = f;
		chatFocus = false;
		chatLines = null;
		draft.setLength(0);
		chatScroll = 0;
		// 입력은 화면(Screen)이 직접 받는다 - lunaCharTyped/lunaKeyPressed(인게임용 TextCapture는 필요 없음).
		loadChat();
	}

	private void closeChat() {
		chatWith = null;
		chatFocus = false;
		chatLines = null;
		draft.setLength(0);
	}

	private void loadChat() {
		if (chatWith == null || chatLoading) {
			return;
		}
		chatLoading = true;
		chatFetchedAt = System.currentTimeMillis();
		LunaSocial.Friend target = chatWith;
		LunaSocial.fetchWhispers(target.accountId()).whenComplete((list, t) -> {
			if (minecraft == null) {
				return;
			}
			minecraft.execute(() -> {
				chatLoading = false;
				if (chatWith == target) {
					// 49-225차(제보: "귓속말이 계속 위쪽으로 올라가요"): chatScroll은 "맨 아래에서 올라간 거리"인데 3초마다 새로 읽을 때
					// 아주 큰 값을 넣어서 매번 맨 위(가장 옛날 말)로 튀었다. 처음 열 때만 맨 아래(0)로, 그 뒤엔 보던 자리 그대로.
					if (chatLines == null) {
						chatScroll = 0;
					}
					chatLines = list == null ? List.of() : list;
				}
			});
		});
	}

	private void sendDraft() {
		if (chatWith == null || draft.length() == 0) {
			return;
		}
		String text = draft.toString();
		draft.setLength(0);
		LunaSocial.Friend target = chatWith;
		// 보낸 줄을 먼저 화면에 넣어 준다(전송 실패하면 다시 읽을 때 사라진다)
		if (chatLines != null) {
			List<LunaSocial.Whisper> next = new java.util.ArrayList<>(chatLines);
			next.add(new LunaSocial.Whisper(true, text, ""));
			chatLines = next;
			chatScroll = 0;   // 49-225차: 보내면 맨 아래(방금 보낸 말)로
		}
		LunaSocial.sendWhisper(target, text).whenComplete((ok, t) -> {
			if (minecraft == null) {
				return;
			}
			minecraft.execute(() -> {
				if (!Boolean.TRUE.equals(ok)) {
					notice("보내지 못했습니다 - 친구끼리만 보낼 수 있어요");
				}
				if (chatWith == target) {
					loadChat();
				}
			});
		});
	}

	/**
	 * 친구 한 명과의 대화. 49-165차: 런처 귓속말 창과 같은 생김새 - 위에 "귓속말 - 이름"과 ×(목록으로), 가운데 말풍선
	 * (내 것은 테마색 채움 + 흰 글자, 상대는 어두운 카드), 아래에 높은 입력칸 + [전송] 버튼.
	 */
	private void renderChatPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		// 3초마다 새로 읽어 상대 답장을 받아 온다(실시간 소켓은 없다 - 런처도 같은 방식).
		if (!chatLoading && System.currentTimeMillis() - chatFetchedAt > 3000) {
			loadChat();
		}
		int lx = px + PAD;
		int lw = PANEL_W - PAD * 2;

		int hy = py + PAD;
		LunaDraw.textBold(ctx, font, LunaDraw.ellipsize(font, "귓속말 - " + chatWith.name(), lw - CLOSE_SZ - 10),
			lx, LunaDraw.textY(hy, CLOSE_SZ), LunaDraw.TEXT);
		drawCloseButton(ctx, px + PANEL_W - PAD - CLOSE_SZ, hy, mouseX, mouseY);

		int inputY = py + ph - PAD - FIELD_H;
		int listY = hy + CLOSE_SZ + 10;
		int listH = inputY - 10 - listY;

		if (chatLines == null) {
			LunaDraw.textCentered(ctx, font, "불러오는 중…", px + PANEL_W / 2, listY + listH / 2 - 4, LunaDraw.TEXT_DIM);
		} else if (chatLines.isEmpty()) {
			LunaDraw.textCentered(ctx, font, "아직 주고받은 말이 없습니다", px + PANEL_W / 2, listY + listH / 2 - 4, LunaDraw.TEXT_DIM);
		} else {
			// 높이를 먼저 재서 아래에 붙여 그린다(최신 줄이 항상 보이게)
			int bubbleMax = lw * 3 / 4;
			int padX = 9;
			int padY = 6;
			int lineH = 11;
			int gap = 6;
			int total = 0;
			List<String[]> wrapped = new java.util.ArrayList<>();
			for (LunaSocial.Whisper m : chatLines) {
				List<String> parts = wrap(m.text(), bubbleMax - padX * 2);
				wrapped.add(parts.toArray(new String[0]));
				total += parts.size() * lineH + padY * 2 + gap;
			}
			total -= gap;
			chatMaxScroll = Math.max(0, total - listH);
			chatScroll = Math.max(0, Math.min(chatMaxScroll, chatScroll));
			lunaV.scissor(ctx, lx, listY, lx + lw, listY + listH);
			int y = listY + Math.max(0, listH - total) - (int) (chatMaxScroll > 0 ? chatMaxScroll - chatScroll : 0);
			for (int i = 0; i < chatLines.size(); i++) {
				LunaSocial.Whisper m = chatLines.get(i);
				String[] parts = wrapped.get(i);
				int bw = 0;
				for (String part : parts) {
					bw = Math.max(bw, LunaDraw.width(font, part));
				}
				bw += padX * 2;
				int bh = parts.length * lineH + padY * 2;
				int bx = m.fromMe() ? lx + lw - bw : lx;
				if (y + bh >= listY && y <= listY + listH) {
					// 49-193차: 말풍선 아래 얕은 그림자 + 옆에 보낸 시각
					LunaDraw.roundRect(ctx, bx, y + 1, bw, bh, 6, 0x40000000);
					if (m.fromMe()) {
						LunaDraw.roundRectGradient(ctx, bx, y, bw, bh, 6, LunaDraw.lighten(LunaDraw.ACCENT | 0xFF000000, 0.10f),
							LunaDraw.ACCENT | 0xFF000000);   // 49-172차: 글자색은 아래에서 테마 밝기에 맞춰
					} else {
						LunaDraw.roundRect(ctx, bx, y, bw, bh, 6, LunaClientScreen.ink(0x2A));
					}
					String at = shortTime(m.at());
					if (!at.isEmpty()) {
						int aw = LunaDraw.width(font, at);
						int ax = m.fromMe() ? bx - 4 - aw : bx + bw + 4;
						if (ax >= lx && ax + aw <= lx + lw) {
							LunaDraw.text(ctx, font, at, ax, y + bh - 9, LunaDraw.TEXT_DIM);
						}
					}
					for (int k = 0; k < parts.length; k++) {
						LunaDraw.text(ctx, font, parts[k], bx + padX, y + padY + 1 + k * lineH,
							m.fromMe() ? onAccent() : LunaDraw.TEXT);
					}
				}
				y += bh + gap;
			}
			ctx.disableScissor();
		}

		// 입력칸 + [전송]
		int sendW = LunaDraw.width(font, "전송") + 20;
		int fieldW = lw - sendW - 8;
		drawField(ctx, lx, inputY, fieldW, FIELD_H, draft, chatFocus ? "" : "눌러서 메시지 입력", chatFocus, mouseX, mouseY);
		boolean canSend = draft.length() > 0;
		drawFilledButton(ctx, lx + lw - sendW, inputY, sendW, BTN_H, "전송", canSend,
			canSend && LunaDraw.in(mouseX, mouseY, lx + lw - sendW, inputY, sendW, BTN_H));
	}

	/** 49-165차: 둥근 입력칸(높이 FIELD_H). 비어 있으면 안내 글, 초점이 있으면 테마색 테두리와 깜빡이는 커서. */
	private void drawField(GuiGraphicsExtractor ctx, int x, int y, int w, int h, StringBuilder text, String placeholder,
			boolean focused, int mouseX, int mouseY) {
		boolean hover = LunaDraw.in(mouseX, mouseY, x, y, w, h);
		// 49-176차: 테마색 없이 무채색 - 켜진 입력칸은 테두리만 조금 더 밝게
		// 49-227차(사진 시안): 공용 입력칸(깊은 속 + 테두리, 초점이면 테마색)
		LunaDraw.field3d(ctx, x, y, w, h, 5, focused, hover);
		// 49-221차: 조합 중인 한글(IME 미리보기)도 글 뒤에 붙여 보여 주고 밑줄을 긋는다
		String pre = focused && lunaPreedit != null ? lunaPreedit : "";
		String full = text.toString() + pre;
		boolean empty = full.isEmpty();
		String shown = empty ? placeholder : full;
		int maxW = w - 20;
		while (LunaDraw.width(font, shown) > maxW && shown.length() > 1) {
			shown = shown.substring(1);
		}
		LunaDraw.text(ctx, font, shown, x + 9, LunaDraw.textY(y, h), empty ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
		if (!pre.isEmpty() && shown.length() >= pre.length()) {
			int ux0 = x + 9 + LunaDraw.width(font, shown.substring(0, shown.length() - pre.length()));
			int ux1 = x + 9 + LunaDraw.width(font, shown);
			ctx.fill(ux0, y + h - 7, ux1, y + h - 6, LunaDraw.TEXT);
		}
		if (focused && pre.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = empty ? x + 9 : x + 9 + LunaDraw.width(font, shown) + 1;
			ctx.fill(cx, y + 6, cx + 1, y + h - 6, LunaDraw.TEXT);
		}
	}

	// ==================== 49-193차: 입체감(사용자: "버튼이 입체감이 아예 없어, 소셜 UI 전체적으로") ====================
	// 버튼 = 아래로 1px 그림자 + 위가 조금 밝은 그라데이션 + 위쪽 안쪽 밝은 선(빛 받는 면). 누르고 있으면 그림자 없이
	// 1px 내려앉는다. 입력칸은 반대로 안쪽 위에 어두운 선(파인 느낌). 카드(친구 줄)는 얕은 그림자 + 윗선, 올리면 1px 뜬다.

	/** 왼쪽 버튼을 누르고 있는지(누른 느낌을 그리려고). */
	private boolean mouseHeld() {
		try {
			return kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 0);
		} catch (Throwable t) {
			return false;
		}
	}

	private static int shade(int argb, float k) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.round(((argb >> 16) & 0xFF) * k);
		int g = Math.round(((argb >> 8) & 0xFF) * k);
		int b = Math.round((argb & 0xFF) * k);
		return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	/** 입체 판. 반환 = 누른 만큼 내려간 y(글자도 같이 내려 그리려고). */
	private int raised(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int fill, boolean hovered) {
		// 49-227차(사진 시안): 공용 입체 버튼. 누르고 있으면 두께만큼(1px) 내려앉는다.
		boolean pressed = hovered && mouseHeld();
		int yy = pressed ? y + 1 : y;
		int kind = fill == (LunaDraw.ACCENT | 0xFF000000) ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL;
		LunaDraw.button3d(ctx, x, yy, w, h, Math.min(5, h / 2), kind, hovered ? 1f : 0f);
		return yy;
	}

	/** 버튼 안 글자 세로 가운데 - 한글은 잉크 중심이 0.5px 아래라 글자별 중심으로 맞춘다("귓속말 글이 밑에 있다"). */
	private int btnTextY(int y, int h, String label) {
		return y + Math.round(h / 2f - LunaCompat.textVisualCenter(label));
	}

	private void btnLabel(GuiGraphicsExtractor ctx, String label, int x, int y, int w, int h, int color) {
		int tw = LunaDraw.width(font, label);
		LunaDraw.text(ctx, font, label, x + (w - tw) / 2, btnTextY(y, h, label), color);
	}

	/** 카드(친구, 계정 줄) - 얕은 그림자 + 윗선, 올리면 1px 뜬다. 반환 = 그려진 y. */
	private int card(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int fill, int border, boolean hovered) {
		// 49-227차(사진 시안): 공용 카드(아래 두께 + 테두리). 메뉴가 떠 있는 줄(border 있음)은 테마색 테두리.
		LunaDraw.card3d(ctx, x, y, w, h, hovered ? 1f : 0f, border != 0);
		return y;
	}

	/** 49-165차: 테마색으로 채운 버튼(흰 글자). enabled가 아니면 흐리게(평평하게). 49-193차: 입체. */
	private void drawFilledButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean enabled, boolean hovered) {
		if (!enabled) {
			LunaDraw.roundRect(ctx, x, y, w, h, Math.min(6, h / 2), LunaClientScreen.ink(0x1E));
			btnLabel(ctx, label, x, y, w, h, LunaDraw.TEXT_DIM);
			return;
		}
		int yy = raised(ctx, x, y, w, h, LunaDraw.ACCENT | 0xFF000000, hovered);
		btnLabel(ctx, label, x, yy, w, h, onAccent());
	}

	/** 49-165차: 속이 살짝 찬 보조 버튼(테마색 글자). */
	/** 49-172차(사용자: "테마색이 흰색이면 귓속말이 안 보임"): 테마색 위 글자는 테마 밝기에 따라 흰/검. */
	private static int onAccent() {
		return kr.lunaslight.mod.util.LunaTheme.luminance(LunaDraw.ACCENT) > 0.55f ? 0xFF14181C : 0xFFFFFFFF;
	}

	/** 보조 버튼 - 무채색 입체 판에 테마색 글자. */
	private void drawGhostButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean hovered) {
		int base = (LunaClientScreen.themeCard() & 0x00FFFFFF) | 0xFF000000;
		int yy = raised(ctx, x, y, w, h, LunaDraw.lighten(base, 0.10f), hovered);
		btnLabel(ctx, label, x, yy, w, h, hovered ? LunaDraw.TEXT : LunaDraw.ACCENT);
	}

	/** 49-165차: 오른쪽 위 × - 런처처럼 둥근 네모 안에 ×. */
	private void drawCloseButton(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
		boolean hover = LunaDraw.in(mouseX, mouseY, x, y, CLOSE_SZ, CLOSE_SZ);
		iconButton(ctx, x, y, CLOSE_SZ, LunaIcons.CLOSE, hover, true);
	}

	/** 네모 아이콘 버튼(×, 새로 고침) - 입체. */
	private void iconButton(GuiGraphicsExtractor ctx, int x, int y, int size, String icon, boolean hover, boolean on) {
		int base = (LunaClientScreen.themeCard() & 0x00FFFFFF) | 0xFF000000;
		int yy = raised(ctx, x, y, size, size, LunaDraw.lighten(base, 0.08f), hover);
		LunaIcons.draw(ctx, font, icon, x + (size - 10) / 2, LunaDraw.iconY(yy, size),
			!on ? LunaDraw.TEXT_DIM : hover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
	}

	/** 49-193차: 보낸 시각("2026-09-29T15:04:05Z" 등) → 내 시간대 "15:04"(오늘이 아니면 "9/28"). 못 읽으면 빈 값. */
	private static String shortTime(String at) {
		if (at == null || at.isEmpty()) {
			return "";
		}
		try {
			java.time.ZonedDateTime t = java.time.OffsetDateTime.parse(at).atZoneSameInstant(java.time.ZoneId.systemDefault());
			if (t.toLocalDate().equals(java.time.LocalDate.now())) {
				return String.format(java.util.Locale.ROOT, "%d:%02d", t.getHour(), t.getMinute());
			}
			return t.getMonthValue() + "/" + t.getDayOfMonth();
		} catch (Throwable e) {
			try {
				java.time.ZonedDateTime t = java.time.Instant.parse(at).atZone(java.time.ZoneId.systemDefault());
				return String.format(java.util.Locale.ROOT, "%d:%02d", t.getHour(), t.getMinute());
			} catch (Throwable ignored) {
				return "";
			}
		}
	}

	/** 말풍선 줄바꿈(폭 안에 들어가게 글자 단위로 자른다 - 한국어라 단어 단위론 안 맞는다). */
	private List<String> wrap(String text, int maxW) {
		List<String> out = new java.util.ArrayList<>();
		if (text == null || text.isEmpty()) {
			out.add("");
			return out;
		}
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			line.append(text.charAt(i));
			if (LunaDraw.width(font, line.toString()) > maxW) {
				line.setLength(line.length() - 1);
				out.add(line.toString());
				line.setLength(0);
				line.append(text.charAt(i));
			}
		}
		out.add(line.toString());
		return out;
	}

	private void notice(String s) {
		notice = s;
		noticeUntil = System.currentTimeMillis() + 3000;
	}

	private boolean right() {
		return mode == MODE_FRIENDS;
	}

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		// 49-142차: 설정 화면과 같은 다크/라이트
		LunaClientScreen.applyScreenTheme();
		tip = null;
		try {
			renderSocial(ctx, mouseX, mouseY, delta);
		} finally {
			LunaClientScreen.restoreScreenTheme();
		}
		if (tip != null) {
			ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	/** 49-172차: 이번 프레임 호버 설명. */
	private String tip;

	private void renderSocial(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		// 뒤: 부모 화면(타이틀) 그대로 + 어두운 막
		boolean drewParent = false;
		if (parent != null) {
			try {
				// 49-45차: 1.15.2~1.19.4는 render의 첫 인자가 MatrixStack이라 직접 못 부른다.
				// 49-46차: width/height를 넘겨 부모가 옛 창 크기로 남아 있으면 맞춰 그리게 한다.
				drewParent = LunaCompat.renderParentScreen(parent, ctx, width, height, -1, -1, delta);
			} catch (Throwable t) {
				LunaCompat.warnOnce("social:parentRender", t);
			}
		}
		if (!drewParent) {
			LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, 0xA6000000);
		}
		float open = LunaDraw.animFrom("social", 0f, 1f, 18f);
		ctx.fill(0, 0, width, height, LunaDraw.withAlpha(0x000000, Math.round(0x66 * open)));
		LunaDraw.setAlpha(open);

		ph = height - MARGIN * 2;
		py = MARGIN;
		int slide = Math.round((1f - open) * 24f);
		px = right() ? width - MARGIN - PANEL_W + slide : MARGIN - slide;
		LunaDraw.panel3d(ctx, px, py, PANEL_W, ph, 8);   // 49-227차: 사진 시안 판

		if (chatWith != null) {
			renderChatPanel(ctx, mouseX, mouseY);
		} else if (right()) {
			renderFriendsPanel(ctx, mouseX, mouseY);
		} else {
			renderProfilePanel(ctx, mouseX, mouseY);
		}
		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			LunaDraw.textCentered(ctx, font, notice, px + PANEL_W / 2, py + ph - PAD - 10, LunaDraw.ACCENT);
		}
		if (menuFor != null && chatWith == null && right()) {
			renderMenu(ctx, mouseX, mouseY);
		}
		confirm.render(ctx, font, width, height, mouseX, mouseY);
		LunaDraw.setAlpha(1f);
	}

	// ==================== 49-90차(8-18): 우클릭 메뉴 ====================

	/** 메뉴 항목 - 위에서부터. 귓속말·참가는 될 때만, 삭제·차단은 항상. */
	private List<String> menuItems(LunaSocial.Friend f) {
		List<String> items = new java.util.ArrayList<>();
		if (canWhisper()) {
			items.add("귓속말");
		}
		if (f.canJoin()) {
			items.add("참가");
		}
		items.add("삭제");
		items.add("차단");
		return items;
	}

	private void openMenu(LunaSocial.Friend f, double mouseX, double mouseY) {
		menuFor = f;
		int h = menuItems(f).size() * MENU_ITEM_H + 8;
		menuX = (int) Math.min(mouseX, width - MENU_W - 4);
		menuY = (int) Math.min(mouseY, height - h - 4);
		LunaDraw.resetAnim("social:menu");
	}

	private void renderMenu(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		List<String> items = menuItems(menuFor);
		int h = items.size() * MENU_ITEM_H + 8;
		float pop = LunaDraw.animFrom("social:menu", 0f, 1f, 22f);
		int y0 = menuY - Math.round((1f - pop) * 4f);
		LunaDraw.shadow(ctx, menuX, y0, MENU_W, h, 6);
		LunaDraw.card3d(ctx, menuX, y0, MENU_W, h, 5, 0, 0);   // 49-227차
		int y = y0 + 4;
		for (String item : items) {
			boolean danger = "삭제".equals(item) || "차단".equals(item);
			boolean hov = LunaDraw.in(mouseX, mouseY, menuX + 4, y, MENU_W - 8, MENU_ITEM_H);
			if (hov) {
				LunaDraw.roundRect(ctx, menuX + 4, y, MENU_W - 8, MENU_ITEM_H, 4,
					danger ? 0x2ECF7B74 : LunaDraw.CARD_HOVER);
			}
			if (danger) {
				LunaIcons.draw(ctx, font, "삭제".equals(item) ? LunaIcons.TRASH : LunaIcons.BAN,
					menuX + 10, LunaDraw.iconY(y, MENU_ITEM_H), hov ? 0xFFFF8B82 : 0xFFCF7B74);
			}
			LunaDraw.text(ctx, font, item, menuX + (danger ? 26 : 12), LunaDraw.textY(y, MENU_ITEM_H),
				danger ? (hov ? 0xFFFF8B82 : 0xFFCF7B74) : (hov ? LunaDraw.TEXT : LunaDraw.TEXT_SUB));
			y += MENU_ITEM_H;
		}
	}

	/** 메뉴가 떠 있을 때의 클릭. 항목이면 실행, 아니면 그냥 닫는다(그 클릭은 버린다). */
	private void handleMenuClick(double mouseX, double mouseY, int button) {
		LunaSocial.Friend f = menuFor;
		List<String> items = menuItems(f);
		menuFor = null;
		if (button != 0) {
			return;
		}
		int y = menuY + 4;
		for (String item : items) {
			if (LunaDraw.in(mouseX, mouseY, menuX + 4, y, MENU_W - 8, MENU_ITEM_H)) {
				switch (item) {
					case "귓속말" -> openChat(f);
					case "참가" -> {
						if (!LunaSocial.join(minecraft, f)) {
							notice("바로 접속하지 못해 주소를 복사했습니다");
						}
					}
					case "삭제" -> confirm.show(LunaIcons.TRASH, f.name() + " 님을 친구에서 지울까요?",
						"다시 추가하려면 상대가 요청을 받아야 합니다", "삭제", () -> run(f, "del"));
					case "차단" -> confirm.show(LunaIcons.BAN, f.name() + " 님을 차단할까요?",
						"친구에서 지워지고 요청/귓속말을 받지 않습니다", "차단", () -> run(f, "block"));
					default -> {
					}
				}
				return;
			}
			y += MENU_ITEM_H;
		}
	}

	/** 헤더: 제목(굵게) + 옆에 작은 부제 + 오른쪽 위 ×. 49-165차: 아이콘 상자 대신 런처처럼 글자만, ×는 둥근 네모. */
	private void renderHeader(GuiGraphicsExtractor ctx, String icon, String title, String sub, int mouseX, int mouseY) {
		int hy = py + PAD;
		int lx = px + PAD;
		LunaDraw.textBold(ctx, font, title, lx, LunaDraw.textY(hy, CLOSE_SZ), LunaDraw.TEXT);
		if (sub != null && !sub.isEmpty()) {
			int tw = LunaDraw.widthBold(font, title);
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, sub, PANEL_W - PAD * 2 - tw - 10 - CLOSE_SZ - 8),
				lx + tw + 10, LunaDraw.textY(hy, CLOSE_SZ), LunaDraw.TEXT_DIM);
		}
		drawCloseButton(ctx, px + PANEL_W - PAD - CLOSE_SZ, hy, mouseX, mouseY);
		LunaDraw.fadeLine(ctx, lx, hy + CLOSE_SZ + 5, PANEL_W - PAD * 2, LunaDraw.ACCENT);
	}

	/** 헤더 아래 첫 내용이 시작하는 y. */
	private int contentTop() {
		return py + PAD + CLOSE_SZ + 12;
	}

	// ==================== 친구(오른쪽) ====================

	private void renderFriendsPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		String me = LunaSocial.signedIn() ? LunaSocial.siteName() : "로그인 필요";
		renderHeader(ctx, LunaIcons.USERS, "소셜", me, mouseX, mouseY);

		if (LunaSocial.signedIn()) {
			renderAddRow(ctx, mouseX, mouseY);
		}
		int ty = listY() - 22;
		String label = "친구" + (friends == null ? "" : " " + friends.size());
		LunaDraw.text(ctx, font, label, px + PAD, LunaDraw.textY(ty, 18), LunaDraw.TEXT_SUB);
		if (LunaSocial.signedIn()) {
			int rw = 22;
			int rx = px + PANEL_W - PAD - rw;
			boolean rh = LunaDraw.in(mouseX, mouseY, rx, ty - 2, rw, rw);
			iconButton(ctx, rx, ty - 2, rw, LunaIcons.RESET, rh, !loading);
		}

		int lx = px + PAD;
		int ly = listY();
		int lw = PANEL_W - PAD * 2;
		int lh = listH();
		lunaV.scissor(ctx, lx, ly, lx + lw, ly + lh);
		try {
			renderFriends(ctx, lx, ly, lw, lh, mouseX, mouseY);
		} finally {
			ctx.disableScissor();
		}
		if (maxScroll > 0) {
			int thumbH = Math.max(20, lh * lh / (lh + maxScroll));
			int thumbY = ly + (int) ((lh - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, lx + lw + 5, thumbY, 3, thumbH, 1, LunaClientScreen.ink(0x38));
		}
	}

	private static int presenceColor(String presence) {
		return switch (presence) {
			case "online" -> 0xFF5AD86E;
			case "away" -> 0xFFF2C94C;
			default -> 0xFF5A5E66;
		};
	}

	private static String presenceLabel(String presence) {
		return switch (presence) {
			case "online" -> "온라인";
			case "away" -> "자리 비움";
			default -> "오프라인";
		};
	}

	/** 목록이 시작하는 y. 49-74차에 위로 "친구 추가" 줄이 들어와서 한 칸 내려갔다. */
	private int listY() {
		// 헤더 → (로그인이면 친구 추가 줄) → "친구 N" 제목 줄 → 목록
		return contentTop() + (LunaSocial.signedIn() ? FIELD_H + 12 : 0) + 22;
	}

	private int listH() {
		return ph - (listY() - py) - PAD - (notice != null && System.currentTimeMillis() < noticeUntil ? 14 : 0);
	}

	/** 49-74차(5-9): 닉네임을 쓰고 [추가]를 누르면 친구 요청이 나간다. 49-165차: 높은 입력칸 + 채운 버튼. */
	private void renderAddRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int y = contentTop();
		int lx = px + PAD;
		int lw = PANEL_W - PAD * 2;
		int bw = LunaDraw.width(font, "추가") + 20;
		int fw = lw - bw - 8;
		drawField(ctx, lx, y, fw, FIELD_H, addDraft, "닉네임으로 친구 추가", addFocus, mouseX, mouseY);
		boolean can = !adding && addDraft.length() > 0;
		drawFilledButton(ctx, lx + lw - bw, y, bw, BTN_H, adding ? "…" : "추가", can,
			can && LunaDraw.in(mouseX, mouseY, lx + lw - bw, y, bw, BTN_H));
	}

	/** 요청(위) + 친구(아래). 둘 다 같은 높이라 스크롤 계산이 한 군데로 모인다. */
	private List<Row> rows() {
		List<Row> out = new java.util.ArrayList<>();
		if (requests != null) {
			for (LunaSocial.Friend r : requests) {
				out.add(new Row(r, true));
			}
		}
		if (friends != null) {
			for (LunaSocial.Friend f : friends) {
				out.add(new Row(f, false));
			}
		}
		return out;
	}

	private void renderFriends(GuiGraphicsExtractor ctx, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
		if (!LunaSocial.signedIn()) {
			maxScroll = 0;
			String[] msg = LunaSocial.available()
				? new String[]{"런처에서 노바 계정으로", "로그인하면 친구가 보입니다"}
				: new String[]{"Nova Client 런처로 실행해야", "친구 기능을 쓸 수 있습니다"};
			LunaDraw.textCentered(ctx, font, msg[0], lx + lw / 2, ly + lh / 2 - 10, LunaDraw.TEXT_DIM);
			LunaDraw.textCentered(ctx, font, msg[1], lx + lw / 2, ly + lh / 2 + 2, LunaDraw.TEXT_DIM);
			return;
		}
		if (loading && friends == null) {
			maxScroll = 0;
			LunaDraw.textCentered(ctx, font, "불러오는 중…", lx + lw / 2, ly + lh / 2 - 4, LunaDraw.TEXT_DIM);
			return;
		}
		if (error != null && (friends == null || friends.isEmpty())) {
			maxScroll = 0;
			LunaDraw.textCentered(ctx, font, error, lx + lw / 2, ly + lh / 2 - 4, 0xFFCF7B74);
			return;
		}
		List<Row> list = rows();
		if (list.isEmpty()) {
			maxScroll = 0;
			LunaDraw.textCentered(ctx, font, "아직 친구가 없습니다", lx + lw / 2, ly + lh / 2 - 10, LunaDraw.TEXT_DIM);
			LunaDraw.textCentered(ctx, font, "위에 닉네임을 쓰고 [추가]", lx + lw / 2, ly + lh / 2 + 2, LunaDraw.TEXT_DIM);
			return;
		}
		maxScroll = Math.max(0, list.size() * rowH() - lh);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int y = ly - (int) scroll;
		for (Row row : list) {
			LunaSocial.Friend f = row.f();
			int rh = rowH() - 4;
			if (y + rh >= ly && y <= ly + lh) {
				boolean inList = LunaDraw.in(mouseX, mouseY, lx, ly, lw, lh);
				boolean hov = inList && LunaDraw.in(mouseX, mouseY, lx, y, lw, rh);
				boolean menu = !row.request() && f == menuFor;
				// 49-165차: 테두리 상자 대신 살짝 찬 둥근 카드(hover면 밝게), 메뉴가 떠 있으면 테마색 테두리
				// 49-167차: 테두리도 텍스처 원(roundRectBorderedFlat)으로 - fill 계단 테두리는 모서리가 거칠었다
				int rowFill = hov || menu ? LunaClientScreen.ink(0x22) : LunaClientScreen.ink(0x12);
				int cardY = card(ctx, lx, y, lw, rh, rowFill, menu ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x8C) : 0, hov || menu);
				int lift = cardY - y;   // 올라간 만큼 안의 것도 같이
				y += lift;
				boolean offline = "offline".equals(f.presence());
				// 왼쪽: 얼굴(마크 닉네임을 알면 스킨 얼굴, 아니면 사람 아이콘) + 접속 점
				int face = 20;
				int fx = lx + 8;
				int fy = y + (rh - face) / 2;
				Identifier skin = row.request() || f.mcName() == null || f.mcName().isEmpty() ? null
					: LunaCompat.playerSkinByName(minecraft, f.mcName());
				if (skin == null || !LunaGfx.drawPlayerFace(ctx, skin, fx, fy, face, LunaDraw.applyAlpha(offline ? 0x99FFFFFF : 0xFFFFFFFF))) {
					LunaDraw.roundRect(ctx, fx, fy, face, face, 6, LunaClientScreen.ink(0x1E));
					LunaIcons.draw(ctx, font, LunaIcons.USER, fx + 5, LunaDraw.iconY(fy, face), row.request() ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
				}
				if (!row.request()) {
					LunaDraw.circle(ctx, fx + face - 6, fy + face - 6, 8, LunaClientScreen.themeBg() | 0xFF000000);
					LunaDraw.circle(ctx, fx + face - 5, fy + face - 5, 6, presenceColor(f.presence()));
				}
				int textX = fx + face + 9;
				// 버튼이 나와 있으면 이름을 더 짧게 자른다 - 겹치면 글자가 뭉개진다
				int nameW = hov || row.request() ? 78 : lw - (textX - lx) - 8;
				LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, f.name(), nameW), textX, y + 8,
					row.request() || !offline ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
				String place = row.request() ? "" : f.place();
				String sub = row.request() ? "친구 요청이 왔습니다"
					: (!place.isEmpty() ? place
						: (f.status() != null && !f.status().isEmpty() ? f.status() : presenceLabel(f.presence())));
				LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, sub, lw - (textX - lx) - 8), textX, y + 21,
					row.request() ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);

				int by = y + (rh - MINI_H) / 2;
				int bx = lx + lw - 8;
				if (row.request()) {
					int bw = btnW("수락");
					bx -= bw;
					drawFilledButton(ctx, bx, by, bw, MINI_H, "수락", true, LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H));
					bx -= 6;
					bw = btnW("거절");
					bx -= bw;
					drawGhostButton(ctx, bx, by, bw, MINI_H, "거절", LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H));
				} else if (hov) {
					if (canWhisper()) {
						int bw = btnW("귓속말");
						bx -= bw;
						drawFilledButton(ctx, bx, by, bw, MINI_H, "귓속말", true, LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H));
						bx -= 6;
					}
					// 49-172차(사용자: "인게임에서 참가하기 버튼이 없음"): 접속 중인 친구면 늘 [참가]를 보이고, 서버 정보가
					// 없으면(런처만 켜 둔 상태) 흐리게 + 설명
					if (!offline) {
						int bw = btnW("참가");
						bx -= bw;
						boolean hv = LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H);
						if (f.canJoin()) {
							drawGhostButton(ctx, bx, by, bw, MINI_H, "참가", hv);
						} else {
							LunaDraw.roundRect(ctx, bx, by, bw, MINI_H, 6, LunaClientScreen.ink(0x14));
							btnLabel(ctx, "참가", bx, by, bw, MINI_H, LunaDraw.TEXT_DIM);
							if (hv) {
								tip = "게임에 접속 중이 아니라 참가할 수 없습니다";
							}
						}
						bx -= 6;
					}
				}
				y -= lift;
			}
			y += rowH();
		}
	}

	/** 친구 한 줄의 높이(이름 + 접속 위치 두 줄) - 49-165차: 34 → 42(줄 사이 4). */
	private int rowH() {
		return 42;
	}

	/** 줄 안 작은 버튼 높이. */
	private static final int MINI_H = 18;

	/** 49-47차: 클라이언트 귓속말이라 게임에 접속해 있지 않아도 된다 - 사이트 로그인만 있으면 된다. */
	private boolean canWhisper() {
		return LunaSocial.signedIn();
	}

	private int btnW(String label) {
		return LunaDraw.width(font, label) + 16;
	}

	/** 친구 줄의 클릭(우클릭 = 메뉴, 참가 · 귓속말 · 수락 · 거절). 처리했으면 true. */
	private boolean handleFriendClick(double mouseX, double mouseY, int button) {
		List<Row> list = rows();
		if (list.isEmpty()) {
			return false;
		}
		int lx = px + PAD;
		int ly = listY();
		int lw = PANEL_W - PAD * 2;
		int lh = listH();
		if (!LunaDraw.in(mouseX, mouseY, lx, ly, lw, lh)) {
			return false;
		}
		int y = ly - (int) scroll;
		for (Row row : list) {
			LunaSocial.Friend f = row.f();
			int rh = rowH() - 4;
			if (LunaDraw.in(mouseX, mouseY, lx, y, lw, rh)) {
				if (!row.request() && button == 1) {
					openMenu(f, mouseX, mouseY);
					return true;
				}
				if (button != 0) {
					return true;
				}
				int by = y + (rh - MINI_H) / 2;
				int bx = lx + lw - 8;
				if (row.request()) {
					for (String label : new String[]{"수락", "거절"}) {
						int bw = btnW(label);
						bx -= bw;
						if (LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H)) {
							answerRequest(f, "수락".equals(label));
							return true;
						}
						bx -= 6;
					}
					return true;
				}
				if (canWhisper()) {
					int bw = btnW("귓속말");
					bx -= bw;
					if (LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H)) {
						openChat(f);
						return true;
					}
					bx -= 6;
				}
				if (!"offline".equals(f.presence())) {
					int bw = btnW("참가");
					bx -= bw;
					if (LunaDraw.in(mouseX, mouseY, bx, by, bw, MINI_H)) {
						if (!f.canJoin()) {
							notice("게임에 접속 중이 아니라 참가할 수 없습니다");
						} else if (!LunaSocial.join(minecraft, f)) {
							notice("바로 접속하지 못해 주소를 복사했습니다");
						}
						return true;
					}
				}
				// 49-193차: 버튼 밖(줄 아무 데나)을 눌러도 귓속말이 열린다 - 작은 버튼만 겨냥하지 않아도 되게
				if (canWhisper()) {
					openChat(f);
				}
				return true;
			}
			y += rowH();
		}
		return false;
	}

	/** 삭제·차단 실행 - 확인 창에서 [삭제]/[차단]을 눌렀을 때. */
	private void run(LunaSocial.Friend f, String kind) {
		String name = f.name();
		java.util.concurrent.CompletableFuture<String> job =
			"del".equals(kind) ? LunaSocial.removeFriend(f.id()) : LunaSocial.blockFriend(f);
		job.whenComplete((err, t) -> {
			if (minecraft == null) {
				return;
			}
			minecraft.execute(() -> {
				if (t != null || err != null) {
					notice(err == null ? "처리하지 못했습니다" : err);
				} else {
					notice(name + ("del".equals(kind) ? " 님을 친구에서 지웠습니다" : " 님을 차단했습니다"));
					refresh();
				}
			});
		});
	}

	/** 들어온 요청 수락/거절(거절은 삭제와 같은 동작 - 한 줄을 지우는 것이다). */
	private void answerRequest(LunaSocial.Friend f, boolean accept) {
		String name = f.name();
		java.util.concurrent.CompletableFuture<String> job =
			accept ? LunaSocial.acceptRequest(f.id()) : LunaSocial.removeFriend(f.id());
		job.whenComplete((err, t) -> {
			if (minecraft == null) {
				return;
			}
			minecraft.execute(() -> {
				if (t != null || err != null) {
					notice(err == null ? "처리하지 못했습니다" : err);
				} else {
					notice(accept ? name + " 님과 친구가 됐습니다" : "요청을 거절했습니다");
					refresh();
				}
			});
		});
	}

	/** [추가] - 닉네임으로 친구 요청. 결과는 아래 안내 줄에 그대로 보여 준다. */
	private void submitAdd() {
		if (adding || addDraft.length() == 0) {
			return;
		}
		adding = true;
		String nick = addDraft.toString();
		addDraft.setLength(0);
		LunaSocial.addFriend(nick).whenComplete((msg, t) -> {
			if (minecraft == null) {
				return;
			}
			minecraft.execute(() -> {
				adding = false;
				notice(t != null ? "보내지 못했습니다" : msg);
				refresh();
			});
		});
	}

	// ==================== 프로필(왼쪽) ====================

	// 49-165차: 프로필 자리(클릭 판정과 그리기가 같은 수를 쓴다)
	private static final int FACE = 48;

	private int profileFaceY() {
		return contentTop();
	}

	private int profileStatsY() {
		return profileFaceY() + FACE + 14;
	}

	private int profileListY() {
		return profileStatsY() + BTN_H + 12 + 22;
	}

	private void renderProfilePanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		renderHeader(ctx, LunaIcons.USER, "프로필", null, mouseX, mouseY);

		// 큰 얼굴 + 닉네임 + UUID
		int face = FACE;
		int fx = px + PAD;
		int fy = profileFaceY();
		Identifier skin = LunaCompat.playerSkinTexture(minecraft);
		LunaDraw.roundRect(ctx, fx - 3, fy - 1, face + 6, face + 6, 8, 0x59000000);
		LunaDraw.roundRectGradient(ctx, fx - 3, fy - 3, face + 6, face + 6, 8, LunaClientScreen.ink(0x2A), LunaClientScreen.ink(0x14));
		if (!LunaGfx.drawPlayerFace(ctx, skin, fx, fy, face, LunaDraw.applyAlpha(0xFFFFFFFF))) {
			LunaDraw.roundRect(ctx, fx, fy, face, face, 6, LunaDraw.CARD);
			LunaIcons.drawLarge(ctx, font, LunaIcons.USER, fx + (face - LunaIcons.SIZE_LG) / 2, LunaDraw.iconLgY(fy, face), LunaDraw.TEXT_DIM);
		}
		String name = LunaCompat.sessionName(minecraft);
		int tx = fx + face + 12;
		int tw = PANEL_W - PAD * 2 - face - 12;
		boolean nameHover = LunaDraw.in(mouseX, mouseY, tx, fy + 4, tw, 12);
		boolean nameCopied = copiedRow == 0 && System.currentTimeMillis() < copiedUntil;
		LunaDraw.textBold(ctx, font, nameCopied ? "복사됨" : name, tx, fy + 6,
			nameCopied ? LunaDraw.ACCENT : (nameHover ? LunaDraw.ACCENT : LunaDraw.TEXT));
		String uuid = LunaSocial.currentUuid(minecraft);
		if (uuid != null && uuid.length() >= 8) {
			boolean uuidHover = LunaDraw.in(mouseX, mouseY, tx, fy + 20, tw, 12);
			boolean uuidCopied = copiedRow == 1 && System.currentTimeMillis() < copiedUntil;
			// 화면엔 잘라서 보여 주지만 복사는 언제나 원본 전체다(잘린 걸 복사해 주면 쓸모가 없다)
			LunaDraw.text(ctx, font, uuidCopied ? "복사됨" : uuid.substring(0, 8) + "…  §8(눌러서 복사)", tx, fy + 22,
				uuidCopied ? LunaDraw.ACCENT : (uuidHover ? LunaDraw.TEXT_SUB : LunaDraw.TEXT_DIM));
		}
		String site = LunaSocial.signedIn() ? "노바 계정 | " + LunaSocial.siteName() : "노바 계정 로그인 안 됨";
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, site, tw), tx, fy + 36,
			LunaSocial.signedIn() ? LunaDraw.TEXT_SUB : LunaDraw.TEXT_DIM);

		// 49-27차: 내 기록(통계) 바로가기 - 49-165차: 채운 버튼 한 줄
		int sy2 = profileStatsY();
		int bw = PANEL_W - PAD * 2;
		boolean statsHover = LunaDraw.in(mouseX, mouseY, px + PAD, sy2, bw, BTN_H);
		int sy3 = raised(ctx, px + PAD, sy2, bw, BTN_H, LunaDraw.ACCENT | 0xFF000000, statsHover);
		int lw2 = LunaDraw.width(font, "내 기록 보기") + 16;
		int ix = px + PAD + (bw - lw2) / 2;
		LunaIcons.draw(ctx, font, LunaIcons.CHART, ix, LunaDraw.iconY(sy3, BTN_H), onAccent());
		LunaDraw.text(ctx, font, "내 기록 보기", ix + 16, btnTextY(sy3, BTN_H, "내 기록 보기"), onAccent());

		int ty = profileListY() - 22;
		LunaDraw.text(ctx, font, "계정 전환", px + PAD, LunaDraw.textY(ty, 18), LunaDraw.TEXT_SUB);

		int lx = px + PAD;
		int ly = profileListY();
		int lw = PANEL_W - PAD * 2;
		int lh = ph - (ly - py) - PAD - (notice != null && System.currentTimeMillis() < noticeUntil ? 14 : 0);
		lunaV.scissor(ctx, lx, ly, lx + lw, ly + lh);
		try {
			renderProfiles(ctx, lx, ly, lw, lh, mouseX, mouseY);
		} finally {
			ctx.disableScissor();
		}
		if (maxScroll > 0) {
			int thumbH = Math.max(20, lh * lh / (lh + maxScroll));
			int thumbY = ly + (int) ((lh - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, lx + lw + 5, thumbY, 3, thumbH, 1, LunaClientScreen.ink(0x38));
		}
	}

	private void renderProfiles(GuiGraphicsExtractor ctx, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
		List<LunaSocial.Account> list = LunaSocial.accounts();
		if (list.isEmpty()) {
			maxScroll = 0;
			String[] msg = LunaSocial.available()
				? new String[]{"런처에 등록된", "마인크래프트 계정이 없습니다"}
				: new String[]{"Nova Client 런처로 실행해야", "계정을 바꿀 수 있습니다"};
			LunaDraw.textCentered(ctx, font, msg[0], lx + lw / 2, ly + 24, LunaDraw.TEXT_DIM);
			LunaDraw.textCentered(ctx, font, msg[1], lx + lw / 2, ly + 36, LunaDraw.TEXT_DIM);
			return;
		}
		maxScroll = Math.max(0, list.size() * ROW_H - lh);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		String current = LunaSocial.currentUuid(minecraft);
		int y = ly - (int) scroll;
		for (LunaSocial.Account a : list) {
			if (y + ROW_H >= ly && y <= ly + lh) {
				boolean active = a.uuid().replace("-", "").equalsIgnoreCase(current);
				int rh = ROW_H - 4;
				boolean hov = !active && LunaDraw.in(mouseX, mouseY, lx, y, lw, rh) && LunaDraw.in(mouseX, mouseY, lx, ly, lw, lh);
				// 49-165차: 둥근 카드, 사용 중이면 테마색 테두리
				int rowFill = hov ? LunaClientScreen.ink(0x22) : LunaClientScreen.ink(0x12);
				int y0 = y;
				y = card(ctx, lx, y, lw, rh, rowFill, active ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x8C) : 0, hov);
				LunaIcons.draw(ctx, font, LunaIcons.USER, lx + 9, LunaDraw.iconY(y, rh), active ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
				LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, a.name(), lw - 26 - 64), lx + 26,
					LunaDraw.textY(y, rh), active ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
				String tag = active ? "사용 중" : (hov ? "전환" : "");
				if (!tag.isEmpty()) {
					int tw = LunaDraw.width(font, tag) + 14;
					if (active) {
						LunaDraw.roundRect(ctx, lx + lw - 8 - tw, y + (rh - MINI_H) / 2, tw, MINI_H, 6, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x2E));
						btnLabel(ctx, tag, lx + lw - 8 - tw, y + (rh - MINI_H) / 2, tw, MINI_H, LunaDraw.ACCENT);
					} else {
						drawFilledButton(ctx, lx + lw - 8 - tw, y + (rh - MINI_H) / 2, tw, MINI_H, tag, true, true);
					}
				}
				y = y0;
			}
			y += ROW_H;
		}
	}

	// ==================== 입력 ====================

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (confirm.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (menuFor != null) {
			handleMenuClick(mouseX, mouseY, button);
			return true;
		}
		int closeX = px + PANEL_W - PAD - CLOSE_SZ;
		int closeY = py + PAD;
		if (chatWith != null) {
			// 49-47차: 대화 중 - 오른쪽 위 ×면 목록으로, 패널 밖이면 화면을 닫는다. 49-165차: [전송] 버튼.
			if (LunaDraw.in(mouseX, mouseY, closeX, closeY, CLOSE_SZ, CLOSE_SZ)) {
				closeChat();
				return true;
			}
			int lx = px + PAD;
			int lw = PANEL_W - PAD * 2;
			int inputY = py + ph - PAD - FIELD_H;
			int sendW = LunaDraw.width(font, "전송") + 20;
			if (LunaDraw.in(mouseX, mouseY, lx + lw - sendW, inputY, sendW, BTN_H)) {
				sendDraft();
				return true;
			}
			// 입력칸을 누르면 입력 켜짐, 다른 곳을 누르면 꺼짐
			chatFocus = LunaDraw.in(mouseX, mouseY, lx, inputY, lw - sendW - 8, FIELD_H);
			if (chatFocus) {
				return true;
			}
			if (!LunaDraw.in(mouseX, mouseY, px, py, PANEL_W, ph)) {
				goBack();
			}
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, closeX, closeY, CLOSE_SZ, CLOSE_SZ) || !LunaDraw.in(mouseX, mouseY, px, py, PANEL_W, ph)) {
			goBack();
			return true;
		}
		if (right()) {
			int ty = listY() - 22;
			if (LunaSocial.signedIn() && LunaDraw.in(mouseX, mouseY, px + PANEL_W - PAD - 22, ty - 2, 22, 22)) {
				if (!loading && System.nanoTime() - lastFetchNanos > 1_000_000_000L) {
					refresh();
				}
				return true;
			}
			// 49-74차: 친구 추가 줄(입력 칸 + [추가])
			if (LunaSocial.signedIn()) {
				int ay = contentTop();
				int lx = px + PAD;
				int lw = PANEL_W - PAD * 2;
				int bw = LunaDraw.width(font, "추가") + 20;
				int fw = lw - bw - 8;
				if (LunaDraw.in(mouseX, mouseY, lx, ay, fw, FIELD_H)) {
					addFocus = true;
					return true;
				}
				if (LunaDraw.in(mouseX, mouseY, lx + lw - bw, ay, bw, BTN_H)) {
					submitAdd();
					return true;
				}
			}
			addFocus = false;
			handleFriendClick(mouseX, mouseY, button);
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, px + PAD, profileStatsY(), PANEL_W - PAD * 2, BTN_H)) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaStatsScreen(this));
			return true;
		}
		// 49-76차(6-12-2): 닉네임 / UUID 줄을 누르면 복사
		{
			int face = FACE;
			int tx = px + PAD + face + 12;
			int tw = PANEL_W - PAD * 2 - face - 12;
			int fy = profileFaceY();
			if (LunaDraw.in(mouseX, mouseY, tx, fy + 4, tw, 12)) {
				LunaCompat.copyToClipboard(minecraft, LunaCompat.sessionName(minecraft));
				copiedRow = 0;
				copiedUntil = System.currentTimeMillis() + 1500;
				return true;
			}
			if (LunaDraw.in(mouseX, mouseY, tx, fy + 20, tw, 12)) {
				String u = LunaSocial.currentUuid(minecraft);
				if (u != null && !u.isEmpty()) {
					LunaCompat.copyToClipboard(minecraft, LunaSocial.dashed(u));
					copiedRow = 1;
					copiedUntil = System.currentTimeMillis() + 1500;
				}
				return true;
			}
		}
		int lx = px + PAD;
		int ly = profileListY();
		int lw = PANEL_W - PAD * 2;
		int lh = ph - (ly - py) - PAD;
		if (LunaDraw.in(mouseX, mouseY, lx, ly, lw, lh)) {
			List<LunaSocial.Account> list = LunaSocial.accounts();
			int y = ly - (int) scroll;
			String current = LunaSocial.currentUuid(minecraft);
			for (LunaSocial.Account a : list) {
				if (LunaDraw.in(mouseX, mouseY, lx, y, lw, ROW_H - 4)) {
					if (a.uuid().replace("-", "").equalsIgnoreCase(current)) {
						return true;
					}
					String err = LunaSocial.switchAccount(minecraft, a);
					notice(err == null ? a.name() + " 계정으로 바꿨습니다" : err);
					return true;
				}
				y += ROW_H;
			}
		}
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		if (chatWith != null) {
			// 49-225차: 휠을 위로 굴리면 옛날 말 쪽으로(맨 아래에서 멀어짐)
			chatScroll = Math.max(0, Math.min(chatMaxScroll, chatScroll + (float) verticalAmount * 14f));
			return true;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) verticalAmount * 14f));
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (confirm.keyPressed(keyCode)) {
			return true;
		}
		if (menuFor != null) {
			menuFor = null;
			return true;
		}
		if (chatWith != null) {
			// 49-47차: 귓속말 대화 중 - 엔터로 보내고, 백스페이스로 지우고, ESC로 목록으로 돌아간다.
			if (keyCode == InputConstants.KEY_ESCAPE) {
				if (chatFocus) {
					chatFocus = false;   // 먼저 입력만 끈다
				} else {
					closeChat();
				}
				return true;
			}
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				if (chatFocus) {
					sendDraft();
				} else {
					chatFocus = true;
				}
				return true;
			}
			if (keyCode == InputConstants.KEY_BACKSPACE) {
				if (chatFocus && draft.length() > 0) {
					draft.setLength(draft.length() - 1);
				}
				return true;
			}
			return true; // 대화 중엔 다른 키가 화면을 닫지 않게 전부 삼킨다
		}
		if (addFocus) {
			// 49-74차: 친구 추가 입력 중 - ESC는 입력만 접고 화면은 닫지 않는다
			if (keyCode == InputConstants.KEY_ESCAPE) {
				addFocus = false;
				addDraft.setLength(0);
				return true;
			}
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				submitAdd();
				return true;
			}
			if (keyCode == InputConstants.KEY_BACKSPACE) {
				if (addDraft.length() > 0) {
					addDraft.setLength(addDraft.length() - 1);
				}
				return true;
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_ESCAPE) {
			goBack();
			return true;
		}
		return false;
	}


	/** 49-221차: 이 칸에 초점이 있는 동안 한글 IME 유지(LunaScreenBase.lunaSyncIme). */
	@Override
	protected boolean lunaWantsText() {
		return chatWith != null ? chatFocus : addFocus;
	}
	private boolean lunaCharTyped0(char chr) {
		if (chr < ' ') {
			return false;
		}
		if (chatWith == null) {
			if (!addFocus) {
				return false;
			}
			if (addDraft.length() < 32) {
				addDraft.append(chr);
			}
			return true;
		}
		if (!chatFocus) {
			return true;
		}
		if (draft.length() < 500) {
			draft.append(chr);
		}
		return true;
	}

	private void goBack() {
		kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
	}

	@Override
	public void onClose() {
		goBack();
	}
}
