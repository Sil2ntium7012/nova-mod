package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 49-48차: 아이템 정보 - 지금 <b>손에 든</b> 아이템 정보를 HUD에 띄운다(툴팁은 인벤토리를 열어야 보이지만
 * 이건 들고 있는 동안 보인다).
 *
 * <p>49-115차(사용자: "지금 형식으로 다시" - 이름/인챈트/설명, 배경 없음, 내구도 제거, 로마→아라비아,
 * 맨손 스왑 후 다시 들면 또 뜨게):
 * <ul>
 *   <li>1줄: <b>이름</b>(개수 1개 초과면 ×개수).</li>
 *   <li>2줄: <b>인챈트</b> - 약자 + 아라비아 레벨, 전부 한 줄, 폭 넘치면 …로 컷.
 *       (약자: 날카로움→날카, 섬세한 손길→섬손. 나머지는 원문. 레벨은 최대 레벨 &gt; 1일 때만 숫자.)</li>
 *   <li>3줄~: <b>설명(lore)</b> 최대 3줄, 더 있으면 마지막에 …. 각 줄도 폭 넘치면 ….</li>
 *   <li><b>배경 없음</b>({@code enableHudStyle()} 호출 안 함) · <b>내구도 표시 제거</b>.</li>
 * </ul>
 *
 * <p><b>맨손 스왑 후 다시 들면 또 뜨는 것</b>: 예전엔 손에 든 것 추적({@link #withinShowTime})이
 * "줄이 있을 때만" 돌아서, 맨손(줄 없음)으로 바꿔도 마지막 아이템이 그대로 기억됐다 → 같은 걸 다시 들면
 * "안 바뀜"으로 판정돼 다시 안 떴다. 이제 추적을 <b>매 프레임 무조건</b> 돌려(맨손도 기록) 다시 들면 뜬다.
 */
public class ItemInfoHudModule extends Module {

	/** 인챈트 이름 약자(사용자 예시). 없는 것은 원문 그대로. */
	private static final Map<String, String> ENCHANT_ABBR = Map.of(
			"날카로움", "날카",
			"섬세한 손길", "섬손");
	/** 인챈트·설명 한 줄의 최대 폭(넘치면 …로 컷). */
	private static final int MAX_W = 220;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 62)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	private final BooleanSetting showCount = register(new BooleanSetting(
			"show_count", "개수", "1개보다 많으면 개수를 함께 보여줍니다.", true));
	private final BooleanSetting showEnchants = register(new BooleanSetting(
			"show_enchants", "인챈트", "걸린 인챈트를 한 줄로 보여줍니다.", true));
	private final BooleanSetting showLore = register(new BooleanSetting(
			"show_lore", "설명", "아이템 설명(lore)을 최대 3줄 보여줍니다.", true));
	private final BooleanSetting offhand = register(new BooleanSetting(
			"offhand", "왼손", "왼손에 든 것도 함께 보여줍니다.", false));
	private final kr.lunaslight.mod.module.setting.IntSetting showSeconds = register(
			new kr.lunaslight.mod.module.setting.IntSetting("show_seconds", "표시 시간",
					"손에 든 것이 바뀐 뒤 이만큼만 보여줍니다(초). 0이면 계속 보입니다.", 3, 0, 10, 1));

	// 49-65차(4-17): 마지막으로 든 것(종류 + 칸)과 그때 시각 - 바뀌면 다시 몇 초 보여 준다.
	private Object lastItem;
	private int lastSlot = -1;
	private long shownSinceMs;

	public ItemInfoHudModule() {
		super("item_info_hud", "아이템 정보", ModuleCategory.HUD, "손에 든 아이템의 이름/인챈트/설명");
		// 49-115차: 배경 상자 없음 - enableHudStyle() 호출 안 함(drawHudLines가 hudBg==null이면 배경 없이 글자만).
	}

	/** 49-122차(사용자: "도구/인챈트되는 아이템 등으로 한정"): 도구·무기거나, 내구도가 있거나, 인챈트가 붙은 것만 표시. */
	private boolean qualifies(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return LunaCompat.isToolLike(stack) || LunaCompat.hasDurability(stack)
				|| !LunaCompat.enchantments(stack).isEmpty();
	}

	/** 인챈트 이름 뒤에 붙은 레벨 표기(로마자/숫자)를 뗀다 - 바닐라 getName(level)이 "효율 V"처럼 준다. */
	private static String stripLevel(String name) {
		if (name == null) {
			return "";
		}
		return name.replaceAll("\\s+[IVXLCDM]+$", "").replaceAll("\\s+\\d+$", "").trim();
	}

	/** 아이템 하나를 줄 목록으로. 비어 있거나 자격 없으면 아무것도 안 넣는다. */
	private void linesFor(ItemStack stack, List<String> out) {
		if (stack == null || stack.isEmpty() || !qualifies(stack)) {
			return;
		}
		// 1줄: 이름 (+ 개수)
		String name;
		try {
			name = stack.getName().getString();
		} catch (Throwable t) {
			return;
		}
		if (showCount.get() && stack.getCount() > 1) {
			name += " §7×" + stack.getCount();
		}
		out.add(name);
		// 2줄: 인챈트 한 줄 (약자 + 아라비아 레벨)
		if (showEnchants.get()) {
			StringBuilder ench = new StringBuilder();
			for (LunaCompat.EnchantInfo e : LunaCompat.enchantments(stack)) {
				String en;
				try {
					en = e.name() == null ? e.id() : e.name().getString();
				} catch (Throwable t) {
					en = e.id();
				}
				en = stripLevel(en);   // 49-122차: 바닐라 이름의 로마자 레벨 제거(아라비아로만 표시)
				en = ENCHANT_ABBR.getOrDefault(en, en);
				if (ench.length() > 0) {
					ench.append("§8, ");
				}
				ench.append("§b").append(en);
				if (e.maxLevel() > 1) {
					ench.append(e.level());
				}
			}
			if (ench.length() > 0) {
				out.add(LunaDraw.ellipsizeFormatted(client.textRenderer, ench.toString(), MAX_W));
			}
		}
		// 3줄~: 설명(lore) 최대 3줄, 넘치면 …
		if (showLore.get()) {
			List<String> lore = LunaCompat.itemLore(stack);
			if (lore != null && !lore.isEmpty()) {
				int shown = Math.min(3, lore.size());
				for (int i = 0; i < shown; i++) {
					out.add(LunaDraw.ellipsizeFormatted(client.textRenderer, "§7" + lore.get(i), MAX_W));
				}
				if (lore.size() > 3) {
					out.add("§8…");
				}
			}
		}
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		List<String> lines = new ArrayList<>();
		if (isPreview()) {
			String head = "다이아몬드 곡괭이";
			if (showCount.get()) {
				head += " §7×1";
			}
			lines.add(head);
			if (showEnchants.get()) {
				lines.add("§b효율5§8, §b행운3§8, §b섬손§8, §b내구성3");
			}
			if (showLore.get()) {
				lines.add("§7광부의 오랜 친구");
			}
			if (offhand.get()) {
				lines.add("방패");
			}
		} else {
			if (client.player == null) {
				return;
			}
			// 49-115차: 추적을 무조건 먼저(맨손 프레임도 기록) - 다시 들면 또 뜨게.
			boolean show = withinShowTime();
			linesFor(client.player.getMainHandStack(), lines);
			if (offhand.get()) {
				linesFor(client.player.getOffHandStack(), lines);
			}
			if (!show) {
				return;
			}
		}
		if (lines.isEmpty()) {
			return;
		}
		int w = hudLinesWidth(lines);
		int h = hudLinesHeight(lines);
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), w);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), h);
		// 49-122차(사용자: "왼쪽 정렬 말고 가운데 정렬 · 설명 없으면 공백 두지 말고"): 각 줄을 블록 폭 가운데로.
		// 없는 줄(인챈트/설명)은 애초에 안 넣으므로 빈 줄·공백은 생기지 않는다.
		for (int i = 0; i < lines.size(); i++) {
			String ln = lines.get(i);
			int lw = LunaCompat.getTextWidth(client.textRenderer, ln);
			LunaCompat.drawHudText(context, client.textRenderer, ln, x + (w - lw) / 2,
				y + i * hudLineH(), textColor.getArgb());
		}
	}

	@Override
	public void onTick() {
		// 49-122차(사용자: "정보가 뜨면 원래 핫바 위 이름은 안 뜨게"): 자격 있는 아이템을 들고 정보가 보이는
		// 동안엔 바닐라 핫바 이름 팝업을 끈다(우리 HUD가 이름을 이미 보여 주므로 중복).
		if (client == null || client.player == null) {
			return;
		}
		ItemStack main = client.player.getMainHandStack();
		if (main != null && !main.isEmpty() && qualifies(main) && withinShowTime()) {
			LunaCompat.setHeldItemTooltipFade(client, 0);
		}
	}

	/**
	 * 지금 보여 줄 때인지. 손에 든 종류나 칸이 바뀌면 타이머를 다시 시작한다.
	 * [표시 시간]이 0이면 언제나 true(예전 동작). <b>매 프레임 무조건 불려</b> 맨손도 기록한다.
	 */
	private boolean withinShowTime() {
		int seconds = showSeconds.get();
		Object item = null;
		int slot = -1;
		try {
			ItemStack held = client.player.getMainHandStack();
			item = held == null || held.isEmpty() ? null : held.getItem();
			// 49-41차에 만들어 둔 것을 쓴다 - 핫바 칸은 1.21.5+가 getSelectedSlot(), 그 아래는 필드라
			// 직접 쓰면 버전마다 깨진다(실제로 1.15.2·1.21.11 둘 다 컴파일에서 막혔다).
			slot = LunaCompat.selectedSlot(client.player);
		} catch (Throwable ignored) {
			// 칸 번호를 못 읽는 버전이면 종류만으로 판단한다(그래도 "바뀐 순간"은 잡힌다)
		}
		if (item != lastItem || slot != lastSlot) {
			lastItem = item;
			lastSlot = slot;
			shownSinceMs = System.currentTimeMillis();
		}
		return seconds <= 0 || System.currentTimeMillis() - shownSinceMs < seconds * 1000L;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
