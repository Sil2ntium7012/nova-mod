package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaIcons;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaRecipes;
import kr.lunaslight.mod.util.ScrollHook;
import kr.lunaslight.mod.util.TextCapture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 제작 도우미 - 작업대 옆 JEI식 패널.
 *
 * 49-34차: 작업대(3×3)나 인벤토리(2×2)를 열면 GUI **오른쪽에 패널**이 붙고, 지금 인벤토리에 있는 재료로 바로
 * 만들 수 있는 것들이 아이콘 격자로 뜬다. 아이콘을 **클릭하면 격자에 재료가 채워지고**(바닐라 조합법 책의
 * [넣기]와 같은 경로 - 서버가 옮기므로 서버에서도 됨), **Shift+클릭이면 최대한 채운다**.
 *
 * 49-39차(사용자가 처음부터 말한 "검색창 + JEI식 목록 + 재귀 자동 제작"으로):
 *  · 패널 위에 **검색창** - 클릭해 글자를 치면 목록이 이름으로 걸러진다(한글 IME 포함, TextCapture).
 *  · **중간 재료를 먼저 만들면 되는 것**도 목록에 뜬다(칸 아래 강조색 밑줄). 클릭하면 계획대로 **자동 제작**:
 *    단계마다 [격자에 넣기 → 결과 칸 꺼내기]를 서버 응답을 기다리며 반복한다(틱 상태기계). 패널 머리에
 *    진행 상황이 뜬다.
 *
 * 49-69차(4-15) 자동 제작을 쓸 수 있게 고친 세 가지:
 *  · **기다리는 시간 1초 → 3초**. 핑 100ms짜리 서버에서 왕복이 1초에 아슬아슬해, 서버가 한 번만
 *    끊겨도 자동 제작이 조용히 멈췄다({@code WAIT_LIMIT} 주석 참고).
 *  · **왜 멈췄는지 알려 준다**. 예전에는 그냥 사라져서 다 된 것인지 실패한 것인지 알 수 없었다.
 *  · **[멈춤] 버튼**. 한 번 시작하면 끝나거나 실패할 때까지 손 쓸 방법이 창을 닫는 것뿐이었다.
 *  · 예전 별도 검색 화면(LunaRecipeScreen)과 그 열기 키는 뺐다.
 *
 * 49-91차(8-20, 사용자: "제작 도우미 전혀 안 고쳐짐 - 조합법 없어도 가능한 조합 · 쉬프트 한 세트 · 조합 속도"):
 *  · 격자 채우기를 조합법 책 [넣기]에서 <b>슬롯 클릭</b>({@link LunaRecipes#placeManual})으로. 책은 서버가
 *    "내가 푼 조합법"만 들어줘서 안 푼 것·책을 끈 서버에선 아무 일도 안 났다. 클릭은 즉시 반영된다.
 *  · 1.21.2+ 서버에서 책에 없던 바닐라 조합법은 게임 jar에서 읽어 목록에 넣는다(LunaRecipes ④).
 *  · 클릭 = 한 세트, <b>Shift+클릭 = 최대</b>(스택 한도 안에서 고르게 나눠 넣음). 결과 칸을 Shift로 꺼내면 한 번에.
 *  · 자동 제작은 단계마다 <b>필요한 세트를 한 번에</b> 넣고 결과를 Shift로 꺼내 왕복이 세트 수만큼 줄었다.
 */
public class CraftingHelperModule extends Module {

	private final BooleanSetting sidePanel = register(new BooleanSetting(
			"side_panel", "작업대 옆 패널", "작업대를 열면 만들 수 있는 것을 옆에 띄웁니다.", true));

	private final BooleanSetting showInInventory = register(new BooleanSetting(
			"in_inventory", "인벤토리 2×2", "인벤토리 창의 2×2 격자에서도 패널을 띄웁니다.", false));
	{
		// 49-133차(사용자: "제작 도우미 기능은 인벤토리에서는 안켜지고 제작대에서만"): 설정은 저장 호환용으로만 남기고 숨긴다.
		showInInventory.hidden();
	}

	private final BooleanSetting showChains = register(new BooleanSetting(
			"show_chains", "중간 단계 포함", "중간 재료를 먼저 만들면 되는 것도 보여 주고, 클릭하면 자동으로 만듭니다.", true));

	private static final int CELL = 18;
	private static final int PAD = 6;
	private static final int HEAD = 16;
	private static final int SEARCH_H = 14;

	private boolean clickHeld;
	private float scroll;
	private float maxScroll;
	private int panelX, panelY, panelW, panelH, cols, rows;
	private int searchX, searchY, searchW;
	/** 목록 한 칸: 바로 만들 수 있으면 chain == null, 중간 단계가 필요하면 chain != null. */
	private record Item(LunaRecipes.Entry entry, LunaRecipes.Chain chain) {
		ItemStack result() {
			return entry.result();
		}
	}
	private List<Item> items = new ArrayList<>();
	private List<Item> shown = new ArrayList<>();
	private long craftableAt;
	private int lastInvHash;
	private Object lastScreen;
	private final StringBuilder query = new StringBuilder();
	private boolean searchFocused;
	private String shownQuery = "";

	public CraftingHelperModule() {
		super("crafting_helper", "제작 도우미", ModuleCategory.INVENTORY, "제작대 | 인벤토리");
		defaultEnabled(true);
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		ScrollHook.register(this::onScroll);
		TextCapture.setTarget(new TextCapture.Target() {
			@Override
			public boolean wants() {
				return searchFocused && client != null && panelActive(kr.lunaslight.mod.util.LunaCompat.screenOf(client));
			}

			@Override
			public void onChar(int codePoint) {
				if (Character.isISOControl(codePoint) || query.length() >= 40) {
					return;
				}
				query.appendCodePoint(codePoint);
				scroll = 0;
			}

			@Override
			public boolean onKey(int key, int action, int modifiers) {
				if (action == InputConstants.RELEASE) {
					return true;   // 눌림을 삼켰으면 뗌도 삼킨다(바닐라가 "뗌"만 받아 어긋나지 않게)
				}
				if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) {
					searchFocused = false;
					return true;
				}
				if (key == InputConstants.KEY_BACKSPACE) {
					if ((modifiers & InputConstants.MOD_CONTROL) != 0) {
						query.setLength(0);
					} else if (query.length() > 0) {
						query.setLength(query.length() - 1);
					}
					scroll = 0;
					return true;
				}
				// 글자 키는 charTyped(믹스인)로 따로 오므로 여기선 삼키기만(E로 창이 닫히지 않게). F1~F12는 넘긴다.
				return key < InputConstants.KEY_F1 || key > InputConstants.KEY_F12;   // 49-215차: F1~F12는 GLFW/SDL 모두 연속
			}
		});
	}

	// 49-53차(4-40): 설정 페이지 아래에 붙던 [패널 조작] 안내표를 없앴다. 클릭·Shift·휠은 열어 보면
	// 바로 알 수 있는 것들이라 표까지 둘 값이 없었다.

	// ==================== 자동 제작(재귀) ====================

	/** 단계 하나: 이 조합법을 times번. */
	private record Job(LunaRecipes.Entry entry, int times) {
	}

	private final java.util.ArrayDeque<Job> jobs = new java.util.ArrayDeque<>();
	private Job currentJob;
	private int jobDone;          // currentJob에서 끝낸 횟수
	private int phase;            // 0 = 넣기 전, 1 = 결과 기다림, 2 = 꺼낸 뒤 비워지길 기다림
	private int waitTicks;
	private int totalJobs, finishedJobs;
	private int placedSets;       // 이번에 격자에 넣은 세트 수(결과를 꺼내면 이만큼 끝난 것)
	private String autoName = "";
	/**
	 * 49-69차(4-15): 서버 응답을 기다리는 한계(틱).
	 *
	 * <p>예전 값은 <b>20틱(1초)</b>이었다. 핑이 100ms만 돼도 [격자에 넣기 → 서버가 결과 칸을 채움 →
	 * 꺼내기 → 격자가 비워짐] 왕복이 1초에 아슬아슬하게 걸리고, 서버가 한 번 끊기면 그대로 넘긴다.
	 * 그러면 자동 제작이 <b>아무 말 없이 중간에 멈춘다</b> - "자동 제작이 되다 만다"의 정체다.
	 */
	private static final int WAIT_LIMIT = 60;
	/** 멈춘 이유(잠깐 패널 머리에 띄운다). 비어 있으면 안 띄움. */
	private String autoNote = "";
	private long autoNoteUntil;
	/** 자동 제작 중 패널 머리의 [멈춤] 자리 - 그리는 쪽과 클릭 판정이 같은 값을 보게 한다. */
	private int cancelX, cancelY, cancelW, cancelH;

	/** 49-105차: 직접 조합(체인 없음)도 자동 경로로. Shift면 재료 소진까지(다 만들면 조용히 끝). */
	private boolean directGreedy;

	/**
	 * 49-124차(사용자: "쉬프트 한 세트가 무조건 64가 아냐 - 양동이는 16, 침대 같은 건 1"): 쉬프트 = 결과물
	 * <b>한 묶음(그 아이템의 최대 스택)</b>을 만든다. 대부분 64, 양동이·눈덩이·알은 16, 침대·보트는 1.
	 * 한 번 조합에 여러 개 나오는 레시피(판자 ×4 등)는 그만큼 적게 조합한다.
	 */
	private static int craftsForStack(LunaRecipes.Entry e) {
		int yield = 1;
		int stack = 64;
		try {
			var r = e.result();
			yield = Math.max(1, r.getCount());
			stack = Math.max(1, r.getMaxStackSize());
		} catch (Throwable ignored) {
		}
		return Math.max(1, (stack + yield - 1) / yield);
	}

	private void startAutoCraft(LunaRecipes.Chain chain, boolean shift) {
		jobs.clear();
		// 49-122차(수정): 배수 곱하기(× 중간 잉여 무시 → 재료 통째로 갈아먹음)를 버리고, 최종 결과 조합 횟수를
		// 목표로 계획을 <b>다시 짠다</b>. 쉬프트면 한 묶음(64) 나오는 만큼(craftsForStack), 아니면 1번.
		int finalTimes = shift ? craftsForStack(chain.entry()) : 1;
		LunaRecipes.Plan plan = LunaRecipes.planFor(client, chain.entry(),
			gridSizeOf(kr.lunaslight.mod.util.LunaCompat.screenOf(client)), finalTimes);
		for (LunaRecipes.Step st : plan.steps()) {
			if (st.entry() != null && st.times() > 0) {
				jobs.add(new Job(st.entry(), st.times()));
			}
		}
		totalJobs = jobs.size();
		finishedJobs = 0;
		currentJob = null;
		jobDone = 0;
		phase = 0;
		directGreedy = shift;   // 재료가 먼저 떨어지면 만든 만큼만 두고 조용히 끝
		autoNote = "";
		autoName = chain.entry().result().getHoverName().getString();
	}

	private void startDirectCraft(LunaRecipes.Entry entry, boolean shift) {
		jobs.clear();
		// 49-113·122차: 클릭=결과 1개, 쉬프트=결과 한 묶음(64). 여러 개 나오는 레시피는 조합 횟수를 그만큼 줄인다.
		// 49-122차(사용자: "판자 한 세트+원목 한 세트로 제작대 64개"): 직접 조합도 계획을 세워, 재료가 모자라면
		// 하위 조합(원목→판자)으로 채워서 목표까지 만든다(예전엔 있는 판자만 쓰고 16개에서 멈췄다).
		int finalTimes = shift ? craftsForStack(entry) : 1;
		LunaRecipes.Plan plan = LunaRecipes.planFor(client, entry, gridSizeOf(kr.lunaslight.mod.util.LunaCompat.screenOf(client)), finalTimes);
		for (LunaRecipes.Step st : plan.steps()) {
			if (st.entry() != null && st.times() > 0) {
				jobs.add(new Job(st.entry(), st.times()));
			}
		}
		if (jobs.isEmpty()) {
			jobs.add(new Job(entry, finalTimes));
		}
		totalJobs = jobs.size();
		finishedJobs = 0;
		currentJob = null;
		jobDone = 0;
		phase = 0;
		directGreedy = shift;
		autoNote = "";
		autoName = entry.result().getHoverName().getString();
	}

	private boolean autoCrafting() {
		return currentJob != null || !jobs.isEmpty();
	}

	/**
	 * 49-69차(4-15): 멈출 때 <b>왜 멈췄는지</b> 남긴다. 예전에는 조용히 사라져서, 다 만들어진 것인지
	 * 재료가 모자란 것인지 서버가 늦은 것인지 알 길이 없었다.
	 */
	private void cancelAutoCraft(String why) {
		jobs.clear();
		currentJob = null;
		phase = 0;
		if (why != null && !why.isEmpty()) {
			autoNote = why;
			autoNoteUntil = System.currentTimeMillis() + 5000;
		}
	}

	@Override
	public void onTick() {
		if (client == null || !autoCrafting()) {
			return;
		}
		if (!panelActive(kr.lunaslight.mod.util.LunaCompat.screenOf(client))) {
			cancelAutoCraft("창을 닫아 멈췄습니다");
			return;
		}
		if (currentJob == null) {
			currentJob = jobs.poll();
			jobDone = 0;
			phase = 0;
			if (currentJob == null) {
				return;
			}
		}
		ItemStack out = outputStack();
		switch (phase) {
			case 0 -> {
				if (!out.isEmpty()) {
					// 결과 칸에 뭔가 남아 있으면 먼저 꺼낸다
					quickMoveOutput();
					phase = 2;
					waitTicks = 0;
					return;
				}
				// 49-91차: 남은 횟수를 한 번에 넣는다(슬롯 클릭). 안 되면 예전 조합법 책 경로로 한 세트.
				// 49-122차: 이제 placeManual이 넘침을 스스로 막으므로(정확 배치 조건 수정) 남은 횟수를 통째로 넘겨 빠르게 채운다.
				placedSets = LunaRecipes.placeManual(client, currentJob.entry(), gridSizeOf(kr.lunaslight.mod.util.LunaCompat.screenOf(client)),
					currentJob.times() - jobDone);
				if (placedSets <= 0) {
					if (!LunaRecipes.place(client, currentJob.entry(), false)) {
						// 49-105차: Shift로 "가능한 만큼" 만드는 중이면 재료가 다 떨어진 것 = 정상 완료(격자 비어 있음).
						if (directGreedy && jobDone > 0) {
							finishedJobs++;
							currentJob = null;
							phase = 0;
							return;
						}
						cancelAutoCraft("재료가 모자라 멈췄습니다");
						return;
					}
					placedSets = 1;
				}
				phase = 1;
				waitTicks = 0;
			}
			case 1 -> {
				if (!out.isEmpty()) {
					quickMoveOutput();
					phase = 2;
					waitTicks = 0;
				} else if (++waitTicks > WAIT_LIMIT) {
					// 서버가 결과 칸을 안 채워 줌 - 재료 부족이거나 서버가 아주 느림
					cancelAutoCraft("재료가 모자라거나 서버가 늦어 멈췄습니다");
				}
			}
			case 2 -> {
				if (out.isEmpty()) {
					jobDone += Math.max(1, placedSets);
					craftableAt = 0;
					if (jobDone >= currentJob.times()) {
						finishedJobs++;
						currentJob = null;
					}
					phase = 0;
				} else if (++waitTicks > WAIT_LIMIT) {
					cancelAutoCraft("서버 응답이 늦어 멈췄습니다");
				}
			}
			default -> phase = 0;
		}
	}

	private ItemStack outputStack() {
		try {
			ItemStack st = client.player.containerMenu.getSlot(0).getItem();
			return st == null ? ItemStack.EMPTY : st;
		} catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}

	private void quickMoveOutput() {
		try {
			// 결과 칸(0)을 Shift+클릭 → 인벤토리로(격자에 한 벌만 들어 있어 정확히 한 번만 만들어진다)
			client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 0, 0,
				net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, client.player);
		} catch (Throwable t) {
			LunaCompat.warnOnce("craftingHelper:quickMove", t);
			cancelAutoCraft("결과를 꺼내지 못해 멈췄습니다");
		}
	}

	// ==================== 옆 패널 ====================

	/** 지금 화면의 격자 크기(3 = 작업대, 2 = 인벤토리, 0 = 해당 없음). */
	private int gridSizeOf(Object screen) {
		if (screen instanceof CraftingScreen) {
			return 3;
		}
		return 0;
	}

	private boolean panelActive(Object screen) {
		return isEnabled() && sidePanel.get() && client.player != null && client.gameMode != null
			&& screen == kr.lunaslight.mod.util.LunaCompat.screenOf(client) && gridSizeOf(screen) > 0;
	}

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		if (!panelActive(screen)) {
			kr.lunaslight.mod.util.SidePanels.clearRight(screen);
			clickHeld = false;
			searchFocused = false;
			return;
		}
		LunaCompat.ensureTextCapture();
		int grid = gridSizeOf(screen);
		if (screen != lastScreen) {
			lastScreen = screen;
			scroll = 0;
			craftableAt = 0;
			searchFocused = false;
			cancelAutoCraft(autoCrafting() ? "다른 창으로 옮겨 멈췄습니다" : null);
		}
		refreshCraftable(grid);
		layout((AbstractContainerScreen<?>) screen);
		kr.lunaslight.mod.util.SidePanels.setRight(screen, panelX + panelW);   // 49-133차: 인벤토리 탭이 이 옆으로 밀려나게
		applyFilter();

		// ---- 패널 ----
		// 49-47차(사용자: "조합법 가이드 너무 못생겼어, 배경도 너무 검정이야 투명하게 해줘"):
		// 거의 불투명한 검정판 → 반투명 유리판. 그림자로 인벤토리 위에 떠 있게 하고, 위 가장자리에
		// 1px 밝은 선을 둬 판의 두께가 보이게 했다. 제목 밑줄은 화면을 가로지르던 그라데이션 선 대신
		// 제목 길이만큼만 강조색, 나머지는 아주 옅은 구분선.
		LunaDraw.panel3d(ctx, panelX, panelY, panelW, panelH, 6);   // 49-227차: 사진 시안 판
		ctx.fill(panelX + 6, panelY, panelX + panelW - 6, panelY + 1, 0x1AFFFFFF);

		// 49-69차(4-15): 자동 제작 중에는 머리에 [멈춤] 버튼을 둔다. 예전에는 한 번 시작하면
		// 끝나거나 실패할 때까지 손 쓸 방법이 없었다(창을 닫는 것 말고는).
		boolean auto = autoCrafting();
		boolean note = !auto && !autoNote.isEmpty() && System.currentTimeMillis() < autoNoteUntil;
		String title = auto ? "자동 제작 중 | " + autoName : (note ? autoNote : "만들 수 있는 것");
		cancelW = auto ? LunaDraw.width(client.font, "멈춤") + 10 : 0;
		cancelH = 12;
		cancelX = panelX + panelW - PAD - cancelW;
		cancelY = panelY + 2;
		String cnt = auto ? finishedJobs + "/" + totalJobs
				+ (currentJob != null && currentJob.times() > 1 ? " (" + (jobDone + 1) + "/" + currentJob.times() + ")" : "")
				: shown.size() + "개";
		int cntW = LunaDraw.width(client.font, cnt);
		String shortTitle = LunaDraw.ellipsize(client.font, title,
			panelW - PAD * 2 - cntW - cancelW - 12);
		LunaDraw.text(ctx, client.font, shortTitle,
			panelX + PAD, panelY + 5, auto ? LunaDraw.ACCENT : (note ? 0xFFEF8A5C : LunaDraw.TEXT));
		LunaDraw.text(ctx, client.font, cnt,
			cancelX - (auto ? 6 : 0) - cntW, panelY + 5, LunaDraw.TEXT_DIM);
		if (auto) {
			boolean stopHover = LunaDraw.in(mouseX, mouseY, cancelX, cancelY, cancelW, cancelH);
			LunaDraw.roundRectBordered(ctx, cancelX, cancelY, cancelW, cancelH, 3,
				stopHover ? 0x33EF4444 : LunaDraw.CARD, stopHover ? 0xFFEF4444 : LunaDraw.CARD_BORDER);
			LunaDraw.text(ctx, client.font, "멈춤", cancelX + 5,
				LunaDraw.textY(cancelY, cancelH), stopHover ? 0xFFEF4444 : LunaDraw.TEXT_SUB);
		}
		int sepY = panelY + HEAD + 2;
		ctx.fill(panelX + PAD, sepY, panelX + panelW - PAD, sepY + 1, 0x1AFFFFFF);
		int underline = Math.min(LunaDraw.width(client.font, shortTitle), panelW - PAD * 2);
		ctx.fill(panelX + PAD, sepY, panelX + PAD + underline, sepY + 1, LunaDraw.ACCENT);

		// ---- 검색창 ----
		searchX = panelX + PAD;
		searchY = panelY + HEAD + 5;
		searchW = panelW - PAD * 2;
		boolean searchHover = LunaDraw.in(mouseX, mouseY, searchX, searchY, searchW, SEARCH_H);
		LunaDraw.roundRectBordered(ctx, searchX, searchY, searchW, SEARCH_H, 3,
			searchFocused ? 0x4C1B2026 : 0x33000000,
			searchFocused ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x99) : (searchHover ? LunaDraw.withAlpha(0xFFFFFF, 0x30) : LunaDraw.CARD_BORDER));
		LunaIcons.draw(ctx, client.font, LunaIcons.SEARCH, searchX + 3, LunaDraw.iconY(searchY, SEARCH_H), LunaDraw.TEXT_DIM);
		int textX = searchX + 3 + 11 + 3;
		int textY = LunaDraw.textY(searchY, SEARCH_H);
		if (query.length() == 0 && !searchFocused) {
			LunaDraw.text(ctx, client.font, "검색", textX, textY, LunaDraw.TEXT_DIM);
		} else {
			String q = LunaDraw.ellipsize(client.font, query.toString(), searchW - 20);
			LunaDraw.text(ctx, client.font, q, textX, textY, LunaDraw.TEXT);
			if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = textX + LunaDraw.width(client.font, q) + 1;
				ctx.fill(cx, searchY + 3, cx + 1, searchY + SEARCH_H - 3, LunaDraw.ACCENT);
			}
		}

		int gx = panelX + PAD;
		int gy = searchY + SEARCH_H + 4;
		int gh = panelY + panelH - PAD - gy;
		rows = Math.max(1, gh / CELL);
		int totalRows = (shown.size() + cols - 1) / cols;
		maxScroll = Math.max(0, (totalRows - rows) * CELL);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		Item hover = null;
		int hoverX = 0, hoverY = 0;
		ctx.enableScissor(gx, gy, gx + cols * CELL, gy + rows * CELL);
		int first = ((int) scroll / CELL) * cols;
		for (int i = first; i < shown.size(); i++) {
			int rel = i - first;
			int cx = gx + (rel % cols) * CELL;
			int cy = gy + (rel / cols) * CELL - ((int) scroll % CELL);
			if (cy >= gy + rows * CELL) {
				break;
			}
			Item it = shown.get(i);
			boolean hov = LunaDraw.in(mouseX, mouseY, cx, cy, CELL, CELL) && mouseY >= gy && mouseY < gy + rows * CELL;
			// 49-47차: 아이템이 허공에 떠 있던 것 → 바닐라 칸처럼 옅은 바탕을 깔고, 올리면 강조색 테두리.
			LunaDraw.roundRect(ctx, cx, cy, CELL - 1, CELL - 1, 3,
				hov ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x2E) : 0x1FFFFFFF);
			if (hov) {
				LunaDraw.roundRectOutline(ctx, cx, cy, CELL - 1, CELL - 1, 3, LunaDraw.withAlpha(LunaDraw.ACCENT, 0xB0));
				hover = it;
				hoverX = cx;
				hoverY = cy;
			}
			ctx.item(it.result(), cx + 1, cy + 1);
			if (it.result().getCount() > 1) {
				String n = Integer.toString(it.result().getCount());
				LunaCompat.drawHudText(ctx, client.font, n,
					cx + CELL - 1 - LunaDraw.width(client.font, n), cy + CELL - 8, 0xFFFFFFFF);
			}
			if (it.chain() != null) {
				// 중간 단계가 필요한 것: 칸 아래 강조색 밑줄(49-41차: 점 → 밑줄, 사용자 "점 싫어")
				ctx.fill(cx + 3, cy + CELL - 2, cx + CELL - 3, cy + CELL - 1, LunaDraw.ACCENT);
			}
		}
		ctx.disableScissor();

		if (shown.isEmpty()) {
			LunaDraw.text(ctx, client.font, query.length() > 0 ? "없음" : "재료 없음", gx, gy + 4, LunaDraw.TEXT_DIM);
		}
		if (maxScroll > 0) {
			int trackX = panelX + panelW - 3;
			int th = Math.max(8, Math.round(rows * CELL * (rows * CELL / (float) (rows * CELL + maxScroll))));
			int ty = gy + Math.round((rows * CELL - th) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, trackX, gy, 2, rows * CELL, 1, 0x14FFFFFF);
			LunaDraw.roundRect(ctx, trackX, ty, 2, th, 1, LunaDraw.withAlpha(LunaDraw.ACCENT, 0xAA));
		}

		// ---- 툴팁: 이름 + 재료(+ 중간 단계) ----
		if (hover != null) {
			drawTooltip(ctx, hover, hoverX + CELL + 4, hoverY);
		}

		// ---- 클릭(GLFW 폴링, 눌린 순간 한 번) ----
		boolean down = client.getWindow() != null
			&& kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 0);
		if (down && !clickHeld) {
			clickHeld = true;
			if (autoCrafting() && cancelW > 0 && LunaDraw.in(mouseX, mouseY, cancelX, cancelY, cancelW, cancelH)) {
				cancelAutoCraft("멈췄습니다");
			} else if (searchHover) {
				searchFocused = !searchFocused;
			} else {
				if (searchFocused && LunaDraw.in(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
					searchFocused = false;
				} else if (searchFocused) {
					searchFocused = false;
				}
				if (hover != null && !holdingStack()) {
					// 49-122차: 쉬프트는 직접·연쇄(2차) 제작 둘 다 결과 한 묶음(64)을 만들게 - 여기서 한 번만 읽어 둘 다 넘긴다.
					boolean shift = keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
					if (hover.chain() != null) {
						if (!autoCrafting()) {
							startAutoCraft(hover.chain(), shift);
						}
					} else if (!autoCrafting()) {
						// 49-105차(사용자: "반블록·계단처럼 중간 단계 없는 것도 바로 만들어지게"): 격자만 채우던 것을
						// 자동 제작 경로(채우기+꺼내기)로 태워 한 번에 완성한다.
						startDirectCraft(hover.entry(), shift);
					}
				}
			}
		} else if (!down) {
			clickHeld = false;
		}
	}

	/** 검색어로 걸러진 목록(검색어·원본이 바뀌었을 때만 다시). */
	private void applyFilter() {
		String q = query.toString().trim().toLowerCase(java.util.Locale.ROOT);
		if (q.equals(shownQuery) && shown.size() <= items.size() && !filterDirty) {
			return;
		}
		filterDirty = false;
		shownQuery = q;
		if (q.isEmpty()) {
			shown = items;
			return;
		}
		List<Item> out = new ArrayList<>();
		for (Item it : items) {
			String name;
			try {
				name = it.result().getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
			} catch (Throwable t) {
				name = "";
			}
			if (name.contains(q) || (it.entry().id() != null && it.entry().id().toLowerCase(java.util.Locale.ROOT).contains(q))) {
				out.add(it);
			}
		}
		shown = out;
	}

	private boolean filterDirty;

	/** 인벤토리 내용이 바뀌었을 때만 다시 계산(프레임마다 수백 개 조합법을 훑지 않게). */
	private void refreshCraftable(int grid) {
		int hash = inventoryHash() * 31 + (showChains.get() ? 1 : 0);
		long now = System.currentTimeMillis();
		if (hash == lastInvHash && now - craftableAt < 1500) {
			return;
		}
		lastInvHash = hash;
		craftableAt = now;
		List<LunaRecipes.Entry> direct = LunaRecipes.craftableNow(client, grid);
		List<Item> out = new ArrayList<>(direct.size() + 16);
		for (LunaRecipes.Entry e : direct) {
			out.add(new Item(e, null));
		}
		if (showChains.get()) {
			for (LunaRecipes.Chain c : LunaRecipes.craftableViaSteps(client, grid, direct)) {
				out.add(new Item(c.entry(), c));
			}
		}
		items = out;
		filterDirty = true;
	}

	private int inventoryHash() {
		int h = 17;
		try {
			var inv = client.player.getInventory();
			Object sizeObj = LunaCompat.callNoArg(inv, "size");
			int size = sizeObj instanceof Number n ? n.intValue() : 41;
			for (int i = 0; i < size; i++) {
				Object st = LunaCompat.call1(inv, "getStack", i);
				if (st instanceof ItemStack s && !s.isEmpty()) {
					h = h * 31 + System.identityHashCode(s.getItem());
					h = h * 31 + s.getCount();
				}
			}
		} catch (Throwable ignored) {
			// 해시 실패 → 매번 계산(느리지만 안전)
			return (int) System.nanoTime();
		}
		return h;
	}

	/** GUI 오른쪽에 패널 자리를 잡는다(화면 밖으로 나가면 폭을 줄인다). */
	private void layout(AbstractContainerScreen<?> hs) {
		int ox = 0, bw = 176, oy = 0, bh = 166;
		try {
			java.lang.reflect.Field fx = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "x");
			java.lang.reflect.Field fy = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "y");
			java.lang.reflect.Field fw = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "backgroundWidth");
			java.lang.reflect.Field fh = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "backgroundHeight");
			fx.setAccessible(true);
			fy.setAccessible(true);
			fw.setAccessible(true);
			fh.setAccessible(true);
			ox = fx.getInt(hs);
			oy = fy.getInt(hs);
			bw = fw.getInt(hs);
			bh = fh.getInt(hs);
		} catch (Throwable t) {
			LunaCompat.warnOnce("craftingHelper:layout", t);
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		panelX = ox + bw + 4;
		int avail = sw - panelX - 4;
		cols = Math.max(2, Math.min(6, (avail - PAD * 2) / CELL));
		panelW = cols * CELL + PAD * 2;
		panelY = oy;
		panelH = Math.min(bh, sh - oy - 4);
	}

	private void drawTooltip(GuiGraphicsExtractor ctx, Item it, int x, int y) {
		LunaRecipes.Entry e = it.entry();
		String name = e.result().getHoverName().getString();
		int nw = LunaDraw.width(client.font, name);

		// 49-105차(사용자: "아이템이 여러 개 뜨지 말고 그냥 판자 ×4 이런 식으로, '먼저 ~~~'는 다 없애"):
		// 재료를 슬롯마다 하나씩 말고 같은 재료끼리 묶어 [아이콘 ×개수]로. 중간 단계("먼저 …") 줄은 전부 제거.
		List<ItemStack> reps = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (LunaRecipes.Slot s : e.slots()) {
			if (s.isEmpty()) {
				continue;
			}
			String key = ingredientKey(s);
			int idx = keys.indexOf(key);
			if (idx >= 0) {
				counts.set(idx, counts.get(idx) + 1);
			} else {
				keys.add(key);
				ItemStack rep = s.options().size() == 1 ? s.first()
					: s.options().get((int) ((System.currentTimeMillis() / 1200) % s.options().size()));
				reps.add(rep);
				counts.add(1);
			}
		}

		boolean gt = LunaCompat.guiTransformSupported(ctx);
		int ingW;
		if (gt) {
			ingW = 0;
			for (int i = 0; i < reps.size(); i++) {
				ingW += 9 + LunaDraw.width(client.font, "×" + counts.get(i)) + 7;
			}
			ingW = Math.max(0, ingW - 3);
		} else {
			ingW = LunaDraw.width(client.font, "재료 " + e.slots().size() + "칸");
		}

		int w = Math.max(nw, ingW) + 12;
		int h = 28;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		if (x + w > sw - 2) {
			x = sw - 2 - w;
		}
		if (y + h > sh - 2) {
			y = sh - 2 - h;
		}
		// 49-47차: 툴팁도 패널과 같은 반투명 유리 톤으로(예전엔 거의 불투명한 검정).
		LunaDraw.panel3d(ctx, x, y, w, h, 5);   // 49-227차: 사진 시안 판
		LunaDraw.text(ctx, client.font, name, x + 6, y + 5, LunaDraw.TEXT);

		int ix = x + 6;
		int iy = y + 16;
		if (gt) {
			for (int i = 0; i < reps.size(); i++) {
				LunaCompat.guiPush(ctx);
				LunaCompat.guiTranslate(ctx, ix, iy);
				LunaCompat.guiScale(ctx, 0.5f, 0.5f);
				ctx.item(reps.get(i), 0, 0);
				LunaCompat.guiPop(ctx);
				String c = "×" + counts.get(i);
				LunaDraw.text(ctx, client.font, c, ix + 9, iy + 1, LunaDraw.TEXT_SUB);
				ix += 9 + LunaDraw.width(client.font, c) + 7;
			}
		} else {
			LunaDraw.text(ctx, client.font, "재료 " + e.slots().size() + "칸", ix, iy, LunaDraw.TEXT_DIM);
		}
	}

	/** 재료 묶음 키 - 같은 후보(대체 재료) 조합이면 같은 재료로 본다. */
	private static String ingredientKey(LunaRecipes.Slot s) {
		List<String> ids = new ArrayList<>();
		for (ItemStack o : s.options()) {
			ids.add(String.valueOf(o.getItem()));
		}
		java.util.Collections.sort(ids);
		return String.join("|", ids);
	}

	private boolean onScroll(double horizontal, double vertical) {
		if (client == null || !panelActive(kr.lunaslight.mod.util.LunaCompat.screenOf(client)) || vertical == 0) {
			return false;
		}
		double mx = client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth() / (double) client.getWindow().getScreenWidth();
		double my = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight() / (double) client.getWindow().getScreenHeight();
		if (!LunaDraw.in(mx, my, panelX, panelY, panelW, panelH)) {
			return false;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) vertical * CELL));
		return true;
	}

	private boolean keyDown(int key) {
		return client.getWindow() != null && kr.lunaslight.mod.util.LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), key);
	}

	private boolean holdingStack() {
		try {
			ItemStack cursor = client.player.containerMenu.getCarried();
			return cursor != null && !cursor.isEmpty();
		} catch (Throwable ignored) {
			return false;
		}
	}
}
