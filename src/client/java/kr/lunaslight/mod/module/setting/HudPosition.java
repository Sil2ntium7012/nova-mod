package kr.lunaslight.mod.module.setting;

/**
 * 화면 모서리 기준 상대 좌표 + 스케일. HUD 요소 드래그 배치(모든 HUD 모듈 공용)에 사용.
 * anchor 기준 (offsetX, offsetY) 만큼 떨어진 위치에 그림 - 해상도가 바뀌어도 위치가 안 틀어짐.
 *
 * 47차: HUD 편집기(LunaHudEditorScreen)를 위해 "마지막으로 그려진 실제 화면 영역"을 기억함.
 * 각 HUD 모듈은 매 프레임 resolveX/resolveY를 호출하므로 여기서 x/y/폭/높이를 받아 적어두면
 * 편집기가 모듈 코드를 하나도 건드리지 않고도 드래그 박스를 정확히 그릴 수 있음.
 */
public class HudPosition {
	public enum Anchor {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, BOTTOM_CENTER,
		LEFT_CENTER, RIGHT_CENTER   // 49-22차: 세로 가운데(효과 HUD를 화면 오른쪽 중간에)
	}

	public Anchor anchor;
	public float offsetX;
	public float offsetY;
	public float scale;

	// ---- 편집기용: 마지막 렌더 영역(저장 안 함) ----
	private transient int lastX, lastY, lastWidth, lastHeight;
	private transient long lastResolveNanos;

	public HudPosition(Anchor anchor, float offsetX, float offsetY, float scale) {
		this.anchor = anchor;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.scale = scale;
	}

	public static HudPosition of(Anchor anchor, float x, float y) {
		return new HudPosition(anchor, x, y, 1.0f);
	}

	/** 실제 화면 픽셀 좌표로 변환. elementWidth/Height는 앵커가 오른쪽/아래쪽일 때 보정용. */
	public int resolveX(int screenWidth, int elementWidth) {
		int x = switch (anchor) {
			case TOP_LEFT, BOTTOM_LEFT, LEFT_CENTER -> Math.round(offsetX);
			case TOP_RIGHT, BOTTOM_RIGHT, RIGHT_CENTER -> Math.round(screenWidth - offsetX - elementWidth * scale);
			case TOP_CENTER, BOTTOM_CENTER -> Math.round((screenWidth - elementWidth * scale) / 2f + offsetX);
		};
		// 49-76차: 화면 밖으로는 안 나가게 잘라 넣는다. 가운데 기준으로 크게 띄운 요소(키스트로크 +153)가
		// 좁은 창에서 밖으로 나가던 것 - 요소가 화면보다 크면 왼쪽/위에 붙인다.
		x = Math.max(0, Math.min(x, Math.round(screenWidth - elementWidth * scale)));
		lastX = x;
		lastWidth = Math.max(1, Math.round(elementWidth * scale));
		lastResolveNanos = System.nanoTime();
		return x;
	}

	public int resolveY(int screenHeight, int elementHeight) {
		int y = switch (anchor) {
			case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> Math.round(offsetY);
			case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> Math.round(screenHeight - offsetY - elementHeight * scale);
			case LEFT_CENTER, RIGHT_CENTER -> Math.round((screenHeight - elementHeight * scale) / 2f + offsetY);
		};
		y = Math.max(0, Math.min(y, Math.round(screenHeight - elementHeight * scale)));
		lastY = y;
		lastHeight = Math.max(1, Math.round(elementHeight * scale));
		lastResolveNanos = System.nanoTime();
		return y;
	}

	// ---- 편집기 API ----

	/** 최근(0.5초 내)에 실제로 그려진 적이 있는지. 없으면 편집기가 기본 크기 박스를 씀. */
	public boolean hasRecentBounds() {
		return lastWidth > 0 && (System.nanoTime() - lastResolveNanos) < 500_000_000L;
	}

	public int getLastX() {
		return lastX;
	}

	public int getLastY() {
		return lastY;
	}

	public int getLastWidth() {
		return lastWidth;
	}

	public int getLastHeight() {
		return lastHeight;
	}

	/** 마지막으로 resolveX/Y가 불린 시각(nanoTime) - 편집기가 "이번 프레임에 그렸는지" 판단. */
	public long getLastResolveNanos() {
		return lastResolveNanos;
	}

	/** 49-24차: 소수 offset의 남는 부분(정확한 위치 − 반올림된 위치, -0.5~0.5). 편집기 드래그를 부드럽게 보이는 데 씀. */
	public float fracX(int screenWidth, int screenElementWidth) {
		float exact = switch (anchor) {
			case TOP_LEFT, BOTTOM_LEFT, LEFT_CENTER -> offsetX;
			case TOP_RIGHT, BOTTOM_RIGHT, RIGHT_CENTER -> screenWidth - offsetX - screenElementWidth;
			case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - screenElementWidth) / 2f + offsetX;
		};
		return exact - Math.round(exact);
	}

	public float fracY(int screenHeight, int screenElementHeight) {
		float exact = switch (anchor) {
			case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> offsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> screenHeight - offsetY - screenElementHeight;
			case LEFT_CENTER, RIGHT_CENTER -> (screenHeight - screenElementHeight) / 2f + offsetY;
		};
		return exact - Math.round(exact);
	}

	/**
	 * 요소의 왼쪽 위 모서리를 화면 좌표 (x, y)에 두도록 현재 앵커 기준 offset을 다시 계산.
	 * 드래그 중 매 프레임 호출(앵커는 유지, 놓을 때 reanchor로 가장 가까운 모서리로 바꿈).
	 */
	public void moveTo(int x, int y, int screenWidth, int screenHeight, int elementWidth, int elementHeight) {
		moveTo((float) x, (float) y, screenWidth, screenHeight, elementWidth, elementHeight);
	}

	/**
	 * 49-24차: 소수 좌표 판(드래그 중 부드럽게) + elementWidth/Height는 **화면에 보이는 크기**(배율 적용 후 =
	 * getLastWidth/Height)로 받는다(예전엔 배율 전 크기를 받아 다시 곱했음 - 배율 1이 아니면 두 번 곱해짐).
	 */
	public void moveTo(float x, float y, int screenWidth, int screenHeight, int elementWidth, int elementHeight) {
		float w = elementWidth;
		float h = elementHeight;
		switch (anchor) {
			case TOP_LEFT, BOTTOM_LEFT, LEFT_CENTER -> offsetX = x;
			case TOP_RIGHT, BOTTOM_RIGHT, RIGHT_CENTER -> offsetX = screenWidth - x - w;
			case TOP_CENTER, BOTTOM_CENTER -> offsetX = x - (screenWidth - w) / 2f;
		}
		switch (anchor) {
			case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> offsetY = y;
			case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> offsetY = screenHeight - y - h;
			case LEFT_CENTER, RIGHT_CENTER -> offsetY = y - (screenHeight - h) / 2f;
		}
	}

	/**
	 * 요소가 지금 있는 자리를 기준으로 가장 가까운 모서리를 앵커로 다시 잡음(화면 좌표는 그대로).
	 * 이렇게 해두면 창 크기/GUI 배율이 바뀌어도 요소가 자기 모서리에 붙어 따라감.
	 */
	public void reanchor(int screenWidth, int screenHeight, int elementWidth, int elementHeight) {
		// elementWidth/Height = 화면에 보이는 크기(배율 적용 후). resolve는 배율 전 크기를 받으므로 나눠서 넘김.
		int x = resolveX(screenWidth, Math.round(elementWidth / Math.max(0.01f, scale)));
		int y = resolveY(screenHeight, Math.round(elementHeight / Math.max(0.01f, scale)));
		float w = elementWidth;
		float h = elementHeight;
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		boolean top = cy < screenHeight / 2f;
		boolean left = cx < screenWidth / 2f;
		// 가로 중앙 1/3 구간이면 CENTER 앵커(위=보스바 아래 배치용, 아래=핫바 주변 배치용)
		boolean centerBand = cx > screenWidth / 3f && cx < screenWidth * 2f / 3f;
		// 49-22차: 세로 중앙 1/3 구간의 좌/우 가장자리면 LEFT/RIGHT_CENTER(효과 HUD 등)
		boolean middleBand = cy > screenHeight / 3f && cy < screenHeight * 2f / 3f;
		if (top && centerBand) {
			anchor = Anchor.TOP_CENTER;
		} else if (!top && centerBand) {
			anchor = Anchor.BOTTOM_CENTER;
		} else if (middleBand) {
			anchor = left ? Anchor.LEFT_CENTER : Anchor.RIGHT_CENTER;
		} else if (top) {
			anchor = left ? Anchor.TOP_LEFT : Anchor.TOP_RIGHT;
		} else {
			anchor = left ? Anchor.BOTTOM_LEFT : Anchor.BOTTOM_RIGHT;
		}
		moveTo(x, y, screenWidth, screenHeight, elementWidth, elementHeight);
	}

	public HudPosition copy() {
		return new HudPosition(anchor, offsetX, offsetY, scale);
	}

	/**
	 * 49-114·122차: 값이 같으면 같은 것으로 본다(anchor·offset·scale만; 편집기용 transient 영역은 제외).
	 * 이게 없으면 Setting.isDefault()가 참조 비교라 config에서 불러온 위치가 좌표가 같아도 "기본값 아님"이 돼
	 * 초기화(↺) 버튼이 기본값에서도 계속 뜬다.
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof HudPosition p)) {
			return false;
		}
		return anchor == p.anchor
				&& Float.compare(offsetX, p.offsetX) == 0
				&& Float.compare(offsetY, p.offsetY) == 0
				&& Float.compare(scale, p.scale) == 0;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(anchor, offsetX, offsetY, scale);
	}
}
