package kr.lunaslight.mod.network;

import kr.lunaslight.mod.LunaClientMod;
import net.minecraft.world.phys.Vec3;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 20차: "같은 서버에 있는, 같은 모드를 쓰는 다른 클라한테 핑 마크를 보여주는" 기능.
 *
 * 중요한 전제: 클라이언트끼리는 절대 직접 통신 못 하고 반드시 "서버"를 거쳐야 함(마인크래프트
 * 네트워크 구조 자체가 클라-서버 구조라 P2P가 없음). 남이 운영하는 서버/렌탈 서버는 이 모드를
 * 모르니 패킷을 그냥 무시함 - 그래서 이 릴레이는 사실상 "내가 직접 서버를 겸하는 경우"에서만
 * 동작함: 싱글플레이 + "LAN에 공개"로 켠 경우, 이 게임 자체가 동시에 서버 역할도 하고 있어서
 * (MinecraftClient#getServer()가 그 통합 서버를 가리킴) 우리 모드 코드가 그 서버 쪽 릴레이
 * 로직까지 실행할 수 있음. 그 외의 경우(진짜 남의 서버에 접속)는 서버가 이 패킷을 릴레이 안
 * 해주므로 항상 조용히 실패하고 "내 화면에 로컬로 찍기"만 계속 동작함(퇴화 없음).
 *
 * CustomPayload/PacketCodec/PayloadTypeRegistry API가 1.20.1/1.20.4엔 아예 없고(패키지 자체가
 * 없음), 1.21.x 사이에서도 세부 시그니처가 안 바뀌었다는 보장이 없어서 이 클래스는 전부
 * 리플렉션으로 짜여 있음 - 실패하면 예외 없이 조용히 "네트워킹 비활성"으로 폴백하고,
 * PingMarkModule의 로컬 표시 기능에는 전혀 영향을 안 줌.
 */
public final class PingPayload {
	private PingPayload() {
	}

	/** 네트워크로 도착한 핑을 받았을 때 호출됨. PingMarkModule이 등록해서 화면에 그리도록 함. */
	public interface ReceiveListener {
		void onReceive(double x, double y, double z, int color, String name);
	}

	private static volatile boolean available = false;
	private static volatile ReceiveListener listener;
	private static Object cachedId;
	private static Class<?> customPayloadClass;

	/** 서버/클라이언트 양쪽에서 이 페이로드 타입을 등록. 실패하면(구버전 등) 조용히 비활성. */
	public static synchronized void registerType(ReceiveListener receiveListener) {
		listener = receiveListener;
		// 49-156차: 한 번 등록했으면 다시 등록하지 않는다(같은 페이로드를 두 번 등록하면 예외가 나서, 기능을 껐다 켜거나
		// 게임 시작 때 다시 준비할 때 오히려 "네트워킹 비활성"으로 바뀌었다). 받는 쪽만 새로 끼운다.
		if (available) {
			return;
		}
		try {
			setup();
			available = true;
		} catch (Throwable t) {
			available = false;
			LunaClientMod.LOGGER.info("[Nova] 핑 마커 서버 릴레이 네트워킹 비활성(이 버전에서 API를 못 찾음) - 로컬 표시만 동작합니다.");
		}
	}

	/** 로컬에 핑을 찍은 뒤, 가능하면 서버로도 보내봄(랜 호스트가 아니면 그냥 조용히 무시됨). */
	public static void sendIfPossible(Vec3 pos, int color, String name) {
		if (!available) {
			return;
		}
		try {
			Object payload = newPayloadProxy(new PingData(pos.x, pos.y, pos.z, color, name == null ? "" : name));
			Class<?> clientNetworkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
			for (Method m : clientNetworkingClass.getMethods()) {
				if (m.getName().equals("send") && m.getParameterCount() == 1
						&& m.getParameterTypes()[0].isInstance(payload)) {
					m.invoke(null, payload);
					return;
				}
			}
		} catch (Throwable ignored) {
			// 서버가 이 채널을 모르거나(일반 서버), 네트워킹 셋업이 이 버전에서 안 맞았던 것 -
			// 로컬 핑 표시는 이미 끝났으므로 조용히 무시.
		}
	}

	// ==================== 내부 구현(전부 리플렉션) ====================

	private record PingData(double x, double y, double z, int color, String name) {
	}

	private static final class PayloadHandler implements InvocationHandler {
		final PingData data;

		PayloadHandler(PingData data) {
			this.data = data;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			// CustomPayload 인터페이스의 유일한 추상 메서드(getId()/type() 등 버전마다 이름이 달라도
			// 인자가 없고 이 페이로드의 식별자를 리턴하면 됨)만 구현하면 되므로 이름 상관없이 처리.
			if (method.getParameterCount() == 0) {
				return cachedId;
			}
			// equals/hashCode/toString 등 Object 메서드가 넘어올 가능성 대비.
			if (method.getName().equals("toString")) {
				return "PingPayload" + data;
			}
			if (method.getName().equals("hashCode")) {
				return System.identityHashCode(proxy);
			}
			if (method.getName().equals("equals")) {
				return proxy == (args != null && args.length > 0 ? args[0] : null);
			}
			return null;
		}
	}

	private static Object newPayloadProxy(PingData data) {
		return Proxy.newProxyInstance(customPayloadClass.getClassLoader(),
				new Class<?>[]{customPayloadClass}, new PayloadHandler(data));
	}

	private static void setup() throws Exception {
		customPayloadClass = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
		Class<?> idClass = Class.forName("net.minecraft.network.packet.CustomPayload$Id");
		Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
		Class<?> codecClass = Class.forName("net.minecraft.network.codec.StreamCodec");
		Class<?> payloadTypeRegistryClass = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
		Class<?> clientNetworkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
		Class<?> serverNetworkingClass = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");

		Object identifier = identifierClass.getMethod("of", String.class, String.class)
				.invoke(null, "lunaslight", "ping");

		// CustomPayload.Id 인스턴스 생성 - 버전마다 static factory("id"/"of") 또는 생성자일 수 있어
		// 여러 후보를 순서대로 시도.
		Object id = null;
		for (Method m : idClass.getMethods()) {
			if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
					&& m.getParameterTypes()[0].isAssignableFrom(identifierClass)) {
				try {
					id = m.invoke(null, identifier);
					break;
				} catch (Exception ignored) {
				}
			}
		}
		if (id == null) {
			id = idClass.getConstructor(identifierClass).newInstance(identifier);
		}
		cachedId = id;

		// PacketCodec.of(encoder, decoder) - 파라미터 타입(인코더/디코더 함수형 인터페이스)을
		// 이름으로 안 찍고 실제 메서드 시그니처에서 그대로 가져와 Proxy로 구현.
		Method ofMethod = null;
		for (Method m : codecClass.getMethods()) {
			if (m.getName().equals("of") && m.getParameterCount() == 2
					&& java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
				ofMethod = m;
				break;
			}
		}
		if (ofMethod == null) {
			throw new NoSuchMethodException("PacketCodec.of(encoder, decoder)를 못 찾음");
		}
		Class<?> encoderIface = ofMethod.getParameterTypes()[0];
		Class<?> decoderIface = ofMethod.getParameterTypes()[1];

		Object encoder = Proxy.newProxyInstance(encoderIface.getClassLoader(), new Class<?>[]{encoderIface},
				(p, method, args) -> {
					if (method.getParameterCount() == 2) {
						Object buf = args[0];
						Object value = args[1];
						PingData data = ((PayloadHandler) Proxy.getInvocationHandler(value)).data;
						writeBytes(buf, serialize(data));
					}
					return null;
				});
		Object decoder = Proxy.newProxyInstance(decoderIface.getClassLoader(), new Class<?>[]{decoderIface},
				(p, method, args) -> {
					if (method.getParameterCount() == 1) {
						byte[] bytes = readBytes(args[0]);
						PingData data = deserialize(bytes);
						return newPayloadProxy(data);
					}
					return null;
				});
		Object codec = ofMethod.invoke(null, encoder, decoder);

		// C2S/S2C 양쪽 레지스트리에 등록 - 메서드 이름이 버전마다 playC2S/playS2C 혹은
		// serverboundPlay/clientboundPlay일 수 있어 후보를 순회.
		registerToDirection(payloadTypeRegistryClass, new String[]{"playC2S", "serverboundPlay"}, id, codec);
		registerToDirection(payloadTypeRegistryClass, new String[]{"playS2C", "clientboundPlay"}, id, codec);

		// 클라이언트: 남한테서 온 핑을 받으면 화면에 표시.
		Method clientRegister = findRegisterGlobalReceiver(clientNetworkingClass);
		Object clientHandler = Proxy.newProxyInstance(clientRegister.getParameterTypes()[1].getClassLoader(),
				new Class<?>[]{clientRegister.getParameterTypes()[1]},
				(p, method, args) -> {
					handleIncoming(args);
					return null;
				});
		clientRegister.invoke(null, id, clientHandler);

		// 서버(내가 랜 호스트일 때만 실제로 동작): 받은 핑을 나 빼고 전부한테 다시 뿌림.
		Method serverRegister = findRegisterGlobalReceiver(serverNetworkingClass);
		Object serverHandler = Proxy.newProxyInstance(serverRegister.getParameterTypes()[1].getClassLoader(),
				new Class<?>[]{serverRegister.getParameterTypes()[1]},
				(p, method, args) -> {
					relayToOtherPlayers(serverNetworkingClass, args);
					return null;
				});
		serverRegister.invoke(null, id, serverHandler);
	}

	private static void registerToDirection(Class<?> registryClass, String[] candidateNames, Object id, Object codec) throws Exception {
		for (String name : candidateNames) {
			try {
				Method dirMethod = registryClass.getMethod(name);
				Object registry = dirMethod.invoke(null);
				for (Method m : registry.getClass().getMethods()) {
					if (m.getName().equals("register") && m.getParameterCount() == 2) {
						m.invoke(registry, id, codec);
						return;
					}
				}
			} catch (NoSuchMethodException ignored) {
				// 다음 후보 이름 시도.
			}
		}
		throw new NoSuchMethodException("PayloadTypeRegistry 방향 메서드를 못 찾음: " + String.join("/", candidateNames));
	}

	private static Method findRegisterGlobalReceiver(Class<?> networkingClass) throws NoSuchMethodException {
		for (Method m : networkingClass.getMethods()) {
			if (m.getName().equals("registerGlobalReceiver") && m.getParameterCount() == 2) {
				return m;
			}
		}
		throw new NoSuchMethodException("registerGlobalReceiver를 못 찾음: " + networkingClass);
	}

	/** 클라이언트에서 수신 핸들러 호출 시 args 중 우리 페이로드 프록시를 찾아 listener로 전달. */
	private static void handleIncoming(Object[] args) {
		if (args == null) return;
		for (Object arg : args) {
			if (arg != null && Proxy.isProxyClass(arg.getClass())
					&& Proxy.getInvocationHandler(arg) instanceof PayloadHandler handler) {
				PingData d = handler.data;
				ReceiveListener l = listener;
				if (l != null) {
					l.onReceive(d.x, d.y, d.z, d.color, d.name);
				}
				return;
			}
		}
	}

	/** 서버에서 수신 핸들러 호출 시: 보낸 사람을 빼고 현재 통합 서버(랜 호스트)에 붙어있는 전원에게 재전송. */
	private static void relayToOtherPlayers(Class<?> serverNetworkingClass, Object[] args) {
		try {
			if (args == null) return;
			Object payload = null;
			Object context = null;
			for (Object arg : args) {
				if (arg != null && Proxy.isProxyClass(arg.getClass()) && Proxy.getInvocationHandler(arg) instanceof PayloadHandler) {
					payload = arg;
				} else if (arg != null) {
					context = arg;
				}
			}
			if (payload == null) return;

			Object sender = null;
			if (context != null) {
				try {
					sender = context.getClass().getMethod("player").invoke(context);
				} catch (Exception ignored) {
				}
			}

			Object server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
			if (server == null) return;
			Boolean dedicated = (Boolean) tryInvokeNoArg(server, "isDedicated");
			if (Boolean.TRUE.equals(dedicated)) {
				// 안전장치: 혹시라도 진짜 데디케이트 서버로 이 모드가 실행되는 상황이면 릴레이 안 함
				// (이 기능은 "내가 곧 서버인 랜 호스트" 상황 전용).
				return;
			}

			Object playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
			Object playerList = playerManager.getClass().getMethod("getPlayerList").invoke(playerManager);

			Method sendMethod = null;
			for (Method m : serverNetworkingClass.getMethods()) {
				if (m.getName().equals("send") && m.getParameterCount() == 2) {
					sendMethod = m;
					break;
				}
			}
			if (sendMethod == null) return;

			for (Object player : (Iterable<?>) playerList) {
				if (player == sender) continue;
				sendMethod.invoke(null, player, payload);
			}
		} catch (Throwable ignored) {
			// 릴레이 실패해도 서버/게임이 죽으면 안 됨 - 조용히 무시.
		}
	}

	private static Object tryInvokeNoArg(Object target, String methodName) {
		try {
			return target.getClass().getMethod(methodName).invoke(target);
		} catch (Throwable t) {
			return null;
		}
	}

	private static void writeBytes(Object buf, byte[] bytes) throws Exception {
		for (Method m : buf.getClass().getMethods()) {
			if (m.getName().equals("writeByteArray") && m.getParameterCount() == 1) {
				m.invoke(buf, (Object) bytes);
				return;
			}
		}
		throw new NoSuchMethodException("writeByteArray를 못 찾음");
	}

	private static byte[] readBytes(Object buf) throws Exception {
		for (Method m : buf.getClass().getMethods()) {
			if (m.getName().equals("readByteArray") && m.getParameterCount() == 0) {
				return (byte[]) m.invoke(buf);
			}
		}
		throw new NoSuchMethodException("readByteArray를 못 찾음");
	}

	private static byte[] serialize(PingData data) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bos)) {
			out.writeDouble(data.x());
			out.writeDouble(data.y());
			out.writeDouble(data.z());
			out.writeInt(data.color());
			out.writeUTF(data.name() == null ? "" : data.name());
		}
		return bos.toByteArray();
	}

	private static PingData deserialize(byte[] bytes) throws Exception {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
			double x = in.readDouble();
			double y = in.readDouble();
			double z = in.readDouble();
			int color = in.readInt();
			String name = in.readUTF();
			return new PingData(x, y, z, color, name);
		}
	}
}
