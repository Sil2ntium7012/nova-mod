package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-63차(4-1) → 49-122차 전면 재설계 (사용자: "키로 하는 게 아니라 시간이 지나면 자동으로 숨겨지는 거야.
 * HUD를 원하는 것만 내릴 수 있게 - 핫바·체력·배고픔 등등, 기본은 모두 켜진 거고").
 *
 * <p><b>동작</b>: 가만히 있으면([숨기는 시간]초 동안 시점·이동·클릭이 없으면) HUD를 자동으로 숨기고, 다시
 * 움직이거나 클릭하면 곧바로 돌아온다. F1처럼 한 번에 다 끄는 게 아니라 <b>고른 항목만</b> 숨긴다.
 *
 * <p><b>무엇을 숨길지</b>: 항목별 스위치(기본 전부 켜짐 = 숨김 대상). 끄면 그 항목은 가만히 있어도 계속 보인다.
 *  · 루나 정보(좌표·시계 등) - {@code ModuleManager}의 HUD 콜백이 {@link #hidden}을 본다.
 *  · 핫바 / 체력·배고픔·방어구 / 경험치 - 바닐라 요소는 {@code HudElementHideMixin}이 아래 static 플래그를 본다.
 *    (체력·배고픔·방어구는 바닐라가 한 메서드에서 같이 그려 하나로 묶인다.)
 *
 * <p>바닐라 요소 숨김은 InGameHud의 해당 렌더 메서드를 취소하는 방식이라, 그 메서드가 없는 아주 옛 버전에서는
 * 조용히 미적용된다(require=0 · CrosshairHideMixin과 같은 안전 패턴).
 */
public class HudHideModule extends Module {

	/** ModuleManager가 보는 루나 HUD 숨김 플래그. */
	public static volatile boolean hidden;
	/** HudElementHideMixin이 보는 바닐라 요소 숨김 플래그. */
	public static volatile boolean hideHotbar;
	public static volatile boolean hideStatus;
	public static volatile boolean hideExp;

	private final IntSetting seconds = register(new IntSetting(
			"seconds", "숨김 시간", "가만히 있고 이 시간이 지나면 자동으로 숨깁니다.", 5, 1, 30, 1).unit("초"));
	private final BooleanSetting luna = register(new BooleanSetting(
			"luna", "노바 정보", "좌표/시계 등 노바가 그리는 정보를 숨김 대상에 넣습니다.", true));
	private final BooleanSetting hotbar = register(new BooleanSetting(
			"hotbar", "핫바", "핫바를 숨김 대상에 넣습니다.", true));
	private final BooleanSetting status = register(new BooleanSetting(
			"status", "체력/배고픔", "체력/배고픔/방어구 표시를 숨김 대상에 넣습니다(바닐라가 같이 그려 하나로 묶입니다).", true));
	private final BooleanSetting exp = register(new BooleanSetting(
			"exp", "경험치", "경험치 막대를 숨김 대상에 넣습니다.", true));

	private float lastYaw, lastPitch;
	private double lastX, lastY, lastZ;
	private long lastActiveMs;

	public HudHideModule() {
		super("hud_hide", "HUD 숨기기", ModuleCategory.HUD, "가만히 있으면 고른 HUD를 자동으로 숨김");
	}

	@Override
	protected void onEnable() {
		lastActiveMs = System.currentTimeMillis();
	}

	@Override
	protected void onDisable() {
		clearAll();
	}

	private static void clearAll() {
		hidden = false;
		hideHotbar = false;
		hideStatus = false;
		hideExp = false;
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null) {
			clearAll();
			return;
		}
		long now = System.currentTimeMillis();
		boolean activity = false;

		float yaw = LunaCompat.getYaw(client.player);
		float pitch = LunaCompat.getPitch(client.player);
		double x = client.player.getX();
		double y = client.player.getY();
		double z = client.player.getZ();
		if (yaw != lastYaw || pitch != lastPitch || x != lastX || y != lastY || z != lastZ) {
			activity = true;   // 시점을 돌리거나 움직이면 활동
		}
		lastYaw = yaw;
		lastPitch = pitch;
		lastX = x;
		lastY = y;
		lastZ = z;

		// 메뉴(인벤토리·채팅 등)가 열려 있으면 숨기지 않는다
		if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
			activity = true;
		}
		// 마우스 버튼(때리기·쓰기·휠클릭)도 활동으로
		if (!activity && client.getWindow() != null) {
			long h = client.getWindow().handle();
			if (kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 0)
					|| kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 1)
					|| kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 2)) {
				activity = true;
			}
		}

		if (activity) {
			lastActiveMs = now;
		}
		boolean active = now - lastActiveMs > seconds.get() * 1000L;
		hidden = active && luna.get();
		hideHotbar = active && hotbar.get();
		hideStatus = active && status.get();
		hideExp = active && exp.get();
	}
}
