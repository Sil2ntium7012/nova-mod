package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 49-6차: "초당 블럭 파괴수 안 됨" 수정. 원인 두 가지였음:
 *  1) PlayerBlockBreakEvents.AFTER는 (통합)서버 쪽에서 ServerPlayerEntity로 발화 -
 *     `player != client.player` 객체 비교가 항상 참이라 전부 걸러짐 → UUID로 비교.
 *  2) 멀티플레이(전용 서버)에서는 그 이벤트가 클라에서 아예 안 옴 → Fabric API의 클라 전용
 *     ClientPlayerBlockBreakEvents.AFTER(1.21.11 fabric-api에 존재 확인)를 리플렉션+Proxy로
 *     우선 등록(구버전 fabric-api엔 없을 수 있어 컴파일 의존 없이). 성공하면 서버 이벤트는
 *     계수에 안 써서 싱글에서 이중 카운트도 없음.
 */
public class BlocksPerSecondHudModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 4, 41)));

	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

	// 서버/클라 스레드 양쪽에서 접근 가능 - 동시성 안전 자료구조 사용
	private final ConcurrentLinkedDeque<Long> breakTimestamps = new ConcurrentLinkedDeque<>();
	private boolean clientEventRegistered;

	public BlocksPerSecondHudModule() {
		super("blocks_broken_hud", "초당 블록", ModuleCategory.HUD, "초당 캔 블록 수");
		enableHudStyle();

		clientEventRegistered = tryRegisterClientBreakEvent();

		// 폴백(구버전 fabric-api): 통합 서버 이벤트 + UUID 비교(싱글 전용)
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (clientEventRegistered) {
				return;
			}
			var cp = client.player;
			if (cp == null || player == null || !player.getUuid().equals(cp.getUuid())) {
				return;
			}
			if (isEnabled()) {
				breakTimestamps.addLast(System.currentTimeMillis());
			}
			// 49-27차: 통계(아이템별 캔 횟수)
			kr.lunaslight.mod.util.LunaStats.onBlockMined(
					kr.lunaslight.mod.util.LunaCompat.blockId(state), kr.lunaslight.mod.util.LunaCompat.blockName(state));
		});
	}

	/** 49-27차: 클라이언트 이벤트 인자(world, player, pos, state, blockEntity)에서 블록 종류를 꺼내 통계에 기록. */
	private static void noteMined(Object[] args) {
		try {
			Object state = args != null && args.length >= 4 ? args[3] : null;
			kr.lunaslight.mod.util.LunaStats.onBlockMined(
					kr.lunaslight.mod.util.LunaCompat.blockId(state), kr.lunaslight.mod.util.LunaCompat.blockName(state));
		} catch (Throwable ignored) {
		}
	}

	/** ClientPlayerBlockBreakEvents.AFTER를 리플렉션으로 등록(클래스가 있는 fabric-api에서만). */
	private boolean tryRegisterClientBreakEvent() {
		try {
			Class<?> eventsClass = Class.forName(
					"net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents");
			Object event = eventsClass.getField("AFTER").get(null);
			Class<?> afterItf = Class.forName(
					"net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents$After");
			InvocationHandler handler = (proxy, method, args) -> {
				if ("afterBlockBreak".equals(method.getName())) {
					if (isEnabled()) {
						breakTimestamps.addLast(System.currentTimeMillis());
					}
					// 49-27차: 통계(아이템별 캔 횟수)는 이 기능이 꺼져 있어도 기록
					noteMined(args);
					return null;
				}
				// hashCode/equals/toString 기본 처리
				return switch (method.getName()) {
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					case "toString" -> "LunaBpsListener";
					default -> null;
				};
			};
			Object listener = Proxy.newProxyInstance(afterItf.getClassLoader(), new Class<?>[]{afterItf}, handler);
			event.getClass().getMethod("register", Object.class).invoke(event, listener);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		Long first;
		while ((first = breakTimestamps.peekFirst()) != null && now - first > 1000) {
			breakTimestamps.pollFirst();
		}
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		int count = breakTimestamps.size();
		String text = count + " b/s";   // 49-124차(사용자: "블록은 b/s"): 속도(m/s)와 구분
		int textWidth = LunaCompat.getTextWidth(client.textRenderer, text);
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), textWidth);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
