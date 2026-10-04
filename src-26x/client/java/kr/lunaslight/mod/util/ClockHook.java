package kr.lunaslight.mod.util;

/**
 * 26.x 전용: 시간 고정(ClientTimeModule)이 걸어 두는 하루 시간 덮어쓰기.
 * 26.1부터 하늘 시간이 World#setTimeOfDay가 아니라 시계 레지스트리(ClientClockManager, WorldClocks.OVERWORLD)로
 * 바뀌어서, ClockTimeMixin이 ClientClockManager#getTotalTicks(overworld 시계)의 반환값을 이 값으로 바꾼다.
 * -1이면 덮어쓰지 않음.
 */
public final class ClockHook {
	private ClockHook() {
	}

	public static volatile long override = -1;

	/** 49-215차: 26.3 - 오버월드 시계 인스턴스(ClockInstanceTrackMixin이 채움, ClockInstanceTimeMixin이 비교). */
	public static volatile Object overworldInstance;
}
