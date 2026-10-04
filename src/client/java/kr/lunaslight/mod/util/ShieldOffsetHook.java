package kr.lunaslight.mod.util;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 49-75차(2-3의 뒤 절반): <b>1인칭 방패 높이</b> - 손에 든 방패만 위아래로 옮긴다.
 *
 * <h3>49-69차에 "위험하다"고 미뤄 둔 자리인데, 재 보니 위험하지 않았다</h3>
 * 그때 적은 걱정은 "{@code renderFirstPersonItem}이 거대한 메서드라 특정 이동 한 번을 집어내다
 * 빗맞으면 손이 통째로 어긋난다"였다. 그건 <b>메서드 안쪽 어딘가에 끼어들 때</b>의 이야기다.
 * 여기서는 안쪽을 건드리지 않는다 - <b>메서드 전체를 감싼다</b>:
 *
 * <ul>
 *   <li>들어갈 때({@code HEAD}) 행렬을 <b>밀어 두고</b>(push + translate),</li>
 *   <li>나올 때({@code RETURN}) <b>되돌린다</b>(pop).</li>
 * </ul>
 *
 * 바닐라가 안에서 하는 push/pop은 자기들끼리 짝이 맞으므로, 내 push 하나 위에 무엇이 쌓이든
 * 나올 때의 깊이는 들어갈 때 + 1이다. 그래서 pop 하나로 정확히 되돌아간다.
 *
 * <h3>왜 손은 안 움직이나</h3>
 * {@code renderFirstPersonItem}은 <b>손 하나 · 아이템 하나</b>마다 따로 불린다. 그래서
 * <b>그 호출의 아이템이 방패일 때만</b> 밀면, 방패를 든 손만 움직이고 반대 손과 다른 아이템은
 * 예전 그대로다. 방패를 안 들었으면 <b>push조차 하지 않는다</b>(비용 0).
 *
 * <p><b>무엇을 방패로 보나</b>: 바닐라 방패({@code Items.SHIELD})만. 모드가 넣은 다른 막는 아이템은
 * 안 움직인다 - "막을 수 있는 것 전부"로 넓히면 무엇이 움직일지 예측이 안 된다.
 *
 * <h3>단위에 대해 솔직히</h3>
 * 여기 좌표는 화면 픽셀이 아니라 <b>블록 단위의 모델 공간</b>이다(바닐라도 방패를 {@code -0.24}
 * 같은 값으로 옮긴다). 그래서 설정 값(-50~50)을 {@link #SPAN}으로 나눠 쓴다 - 끝까지 올리면
 * 약 4분의 1 블록이다. 몇이 "딱 좋은지"는 시야각·화면 비율마다 달라서
 * <b>눈으로 맞추는 슬라이더</b>로 두었다. 내가 숫자를 정해 주는 척하는 것보다 낫다.
 */
public final class ShieldOffsetHook {
	private ShieldOffsetHook() {
	}

	/** -50 ~ 50 한 칸이 모델 좌표로 얼마인지. 50이 약 4분의 1 블록이 되도록 잡았다. */
	private static final float SPAN = 200f;

	/** 위로 올리면 양수. 0이면 손대지 않는다. */
	public static volatile int offset;

	/**
	 * <b>손마다 따로 세는 깊이.</b> 같은 프레임에 주 손과 보조 손이 잇달아 불리고, 안쪽에서
	 * 예외가 나면 RETURN이 안 올 수도 있다. 단순한 boolean 하나로 짝을 맞추면 그런 순간에
	 * 어긋나서 <b>화면 전체가 깨진다</b>. 그래서 "민 횟수"를 세고, pop은 센 만큼만 한다.
	 */
	private static int pushed;

	public static void push(ItemStack stack, MatrixStack matrices) {
		int v = offset;
		if (v == 0 || matrices == null || !isShield(stack)) {
			return;
		}
		try {
			matrices.push();
			matrices.translate(0.0, v / SPAN, 0.0);
			pushed++;
		} catch (Throwable t) {
			LunaCompat.warnOnce("shieldOffset:push", t);
		}
	}

	public static void pop(MatrixStack matrices) {
		if (pushed <= 0 || matrices == null) {
			return;
		}
		pushed--;
		try {
			matrices.pop();
		} catch (Throwable t) {
			LunaCompat.warnOnce("shieldOffset:pop", t);
		}
	}

	/**
	 * 바닐라 방패인지. {@code ItemStack#isOf}는 1.17에 생겨서 못 쓰고, {@code ShieldItem} 클래스는
	 * 1.21.5에서 없어졌다(둘 다 javap 실측). {@code getItem() == Items.SHIELD}는
	 * <b>1.15.2부터 26.x까지 전부</b> 되는 유일한 길이다.
	 */
	private static boolean isShield(ItemStack stack) {
		return stack != null && stack.getItem() == Items.SHIELD;
	}
}
