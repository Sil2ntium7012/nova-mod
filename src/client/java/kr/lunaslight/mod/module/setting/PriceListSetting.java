package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.List;

/**
 * 49-110차(사용자: "작물 계산기 가격 등록을 설정 페이지에 키바인드처럼 - 작물/광물 리스트에서 고르고 가격 설정, 삭제도"):
 * 가격표 등록을 화면(LunaClientScreen)이 직접 그리는 컨트롤러 설정. 값은 저장 안 함(가격은 기존 HarvestTracker의
 * 가격표 문자열에 그대로 저장 - 하위호환). 이 설정은 그 표를 {@link Backing}으로 들여다보고 추가/가격변경/삭제/표전환을 시킨다.
 *
 * <p>화면에서: 머리줄 [추가하기] → 작물/광물 아이템 목록(피커)에서 고름 → 아래 박스에 [아이콘·이름·가격칸·×] 한 줄씩.
 */
public class PriceListSetting extends Setting<Boolean> {

	/** 실제 가격표(HarvestTrackerModule)를 쥔 쪽이 구현한다. */
	public interface Backing {
		/** 표 이름들(작물·광물 …). */
		List<String> tableNames();

		String activeTable();

		void setActiveTable(String name);

		/** 지금 표의 항목(아이템id → 개당 가격, 등록 순서). */
		java.util.LinkedHashMap<String, Double> entries();

		void setPrice(String id, double price);

		void remove(String id);

		/** 고를 수 있는 작물·광물 아이템 id 목록(피커에 뜬다). */
		List<String> pickList();
	}

	private final Backing backing;
	private final String buttonLabel;

	public PriceListSetting(String id, String displayName, String description, String buttonLabel, Backing backing) {
		super(id, displayName, description, Boolean.FALSE);
		this.buttonLabel = buttonLabel;
		this.backing = backing;
	}

	public String getButtonLabel() {
		return buttonLabel;
	}

	public List<String> tableNames() {
		return backing.tableNames();
	}

	public String activeTable() {
		return backing.activeTable();
	}

	public void setActiveTable(String name) {
		backing.setActiveTable(name);
	}

	public java.util.LinkedHashMap<String, Double> entries() {
		return backing.entries();
	}

	public void setPrice(String id, double price) {
		backing.setPrice(id, price);
	}

	public void remove(String id) {
		backing.remove(id);
	}

	public List<String> pickList() {
		return backing.pickList();
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement element) {
	}
}
