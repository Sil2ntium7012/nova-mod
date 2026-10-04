package kr.lunaslight.mod.module.impl.waypoint;

import kr.lunaslight.mod.util.WindowAccess;
import org.lwjgl.glfw.GLFW;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.FloatSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.Setting;

/**
 * 저장된 시점(yaw/pitch)으로 키를 누르면 플레이어 시점을 돌리는 모듈(기능 #40).
 *
 * <p>49-245차(사용자: "화면 고정 시점 설정해서 추가하는 식으로 여러 개 가능하게 해 주고 키 지정도 다 따로"): 방향 하나 고정이던 것을
 * [시점 추가]로 하나씩 늘리는 목록으로 바꿨다(최대 {@value #POOL}개). 시점마다 자기 묶음(키, 가로 각도, 세로 각도, [지금 방향], [삭제]).
 * 저장 id는 1번이 예전 그대로(snap_key/target_yaw/target_pitch)라 쓰던 설정이 1번 시점으로 이어진다.
 * 여러 키가 함께 눌리면 마지막에 누른 시점이 이긴다.
 */
public class ViewSnapModule extends Module {

	private static final int POOL = 8;

	/**
	 * 49-195차(사용자: "시점 고정 토글/유지로 할 수 있게, 기본은 유지 - 꾹 눌러야 되는 거"): 유지 = 키를 누르고 있는 동안만
	 * 그 방향으로 고정(예전 동작), 토글 = 한 번 누르면 고정되고 다시 누르면 풀린다.
	 */
	public enum Mode {
		TOGGLE("토글"),
		HOLD("유지");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final ActionSetting add = register(new ActionSetting("add_view", "시점", "고정할 시점을 하나 더 만듭니다.", "추가하기", this::addPreset));

	private final EnumSetting<Mode> mode = register(new EnumSetting<>(
			"mode", "방식", "유지는 키를 누르고 있는 동안만, 토글은 한 번 누르면 고정되고 다시 누르면 풀립니다.",
			Mode.HOLD, Mode.class));

	// 49-24차: "천천히 움직이지 말고 거의 바로" - 기본 즉시 전환, 끄면 회전 속도대로
	private final BooleanSetting instant = register(new BooleanSetting(
			"instant", "즉시 전환", "보간 없이 바로 돌립니다. 끄면 아래 속도로 돌아갑니다.", true));

	private final FloatSetting turnSpeed = register(new FloatSetting(
			"turn_speed", "회전 속도", "틱당 최대 회전 각도입니다.", 45f, 1f, 180f, 1f).unit("°"));

	/** 쓰는 시점 수(나머지 칸은 숨김, 저장용). */
	private final IntSetting count = register(new IntSetting("view_count", "시점 수", "", 1, 0, POOL, 1));

	private final KeybindSetting[] keys = new KeybindSetting[POOL];
	private final FloatSetting[] yaws = new FloatSetting[POOL];
	private final FloatSetting[] pitches = new FloatSetting[POOL];
	private final ActionSetting[] heres = new ActionSetting[POOL];
	private final ActionSetting[] dels = new ActionSetting[POOL];

	private final boolean[] lastDown = new boolean[POOL];
	/** 지금 고정 중인 시점(-1 = 없음). */
	private int active = -1;

	public ViewSnapModule() {
		super("view_snap", "시점 고정", ModuleCategory.VIEW, "키마다 저장한 방향 보기");   // 49-76차(6-11): 이름만 "시점 전환" → "시점 고정"
		for (int i = 0; i < POOL; i++) {
			final int idx = i;
			String suf = i == 0 ? "" : "_" + (i + 1);
			String g = "시점 " + (i + 1);
			keys[i] = register(new KeybindSetting("snap_key" + suf, "키", "누르면 이 시점으로 돌립니다.", i == 0 ? GLFW.GLFW_KEY_H : -1));
			yaws[i] = register(new FloatSetting("target_yaw" + suf, "가로 각도", "목표 가로 각도입니다. 0이 남쪽입니다.", 0f, -180f, 180f, 1f));
			yaws[i].unit("°");
			pitches[i] = register(new FloatSetting("target_pitch" + suf, "세로 각도", "목표 세로 각도입니다. 0이 수평입니다.", 0f, -90f, 90f, 1f));
			pitches[i].unit("°");
			heres[i] = register(new ActionSetting("here" + suf, "지금 방향", "지금 보고 있는 방향을 이 시점에 넣습니다.", "넣기", () -> captureHere(idx)));
			dels[i] = register(new ActionSetting("delete" + suf, "시점 삭제", "이 시점을 지웁니다.", "삭제", () -> deletePreset(idx)));
			for (Setting<?> s : new Setting<?>[]{keys[i], yaws[i], pitches[i], heres[i], dels[i]}) {
				s.group(g);
			}
		}
		count.hidden();
		count.onChange(this::applyVisibility);
		applyVisibility();
	}

	private int n() {
		return Math.max(0, Math.min(POOL, count.get()));
	}

	private void applyVisibility() {
		int n = n();
		for (int i = 0; i < POOL; i++) {
			boolean hide = i >= n;
			keys[i].setHidden(hide);
			yaws[i].setHidden(hide);
			pitches[i].setHidden(hide);
			heres[i].setHidden(hide);
			dels[i].setHidden(hide);
		}
		if (add != null) {
			add.setHidden(n >= POOL);
		}
	}

	private void addPreset() {
		int n = n();
		if (n >= POOL) {
			return;
		}
		keys[n].setValue(-1);
		if (client != null && client.player != null) {
			yaws[n].setValue(snapAngle(kr.lunaslight.mod.util.LunaCompat.getYaw(client.player), -180f, 180f));
			pitches[n].setValue(snapAngle(kr.lunaslight.mod.util.LunaCompat.getPitch(client.player), -90f, 90f));
		} else {
			yaws[n].setValue(0f);
			pitches[n].setValue(0f);
		}
		count.setValue(n + 1);
	}

	private void captureHere(int i) {
		if (client == null || client.player == null) {
			return;
		}
		yaws[i].setValue(snapAngle(kr.lunaslight.mod.util.LunaCompat.getYaw(client.player), -180f, 180f));
		pitches[i].setValue(snapAngle(kr.lunaslight.mod.util.LunaCompat.getPitch(client.player), -90f, 90f));
	}

	/** 뒤 시점들을 한 칸씩 당기고 수를 줄인다. */
	private void deletePreset(int i) {
		int n = n();
		if (i < 0 || i >= n) {
			return;
		}
		for (int j = i; j < n - 1; j++) {
			keys[j].setValue(keys[j + 1].getValue());
			yaws[j].setValue(yaws[j + 1].getValue());
			pitches[j].setValue(pitches[j + 1].getValue());
		}
		keys[n - 1].setValue(-1);
		yaws[n - 1].setValue(0f);
		pitches[n - 1].setValue(0f);
		active = -1;
		count.setValue(n - 1);
	}

	/** 플레이어 각도를 설정 범위(가로 -180~180, 세로 -90~90)의 정수로. */
	private static float snapAngle(float v, float min, float max) {
		float d = max > 90f ? normalizeAngle(v) : v;
		return Math.max(min, Math.min(max, Math.round(d)));
	}

	@Override
	protected void onDisable() {
		active = -1;
		java.util.Arrays.fill(lastDown, false);
	}

	@Override
	public void onTick() {
		if (client.player == null || WindowAccess.of(client) == null) {
			return;
		}
		int n = n();
		boolean hold = mode.get() == Mode.HOLD;
		int newly = -1, anyDown = -1;
		boolean activeHeld = false;
		for (int i = 0; i < n; i++) {
			boolean down = keys[i].isBound() && keys[i].isDown(client);
			boolean pressed = down && !lastDown[i];
			lastDown[i] = down;
			if (pressed) {
				newly = i;
			}
			if (down && i == active) {
				activeHeld = true;
			}
			if (down && anyDown < 0) {
				anyDown = i;
			}
		}
		for (int i = n; i < POOL; i++) {
			lastDown[i] = false;
		}
		if (hold) {
			// 유지: 새로 누른 시점이 이기고, 아니면 누르고 있던 시점 그대로
			active = newly >= 0 ? newly : activeHeld ? active : anyDown;
		} else if (newly >= 0) {
			// 토글: 같은 키 = 풀기, 다른 키 = 그 시점으로
			active = active == newly ? -1 : newly;
		}
		if (active >= n) {
			active = -1;
		}
		if (active < 0) {
			return;
		}

		float currentYaw = kr.lunaslight.mod.util.LunaCompat.getYaw(client.player);
		float currentPitch = kr.lunaslight.mod.util.LunaCompat.getPitch(client.player);
		float speed = instant.get() ? 360f : turnSpeed.get();

		float newYaw = stepToward(currentYaw, yaws[active].get(), speed);
		float newPitch = stepToward(currentPitch, pitches[active].get(), speed);

		kr.lunaslight.mod.util.LunaCompat.setYaw(client.player, newYaw);
		kr.lunaslight.mod.util.LunaCompat.setPitch(client.player, newPitch);
	}

	/**
	 * current를 target 방향으로 최단 경로(-180~180 정규화)를 따라 최대 maxStep만큼 이동시킨 값.
	 * 예: 359도에서 1도로 갈 때 358도가 아니라 2도만 돌도록 diff를 정규화해서 계산.
	 */
	private static float stepToward(float current, float target, float maxStep) {
		float diff = normalizeAngle(target - current);
		if (Math.abs(diff) <= maxStep) {
			return current + diff;
		}
		return current + Math.copySign(maxStep, diff);
	}

	private static float normalizeAngle(float deg) {
		float d = deg % 360f;
		if (d < -180f) d += 360f;
		if (d > 180f) d -= 360f;
		return d;
	}
}
