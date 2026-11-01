package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.TodoList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-473차: <b>할 일 HUD</b> - 할 일 목록({@link TodoList})을 화면 구석에.
 * <pre>
 *  할 일 2/5
 *  [ ] 감자 300개 캐기     120/300
 *  [v] 서버 추천하기
 * </pre>
 */
public class TodoHudModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 180)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));
	private final BooleanSetting showDone = register(new BooleanSetting(
			"show_done", "끝낸 것도 보기", "체크된 할 일도 흐리게 보여 줍니다.", true));
	private final IntSetting maxLines = register(new IntSetting(
			"max_lines", "최대 줄", "한 번에 보여 줄 할 일 수입니다.", 6, 1, 15, 1));

	private static final int OK = 0xFF4CC38A;

	public TodoHudModule() {
		super("todo_hud", "할 일 HUD", ModuleCategory.TODO, "할 일 목록을 화면에");
		defaultEnabled(true);
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
		if (!isPreview() && client.player == null) {
			return;
		}
		List<TodoList.Task> all = new ArrayList<>();
		if (isPreview()) {
			TodoList.Task a = new TodoList.Task();
			a.text = "감자 300개 캐기";
			a.type = TodoList.GAIN;
			a.goal = 300;
			a.progress = 120;
			TodoList.Task b = new TodoList.Task();
			b.text = "서버 추천하기";
			b.done = true;
			all.add(a);
			all.add(b);
		} else {
			all.addAll(TodoList.all());
		}
		if (all.isEmpty()) {
			return;
		}
		List<TodoList.Task> shown = new ArrayList<>();
		for (TodoList.Task t : all) {
			if (!t.done) {
				shown.add(t);
			}
		}
		if (showDone.get()) {
			for (TodoList.Task t : all) {
				if (t.done) {
					shown.add(t);
				}
			}
		}
		if (shown.isEmpty()) {
			return;
		}
		int max = Math.max(1, maxLines.get());
		int more = Math.max(0, shown.size() - max);
		if (more > 0) {
			shown = shown.subList(0, max);
		}
		int done = 0;
		for (TodoList.Task t : all) {
			if (t.done) {
				done++;
			}
		}
		String title = "할 일 " + done + "/" + all.size();
		int textW = LunaCompat.getTextWidth(client.font, title);
		int progW = 0;
		for (TodoList.Task t : shown) {
			textW = Math.max(textW, 14 + LunaCompat.getTextWidth(client.font, name(t)));
			if (!t.done && TodoList.needsGoal(t.type) && t.goal > 1) {
				progW = Math.max(progW, LunaCompat.getTextWidth(client.font, prog(t)));
			}
		}
		int w = Math.max(90, 6 + textW + (progW > 0 ? 10 + progW : 0) + 6);
		int rowH = 12;
		int h = 6 + 11 + shown.size() * rowH + (more > 0 ? 10 : 0) + 3;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		drawHudPanel(ctx, x, y, w, h);
		// 49-474차: 화면 오른쪽 절반이면 좌우 뒤집기(체크 칸 오른쪽, 글 오른쪽 맞춤, 진행 수 왼쪽)
		boolean right = hudOnRight(x, w);
		int color = textColor.getArgb();
		int dim = (color & 0x00FFFFFF) | 0x80000000;
		int titleW = LunaCompat.getTextWidth(client.font, title);
		LunaCompat.drawHudText(ctx, client.font, title, right ? x + w - 6 - titleW : x + 6, y + 5, color);
		int ry = y + 6 + 11;
		for (TodoList.Task t : shown) {
			int bx = right ? x + w - 6 - 8 : x + 6;
			int by = ry + 1;
			if (t.done) {
				LunaDraw.roundRect(ctx, bx, by, 8, 8, 2, OK);
			} else {
				LunaDraw.roundRect(ctx, bx, by, 8, 8, 2, (color & 0x00FFFFFF) | 0x60000000);
				LunaDraw.roundRect(ctx, bx + 1, by + 1, 6, 6, 2, 0xC0101010);
			}
			String nm = name(t);
			int nw = LunaCompat.getTextWidth(client.font, nm);
			LunaCompat.drawHudText(ctx, client.font, nm, right ? bx - 6 - nw : bx + 14, ry, t.done ? dim : color);
			if (!t.done && TodoList.needsGoal(t.type) && t.goal > 1) {
				String pr = prog(t);
				int pw = LunaCompat.getTextWidth(client.font, pr);
				LunaCompat.drawHudText(ctx, client.font, pr, right ? x + 6 : x + w - 6 - pw, ry, dim);
			}
			ry += rowH;
		}
		if (more > 0) {
			String m = "+" + more + "개 더";
			LunaCompat.drawHudText(ctx, client.font, m, right ? x + w - 6 - LunaCompat.getTextWidth(client.font, m) : x + 6, ry, dim);
		}
	}

	private static String name(TodoList.Task t) {
		return t.text == null || t.text.isBlank() ? TodoList.typeName(t.type) : t.text;
	}

	private static String prog(TodoList.Task t) {
		if (TodoList.PLAY.equals(t.type)) {
			return t.progress + "/" + t.goal + "분";
		}
		return t.progress + "/" + t.goal;
	}
}
