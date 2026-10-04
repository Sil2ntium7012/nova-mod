package kr.lunaslight.mod.util;

/**
 * 49-39차(사용자: "갑옷 색상을 내가 입은 갑옷별로 - 다이아는 파랑, 금은 노랑 / 인챈트되면 반짝이게"):
 * 바닐라 갑옷 줄(10칸)을 ArmorBarColorModule이 대신 그린다. 믹스인은 버전별로 두 경로:
 *  · 1.20.5+  InGameHud#renderArmor HEAD 취소 → {@link #dispatchRow} (한 번에 10칸)
 *  · 1.20.1~1.20.4  renderStatusBars 안 인라인 drawTexture 호출을 Redirect → {@link #dispatchSlot} (칸마다)
 * 모듈 클래스를 믹스인이 직접 참조하지 않게 분리(HealthBarHook·ScrollHook과 같은 이유).
 */
public final class ArmorBarHook {
	private ArmorBarHook() {
	}

	public interface Handler {
		/** 10칸 전부. true면 바닐라 갑옷 줄을 그리지 않는다. */
		boolean drawRow(Object drawContext, int x, int y);

		/** 한 칸(slot 0~9). true면 그 칸의 바닐라 아이콘을 그리지 않는다. */
		boolean drawSlot(Object drawContext, int x, int y, int slot);
	}

	private static volatile Handler handler;

	public static void set(Handler h) {
		handler = h;
	}

	public static boolean dispatchRow(Object drawContext, int x, int y) {
		Handler h = handler;
		if (h == null) {
			return false;
		}
		try {
			return h.drawRow(drawContext, x, y);
		} catch (Throwable t) {
			LunaCompat.warnOnce("armorBar", t);
			return false;
		}
	}

	/** 인라인 시대: 바닐라가 (x, y)에 아이콘 하나를 찍으려는 순간. x로 칸 번호를 되짚는다(왼쪽 끝 = 화면 폭/2 − 91). */
	public static boolean dispatchSlot(Object drawContext, int x, int y) {
		Handler h = handler;
		if (h == null) {
			return false;
		}
		try {
			int startX = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - 91;
			int slot = Math.floorDiv(x - startX, 8);
			if (slot < 0 || slot > 9 || (x - startX) % 8 != 0) {
				return false;   // 갑옷 줄이 아닌 다른 아이콘(안전장치)
			}
			return h.drawSlot(drawContext, x, y, slot);
		} catch (Throwable t) {
			LunaCompat.warnOnce("armorBarSlot", t);
			return false;
		}
	}
}
