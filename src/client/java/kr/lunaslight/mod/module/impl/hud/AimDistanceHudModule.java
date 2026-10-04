package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.HitResult;

/**
 * ⚠️ 컴파일 확인 필요: MinecraftClient#crosshairTarget 필드가 public(또는 getter가 있는 형태)이라고
 * 가정했습니다. Yarn 매핑 기준으로는 공개 필드로 알려져 있으나, 실제로 접근이 안 되면
 * client.targetedEntity / client.world.raycast 등의 대안으로 교체가 필요합니다.
 */
// 49-202차(핵 클라이언트의 "Reach" 기능과 혼동 방지): 이름만 바꿈(옛 ReachDistanceHudModule, id reach_distance_hud).
// 조준한 곳까지의 거리를 보여주기만 한다.
public class AimDistanceHudModule extends Module {

	private final PositionSetting position;
	private final ColorSetting textColor;
	// 49-195차(사용자: "거리에 때린 순간 고정 기능 삭제"): [때린 순간 고정](hold_on_hit, 49-65차)을 뺐다.

	public AimDistanceHudModule() {
		super("aim_distance_hud", "거리", ModuleCategory.HUD, "조준한 곳까지의 거리");
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 4, 58)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		enableHudStyle();
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (client.player == null) {
			return;
		}
		HitResult hitResult = client.crosshairTarget;
		double distance;
		if (isPreview()) {
			distance = 3.2; // 49-22차 미리보기 예시
		} else {
			// 49-32차(사용자: "거리보기는 내 손이 안 닿아도 떠야 해"):
			// 바닐라 조준 대상(client.crosshairTarget)은 손이 닿는 거리(3~6블록)까지만 잡힌다.
			// 빗나가면 우리가 직접 멀리까지 레이캐스트해서 그 지점까지의 거리를 보여 준다.
			if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
				distance = LunaCompat.getEyePos(client.player).distanceTo(hitResult.getPos());
			} else {
				HitResult far0 = longRaycast();
				if (far0 == null || far0.getType() == HitResult.Type.MISS) {
					return;
				}
				distance = LunaCompat.getEyePos(client.player).distanceTo(far0.getPos());
			}
		}
		String text = String.format("거리: %.1fm", distance);

		int textWidth = LunaCompat.getTextWidth(client.textRenderer, text);
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), textWidth);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}

	/** 손이 닿는 거리를 넘어서까지 보는 레이캐스트(49-41차: 최대 258블록, 액체는 무시). 매 프레임 한 번 - 블록 레이캐스트는 청크 단위라 가볍다. */
	private HitResult longRaycast() {
		try {
			java.lang.reflect.Method m = LunaCompat.findMethod(client.player.getClass(), "raycast",
				double.class, float.class, boolean.class);
			if (m == null) {
				return null;
			}
			m.setAccessible(true);
			Object r = m.invoke(client.player, 258.0, 1.0f, false);
			return r instanceof HitResult h ? h : null;
		} catch (Throwable ignored) {
			return null;
		}
	}
}
