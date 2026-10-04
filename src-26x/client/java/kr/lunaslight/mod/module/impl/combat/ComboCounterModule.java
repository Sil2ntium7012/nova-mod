package kr.lunaslight.mod.module.impl.combat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 49-23차 전투: 콤보 카운터. 공격 키를 누른 직후(4틱 이내) 조준 중인 엔티티의 hurtTime이 올라가면(= 맞았음)
 * 콤보 +1. 내가 맞으면(옵션) 또는 일정 시간 못 때리면 0으로. 패킷/믹스인 없이 클라이언트 상태만 본다.
 */
public class ComboCounterModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "콤보 표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 190)));

	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "콤보 글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));

	private final IntSetting timeout = register(new IntSetting(
			"timeout", "초기화 시간", "이 시간(초) 동안 때리지 못하면 콤보가 초기화됩니다.", 3, 1, 10, 1).unit("초"));

	private final BooleanSetting resetOnHurt = register(new BooleanSetting(
			"reset_on_hurt", "피격 시 초기화", "내가 피해를 입으면 콤보가 초기화됩니다.", true));

	private final BooleanSetting showZero = register(new BooleanSetting(
			"show_zero", "0 표시", "콤보가 0일 때도 표시합니다.", false));

	private int combo;
	private long lastHitNanos;
	private int ticksSinceAttack = 100;
	private boolean attackWasPressed;
	private int prevTargetId = -1;
	private int prevTargetHurt;
	private int prevSelfHurt;

	public ComboCounterModule() {
		super("combo_counter", "콤보", ModuleCategory.COMBAT, "연속으로 때린 횟수");
		enableHudStyle();
	}

	@Override
	protected void onEnable() {
		combo = 0;
	}

	@Override
	public void onTick() {
		if (client.player == null || client.level == null) {
			return;
		}
		Object attackKey = LunaCompat.getKeyBindingField(client.options, "attackKey", "keyAttack");
		boolean pressed = LunaCompat.isKeyBindingPressed(attackKey);
		if (pressed && !attackWasPressed) {
			ticksSinceAttack = 0;
		} else if (ticksSinceAttack < 100) {
			ticksSinceAttack++;
		}
		attackWasPressed = pressed;

		// 조준 중인 엔티티가 이번 틱에 맞았는지(hurtTime 상승)
		LivingEntity target = null;
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity living) {
			target = living;
		}
		if (target != null) {
			int id = System.identityHashCode(target); // getId/getEntityId 이름이 버전마다 달라 identity로
			int hurt = target.hurtTime;
			boolean newHit = id == prevTargetId ? hurt > prevTargetHurt : hurt >= 9;
			if (newHit && ticksSinceAttack <= 4) {
				combo++;
				lastHitNanos = System.nanoTime();
				ticksSinceAttack = 100; // 한 번의 스윙에 한 번만
			}
			prevTargetId = id;
			prevTargetHurt = hurt;
		} else {
			prevTargetId = -1;
			prevTargetHurt = 0;
		}

		// 내가 맞음
		int selfHurt = client.player.hurtTime;
		if (resetOnHurt.get() && selfHurt > prevSelfHurt) {
			combo = 0;
		}
		prevSelfHurt = selfHurt;

		if (combo > 0 && System.nanoTime() - lastHitNanos > timeout.get() * 1_000_000_000L) {
			combo = 0;
		}
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		int n = combo;
		if (isPreview()) {
			n = 7;
		} else {
			if (client.player == null || LunaCompat.isHudHidden(client)) {
				return;
			}
			if (n == 0 && !showZero.get()) {
				return;
			}
		}
		String text = n + " 콤보";
		int w = LunaCompat.getTextWidth(client.font, text);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
