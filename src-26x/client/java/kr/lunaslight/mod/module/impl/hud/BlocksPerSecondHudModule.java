package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	public BlocksPerSecondHudModule() {
		super("blocks_broken_hud", "초당 블록", ModuleCategory.HUD, "초당 캔 블록 수");
		enableHudStyle();
		// 49-506차(사용자: "bps 작동을 안 해"): 예전엔 Fabric 블록 부수기 이벤트로 셌는데, 서버가 캐기를 직접 처리하는 곳(너굴마을 등 -
		// 클라가 끝까지 못 부수고 서버가 블록을 지운다)에서는 이벤트가 안 와서 늘 0이었다. 통계(캔 블록)와 같은 감지 - "공격 키를 누른 채
		// 조준하던 블록이 공기나 다른 블록으로 바뀜, 작물은 자란 단계가 줄어듦"(LunaStats.trackMining) - 로 센다. 싱글, 바닐라 서버도 같다.
		// 바닐라처럼 클라가 직접 부수는 곳(한 번에 부서지는 블록 포함)은 Fabric 클라 이벤트도 받는다. 같은 자리를 두 번 세지 않게 자리로 거른다.
		kr.lunaslight.mod.util.LunaStats.minedListener = this::count;
		try {
			net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register(
				(world, player, pos, state) -> count(pos.asLong()));
		} catch (Throwable t) {
			LunaCompat.warnOnce("bps:event", t);
		}
	}

	/** 최근에 센 자리 → 시각(같은 블록을 두 길로 두 번 세지 않게). */
	private final java.util.Map<Long, Long> recent = new java.util.concurrent.ConcurrentHashMap<>();

	private void count(long pos) {
		if (!isEnabled()) {
			return;
		}
		long now = System.currentTimeMillis();
		Long at = recent.put(pos, now);
		if (at != null && now - at < 2000) {
			return;
		}
		breakTimestamps.addLast(now);
	}

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		if (recent.size() > 64) {
			recent.values().removeIf(t -> now - t > 2000);
		}
		Long first;
		while ((first = breakTimestamps.peekFirst()) != null && now - first > 1000) {
			breakTimestamps.pollFirst();
		}
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		int count = breakTimestamps.size();
		String text = count + " b/s";   // 49-124차(사용자: "블록은 b/s"): 속도(m/s)와 구분
		int textWidth = LunaCompat.getTextWidth(client.font, text);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
