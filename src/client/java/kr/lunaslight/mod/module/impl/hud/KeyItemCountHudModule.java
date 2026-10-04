package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-65차(4-2): <b>소모품 개수</b>(49-179차에 이름을 아이템 개수에서 바꿈) - 폭죽·토템·화살처럼 "몇 개 남았는지가 중요한" 것만 화면에 띄운다.
 *
 * <p>사용자 요청 4-2: "폭죽·토템·화살 등 주요 아이템 개수 표시".
 *
 * <p>인벤토리를 열지 않아도 남은 수를 알 수 있는 게 요점이다 - 겉날개 비행 중 폭죽, PVP 중 토템,
 * 활 쏘는 중 화살은 <b>인벤토리를 열 수 없는 순간</b>에 가장 알고 싶은 값이다.
 *
 * <p><b>아이콘 + 숫자</b>로 그린다. 글자로 "폭죽 32"라고 쓰면 종류가 늘수록 줄이 길어지는데,
 * 아이콘은 한눈에 구분되고 폭이 일정하다.
 *
 * <p><b>없는 건 안 띄운다</b>(기본값). 화살을 안 들고 다니는 사람 화면에 "화살 0"이 계속 떠 있을
 * 이유가 없다. 0개도 보고 싶으면 [0개도 보이기]를 켜면 된다.
 *
 * <p><b>성능</b>: 인벤토리 훑기는 <b>틱마다 한 번</b>(초 20회)이고 결과만 들고 있다가 프레임마다 그린다.
 * 프레임마다 훑으면 화면 주사율만큼(144Hz면 7배) 헛돌게 된다.
 */
public class KeyItemCountHudModule extends Module {

	private static final int ICON = 16;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_RIGHT, 62, 6)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "개수 글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));

	private final BooleanSetting fireworks = register(new BooleanSetting(
			"fireworks", "폭죽", "겉날개 폭죽 개수를 보여줍니다.", true));
	private final BooleanSetting totems = register(new BooleanSetting(
			"totems", "토템", "불사의 토템 개수를 보여줍니다.", true));
	private final BooleanSetting arrows = register(new BooleanSetting(
			"arrows", "화살", "화살(일반/분광/효과) 개수를 모두 합쳐 보여줍니다.", true));
	private final BooleanSetting pearls = register(new BooleanSetting(
			"pearls", "엔더 진주", "엔더 진주 개수를 보여줍니다.", false));
	private final BooleanSetting gapples = register(new BooleanSetting(
			"gapples", "황금 사과", "황금 사과(일반/마법이 부여된) 개수를 합쳐 보여줍니다.", false));
	// 49-179차(사용자: "아이템 개수가 아니라 소모품 개수, 손에 든 아이템 수 기능은 따로"): 49-157차에 합쳤던
	// [손 아이템]은 다시 [손 아이템 개수](HeldItemCounterModule)로 분리했다.
	private final BooleanSetting showZero = register(new BooleanSetting(
			"show_zero", "0개 표시", "하나도 없을 때도 자리를 지킵니다. 끄면 없는 것은 안 보입니다.", false));

	/** 한 줄에 그릴 것: 아이콘으로 쓸 대표 아이템 + 합계. */
	private record Entry(ItemStack icon, int count) {
	}

	private List<Entry> entries = new ArrayList<>();

	public KeyItemCountHudModule() {
		super("key_item_count", "소모품 개수", ModuleCategory.HUD, "폭죽/토템/화살 남은 수");
		enableHudStyle();
	}

	@Override
	public void onTick() {
		entries = collect();
	}

	private List<Entry> collect() {
		List<Entry> out = new ArrayList<>();
		if (client == null || client.player == null) {
			return out;
		}
		if (fireworks.get()) {
			add(out, Items.FIREWORK_ROCKET, count(Items.FIREWORK_ROCKET));
		}
		if (totems.get()) {
			add(out, Items.TOTEM_OF_UNDYING, count(Items.TOTEM_OF_UNDYING));
		}
		if (arrows.get()) {
			// 화살은 세 종류가 같은 목적이라 합쳐서 센다 - 따로 세면 줄만 늘고 알고 싶은 답은 못 준다.
			add(out, Items.ARROW,
					count(Items.ARROW) + count(Items.SPECTRAL_ARROW) + count(Items.TIPPED_ARROW));
		}
		if (pearls.get()) {
			add(out, Items.ENDER_PEARL, count(Items.ENDER_PEARL));
		}
		if (gapples.get()) {
			add(out, Items.GOLDEN_APPLE,
					count(Items.GOLDEN_APPLE) + count(Items.ENCHANTED_GOLDEN_APPLE));
		}
		return out;
	}

	private void add(List<Entry> out, Item icon, int count) {
		if (count > 0 || showZero.get()) {
			out.add(new Entry(new ItemStack(icon), count));
		}
	}

	/** 인벤토리 전체(핫바·가방·왼손 포함)에서 이 아이템 개수 합. */
	private int count(Item item) {
		int total = 0;
		try {
			Object inv = LunaCompat.getPlayerInventory(client.player);
			int size = LunaCompat.invSize(inv);
			for (int i = 0; i < size; i++) {
				ItemStack stack = LunaCompat.invGetStack(inv, i);
				if (stack != null && !stack.isEmpty() && stack.getItem() == item) {
					total += stack.getCount();
				}
			}
		} catch (Throwable ignored) {
			// 인벤토리를 못 읽는 상황이면 0으로 둔다(틀린 수를 보여 주느니)
		}
		return total;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		List<Entry> list = isPreview() ? previewEntries() : entries;
		if (list.isEmpty()) {
			return;
		}
		int w = width(list);
		int h = ICON;
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), w);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), h);
		drawHudPanel(context, x, y, w, h);
		int cx = x;
		for (Entry e : list) {
			context.drawItem(e.icon(), cx, y);
			cx += ICON + 3;
			String n = String.valueOf(e.count());
			// 아이콘 세로 가운데에 글자 밑선을 맞춘다(아이콘 16 · 글자 밴드 높이 8 기준).
			LunaCompat.drawHudText(context, client.textRenderer, n, cx, y + 4, textColor.getArgb());
			cx += LunaCompat.getTextWidth(client.textRenderer, n) + 8;
		}
	}

	private int width(List<Entry> list) {
		int w = 0;
		for (Entry e : list) {
			w += ICON + 3 + LunaCompat.getTextWidth(client.textRenderer, String.valueOf(e.count())) + 8;
		}
		return Math.max(1, w - 8);   // 마지막 뒤 여백은 뺀다
	}

	/** 49-88차(8-12, 사용자: "미리보기가 설정에 따라 바뀌어야"): 켠 항목·0 표시 설정을 그대로 따른다. */
	private List<Entry> previewEntries() {
		List<Entry> out = new ArrayList<>();
		if (fireworks.get()) add(out, Items.FIREWORK_ROCKET, 32);
		if (totems.get()) add(out, Items.TOTEM_OF_UNDYING, 3);
		if (arrows.get()) add(out, Items.ARROW, 128);
		if (pearls.get()) add(out, Items.ENDER_PEARL, 0);
		if (gapples.get()) add(out, Items.GOLDEN_APPLE, 5);
		return out;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
