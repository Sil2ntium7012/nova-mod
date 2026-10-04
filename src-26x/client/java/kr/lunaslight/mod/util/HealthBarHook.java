package kr.lunaslight.mod.util;

import java.util.function.BooleanSupplier;

/**
 * 49-34차(사용자: "체력 내가 1줄로 고정시키라고 했잖아"): 최대 체력이 20을 넘으면 바닐라는 하트를
 * 두 줄·세 줄로 겹쳐 쌓는다. HealthBarMixin(InGameHud#renderHealthBar HEAD)이 여기에 물어봐서
 * true면 바닐라 그리기를 취소하고, SimpleHealthHudModule이 **한 줄(10칸)로 압축한 하트**를 대신 그린다.
 * 모듈 클래스를 mixin에서 직접 참조하지 않게 분리(ScrollHook과 같은 이유).
 *
 * 49-35차: 갑옷 줄. 바닐라 renderStatusBars는 갑옷을 "하트 줄 수"만큼 위로 띄워 그리므로(하트 줄이
 * 2줄이면 갑옷이 한 칸 더 위) 하트를 한 줄로 줄이면 하트와 갑옷 사이가 벌어진다 → {@link #compressing()}이
 * true인 프레임엔 갑옷도 한 줄 기준(hearts y − 10)으로 그리게 HealthBarMixin이 인자를 바꿔 준다.
 */
public final class HealthBarHook {
	private HealthBarHook() {
	}

	public interface Handler {
		/**
		 * @return true면 바닐라 하트 줄을 그리지 않는다(내가 대신 그림).
		 */
		boolean replace(Object drawContext, int x, int y, float maxHealth, int lastHealth, int health, int absorption);
	}

	private static volatile Handler handler;
	private static volatile BooleanSupplier compressing;

	/** 1.20.1~1.20.4: renderStatusBars의 갑옷 y 지역변수 슬롯(17)을 세 버전 다 javap로 실측(bipush 10; isub; istore 17) - 그때만 건드린다. */
	private static final boolean LEGACY_ARMOR_SLOT = LunaVersion.isWithin("1.20.1", "1.20.4");

	public static void set(Handler h) {
		handler = h;
	}

	/** 모듈이 "지금 하트를 한 줄로 압축해 그리는 중인가"를 알려 주는 공급자. */
	public static void setCompressing(BooleanSupplier s) {
		compressing = s;
	}

	public static boolean dispatch(Object drawContext, int x, int y, float maxHealth, int lastHealth, int health, int absorption) {
		Handler h = handler;
		if (h == null) {
			return false;
		}
		try {
			return h.replace(drawContext, x, y, maxHealth, lastHealth, health, absorption);
		} catch (Throwable t) {
			LunaCompat.warnOnce("healthBar", t);
			return false;
		}
	}

	/** 이번 프레임에 하트가 한 줄로 압축되는가(갑옷 줄 위치 결정용, 매 프레임 호출되므로 가볍게). */
	public static boolean compressing() {
		BooleanSupplier s = compressing;
		if (s == null) {
			return false;
		}
		try {
			return s.getAsBoolean();
		} catch (Throwable t) {
			LunaCompat.warnOnce("healthBarRows", t);
			return false;
		}
	}

	/** 1.20.3+ : renderArmor(…, y, lines, rowHeight, x)의 lines를 1로 → 바닐라가 갑옷을 하트 바로 위에 그린다. */
	public static int armorRows(int lines) {
		return lines > 1 && compressing() ? 1 : lines;
	}

	/** 1.20.1~1.20.4 : 갑옷 y(= o − (줄수−1)×줄높이 − 10)를 한 줄 기준(o − 10 = 화면 높이 − 49)으로. */
	public static int legacyArmorY(int original) {
		if (!LEGACY_ARMOR_SLOT || !compressing()) {
			return original;
		}
		try {
			return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight() - 49;
		} catch (Throwable t) {
			return original;
		}
	}
}
