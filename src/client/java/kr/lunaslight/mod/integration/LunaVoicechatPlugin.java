package kr.lunaslight.mod.integration;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophoneMuteEvent;
import de.maxhenkel.voicechat.api.events.VoicechatDisableEvent;
import kr.lunaslight.mod.util.VoiceState;

import java.util.UUID;

/**
 * 49-50차: Simple Voice Chat 애드온 본체. <b>이 클래스만</b> {@code de.maxhenkel.voicechat.api}를 만진다.
 *
 * <p>등록 경로는 SVC 공식 문서 그대로다 - Fabric에서는 {@code fabric.mod.json}의
 * {@code "entrypoints" > "voicechat"}에 이 클래스 이름을 적어두면 SVC가 직접 읽어서 인스턴스를 만든다.
 * 즉 <b>SVC가 없으면 이 클래스는 로드조차 되지 않는다</b> - 40개 버전 어디에 넣어도 SVC 없는 환경에서
 * 클래스 못 찾음 오류가 날 수 없는 구조다. 우리 코드 어디에서도 이 클래스를 직접 참조하지 않는다.
 *
 * <p>API jar는 마인크래프트 클래스를 전혀 참조하지 않는 순수 인터페이스 묶음(자바 8 바이트코드)이라
 * 버전별로 따로 받을 필요가 없다 - {@code libs/voicechat-api-*.jar} 한 벌을 40개 서브프로젝트가
 * compileOnly로 공유한다(jar에는 안 들어간다 - SVC가 런타임에 자기 걸 준다).
 *
 * <p>하는 일은 상태를 {@link VoiceState}에 옮겨 적는 것뿐이다. 오디오를 건드리거나 취소하지 않는다
 * (이벤트를 취소하면 그 사람 소리가 안 들린다 - 우리는 읽기만 한다).
 */
public class LunaVoicechatPlugin implements VoicechatPlugin {

	/** SVC 로그/충돌 진단에 뜨는 이름. */
	@Override
	public String getPluginId() {
		return "lunaslight";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		// 연결/해제 - HUD를 그릴지 말지의 기준
		registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
			bind(event.getVoicechat());
			VoiceState.setConnected(event.isConnected());
		});
		// 음소거/음성채팅 끄기 - 값 자체는 API에서 그때그때 읽으므로 여기선 다리만 걸어 둔다
		registration.registerEvent(MicrophoneMuteEvent.class, event -> bind(event.getVoicechat()));
		registration.registerEvent(VoicechatDisableEvent.class, event -> bind(event.getVoicechat()));

		// 음량 막대용 - 수신 패킷 세기. 세 갈래(엔티티/위치/고정)가 따로 발행돼서 전부 건다.
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, event -> {
			bind(event.getVoicechat());
			VoiceState.markAudio(event.getEntityId(), event.getRawAudio());
		});
		registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, event -> {
			bind(event.getVoicechat());
			VoiceState.markAudio(event.getId(), event.getRawAudio());
		});
		registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, event -> {
			bind(event.getVoicechat());
			VoiceState.markAudio(event.getId(), event.getRawAudio());
		});
	}

	/** 이미 걸어 둔 API면 아무것도 하지 않는다 - 수신 패킷마다(초당 20회) 객체를 새로 만들지 않도록. */
	private static volatile VoicechatClientApi bound;

	private static void bind(VoicechatClientApi api) {
		if (api == null || api == bound) {
			return;
		}
		bound = api;
		VoiceState.setBridge(new VoiceState.Bridge() {
			@Override
			public boolean muted() {
				return api.isMuted();
			}

			@Override
			public boolean disabled() {
				return api.isDisabled();
			}

			@Override
			public boolean talking(UUID id) {
				return api.isTalking(id);
			}

			@Override
			public boolean whispering(UUID id) {
				return api.isWhispering(id);
			}

			@Override
			public String groupName() {
				Group group = api.getGroup();
				return group == null ? null : group.getName();
			}
		});
	}
}
