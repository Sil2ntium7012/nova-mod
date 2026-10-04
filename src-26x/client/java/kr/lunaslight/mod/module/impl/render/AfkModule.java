package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.SettingsPage;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.BlockEntityCullHook;
import kr.lunaslight.mod.util.EntityHideHook;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;

/**
 * 49-63차(3-9): <b>자리 비움</b> - 한참 아무것도 안 하면 그리는 양을 줄여 컴퓨터를 쉬게 한다.
 *
 * <p>사용자 요청 3-9: "AFK 기능 (FPS·엔티티 제한 등)".
 *
 * <p><b>무엇을 보고 "자리 비움"으로 보나</b>: <b>시점과 위치</b>다. 마우스를 조금이라도 움직이면 시점이
 * 바뀌고, 걷거나 밀리면 위치가 바뀐다. 둘 다 정해진 시간 동안 그대로면 자리 비움으로 본다.
 * 화면(인벤토리·설정 등)을 열거나 닫는 것도 "뭔가 했다"로 친다.
 *
 * <p>깨어나는 건 <b>즉시</b>다 - 다음 틱에 시점이나 위치가 조금만 달라져도 바로 원래대로 돌아간다.
 * 그래서 "돌아왔는데 화면이 이상한" 구간이 없다.
 *
 * <p><b>자리 비움일 때 하는 일</b>
 * <ol>
 *   <li><b>먼 것 안 그리기</b> - 엔티티와 상자·간판을 정한 거리 안쪽만 그린다. 49-59·60·62차에
 *       만든 관문을 그대로 쓴다(믹스인이 안 늘었다). 두 기능(엔티티 줄이기·상자 화로 줄이기)이 이미
 *       거리를 걸어 뒀다면 <b>더 가까운 쪽</b>이 이긴다 - 서로 값을 뺏지 않게 칸을 따로 뒀다.</li>
 *   <li><b>프레임 제한</b> - 창의 프레임 상한을 내린다.</li>
 * </ol>
 *
 * <p><b>프레임 제한이 1.21.3부터 없는 이유</b>(실측): {@code Window.setFramerateLimit(int)}은
 * 1.15.2 ~ 1.21.1에만 있고 1.21.3부터 사라졌다. 대신 그 시대의 마인크래프트는 <b>자기가 이미</b>
 * 같은 일을 한다({@code InactivityFpsLimiter} - 가만히 있으면 프레임을 알아서 내린다).
 * 그래서 그 버전에서는 <b>설정 자체를 안 만든다</b> - 있는데 아무 일도 안 하는 칸을 두느니 없는 게 낫다.
 *
 * <p>프레임 상한은 <b>설정 파일에 저장되는 값이 아니라 창에 직접</b> 건다. 그래서 자리 비움이 풀리면
 * 원래 값으로 정확히 돌아가고, 게임이 그 사이에 꺼져도 사용자의 저장된 최대 프레임 설정은 그대로다.
 */
public class AfkModule extends Module {

	/** 이 버전에 창 프레임 상한 API가 있는지(1.15.2~1.21.1). 없으면 그 설정을 아예 안 만든다. */
	private static final boolean HAS_WINDOW_FPS = LunaVersion.isWithin(null, "1.21.1");

	private final IntSetting waitMinutes = register(new IntSetting(
			"wait", "대기 시간", "시점도 위치도 이만큼 그대로면 자리 비움으로 봅니다(분).", 3, 1, 30, 1));

	private final IntSetting distance = register(new IntSetting(
			"distance", "거리", "자리 비움일 때 이 거리(블록) 밖은 안 그립니다.", 24, 8, 96, 4).unit("블록"));

	/**
	 * 49-201차(사용자: "잠수 모드 시 FPS가 60에 고정되고, 같은 클라이언트 쓰는 사람들한테 잠수라고 Zzz 뜨게 - 머리 위랑 탭리스트"):
	 * [프레임 제한] 설정(기본 10)을 없애고 자리 비움이면 <b>60 고정</b>. 1.21.1 이하는 창에 직접 60을 걸고, 1.21.2+는 마크가
	 * 가만히 있으면 스스로 30/10으로 내리는(InactivityFpsLimiter) 값을 AfkFpsMixin이 60으로 바꾼다(최소화 중은 그대로 10).
	 * 자리 비움 여부는 루나 접속 정보(.luna-presence.json의 afk)로도 올라가 같은 서버의 루나 유저에게 Zzz로 보인다.
	 */
	public static final int AFK_FPS = 60;
	private static volatile boolean enabledNow;
	private static volatile boolean afkNow;

	/** 이 기능이 켜져 있는가(AfkFpsMixin이 본다). */
	public static boolean enabledNow() {
		return enabledNow;
	}

	/** 지금 자리 비움인가(켜져 있을 때만 true). 접속 정보, 탭리스트, 머리 위 Zzz가 본다. */
	public static boolean isAfkNow() {
		return enabledNow && afkNow;
	}

	// ---- 상태 ----
	private boolean afk;
	private long lastActivityMs = System.currentTimeMillis();
	private double lastX, lastY, lastZ;
	private float lastYaw, lastPitch;
	private Object lastScreen;
	private boolean seeded;
	private int savedFps = -1;

	public AfkModule() {
		super("afk", "자리 비움", ModuleCategory.FEATURE, "가만히 있을 때 그리는 양 줄임");
		// 사용자 요청 목록 "3. 그래픽 설정"의 3-9 → [그래픽] 설정 페이지.
		settingsPage(SettingsPage.GRAPHICS);
	}

	@Override
	protected void onEnable() {
		enabledNow = true;
		seeded = false;
		lastActivityMs = System.currentTimeMillis();
	}

	@Override
	protected void onDisable() {
		enabledNow = false;
		wake();
	}

	@Override
	public void onTick() {
		enabledNow = true;   // 설정 파일에서 켜진 채 불러와 onEnable이 안 불린 경우
		if (client == null || client.player == null) {
			wake();
			seeded = false;
			return;
		}
		if (moved()) {
			lastActivityMs = System.currentTimeMillis();
			wake();
			return;
		}
		long idleMs = System.currentTimeMillis() - lastActivityMs;
		if (idleMs >= waitMinutes.get() * 60_000L) {
			sleep();
		}
	}

	/**
	 * 시점·위치·열린 화면이 지난 틱과 달라졌는지. 처음 한 번은 기준만 잡고 "움직였다"로 보지 않는다.
	 * 시점은 마우스를 조금만 움직여도 바뀌므로 이것만으로 사람이 붙어 있는지 거의 다 잡힌다.
	 */
	private boolean moved() {
		double x = client.player.getX();
		double y = client.player.getY();
		double z = client.player.getZ();
		float yaw = LunaCompat.getYaw(client.player);
		float pitch = LunaCompat.getPitch(client.player);
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		boolean changed = !seeded
				|| x != lastX || y != lastY || z != lastZ
				|| yaw != lastYaw || pitch != lastPitch
				|| screen != lastScreen;
		lastX = x;
		lastY = y;
		lastZ = z;
		lastYaw = yaw;
		lastPitch = pitch;
		lastScreen = screen;
		boolean first = !seeded;
		seeded = true;
		return changed && !first;
	}

	private void sleep() {
		double d = distance.get();
		EntityHideHook.afkDistanceSq = d * d;
		BlockEntityCullHook.afkDistanceSq = d * d;
		if (!afk) {
			afk = true;
			afkNow = true;
			applyFps(HAS_WINDOW_FPS ? AFK_FPS : -1);
		}
	}

	private void wake() {
		EntityHideHook.afkDistanceSq = 0;
		BlockEntityCullHook.afkDistanceSq = 0;
		if (afk) {
			afk = false;
			afkNow = false;
			applyFps(-1);   // 저장해 둔 원래 값으로
		}
	}

	/**
	 * 창의 프레임 상한을 건다. limit &lt; 0이면 자리 비움에 들어갈 때 적어 둔 원래 값으로 되돌린다.
	 * 1.21.3+에는 메서드가 없어 조용히 아무것도 안 한다(그 버전은 마크가 직접 한다 - 클래스 주석 참고).
	 */
	private void applyFps(int limit) {
		try {
			Object window = client == null ? null : client.getWindow();
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
					savedFps = cur instanceof Integer i ? i : -1;
				}
				set.invoke(window, limit);
			} else if (savedFps >= 0) {
				set.invoke(window, savedFps);
				savedFps = -1;
			}
		} catch (Throwable ignored) {
			// 창을 못 건드리는 상황이면 거리 줄이기만으로도 자리 비움의 값은 한다
		}
	}
}
