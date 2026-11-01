package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.ServerPackRescue;

/** 49-477차: <b>서버 팩 다시 받기</b> - 서버에 들어갔는데 리소스팩이 안 오면 지난번 팩을 다시 받는다({@link ServerPackRescue}). 기본 켜짐. */
public class ServerPackRescueModule extends Module {

	private final IntSetting wait = register(new IntSetting(
			"wait", "기다릴 시간(초)", "서버에 들어가고 이 시간 동안 서버 리소스팩이 안 켜지면 다시 받습니다.", 15, 5, 60, 1));

	public ServerPackRescueModule() {
		super("server_pack_rescue", "서버 팩 다시 받기", ModuleCategory.FEATURE, "서버 리소스팩이 안 오면 지난번 팩을 다시 받기");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		ServerPackRescue.on = true;
	}

	@Override
	protected void onDisable() {
		ServerPackRescue.on = false;
	}

	@Override
	public void onTick() {
		ServerPackRescue.on = isEnabled();
		ServerPackRescue.waitSeconds = wait.get();
		ServerPackRescue.tick(client);
	}
}
