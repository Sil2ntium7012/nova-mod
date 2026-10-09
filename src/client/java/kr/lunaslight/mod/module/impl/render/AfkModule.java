package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.AfkWatch;
import kr.lunaslight.mod.util.EntityPos;
import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.util.BlockEntityCullHook;
import kr.lunaslight.mod.util.EntityHideHook;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;

/**
 * 49-63차(3-9): <b>자리 비움</b> - 한참 아무것도 안 하면 그리는 양을 줄여 컴퓨터를 쉬게 한다.
 *
 * <p>49-312차(사용자: "잠수하면 무조건 AFK 고정. AFK 설정(켜기/끄기, 시간 등)은 바꿀 수 없게"): 항상 켜짐(alwaysOn),
 * 설정 칸(대기 시간, 거리)을 없애고 고정값(3분, 24블록)으로. 기능 목록, 검색, [그래픽] 페이지 어디에도 안 나온다.
 *
 * <p><b>두 가지 자리 비움</b>(49-312차, 사용자: "같은 행동을 반복하고 있으면(매크로, 자동 클릭처럼) 고개는 떨구지 말고 AFK는 유지"):
 * <ul>
 *   <li><b>사람 손길</b> = 마우스로 시점을 돌림, 화면을 열고 닫음, 화면 안에서 커서를 움직임, 채팅을 침. 단, 최근 3분 안에 이미
 *       나왔던 모습(시점 각도 + 화면 + 커서 + 채팅 글자)으로 되돌아가는 것은 "되풀이"라 손길로 안 친다 - 매크로가 몇 군데 시점이나
 *       화면을 오가도 자리 비움이 풀리지 않는다. 사람이 마우스를 움직이면 거의 매번 처음 보는 각도라 바로 풀린다.</li>
 *   <li><b>움직임</b> = 위치, 손 휘두르기, 아이템 쓰기, 웅크리기, 핫바 칸 바꾸기(그리고 위의 손길).</li>
 * </ul>
 * 사람 손길이 3분 없으면 자리 비움(AFK). 그중 움직임까지 3분 없으면 <b>잠듦</b>(고개 떨굼), 움직임이 있으면 <b>되풀이</b>(고개는 그대로).
 * 둘 다 머리 위 AFK, 탭 목록 ZZZ, 날개, 망토 숨김은 같다. 남에게는 접속 정보(.luna-presence.json의 afk)로 알린다 - 고개는
 * 보는 쪽이 그 사람 몸이 3분 동안 가만히 있었는지로 직접 판단한다({@link AfkWatch}).
 *
 * <p><b>자리 비움일 때 하는 일</b>: 엔티티와 상자, 간판은 24블록 안쪽만 그리고, 프레임은 60으로(1.21.1 이하는 창에 직접,
 * 그 위는 AfkFpsMixin).
 */
public class AfkModule extends Module {

	/** 이 버전에 창 프레임 상한 API가 있는지(1.15.2~1.21.1). */
	private static final boolean HAS_WINDOW_FPS = LunaVersion.isWithin(null, "1.21.1");

	/** 49-312차: 고정 대기 시간(바꿀 수 없음). 남의 몸 판단(AfkWatch)도 같은 값을 쓴다. */
	public static final long WAIT_MS = 3L * 60_000L;
	private static final double DISTANCE = 24;

	public static final int AFK_FPS = 60;
	private static volatile boolean enabledNow;
	private static volatile boolean afkNow;
	private static volatile boolean idleNow;

	public static boolean enabledNow() {
		return enabledNow;
	}

	/** 지금 자리 비움인가(잠듦이든 되풀이든). 접속 정보, 탭 목록, 머리 위 AFK, 치장 숨김이 본다. */
	public static boolean isAfkNow() {
		return enabledNow && afkNow;
	}

	/** 49-312차: 자리 비움이면서 움직임도 없는가(고개 떨굼). 되풀이 중이면 false. */
	public static boolean isIdleNow() {
		return enabledNow && afkNow && idleNow;
	}

	// ---- 상태 ----
	private boolean afk;
	private long lastHumanMs = System.currentTimeMillis();
	private long lastAnyMs = System.currentTimeMillis();
	private boolean seeded;
	private long lastSig;
	private double lastX, lastY, lastZ;
	private int lastSlot;
	private boolean lastSneak;
	private int savedFps = -1;
	private int tick;
	/** 최근 3분 안에 나온 모습(시점 + 화면 + 커서 + 채팅) → 마지막으로 본 시각. 여기 있는 모습으로 돌아가는 건 손길이 아니다. */
	private final java.util.HashMap<Long, Long> seen = new java.util.HashMap<>();

	public AfkModule() {
		super("afk", "자리 비움", ModuleCategory.FEATURE, "가만히 있으면 자리 비움");
		alwaysOn();
	}

	/** 49-312차: 설정할 게 없다 - 어디에도 안 띄운다. */
	@Override
	public boolean hiddenInList() {
		return true;
	}

	@Override
	protected void onEnable() {
		enabledNow = true;
		seeded = false;
	}

	@Override
	protected void onDisable() {
		enabledNow = false;
		wake();
	}

	@Override
	public void onTick() {
		enabledNow = true;
		try {
			AfkWatch.tick(client);
		} catch (Throwable t) {
			LunaCompat.warnOnce("afkWatch", t);
		}
		if (client == null || client.player == null) {
			wake();
			seeded = false;
			seen.clear();
			return;
		}
		long now = System.currentTimeMillis();
		observe(now);
		if (now - lastHumanMs >= WAIT_MS) {
			idleNow = now - lastAnyMs >= WAIT_MS;
			sleep();
		} else {
			wake();
		}
	}

	private void observe(long now) {
		net.minecraft.entity.player.PlayerEntity p = client.player;
		float yaw = LunaCompat.getYaw(p);
		float pitch = LunaCompat.getPitch(p);
		Object screen = client.currentScreen;
		long sig = Math.round(yaw * 10f) * 31L + Math.round(pitch * 10f);
		if (screen != null) {
			int cx = 0, cy = 0;
			try {
				cx = (int) (client.mouse.getX() / 2);
				cy = (int) (client.mouse.getY() / 2);
			} catch (Throwable ignored) {
			}
			sig = sig * 31L + screen.getClass().getName().hashCode();
			sig = sig * 31L + cx * 4099L + cy;
			sig = sig * 31L + chatText(screen).hashCode();
		}
		double x = EntityPos.x(p), y = EntityPos.y(p), z = EntityPos.z(p);
		int slot = LunaCompat.selectedSlot(p);
		Object sn = LunaCompat.callNoArg(p, "isSneaking");
		boolean sneak = sn instanceof Boolean && (Boolean) sn;
		if (!seeded) {
			seeded = true;
			lastSig = sig;
			lastX = x;
			lastY = y;
			lastZ = z;
			lastSlot = slot;
			lastSneak = sneak;
			lastHumanMs = now;
			lastAnyMs = now;
			seen.clear();
			seen.put(sig, now);
			return;
		}
		boolean any = false;
		if (sig != lastSig) {
			any = true;
			Long at = seen.get(sig);
			if (at == null || now - at >= WAIT_MS) {
				lastHumanMs = now;   // 처음 보는 모습 = 사람 손길
			}
			lastSig = sig;
		}
		seen.put(sig, now);
		if (Math.abs(x - lastX) > 1e-4 || Math.abs(y - lastY) > 1e-4 || Math.abs(z - lastZ) > 1e-4
				|| slot != lastSlot || sneak != lastSneak || AfkWatch.swinging(p) || AfkWatch.usingItem(p)) {
			any = true;
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		lastSlot = slot;
		lastSneak = sneak;
		if (any) {
			lastAnyMs = now;
		}
		if (++tick % 40 == 0) {
			seen.values().removeIf(t -> now - t >= WAIT_MS);
		}
	}

	/** 채팅 화면이면 입력 중인 글자(치는 것도 사람 손길). 아니면 빈 글자. */
	private static String chatText(Object screen) {
		if (!(screen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
			return "";
		}
		try {
			java.lang.reflect.Field f = LunaCompat.findField(net.minecraft.client.gui.screen.ChatScreen.class, "chatField");
			Object box = f == null ? null : f.get(screen);
			Object v = box == null ? null : LunaCompat.callNoArg(box, "getText");
			return v == null ? "" : v.toString();
		} catch (Throwable t) {
			return "";
		}
	}

	private void sleep() {
		EntityHideHook.afkDistanceSq = DISTANCE * DISTANCE;
		BlockEntityCullHook.afkDistanceSq = DISTANCE * DISTANCE;
		if (!afk) {
			afk = true;
			afkNow = true;
			applyFps(HAS_WINDOW_FPS ? AFK_FPS : -1);
		}
	}

	private void wake() {
		EntityHideHook.afkDistanceSq = 0;
		BlockEntityCullHook.afkDistanceSq = 0;
		idleNow = false;
		if (afk) {
			afk = false;
			afkNow = false;
			applyFps(-1);
		}
	}

	/** 창의 프레임 상한(1.21.3+는 메서드가 없어 조용히 아무것도 안 한다). limit &lt; 0이면 원래 값으로. */
	private void applyFps(int limit) {
		try {
			Object window = client == null ? null : WindowAccess.of(client);
			if (window == null) {
				return;
			}
			java.lang.reflect.Method set = LunaCompat.findMethod(window.getClass(), "setFramerateLimit", int.class);
			if (set == null) {
				return;
			}
			if (limit >= 0) {
				if (savedFps < 0) {
					java.lang.reflect.Method get = LunaCompat.findMethod(window.getClass(), "getFramerateLimit");
					Object cur = get == null ? null : get.invoke(window);
					savedFps = cur instanceof Integer ? (Integer) cur : -1;
				}
				set.invoke(window, limit);
			} else if (savedFps >= 0) {
				set.invoke(window, savedFps);
				savedFps = -1;
			}
		} catch (Throwable ignored) {
		}
	}
}
