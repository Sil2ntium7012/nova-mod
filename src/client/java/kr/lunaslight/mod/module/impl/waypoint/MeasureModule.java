package kr.lunaslight.mod.module.impl.waypoint;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.MeasureHook;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-41차: 거리 재기(사용자: "막대기로 Shift+우클릭 하고 Shift+좌클릭 하면 그 사이의 거리를 알려주고, 직선이 아니면
 * 그 사이에 몇 개의 블록이 들어가는지 - 빈 공간이 아니라 그냥 그 공간 안이 얼마나 큰지").
 *
 *  · 막대기를 손에 들고 **웅크린 채** 우클릭 = 시작점(A), 좌클릭 = 끝점(B). 바라보는 블록(최대 258블록)이 찍힌다.
 *    클릭은 MeasureClickMixin이 가로채서 바닐라 공격/사용/블록 깨기가 일어나지 않는다.
 *  · A·B 두 블록과 그 사이 전체(꼭짓점 두 개가 만드는 직육면체)를 테두리로 그리고,
 *    HUD에 "거리 12.3블록" + "가로 5 × 높이 3 × 세로 7 = 105칸"(한 축이면 "N칸")을 띄운다.
 *  · 다시 우클릭하면 A가 새로 찍히고 B는 지워진다. 막대기를 놓아도 표시는 남는다(다시 재려면 찍으면 됨).
 *
 * <p>49-76차(6-2, 사용자):
 *  · <b>순서 상관없이</b> - 좌클릭이든 우클릭이든 <b>첫 클릭이 시작점, 두 번째가 끝점</b>. 둘 다 찍힌 뒤 또 누르면 새로 시작.
 *  · <b>재는 아이템을 들고 있을 때만</b> 그린다. 점은 기억해 두므로 다시 들면 그대로 보인다.
 *  · <b>허공을 보고 Shift+클릭 = 선택 취소.</b>
 *  · 49-219차: 끝점은 시작점과 <b>반대 버튼</b>(좌클릭으로 시작하면 우클릭으로 끝). 같은 버튼은 시작점 옮기기.
 *  · 재는 아이템을 <b>바꿀 수 있다</b>(설정 "아이템" - id나 이름 아무거나. 예: stick / 막대기 / blaze_rod).
 */
public class MeasureModule extends Module implements MeasureHook.Handler {

	private static final double MAX_DIST = 258.0;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "재기 결과 표시 위치입니다.", HudPosition.of(HudPosition.Anchor.LEFT_CENTER, 6, -110)));
	private final ColorSetting lineColor = register(new ColorSetting(
			"line_color", "선 색", "두 점과 그 사이 상자 테두리의 색입니다.", 0xFFA9D973));
	private final kr.lunaslight.mod.module.setting.StringSetting item = register(new kr.lunaslight.mod.module.setting.StringSetting(
			"item", "아이템", "재는 데 쓸 아이템입니다. 이름(막대기)이나 id(stick, blaze_rod) 아무거나 됩니다.", "stick"));

	private BlockPos a, b;

	public MeasureModule() {
		super("measure", "거리 재기", ModuleCategory.FEATURE, "막대기 + 웅크리기: 좌클릭과 우클릭으로 두 점");
		defaultEnabled(true);
		enableHudStyle();
		MeasureHook.set(this);
	}

	// ==================== 클릭 ====================

	@Override
	public boolean active() {
		try {
			if (!isEnabled() || client == null || client.player == null || client.currentScreen != null) {
				return false;
			}
			if (!client.player.isSneaking()) {
				return false;
			}
			return holding();
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 재는 아이템을 주 손에 들고 있는가. 설정의 글자를 id(stick)로도, 이름(막대기)으로도 맞춰 본다. */
	private boolean holding() {
		try {
			if (client == null || client.player == null) {
				return false;
			}
			ItemStack main = client.player.getMainHandStack();
			if (main == null || main.isEmpty()) {
				return false;
			}
			String want = item.get() == null ? "" : item.get().trim().toLowerCase();
			if (want.isEmpty()) {
				return main.getItem() == Items.STICK;
			}
			Object id = LunaCompat.getItemId(main.getItem());
			if (id != null) {
				String full = id.toString().toLowerCase();               // minecraft:stick
				String path = full.substring(full.indexOf(':') + 1);      // stick
				if (want.equals(full) || want.equals(path)) {
					return true;
				}
			}
			String shown = main.getName().getString();
			return shown != null && want.equals(shown.trim().toLowerCase());
		} catch (Throwable ignored) {
			return false;
		}
	}

	@Override
	public boolean onUse() {
		return place(true);
	}

	@Override
	public boolean onAttack() {
		return place(false);
	}

	/** 49-219차: 시작점을 찍은 버튼(true = 우클릭). 끝점은 반대 버튼으로만 찍힌다. */
	private boolean startUse;

	/**
	 * 클릭 한 번. 시작은 아무 버튼이나, 끝점은 시작과 반대 버튼(49-219차). 둘 다 있으면 새로 시작.
	 * 허공을 보고 눌렀으면 <b>선택 취소</b>.
	 */
	private boolean place(boolean use) {
		BlockPos p = lookedBlock();
		if (p == null) {
			if (a != null || b != null) {
				a = null;
				b = null;
				LunaCompat.sendActionBar(client, "§7선택을 지웠습니다");
			} else {
				LunaCompat.sendActionBar(client, "§7블록을 바라보고 눌러 주세요");
			}
			return true;
		}
		// 49-219차(제보: "Shift+좌클릭으로 시작했으면 우클릭으로 끝나야 하는데 좌클릭으로 끝나요"):
		// 시작은 아무 버튼이나, 끝점은 반대 버튼. 같은 버튼을 또 누르면 시작점을 옮긴다.
		if (a == null || b != null || use == startUse) {
			a = p;
			b = null;
			startUse = use;
			LunaCompat.sendActionBar(client, "§7시작점 §f" + fmt(p) + " §7- 다른 블록을 Shift+" + endName() + "하면 끝점");
			return true;
		}
		b = p;
		LunaCompat.sendActionBar(client, "§f" + summary());
		return true;
	}

	/** 바라보는 블록(손이 안 닿아도 멀리까지). */
	private BlockPos lookedBlock() {
		BlockPos near = LunaCompat.targetedBlock(client);
		if (near != null) {
			return near;
		}
		try {
			java.lang.reflect.Method m = LunaCompat.findMethod(client.player.getClass(), "raycast",
				double.class, float.class, boolean.class);
			if (m == null) {
				return null;
			}
			m.setAccessible(true);
			Object r = m.invoke(client.player, MAX_DIST, 1.0f, false);
			if (r instanceof HitResult h && h.getType() == HitResult.Type.BLOCK) {
				Object pos = LunaCompat.callNoArg(h, "getBlockPos");
				return pos instanceof BlockPos bp ? bp : null;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** 끝점을 찍을 버튼 이름. */
	private String endName() {
		return startUse ? "좌클릭" : "우클릭";
	}

	private static String fmt(BlockPos p) {
		return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
	}

	// ==================== 계산 ====================

	private int spanX() {
		return Math.abs(a.getX() - b.getX()) + 1;
	}

	private int spanY() {
		return Math.abs(a.getY() - b.getY()) + 1;
	}

	private int spanZ() {
		return Math.abs(a.getZ() - b.getZ()) + 1;
	}

	/** 두 블록 가운데 사이의 직선 거리. */
	private double straight() {
		double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private boolean isLine() {
		int axes = (spanX() > 1 ? 1 : 0) + (spanY() > 1 ? 1 : 0) + (spanZ() > 1 ? 1 : 0);
		return axes <= 1;
	}

	private String summary() {
		if (isLine()) {
			int n = Math.max(spanX(), Math.max(spanY(), spanZ()));
			return "거리 " + String.format("%.1f", straight()) + "블록 | " + countText(n);
		}
		long total = (long) spanX() * spanY() * spanZ();
		return "거리 " + String.format("%.1f", straight()) + "블록 | 가로 " + spanX() + " × 높이 " + spanY() + " × 세로 " + spanZ()
				+ " | " + countText(total);
	}

	/**
	 * 49-195차(사용자: "칸 수는 밑으로 내리고 블록이라고 이름 변경 + 옆에 몇 세트인지 1.2세트 이런 식으로"):
	 * "105블록 | 1.6세트"(한 세트 = 64개). 딱 떨어지면 "2세트".
	 */
	static String countText(long n) {
		double sets = n / 64.0;
		String st = String.format(java.util.Locale.ROOT, "%.1f", sets);
		if (st.endsWith(".0")) {
			st = st.substring(0, st.length() - 2);
		}
		return n + "블록 | " + st + "세트";
	}

	// ==================== 표시 ====================

	/**
	 * 49-218차(제보: "F3 눌렀을 때 거리재기가 안돼요"): F3 중엔 Luna HUD를 전부 숨겨서 점과 상자, 결과 글자가
	 * 같이 사라졌다. 재기는 F3에서도 그린다. 글자는 F3 왼쪽 글과 겹치지 않게 그동안만 핫바 위 가운데로 옮긴다.
	 */
	@Override
	public boolean rendersOnDebugHud() {
		return true;
	}

	// 49-47차(사용자: "미리보기 필요 없는 것들은 없애도 돼"): 거리 재기는 월드 안에 그리는 것이라
	// 작은 미리보기 칸에 담으면 실제와 전혀 달라 보인다 - 미리보기 없음(기본값 그대로).

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		List<String> lines = new ArrayList<>();
		if (isPreview()) {
			lines.add("거리 8.6블록");
			lines.add("가로 5 × 높이 3 × 세로 7");
			lines.add(countText(105));
		} else {
			if (client.player == null || a == null || !holding()) {
				return;   // 49-76차(6-2): 재는 아이템을 들고 있을 때만 - 점은 남아 있어서 다시 들면 그대로다
			}
			if (client.currentScreen == null) {
				drawWorld(context);
			}
			if (b == null) {
				lines.add("시작점 " + fmt(a));
				lines.add("Shift+" + endName() + "으로 끝점");
			} else {
				lines.add("거리 " + String.format("%.1f", straight()) + "블록");
				if (isLine()) {
					lines.add(countText(Math.max(spanX(), Math.max(spanY(), spanZ()))));
				} else {
					long total = (long) spanX() * spanY() * spanZ();
					lines.add("가로 " + spanX() + " × 높이 " + spanY() + " × 세로 " + spanZ());
					lines.add(countText(total));   // 49-195차: 개수는 맨 아래 줄로
				}
			}
		}
		int w = hudLinesWidth(lines);
		int h = hudLinesHeight(lines);
		int sw = WindowAccess.of(client).getScaledWidth(), sh = WindowAccess.of(client).getScaledHeight();
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		if (!isPreview() && LunaCompat.isDebugHudShown(client)) {
			x = (sw - w) / 2;
			y = sh - 72 - h;
		}
		drawHudLines(context, lines, x, y, 0xFFFFFFFF);
	}

	private static final int[][] EDGES = {
		{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
	};
	private static final double[][] VIEW = new double[8][3];

	/** A·B 블록 테두리(진하게) + 둘 사이 직육면체 테두리(옅게). */
	private void drawWorld(DrawContext context) {
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		int col = lineColor.getArgb();
		drawBox(context, proj, a.getX(), a.getY(), a.getZ(), a.getX() + 1, a.getY() + 1, a.getZ() + 1, 2f, col);
		if (b == null) {
			return;
		}
		drawBox(context, proj, b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1, 2f, col);
		if (!isLine() || straight() > 1.5) {
			int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX()) + 1;
			int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY()) + 1;
			int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ()) + 1;
			drawBox(context, proj, x0, y0, z0, x1, y1, z1, 1f, (col & 0x00FFFFFF) | 0x8C000000);
		}
	}

	private void drawBox(DrawContext context, LunaProjection proj, double x0, double y0, double z0,
			double x1, double y1, double z1, float width, int argb) {
		double[][] view = VIEW;
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? x0 : x1;
			double z = (i & 2) == 0 ? z0 : z1;
			double y = (i & 4) == 0 ? y0 : y1;
			proj.toView(x, y, z, view[i]);
		}
		for (int[] e : EDGES) {
			proj.drawViewSegment(context, view[e[0]], view[e[1]], width, argb);
		}
	}
}
