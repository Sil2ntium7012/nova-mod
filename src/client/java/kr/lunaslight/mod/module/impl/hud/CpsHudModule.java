package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 49-6차: "빠르게 누를 때 인식이 잘 안돼" 수정.
 * 예전엔 KeyBinding.isPressed()를 틱(50ms)마다 폴링해서 한 틱 안에 눌렀다 뗀 클릭을 통째로
 * 놓쳤음(10CPS 이상에서 필연적으로 뭉개짐). 이제 LunaCompat.ensureMouseClickCounter()가
 * GLFW 마우스 콜백을 감싸 이벤트 단위로 전부 계수 - 지터클릭/버터플라이도 정확.
 * 콜백 설치가 실패하는 환경에서만 예전 폴링 방식으로 폴백.
 */
public class CpsHudModule extends Module {

	/** 49-47차: 설정 화면에 그대로 보이는 이름이라 한글로. */
	public enum DisplayMode {
		TOTAL("합쳐서"), SPLIT_LEFT_RIGHT("좌/우 나눠서");

		private final String label;

		DisplayMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final PositionSetting position;
	private final ColorSetting textColor;
	private final EnumSetting<DisplayMode> displayMode;

	// 클릭 시각(ms) 슬라이딩 윈도(1000ms)
	private final Deque<Long> leftClicks = new ArrayDeque<>();
	private final Deque<Long> rightClicks = new ArrayDeque<>();

	// 폴백(콜백 설치 실패 시)용 edge-detection 상태
	private boolean prevAttackPressed = false;
	private boolean prevUsePressed = false;

	public CpsHudModule() {
		super("cps_hud", "CPS", ModuleCategory.HUD, "초당 클릭 수");
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 4, 24)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		displayMode = register(new EnumSetting<>("display_mode", "표시 방식", "합산 또는 좌/우클릭을 나눠 표시합니다.",
			DisplayMode.TOTAL, DisplayMode.class));
		enableHudStyle();
	}

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();

		if (LunaCompat.ensureMouseClickCounter()) {
			int l = LunaCompat.drainLeftClicks();
			int r = LunaCompat.drainRightClicks();
			for (int i = 0; i < l; i++) {
				leftClicks.addLast(now);
			}
			for (int i = 0; i < r; i++) {
				rightClicks.addLast(now);
			}
		} else {
			// 폴백: 예전 틱 폴링(정밀도 낮음)
			boolean attackPressed = LunaCompat.isKeyBindingPressed(
					LunaCompat.getKeyBindingField(client.options, "attackKey", "keyAttack"));
			if (attackPressed && !prevAttackPressed) {
				leftClicks.addLast(now);
			}
			prevAttackPressed = attackPressed;

			boolean usePressed = LunaCompat.isKeyBindingPressed(
					LunaCompat.getKeyBindingField(client.options, "useKey", "keyUse"));
			if (usePressed && !prevUsePressed) {
				rightClicks.addLast(now);
			}
			prevUsePressed = usePressed;
		}

		while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) {
			leftClicks.removeFirst();
		}
		while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) {
			rightClicks.removeFirst();
		}
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounterParam) {
		String text;
		if (displayMode.get() == DisplayMode.SPLIT_LEFT_RIGHT) {
			text = "CPS: " + leftClicks.size() + " / " + rightClicks.size();
		} else {
			text = "CPS: " + (leftClicks.size() + rightClicks.size());
		}

		int textWidth = LunaCompat.getTextWidth(client.textRenderer, text);
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), textWidth);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
