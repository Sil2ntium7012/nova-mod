package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.ChatState;

/**
 * 49-53차(1-1): 채팅 숨기기 - 보고 싶지 않은 줄을 채팅에 아예 안 넣는다.
 *
 * <p>발전 과제 달성 메시지 판별은 <b>번역 키</b>({@code chat.type.advancement.*})로 한다 - 보이는
 * 글자로 맞추면 언어를 바꾸는 순간 안 먹는다. 내 발전 과제도 같이 숨는다(같은 키를 쓴다) -
 * 토스트는 그대로 뜬다.
 *
 * <p>49-54차(1-5): [단어] 추가. 이쪽 대상(서버 광고·홍보 도배)은 번역 키가 없어서 보이는 글자로
 * 맞출 수밖에 없다. 대소문자는 가리지 않고, 설정이 바뀔 때만 잘라서 {@link ChatState#blockedWords}에
 * 넣어 둔다 - 메시지마다 설정 문자열을 쪼개지 않게.
 */
public class ChatFilterModule extends Module {

	private final BooleanSetting advancements = register(new BooleanSetting(
			"advancements", "발전 과제 메시지", "누가 발전 과제를 달성했다는 채팅을 숨깁니다.", true));

	private final StringSetting words = register(new StringSetting(
			"words", "단어", "이 단어가 들어간 채팅을 숨깁니다. 대소문자는 가리지 않습니다.", "").list());

	public ChatFilterModule() {
		super("chat_filter", "채팅 숨김", ModuleCategory.FEATURE, "원치 않는 채팅 줄 숨김");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);   // 49-56차: 기능이 아니라 [일반] 설정
		words.onChange(this::apply);
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	public void onTick() {
		apply();
	}

	@Override
	protected void onDisable() {
		ChatState.hideAdvancements = false;
		ChatState.blockedWords = null;
	}

	private void apply() {
		boolean on = isEnabled();
		ChatState.hideAdvancements = on && advancements.get();
		ChatState.blockedWords = on ? ChatState.parseWords(words.get()) : null;
	}
}
