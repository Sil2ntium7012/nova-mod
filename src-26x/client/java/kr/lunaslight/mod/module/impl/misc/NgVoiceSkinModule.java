package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.NovaVoiceSkin;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;

/** 49-473차: <b>보이스 화면 노바 스타일</b> - 너굴마을 보이스(V) 화면을 노바 설정 화면 모양으로({@link NovaVoiceSkin}). 기본 켜짐. */
public class NgVoiceSkinModule extends Module {

	public NgVoiceSkinModule() {
		super("ng_voice_skin", "보이스 화면 노바 스타일", ModuleCategory.SERVER, "V 보이스 설정 화면을 노바 모양으로");
		serverGroup("너굴마을");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		NovaVoiceSkin.on = true;
	}

	@Override
	protected void onDisable() {
		NovaVoiceSkin.on = false;
	}

	@Override
	public void onTick() {
		NovaVoiceSkin.on = isEnabled();
	}
}
