package kr.lunaslight.mod.module;

/**
 * 설정 화면(LunaClientScreen)의 탭 구분용 카테고리. 순서가 곧 탭이 나열되는 순서입니다.
 * (저장 파일은 모듈 id 기준이라 카테고리를 옮겨도 설정은 유지됨.)
 *
 * <p><b>49-53차(4-47): 아홉 개 → 여섯 개.</b> 사용자 요청으로 렌더링·탐색·채팅·화면 넷을 없앴다.
 * "렌더링"은 이름만으로 무엇이 들었는지 짐작이 안 되는 자루였고(색보정·이름표·TNT·자막이 한 칸에),
 * 탐색·채팅·화면은 각각 두세 개짜리라 탭을 하나 차지할 값이 없었다. 남은 여섯은 <b>"무엇을 만지는
 * 기능인가"</b>로만 갈린다:
 *
 * <ul>
 *   <li>{@code HUD} - 화면에 정보를 띄우는 것(좌표·시계·체력·서버 주소·FPS·핑·갑옷·아이템 획득·듣고 있는 노래…)</li>
 *   <li>(49-185차) {@code PERFORMANCE}(성능)은 없앴다 - 49-179차에 수치 HUD를 HUD로 옮긴 뒤 남은 셋(가려진 것 안 그리기,
 *       엔티티 줄이기, 자리 비움)이 전부 항상 켜짐이거나 [그래픽] 설정 페이지 항목이라 탭이 비었다(사용자: "성능에 0개인데
 *       그냥 없애자"). 그 셋은 FEATURE로(목록에는 안 나온다).</li>
 *   <li>{@code VIEW} - 보이는 것 자체를 바꾸는 것(줌·시점·밝기·크로스헤어·인챈트 반짝임·날씨·시간·이름표·자막).
 *       49-179차: 버려진 아이템 정보와 빛기둥은 아이템이라 인벤토리로.</li>
 *   <li>{@code COMBAT} - 싸울 때 쓰는 것(콤보·크리티컬·방패)</li>
 *   <li>{@code INVENTORY} - 아이템·가방을 만지는 것</li>
 *   <li>{@code FEATURE} - 나머지 전부</li>
 *   <li>{@code SERVER} - 특정 서버 전용(너굴마을 등), 서버별로 묶인다</li>
 * </ul>
 *
 * <p>설정 화면에는 이 여섯 앞에 <b>[전체]</b> 탭이 하나 더 붙는다(카테고리 값이 아니라 화면 쪽에서
 * {@code null}로 다루는 가상 탭 - LunaClientScreen.TAB_ALL 참고).
 */
public enum ModuleCategory {
	HUD("HUD"),
	VIEW("시점"),
	COMBAT("전투"),
	INVENTORY("인벤토리"),
	FEATURE("기능"),
	/** 49-138차(사용자: "너굴마을 같은 서버 전용 기능은 따로 서버 기능 분류하고 거기서 또 서버별로"): 특정 서버에서만 쓰는 기능. {@link Module#serverGroup()}로 서버별로 나뉜다. */
	SERVER("서버");

	private final String displayName;

	ModuleCategory(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
