package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-29차: 항상 달리기(사용자 요청). 앞으로 갈 때는 늘 달린다.
 * 49-698차: [방식]의 토글을 없앰(바닐라 달리기 전환과 같음). [켜기/끄기 키]로 잠시 껐다 켤 수 있다.
 * 해제 조건은 설정으로 - 뒤로 가면 / 웅크리면 / 배고픔이 6 이하면 / 화면을 열면.
 *
 * 방식: 바닐라 달리기 키바인딩을 눌린 상태로 유지한다(KeyBinding#setPressed) - 마인크래프트가 평소처럼
 * 달리기를 시작하므로 서버에도 정상적으로 보인다.
 */
public class SprintToggleModule extends Module {

	// 49-698차(사용자: "키 지정하고 눌렀을 때 안 껐다 켜져, 토글이 아니어도 작동해야지. 토글 삭제해 줘, 기본 마크랑 다를 게 없음"):
	// [방식] 설정(토글/항상)을 없애고 늘 항상 달리기로 동작한다. 지정한 키는 항상 달리기를 껐다 켠다.
	// 저장 이름 toggle_key는 그대로 두어 예전에 지정한 키가 이어진다.
	private final KeybindSetting toggleKey = register(new KeybindSetting(
			"toggle_key", "켜기/끄기 키", "누를 때마다 항상 달리기를 껐다 켭니다.", -1));

	private final BooleanSetting stopOnBackward = register(new BooleanSetting(
			"stop_on_backward", "뒤로 갈 때 해제", "뒤나 옆으로 움직이면 달리기를 멈춥니다.", true));

	private final BooleanSetting stopOnSneak = register(new BooleanSetting(
			"stop_on_sneak", "웅크릴 때 해제", "웅크리면 달리기를 멈춥니다.", true));

	private final BooleanSetting stopOnHunger = register(new BooleanSetting(
			"stop_on_hunger", "배고플 때 해제", "배고픔이 6 이하로 떨어지면 달리기를 멈춥니다.", true));

	private final BooleanSetting showIndicator = register(new BooleanSetting(
			"indicator", "표시", "화면에 작게 띄웁니다. 지금 달리는 중이 아니면 흐리게 표시됩니다.", true).style());

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시가 뜨는 자리입니다.", HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 4, 4)));

	private final ColorSetting color = register(new ColorSetting(
			"color", "색", "표시 색입니다.", LunaTheme.DEFAULT_ACCENT));

	/** 키로 잠시 끈 상태(기능은 켜져 있음). */
	private boolean paused;
	private boolean keyWasDown;

	public SprintToggleModule() {
		super("sprint_toggle", "항상 달리기", ModuleCategory.FEATURE, "앞으로 갈 때 늘 달리기");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		enableHudStyle(0xB2090A0C, true, (kr.lunaslight.mod.util.LunaTheme.DEFAULT_ACCENT & 0x00FFFFFF) | 0x66000000, kr.lunaslight.mod.module.Module.HudShape.FOLLOW);
		showIndicator.withColor(color);
	}

	@Override
	protected void onDisable() {
		release();
		paused = false;
	}

	@Override
	public void onTick() {
		// 지정한 키를 누를 때마다 껐다 켬. 채팅이나 화면이 열려 있을 때는 무시.
		boolean down = toggleKey.isBound() && LunaCompat.screenOf(client) == null && toggleKey.isDown(client);
		if (down && !keyWasDown && client.player != null) {
			paused = !paused;
			if (paused) {
				setSprintPressed(false);
				try {
					client.player.setSprinting(false);
				} catch (Throwable ignored) {
				}
			}
		}
		keyWasDown = down;
		if (client.player == null) {
			return;
		}
		if (paused || shouldStop()) {
			release();
			return;
		}
		hold();
	}

	/** 지금 달리는 중으로 취급되는지(표시용). */
	private boolean active() {
		return !paused && client.player != null && !shouldStop();
	}

	private boolean shouldStop() {
		try {
			if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
				return true;
			}
			if (stopOnSneak.get() && client.player.isShiftKeyDown()) {
				return true;
			}
			if (stopOnHunger.get() && client.player.getFoodData().getFoodLevel() <= 6) {
				return true;
			}
			if (stopOnBackward.get() && !movingForward()) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** 앞으로 가는 키가 눌려 있는지. */
	private boolean movingForward() {
		Object binding = keyBinding("forwardKey", "keyForward");
		Object pressed = binding == null ? null : LunaCompat.invokeNoArg(binding, "isPressed");
		return pressed instanceof Boolean b && b;
	}

	/** 바닐라 달리기 키를 눌린 상태로 유지. */
	private void hold() {
		setSprintPressed(true);
		try {
			client.player.setSprinting(true);
		} catch (Throwable ignored) {
		}
	}

	/** 우리가 눌러 둔 상태를 놓아 줌(진짜로 손가락이 눌리고 있으면 그대로 둠). */
	private void release() {
		if (!vanillaSprintPhysicallyDown()) {
			setSprintPressed(false);
		}
	}

	private void setSprintPressed(boolean pressed) {
		Object binding = keyBinding("sprintKey", "keySprint");
		if (binding == null) {
			return;
		}
		try {
			for (java.lang.reflect.Method m : binding.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
						&& LunaCompat.nameMatches(binding.getClass(), "setPressed", m.getName())) {
					m.invoke(binding, pressed);
					return;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("sprintToggle:setPressed", t);
		}
	}

	/** 바닐라 달리기 키가 물리적으로 눌려 있는지(GLFW로 직접 - 우리가 눌러 둔 상태와 구분). */
	private boolean vanillaSprintPhysicallyDown() {
		try {
			Object binding = keyBinding("sprintKey", "keySprint");
			if (binding == null || client.getWindow() == null) {
				return false;
			}
			java.lang.reflect.Field f = LunaCompat.findField(binding.getClass(), "boundKey");
			if (f == null) {
				return false;
			}
			f.setAccessible(true);
			Object key = f.get(binding);
			Object code = LunaCompat.invokeNoArg(key, "getCode");
			if (!(code instanceof Integer c) || c < 0) {
				return false;
			}
			return kr.lunaslight.mod.util.LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), c);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** GameOptions의 키바인딩 필드(이름이 버전마다 sprintKey/keySprint로 다름). */
	private Object keyBinding(String... names) {
		try {
			Object options = client.options;
			for (String n : names) {
				java.lang.reflect.Field f = LunaCompat.findField(options.getClass(), n);
				if (f != null) {
					f.setAccessible(true);
					Object v = f.get(options);
					if (v != null) {
						return v;
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("sprintToggle:binding", t);
		}
		return null;
	}

	// ==================== 표시 ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!showIndicator.get()) {
			return;
		}
		// 49-124차(사용자: "달릴 때만 뜨는 게 아니라 무조건 떠 있어야"): 켜져 있으면 항상 표시한다.
		// 지금 실제로 달리는 중이 아니면(토글 꺼짐 등) 흐리게 그려 상태를 구분한다.
		boolean activeNow = isPreview() || active();
		String text = !isPreview() && paused ? "달리기 꺼짐" : "항상 달리기";
		// 49-32차: 사용자 요청으로 표시를 키우고 기본 위치를 오른쪽 위 구석으로.
		// 49-210차(사용자: "항상 달리기 글 위치가 안 맞아"): 오른쪽 여백을 왼쪽(7)과 같게 - 예전엔 5라 글이 오른쪽으로 쏠려 보였다
		int w = LunaCompat.getTextWidth(client.font, text) + 24;
		int h = 16;
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), h);
		int c = activeNow ? color.getArgb() : ((color.getArgb() & 0x00FFFFFF) | 0x66000000);
		drawHudBox(context, x, y, w, h, activeNow ? 1f : 0.57f);   // 49-156차: 이 기능의 [배경] 설정대로(달리지 않을 땐 흐리게)
		// 왼쪽에 작은 화살표 두 개(달리는 느낌) - 커진 상자에 맞춰 위치도 같이 내림
		int ay = y + h / 2;
		// 49-210차: 화살표를 상자 세로 가운데(짝수 높이 4/6)에 - 예전엔 0.5px 위로 치우쳤다
		context.fill(x + 7, ay - 2, x + 10, ay + 2, c);
		context.fill(x + 11, ay - 3, x + 13, ay + 3, c);
		LunaCompat.drawHudText(context, client.font, text, x + 17,
			y + Math.round(h / 2f - LunaCompat.textVisualCenter(text)), c);   // 49-210차: 한글 글자 중심(기본 글꼴은 영문보다 0.5px 아래)
	}
}
