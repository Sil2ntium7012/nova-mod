package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.ChatFaces;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-81차(4-48 "채팅 닉네임 앞에 얼굴"): <b>채팅 얼굴</b> - 플레이어 채팅 줄 앞에 그 사람 스킨 얼굴(8×8).
 * 어떻게 하는지는 {@link ChatFaces} 주석에 다 있다(줄 위치를 다시 계산하지 않고 메시지에 자리를 심는 방식).
 *
 * <p>설정은 없다 - "얼굴을 붙인다"가 기능의 전부다. 켜고 끄기만.
 *
 * <p><b>1.16+.</b> 1.15.2만 잠긴다(채팅 줄이 String이라 꼬리표를 붙일 Style이 없고 withFont도 없다).
 * 49-82차: 1.16~1.19.4는 shim DrawContext에 drawTexture를 넣고 띄우개를 비트맵 글꼴로 바꿔 지원.
 * 서버가 채팅을 통째로 꾸며서 이름을 못 찾는 줄에는 얼굴이 안 붙는다(지어내지 않는다).
 * 49-208차: 줄 맨 앞 하나가 아니라 메시지 안 플레이어 이름마다 그 바로 앞(괄호에 싸였으면 괄호 앞)에 붙는다.
 */
public class ChatFaceModule extends Module {

	public ChatFaceModule() {
		super("chat_face", "채팅 얼굴", ModuleCategory.FEATURE, "채팅 속 플레이어 이름 앞 얼굴");
		supportedVersions("1.16", null);
		defaultEnabled(true);   // 49-121차(사용자): 기본 활성화
	}

	@Override
	protected void onEnable() {
		ChatFaces.enabled = isVersionSupported();
	}

	@Override
	protected void onDisable() {
		ChatFaces.enabled = false;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!isPreview() || client == null) {
			return;
		}
		String me = LunaCompat.sessionName(client);
		String line = "<" + me + "> 안녕하세요";
		int w = ChatFaces.ADVANCE + LunaCompat.getTextWidth(client.font, line);
		ChatFaces.drawSample(context, client, previewCenterX() - w / 2, previewCenterY() - 4, line);
	}
}
