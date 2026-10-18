package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.ChatState;

/**
 * 49-388차(사용자: "채팅 기능 안 켜도 채팅 무제한 기록 기능 만들어 줄래?"): 무제한 채팅 기록을 [채팅] 기능에서 따로 뺐다.
 * 켜 두면 채팅창 기록 100줄 제한이 30000줄로 풀린다(ChatHudMixin이 ChatState.unlimitedHistory를 본다). 기본 켜짐.
 */
public class UnlimitedChatModule extends Module {

	public UnlimitedChatModule() {
		super("unlimited_chat", "무제한 채팅", ModuleCategory.FEATURE, "채팅 기록 100줄 제한을 풀어 30000줄까지");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		ChatState.unlimitedHistory = true;
	}

	@Override
	protected void onDisable() {
		ChatState.unlimitedHistory = false;
	}

	@Override
	public void onTick() {
		ChatState.unlimitedHistory = true;   // 설정을 불러와 켜진 채로 시작해도 맞게
	}
}
