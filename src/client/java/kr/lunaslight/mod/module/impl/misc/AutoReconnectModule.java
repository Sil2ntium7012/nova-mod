package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.util.LunaCompat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;

/**
 * 19차: ConnectScreen.connect(...) 오버로드가 버전마다 파라미터 개수/순서가 달라서(quickPlay,
 * previousAddress 유무 등) 특정 시그니처를 고정해서 부르면 다른 버전에서 컴파일이 깨짐.
 * 리플렉션으로 정적 connect 메서드를 전부 찾아, 각 파라미터 타입을 보고 아는 값(Screen/
 * MinecraftClient/ServerAddress/ServerInfo)은 채우고 나머지(boolean/참조타입)는 안전한 기본값
 * (false/null)으로 채워서 맞는 오버로드를 실행합니다.
 *
 * 49-44차: 예전엔 DISCONNECT 이벤트만 보고 재접속해서, 일시정지 메뉴의 [서버 나가기]로 직접
 * 나가도 몇 초 뒤에 제멋대로 다시 들어가 버렸다(사용자 입장에선 그냥 안 나가지는 모드).
 * 이벤트만으로는 구분이 안 되지만 <b>화면</b>으로는 된다 - 튕기면(타임아웃/킥/서버 종료)
 * 바닐라가 <code>DisconnectedScreen</code>을 띄우고, 스스로 나가면 서버 목록/타이틀로 바로 간다.
 * 그래서 <b>지금 DisconnectedScreen이 떠 있을 때만</b> 재접속한다. 거기에 더해 그 화면의 사유
 * 문구가 차단/화이트리스트면 재접속하지 않고(못 들어가는 서버를 세 번 두드릴 이유가 없다),
 * 화면 아래에 남은 시간을 그려 무슨 일이 일어나는지 보이게 한다. 사용자가 그 화면을 떠나면
 * (목록으로/타이틀로) 그 자리에서 예약이 취소된다.
 *
 * 21차: ServerInfo.ServerType(3번째 생성자 인자, OTHER 등)가 1.20.1엔 없음(nested enum 이름/구조가
 * 버전마다 다름) - ServerInfo를 직접 new하지 않고 buildServerInfo(...)에서 생성자를 전부 리플렉션
 * 순회하며 (String,String)로 시작하는 것을 찾아, 나머지 파라미터가 enum이면 그 안에서 "OTHER"
 * 상수(없으면 첫 상수)를 채우는 식으로 버전 상관없이 안전하게 생성.
 *
 * 35차: net.minecraft.client.network.ServerAddress를 컴파일 타임에 import해서 쓰면 실제로
 * 1.16~1.19.2 빌드에서 "cannot find symbol"이 났음(claude/nova-mod-todo.md 35차 - 사전 조사와
 * 어긋난 실측 결과라 안전망 차원에서 이관). ServerAddress를 아예 이름으로 참조하지 않고,
 * ConnectScreen.connect(...)의 실제 파라미터 타입 중 "String 하나 받아 자기 타입을 반환하는
 * 정적 팩토리 메서드(parse 등)"를 가진 타입을 찾아 주소 문자열로 그 자리에서 인스턴스를 만드는
 * 식으로 완전히 리플렉션화(tryParseAddress 참고) - 어떤 클래스/패키지에 있든 상관없이 동작.
 */
public class AutoReconnectModule extends Module {

	private final IntSetting delaySeconds;
	private final IntSetting maxAttempts;

	private static volatile String lastAddress = null;
	private int ticksSinceDisconnect = -1;
	private int attemptsUsed = 0;
	/** 차단/화이트리스트 등 다시 들어가 봐야 소용없는 사유일 때 그 문구(화면에 그대로 보여 준다). */
	private String blockedReason = null;

	/** 다시 들어가 봐야 소용없는 사유들 - 한국어/영어 서버 양쪽. */
	private static final String[] HOPELESS = {
		"ban", "banned", "blacklist", "whitelist",
		"밴", "차단", "정지", "화이트리스트", "허용 목록", "등록되지"
	};

	public AutoReconnectModule() {
		super("auto_reconnect", "자동 재접속", ModuleCategory.FEATURE, "튕기면 자동으로 다시 접속(너굴마을 제외)");

		delaySeconds = register(new IntSetting("delay_seconds", "대기 시간", "튕긴 뒤 다시 접속하기까지 기다리는 시간(초)입니다.", 5, 1, 60, 1).unit("초"));
		maxAttempts = register(new IntSetting("max_attempts", "최대 횟수", "연속으로 다시 접속을 시도하는 최대 횟수입니다.", 3, 1, 10, 1));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.getCurrentServerEntry() != null) {
				lastAddress = client.getCurrentServerEntry().address;
			}
			// 정상적으로 접속(재접속 포함)했으므로 시도 횟수 초기화
			attemptsUsed = 0;
			ticksSinceDisconnect = -1;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (isEnabled() && lastAddress != null) {
				ticksSinceDisconnect = 0;
				blockedReason = null;
			}
		});

		// 49-106차(세션 복구): 접속을 "시도하는" 순간의 서버 주소도 잡아 둔다. 로그인 실패(세션 만료)는 JOIN 전에
		// 나므로 JOIN만 봐서는 주소를 모른다 → 접속 중(currentServerEntry가 있을 때) 매 틱 주소를 기억한다.
		// (이 모듈을 꺼 놔도 세션 복구가 재접속 주소를 알 수 있게, isEnabled()과 무관하게 항상 잡는다.)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				ServerInfo entry = client.getCurrentServerEntry();
				if (entry != null && entry.address != null && !entry.address.isEmpty()) {
					lastAddress = entry.address;
				}
			} catch (Throwable ignored) {
			}
		});

		// 49-44차: 끊김 화면 아래에 남은 시간을 그린다(Screen API가 없는 1.15.2는 조용히 빠짐).
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
	}

	@Override
	public void onTick() {
		if (ticksSinceDisconnect < 0) {
			// 49-44차: 재접속 시도가 또 실패하면 DISCONNECT 이벤트가 오지 않는다(월드에 한 번도
			// 못 들어갔으므로). 끊김 화면이 다시 떠 있고 남은 횟수가 있으면 스스로 이어서 센다 -
			// 이게 없으면 "최대 횟수"를 몇으로 두든 실제로는 한 번만 시도하고 끝났다.
			if (attemptsUsed > 0 && attemptsUsed < maxAttempts.get() && lastAddress != null
					&& client.player == null && isDisconnectedScreen(client.currentScreen)) {
				ticksSinceDisconnect = 0;
				blockedReason = null;
			} else {
				return;
			}
		}
		if (client.player != null) {
			// 아직 월드에 있으면(재접속 성공 등) 카운트 중지
			cancel();
			return;
		}
		// 49-44차 핵심: "끊김 화면"이 떠 있을 때만 세어 나간다. 스스로 나갔으면 이 화면이
		// 아예 안 뜨므로(서버 목록/타이틀로 바로 감) 재접속이 시작되지 않는다. 세는 도중에
		// 사용자가 화면을 떠나도(목록/타이틀로 이동) 여기서 바로 취소된다.
		if (!isDisconnectedScreen(client.currentScreen)) {
			cancel();
			return;
		}
		// 49-183차(사용자: "자동 재접속 너굴마을 서버에서는 불가능하게"): 너굴마을에서 튕기면 다시 들어가지 않는다.
		if (isNeogulAddress(lastAddress)) {
			return;
		}
		if (blockedReason == null) {
			blockedReason = hopelessReason(client.currentScreen); // 처음 한 번만 읽어 둔다
		}
		if (!blockedReason.isEmpty()) {
			return; // 차단/화이트리스트 - 화면 문구만 남기고 시도하지 않음
		}
		if (attemptsUsed >= maxAttempts.get() || lastAddress == null) {
			return;
		}

		ticksSinceDisconnect++;
		int delayTicks = delaySeconds.get() * 20;

		if (ticksSinceDisconnect >= delayTicks) {
			ticksSinceDisconnect = -1;
			attemptsUsed++;
			reconnectTo(lastAddress);
		}
	}

	/** 49-106차(세션 복구): 마지막으로 접속했던 서버 주소(없으면 null). 다른 기능이 재접속할 때 쓴다. */
	public static String lastServerAddress() {
		return lastAddress;
	}

	/** 49-256차: 너굴마을에 들어가 있으면 목록에서 회색(거기선 재접속하지 않는다). */
	@Override
	public String unavailableHere() {
		return onRemoteServer() && isNeogulAddress(kr.lunaslight.mod.util.LunaCompat.currentServerAddress(client)) ? "이 서버에서 안 됨" : null;
	}

	/** 49-183차: 너굴마을(mcng.kr) 주소인가 - 거기선 자동 재접속을 하지 않는다. */
	private static boolean isNeogulAddress(String address) {
		return address != null && address.toLowerCase(java.util.Locale.ROOT).contains("mcng");
	}

	private void cancel() {
		ticksSinceDisconnect = -1;
		blockedReason = null;
	}

	// ==================== 끊김 화면 판별 · 사유 읽기 ====================

	/** 지금 화면이 바닐라 DisconnectedScreen인가(버전마다 패키지가 달라 이름으로 찾는다). */
	private static boolean isDisconnectedScreen(Screen screen) {
		if (screen == null) {
			return false;
		}
		try {
			return LunaCompat.classForName("net.minecraft.client.gui.screen.DisconnectedScreen").isInstance(screen);
		} catch (Throwable ignored) {
			// 이름을 못 찾는 환경(아주 예외적) - 예전처럼 화면 이름으로만 본다.
			return screen.getClass().getName().contains("Disconnected");
		}
	}

	/**
	 * 끊김 화면이 들고 있는 사유 Text를 리플렉션으로 긁어(필드 이름이 버전마다 다름) 차단/
	 * 화이트리스트로 보이면 그 문구를 돌려준다. 아니면 빈 문자열(= 재접속해도 되는 끊김).
	 */
	private static String hopelessReason(Screen screen) {
		String text = screenText(screen).toLowerCase(java.util.Locale.ROOT);
		if (text.isEmpty()) {
			return "";
		}
		for (String word : HOPELESS) {
			if (text.contains(word)) {
				return screenText(screen);
			}
		}
		return "";
	}

	/** 화면 인스턴스의 Text 필드(사유·제목)를 전부 이어 붙인다. */
	private static String screenText(Screen screen) {
		StringBuilder sb = new StringBuilder();
		try {
			for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				f.setAccessible(true);
				Object v = f.get(screen);
				if (v instanceof net.minecraft.text.Text t) {
					if (sb.length() > 0) {
						sb.append(' ');
					}
					sb.append(t.getString());
				}
			}
		} catch (Throwable ignored) {
			// 필드를 못 읽는 환경 - 사유 판별만 포기하고 재접속은 정상 진행
		}
		return sb.toString();
	}

	// ==================== 끊김 화면에 남은 시간 그리기 ====================

	private void onScreenFrame(Object screen, DrawContext ctx, int mouseX, int mouseY) {
		if (!isEnabled() || !(screen instanceof Screen s) || !isDisconnectedScreen(s)) {
			return;
		}
		String line;
		if (isNeogulAddress(lastAddress)) {
			line = "너굴마을에서는 자동 재접속을 하지 않습니다";
		} else if (blockedReason != null && !blockedReason.isEmpty()) {
			line = "자동 재접속 안 함 - 들어갈 수 없는 서버입니다";
		} else if (ticksSinceDisconnect < 0 || lastAddress == null) {
			return;
		} else if (attemptsUsed >= maxAttempts.get()) {
			line = "자동 재접속 " + attemptsUsed + "번 모두 실패했습니다";
		} else {
			int left = Math.max(0, (delaySeconds.get() * 20 - ticksSinceDisconnect + 19) / 20);
			line = left > 0
				? left + "초 뒤 다시 접속합니다 | " + (attemptsUsed + 1) + "/" + maxAttempts.get()
				: "다시 접속하는 중… | " + (attemptsUsed + 1) + "/" + maxAttempts.get();
		}
		int w = LunaCompat.getTextWidth(client.textRenderer, line);
		int x = (s.width - w) / 2;
		int y = s.height - 24;
		ctx.fill(x - 6, y - 4, x + w + 6, y + 12, 0x80000000);
		LunaDraw.text(ctx, client.textRenderer, line, x, y, LunaDraw.TEXT_DIM);
	}

	/** 49-106차: 세션 복구 등 다른 기능도 같은 재접속 경로를 쓸 수 있게 static 공개. */
	public static void reconnectTo(String address) {
		try {
			ServerInfo serverInfo = buildServerInfo("Nova Auto-Reconnect", address);
			if (serverInfo == null) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] ServerInfo 생성 실패 - 자동 재접속을 건너뜁니다.");
				return;
			}
			MinecraftClient mc = MinecraftClient.getInstance();

			Class<?> connectScreenClass = kr.lunaslight.mod.util.LunaCompat.classForName("net.minecraft.client.gui.screen.ConnectScreen"); // 46차: 프로덕션 이름 대응
			boolean invoked = false;
			for (java.lang.reflect.Method m : connectScreenClass.getMethods()) {
				if (!kr.lunaslight.mod.util.LunaCompat.nameMatches(connectScreenClass, "connect", m.getName()) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
					continue;
				}
				Object[] args = buildConnectArgs(m.getParameterTypes(), mc, address, serverInfo);
				if (args == null) {
					continue;
				}
				try {
					m.invoke(null, args);
					invoked = true;
					break;
				} catch (Exception ignored) {
					// 이 오버로드는 인자가 안 맞았던 것 - 다음 오버로드 시도.
				}
			}
			if (!invoked) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] ConnectScreen.connect 오버로드를 찾지 못해 자동 재접속 실패");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * ServerInfo(String name, String address, ...) 생성자를 리플렉션으로 찾아 생성.
	 * 3번째 이후 파라미터가 enum(ServerType 등, 버전마다 이름/존재 여부 다름)이면 그 안에서
	 * "OTHER" 상수(없으면 첫 상수)를 채우고, boolean이면 false, 그 외 참조 타입이면 null로 채움.
	 */
	private static ServerInfo buildServerInfo(String name, String address) {
		for (java.lang.reflect.Constructor<?> c : ServerInfo.class.getConstructors()) {
			Class<?>[] params = c.getParameterTypes();
			if (params.length < 2 || params[0] != String.class || params[1] != String.class) {
				continue;
			}
			Object[] args = new Object[params.length];
			args[0] = name;
			args[1] = address;
			boolean fillable = true;
			for (int i = 2; i < params.length; i++) {
				Class<?> p = params[i];
				if (p.isEnum()) {
					Object[] constants = p.getEnumConstants();
					Object chosen = null;
					for (Object k : constants) {
						if (kr.lunaslight.mod.util.LunaCompat.enumNameIs(k, "OTHER")) {
							chosen = k;
							break;
						}
					}
					args[i] = chosen != null ? chosen : (constants.length > 0 ? constants[0] : null);
				} else if (p == boolean.class || p == Boolean.class) {
					args[i] = false;
				} else if (!p.isPrimitive()) {
					args[i] = null;
				} else {
					fillable = false;
					break;
				}
			}
			if (!fillable) {
				continue;
			}
			try {
				return (ServerInfo) c.newInstance(args);
			} catch (Throwable ignored) {
				// 이 생성자 시그니처는 안 맞았던 것 - 다음 후보 시도.
			}
		}
		return null;
	}

	/** connect(...) 파라미터 타입 배열을 보고 아는 타입은 채우고, 모르는 primitive면 이 오버로드를 포기(null). */
	private static Object[] buildConnectArgs(Class<?>[] params, MinecraftClient mc, String rawAddress, ServerInfo info) {
		Object[] args = new Object[params.length];
		for (int i = 0; i < params.length; i++) {
			Class<?> p = params[i];
			if (Screen.class.isAssignableFrom(p)) {
				args[i] = mc.currentScreen;
			} else if (MinecraftClient.class.isAssignableFrom(p)) {
				args[i] = mc;
			} else if (ServerInfo.class.isAssignableFrom(p)) {
				args[i] = info;
			} else if (p == boolean.class || p == Boolean.class) {
				args[i] = false;
			} else if (!p.isPrimitive()) {
				// 알 수 없는 참조 타입 - ServerAddress류(주소 문자열 하나로 자기 타입을 만드는 정적
				// 팩토리를 가진 타입)일 수 있으니 먼저 시도, 아니면(Runnable 콜백 등) null로 채움.
				Object parsed = tryParseAddress(p, rawAddress);
				args[i] = parsed;
			} else {
				return null; // 알 수 없는 primitive 파라미터 - 이 오버로드는 채울 수 없음
			}
		}
		return args;
	}

	/** type이 "String 하나 받아 자기 타입을 반환하는 정적 메서드"(parse 등)를 가지면 그걸로 인스턴스 생성. */
	private static Object tryParseAddress(Class<?> type, String rawAddress) {
		for (java.lang.reflect.Method m : type.getMethods()) {
			if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
				continue;
			}
			if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) {
				continue;
			}
			if (!type.isAssignableFrom(m.getReturnType())) {
				continue;
			}
			try {
				Object result = m.invoke(null, rawAddress);
				if (result != null) {
					return result;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}
}
