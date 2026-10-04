package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 49-42차: 탭 목록 인원 제한 → 49-124차(사용자: "탭 인원 제한 > 탭리스트로 이름 변경 + 80명씩 페이지로"):
 * <b>탭리스트</b>. 바닐라는 탭 목록을 80명에서 잘라 81번째부터는 볼 수 없다. 이제 바닐라의 80 상수를 풀고
 * 전체 목록을 받은 뒤 <b>한 페이지 인원(기본 80)</b>씩 잘라 보여 준다. 탭을 누른 채 화살표 키(바꿀 수 있음)로
 * 페이지를 넘기고, 페이지 수는 탭 목록 아래에 뜬다. 탭을 떼면 1페이지로 돌아간다.
 *
 * <p>페이지 자르기는 {@code collectPlayerEntries}(1.19.4+)의 RETURN에서 한다. 그 메서드가 없는 옛 버전
 * (1.15.2~1.19.2, 목록을 render 안에서 만든다)은 페이지 없이 예전처럼 앞에서 [한 페이지 인원]까지만 보인다.
 */
public class TabListLimitModule extends Module {

	private static volatile boolean active;
	private static volatile int activePerPage = 80;
	private static volatile int page;
	private static volatile int total;

	private final IntSetting perPage = register(new IntSetting(
			"per_page", "한 페이지 인원", "탭 목록 한 페이지에 보여줄 인원입니다.", 80, 5, 80, 1));
	private final KeybindSetting prevKey = register(new KeybindSetting(
			"prev_key", "이전 페이지 키", "탭을 누른 채 이 키를 누르면 앞 페이지로 갑니다.", InputConstants.KEY_LEFT));
	private final KeybindSetting nextKey = register(new KeybindSetting(
			"next_key", "다음 페이지 키", "탭을 누른 채 이 키를 누르면 다음 페이지로 갑니다.", InputConstants.KEY_RIGHT));
	private final BooleanSetting showPage = register(new BooleanSetting(
			"show_page", "페이지 표시", "지금 페이지 / 전체 페이지를 표시합니다.", true));

	// 49-195차(사용자: 사진 + "탭리스트 페이지 이상한 곳에 나옴 - 서버 가면. 수정할 거 아니면 삭제"): 페이지 표시를 HUD로 따로
	// 그리지 않고 <b>탭 목록의 바닥글 맨 아래 줄</b>로 넣는다(PlayerListHudMixin이 그리는 동안만 바닥글에 한 줄 붙였다 뗀다).
	// 예전엔 목록 높이를 머리글 줄 수와 인원으로 어림해서 그 아래에 그렸는데, 서버 머리글·바닥글·줄바꿈을 정확히 못 세서
	// 큰 서버에선 목록 한가운데 겹쳤다. 바닥글로 넣으면 바닐라가 목록 바로 아래에, 목록 배경 안에 그려 준다.
	// 그래서 [페이지 표시 위치]와 [배경] 설정은 뺐다.

	private boolean prevWasDown;
	private boolean nextWasDown;
	private boolean tabWasDown;

	public TabListLimitModule() {
		super("tab_list_limit", "탭리스트", ModuleCategory.HUD, "탭 목록을 페이지로 넘겨 81명 이후도 보기");
	}

	@Override
	protected void onEnable() {
		active = true;
		activePerPage = perPage.get();
		page = 0;
	}

	@Override
	protected void onDisable() {
		active = false;
		page = 0;
	}

	@Override
	public void onTick() {
		active = true;
		showPageNow = showPage.get();   // 49-156차: 켜진 채 불러와 onEnable이 안 불린 경우에도 믹스인이 동작하게
		activePerPage = perPage.get();
		if (client == null || client.player == null) {
			return;
		}
		boolean tabDown = tabHeld();
		if (!tabDown) {
			// 탭을 떼면 다음에 열 때 1페이지부터
			if (tabWasDown) {
				page = 0;
			}
			tabWasDown = false;
			prevWasDown = false;
			nextWasDown = false;
			return;
		}
		tabWasDown = true;
		int pages = pages();
		boolean prevDown = prevKey.isBound() && prevKey.isDown(client);
		boolean nextDown = nextKey.isBound() && nextKey.isDown(client);
		if (prevDown && !prevWasDown && page > 0) {
			page--;
		}
		if (nextDown && !nextWasDown && page < pages - 1) {
			page++;
		}
		prevWasDown = prevDown;
		nextWasDown = nextDown;
	}

	/** 바닐라 탭(플레이어 목록) 키가 눌려 있는지 - 필드 이름이 버전마다 playerListKey/keyPlayerList로 다르다. */
	private boolean tabHeld() {
		try {
			Object options = client.options;
			for (String n : new String[]{"playerListKey", "keyPlayerList"}) {
				java.lang.reflect.Field f = LunaCompat.findField(options.getClass(), n);
				if (f != null) {
					f.setAccessible(true);
					Object binding = f.get(options);
					Object pressed = binding == null ? null : LunaCompat.invokeNoArg(binding, "isPressed");
					if (pressed instanceof Boolean b) {
						return b;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	// ==================== 표시(페이지 수 - 탭 목록 바닥글 맨 아래 줄) ====================

	private static volatile boolean showPageNow = true;
	private static Object swappedFooter;
	private static boolean swapped;

	/**
	 * PlayerListHudMixin: 탭 목록을 그리기 직전. 페이지가 둘 이상이면 바닥글에 "페이지 1 / 6"(회색) 한 줄을 붙인다.
	 * 원래 바닥글은 afterRender에서 그대로 되돌린다(서버가 보낸 값을 바꾸지 않는다).
	 */
	public static void beforeRender(Object hud) {
		if (swapped) {
			afterRender(hud);   // 지난번에 되돌리지 못했으면(예외 등) 먼저 되돌린다
		}
		if (!active || !showPageNow || hud == null || pages() <= 1) {
			return;
		}
		try {
			java.lang.reflect.Field f = LunaCompat.findField(hud.getClass(), "footer");
			if (f == null) {
				return;
			}
			f.setAccessible(true);
			Object footer = f.get(hud);
			net.minecraft.network.chat.Component line = LunaCompat.coloredText("페이지 " + (page + 1) + " / " + pages(), 0xAAAAAA);
			net.minecraft.network.chat.Component next = footer instanceof net.minecraft.network.chat.Component t
					? LunaCompat.join(t, LunaCompat.coloredText("\n", 0xAAAAAA), line)
					: line;
			swappedFooter = footer;
			swapped = true;
			f.set(hud, next);
		} catch (Throwable t) {
			LunaCompat.warnOnce("tabList:footer", t);
		}
	}

	/** PlayerListHudMixin: 탭 목록을 다 그린 뒤 - 바닥글을 원래대로. */
	public static void afterRender(Object hud) {
		if (!swapped || hud == null) {
			return;
		}
		swapped = false;
		try {
			java.lang.reflect.Field f = LunaCompat.findField(hud.getClass(), "footer");
			if (f != null) {
				f.setAccessible(true);
				f.set(hud, swappedFooter);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("tabList:footerRestore", t);
		}
		swappedFooter = null;
	}

	// 머리글 줄 수 어림(headerLines)과 HUD 그리기는 49-195차에 뺐다.

	// ==================== 믹스인이 참조하는 정적 상태 ====================

	public static boolean isActive() {
		return active;
	}

	public static int perPage() {
		return Math.max(1, activePerPage);
	}

	public static int page() {
		return page;
	}

	/** 믹스인이 전체(잘리기 전) 인원을 알려준다. */
	public static void setTotal(int n) {
		total = Math.max(0, n);
		int pages = pages();
		if (page > pages - 1) {
			page = Math.max(0, pages - 1);
		}
	}

	public static int pages() {
		return Math.max(1, (total + perPage() - 1) / perPage());
	}
}
