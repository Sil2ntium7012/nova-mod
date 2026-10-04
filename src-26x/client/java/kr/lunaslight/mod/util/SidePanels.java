package kr.lunaslight.mod.util;

/**
 * 49-133차: GUI 옆에 붙는 판들끼리 자리를 나누는 곳. 제작 도우미가 작업대 오른쪽에 패널을 띄우면 그 오른쪽 끝을 여기 적고,
 * 인벤토리 탭은 이걸 보고 겹치지 않게 그 옆으로 밀려난다. (두 모듈이 서로를 직접 참조하면 제작 도우미가 빠지는
 * 옛 버전 빌드에서 인벤토리 탭까지 깨지므로 중립 자리를 둔다.)
 */
public final class SidePanels {

	private static volatile Object rightScreen;
	private static volatile int rightEdge = -1;

	private SidePanels() {
	}

	/** 이 화면 오른쪽에 판이 떠 있고 그 오른쪽 끝이 x임을 적는다. */
	public static void setRight(Object screen, int x) {
		rightScreen = screen;
		rightEdge = x;
	}

	/** 이 화면의 판이 사라졌으면 지운다. */
	public static void clearRight(Object screen) {
		if (screen == rightScreen) {
			rightScreen = null;
			rightEdge = -1;
		}
	}

	/** 이 화면 오른쪽에 떠 있는 판의 오른쪽 끝 x, 없으면 -1. */
	public static int rightEdge(Object screen) {
		return screen != null && screen == rightScreen ? rightEdge : -1;
	}
}
