package kr.lunaslight.mod.module;

/**
 * 49-194차: 기능이 <b>꺼져 있어도</b> 매 틱 돌아야 하는 일이 있는 기능. 켜져 있으면 onTick만 불리고, 꺼져 있으면
 * ModuleManager가 이걸 부른다. 예: 아이템 찾기가 꺼져 있어도 블록 정보(상자 내용물)나 인벤토리 탭(미리보기)이
 * 켜져 있으면 연 상자의 내용물을 기록한다.
 */
public interface BackgroundTick {
	void backgroundTick();
}
