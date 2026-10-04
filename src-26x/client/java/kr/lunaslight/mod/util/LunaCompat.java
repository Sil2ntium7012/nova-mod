package kr.lunaslight.mod.util;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 19차: Nova-Mod는 1.20.1/1.20.4/1.21.1/1.21.4/1.21.11 5개 버전을 하나의 공유 소스 트리로
 * 빌드합니다(Stonecutter 없이 순수 Gradle 멀티 프로젝트 + sourceSet 재할당). 문제는 이 5개
 * 버전 사이에서 바닐라/Fabric API 일부의 "존재 여부"와 "정확한 시그니처"가 다르다는 점인데,
 * 공유 소스에서 그 타입을 컴파일 타임에 직접 참조하면 그게 다른 버전 하나에서 컴파일이 깨집니다.
 *
 * 이 클래스는 그런 API들을 전부 리플렉션으로 모아, 각 모듈이 버전 분기 코드를 직접 갖지 않고도
 * 5개 버전 전부에서 컴파일되게 합니다. 원칙: 리플렉션이 실패하면(그 버전에 해당 API가 없으면)
 * 예외를 던지지 않고 조용히 폴백하거나 기능을 비활성화합니다 - 컴파일은 항상 되어야 하고,
 * 런타임에 게임을 죽여서는 안 됩니다.
 */
public final class LunaCompat {
	private LunaCompat() {
	}

	// ==================== 위치(Vec3d) 접근 ====================
	// Entity/Camera#getPos()가 1.21.9+에서 이름이 바뀌었을 가능성이 있어(렌더링 구조 대개편),
	// 알려진 후보 이름들을 순서대로 시도.
	public static Vec3 getPos(Object target) {
		if (target == null) {
			return null;
		}
		for (String name : new String[]{"getPos", "getEntityPos", "getCameraPos"}) {
			Object r = callNoArg(target, name);
			if (r instanceof Vec3 vec) {
				return vec;
			}
		}
		return null;
	}

	// ==================== InputUtil.isKeyPressed ====================
	// 구버전: isKeyPressed(long windowHandle, int code) / 1.21.9+: isKeyPressed(Window window, int code)
	private static Method isKeyPressedMethod;
	private static boolean isKeyPressedTakesHandle;
	private static boolean isKeyPressedResolved;

	// ==================== 49-24차: 조합키(Ctrl/Shift/Alt + 키) ====================
	// 키 코드 위쪽 비트에 필요한 보조키를 실어 하나의 int로 저장한다(GLFW 키 ≤ 348, 마우스 인코딩 ≤ 1015라
	// 아래 20비트면 충분). 보조키가 없는 지정은 예전처럼 보조키 상태를 무시한다(호환).
	public static final int KEY_MASK = 0xFFFFF;
	public static final int MOD_SHIFT = 1 << 20;
	public static final int MOD_CTRL = 1 << 21;
	public static final int MOD_ALT = 1 << 22;
	public static final int MOD_MASK = MOD_SHIFT | MOD_CTRL | MOD_ALT;

	/** 보조키 비트를 뗀 실제 키 코드(-1은 그대로). */
	public static int baseKey(int keyCode) {
		return keyCode < 0 ? keyCode : keyCode & KEY_MASK;
	}

	public static int keyModifiers(int keyCode) {
		return keyCode < 0 ? 0 : keyCode & MOD_MASK;
	}

	/** GLFW 키가 보조키(Shift/Ctrl/Alt 좌우)인지. */
	public static boolean isModifierKey(int glfwKey) {
		return glfwKey == com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT || glfwKey == com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT || glfwKey == com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL || glfwKey == com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL
				|| glfwKey == com.mojang.blaze3d.platform.InputConstants.KEY_LALT || glfwKey == com.mojang.blaze3d.platform.InputConstants.KEY_RALT;
	}

	/** 보조키 GLFW 코드 → MOD_* 비트(보조키가 아니면 0). */
	public static int modifierBitOf(int glfwKey) {
		return switch (glfwKey) {
			case com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT -> MOD_SHIFT;
			case com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL -> MOD_CTRL;
			case com.mojang.blaze3d.platform.InputConstants.KEY_LALT, com.mojang.blaze3d.platform.InputConstants.KEY_RALT -> MOD_ALT;
			default -> 0;
		};
	}

	/** 지금 눌린 보조키들의 MOD_* 비트 합. */
	public static int heldModifiers(Minecraft client) {
		int m = 0;
		if (isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) || isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)) {
			m |= MOD_SHIFT;
		}
		if (isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL) || isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL)) {
			m |= MOD_CTRL;
		}
		if (isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_LALT) || isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_RALT)) {
			m |= MOD_ALT;
		}
		return m;
	}

	public static boolean isKeyPressed(Minecraft client, int keyCode) {
		if (client == null || client.getWindow() == null || keyCode < 0) {
			return false;
		}
		// 49-24차: 조합키 - 보조키 비트가 있으면 그 보조키(좌/우 아무거나)가 전부 눌려 있어야 함
		int mods = keyCode & MOD_MASK;
		if (mods != 0) {
			keyCode &= KEY_MASK;
			if ((mods & MOD_SHIFT) != 0 && !(isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) || isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT))) {
				return false;
			}
			if ((mods & MOD_CTRL) != 0 && !(isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL) || isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL))) {
				return false;
			}
			if ((mods & MOD_ALT) != 0 && !(isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_LALT) || isKeyPressed(client, com.mojang.blaze3d.platform.InputConstants.KEY_RALT))) {
				return false;
			}
		}
		// 49-22차: 마우스 옆버튼(키 지정에서 -(1000+버튼)으로 인코딩) - GLFW로 직접 읽음.
		if (keyCode >= MOUSE_KEY_BASE) {
			return isMouseButtonPressed(client, keyCode - MOUSE_KEY_BASE);
		}
		try {
			if (!isKeyPressedResolved) {
				isKeyPressedResolved = true;
				Class<?> inputUtilClass = classForName("com.mojang.blaze3d.platform.InputConstants");
				for (Method m : inputUtilClass.getMethods()) {
					// 49-215차: 26.3은 창 인자가 빠진 isKeyDown(int) 하나뿐(SDL)
					if (m.getParameterCount() < 1 || m.getParameterCount() > 2
							|| !nameMatches(inputUtilClass, "isKeyDown", m.getName())) {
						continue;
					}
					isKeyPressedMethod = m;
					isKeyPressedTakesHandle = m.getParameterCount() == 2 && m.getParameterTypes()[0] == long.class;
					break;
				}
			}
			if (isKeyPressedMethod != null) {
				Object result = isKeyPressedMethod.getParameterCount() == 1
						? isKeyPressedMethod.invoke(null, keyCode)
						: isKeyPressedTakesHandle
						? isKeyPressedMethod.invoke(null, client.getWindow().handle(), keyCode)
						: isKeyPressedMethod.invoke(null, client.getWindow(), keyCode);
				return Boolean.TRUE.equals(result);
			}
		} catch (Throwable ignored) {
			// InputUtil 자체를 못 찾거나 시그니처가 완전히 다르면 "안 눌림"으로 안전하게 폴백.
		}
		return false;
	}

	// ==================== 49-22차: 마우스 버튼을 키 코드처럼 ====================
	// 키 지정(KeybindSetting)에 마우스 옆버튼(뒤로/앞으로 = GLFW 버튼 3/4)도 넣을 수 있게, 마우스 버튼 b를
	// 키 코드 MOUSE_KEY_BASE + b 로 인코딩한다(GLFW 키 코드는 최대 348이라 겹치지 않음).
	public static final int MOUSE_KEY_BASE = 1000;

	public static boolean isMouseKeyCode(int keyCode) {
		return keyCode >= MOUSE_KEY_BASE && keyCode < MOUSE_KEY_BASE + 16;
	}

	public static int mouseKeyCode(int button) {
		return MOUSE_KEY_BASE + button;
	}

	public static boolean isMouseButtonPressed(Minecraft client, int button) {
		return LunaInput.mouseDown(client, button);
	}

	/** 키 코드의 표시 이름(마우스 버튼 인코딩·조합키 포함: "Ctrl + R"). */
	public static String keyDisplayName(int keyCode) {
		if (keyCode < 0) {
			return "없음";
		}
		int mods = keyCode & MOD_MASK;
		if (mods != 0) {
			StringBuilder sb = new StringBuilder();
			if ((mods & MOD_CTRL) != 0) {
				sb.append("Ctrl + ");
			}
			if ((mods & MOD_SHIFT) != 0) {
				sb.append("Shift + ");
			}
			if ((mods & MOD_ALT) != 0) {
				sb.append("Alt + ");
			}
			return sb + keyDisplayName(keyCode & KEY_MASK);
		}
		if (isMouseKeyCode(keyCode)) {
			int b = keyCode - MOUSE_KEY_BASE;
			return switch (b) {
				case 0 -> "마우스 왼쪽";
				case 1 -> "마우스 오른쪽";
				case 2 -> "마우스 휠";
				case 3 -> "마우스 4";
				case 4 -> "마우스 5";
				default -> "마우스 " + (b + 1);
			};
		}
		try {
			Class<?> inputUtil = classForName("com.mojang.blaze3d.platform.InputConstants");
			Class<?> typeClass = classForName("com.mojang.blaze3d.platform.InputConstants$Type");
			Object key = LunaInput.keyboardKey(keyCode);   // 49-215차: 26.3은 KEYSYM → KEYBOARD
			String s = getKeyCodeDisplayName(key);
			if (s != null && !s.isEmpty() && !s.equals("?")) {
				return s;
			}
			if (inputUtil == null) {
				return "키 " + keyCode;
			}
		} catch (Throwable ignored) {
		}
		return "키 " + keyCode;
	}

	// ==================== GameOptions#getPerspective()/setPerspective ====================
	// 구버전: getPerspective()가 SimpleOption<Perspective> 반환(.getValue()/.setValue() 필요)
	// 신버전: getPerspective()가 Perspective를 직접 반환, setPerspective(Perspective) 세터 존재
	//
	// 35차: Perspective 자체를 컴파일 타임 타입으로 쓰지 않고 완전히 Object로 바꿈 - Perspective
	// 클래스가 사는 패키지가 1.17 미만에서는 net.minecraft.client.options(복수형), 1.17+부터는
	// net.minecraft.client.option(단수형)으로 갈려서(claude/nova-mod-todo.md 33차 조사), 어느 한쪽
	// 패키지를 코드에 직접 적으면 반대쪽 버전에서 컴파일이 깨짐. 완전 리플렉션/덕타이핑으로 흡수.
	public static Object getPerspective(Object gameOptions) {
		try {
			Object raw = getMethodCompat(gameOptions.getClass(), "getCameraType").invoke(gameOptions);
			if (raw == null) {
				return null;
			}
			try {
				// 구버전: SimpleOption<Perspective> - getValue() 필요.
				return getMethodCompat(raw.getClass(), "getValue").invoke(raw);
			} catch (NoSuchMethodException noValueMethod) {
				// 신버전: 이미 Perspective 그 자체.
				return raw;
			}
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static void setPerspective(Object gameOptions, Object perspective) {
		if (perspective == null) {
			return;
		}
		try {
			for (Method m : gameOptions.getClass().getMethods()) {
				if (nameMatches(gameOptions.getClass(), "setCameraType", m.getName()) && m.getParameterCount() == 1) {
					m.invoke(gameOptions, perspective);
					return;
				}
			}
			Object raw = getMethodCompat(gameOptions.getClass(), "getCameraType").invoke(gameOptions);
			for (Method m : raw.getClass().getMethods()) {
				if (nameMatches(raw.getClass(), "setValue", m.getName()) && m.getParameterCount() == 1) {
					m.invoke(raw, perspective);
					return;
				}
			}
		} catch (Throwable ignored) {
			// 시야 전환 실패해도 게임이 죽으면 안 되므로 조용히 무시.
		}
	}

	/** Perspective.THIRD_PERSON_BACK 상수를 리플렉션으로(option/options 패키지 둘 다 시도). */
	public static Object thirdPersonBackPerspective() {
		return perspectiveConstant("THIRD_PERSON_BACK");
	}

	/** 49-21차: Perspective.THIRD_PERSON_FRONT(앞에서 보는 2인칭 느낌의 시점). */
	public static Object thirdPersonFrontPerspective() {
		return perspectiveConstant("THIRD_PERSON_FRONT");
	}

	private static Object perspectiveConstant(String name) {
		Class<?> perspectiveClass = resolveClass(
				"net.minecraft.client.CameraType", "net.minecraft.client.options.Perspective");
		if (perspectiveClass == null || !perspectiveClass.isEnum()) {
			return null;
		}
		for (Object constant : perspectiveClass.getEnumConstants()) {
			if (enumNameIs(constant, name)) {
				return constant;
			}
		}
		return null;
	}

	/**
	 * 49-215차: 이름 후보 중 있는 클래스(없으면 ClassNotFoundException). 26.3에서 렌더 클래스가
	 * com.mojang.blaze3d.pipeline/buffers → com.mojang.renderpearl.api.pipeline/buffers로 옮겨갔다.
	 */
	public static Class<?> requireClass(String... candidateNames) throws ClassNotFoundException {
		Class<?> c = resolveClass(candidateNames);
		if (c == null) {
			throw new ClassNotFoundException(String.join(" | ", candidateNames));
		}
		return c;
	}

	/** 후보 클래스 이름들을 순서대로 시도해서 처음 발견되는 것을 반환(전부 실패하면 null). */
	public static Class<?> resolveClass(String... candidateNames) {
		for (String name : candidateNames) {
			// 46차: Yarn 이름 + intermediary 이름 둘 다 시도(프로덕션 대응 - 아래 "프로덕션 이름 변환" 참고)
			// 49-22차: classForName이 결과("없음" 포함)를 캐시하므로 매 호출 Class.forName 비용이 사라짐.
			Class<?> c = classOrNull(name);
			if (c != null) {
				return c;
			}
		}
		return null;
	}

	// ==================== Text.literal(35차) ====================
	// Text 인터페이스의 정적 팩토리 literal(String)은 신버전(1.19 근처 Text 개편) 방식이고,
	// 그 이전엔 new LiteralText(String) 생성자로 만들었음. Text.class 자체는 모든 버전에 있는
	// 안정적 타입이라 반환값은 Text로 유지(호출부가 리플렉션 없이 그대로 쓸 수 있게).
	public static Component textLiteral(String content) {
		try {
			Method m = getMethodCompat(Component.class, "literal", String.class);
			Object result = m.invoke(null, content);
			if (result instanceof Component text) {
				return text;
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> literalTextClass = classForName("net.minecraft.text.LiteralText");
			Object result = literalTextClass.getConstructor(String.class).newInstance(content);
			if (result instanceof Component text) {
				return text;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	// ==================== 49-21차: Identifier / 아이템 id → 아이템 ====================
	/** Identifier.of(ns, path)(1.21+) 또는 new Identifier(ns, path)(구버전). 실패 시 null. */
	public static Identifier identifier(String namespace, String path) {
		try {
			Method of = getMethodCompat(Identifier.class, "of", String.class, String.class);
			return (Identifier) of.invoke(null, namespace, path);
		} catch (Throwable ignored) {
		}
		try {
			return Identifier.class.getConstructor(String.class, String.class).newInstance(namespace, path);
		} catch (Throwable t) {
			warnOnce("identifier", t);
			return null;
		}
	}

	/** "minecraft:stone" 같은 id로 Item을 찾음. 이 버전에 없는 아이템이면 null. */
	/** Optional / Holder(value())를 벗긴 레지스트리 값. 못 벗기면 그대로. */
	private static Object unwrapRegistryResult(Object o) {
		if (o instanceof java.util.Optional<?> opt) {
			o = opt.orElse(null);
		}
		if (o != null && !(o instanceof Item)) {
			try {
				Method value = o.getClass().getMethod("value");
				value.setAccessible(true);
				o = value.invoke(o);
			} catch (Throwable ignored) {
			}
		}
		return o;
	}

	public static Item itemById(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		String ns = "minecraft", path = id;
		int colon = id.indexOf(':');
		if (colon >= 0) {
			ns = id.substring(0, colon);
			path = id.substring(colon + 1);
		}
		Identifier ident = identifier(ns, path);
		if (ident == null) {
			return null;
		}
		for (String holderClassName : new String[]{"net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.util.registry.Registry"}) {
			try {
				Class<?> cls = classForName(holderClassName);
				Object registry = getFieldCompat(cls, "ITEM").get(null);
				// containsId로 먼저 확인(없으면 get이 air를 돌려주는 버전이 있음)
				try {
					Object has = getMethodCompat(registry.getClass(), "containsKey", Identifier.class).invoke(registry, ident);
					if (Boolean.FALSE.equals(has)) {
						return null;
					}
				} catch (Throwable ignored) {
				}
				// 49-209차: 26.x(모장 이름)의 Registry#get(Identifier)은 Optional<Holder.Reference>를 돌려준다 - 그래서
				// 늘 null이 나와 예시 아이템(미리보기의 조약돌 등)이 안 그려졌다. 값을 바로 주는 getValue를 먼저 쓰고,
				// 그래도 아니면 Optional/Holder를 벗겨 본다.
				Object item = null;
				Method getValue = findMethod(registry.getClass(), "getValue", Identifier.class);
				if (getValue != null) {
					item = getValue.invoke(registry, ident);
				}
				if (!(item instanceof Item)) {
					item = unwrapRegistryResult(getMethodCompat(registry.getClass(), "get", Identifier.class).invoke(registry, ident));
				}
				return item instanceof Item i ? i : null;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	// ==================== 레지스트리(Item ID 조회) ====================
	// 35차: 아이템 레지스트리 홀더가 1.19.3 미만은 net.minecraft.util.registry.Registry,
	// 1.19.3+는 net.minecraft.registry.Registries로 갈림(패키지/클래스명만 다르고 .getId(item)
	// API 자체는 동일한 진짜 Registry<Item> 객체) - 두 홀더 클래스 이름을 순서대로 시도.
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> REG_KEY_METHOD = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * 49-210차: 레지스트리 값 → Identifier. 26.x(모장 이름)는 {@code getId(T)}가 <b>int(번호)</b>이고 Identifier는
	 * {@code getKey(T)}라, 예전처럼 이름 "getId"로 부르면 번호가 나와 아이템/블록/엔티티 id를 늘 못 읽었다
	 * (작물 계산기가 캔 블록을 못 알아봐 한 판이 안 열림 등). 반환형이 Identifier인 한 인자 메서드를 골라 부른다.
	 */
	public static Identifier registryKeyOf(Object registry, Object value) {
		if (registry == null || value == null) {
			return null;
		}
		Class<?> rc = registry.getClass();
		Object cached = REG_KEY_METHOD.get(rc);
		if (cached == null) {
			cached = MISS;
			Method fallback = null;
			for (Method m : rc.getMethods()) {
				if (m.getParameterCount() != 1 || !Identifier.class.isAssignableFrom(m.getReturnType())
						|| m.getParameterTypes()[0] != Object.class) {
					continue;
				}
				if ("getKey".equals(m.getName())) {
					cached = m;
					break;
				}
				if (fallback == null) {
					fallback = m;
				}
			}
			if (cached == MISS && fallback != null) {
				cached = fallback;
			}
			if (cached instanceof Method mm) {
				try {
					mm.setAccessible(true);
				} catch (Throwable ignored) {
				}
			}
			REG_KEY_METHOD.put(rc, cached);
		}
		if (cached instanceof Method m) {
			try {
				Object r = m.invoke(registry, value);
				if (r instanceof Identifier id) {
					return id;
				}
			} catch (Throwable ignored) {
			}
		}
		Object r = call1(registry, "getKey", value);
		return r instanceof Identifier id ? id : null;
	}

	public static Identifier getItemId(Item item) {
		if (item == null) {
			return null;
		}
		for (String holderClassName : new String[]{"net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.util.registry.Registry"}) {
			try {
				Class<?> cls = classForName(holderClassName);
				Object registry = getFieldCompat(cls, "ITEM").get(null);
				Object id = registryKeyOf(registry, item);
				if (id instanceof Identifier identifier) {
					return identifier;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	// ==================== 바이옴 이름 조회 ====================
	// 35차: World#getBiome(BlockPos)의 반환 형태가 세 시대로 갈림 - 1.18+는 RegistryEntry<Biome>를
	// 반환(getKey() -> Optional<RegistryKey<Biome>> -> getValue() -> Identifier), 1.16~1.17.1은
	// Biome를 직접 반환(레지스트리에서 역조회로 Identifier를 구해야 함). 어느 쪽이든 최종적으로
	// Identifier의 path 문자열만 뽑아서 반환.
	public static String getBiomeName(Object world, BlockPos pos) {
		if (world == null || pos == null) {
			return null;
		}
		try {
			Object biomeResult = call1(world, "getBiome", pos);
			if (biomeResult == null) {
				return null;
			}
			// 신버전(1.18+): RegistryEntry<Biome> 스타일 - getKey() -> Optional<RegistryKey<Biome>>
			Object keyOptional = callNoArg(biomeResult, "getKey");
			if (!(keyOptional instanceof java.util.Optional<?>)) {
				keyOptional = callNoArg(biomeResult, "unwrapKey");   // 49-210차: 26.x 모장 이름
			}
			if (keyOptional instanceof java.util.Optional<?> opt && opt.isPresent()) {
				Object key = opt.get();
				Object identifier = callNoArg(key, "getValue");
				if (!(identifier instanceof Identifier)) {
					identifier = callNoArg(key, "identifier");   // 49-210차: 26.x 모장 이름
				}
				if (identifier != null) {
					Object path = callNoArg(identifier, "getPath");
					if (path instanceof String s) {
						return s;
					}
				}
			}
			// 구버전(1.16~1.17.1): Biome 직접 반환 - 레지스트리에서 역조회
			for (String holderClassName : new String[]{"net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.util.registry.Registry"}) {
				try {
					Class<?> cls = classForName(holderClassName);
					Object registry = getFieldCompat(cls, "BIOME").get(null);
					Object id = registryKeyOf(registry, biomeResult);
					if (id != null) {
						Object path = callNoArg(id, "getPath");
						if (path instanceof String s) {
							return s;
						}
					}
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	// ==================== KeyBinding 생성/등록 ====================
	// 35차: KeyBinding 생성자의 마지막 인자 타입이 대부분 버전에서 String(카테고리 번역키)이지만
	// 1.21.9+부터 KeyBinding.Category 레코드로 바뀜(claude/nova-mod-todo.md 33차). 또한 클래스
	// 패키지 자체가 1.16.x는 net.minecraft.client.options(복수형), 1.17+는
	// net.minecraft.client.option(단수형)으로 갈림. 둘 다 리플렉션으로 흡수.
	public static Object createAndRegisterKeyBinding(String translationKey, int glfwKeyCode) {
		try {
			Class<?> keyBindingClass = resolveClass(
					"net.minecraft.client.KeyMapping", "net.minecraft.client.options.KeyBinding");
			if (keyBindingClass == null) {
				return null;
			}
			Class<?> typeClass = classForName("com.mojang.blaze3d.platform.InputConstants$Type");
			Object keysymType = LunaInput.keyboardKey(-1).getType();   // 49-215차: 26.3은 KEYSYM → KEYBOARD

			Object keyBinding = null;
			for (java.lang.reflect.Constructor<?> ctor : keyBindingClass.getConstructors()) {
				Class<?>[] params = ctor.getParameterTypes();
				if (params.length != 4 || params[0] != String.class || !params[1].isAssignableFrom(typeClass) || params[2] != int.class) {
					continue;
				}
				try {
					Object lastArg;
					if (params[3] == String.class) {
						lastArg = "key.categories.lunaslight";
					} else if (classIs(params[3], "net.minecraft.client.KeyMapping$Category")) {
						Class<?> categoryClass = params[3];
						Object identifier = getMethodCompat(Identifier.class, "of", String.class, String.class)
								.invoke(null, kr.lunaslight.mod.LunaClientMod.MOD_ID, "main");
						lastArg = getMethodCompat(categoryClass, "create", Identifier.class).invoke(null, identifier);
					} else {
						continue;
					}
					keyBinding = ctor.newInstance(translationKey, keysymType, glfwKeyCode, lastArg);
					break;
				} catch (Throwable ignored) {
				}
			}
			if (keyBinding == null) {
				return null;
			}
			// 26.x: Fabric API가 keybinding.v1.KeyBindingHelper#registerKeyBinding 대신
			// keymapping.v1.KeyMappingHelper#registerKeyMapping (26.1+) - 둘 다 시도.
			Class<?> helperClass = resolveClass("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
					"net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
			if (helperClass != null) {
				for (Method m : helperClass.getMethods()) {
					if ((m.getName().equals("registerKeyMapping") || m.getName().equals("registerKeyBinding")) && m.getParameterCount() == 1) {
						m.invoke(null, keyBinding);
						return keyBinding;
					}
				}
			}
			return keyBinding;
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 키바인드 생성/등록 실패", t);
			return null;
		}
	}

	/** keyBinding.wasPressed() 호출(구/신 패키지 둘 다 같은 메서드명). */
	public static boolean wasKeyBindingPressed(Object keyBinding) {
		if (keyBinding == null) {
			return false;
		}
		try {
			Object r = getMethodCompat(keyBinding.getClass(), "consumeClick").invoke(keyBinding);
			return Boolean.TRUE.equals(r);
		} catch (Throwable ignored) {
			return false;
		}
	}

	// ==================== 데이터 컴포넌트(FOOD/CONSUMABLE/CUSTOM_DATA/CONTAINER) ====================
	// net.minecraft.component 패키지 전체가 1.20.5+ 전용 - 1.20.1/1.20.4엔 패키지 자체가 없어
	// 공유 소스에서 그 타입을 직접 쓰면 그 두 버전 컴파일이 깨짐. 전부 리플렉션으로 우회하고,
	// 1.20.1/1.20.4에서는 클래스를 못 찾아 항상 "기능 없음"으로 자연스럽게 폴백됨.
	public static boolean isFoodOrDrink(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		// 48-2차: 컴포넌트보다 먼저 ItemStack#getUseAction()으로 판별 - 이 메서드는 1.15.2~1.21.11
		// 전부 같은 Yarn 이름으로 존재(tiny 실측)해서 가장 신뢰도가 높음. EAT/DRINK면 음식/음료.
		try {
			Object action = getMethodCompat(ItemStack.class, "getUseAnimation").invoke(stack);
			if (enumNameIs(action, "EAT") || enumNameIs(action, "DRINK")) {
				return true;
			}
		} catch (Throwable t) {
			warnOnce("isFoodOrDrink:getUseAction", t);
		}
		try {
			Class<?> typesClass = classForName("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classForName("net.minecraft.core.component.DataComponentType");
			Method contains = getMethodCompat(ItemStack.class, "has", componentTypeClass);
			Object foodType = getFieldCompat(typesClass, "FOOD").get(null);
			if (Boolean.TRUE.equals(contains.invoke(stack, foodType))) {
				return true;
			}
			Method get = getMethodCompat(ItemStack.class, "get", componentTypeClass);
			Object consumableType = getFieldCompat(typesClass, "CONSUMABLE").get(null);
			Object consumable = get.invoke(stack, consumableType);
			if (consumable != null) {
				Object action = getMethodCompat(consumable.getClass(), "animation").invoke(consumable);
				return enumNameIs(action, "EAT") || enumNameIs(action, "DRINK");
			}
		} catch (Throwable t) {
			// 1.20.1/1.20.4: 컴포넌트 시스템 자체가 없음 - 이 판별 기능은 그냥 비활성. (48차: 한 번만 로그)
			warnOnce("isFoodOrDrink", t);
		}
		return false;
	}

	/**
	 * 49-20차: 이 아이템을 먹으면 회복되는 [배고픔 포인트, 포만감(saturation)] - 음식이 아니면 null.
	 *
	 * 버전별로 데이터 위치가 완전히 다르다(tiny 매핑 실측):
	 *  - 1.20.5+ : ItemStack.get(DataComponentTypes.FOOD) → FoodComponent#nutrition()/saturation()
	 *              (여기서 saturation()은 이미 계산된 **최종 값**이라 그대로 쓴다)
	 *  - ~1.20.4 : Item#getFoodComponent() → FoodComponent#getHunger()/getSaturationModifier()
	 *              (이쪽은 '배율'이라 바닐라 공식대로 hunger × modifier × 2 로 환산)
	 */
	// 49-23차: 매 프레임 불리므로(포만감 HUD) 해석 결과를 캐시 - 예전엔 구버전에서 프레임마다
	// ClassNotFoundException을 만들어 버렸다.
	private static int foodMode = -1; // 0=컴포넌트(1.20.5+), 1=Item#getFoodComponent(~1.20.4), -2=둘 다 없음
	private static Object foodComponentType;
	private static Method foodGet;

	public static float[] foodValues(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		if (foodMode == -1) {
			foodMode = -2;
			Class<?> typesClass = classOrNull("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classOrNull("net.minecraft.core.component.DataComponentType");
			if (typesClass != null && componentTypeClass != null) {
				try {
					foodComponentType = getFieldCompat(typesClass, "FOOD").get(null);
					foodGet = getMethodCompat(ItemStack.class, "get", componentTypeClass);
					foodMode = 0;
				} catch (Throwable t) {
					warnOnce("foodValues:component", t);
				}
			}
			if (foodMode == -2 && findNoArgMethod(net.minecraft.world.item.Item.class, "getFoodComponent") != null) {
				foodMode = 1;
			}
		}
		try {
			if (foodMode == 0) {
				// ① 컴포넌트(1.20.5+): FoodComponent#nutrition()/saturation() (saturation()은 이미 최종 값)
				Object food = foodGet.invoke(stack, foodComponentType);
				if (food == null) {
					return null; // 컴포넌트 시스템이 있는 버전인데 FOOD가 없으면 음식이 아님
				}
				Method nutrition = findNoArgMethod(food.getClass(), "nutrition");
				Method saturation = findNoArgMethod(food.getClass(), "saturation");
				if (nutrition == null || saturation == null) {
					return null;
				}
				return new float[] { ((Number) nutrition.invoke(food)).floatValue(), ((Number) saturation.invoke(food)).floatValue() };
			}
			if (foodMode == 1) {
				// ② 예전 방식(~1.20.4): FoodComponent#getHunger()/getSaturationModifier() (배율 → hunger × modifier × 2)
				Object item = stack.getItem();
				Method getFood = findNoArgMethod(item.getClass(), "getFoodComponent");
				Object food = getFood == null ? null : getFood.invoke(item);
				if (food == null) {
					return null;
				}
				Method hungerM = findNoArgMethod(food.getClass(), "getHunger");
				Method modM = findNoArgMethod(food.getClass(), "getSaturationModifier");
				if (hungerM == null || modM == null) {
					return null;
				}
				float hunger = ((Number) hungerM.invoke(food)).floatValue();
				float modifier = ((Number) modM.invoke(food)).floatValue();
				return new float[] { hunger, hunger * modifier * 2f };
			}
		} catch (Throwable t) {
			warnOnce("foodValues", t);
		}
		return null;
	}

	/** stack에 커스텀 데이터(NBT)가 있는지. 1.20.1/1.20.4에서는 항상 false. */
	public static boolean hasCustomData(ItemStack stack) {
		try {
			Class<?> typesClass = classForName("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classForName("net.minecraft.core.component.DataComponentType");
			Object customDataType = getFieldCompat(typesClass, "CUSTOM_DATA").get(null);
			Method get = getMethodCompat(ItemStack.class, "get", componentTypeClass);
			Object custom = get.invoke(stack, customDataType);
			if (custom == null) {
				return false;
			}
			Object empty = getMethodCompat(custom.getClass(), "isEmpty").invoke(custom);
			return !Boolean.TRUE.equals(empty);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * 49-32차: 이 컴포넌트가 붙어 있는지(1.20.5+). 그 전 버전엔 컴포넌트 자체가 없어 false.
	 * fieldName은 DataComponentTypes의 상수 이름(TOOL, WEAPON, EQUIPPABLE …).
	 */
	public static boolean hasComponent(ItemStack stack, String fieldName) {
		try {
			Class<?> typesClass = classOrNull("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classOrNull("net.minecraft.core.component.DataComponentType");
			if (typesClass == null || componentTypeClass == null) {
				return false;
			}
			java.lang.reflect.Field f = findField(typesClass, fieldName);
			if (f == null) {
				return false;
			}
			Object type = f.get(null);
			Method get = findMethod(ItemStack.class, "get", componentTypeClass);
			return get != null && get.invoke(stack, type) != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * 49-32차: "도구·무기인가?" — 1.21.2부터 마인크래프트가 PickaxeItem/SwordItem 같은 **클래스 자체를
	 * 없애고** 데이터 컴포넌트로 바꿔서, 클래스 이름으로만 판단하던 코드가 최신 버전에서 전부
	 * "도구가 아님"으로 새어 나갔다(사용자: "도구 두 번 눌러 버리기가 안 되고 바로 버려짐").
	 * 이제 ① TOOL/WEAPON 컴포넌트 ② 내구도가 있는지 순으로 본다.
	 */
	public static boolean isToolLike(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return hasComponent(stack, "TOOL") || hasComponent(stack, "WEAPON");
	}

	/** 내구도가 있는 아이템인지(도구·무기·방어구·활 …). 못 읽으면 false. */
	public static boolean hasDurability(ItemStack stack) {
		try {
			Object max = invokeNoArg(stack, "getMaxDamage");
			return max instanceof Integer i && i > 0;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 셜커박스 등 CONTAINER 컴포넌트의 내용물을 하나씩 consumer로 전달. 1.20.1/1.20.4에서는 아무 것도 안 함. */
	public static void forEachContainerItem(ItemStack stack, Consumer<ItemStack> consumer) {
		try {
			Class<?> typesClass = classForName("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classForName("net.minecraft.core.component.DataComponentType");
			Object containerType = getFieldCompat(typesClass, "CONTAINER").get(null);
			Method get = getMethodCompat(ItemStack.class, "get", componentTypeClass);
			Object container = get.invoke(stack, containerType);
			if (container == null) {
				return;
			}
			Object iterable = getMethodCompat(container.getClass(), "iterableContents").invoke(container);
			for (Object o : (Iterable<?>) iterable) {
				if (o instanceof ItemStack contained) {
					consumer.accept(contained);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	// ==================== EnchantmentHelper ====================
	// 21차: EnchantmentHelper.getEnchantments(ItemStack)가 1.20.1/1.20.4엔 없음(그때는
	// EnchantmentHelper.get(ItemStack) -> Map<Enchantment,Integer> 형태였고, 1.20.5+부터
	// getEnchantments(ItemStack) -> ItemEnchantmentsComponent(RegistryEntry 기반) 형태로 개편됨).
	// 두 형태 다 리플렉션으로 흡수해서 (레벨, 최대레벨) 쌍을 순서대로 consumer에 전달.
	public static void forEachEnchantment(ItemStack stack, BiConsumer<Integer, Integer> consumer) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		try {
			Class<?> helperClass = classForName("net.minecraft.world.item.enchantment.EnchantmentHelper");
			for (Method m : helperClass.getMethods()) {
				if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) {
					continue;
				}
				if (!m.getParameterTypes()[0].isInstance(stack)) {
					continue;
				}
				if (!nameMatches(helperClass, "get", m.getName()) && !nameMatches(helperClass, "getEnchantments", m.getName())) {
					continue;
				}
				try {
					Object result = m.invoke(null, stack);
					if (result == null) {
						continue;
					}
					if (result instanceof java.util.Map<?, ?> map) {
						// 구버전: Map<Enchantment, Integer>
						for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
							int level = (e.getValue() instanceof Integer i) ? i : 0;
							int maxLevel = getEnchantmentMaxLevel(e.getKey());
							consumer.accept(level, maxLevel);
						}
						return;
					}
					// 신버전: ItemEnchantmentsComponent - getEnchantments()가 Set<RegistryEntry<Enchantment>>
					Object entries = callNoArg(result, "getEnchantments");
					if (entries instanceof Iterable<?> iterable) {
						for (Object entry : iterable) {
							Object levelObj = call1(result, "getLevel", entry);
							int level = (levelObj instanceof Integer i) ? i : 0;
							Object enchantment = resolveMember(entry, "value");
							int maxLevel = getEnchantmentMaxLevel(enchantment != null ? enchantment : entry);
							consumer.accept(level, maxLevel);
						}
						return;
					}
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
			// EnchantmentHelper 자체를 못 찾는 경우는 없겠지만, 만약을 대비해 조용히 무시.
		}
	}

	private static int getEnchantmentMaxLevel(Object enchantment) {
		Object r = callNoArg(enchantment, "getMaxLevel");
		return (r instanceof Integer i) ? i : 0;
	}

	/** target의 1인자 메서드를 이름으로 찾아 호출(가장 먼저 매치되는 것 사용). */
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, Object>> CALL1_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, Object>> CALLN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * 49-39차: N인자 메서드를 이름 + 인자 개수 + 인자 타입 호환으로 찾아 호출(기본형은 박싱 타입으로 비교).
	 * Redirect 믹스인이 "원래 호출"을 되돌릴 때처럼, 시그니처가 버전마다 달라 소스에서 직접 못 부르는 경우용.
	 * 실패하면 null(그 그리기 한 번만 빠짐).
	 */
	public static Object invokeNamed(Object target, String methodName, Object... args) {
		if (target == null) {
			return null;
		}
		Class<?> tc = target.getClass();
		StringBuilder kb = new StringBuilder(methodName).append('/').append(args.length);
		for (Object a : args) {
			kb.append(':').append(a == null ? "null" : a.getClass().getSimpleName());
		}
		String key = kb.toString();
		java.util.concurrent.ConcurrentHashMap<String, Object> byKey = perClass(CALLN_CACHE, tc);
		Object cached = byKey.get(key);
		if (cached == MISS) {
			return null;
		}
		if (cached instanceof Method cm) {
			try {
				return cm.invoke(target, args);
			} catch (Throwable ignored) {
				return null;
			}
		}
		List<String> names = memberNameCandidates(tc, methodName);
		for (Class<?> c = tc; c != null; c = c.getSuperclass()) {
			for (Method m : c.getMethods()) {
				if (!names.contains(m.getName()) || m.getParameterCount() != args.length) {
					continue;
				}
				Class<?>[] pt = m.getParameterTypes();
				boolean ok = true;
				for (int i = 0; i < pt.length && ok; i++) {
					ok = args[i] == null ? !pt[i].isPrimitive() : boxed(pt[i]).isInstance(args[i]);
				}
				if (!ok) {
					continue;
				}
				try {
					m.setAccessible(true);
					Object r = m.invoke(target, args);
					byKey.put(key, m);
					return r;
				} catch (Throwable ignored) {
					// 다음 후보
				}
			}
		}
		byKey.put(key, MISS);
		return null;
	}

	private static Class<?> boxed(Class<?> c) {
		if (!c.isPrimitive()) return c;
		if (c == int.class) return Integer.class;
		if (c == float.class) return Float.class;
		if (c == double.class) return Double.class;
		if (c == long.class) return Long.class;
		if (c == boolean.class) return Boolean.class;
		if (c == short.class) return Short.class;
		if (c == byte.class) return Byte.class;
		if (c == char.class) return Character.class;
		return c;
	}

	/** 1인자 메서드를 이름으로 찾아 호출. 49-22차: (클래스, 이름, 인자 타입)별로 성공한 메서드를 캐시. */
	public static Object call1(Object target, String methodName, Object arg) {
		if (target == null) {
			return null;
		}
		Class<?> tc = target.getClass();
		String key = methodName + "(" + (arg == null ? "null" : arg.getClass().getName()) + ")";
		java.util.concurrent.ConcurrentHashMap<String, Object> byKey = perClass(CALL1_CACHE, tc);
		Object cached = byKey.get(key);
		if (cached == MISS) {
			return null;
		}
		if (cached instanceof Method cm) {
			try {
				return cm.invoke(target, arg);
			} catch (Throwable ignored) {
				return null;
			}
		}
		List<String> names = memberNameCandidates(tc, methodName);
		for (Class<?> c = tc; c != null; c = c.getSuperclass()) {
			for (Method m : c.getMethods()) {
				if (names.contains(m.getName()) && m.getParameterCount() == 1) {
					try {
						m.setAccessible(true);
						Object r = m.invoke(target, arg);
						byKey.put(key, m);
						return r;
					} catch (Throwable ignored) {
					}
				}
			}
		}
		byKey.put(key, MISS);
		return null;
	}

	// ==================== ItemTooltipCallback ====================
	// getTooltip(...)의 파라미터 개수(TooltipType 유무)가 버전마다 다르고, EVENT가 unregister를
	// 지원하지 않음 -> Proxy로 시그니처 상관없이 동적 등록하고, on/off는 등록 해제가 아니라
	// enabled 콜백으로 내부에서 판단.
	public static void registerTooltipCallback(BooleanSupplier enabled, BiConsumer<ItemStack, List<Component>> handler) {
		try {
			Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback");
			Object proxy = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
					(InvocationHandler) (p, method, args) -> {
						if (enabled.getAsBoolean() && args != null && args.length >= 2) {
							Object stackObj = args[0];
							Object linesObj = args[args.length - 1];
							if (stackObj instanceof ItemStack stack && linesObj instanceof List<?> list && !stack.isEmpty()) {
								@SuppressWarnings("unchecked")
								List<Component> lines = (List<Component>) list;
								handler.accept(stack, lines);
							}
						}
						return null;
					});
			Object event = getFieldCompat(callbackClass, "EVENT").get(null);
			invokeRegister(event, proxy);
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 아이템 툴팁 콜백 등록 실패", t);
		}
	}

	// ==================== HudRenderCallback ====================
	// 신버전: render(DrawContext, RenderTickCounter) / 구버전(1.20.1/1.20.4): render(DrawContext, float)
	public interface HudRenderHandler {
		void render(GuiGraphicsExtractor context, DeltaTracker tickCounter);
	}

	private static int hudElementSeq;

	public static void registerHudRenderCallback(HudRenderHandler handler) {
		// 26.x: HudRenderCallback이 Fabric API에서 제거됨(1.21.6에서 deprecated) → HudElementRegistry.addLast(id, HudElement).
		// HudElement#extractRenderState(GuiGraphicsExtractor, DeltaTracker)를 Proxy로 구현해 같은 핸들러로 넘긴다.
		try {
			Class<?> registry = classOrNull("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
			Class<?> elementClass = classOrNull("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement");
			if (registry != null && elementClass != null) {
				Object element = Proxy.newProxyInstance(elementClass.getClassLoader(), new Class<?>[]{elementClass},
						(InvocationHandler) (p, method, args) -> {
							if (method.getDeclaringClass() == Object.class) {
								return proxyObjectMethod(p, method, args);
							}
							if (args == null || args.length < 1) {
								return null;
							}
							GuiGraphicsExtractor ctx = toDrawContext(args[0]);
							if (ctx == null) {
								return null;
							}
							DeltaTracker counter = toRenderTickCounter(args.length > 1 ? args[1] : null);
							handler.render(ctx, counter);
							return null;
						});
				Identifier id = identifier(kr.lunaslight.mod.LunaClientMod.MOD_ID, "hud_" + (hudElementSeq++));
				for (Method m : registry.getMethods()) {
					if (m.getName().equals("addLast") && m.getParameterCount() == 2) {
						m.invoke(null, id, element);
						return;
					}
				}
			}
			Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
			Object proxy = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
					(InvocationHandler) (p, method, args) -> {
						GuiGraphicsExtractor ctx = toDrawContext(args[0]);
						if (ctx == null) {
							return null;
						}
						DeltaTracker counter = toRenderTickCounter(args.length > 1 ? args[1] : null);
						handler.render(ctx, counter);
						return null;
					});
			Object event = getFieldCompat(callbackClass, "EVENT").get(null);
			invokeRegister(event, proxy);
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] HUD 렌더 콜백 등록 실패", t);
		}
	}

	/**
	 * 49-45차: 바닐라 "옵션" 화면 열기. 클래스 위치가 버전마다 달라 이름을 나열해 찾는다
	 * (1.16+ screen.option.OptionsScreen / 1.15.2 screen.SettingsScreen). 생성자는 어느 쪽이든
	 * (Screen parent, GameOptions options) 형태라 그대로 넘기면 된다. 못 찾으면 아무 일도 안 한다.
	 */
	public static boolean openVanillaOptions(Object parent) {
		Minecraft mc = Minecraft.getInstance();
		String[] names = {
			"net.minecraft.client.gui.screens.options.OptionsScreen",
			"net.minecraft.client.gui.screen.options.OptionsScreen",
			"net.minecraft.client.gui.screen.SettingsScreen",
		};
		// 49-96차(26.x에서 "마크 설정이 안 열림"): 26.x는 OptionsScreen 생성자가 (Screen, GameOptions, boolean inWorld)
		// 3개로 바뀌었다(fix262에서 게임 안 화면도 이 인자를 넣게 고쳤던 그 변화). 2개짜리만 찾던 예전 코드는
		// 26.x에서 맞는 생성자를 못 찾아 아무 일도 안 하고 끝났다 - 3개(마지막 boolean = 월드 안인지)도 받는다.
		boolean inWorld = mc.level != null;
		for (String name : names) {
			try {
				Class<?> cls = classForName(name);
				java.lang.reflect.Constructor<?> two = null, three = null;
				for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
					Class<?>[] p = c.getParameterTypes();
					if (p.length == 2 && p[0].isInstance(parent) && p[1].isInstance(mc.options)) {
						two = c;
					} else if (p.length == 3 && p[0].isInstance(parent) && p[1].isInstance(mc.options)
							&& (p[2] == boolean.class || p[2] == Boolean.class)) {
						three = c;
					}
				}
				if (two != null) {
					setScreen(two.newInstance(parent, mc.options));
					return true;
				}
				if (three != null) {
					setScreen(three.newInstance(parent, mc.options, inWorld));
					return true;
				}
			} catch (Throwable ignored) {
				// 다음 후보 이름으로
			}
		}
		warnOnce("vanillaOptions", new IllegalStateException("옵션 화면 클래스를 찾지 못함"));
		return false;
	}

	/**
	 * 49-45차: 부모 화면(바닐라 Screen)을 우리 화면 뒤에 그대로 한 번 그린다. 그리기 인자가
	 * 1.20+는 DrawContext, 1.15.2~1.19.4는 MatrixStack이라 직접 부르면 구세대에서 컴파일이
	 * 깨진다 - 실제 파라미터 타입을 보고 맞는 쪽을 넘긴다(구세대에선 shim DrawContext가 들고
	 * 있는 MatrixStack을 꺼내 준다). 실패하면 false를 돌려주고 호출부가 대체 배경을 그린다.
	 *
	 * ⚠️ 49-46차: width/height를 꼭 넘길 것. 마인크래프트는 <b>지금 떠 있는 화면 하나만</b> 리사이즈하기
	 * 때문에, 창 크기를 바꾸면 뒤에 그려지는 부모 화면은 예전 크기(예: 1280×720) 그대로 남아 화면
	 * 왼쪽 위 구석에만 그려진다. 그리기 직전에 크기가 어긋나 있으면 부모도 맞춰 준다.
	 */
	public static boolean renderParentScreen(Object parent, GuiGraphicsExtractor ctx, int width, int height,
			int mouseX, int mouseY, float delta) {
		if (parent == null || ctx == null) {
			return false;
		}
		resizeScreenIfNeeded(parent, width, height);
		Object matrices = null;
		try {
			java.lang.reflect.Method gm = ctx.getClass().getMethod("getMatrices");
			matrices = gm.invoke(ctx);
		} catch (Throwable ignored) {
			// 신버전에도 getMatrices가 있지만 없어도 상관없다(아래에서 ctx를 그대로 넘김).
		}
		for (Method m : parent.getClass().getMethods()) {
			if (!nameMatches(parent.getClass(), "render", m.getName())) {
				continue;
			}
			Class<?>[] p = m.getParameterTypes();
			if (p.length != 4 || p[1] != int.class || p[2] != int.class || p[3] != float.class) {
				continue;
			}
			Object first = p[0].isInstance(ctx) ? ctx : (matrices != null && p[0].isInstance(matrices) ? matrices : null);
			if (first == null) {
				continue;
			}
			try {
				m.invoke(parent, first, mouseX, mouseY, delta);
				return true;
			} catch (Throwable ignored) {
				// 다음 후보로
			}
		}
		return false;
	}

	/**
	 * 49-46차: 화면의 width/height가 지금 창 크기와 다르면 resize(client,w,h)를 한 번 불러 맞춘다
	 * (resize가 init을 다시 돌려 버튼 배치까지 새로 잡아 준다). 이미 맞으면 아무것도 하지 않으므로
	 * 매 프레임 불러도 비용이 없다 - 크기가 바뀐 그 프레임에만 한 번 돈다.
	 */
	public static void resizeScreenIfNeeded(Object screen, int width, int height) {
		if (screen == null || width <= 0 || height <= 0) {
			return;
		}
		try {
			java.lang.reflect.Field fw = findField(net.minecraft.client.gui.screens.Screen.class, "width");
			java.lang.reflect.Field fh = findField(net.minecraft.client.gui.screens.Screen.class, "height");
			if (fw == null || fh == null) {
				return; // 크기를 못 읽으면 건드리지 않는다(매 프레임 init을 돌리는 게 훨씬 나쁘다)
			}
			if (fw.getInt(screen) == width && fh.getInt(screen) == height) {
				return;
			}
			// resize의 시그니처가 두 갈래다(실측): ≤1.20.x는 resize(MinecraftClient,int,int),
			// 1.21.11·26.x는 resize(int,int) - MinecraftClient 인자가 빠졌다. 둘 다 받는다.
			Minecraft mc = Minecraft.getInstance();
			Method three = null;
			Method two = null;
			for (Method m : screen.getClass().getMethods()) {
				if (!nameMatches(screen.getClass(), "resize", m.getName())) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 3 && p[0].isInstance(mc) && p[1] == int.class && p[2] == int.class) {
					three = m;
				} else if (p.length == 2 && p[0] == int.class && p[1] == int.class) {
					two = m;
				}
			}
			if (three != null) {
				three.invoke(screen, mc, width, height);
			} else if (two != null) {
				two.invoke(screen, width, height);
			}
		} catch (Throwable t) {
			warnOnce("resizeScreen", t);
		}
	}

	/** 49-21차: 화면(Screen)이 그려진 직후 호출되는 핸들러(아이템 일괄 처분의 슬롯 하이라이트용). */
	public interface ScreenRenderHandler {
		void render(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY);
	}

	/**
	 * 49-21차: Fabric Screen API(v1)의 ScreenEvents.AFTER_INIT → afterRender(screen) 체인을 리플렉션
	 * Proxy로 등록. afterRender 콜백의 두 번째 인자가 MatrixStack(≤1.19.4)/DrawContext(1.20+)로 갈려서
	 * HudRenderCallback과 같은 방식으로 흡수. Screen API가 없는 버전(1.15.2)은 경고만 남기고 비활성.
	 */
	public static void registerScreenAfterRender(ScreenRenderHandler handler) {
		try {
			Class<?> eventsClass = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents");
			Class<?> afterInitClass = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterInit");
			// 26.x: afterRender → afterExtract(Screen, GuiGraphicsExtractor, mouseX, mouseY, tickDelta)
			Class<?> afterRenderClass = resolveClass("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterExtract",
					"net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterRender");
			Method afterRender;
			try {
				afterRender = eventsClass.getMethod("afterExtract", net.minecraft.client.gui.screens.Screen.class);
			} catch (NoSuchMethodException e) {
				afterRender = eventsClass.getMethod("afterRender", net.minecraft.client.gui.screens.Screen.class);
			}
			final Method afterRenderFinal = afterRender;
			Object initProxy = Proxy.newProxyInstance(afterInitClass.getClassLoader(), new Class<?>[]{afterInitClass},
					(InvocationHandler) (p, method, args) -> {
						if (method.getDeclaringClass() == Object.class) {
							return proxyObjectMethod(p, method, args);
						}
						// 49-41차: 인터페이스 메서드 이름은 afterInit (onInit이 아님 - Fabric API 0.141.6 jar javap 실측).
						// 지금까지 이름이 안 맞아 화면 렌더 콜백이 한 번도 등록되지 않았고, 그 위에 얹힌 마우스 트윅스
						// 끌기·일괄 정리 대상 표시·작업대 옆 제작 패널이 전부 "아예 안 되는" 상태였다.
						String mn = method.getName();
						if (!("afterInit".equals(mn) || "onInit".equals(mn)) || args == null || args.length < 2) {
							return null;
						}
						Object screen = args[1];
						Object renderEvent = afterRenderFinal.invoke(null, screen);
						Object renderProxy = Proxy.newProxyInstance(afterRenderClass.getClassLoader(),
								new Class<?>[]{afterRenderClass}, (InvocationHandler) (p2, m2, a2) -> {
									if (m2.getDeclaringClass() == Object.class) {
										return proxyObjectMethod(p2, m2, a2);
									}
									if (!("afterRender".equals(m2.getName()) || "afterExtract".equals(m2.getName())) || a2 == null || a2.length < 4) {
										return null;
									}
									GuiGraphicsExtractor ctx = toDrawContext(a2[1]);
									if (ctx != null) {
										int mx = a2[2] instanceof Number n ? n.intValue() : 0;
										int my = a2[3] instanceof Number n ? n.intValue() : 0;
										handler.render(a2[0], ctx, mx, my);
									}
									return null;
								});
						invokeRegister(renderEvent, renderProxy);
						return null;
					});
			Object initEvent = getFieldCompat(eventsClass, "AFTER_INIT").get(null);
			invokeRegister(initEvent, initProxy);
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 화면 렌더 콜백 등록 실패(이 버전엔 Screen API 없음)", t);
		}
	}

	/** Proxy가 받은 Object 기본 메서드(hashCode/equals/toString) 처리. */
	private static Object proxyObjectMethod(Object proxy, Method method, Object[] args) {
		return switch (method.getName()) {
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> args != null && args.length == 1 && proxy == args[0];
			default -> "LunaProxy@" + Integer.toHexString(System.identityHashCode(proxy));
		};
	}

	/**
	 * 28차: args[0]이 이미 DrawContext면 그대로 쓰고(1.20+), 아니면(1.16~1.19.4: HudRenderCallback이
	 * MatrixStack을 직접 줌 - DrawContext 자체가 없는 버전) 그 서브프로젝트 전용 shim의
	 * DrawContext(MatrixStack) 생성자를 리플렉션으로 찾아 감싼다. 신버전에서는 이 생성자가 아예
	 * 없어서(진짜 DrawContext는 다른 생성자를 가짐) 애초에 이 분기에 들어오지 않는다(instanceof에서
	 * 이미 처리됨) - 그래도 못 찾으면 null 반환, 호출부가 그 프레임은 그냥 건너뜀.
	 */
	/** 49-47차: 색보정 믹스인(구버전 MatrixStack 경로)도 쓴다. */
	public static GuiGraphicsExtractor toDrawContext(Object raw) {
		if (raw instanceof GuiGraphicsExtractor dc) {
			return dc;
		}
		if (raw == null) {
			return null;
		}
		try {
			java.lang.reflect.Constructor<?> ctor = GuiGraphicsExtractor.class.getConstructor(raw.getClass());
			return (GuiGraphicsExtractor) ctor.newInstance(raw);
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * 28차: gui/ 패키지가 통째로 빠진 서브프로젝트(1.16~1.19.4 - LunaClientScreen/ChatSearchScreen이
	 * Screen 상속 문제로 아직 이식 안 됨, claude/nova-mod-todo.md 28차 참고)에서 화면을 열려는
	 * 코드가 컴파일 자체는 되면서도(직접 import/생성자 호출 대신 클래스 이름 문자열로 리플렉션)
	 * 그 버전에서는 조용히 "미지원"으로 처리되게 하는 공용 헬퍼. parent는 null 가능.
	 */
	public static void openScreenReflectively(String screenClassName, Object parent) {
		try {
			Class<?> screenClass = Class.forName(screenClassName);
			Class<?> parentType = screenClass.getConstructors()[0].getParameterTypes()[0];
			Object screen = screenClass.getConstructor(parentType).newInstance(parent);
			setScreen(screen);
		} catch (ClassNotFoundException notSupportedOnThisVersion) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 이 버전에서는 아직 지원되지 않는 화면입니다: " + screenClassName);
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 화면 열기 실패: " + screenClassName, t);
		}
	}

	/**
	 * 35차: MinecraftClient#setScreen(Screen)는 1.17.1부터의 이름이고, 1.16~1.17은
	 * openScreen(Screen)였음(리서치로 경계 확인 - claude/nova-mod-todo.md 35차). 이 메서드 자체가
	 * LunaCompat.java(gui/ 패키지 제외 대상이 아닌 공용 유틸)에 있어서 이름 하나로 고정해서 직접
	 * 호출하면 1.16/1.17 컴파일이 깨지므로 리플렉션으로 두 이름 다 시도.
	 */
	public static void setScreen(Object screen) {
		showScreen(Minecraft.getInstance(), screen instanceof net.minecraft.client.gui.screens.Screen sc ? sc : null);
	}

	// ==================== 26.x: 현재 화면 읽기/바꾸기 ====================
	// 26.1.x까지는 MinecraftClient#screen 필드 + setScreen(Screen), 26.2부터는 Gui(gui 필드)로 옮겨가
	// gui.screen() / gui.setScreen(Screen)이 됐다. 한 소스로 4개 버전(26.1/26.1.1/26.1.2/26.2)을 빌드하므로
	// 둘 다 리플렉션으로 시도(한 번 해석해 캐시).
	private static boolean screenAccessResolved;
	private static java.lang.reflect.Field screenField;
	private static Method setScreenMethod;   // Minecraft#setScreen(Screen)
	private static Method guiScreenMethod, guiSetScreenMethod; // Gui#screen() / Gui#setScreen(Screen)

	private static void resolveScreenAccess(Minecraft mc) {
		if (screenAccessResolved) {
			return;
		}
		screenAccessResolved = true;
		try {
			screenField = findField(mc.getClass(), "screen");
			if (screenField != null) {
				screenField.setAccessible(true);
			}
		} catch (Throwable ignored) {
		}
		setScreenMethod = findMethod(mc.getClass(), "setScreen", net.minecraft.client.gui.screens.Screen.class);
		try {
			Object gui = mc.gui;
			if (gui != null) {
				guiScreenMethod = findNoArgMethod(gui.getClass(), "screen");
				guiSetScreenMethod = findMethod(gui.getClass(), "setScreen", net.minecraft.client.gui.screens.Screen.class);
			}
		} catch (Throwable ignored) {
		}
	}

	/** 지금 열려 있는 화면(없으면 null). */
	public static net.minecraft.client.gui.screens.Screen screenOf(Minecraft mc) {
		if (mc == null) {
			return null;
		}
		resolveScreenAccess(mc);
		try {
			if (screenField != null) {
				Object s = screenField.get(mc);
				return s instanceof net.minecraft.client.gui.screens.Screen sc ? sc : null;
			}
			if (guiScreenMethod != null) {
				Object s = guiScreenMethod.invoke(mc.gui);
				return s instanceof net.minecraft.client.gui.screens.Screen sc ? sc : null;
			}
		} catch (Throwable t) {
			warnOnce("screenOf", t);
		}
		return null;
	}

	/** 화면 열기/닫기(null = 닫기). */
	public static void showScreen(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
		if (mc == null) {
			return;
		}
		resolveScreenAccess(mc);
		try {
			if (setScreenMethod != null) {
				setScreenMethod.invoke(mc, screen);
				return;
			}
			if (guiSetScreenMethod != null) {
				guiSetScreenMethod.invoke(mc.gui, screen);
			}
		} catch (Throwable t) {
			warnOnce("showScreen", t);
		}
	}

	/** 주 프레임버퍼: Minecraft#getMainRenderTarget()(~26.1) / GameRenderer#mainRenderTarget()(26.2). */
	public static Object mainRenderTarget(Minecraft mc) {
		if (mc == null) {
			return null;
		}
		Object r = callNoArg(mc, "getMainRenderTarget");
		if (r == null && mc.gameRenderer != null) {
			r = callNoArg(mc.gameRenderer, "mainRenderTarget");
		}
		return r;
	}

	/** 49-36차: 엔티티 네트워크 id - getId()(1.17+) / getEntityId()(≤1.16). 실패 시 -1. */
	public static int entityNetworkId(Object entity) {
		for (String name : new String[]{"getId", "getEntityId"}) {
			Object v = callNoArg(entity, name);
			if (v instanceof Integer i) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 49-36차: 커서에 든 아이템 - ScreenHandler#getCursorStack()(1.17+) / PlayerInventory#getCursorStack()(≤1.16).
	 * 실패 시 ItemStack.EMPTY.
	 */
	public static net.minecraft.world.item.ItemStack cursorStack(net.minecraft.world.entity.player.Player player) {
		if (player == null) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
		try {
			Object handler = getFieldValue(player, "containerMenu", "container");
			Object v = handler == null ? null : callNoArg(handler, "getCarried");
			if (v instanceof net.minecraft.world.item.ItemStack st) {
				return st;
			}
			Object inv = getFieldValue(player, "inventory");
			if (inv == null) {
				inv = callNoArg(player, "getInventory");
			}
			v = inv == null ? null : callNoArg(inv, "getCarried");
			if (v instanceof net.minecraft.world.item.ItemStack st) {
				return st;
			}
		} catch (Throwable ignored) {
		}
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	/**
	 * 49-5차: "월드로 나가기"를 바닐라와 100% 동일하게. 예전 코드는 disconnectWithSavingScreen()만
	 * 부르고 setScreen(TitleScreen)을 강제했는데, 바닐라 나가기 버튼은 그 전에 반드시
	 * world.disconnect()로 레벨 연결을 먼저 끊는다. 그걸 빼먹으면 통합 서버가 저장 도중 클라
	 * 연결이 닫히길 기다리며 멈춰서(→ "세계 저장 중" 정지 → Watchdog 크래시) 발생.
	 *
	 * 버전별로 진입점 이름이 갈려서 "전체 흐름을 한 번에 처리하는" 메서드를 우선 시도하고,
	 * 그게 없으면(중간 버전) 수동으로 레벨 해제 후 저장 화면을 띄운다. 성공한 진입점이 화면
	 * 전환까지 처리하므로 여기서 TitleScreen을 직접 세팅하지 않는다(그게 정지의 원인이었음).
	 */
	public static void quitToTitle(Minecraft client) {
		if (client == null) {
			return;
		}
		Object reason = literalTextSafe("Disconnected");
		// 1) 1.21.9+ : client.disconnect(Text) - 레벨 해제→저장→화면 전환 전부 내부 처리(바닐라 나가기와 동일)
		if (reason != null && invokeIfPresent(client, "disconnect",
				new Class<?>[]{Component.class}, new Object[]{reason})) {
			return;
		}
		// 2) 1.20.x : client.disconnect(Screen) - 통합 서버 종료+저장 후 해당 화면으로(타이틀)
		try {
			Object title = new net.minecraft.client.gui.screens.TitleScreen();
			if (invokeIfPresent(client, "disconnect",
					new Class<?>[]{net.minecraft.client.gui.screens.Screen.class}, new Object[]{title})) {
				return;
			}
		} catch (Throwable ignored) {
		}
		// 3) 1.21.1~1.21.8 : 저장 화면 진입점이 레벨을 스스로 안 끊으므로 먼저 world.disconnect() 후 호출
		disconnectLevel(client, reason);
		if (invokeIfPresent(client, "disconnectWithSavingScreen", new Class<?>[0], new Object[0])) {
			return;
		}
		// 4) 최후 폴백 : no-arg disconnect()
		if (invokeIfPresent(client, "disconnect", new Class<?>[0], new Object[0])) {
			return;
		}
		throw new IllegalStateException("월드 나가기 진입점을 찾지 못함");
	}

	/** client.world(ClientWorld)의 연결 해제. 인자 0개(구버전)/Text 1개(신버전) 둘 다 시도. */
	private static void disconnectLevel(Minecraft client, Object reason) {
		try {
			Object world = client.level;
			if (world == null) {
				return;
			}
			if (invokeIfPresent(world, "disconnect", new Class<?>[0], new Object[0])) {
				return;
			}
			if (reason != null) {
				invokeIfPresent(world, "disconnect", new Class<?>[]{Component.class}, new Object[]{reason});
			}
		} catch (Throwable ignored) {
		}
	}

	/** target에서 yarnName(정확한 파라미터 타입)을 찾아 호출. 없으면 false(예외 안 던짐). */
	private static boolean invokeIfPresent(Object target, String yarnName, Class<?>[] paramTypes, Object[] args) {
		try {
			Method m = getMethodCompat(target.getClass(), yarnName, paramTypes);
			m.setAccessible(true);
			m.invoke(target, args);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Text.literal(1.19+) / new LiteralText(구버전) - 49-36차: 직접 호출은 1.18 이하에서 컴파일이 안 돼 textLiteral로. */
	private static Object literalTextSafe(String s) {
		try {
			return textLiteral(s);
		} catch (Throwable ignored) {
			return null;
		}
	}

	// ==================== 49-6차: 마우스 클릭 원시 계수(CPS용) ====================
	// KeyBinding.isPressed()를 틱마다 폴링하면 한 틱(50ms) 안에 눌렀다 뗀 클릭을 통째로 놓쳐서
	// 10CPS 이상에서 카운트가 뭉개짐("빠르게 누를 때 인식 안 됨"의 원인). GLFW 마우스 버튼
	// 콜백을 기존 콜백을 감싸는 방식으로 설치해 이벤트 단위로 전부 계수한다(믹스인 불필요,
	// 원래 콜백에 그대로 위임하므로 게임 입력엔 영향 없음).
	private static boolean mouseHookInstalled;
	private static boolean mouseHookBroken;
	private static final java.util.concurrent.atomic.AtomicInteger RAW_LEFT_CLICKS = new java.util.concurrent.atomic.AtomicInteger();
	private static final java.util.concurrent.atomic.AtomicInteger RAW_RIGHT_CLICKS = new java.util.concurrent.atomic.AtomicInteger();
	private static Object previousMouseCallback; // GC 방지 + 위임 대상

	/**
	 * 49-215차: GLFW 콜백을 감싸던 방식은 26.3(SDL, GLFW 없음)에서 못 쓴다. 26.x는 전부 MouseHandler.onButton에
	 * 믹스인(MouseButtonCountMixin)을 걸어 세므로 여기선 "켜졌다"만 표시한다(시그니처는 26.1~26.3 동일, javap 실측).
	 */
	public static boolean ensureMouseClickCounter() {
		if (!mouseHookInstalled) {
			mouseHookInstalled = true;
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 마우스 클릭 계수기 설치(CPS 정밀 측정)");
		}
		return !mouseHookBroken;
	}

	/** 믹스인에서: 버튼 누름(GLFW 번호). */
	public static void countRawClick(int glfwButton) {
		if (!mouseHookInstalled) {
			return;
		}
		if (glfwButton == 0) {
			RAW_LEFT_CLICKS.incrementAndGet();
		} else if (glfwButton == 1) {
			RAW_RIGHT_CLICKS.incrementAndGet();
		}
	}

	// ==================== 49-39차: 글자 입력 가로채기(TextCapture) ====================
	private static boolean textCaptureInstalled, textCaptureBroken;
	private static Object previousKeyCallback, previousCharModsCallback;   // GC 방지용 보관

	/**
	 * GLFW key/charmods 콜백을 감싸 TextCapture로 먼저 보낸다(마우스 클릭 계수기와 같은 방식). 마인크래프트가
	 * 두 콜백을 등록한 뒤(창 생성 후) 한 번만 설치하면 되고, 대상이 없을 땐 그대로 바닐라로 흘려보낸다.
	 * (1.20.1·1.21.11 InputUtil이 glfwSetKeyCallback + glfwSetCharModsCallback을 쓰는 것을 javap로 확인)
	 */
	public static boolean ensureTextCapture() {
		// 49-215차: GLFW 콜백 대신 KeyboardHandler.keyPress/charTyped 믹스인(KeyboardKeyMixin/KeyboardCharMixin)이
		// TextCapture로 먼저 보낸다 - 26.3(SDL)에도 그대로 된다. 여기선 "켜졌다"만 표시.
		if (!textCaptureInstalled) {
			textCaptureInstalled = true;
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 글자 입력 가로채기 설치(패널 검색창)");
		}
		return !textCaptureBroken;
	}

	public static boolean textCaptureInstalled() {
		return textCaptureInstalled;
	}

	/** 마지막 호출 이후 눌린 좌클릭 수를 돌려주고 0으로 리셋. */
	public static int drainLeftClicks() {
		return RAW_LEFT_CLICKS.getAndSet(0);
	}

	public static int drainRightClicks() {
		return RAW_RIGHT_CLICKS.getAndSet(0);
	}

	// ==================== 49-7차: 타이틀 화면 교체 ====================
	// ESC 일시정지와 같은 패턴: 바닐라 TitleScreen이 뜨는 순간 LunaTitleScreen으로 바꿔치기.
	// 정확히 TitleScreen 클래스일 때만(모드가 상속한 화면은 건드리지 않음), 실패 시 영구 비활성.
	private static boolean titleSwapBroken;
	private static Class<?> titleScreenClass;
	private static java.lang.reflect.Constructor<?> lunaTitleCtor;

	/** 49-151차: 바꿔 넣기가 연달아 안 먹은 횟수(3번이면 포기하고 로그). */
	private static int titleSwapFails;

	/**
	 * 49-151차(사용자: "맵에서 나가면 바닐라 타이틀이 뜨면서 버그"): 틱에서만 바꾸던 것을 화면이 그려질 때마다도 본다
	 * (틱 순서와 상관없이 바닐라 타이틀이 한 프레임이라도 그려지면 곧바로 Luna 타이틀로). 모드 초기화 때 한 번.
	 */
	public static void installTitleSwapHook() {
		try {
			registerScreenAfterRender((screen, ctx, mx, my) -> maybeSwapTitleScreen(Minecraft.getInstance()));
		} catch (Throwable t) {
			warnOnce("titleSwapHook", t);
		}
	}

	public static void maybeSwapTitleScreen(Minecraft client) {
		// 49-151차: 월드가 남아 있어도(나가는 도중) 바닐라 타이틀이 떠 있으면 바꾼다 - 월드 조건 때문에 놓치던 경우 방지.
		if (titleSwapBroken || client == null) {
			return;
		}
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (screen == null) {
			return;
		}
		try {
			if (titleScreenClass == null) {
				titleScreenClass = classForName("net.minecraft.client.gui.screens.TitleScreen");
			}
			if (titleScreenClass != screen.getClass()) {
				return;
			}
			if (lunaTitleCtor == null) {
				Class<?> ours = Class.forName("kr.lunaslight.mod.gui.LunaTitleScreen");
				lunaTitleCtor = ours.getConstructor(net.minecraft.client.gui.screens.Screen.class);
			}
			setScreen(lunaTitleCtor.newInstance(screen));
			if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) == screen && ++titleSwapFails >= 3) {
				titleSwapBroken = true;
				kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 타이틀 화면 바꾸기가 계속 실패해서 멈춤");
			}
		} catch (Throwable t) {
			warnOnce("titleSwap", t);
			titleSwapBroken = true;
		}
	}

	/**
	 * 바닐라 화면을 클래스 이름으로 열기 - (Screen parent) 생성자(SelectWorldScreen/MultiplayerScreen은
	 * 1.20~1.21.11 전부 이 시그니처, 매핑으로 확인). 실패 시 false(호출측이 폴백).
	 */
	public static boolean openVanillaScreenByName(String className, net.minecraft.client.gui.screens.Screen parent) {
		try {
			Class<?> cls = classForName(className);
			Object screen = cls.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(parent);
			setScreen(screen);
			return true;
		} catch (Throwable t) {
			warnOnce("openScreen:" + className, t);
			return false;
		}
	}

	/**
	 * 49-17차: 통계 화면처럼 (Screen, X) 생성자를 쓰는 바닐라 화면 열기. 인자 개수/타입이 맞는
	 * 생성자를 찾아 부른다(StatsScreen(Screen, StatHandler) - 1.15~1.21.11 동일 구조, 매핑 실측).
	 */
	public static boolean openVanillaScreenWithArg(String className, net.minecraft.client.gui.screens.Screen parent,
			Object extra) {
		try {
			Class<?> cls = classForName(className);
			for (java.lang.reflect.Constructor<?> ctor : cls.getConstructors()) {
				Class<?>[] p = ctor.getParameterTypes();
				if (p.length == 2 && p[0].isAssignableFrom(net.minecraft.client.gui.screens.Screen.class)
					&& extra != null && p[1].isInstance(extra)) {
					setScreen(ctor.newInstance(parent, extra));
					return true;
				}
			}
		} catch (Throwable t) {
			warnOnce("openScreenArg:" + className, t);
		}
		return openVanillaScreenByName(className, parent);
	}

	/** 지금 싱글플레이(통합 서버) 중인지 - "랜 서버 열기" 버튼 노출 판단용. */
	public static boolean isSinglePlayer(Minecraft client) {
		try {
			Object v = callNoArg(client, "hasSingleplayerServer");
			return v instanceof Boolean b && b;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 플레이어 통계 핸들러(StatsScreen 생성자 인자). 실패 시 null. */
	public static Object statHandler(Minecraft client) {
		try {
			return client.player == null ? null : callNoArg(client.player, "getStats");
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * 49-27차: 서버 주소로 바로 접속(소셜 친구 [참가]). 버전마다 진입점이 달라 순서대로 시도한다.
	 *  ① ConnectScreen.connect(Screen, MinecraftClient, ServerAddress, ServerInfo, …) 정적 메서드(1.19+)
	 *  ② new ConnectScreen(Screen, MinecraftClient, ServerInfo) 화면(1.15~1.18)
	 * 둘 다 없으면 false(호출측이 주소 복사 안내).
	 */
	public static boolean joinServer(Minecraft client, String address, String label) {
		if (client == null || address == null || address.isEmpty()) {
			return false;
		}
		try {
			Class<?> connectScreen = classOrNull("net.minecraft.client.gui.screens.ConnectScreen");
			if (connectScreen == null) {
				connectScreen = classOrNull("net.minecraft.client.gui.screen.ConnectScreen");
			}
			Class<?> serverAddressClass = classOrNull("net.minecraft.client.multiplayer.resolver.ServerAddress");
			Class<?> serverInfoClass = classOrNull("net.minecraft.client.multiplayer.ServerData");
			if (connectScreen == null || serverAddressClass == null || serverInfoClass == null) {
				return false;
			}
			Object serverAddress = null;
			for (Method m : serverAddressClass.getMethods()) {
				if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
						&& m.getParameterTypes()[0] == String.class && nameMatches(serverAddressClass, "parse", m.getName())) {
					serverAddress = m.invoke(null, address);
					break;
				}
			}
			Object serverInfo = newServerInfo(serverInfoClass, label == null || label.isEmpty() ? address : label, address);
			Object parent = kr.lunaslight.mod.util.LunaCompat.screenOf(client);

			// ① 정적 connect
			for (Method m : connectScreen.getMethods()) {
				if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || !nameMatches(connectScreen, "connect", m.getName())) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				if (p.length < 4 || !p[0].isAssignableFrom(net.minecraft.client.gui.screens.Screen.class)
						|| !p[1].isInstance(client) || serverAddress == null || !p[2].isInstance(serverAddress)
						|| !p[3].isInstance(serverInfo)) {
					continue;
				}
				Object[] args = new Object[p.length];
				args[0] = parent;
				args[1] = client;
				args[2] = serverAddress;
				args[3] = serverInfo;
				for (int i = 4; i < p.length; i++) {
					args[i] = p[i] == boolean.class ? Boolean.FALSE : null;
				}
				m.invoke(null, args);
				return true;
			}
			// ② 화면 생성자
			for (java.lang.reflect.Constructor<?> c : connectScreen.getConstructors()) {
				Class<?>[] p = c.getParameterTypes();
				if (p.length == 3 && p[0].isAssignableFrom(net.minecraft.client.gui.screens.Screen.class)
						&& p[1].isInstance(client) && p[2].isInstance(serverInfo)) {
					Object screen = c.newInstance(parent, client, serverInfo);
					setScreen(screen); // 49-36차: 1.16은 openScreen - 리플렉션 헬퍼로
					return true;
				}
			}
		} catch (Throwable t) {
			warnOnce("joinServer", t);
		}
		return false;
	}

	/** ServerInfo(이름, 주소, local/ServerType) - 시대별 생성자. */
	private static Object newServerInfo(Class<?> cls, String name, String address) {
		for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length != 3 || p[0] != String.class || p[1] != String.class) {
				continue;
			}
			try {
				if (p[2] == boolean.class) {
					return c.newInstance(name, address, false);
				}
				if (p[2].isEnum()) {
					Object other = null;
					for (Object v : p[2].getEnumConstants()) {
						if (v instanceof Enum<?> e && "OTHER".equals(e.name())) {
							other = v;
							break;
						}
					}
					return c.newInstance(name, address, other);
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/** 49-27차: 채팅창을 미리 채운 채로 열기(귓속말). 1.21.11은 (String, boolean) 생성자. */
	public static boolean openChatWith(Minecraft client, String prefill) {
		try {
			Class<?> cls = classForName("net.minecraft.client.gui.screens.ChatScreen");
			for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
				Class<?>[] p = c.getParameterTypes();
				if (p.length == 1 && p[0] == String.class) {
					setScreen(c.newInstance(prefill));
					return true;
				}
				if (p.length == 2 && p[0] == String.class && p[1] == boolean.class) {
					setScreen(c.newInstance(prefill, false));
					return true;
				}
			}
		} catch (Throwable t) {
			warnOnce("openChatWith", t);
		}
		return false;
	}

	/** 49-27차: 클립보드에 복사(참가 실패 시 주소 안내용). */
	public static void copyToClipboard(Minecraft client, String text) {
		try {
			Object keyboard = callNoArg(client, "getKeyboard");
			if (keyboard != null) {
				for (Method m : keyboard.getClass().getMethods()) {
					if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class
							&& nameMatches(keyboard.getClass(), "setClipboard", m.getName())) {
						m.invoke(keyboard, text);
						return;
					}
				}
			}
		} catch (Throwable t) {
			warnOnce("clipboard", t);
		}
	}

	/** 49-27차: 버리기(기본 Q) 키가 눌려 있는지 - 줄어든 개수를 "사용"과 "버림"으로 나누는 데 씀. */
	public static boolean isDropKeyPressed(Minecraft client) {
		try {
			Object options = client.options;
			java.lang.reflect.Field f = findField(options.getClass(), "keyDrop");
			if (f == null) {
				return false;
			}
			f.setAccessible(true);
			Object binding = f.get(options);
			Object pressed = callNoArg(binding, "isDown");
			return pressed instanceof Boolean b && b;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 49-27차: BlockState → 블록 등록 id("minecraft:stone"). 못 읽으면 null. */
	public static String blockId(Object state) {
		try {
			Object block = callNoArg(state, "getBlock");
			if (block == null) {
				return null;
			}
			Class<?> registriesClass = classOrNull("net.minecraft.core.registries.BuiltInRegistries");
			if (registriesClass == null) {
				registriesClass = classOrNull("net.minecraft.util.registry.Registry");
			}
			if (registriesClass != null) {
				java.lang.reflect.Field f = findField(registriesClass, "BLOCK");
				if (f != null) {
					Object registry = f.get(null);
					Object id = registryKeyOf(registry, block);
					if (id != null) {
						return id.toString();
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** 49-27차: BlockState → 표시 이름("돌"). 못 읽으면 id. */
	public static String blockName(Object state) {
		try {
			Object block = callNoArg(state, "getBlock");
			Object name = callNoArg(block, "getName");
			if (name instanceof Component t) {
				return t.getString();
			}
		} catch (Throwable ignored) {
		}
		return blockId(state);
	}

	/** 49-27차: 공격(좌클릭) 키가 눌려 있는지 - 통계의 "내가 때린 대상" 추적용. */
	public static boolean isAttackPressed(Minecraft client) {
		try {
			if (client.getWindow() == null) {
				return false;
			}
			return LunaInput.mouseDown(client, 0);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 49-27차: 엔티티 종류 id("minecraft:zombie"). 못 읽으면 클래스 이름. */
	public static String entityTypeId(Object entity) {
		try {
			Object type = callNoArg(entity, "getType");
			Class<?> registriesClass = classOrNull("net.minecraft.core.registries.BuiltInRegistries");
			if (registriesClass == null) {
				registriesClass = classOrNull("net.minecraft.util.registry.Registry");
			}
			if (registriesClass != null && type != null) {
				java.lang.reflect.Field f = findField(registriesClass, "ENTITY_TYPE");
				if (f != null) {
					Object registry = f.get(null);
					Object id = registryKeyOf(registry, type);
					if (id != null) {
						return id.toString();
					}
				}
			}
			if (type != null) {
				Object name = callNoArg(type, "getName");
				if (name instanceof Component t) {
					return t.getString();
				}
			}
		} catch (Throwable ignored) {
		}
		return entity == null ? "unknown" : entity.getClass().getSimpleName();
	}

	/**
	 * 49-230차(사용자: "통계 캡처 1.21.11 이하도 다 되게"): 화면을 직접 읽는 길({@link #captureFramebufferAsync})이 안 되는 버전/환경을
	 * 위한 두 번째 길 - 바닐라 스크린샷 저장(F2와 같은 코드)에 <b>파일 이름을 정해서</b> 맡긴다. 결과는 게임 폴더/screenshots/fileName.
	 * 시그니처: (File, String, 화면, Consumer) ~1.21.7 / (File, String, 화면, int, Consumer) 1.21.8+ · 26.x. 부르기에 성공하면 true.
	 */
	public static boolean saveScreenshotNamed(Minecraft client, String fileName) {
		if (client == null || fileName == null) {
			return false;
		}
		try {
			Object framebuffer = mainRenderTarget(client);
			Class<?> rc = resolveClass("net.minecraft.client.Screenshot", "net.minecraft.client.util.ScreenshotUtils");
			if (rc == null || framebuffer == null) {
				return false;
			}
			File gameDir = client.gameDirectory;
			java.util.function.Consumer<Object> quiet = msg -> { };
			for (Method m : rc.getMethods()) {
				if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
					continue;
				}
				boolean named = false;
				for (String n : new String[]{"grab", "saveScreenshot"}) {
					named |= nameMatches(rc, n, m.getName()) || n.equals(m.getName());
				}
				Class<?>[] p = m.getParameterTypes();
				if (!named || p.length < 4 || p[0] != File.class || p[1] != String.class
						|| p[p.length - 1] != java.util.function.Consumer.class) {
					continue;
				}
				if (p.length == 4) {
					m.invoke(null, gameDir, fileName, framebuffer, quiet);
					return true;
				}
				if (p.length == 5 && p[3] == int.class) {
					m.invoke(null, gameDir, fileName, framebuffer, 1, quiet);
					return true;
				}
			}
		} catch (Throwable t) {
			warnOnce("saveScreenshotNamed", t);
		}
		return false;
	}

	/** 49-230차: 게임 폴더(스크린샷 폴더의 부모). */
	public static java.nio.file.Path gameDirPath(Minecraft client) {
		return client.gameDirectory.toPath();
	}

	/**
	 * 49-73차: 여기 있던 {@code saveScreenshotVanilla}(바닐라 스크린샷 저장을 빌려 쓰던 폴백)를 지웠다.
	 *
	 * <p>49-32차에 "통계 이미지가 안 나온다"를 고치려고 넣은 것인데, <b>최신 버전에서는 그 폴백도
	 * 틀렸다</b>: 1.21.8+의 {@code saveScreenshot}은 {@code (File, String, Framebuffer, int, Consumer)}라
	 * 우리 호출과 인자가 안 맞고, 그나마 맞는 3인자 판은 <b>파일 이름을 우리가 정할 수 없어</b>
	 * 저장된 파일을 luna-stats로 옮기는 일이 조용히 실패했다(화면에는 "저장했습니다"가 떴다).
	 *
	 * <p>이제는 {@link #captureFramebufferAsync} 하나로 전 버전이 해결되므로 폴백 자체가 필요 없다.
	 * <b>틀린 길을 남겨 두면 언젠가 또 그리로 간다.</b>
	 */
	public static boolean captureFramebufferAsync(Minecraft client,
			java.util.function.Consumer<NativeImage> sink) {
		if (client == null || sink == null) {
			return false;
		}
		try {
			// 49-229차(사용자: "통계가 높은 버전에서 캡처가 안 돼"): getFramebuffer는 26.x에 없다(26.1 Minecraft#getMainRenderTarget,
			// 26.2~ GameRenderer#mainRenderTarget) - 그래서 "이 버전에서는 화면을 이미지로 뜨지 못합니다"로 끝났다. 위 공용 함수로.
			Object framebuffer = mainRenderTarget(client);
			Class<?> recorderClass = resolveClass("net.minecraft.client.Screenshot",
					"net.minecraft.client.util.ScreenshotUtils");
			if (recorderClass == null || framebuffer == null) {
				return false;
			}
			Method oneArg = null;
			Method consumerArg = null;
			for (Method m : recorderClass.getMethods()) {
				if (!nameMatches(recorderClass, "takeScreenshot", m.getName())
						|| !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 1 && NativeImage.class.isAssignableFrom(m.getReturnType())) {
					oneArg = m;
				} else if (p.length == 2 && p[1] == java.util.function.Consumer.class) {
					consumerArg = m;
				}
			}
			if (oneArg != null) {
				Object result = oneArg.invoke(null, framebuffer);
				if (result instanceof NativeImage image) {
					sink.accept(image);
					return true;
				}
				return false;
			}
			if (consumerArg != null) {
				java.util.function.Consumer<Object> relay = value -> {
					if (value instanceof NativeImage image) {
						sink.accept(image);
					}
				};
				consumerArg.invoke(null, framebuffer, relay);
				return true;
			}
		} catch (Throwable t) {
			warnOnce("captureFramebufferAsync", t);
		}
		return false;
	}

	public static Object captureFramebuffer(Minecraft client) {
		try {
			// 49-229차(사용자: "통계가 높은 버전에서 캡처가 안 돼"): getFramebuffer는 26.x에 없다(26.1 Minecraft#getMainRenderTarget,
			// 26.2~ GameRenderer#mainRenderTarget) - 그래서 "이 버전에서는 화면을 이미지로 뜨지 못합니다"로 끝났다. 위 공용 함수로.
			Object framebuffer = mainRenderTarget(client);
			Class<?> recorderClass = resolveClass("net.minecraft.client.Screenshot",
					"net.minecraft.client.util.ScreenshotUtils");
			if (recorderClass == null || framebuffer == null) {
				return null;
			}
			for (Method m : recorderClass.getMethods()) {
				if (!nameMatches(recorderClass, "takeScreenshot", m.getName())
						|| !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
					continue;
				}
				Class<?>[] params = m.getParameterTypes();
				try {
					if (params.length == 1) {
						Object result = m.invoke(null, framebuffer);
						if (result != null) {
							return result;
						}
					} else if (params.length == 2) {
						Object[] holder = new Object[1];
						java.util.function.Consumer<Object> callback = img -> holder[0] = img;
						m.invoke(null, framebuffer, callback);
						if (holder[0] != null) {
							return holder[0];
						}
					}
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable t) {
			warnOnce("captureFramebuffer", t);
		}
		return null;
	}

	/** 49-27차: 지금 조준 중인 블록 위치(블록이 아니면 null). */
	/**
	 * 49-258차: 우클릭하면 블록이 놓일 자리 {x, y, z} = 바라보는 블록 + 바라보는 면 쪽 한 칸. 블록을 안 보고 있으면 null.
	 */
	public static int[] targetedPlacePos(Minecraft client) {
		try {
			BlockPos p = targetedBlock(client);
			if (p == null) {
				return null;
			}
			Object side = callNoArg(client.hitResult, "getDirection");
			if (side == null) {
				return null;
			}
			int dx = intOf(callNoArg(side, "getStepX")), dy = intOf(callNoArg(side, "getStepY")), dz = intOf(callNoArg(side, "getStepZ"));
			return new int[]{p.getX() + dx, p.getY() + dy, p.getZ() + dz};
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static int intOf(Object o) {
		return o instanceof Integer i ? i : 0;
	}

	public static BlockPos targetedBlock(Minecraft client) {
		try {
			Object hit = client.hitResult;
			if (hit == null) {
				return null;
			}
			Object type = callNoArg(hit, "getType");
			if (type instanceof Enum<?> e && !"BLOCK".equals(e.name())) {
				return null;
			}
			Object pos = callNoArg(hit, "getBlockPos");
			return pos instanceof BlockPos bp ? bp : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * 26.x: MinecraftClient.gui(Gui)는 화면/오버레이 관리자이고 실제 인게임 HUD(채팅·탭 목록·타이틀…)는
	 * Gui.hud(Hud)로 분리됐다. 그 전 버전은 gui 자체가 HUD. 채팅창 등을 찾을 땐 이걸로.
	 */
	public static Object inGameHud(Minecraft client) {
		Object gui = client.gui;
		Object hud = getFieldValue(gui, "hud");
		return hud != null ? hud : gui;
	}

	/** 49-27차: 내 채팅창에만 보이는 안내 줄(서버로 전송하지 않음). */
	public static void printLocalMessage(Minecraft client, String text) {
		try {
			Object hud = inGameHud(client);
			Object chat = callNoArg(hud, "getChatHud");
			if (chat == null) {
				return;
			}
			Component msg = textLiteral(text);
			for (Method m : chat.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && nameMatches(chat.getClass(), "addMessage", m.getName())
						&& m.getParameterTypes()[0].isInstance(msg)) {
					m.invoke(chat, msg);
					return;
				}
			}
		} catch (Throwable t) {
			warnOnce("printLocalMessage", t);
		}
	}

	/**
	 * 49-32차: 싱글플레이 세이브 폴더 이름(saves/ 아래 폴더). 서버면 null.
	 * 월드 이름은 여러 개가 겹칠 수 있어서, 맵을 구분하는 열쇠로는 이걸 쓴다.
	 */
	public static String currentWorldFolder(Minecraft client) {
		try {
			if (!isSinglePlayer(client)) {
				return null;
			}
			Object server = callNoArg(client, "getSingleplayerServer");
			Object session = server == null ? null : getFieldValue(server, "session", "storageSource");
			if (session == null) {
				return null;
			}
			Object name = callNoArg(session, "getLevelId");
			if (name == null) {
				name = callNoArg(session, "getLevelId");
			}
			if (name instanceof String str && !str.isEmpty()) {
				return str;
			}
		} catch (Throwable ignored) {
			// 아래에서 null
		}
		return null;
	}

	/** 49-32차: saves/&lt;폴더&gt; 가 아직 있는지(지워진 맵 표시용). */
	public static boolean worldFolderExists(Minecraft client, String folder) {
		try {
			if (client == null || folder == null || folder.isEmpty()) {
				return false;
			}
			return java.nio.file.Files.isDirectory(
					client.gameDirectory.toPath().resolve("saves").resolve(folder));
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 접속 중인 서버 주소(싱글이면 월드 이름, 실패 시 null). */
	public static String currentServerLabel(Minecraft client) {
		try {
			if (isSinglePlayer(client)) {
				Object server = callNoArg(client, "getSingleplayerServer");
				if (server != null) {
					Object name = callNoArg(server, "getWorldData");
					Object lvl = name == null ? null : callNoArg(name, "getLevelName");
					if (lvl instanceof String str) {
						return str;
					}
				}
				return "싱글플레이";
			}
			Object entry = callNoArg(client, "getCurrentServer");
			if (entry != null) {
				Object addr = getFieldCompat(entry.getClass(), "address").get(entry);
				if (addr instanceof String str) {
					return str;
				}
			}
		} catch (Throwable ignored) {
			// 아래 폴백
		}
		return null;
	}

	/**
	 * 49-48차: 내 화면의 비·천둥 게이지를 덮어쓴다(World#setRainGradient/setThunderGradient -
	 * 1.15.2~26.2 이름 동일). 클라이언트 월드에만 쓰므로 서버로 나가는 건 아무것도 없다.
	 */
	public static void setWeatherGradients(Object world, float rain, float thunder) {
		if (world == null) {
			return;
		}
		try {
			for (Method m : world.getClass().getMethods()) {
				if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != float.class) {
					continue;
				}
				if (nameMatches(world.getClass(), "setRainLevel", m.getName())) {
					m.invoke(world, rain);
				} else if (nameMatches(world.getClass(), "setThunderLevel", m.getName())) {
					m.invoke(world, thunder);
				}
			}
		} catch (Throwable t) {
			warnOnce("weather:gradient", t);
		}
	}

	/**
	 * 49-48차: 접속한 서버의 주소(host:port). 싱글/실패면 null. currentServerLabel과 달리 <b>항상 주소</b>다.
	 */
	public static String currentServerAddress(Minecraft client) {
		try {
			if (client == null || isSinglePlayer(client)) {
				return null;
			}
			Object entry = callNoArg(client, "getCurrentServer");
			if (entry != null) {
				Object addr = getFieldCompat(entry.getClass(), "address").get(entry);
				if (addr instanceof String str && !str.isEmpty()) {
					return str;
				}
			}
		} catch (Throwable ignored) {
		}
		// 49-263차: 서버 목록 항목 없이 바로 접속하면(옛 --server 실행 등) 항목이 비어 있다 - 실제 연결 주소로
		try {
			Object handler = callNoArg(client, "getConnection");
			Object conn = handler == null ? null : callNoArg(handler, "getConnection");
			Object sa = conn == null ? null : callNoArg(conn, "getRemoteAddress");
			if (sa instanceof java.net.InetSocketAddress isa) {
				String host = isa.getHostString();
				if (host != null && !host.isEmpty()) {
					return isa.getPort() == 25565 ? host : host + ":" + isa.getPort();
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/**
	 * 49-256차(사용자: "서버 주소 기능에 서버 이름이 잘 안 나와"): 화면에 보일 서버 이름. 지금 접속 항목의 이름이 비었거나, 바닐라 기본 이름
	 * ("Minecraft 서버" - 런처의 바로 접속/직접 연결이 붙이는 이름)이거나, 주소와 같거나, 런처가 붙인 "Nova …"면 서버 목록(servers.dat)에서
	 * 같은 주소로 저장된 이름을 찾는다. 그래도 없으면 null(주소만 보이게).
	 */
	public static String currentServerDisplayName(Minecraft client) {
		String addr = currentServerAddress(client);
		String name = currentServerName(client);
		if (name != null && !genericServerName(name, addr)) {
			return name;
		}
		String saved = savedServerName(client, addr);
		return saved != null && !genericServerName(saved, addr) ? saved : null;
	}

	private static boolean genericServerName(String name, String addr) {
		String n = name.trim();
		if (n.isEmpty() || n.startsWith("Nova ") || (addr != null && n.equalsIgnoreCase(addr.trim()))) {
			return true;
		}
		if (n.equalsIgnoreCase("Minecraft Server") || n.equals("Minecraft 서버") || n.equals("마인크래프트 서버")) {
			return true;
		}
		String def = translate("selectServer.defaultName");
		return def != null && !def.equals("selectServer.defaultName") && n.equals(def);
	}

	private static String savedNameAddr;
	private static String savedNameValue;
	private static long savedNameAt;

	/** 서버 목록(servers.dat)에서 주소가 addr인 항목의 이름. 30초 캐시. 못 찾으면 null. */
	private static String savedServerName(Minecraft client, String addr) {
		if (client == null || addr == null || addr.isEmpty()) {
			return null;
		}
		long now = System.currentTimeMillis();
		if (addr.equals(savedNameAddr) && now - savedNameAt < 30_000L) {
			return savedNameValue;
		}
		savedNameAddr = addr;
		savedNameAt = now;
		savedNameValue = null;
		try {
			Class<?> cls = null;
			for (String cn : new String[]{"net.minecraft.client.multiplayer.ServerList"}) {
				cls = classOrNull(cn);
				if (cls != null) {
					break;
				}
			}
			if (cls == null) {
				return null;
			}
			Object list = null;
			for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
				if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isInstance(client)) {
					list = c.newInstance(client);
					break;
				}
			}
			if (list == null) {
				return null;
			}
			callNoArg(list, "load");
			Object sz = callNoArg(list, "size");
			int n = sz instanceof Integer szi ? szi : 0;
			String want = normAddr(addr);
			Method get = null;
			for (Method m : list.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class && nameMatches(list.getClass(), "get", m.getName())) {
					get = m;
					break;
				}
			}
			if (get == null) {
				return null;
			}
			for (int i = 0; i < n; i++) {
				Object info = get.invoke(list, i);
				if (info == null) {
					continue;
				}
				Object a = getFieldCompat(info.getClass(), "ip").get(info);
				Object nm = getFieldCompat(info.getClass(), "name").get(info);
				if (a instanceof String as && normAddr(as).equals(want) && nm instanceof String ns && !ns.isEmpty()) {
					savedNameValue = ns;
					break;
				}
			}
		} catch (Throwable t) {
			warnOnce("serverList:name", t);
		}
		return savedNameValue;
	}

	private static String normAddr(String a) {
		String s = a.trim().toLowerCase(java.util.Locale.ROOT);
		if (s.endsWith(":25565")) {
			s = s.substring(0, s.length() - 6);
		}
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}

	/** 49-48차: 서버 목록에 적어 둔 이름(없으면 null). */
	public static String currentServerName(Minecraft client) {
		try {
			if (client == null || isSinglePlayer(client)) {
				return null;
			}
			Object entry = callNoArg(client, "getCurrentServer");
			if (entry != null) {
				Object name = getFieldCompat(entry.getClass(), "name").get(entry);
				if (name instanceof String str && !str.isEmpty()) {
					return str;
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/**
	 * 49-48차: 지금 서 있는 자리의 밝기(F3 표시용). {@code int[]{합친 밝기, 하늘, 블록}} - 못 읽으면 null.
	 * World#getLightLevel(LightType, BlockPos)는 1.15.2~26.2 이름이 같다.
	 */
	public static int[] lightLevelsAt(Minecraft client) {
		try {
			if (client == null || client.level == null || client.player == null) {
				return null;
			}
			Object pos = callNoArg(client.player, "blockPosition");
			if (pos == null) {
				return null;
			}
			Class<?> lightType = classForName("net.minecraft.world.level.LightLayer");
			Object sky = null;
			Object block = null;
			for (Object c : lightType.getEnumConstants()) {
				if (enumNameIs(c, "SKY")) {
					sky = c;
				} else if (enumNameIs(c, "BLOCK")) {
					block = c;
				}
			}
			Method m = null;
			for (Method cand : client.level.getClass().getMethods()) {
				if (!nameMatches(client.level.getClass(), "getLightLevel", cand.getName())) {
					continue;
				}
				Class<?>[] p = cand.getParameterTypes();
				if (p.length == 2 && p[0] == lightType && p[1].isInstance(pos)) {
					m = cand;
					break;
				}
			}
			if (m == null || sky == null || block == null) {
				return null;
			}
			int s = ((Number) m.invoke(client.level, sky, pos)).intValue();
			int b = ((Number) m.invoke(client.level, block, pos)).intValue();
			return new int[]{Math.max(s, b), s, b};
		} catch (Throwable t) {
			warnOnce("lightLevel", t);
			return null;
		}
	}

	/**
	 * Screen.renderPanoramaBackground(ctx, delta) - 1.20.5+에만 존재하는 protected 메서드라
	 * getMethod(공개 전용)로는 못 찾음 → 슈퍼클래스를 직접 걸어 올라가며 getDeclaredMethod.
	 * 성공 여부 반환(실패 시 호출측이 그라데이션 배경 폴백).
	 */
	public static boolean renderPanorama(Object screen, Object drawContext, float delta) {
		try {
			Class<?> c = screen.getClass();
			while (c != null && c != Object.class) {
				for (String actual : memberNameCandidates(c, "extractPanorama")) {
					try {
						Method m = c.getDeclaredMethod(actual,
								net.minecraft.client.gui.GuiGraphicsExtractor.class, float.class);
						m.setAccessible(true);
						m.invoke(screen, drawContext, delta);
						return true;
					} catch (NoSuchMethodException ignored) {
					}
				}
				c = c.getSuperclass();
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** 액션바(핫바 위) 한 줄 메시지. sendMessage(Text,boolean overlay) - 실패 시 조용히 무시. */
	public static void sendActionBar(Minecraft client, String message) {
		try {
			Object player = client.player;
			if (player == null) {
				return;
			}
			Object text = literalTextSafe(message);
			if (text == null) {
				return;
			}
			Method m = getMethodCompat(player.getClass(), "sendMessage", Component.class, boolean.class);
			m.invoke(player, text, true);
		} catch (Throwable ignored) {
		}
	}

	/** F1(HUD 숨김) 상태인지. 판별 실패 시 false. */
	/**
	 * 49-34차(사용자: "HUD 편집기 내 조준점이랑 중앙이 안맞아"): 바닐라 십자선의 **가운데 픽셀**.
	 * 바닐라는 15×15 십자를 (w-15)/2 에 그리므로 가운데 픽셀은 (w-15)/2 + 7 -
	 * 화면 폭이 홀수면 w/2와 같지만 짝수면 w/2 - 1 이다. 지금까지 우리 십자선·편집기 안내선이
	 * 전부 w/2를 써서 짝수 폭에서 1px 오른쪽·아래로 어긋났다. 십자와 맞춰야 하는 곳은 전부 이걸 쓴다.
	 */
	public static int crosshairCenterX(Minecraft client) {
		return (client.getWindow().getGuiScaledWidth() - 15) / 2 + 7;
	}

	public static int crosshairCenterY(Minecraft client) {
		return (client.getWindow().getGuiScaledHeight() - 15) / 2 + 7;
	}

	public static boolean isHudHidden(Minecraft client) {
		try {
			// 26.x: GameOptions.hudHidden이 사라지고 Hud#isHidden()(F1)으로 옮겨감.
			Object hud = inGameHud(client);
			if (hud != null && hud != client.gui) {
				Object r = callNoArg(hud, "isHidden");
				if (r instanceof Boolean b) {
					return b;
				}
			}
			java.lang.reflect.Field f = findField(client.options.getClass(), "hudHidden");
			return f != null && f.getBoolean(client.options);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * 클라이언트 월드의 "하루 시간"만 바꿈(하늘/밝기 표시용, 서버 전송 없음).
	 * setTimeOfDay(long)(~1.21.8) → setTime(long,long,boolean)(1.21.9+) 순서로 시도.
	 */
	public static boolean setClientTimeOfDay(Object world, long timeOfDay) {
		if (world == null) {
			return false;
		}
		if (classOrNull("net.minecraft.client.ClientClockManager") != null) {
			// 26.x: 시계 레지스트리 방식 - ClockTimeMixin이 읽는 정적 값만 바꾼다.
			ClockHook.override = timeOfDay;
			return true;
		}
		try {
			Method m = getMethodCompat(world.getClass(), "setTimeOfDay", long.class);
			m.invoke(world, timeOfDay);
			return true;
		} catch (Throwable ignored) {
		}
		try {
			Object cur = getMethodCompat(world.getClass(), "getTime").invoke(world);
			long worldTime = cur instanceof Number n ? n.longValue() : 0L;
			Method m = getMethodCompat(world.getClass(), "setGameTime", long.class, long.class, boolean.class);
			m.invoke(world, worldTime, timeOfDay, false);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 시간 고정 해제(26.x 시계 덮어쓰기 끄기). 구버전은 서버가 다음 틱에 실제 시간을 다시 보내므로 할 일 없음. */
	public static void clearClientTimeOfDay() {
		ClockHook.override = -1;
	}

	/** 서바이벌 상태바(배고픔 등)가 실제로 그려지는 상태인지(F1/크리에이티브 제외). 판별 실패 시 true. */
	public static boolean shouldShowSurvivalHud(Minecraft client) {
		if (isHudHidden(client)) {
			return false;
		}
		try {
			Object im = client.gameMode;
			if (im != null) {
				Method m = findNoArgMethod(im.getClass(), "canHurtPlayer");
				if (m != null) {
					Object r = m.invoke(im);
					if (r instanceof Boolean b) {
						return b;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return true;
	}

	// ==================== 49차: ESC 일시정지 메뉴 교체 ====================
	// 바닐라 GameMenuScreen(버튼 있는 일반 일시정지)이 열리면 같은 틱에 LunaPauseScreen으로 교체.
	// gui 패키지가 빠진 서브프로젝트(1.16~1.19.4)에서는 클래스를 못 찾아 조용히 비활성.
	private static boolean pauseSwapBroken;
	private static Class<?> gameMenuScreenClass;
	private static java.lang.reflect.Constructor<?> lunaPauseCtor;

	public static void maybeSwapPauseMenu(Minecraft client) {
		if (pauseSwapBroken || client == null || client.level == null) {
			return;
		}
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (screen == null) {
			return;
		}
		try {
			if (gameMenuScreenClass == null) {
				gameMenuScreenClass = classForName("net.minecraft.client.gui.screens.PauseScreen");
			}
			if (!gameMenuScreenClass.isInstance(screen) || gameMenuScreenClass != screen.getClass()) {
				return; // 정확히 바닐라 일시정지만(다른 모드가 상속한 화면은 건드리지 않음)
			}
			// F3+ESC(버튼 없는 일시정지)는 그대로 둠
			try {
				java.lang.reflect.Field f = getFieldCompat(gameMenuScreenClass, "showPauseMenu");
				f.setAccessible(true);
				if (!f.getBoolean(screen)) {
					return;
				}
			} catch (Throwable ignored) {
				// 필드가 없는 버전(1.20.1 등)은 항상 버튼 있는 메뉴로 취급
			}
			if (lunaPauseCtor == null) {
				Class<?> ours = Class.forName("kr.lunaslight.mod.gui.LunaPauseScreen");
				lunaPauseCtor = ours.getConstructor(net.minecraft.client.gui.screens.Screen.class);
			}
			setScreen(lunaPauseCtor.newInstance(screen));
		} catch (Throwable t) {
			warnOnce("pauseSwap", t);
			pauseSwapBroken = true;
		}
	}

	private static DeltaTracker toRenderTickCounter(Object raw) {
		if (raw instanceof DeltaTracker rtc) {
			return rtc;
		}
		// 구버전(1.20.1/1.20.4): HudRenderCallback이 float tickDelta를 직접 줌.
		// 같은 이름의 구형 RenderTickCounter 클래스(생성자 기반, getTickDelta 없음)를
		// 최선을 다해 하나 만들어서 넘김(완벽히 정확한 보간값은 아닐 수 있으나 비치명적).
		try {
			return DeltaTracker.class.getConstructor(float.class, long.class)
					.newInstance(20f, System.currentTimeMillis());
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** tickCounter.getTickDelta(true) 호출 - 구버전엔 이 메서드가 없어 필드로 폴백, 그래도 없으면 1.0f. */
	public static float getTickDelta(Object tickCounter) {
		if (tickCounter == null) {
			return 1f;
		}
		try {
			Method m = getMethodCompat(tickCounter.getClass(), "getTickDelta", boolean.class);
			Object r = m.invoke(tickCounter, true);
			if (r instanceof Float f) {
				return f;
			}
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(tickCounter.getClass(), "tickDelta");
			f.setAccessible(true);
			Object v = f.get(tickCounter);
			if (v instanceof Float fl) {
				return fl;
			}
		} catch (Throwable ignored) {
		}
		return 1f;
	}

	// ==================== WorldRenderEvents ====================
	// 19차 당시: 1.21.9 포팅에서 net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents가
	// 업스트림 자체에서 통째로 제거됨(Fabric API GitHub 이슈 #4902) - 그때는 미재구현이라 판단해
	// 1.21.11에서 월드 렌더 의존 모듈 3개를 조용히 비활성시켰음.
	//
	// 20차: 다시 조사해보니 fabric-api 0.141.0+1.21.11부터 같은 기능이
	// net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents 로 "패키지만 옮겨서"
	// 재구현되어 있었음(gradle.properties의 1.21.11 fabric_version=0.141.3+1.21.11이 이미 이 버전
	// 이후라 별도 버전업 불필요). 다만 이벤트 이름 자체가 바뀜(더 이상 LAST가 없고 END_MAIN /
	// AFTER_ENTITIES / BEFORE_TRANSLUCENT 등 세분화된 상수들로 재편) - 구/신 패키지 둘 다
	// 순서대로 시도해서 존재하는 쪽으로 등록. (신 패키지의 각 상수는 구버전과 동일하게
	// "이름을 PascalCase로 바꾼 중첩 인터페이스"가 리스너 타입이라고 가정 - 예: END_MAIN ->
	// WorldRenderEvents$EndMain. 실제 Fabric 문서/자바독으로 존재를 확인했으나 나중에 또
	// 이름이 바뀔 수 있으니(문서에 "26.2"에서 또 한 번 개편 예정이라는 언급 있음) 후보를
	// 여러 개 순서대로 시도하고, 전부 실패하면 그냥 조용히 비활성 - 컴파일/실행엔 영향 없음.)
	private static final String[] NEW_WORLD_RENDER_EVENT_CANDIDATES =
			{"END_MAIN", "AFTER_ENTITIES", "BEFORE_TRANSLUCENT", "BEFORE_DEBUG_RENDER"};

	private static boolean worldRenderRegistered;

	/** 49-22차: 월드 렌더 이벤트가 이 버전에서 등록됐는지(안 됐으면 레이저 등은 HUD 투영으로 폴백). */
	public static boolean worldRenderAvailable() {
		return worldRenderRegistered;
	}

	public static void registerWorldRenderLast(Consumer<Object> handler) {
		// 26.x: 월드 렌더가 SubmitNodeCollector 기반으로 완전히 바뀌어(LevelRenderEvents) 예전 VertexConsumer
		// 그리기가 통하지 않는다 → 이 트리에서는 등록하지 않고 전부 HUD 투영(LunaProjection)으로 그린다.
		if (classOrNull("net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents") != null) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 26.x: 월드 렌더 이벤트 대신 HUD 투영으로 그림");
			return;
		}
		if (tryRegisterOldWorldRenderEvents(handler)) {
			worldRenderRegistered = true;
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 월드 렌더 이벤트: WorldRenderEvents.LAST(구 API)");
			return;
		}
		for (String candidate : NEW_WORLD_RENDER_EVENT_CANDIDATES) {
			if (tryRegisterNewWorldRenderEvent(candidate, handler)) {
				worldRenderRegistered = true;
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 월드 렌더 이벤트: v1.world.WorldRenderEvents." + candidate);
				return;
			}
		}
		kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 월드 렌더 이벤트를 등록하지 못함 - 블록/엔티티 테두리 등 월드 렌더 기능 비활성");
		// 구/신 패키지 둘 다 없거나 시그니처가 또 바뀜 - 월드 렌더 의존 모듈은 이 버전에서
		// onWorldRender가 호출되지 않아 자동으로 비활성됨. 게임은 절대 죽지 않음.
	}

	private static boolean tryRegisterOldWorldRenderEvents(Consumer<Object> handler) {
		try {
			Class<?> eventsClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents");
			Class<?> listenerClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents$Last");
			Object proxy = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass},
					(InvocationHandler) (p, method, args) -> {
						handler.accept(args != null && args.length > 0 ? args[0] : null);
						return null;
					});
			Object lastEvent = getFieldCompat(eventsClass, "LAST").get(null);
			invokeRegister(lastEvent, proxy);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 1.21.11+: net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.<FIELD_NAME> 시도. */
	private static boolean tryRegisterNewWorldRenderEvent(String fieldName, Consumer<Object> handler) {
		try {
			Class<?> eventsClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents");
			Class<?> listenerClass = Class.forName(eventsClass.getName() + "$" + toPascalCase(fieldName));
			Object proxy = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass},
					(InvocationHandler) (p, method, args) -> {
						handler.accept(args != null && args.length > 0 ? args[0] : null);
						return null;
					});
			Object event = eventsClass.getField(fieldName).get(null);
			invokeRegister(event, proxy);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private static String toPascalCase(String screamingSnakeCase) {
		StringBuilder sb = new StringBuilder();
		for (String part : screamingSnakeCase.split("_")) {
			if (part.isEmpty()) continue;
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}

	/**
	 * 월드 렌더 콜백 context에서 카메라 위치를 뽑아낸다.
	 * 구버전: context.camera() -> Camera -> getPos().
	 * 신버전(1.21.11+, v1.world 패키지): context.worldState().cameraRenderState.pos
	 * (레코드/필드 혼용 가능성이 있어 resolveMember로 메서드/필드 둘 다 시도).
	 */
	// ==================== 49-16차: HUD 패스에서 쓸 카메라 상태 ====================
	// 크로스헤어 아웃라인의 "가려진 부분도 보이게"를 위해 화면 좌표 투영을 직접 계산한다.
	// 마크의 투영 행렬은 1.21.11에서 RenderSystem.getProjectionMatrix()가 사라져(GPU 버퍼로 이동)
	// 버전 호환 확보가 어렵다 → 카메라 위치/각도 + FOV만 받아 우리가 직접 투영(전 버전 동일 수식).

	/**
	 * 설정 화면의 시야각(FOV) 값. GameRendererFovMixin이 못 붙은 버전에서 아웃라인 투영이
	 * 조용히 멈추지 않도록 하는 폴백. 실패하면 70.
	 */
	public static double optionsFov(Minecraft client) {
		if (client == null || client.options == null) {
			return 70;
		}
		try {
			Object opt = callNoArg(client.options, "fov");   // 1.19+ SimpleOption<Integer>
			if (opt != null) {
				Object val = callNoArg(opt, "getValue");
				if (val instanceof Number n) {
					return n.doubleValue();
				}
			}
		} catch (Throwable ignored) {
			// 구버전: 필드 직접 읽기
		}
		try {
			Object val = getFieldCompat(client.options.getClass(), "fov").get(client.options);
			if (val instanceof Number n) {
				return n.doubleValue();
			}
		} catch (Throwable ignored) {
			// 폴백 실패
		}
		return 70;
	}

	// ==================== 49-76차(6-16): 불투명한 꽉 찬 블록인가(가려짐 판정용) ====================
	// 시대마다 이름이 갈린다(javap 실측):
	//   1.15.2          isOpaque() && isFullCube(BlockView, BlockPos)
	//   1.16 ~ 1.21.1   isOpaqueFullCube(BlockView, BlockPos)
	//   1.21.2+         isOpaqueFullCube()
	// 다른 스레드에서 불리므로 예외는 전부 "안 막힘"(= 그린다)으로 떨어뜨린다.
	private static Method opaqueFull2, opaqueFull0, opaque0, fullCube2;
	private static boolean opaqueResolved;

	public static boolean isOpaqueFullCube(Object world, BlockPos pos) {
		try {
			Object state = ((net.minecraft.world.level.BlockGetter) world).getBlockState(pos);
			if (state == null) {
				return false;
			}
			if (!opaqueResolved) {
				Class<?> c = state.getClass();
				opaqueFull2 = findMethod(c, "isSolidRender", net.minecraft.world.level.BlockGetter.class, BlockPos.class);
				opaqueFull0 = findMethod(c, "isSolidRender");
				opaque0 = findMethod(c, "isOpaque");
				fullCube2 = findMethod(c, "isCollisionShapeFullBlock", net.minecraft.world.level.BlockGetter.class, BlockPos.class);
				opaqueResolved = true;
			}
			if (opaqueFull2 != null) {
				return (Boolean) opaqueFull2.invoke(state, world, pos);
			}
			if (opaqueFull0 != null) {
				return (Boolean) opaqueFull0.invoke(state);
			}
			if (opaque0 != null && fullCube2 != null) {
				return (Boolean) opaque0.invoke(state) && (Boolean) fullCube2.invoke(state, world, pos);
			}
			return false;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** GameRenderer#getCamera(). 실패 시 null. */
	public static Object getCamera(Minecraft client) {
		if (client == null || client.gameRenderer == null) {
			return null;
		}
		try {
			return callNoArg(client.gameRenderer, "mainCamera");
		} catch (Throwable t) {
			warnOnce("getCamera", t);
			return null;
		}
	}

	/** Camera#getPos(). 실패하면 플레이어 눈높이로 폴백. */
	public static Vec3 cameraPos(Object camera, Minecraft client) {
		Vec3 pos = camera == null ? null : getPos(camera);
		if (pos != null) {
			return pos;
		}
		if (client != null && client.player != null) {
			return client.player.getEyePosition(1.0f);
		}
		return null;
	}

	private static float cameraAngle(Object camera, Minecraft client, String name, boolean pitch) {
		if (camera != null) {
			try {
				Object v = callNoArg(camera, name);
				if (v instanceof Float f) {
					return f;
				}
				if (v instanceof Number n) {
					return n.floatValue();
				}
			} catch (Throwable ignored) {
				// 아래 플레이어 폴백으로
			}
		}
		if (client != null && client.player != null) {
			// 49-36차: getPitch()/getYaw()는 1.17+ - 1.16 이하는 public 필드 pitch/yaw. 리플렉션으로 둘 다.
			Object v = callNoArg(client.player, pitch ? "getPitch" : "getYaw");
			if (v == null) {
				v = getFieldValue(client.player, pitch ? "pitch" : "yaw");
			}
			if (v instanceof Number n) {
				return n.floatValue();
			}
		}
		return 0f;
	}

	public static float cameraYaw(Object camera, Minecraft client) {
		return cameraAngle(camera, client, "getYaw", false);
	}

	public static float cameraPitch(Object camera, Minecraft client) {
		return cameraAngle(camera, client, "getPitch", true);
	}

	public static Vec3 getWorldRenderCameraPos(Object context) {
		if (context == null) {
			return null;
		}
		Object camera = callNoArg(context, "camera");
		if (camera != null) {
			Vec3 pos = getPos(camera);
			if (pos != null) {
				return pos;
			}
		}
		Object worldState = resolveMember(context, "worldState");
		Object cameraRenderState = resolveMember(worldState, "cameraRenderState");
		Object pos = resolveMember(cameraRenderState, "pos");
		if (pos instanceof Vec3 v) {
			return v;
		}
		warnOnce("getWorldRenderCameraPos", new IllegalStateException("camera()/worldState().cameraRenderState.pos 둘 다 실패: "
			+ context.getClass().getName()));
		return null;
	}

	/** 구버전: context.matrixStack(). 신버전(v1.world): context.matrices(). */
	public static PoseStack getMatrices(Object context) {
		Object m = callNoArg(context, "matrices");
		if (m instanceof PoseStack ms) {
			return ms;
		}
		m = callNoArg(context, "matrixStack");
		if (m instanceof PoseStack ms) {
			return ms;
		}
		warnOnce("getMatrices", new IllegalStateException("matrices()/matrixStack() 둘 다 실패: " + (context == null ? "null" : context.getClass().getName())));
		return null;
	}

	/** context.camera()/matrixStack()/consumers() 등 인자 없는 메서드를 이름으로 호출. */
	public static Object call(Object target, String methodName) {
		return callNoArg(target, methodName);
	}

	/**
	 * VertexConsumerProvider#getBuffer(선 렌더 레이어)를 리플렉션으로.
	 * 선 레이어: ≤1.21.10 RenderLayer.getLines() / 1.21.11+ RenderLayers.lines() (48차 실측).
	 */
	public static VertexConsumer getLineBuffer(Object vertexConsumerProvider) {
		if (vertexConsumerProvider == null) {
			return null;
		}
		try {
			Object lines;
			try {
				Class<?> renderLayerClass = classForName("net.minecraft.client.renderer.rendertype.RenderType");
				lines = getMethodCompat(renderLayerClass, "getLines").invoke(null);
			} catch (Throwable ignored) {
				Class<?> renderLayersClass = classForName("net.minecraft.client.renderer.rendertype.RenderTypes");
				lines = getMethodCompat(renderLayersClass, "lines").invoke(null);
			}
			return getBufferForLayer(vertexConsumerProvider, lines, "getLineBuffer");
		} catch (Throwable t) {
			warnOnce("getLineBuffer", t);
		}
		return null;
	}

	/**
	 * 48-2차: 번개(lightning) 레이어 버퍼 - POSITION_COLOR 반투명 가산혼합 쿼드. 아이템 빛기둥의
	 * "빛나는 기둥" 렌더용. ≤1.21.10 RenderLayer.getLightning() / 1.21.11+ RenderLayers.lightning().
	 */
	public static VertexConsumer getBeamBuffer(Object vertexConsumerProvider) {
		if (vertexConsumerProvider == null) {
			return null;
		}
		try {
			Object layer;
			try {
				Class<?> renderLayerClass = classForName("net.minecraft.client.renderer.rendertype.RenderType");
				layer = getMethodCompat(renderLayerClass, "getLightning").invoke(null);
			} catch (Throwable ignored) {
				Class<?> renderLayersClass = classForName("net.minecraft.client.renderer.rendertype.RenderTypes");
				layer = getMethodCompat(renderLayersClass, "lightning").invoke(null);
			}
			return getBufferForLayer(vertexConsumerProvider, layer, "getBeamBuffer");
		} catch (Throwable t) {
			warnOnce("getBeamBuffer", t);
		}
		return null;
	}

	private static Class<?> getBufferProviderClass;   // 49-22차: 매 프레임 getMethods() 순회 대신 한 번 찾아 캐시
	private static Method getBufferMethod;

	private static VertexConsumer getBufferForLayer(Object provider, Object layer, String warnKey) throws Exception {
		Class<?> pc = provider.getClass();
		if (getBufferProviderClass != pc) {
			Method found = null;
			for (Method m : pc.getMethods()) {
				if (m.getParameterCount() == 1 && nameMatches(pc, "getBuffer", m.getName())) {
					m.setAccessible(true);
					found = m;
					break;
				}
			}
			getBufferMethod = found;
			getBufferProviderClass = pc;
		}
		if (getBufferMethod != null) {
			Object buffer = getBufferMethod.invoke(provider, layer);
			if (buffer instanceof VertexConsumer vc) {
				return vc;
			}
		}
		warnOnce(warnKey, new IllegalStateException("getBuffer 메서드를 못 찾음: " + pc.getName()));
		return null;
	}

	// ==================== 월드 좌표 쿼드 방출(48-2차, 아이템 빛기둥용) ====================
	// VertexConsumer#vertex가 버전마다 다름: (Matrix4f,fff)(≤1.21.1, 이후 next() 필요할 수 있음) /
	// (MatrixStack.Entry,fff)(1.21.2+, next() 없음). 리플렉션으로 한 번 해석해서 캐시.
	private static Class<?> vertexBufferClass;
	private static Method vertexMethod;          // 4파라미터 vertex
	private static boolean vertexTakesEntry;     // p0가 MatrixStack.Entry인지(Matrix4f인지)
	private static Method vertexNextMethod;      // ≤1.20.x의 next() (없으면 null)
	private static Method entryMatrixMethod;     // MatrixStack.Entry -> 위치 행렬

	public static boolean emitVertex(PoseStack matrices, VertexConsumer buffer, float x, float y, float z,
			float r, float g, float b, float a) {
		try {
			Class<?> cls = buffer.getClass();
			if (vertexBufferClass != cls) {
				Method found = null;
				boolean entry = false;
				for (Method m : cls.getMethods()) {
					if (!nameMatches(cls, "vertex", m.getName()) || m.getParameterCount() != 4) {
						continue;
					}
					Class<?>[] p = m.getParameterTypes();
					if (p[1] != float.class || p[2] != float.class || p[3] != float.class) {
						continue;
					}
					if (p[0].isInstance(matrices.last())) {
						found = m;
						entry = true;
						break;
					}
					if (p[0].getName().endsWith("Matrix4f")) {
						found = m;
						entry = false;
						// Entry 판을 더 우선하고 싶으므로 break 안 함
					}
				}
				if (found == null) {
					warnOnce("emitVertex", new IllegalStateException("vertex(…,fff) 후보 없음: " + cls.getName()));
					return false;
				}
				Method next = null;
				try {
					for (Method m : cls.getMethods()) {
						if (nameMatches(cls, "next", m.getName()) && m.getParameterCount() == 0 && m.getReturnType() == void.class) {
							next = m;
							break;
						}
					}
				} catch (Throwable ignored) {
				}
				Method entryMatrix = null;
				if (!entry) {
					Object peek = matrices.last();
					for (String name : new String[]{"getPositionMatrix", "getModel"}) {
						try {
							entryMatrix = getMethodCompat(peek.getClass(), name);
							break;
						} catch (Throwable ignored) {
						}
					}
					if (entryMatrix == null) {
						warnOnce("emitVertex", new IllegalStateException("Entry에서 위치 행렬을 못 얻음"));
						return false;
					}
				}
				found.setAccessible(true);
				vertexMethod = found;
				vertexTakesEntry = entry;
				vertexNextMethod = next;
				entryMatrixMethod = entryMatrix;
				vertexBufferClass = cls;
			}
			Object first = vertexTakesEntry ? matrices.last() : entryMatrixMethod.invoke(matrices.last());
			vertexMethod.invoke(buffer, first, x, y, z);
			buffer.setColor(r, g, b, a);
			if (vertexNextMethod != null) {
				vertexNextMethod.invoke(buffer);
			}
			return true;
		} catch (Throwable t) {
			warnOnce("emitVertex", t);
			return false;
		}
	}

	/** 세로 사각형(카메라 기준 상대 좌표) 하나 - 아래는 진하고 위로 갈수록 투명해지는 그라데이션. 양면. */
	public static void emitBeamQuad(PoseStack matrices, VertexConsumer buffer,
			float x1, float z1, float x2, float z2, float yBottom, float yTop,
			float r, float g, float b, float aBottom, float aTop) {
		// 정면
		emitVertex(matrices, buffer, x1, yBottom, z1, r, g, b, aBottom);
		emitVertex(matrices, buffer, x2, yBottom, z2, r, g, b, aBottom);
		emitVertex(matrices, buffer, x2, yTop, z2, r, g, b, aTop);
		emitVertex(matrices, buffer, x1, yTop, z1, r, g, b, aTop);
		// 뒷면(반대 감김 - 컬링 대비)
		emitVertex(matrices, buffer, x1, yTop, z1, r, g, b, aTop);
		emitVertex(matrices, buffer, x2, yTop, z2, r, g, b, aTop);
		emitVertex(matrices, buffer, x2, yBottom, z2, r, g, b, aBottom);
		emitVertex(matrices, buffer, x1, yBottom, z1, r, g, b, aBottom);
	}

	/**
	 * 월드 좌표(카메라 기준으로 이미 보정된) Box의 테두리 선을 그림. 버전별 실제 API(48차 tiny 매핑 실측):
	 *   ≤1.21.1   WorldRenderer.drawBox(MatrixStack, VertexConsumer, Box, r,g,b,a)
	 *   1.21.2~8  VertexRendering.drawBox(MatrixStack, VertexConsumer, Box, r,g,b,a)
	 *   1.21.9~10 VertexRendering.drawBox(MatrixStack.Entry, VertexConsumer, Box, r,g,b,a)
	 *   1.21.11+  VertexRendering.drawOutline(MatrixStack, VertexConsumer, VoxelShape, x,y,z, argb, lineWidth)
	 */
	private static boolean drawBoxResolved;      // 49-22차: 한 번 해석해서 캐시(매 프레임 getMethods() 순회 제거)
	private static Method drawBoxMethod;         // drawBox(…) 또는 drawOutline(…)
	private static int drawBoxKind;              // 0 = 없음, 1 = drawBox(MatrixStack), 2 = drawBox(Entry), 3 = drawOutline(7), 4 = drawOutline(8)

	public static void drawBox(PoseStack matrices, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
		if (matrices == null || buffer == null || box == null) {
			return;
		}
		if (!drawBoxResolved) {
			drawBoxResolved = true;
			resolve:
			for (String className : new String[]{"net.minecraft.client.render.VertexRendering", "net.minecraft.client.renderer.LevelRenderer"}) {
				Class<?> cls = classOrNull(className);
				if (cls == null) {
					continue;
				}
				for (Method m : cls.getMethods()) {
					if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
						continue;
					}
					Class<?>[] p = m.getParameterTypes();
					if (nameMatches(cls, "extractBackground", m.getName()) && p.length == 7 && p[2] == AABB.class) {
						m.setAccessible(true);
						drawBoxMethod = m;
						drawBoxKind = p[0] == PoseStack.class ? 1 : 2;
						break resolve;
					}
					if (nameMatches(cls, "drawOutline", m.getName()) && p.length >= 7 && p[0] == PoseStack.class
						&& p[2] == net.minecraft.world.phys.shapes.VoxelShape.class && p[6] == int.class) {
						m.setAccessible(true);
						drawBoxMethod = m;
						drawBoxKind = p.length == 8 ? 4 : 3;
						break resolve;
					}
				}
			}
			if (drawBoxMethod == null) {
				warnOnce("drawBox", new IllegalStateException("drawBox/drawOutline 후보를 못 찾음"));
			}
		}
		if (drawBoxMethod == null) {
			return;
		}
		try {
			switch (drawBoxKind) {
				case 1 -> drawBoxMethod.invoke(null, matrices, buffer, box, r, g, b, a);
				case 2 -> drawBoxMethod.invoke(null, matrices.last(), buffer, box, r, g, b, a);
				default -> {
					int argb = (Math.round(a * 255) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
					Object shape = net.minecraft.world.phys.shapes.Shapes.create(box);
					if (drawBoxKind == 4) {
						drawBoxMethod.invoke(null, matrices, buffer, shape, 0.0, 0.0, 0.0, argb, 2.0f);
					} else {
						drawBoxMethod.invoke(null, matrices, buffer, shape, 0.0, 0.0, 0.0, argb);
					}
				}
			}
		} catch (Throwable t) {
			warnOnce("drawBox:" + drawBoxMethod.getName(), t);
		}
	}

	// ==================== 한 번만 경고(조용히 실패하던 리플렉션 진단용, 48차) ====================
	private static final java.util.Set<String> WARNED_ONCE = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static void warnOnce(String key, Throwable t) {
		if (WARNED_ONCE.add(key)) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 호환층 실패(" + key + ") - 이 기능은 이 버전에서 비활성", t);
		}
	}

	// ==================== GameOptions 키바인드 필드(35차) ====================
	// 1.18.1 이하는 keyAttack/keyUse/keyJump/keyForward/keyBack/keyLeft/keyRight/keyDrop,
	// 1.18.2부터는 attackKey/useKey/jumpKey/forwardKey/backKey/leftKey/rightKey/dropKey로
	// 필드명이 한 번에 갈림(리서치로 경계 확인 - claude/nova-mod-todo.md 35차).
	public static Object getKeyBindingField(Object gameOptions, String newName, String oldName) {
		for (String name : new String[]{newName, oldName}) {
			try {
				java.lang.reflect.Field f = getFieldCompat(gameOptions.getClass(), name);
				Object v = f.get(gameOptions);
				if (v != null) {
					return v;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/** keyBinding.isPressed() 호출(구/신 패키지 둘 다 같은 메서드명). */
	public static boolean isKeyBindingPressed(Object keyBinding) {
		if (keyBinding == null) {
			return false;
		}
		try {
			Object r = getMethodCompat(keyBinding.getClass(), "isDown").invoke(keyBinding);
			return Boolean.TRUE.equals(r);
		} catch (Throwable ignored) {
			return false;
		}
	}

	// ==================== PlayerEntity 인벤토리 접근(35차) ====================
	// 1.16.x는 public final 필드 PlayerEntity#inventory, 1.17+는 getInventory() 메서드
	// (리서치로 경계 확인 - claude/nova-mod-todo.md 35차).
	/**
	 * 49-41차: 선택된 핫바 칸(0~8). 1.21.5+는 PlayerInventory#getSelectedSlot(), 그 아래는 public 필드 selectedSlot
	 * (1.20.1 javap·1.21.11 tiny). 못 읽으면 -1.
	 */
	public static int selectedSlot(Object player) {
		try {
			Inventory inv = getPlayerInventory(player);
			if (inv == null) {
				return -1;
			}
			Object v = callNoArg(inv, "getSelectedSlot");
			if (v instanceof Integer i) {
				return i;
			}
			java.lang.reflect.Field f = getFieldCompat(Inventory.class, "selected");
			f.setAccessible(true);
			return f.getInt(inv);
		} catch (Throwable ignored) {
			return -1;
		}
	}

	public static Inventory getPlayerInventory(Object player) {
		Object viaMethod = callNoArg(player, "getInventory");
		if (viaMethod instanceof Inventory pi) {
			return pi;
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(player.getClass(), "inventory");
			Object v = f.get(player);
			if (v instanceof Inventory pi) {
				return pi;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	// ==================== 채팅/명령어 전송(35차) ====================
	/**
	 * 채팅 메시지 또는 명령어(맨 앞 "/")를 전송. 버전별로 API가 크게 갈려서(리서치로 확인,
	 * claude/nova-mod-todo.md 35차) 신형부터 순서대로 시도:
	 * - 1.19.3+: networkHandler.sendChatCommand(String) / sendChatMessage(String)
	 * - 1.19.1~1.19.2(채팅 미리보기 시대): player.sendCommand(String,Text) / sendChatMessage(String,Text)
	 *   (Text 인자는 null로 시도)
	 * - 1.16~1.19: player.sendChatMessage(String) 단일 메서드(원문 그대로, 맨 앞 "/"도 알아서 처리)
	 */
	public static void sendChatOrCommand(Minecraft client, String rawMessage) {
		if (client == null || client.player == null || rawMessage == null || rawMessage.isEmpty()) {
			return;
		}
		boolean isCommand = rawMessage.startsWith("/");
		String withoutSlash = isCommand ? rawMessage.substring(1) : rawMessage;

		Object networkHandler = resolveMember(client.player, "networkHandler");
		if (networkHandler != null) {
			String methodName = isCommand ? "sendChatCommand" : "sendChatMessage";
			if (tryInvoke1Arg(networkHandler, methodName, String.class, withoutSlash)) {
				return;
			}
		}

		String textMethodName = isCommand ? "sendCommand" : "sendChatMessage";
		if (tryInvoke2ArgWithNull(client.player, textMethodName, String.class, withoutSlash)) {
			return;
		}

		tryInvoke1Arg(client.player, "sendChatMessage", String.class, rawMessage);
	}

	private static boolean tryInvoke1Arg(Object target, String methodName, Class<?> argType, Object arg) {
		if (target == null) {
			return false;
		}
		try {
			Method m = getMethodCompat(target.getClass(), methodName, argType);
			m.invoke(target, arg);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** methodName(firstType, 그 외 아무 타입 하나)의 2인자 메서드를 찾아 (first, null)로 호출. */
	private static boolean tryInvoke2ArgWithNull(Object target, String methodName, Class<?> firstType, Object first) {
		if (target == null) {
			return false;
		}
		for (Method m : target.getClass().getMethods()) {
			if (!nameMatches(target.getClass(), methodName, m.getName()) || m.getParameterCount() != 2) {
				continue;
			}
			Class<?>[] params = m.getParameterTypes();
			if (!params[0].isAssignableFrom(firstType)) {
				continue;
			}
			try {
				m.invoke(target, first, null);
				return true;
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	// ==================== 엔티티 회전(yaw/pitch, 35차) ====================
	// 1.17+는 getYaw()/getPitch()/setYaw(float)/setPitch(float) 메서드, 1.16.x는 public 필드
	// entity.yaw/entity.pitch 직접 접근(리서치로 경계 확인 - claude/nova-mod-todo.md 35차).
	public static float getYaw(Object entity) {
		Object v = callNoArg(entity, "getYaw");
		if (v instanceof Float f) {
			return f;
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(entity.getClass(), "yaw");
			Object raw = f.get(entity);
			if (raw instanceof Float fl) {
				return fl;
			}
		} catch (Throwable ignored) {
		}
		return 0f;
	}

	public static float getPitch(Object entity) {
		Object v = callNoArg(entity, "getPitch");
		if (v instanceof Float f) {
			return f;
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(entity.getClass(), "pitch");
			Object raw = f.get(entity);
			if (raw instanceof Float fl) {
				return fl;
			}
		} catch (Throwable ignored) {
		}
		return 0f;
	}

	public static void setYaw(Object entity, float yaw) {
		try {
			getMethodCompat(entity.getClass(), "setYRot", float.class).invoke(entity, yaw);
			return;
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(entity.getClass(), "yaw");
			f.setFloat(entity, yaw);
		} catch (Throwable ignored) {
		}
	}

	public static void setPitch(Object entity, float pitch) {
		try {
			getMethodCompat(entity.getClass(), "setPitch", float.class).invoke(entity, pitch);
			return;
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(entity.getClass(), "pitch");
			f.setFloat(entity, pitch);
		} catch (Throwable ignored) {
		}
	}

	// ==================== World#getEntitiesByClass(35차) ====================
	/**
	 * world.getEntitiesByClass(Class, Box, Predicate) 계열 메서드를 이름으로 찾아 호출(파라미터가
	 * 3~4개인 오버로드를 순서대로 시도, 예측 못한 추가 파라미터는 null로 채움). 실제로는 3인자
	 * 시그니처 자체가 버전 상관없이 존재하는 것으로 보이나(EntityView 인터페이스), 못 찾는 경우도
	 * 안전하게 빈 리스트로 폴백(claude/nova-mod-todo.md 35차 - 조사와 실제 컴파일 결과가 어긋나서
	 * 안전망 차원에서 리플렉션으로 이관).
	 */
	private static final java.util.function.Predicate<Object> ALWAYS_TRUE = o -> true;
	private static Class<?> entitiesByClassWorld;   // 49-22차: 월드 클래스별로 찾은 오버로드를 기억
	private static Method entitiesByClassMethod;

	@SuppressWarnings("unchecked")
	public static <T> List<T> getEntitiesByClass(Object world, Class<T> entityClass, AABB box) {
		if (world == null) {
			return java.util.Collections.emptyList();
		}
		Class<?> wc = world.getClass();
		Method cachedMethod = entitiesByClassWorld == wc ? entitiesByClassMethod : null;
		if (cachedMethod != null) {
			try {
				Object result = cachedMethod.invoke(world, buildEntityArgs(cachedMethod.getParameterTypes(), entityClass, box));
				if (result instanceof List<?> list) {
					return (List<T>) list;
				}
			} catch (Throwable ignored) {
			}
			return java.util.Collections.emptyList();
		}
		for (Method m : wc.getMethods()) {
			if (!nameMatches(wc, "getEntitiesOfClass", m.getName())) {
				continue;
			}
			Class<?>[] params = m.getParameterTypes();
			if (params.length < 2 || params.length > 4 || params[0] != Class.class) {
				continue;
			}
			try {
				Object result = m.invoke(world, buildEntityArgs(params, entityClass, box));
				if (result instanceof List<?> list) {
					entitiesByClassWorld = wc;
					entitiesByClassMethod = m;
					return (List<T>) list;
				}
			} catch (Throwable ignored) {
			}
		}
		return java.util.Collections.emptyList();
	}

	private static Object[] buildEntityArgs(Class<?>[] params, Class<?> entityClass, AABB box) {
		Object[] args = new Object[params.length];
		args[0] = entityClass;
		for (int i = 1; i < params.length; i++) {
			if (AABB.class.isAssignableFrom(params[i])) {
				args[i] = box;
			} else if (params[i] == java.util.function.Predicate.class) {
				args[i] = ALWAYS_TRUE;
			} else {
				args[i] = null;
			}
		}
		return args;
	}

	// ==================== NativeImage 저장(35차) ====================
	/** NativeImage#writeTo(File)(1.17.1+)/writeFile(File)(1.16~1.17) 순서로 시도. */
	public static void writeImage(NativeImage image, File file) throws java.io.IOException {
		for (String methodName : new String[]{"writeTo", "writeFile", "writeToFile"}) {   // 49-229차: 26.x 이름(writeToFile)도
			try {
				Method m = getMethodCompat(image.getClass(), methodName, File.class);
				m.invoke(image, file);
				return;
			} catch (NoSuchMethodException notThisOne) {
				// 다음 후보 이름 시도
			} catch (java.lang.reflect.InvocationTargetException ite) {
				if (ite.getCause() instanceof java.io.IOException ioException) {
					throw ioException;
				}
			} catch (Throwable ignored) {
			}
		}
		// 49-230차: 이름을 하나도 못 찾았으면 조용히 지나가지 않는다 - 예전엔 파일 없이 "저장했습니다"가 떴다
		throw new java.io.IOException("NativeImage 저장 메서드를 찾지 못함: " + image.getClass().getName());
	}

	// ==================== 49-67차(4-20): 디스크의 그림 → GUI 텍스처 ====================
	// 스크린샷 보관함이 쓴다. 지켜야 할 것이 둘이다.
	//  1. **프레임을 떨어뜨리지 말 것** - 1920×1080 PNG 한 장을 푸는 데 수십 ms가 든다. 그래서
	//     푸는 일과 줄이는 일은 전부 백그라운드 스레드에서 하고(GL을 안 건드리므로 가능하다),
	//     렌더 스레드는 다 줄여 둔 작은 그림을 올리기만 한다.
	//  2. **40개 버전에서 같은 길일 것** - 픽셀을 직접 만지는 이름은 시대마다 갈리지만
	//     (`setPixelRgba` → `setPixelColor` → `setColor` → `setColorArgb`),
	//     `resizeSubRectTo(x, y, w, h, 대상)`은 **1.15.2부터 1.21.11까지 이름도 시그니처도 같다**
	//     (javap 실측). 그래서 축소는 전부 이 하나로 한다 - 리플렉션도 필요 없다.

	/**
	 * 디스크의 그림 파일을 읽어 <b>긴 변이 {@code maxSide} 이하가 되도록 줄인</b> NativeImage를 만든다.
	 * GL을 전혀 건드리지 않으므로 <b>백그라운드 스레드에서 불러도 된다</b>(그러라고 만든 것이다).
	 * 원본이 이미 작으면 줄이지 않고 그대로 돌려준다. 실패하면 null.
	 *
	 * <p>돌려준 NativeImage는 <b>부른 쪽이 책임진다</b> - 텍스처로 등록했다면 텍스처가, 아니면 직접
	 * {@code close()} 해야 한다.
	 */
	public static NativeImage readScaledImage(java.nio.file.Path file, int maxSide) {
		NativeImage full = null;
		try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
			full = NativeImage.read(in);
			if (full == null) {
				return null;
			}
			int w = full.getWidth();
			int h = full.getHeight();
			if (w <= 0 || h <= 0) {
				full.close();
				return null;
			}
			int longSide = Math.max(w, h);
			if (longSide <= maxSide) {
				return full;                       // 이미 충분히 작다 - 그대로
			}
			double k = maxSide / (double) longSide;
			int tw = Math.max(1, (int) Math.round(w * k));
			int th = Math.max(1, (int) Math.round(h * k));
			NativeImage small = new NativeImage(tw, th, false);
			full.resizeSubRectTo(0, 0, w, h, small);
			full.close();
			return small;
		} catch (Throwable t) {
			if (full != null) {
				try {
					full.close();
				} catch (Throwable ignored) {
				}
			}
			warnOnce("readScaledImage", t);
			return null;
		}
	}

	/**
	 * NativeImage를 화면에서 그릴 수 있는 텍스처로 등록한다. <b>렌더 스레드에서만</b> 부를 것
	 * (GL 텍스처를 만든다). 실패하면 null이고, 그때 그림은 이 함수가 닫는다.
	 *
	 * <p>{@code NativeImageBackedTexture}의 생성자는 시대마다 인자가 다르다(그림 하나 →
	 * 이름+그림 → 이름공급자+그림). <b>인자 타입</b>으로 골라 부른다 - 마인크래프트 메서드
	 * 이름을 글자로 적지 않으므로 26.x 변환에도 걸리지 않는다.
	 */
	public static Identifier registerImageTexture(Minecraft client, String path, NativeImage image) {
		if (client == null || image == null) {
			return null;
		}
		try {
			Identifier id = identifier("lunaslight", path);
			net.minecraft.client.renderer.texture.TextureManager manager = client.getTextureManager();
			if (id == null || manager == null) {
				image.close();
				return null;
			}
			DynamicTexture texture = newBackedTexture(image);
			if (texture == null) {
				image.close();
				return null;
			}
			manager.register(id, texture);
			return id;
		} catch (Throwable t) {
			try {
				image.close();
			} catch (Throwable ignored) {
			}
			warnOnce("registerImageTexture", t);
			return null;
		}
	}

	private static DynamicTexture newBackedTexture(NativeImage image) {
		// (NativeImage) - ~1.21.4
		for (java.lang.reflect.Constructor<?> c : DynamicTexture.class.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length == 1 && p[0] == NativeImage.class) {
				try {
					return (DynamicTexture) c.newInstance(image);
				} catch (Throwable ignored) {
				}
			}
		}
		// (String|Supplier<String>, NativeImage) - 1.21.5+ (GPU 디버그 이름이 붙었다)
		for (java.lang.reflect.Constructor<?> c : DynamicTexture.class.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			if (p.length != 2 || p[1] != NativeImage.class) {
				continue;
			}
			Object label = null;
			if (p[0] == String.class) {
				label = "luna";
			} else if (p[0] == java.util.function.Supplier.class) {
				label = (java.util.function.Supplier<String>) () -> "luna";
			}
			if (label != null) {
				try {
					return (DynamicTexture) c.newInstance(label, image);
				} catch (Throwable ignored) {
				}
			}
		}
		return null;
	}

	/**
	 * 등록해 둔 텍스처를 GPU에서 내린다. 화면을 닫을 때 반드시 불러야 한다 - 안 부르면 스크린샷을
	 * 한 번 넘겨볼 때마다 GPU 메모리가 계속 쌓인다.
	 *
	 * <p>{@code image}는 안전장치다. 요즘 버전은 텍스처를 내릴 때 그림도 같이 닫아 주지만
	 * 1.15.2는 GL id만 지우고 그림(네이티브 메모리)은 그대로 둔다. NativeImage#close는 두 번 불러도
	 * 안전하게 만들어져 있어서(포인터를 0으로) 여기서 한 번 더 부른다.
	 */
	public static void unregisterImageTexture(Minecraft client, Identifier id, NativeImage image) {
		try {
			if (client != null && id != null) {
				net.minecraft.client.renderer.texture.TextureManager manager = client.getTextureManager();
				if (manager != null) {
					manager.release(id);
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			if (image != null) {
				image.close();
			}
		} catch (Throwable ignored) {
		}
	}

	// ==================== 엔티티 눈 위치(35차) ====================
	// 1.17+는 getEyePos(), 1.16.x는 그 메서드 자체가 없어 getCameraPosVec(1.0F)(보간 카메라 위치)로
	// 근사(둘 다 1.16.5에 존재 확인 - claude/nova-mod-todo.md 35차).
	public static Vec3 getEyePos(Object entity) {
		Object v = callNoArg(entity, "getEyePosition");
		if (v instanceof Vec3 vec) {
			return vec;
		}
		Object cam = call1(entity, "getEyePosition", 1.0f);
		if (cam instanceof Vec3 vec) {
			return vec;
		}
		return getPos(entity);
	}

	// ==================== GameOptions#getGamma() ====================
	// getPerspective/setPerspective와 동일 패턴: getGamma()가 SimpleOption<Double>을 반환하는
	// 신버전과, 감마가 SimpleOption 자체가 아닌(또는 메서드명이 다른) 구버전이 있을 수 있어
	// 완전 리플렉션으로 흡수. getValue()가 없으면 raw 자체가 이미 Double/Number인 것으로 간주.
	/**
	 * 49-53차(3-8): 바닐라 [FOV 효과 크기]({@code fovEffectScale}) - 속도로 시야가 출렁이는 정도.
	 * 1.17부터 있다. 없는 버전(1.16 이하)에서는 null을 돌려준다.
	 */
	public static Double getFovEffectScale(Object gameOptions) {
		try {
			Object raw = getMethodCompat(gameOptions.getClass(), "fovEffectScale").invoke(gameOptions);
			if (raw == null) {
				return null;
			}
			try {
				Object value = getMethodCompat(raw.getClass(), "getValue").invoke(raw);
				if (value instanceof Number n) {
					return n.doubleValue();
				}
			} catch (NoSuchMethodException noValueMethod) {
				if (raw instanceof Number n) {
					return n.doubleValue();
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static void setFovEffectScale(Object gameOptions, double value) {
		try {
			Object raw = getMethodCompat(gameOptions.getClass(), "fovEffectScale").invoke(gameOptions);
			if (raw == null) {
				return;
			}
			for (Method m : raw.getClass().getMethods()) {
				if (nameMatches(raw.getClass(), "setValue", m.getName()) && m.getParameterCount() == 1) {
					m.invoke(raw, value);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public static Double getGamma(Object gameOptions) {
		try {
			Object raw = getMethodCompat(gameOptions.getClass(), "gamma").invoke(gameOptions);
			if (raw == null) {
				return null;
			}
			try {
				Object value = getMethodCompat(raw.getClass(), "getValue").invoke(raw);
				if (value instanceof Number n) {
					return n.doubleValue();
				}
			} catch (NoSuchMethodException noValueMethod) {
				if (raw instanceof Number n) {
					return n.doubleValue();
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static void setGamma(Object gameOptions, double value) {
		try {
			Object raw = getMethodCompat(gameOptions.getClass(), "gamma").invoke(gameOptions);
			if (raw == null) {
				return;
			}
			for (Method m : raw.getClass().getMethods()) {
				if (nameMatches(raw.getClass(), "setValue", m.getName()) && m.getParameterCount() == 1) {
					m.invoke(raw, value);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	/**
	 * 49-21차: 감마를 **검증 없이** 넣는다(사용자: "야간투시 낀 급으로 완전히 밝게").
	 * 1.19+의 SimpleOption#setValue는 0~1로 잘라내므로, 내부 value 필드에 직접 쓴다 - 라이트맵은
	 * getValue()로 생값을 읽어 쓰기 때문에 1을 훨씬 넘는 감마(풀브라이트)가 실제로 적용된다
	 * (options.txt의 gamma:10 해킹과 같은 원리, 1.19부터는 로드 시 잘리지만 런타임 값은 안 잘림).
	 * ≤1.18은 gamma가 그냥 double 필드라 직접 쓴다.
	 */
	public static boolean setGammaRaw(Object gameOptions, double value) {
		try {
			Object raw = getMethodCompat(gameOptions.getClass(), "gamma").invoke(gameOptions);
			if (raw != null) {
				if (raw instanceof Number) {
					// 구버전: getGamma()가 double 자체를 돌려줌 → 필드에 직접
					java.lang.reflect.Field f = getFieldCompat(gameOptions.getClass(), "gamma");
					f.setAccessible(true);
					f.setDouble(gameOptions, value);
					return true;
				}
				java.lang.reflect.Field vf = getFieldCompat(raw.getClass(), "value");
				vf.setAccessible(true);
				vf.set(raw, value);
				return true;
			}
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(gameOptions.getClass(), "gamma");
			f.setAccessible(true);
			Object cur = f.get(gameOptions);
			if (cur instanceof Number || f.getType() == double.class) {
				f.setDouble(gameOptions, value);
				return true;
			}
			java.lang.reflect.Field vf = getFieldCompat(cur.getClass(), "value");
			vf.setAccessible(true);
			vf.set(cur, value);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	// ==================== 49-21차: 마우스 감도(줌 중 감도 낮추기) ====================
	public static Double getMouseSensitivity(Object gameOptions) {
		try {
			Object raw = null;
			try {
				raw = getMethodCompat(gameOptions.getClass(), "sensitivity").invoke(gameOptions);
			} catch (NoSuchMethodException noGetter) {
				java.lang.reflect.Field f = getFieldCompat(gameOptions.getClass(), "sensitivity");
				f.setAccessible(true);
				raw = f.get(gameOptions);
			}
			if (raw instanceof Number n) {
				return n.doubleValue();
			}
			if (raw != null) {
				Object v = getMethodCompat(raw.getClass(), "getValue").invoke(raw);
				if (v instanceof Number n) {
					return n.doubleValue();
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static void setMouseSensitivity(Object gameOptions, double value) {
		try {
			Object raw = null;
			try {
				raw = getMethodCompat(gameOptions.getClass(), "sensitivity").invoke(gameOptions);
			} catch (NoSuchMethodException noGetter) {
				java.lang.reflect.Field f = getFieldCompat(gameOptions.getClass(), "sensitivity");
				f.setAccessible(true);
				if (f.getType() == double.class) {
					f.setDouble(gameOptions, value);
					return;
				}
				raw = f.get(gameOptions);
			}
			if (raw == null) {
				return;
			}
			for (Method m : raw.getClass().getMethods()) {
				if (nameMatches(raw.getClass(), "setValue", m.getName()) && m.getParameterCount() == 1) {
					m.invoke(raw, value);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	/** 49-21차: F3 디버그 화면이 떠 있는지. ≤1.20.1 options.debugEnabled / 1.20.2+ DebugHud#shouldShowDebugHud. */
	private static Method debugHudGetter, debugHudShown;
	private static java.lang.reflect.Field debugEnabledField;
	private static boolean debugHudResolved;

	public static boolean isDebugHudShown(Minecraft client) {
		if (!debugHudResolved) {
			debugHudResolved = true; // 매 프레임 불리므로 한 번만 해석
			try {
				debugHudGetter = getMethodCompat(client.getClass(), "getDebugOverlay");
				Object hud = debugHudGetter.invoke(client);
				if (hud != null) {
					debugHudShown = getMethodCompat(hud.getClass(), "showDebugScreen");
				}
			} catch (Throwable ignored) {
				debugHudGetter = null;
			}
			try {
				debugEnabledField = getFieldCompat(client.options.getClass(), "debugEnabled");
				debugEnabledField.setAccessible(true);
			} catch (Throwable ignored) {
				debugEnabledField = null;
			}
		}
		try {
			if (debugHudGetter != null && debugHudShown != null) {
				Object hud = debugHudGetter.invoke(client);
				if (hud != null && debugHudShown.invoke(hud) instanceof Boolean b) {
					return b;
				}
			}
			if (debugEnabledField != null) {
				return debugEnabledField.getBoolean(client.options);
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	// ==================== 46차: 프로덕션(intermediary) 이름 변환 레이어 ====================
	// 실제 게임(런처로 실행하는 프로덕션)에서는 마인크래프트 클래스/메서드/필드가 Yarn 이름이 아니라
	// intermediary 이름(net.minecraft.class_304 / method_1436 / field_1653)으로만 존재합니다. Loom은
	// 소스의 "직접 참조"만 빌드 시 바꿔주고, 문자열로 이름을 적어 찾는 리플렉션은 못 바꿔줘서,
	// 이 클래스의 헬퍼 전부가 개발 환경(gradlew runClient)에서만 동작하고 실제 게임에서는 조용히
	// 실패하고 있었습니다(46차 인게임 검증에서 발견 - Right Shift가 안 열리던 원인).
	//
	// 대응: 빌드 시 loom-common.gradle의 generateLunaYarnMap 태스크가 그 버전의 Yarn 매핑에서
	// "Yarn 이름 <-> intermediary 이름" 대응표(lunaslight/yarnmap.txt)를 jar에 넣어주고, 여기서
	// 런타임에 한 번 읽어 모든 리플렉션 조회가 두 이름을 다 시도하게 합니다. 개발 환경에서는
	// 표를 안 읽고 Yarn 이름 그대로 씁니다. Fabric API 클래스(net.fabricmc.*)나 우리 클래스는
	// 이름이 안 바뀌므로 Yarn 이름(=원래 이름) 후보가 그대로 맞습니다.
	private static final boolean DEV_ENV = net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
	private static volatile boolean yarnMapLoaded;
	private static java.util.Map<String, String> yarnClassMap = java.util.Collections.emptyMap();
	private static java.util.Map<String, java.util.Map<String, List<String>>> yarnMemberMap = java.util.Collections.emptyMap();

	private static synchronized void loadYarnMap() {
		if (yarnMapLoaded) {
			return;
		}
		yarnMapLoaded = true;
		if (DEV_ENV) {
			return;
		}
		java.util.Map<String, String> classes = new java.util.HashMap<>();
		java.util.Map<String, java.util.Map<String, List<String>>> members = new java.util.HashMap<>();
		try (java.io.InputStream in = LunaCompat.class.getResourceAsStream("/lunaslight/yarnmap.txt")) {
			if (in == null) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] lunaslight/yarnmap.txt 리소스가 없음 - 프로덕션에서 리플렉션 기반 기능이 동작하지 않을 수 있음");
				return;
			}
			java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), 1 << 16);
			java.util.Map<String, List<String>> current = null;
			String line;
			while ((line = reader.readLine()) != null) {
				int t1 = line.indexOf('\t');
				if (t1 < 0) {
					continue;
				}
				int t2 = line.indexOf('\t', t1 + 1);
				if (t2 < 0) {
					continue;
				}
				String kind = line.substring(0, t1);
				String yarn = line.substring(t1 + 1, t2);
				String intermediary = line.substring(t2 + 1);
				if (kind.equals("C")) {
					classes.put(yarn, intermediary);
					current = members.computeIfAbsent(intermediary, k -> new java.util.HashMap<>());
				} else if (current != null) {
					current.computeIfAbsent(yarn, k -> new java.util.ArrayList<>(2)).add(intermediary);
				}
			}
			yarnClassMap = classes;
			yarnMemberMap = members;
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] Yarn->intermediary 이름표 로드: 클래스 " + classes.size() + "개");
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] yarnmap.txt 읽기 실패", t);
		}
	}

	// ==================== 49-22차: 리플렉션 결과 캐시(프레임 드랍 대책) ====================
	// 사용자: "엔티티 정보나 테두리 같은 게 뜰 때 프레임 드랍이 너무 심해 … FPS 600 유지는 해야지".
	// 원인: 아래 헬퍼들(classForName/getMethodCompat/getFieldCompat/callNoArg/getEntitiesByClass/
	// isKeyPressed …)이 **호출될 때마다** 이름표 조회 + 클래스 계층 순회 + getMethods()(수백 개 메서드
	// 배열 복사) + 예외 생성(스택 트레이스 채우기)을 반복했고, 투영/이름표/엔티티 정보/키스트로크가
	// 매 프레임 이걸 수십~수백 번 부르고 있었다. 이제 (클래스, 이름[, 파라미터]) → 결과(Method/Field/
	// Class, 또는 "없음" 표식)를 한 번 구하면 영구 캐시해서, 두 번째부터는 해시맵 조회 한 번이다.
	// "없음"도 캐시하고, 없을 때 던지는 예외는 스택 트레이스를 채우지 않는 경량 예외를 쓴다(폴백
	// 체인이 있는 호출부가 매 프레임 예외 비용을 내지 않게).
	private static final Object MISS = new Object();
	private static final java.util.concurrent.ConcurrentHashMap<String, Object> CLASS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, List<String>>> MEMBER_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, Object>> METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, Object>> FIELD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, Object>> NOARG_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	/** 스택 트레이스를 채우지 않는 경량 NoSuchMethodException(캐시된 "없음" 결과용). */
	private static final class QuietNoSuchMethod extends NoSuchMethodException {
		QuietNoSuchMethod(String msg) {
			super(msg);
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}

	/** 스택 트레이스를 채우지 않는 경량 NoSuchFieldException(캐시된 "없음" 결과용). */
	private static final class QuietNoSuchField extends NoSuchFieldException {
		QuietNoSuchField(String msg) {
			super(msg);
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}
	}

	private static java.util.concurrent.ConcurrentHashMap<String, Object> perClass(
			java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.concurrent.ConcurrentHashMap<String, Object>> cache, Class<?> cls) {
		java.util.concurrent.ConcurrentHashMap<String, Object> m = cache.get(cls);
		if (m == null) {
			m = new java.util.concurrent.ConcurrentHashMap<>();
			java.util.concurrent.ConcurrentHashMap<String, Object> prev = cache.putIfAbsent(cls, m);
			if (prev != null) {
				m = prev;
			}
		}
		return m;
	}

	private static String methodKey(String yarnName, Class<?>[] params) {
		if (params == null || params.length == 0) {
			return yarnName;
		}
		StringBuilder sb = new StringBuilder(yarnName.length() + 16 * params.length);
		sb.append(yarnName).append('(');
		for (Class<?> p : params) {
			sb.append(p == null ? "null" : p.getName()).append(',');
		}
		return sb.append(')').toString();
	}

	/** Yarn 클래스 이름 -> 이 런타임에서 시도할 실제 이름 후보(Yarn 이름 자체 + intermediary 이름). */
	public static String[] classNameCandidates(String yarnName) {
		loadYarnMap();
		String intermediary = yarnClassMap.get(yarnName);
		return intermediary == null ? new String[]{yarnName} : new String[]{yarnName, intermediary};
	}

	/**
	 * cls(상위 클래스/인터페이스 포함)에서 Yarn 멤버 이름이 실제로 가질 수 있는 이름 후보들(첫 번째는 Yarn 이름 자체).
	 * 49-22차: (클래스, 이름)별로 캐시 - 반환 리스트는 읽기 전용.
	 */
	public static List<String> memberNameCandidates(Class<?> cls, String yarnName) {
		if (cls == null) {
			return java.util.Collections.singletonList(yarnName);
		}
		java.util.concurrent.ConcurrentHashMap<String, List<String>> byName = MEMBER_NAME_CACHE.get(cls);
		if (byName == null) {
			byName = new java.util.concurrent.ConcurrentHashMap<>();
			java.util.concurrent.ConcurrentHashMap<String, List<String>> prev = MEMBER_NAME_CACHE.putIfAbsent(cls, byName);
			if (prev != null) {
				byName = prev;
			}
		}
		List<String> cached = byName.get(yarnName);
		if (cached != null) {
			return cached;
		}
		loadYarnMap();
		List<String> out = new java.util.ArrayList<>(3);
		out.add(yarnName);
		if (!yarnMemberMap.isEmpty()) {
			collectMemberNames(cls, yarnName, out, new java.util.HashSet<>());
		}
		List<String> ro = java.util.Collections.unmodifiableList(out);
		byName.put(yarnName, ro);
		return ro;
	}

	private static void collectMemberNames(Class<?> c, String yarnName, List<String> out, java.util.Set<Class<?>> seen) {
		if (c == null || !seen.add(c)) {
			return;
		}
		java.util.Map<String, List<String>> byYarn = yarnMemberMap.get(c.getName());
		if (byYarn != null) {
			List<String> ids = byYarn.get(yarnName);
			if (ids != null) {
				for (String id : ids) {
					if (!out.contains(id)) {
						out.add(id);
					}
				}
			}
		}
		collectMemberNames(c.getSuperclass(), yarnName, out, seen);
		for (Class<?> itf : c.getInterfaces()) {
			collectMemberNames(itf, yarnName, out, seen);
		}
	}

	/** getMethods() 순회 중 "이 메서드가 Yarn 이름 yarnName에 해당하는가" 판정용. */
	public static boolean nameMatches(Class<?> owner, String yarnName, String actualName) {
		return memberNameCandidates(owner, yarnName).contains(actualName);
	}

	/** Class.forName(yarn 이름)의 프로덕션 대응판 - 못 찾으면 원래처럼 ClassNotFoundException. (49-22차: 결과 캐시) */
	public static Class<?> classForName(String yarnName) throws ClassNotFoundException {
		Object cached = CLASS_CACHE.get(yarnName);
		if (cached == null) {
			cached = MISS;
			for (String actual : classNameCandidates(yarnName)) {
				try {
					cached = Class.forName(actual);
					break;
				} catch (Throwable ignored) {
				}
			}
			CLASS_CACHE.put(yarnName, cached);
		}
		if (cached == MISS) {
			throw new ClassNotFoundException(yarnName);
		}
		return (Class<?>) cached;
	}

	/** classForName의 예외 없는 판 - 없으면 null. */
	public static Class<?> classOrNull(String yarnName) {
		try {
			return classForName(yarnName);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/** cls.getMethod(yarn 이름, params)의 프로덕션 대응판 - 못 찾으면 원래처럼 NoSuchMethodException. (49-22차: 결과 캐시) */
	public static Method getMethodCompat(Class<?> cls, String yarnName, Class<?>... params) throws NoSuchMethodException {
		Method m = findMethod(cls, yarnName, params);
		if (m == null) {
			throw new QuietNoSuchMethod(cls.getName() + "#" + yarnName);
		}
		return m;
	}

	/** getMethodCompat의 예외 없는 판 - 없으면 null(캐시됨). */
	public static Method findMethod(Class<?> cls, String yarnName, Class<?>... params) {
		if (cls == null || yarnName == null) {
			return null;
		}
		java.util.concurrent.ConcurrentHashMap<String, Object> byKey = perClass(METHOD_CACHE, cls);
		String key = methodKey(yarnName, params);
		Object cached = byKey.get(key);
		if (cached == null) {
			cached = MISS;
			for (String actual : memberNameCandidates(cls, yarnName)) {
				try {
					Method m = cls.getMethod(actual, params);
					try {
						m.setAccessible(true);
					} catch (Throwable ignored) {
					}
					cached = m;
					break;
				} catch (Throwable ignored) {
				}
			}
			byKey.put(key, cached);
		}
		return cached == MISS ? null : (Method) cached;
	}

	/**
	 * 49-69차: {@link #findMethod}와 같지만 <b>private 메서드도</b> 찾는다(상위 클래스까지 훑는다).
	 *
	 * <p>필요해진 이유: {@code ChatHud#getWidth()}·{@code getHeight()}·{@code getChatScale()}이
	 * 1.21.11부터 <b>private으로 바뀌었다</b>(1.20.4까지는 public — javap 실측). public만 보는
	 * {@code getMethod}로는 그 버전에서 조용히 못 찾는다.
	 *
	 * <p>남의 private을 부르는 일이라 <b>읽기 전용 값에만</b> 쓴다(여기서는 채팅 칸 크기).
	 */
	public static Method findAnyMethod(Class<?> cls, String yarnName, Class<?>... params) {
		Method open = findMethod(cls, yarnName, params);
		if (open != null) {
			return open;
		}
		for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
			for (String actual : memberNameCandidates(c, yarnName)) {
				try {
					Method m = c.getDeclaredMethod(actual, params);
					m.setAccessible(true);
					return m;
				} catch (Throwable ignored) {
				}
			}
		}
		return null;
	}

	/** cls.getField(yarn 이름)의 프로덕션 대응판 - public 필드가 없으면 상위 클래스까지 declared 필드도 뒤짐. 못 찾으면 NoSuchFieldException. (49-22차: 결과 캐시) */
	public static java.lang.reflect.Field getFieldCompat(Class<?> cls, String yarnName) throws NoSuchFieldException {
		java.lang.reflect.Field f = findField(cls, yarnName);
		if (f == null) {
			throw new QuietNoSuchField((cls == null ? "null" : cls.getName()) + "#" + yarnName);
		}
		return f;
	}

	/** getFieldCompat의 예외 없는 판 - 없으면 null(캐시됨). 찾은 필드는 이미 setAccessible(true) 상태. */
	public static java.lang.reflect.Field findField(Class<?> cls, String yarnName) {
		if (cls == null || yarnName == null) {
			return null;
		}
		java.util.concurrent.ConcurrentHashMap<String, Object> byName = perClass(FIELD_CACHE, cls);
		Object cached = byName.get(yarnName);
		if (cached == null) {
			cached = MISS;
			List<String> names = memberNameCandidates(cls, yarnName);
			outer:
			{
				for (String actual : names) {
					try {
						java.lang.reflect.Field f = cls.getField(actual);
						try {
							f.setAccessible(true);
						} catch (Throwable ignored) {
						}
						cached = f;
						break outer;
					} catch (Throwable ignored) {
					}
				}
				for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
					for (String actual : names) {
						try {
							java.lang.reflect.Field f = c.getDeclaredField(actual);
							f.setAccessible(true);
							cached = f;
							break outer;
						} catch (Throwable ignored) {
						}
					}
				}
			}
			byName.put(yarnName, cached);
		}
		return cached == MISS ? null : (java.lang.reflect.Field) cached;
	}

	/** 클래스 c가 Yarn 전체 이름 yarnFullName(예: net.minecraft.client.option.KeyBinding$Category)에 해당하는지. */
	public static boolean classIs(Class<?> c, String yarnFullName) {
		if (c == null) {
			return false;
		}
		for (String actual : classNameCandidates(yarnFullName)) {
			if (c.getName().equals(actual)) {
				return true;
			}
		}
		return false;
	}

	/** enum 상수의 이름이 Yarn 이름 yarnName인지(프로덕션에서는 상수 이름도 field_xxx로 바뀌므로 표로 비교). */
	public static boolean enumNameIs(Object constant, String yarnName) {
		if (!(constant instanceof Enum<?> e)) {
			return false;
		}
		return nameMatches(e.getDeclaringClass(), yarnName, e.name());
	}

	/** enumClass에서 Yarn 이름이 yarnName인 상수를 찾음(없으면 null). */
	public static Object enumConstant(Class<?> enumClass, String yarnName) {
		if (enumClass == null || !enumClass.isEnum()) {
			return null;
		}
		for (Object constant : enumClass.getEnumConstants()) {
			if (enumNameIs(constant, yarnName)) {
				return constant;
			}
		}
		return null;
	}

	// ==================== 내부 유틸 ====================
	// 24-36차(Nova-Client 쪽 라운드에서 실제 플레이 로그로 발견): event.getClass()가 돌려주는
	// 실제 런타임 클래스는 Fabric API의 비공개 구현체(예: net.fabricmc.fabric.impl.base.event.
	// ArrayBackedEvent)라서, register 메서드 자체는 public이어도 그 메서드가 "선언된 클래스"가
	// public이 아니면 자바 리플렉션이 IllegalAccessException을 던짐(callNoArg/resolveMember 등
	// 이 파일의 다른 리플렉션 헬퍼들은 전부 setAccessible(true)를 먼저 불러서 이 문제를 피하고
	// 있었는데, 여기만 빠져있었음). 실제로 이것 때문에 registerTooltipCallback/
	// registerHudRenderCallback 호출이 매 실행마다 조용히 실패해서 HUD 위젯 16개 + 아이템 툴팁
	// 관련 모듈이 "설정에는 켜져 있는데 화면엔 아무것도 안 보이는" 상태였음.
	private static void invokeRegister(Object event, Object listener) throws Exception {
		for (Method m : event.getClass().getMethods()) {
			if (m.getName().equals("register") && m.getParameterCount() == 1) {
				m.setAccessible(true);
				m.invoke(event, listener);
				return;
			}
		}
	}

	/** 이름이 같은 무인자 메서드를 먼저 시도하고(레코드 접근자 등), 없으면 같은 이름의 필드를 시도. */
	private static Object resolveMember(Object target, String name) {
		if (target == null) {
			return null;
		}
		Object viaMethod = callNoArg(target, name);
		if (viaMethod != null) {
			return viaMethod;
		}
		try {
			java.lang.reflect.Field f = getFieldCompat(target.getClass(), name);
			return f.get(target);
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** 49-23차: 인자 없는 메서드를 Yarn 이름으로 호출(없거나 실패하면 null) - 모듈에서 쓰는 공개 판. */
	public static Object invokeNoArg(Object target, String methodName) {
		return callNoArg(target, methodName);
	}

	public static Object callNoArg(Object target, String methodName) {
		if (target == null) {
			return null;
		}
		Method m = findNoArgMethod(target.getClass(), methodName);
		if (m == null) {
			return null;
		}
		try {
			return m.invoke(target);
		} catch (Throwable t) {
			return null;
		}
	}

	/** 인자 없는 public 메서드를 Yarn 이름으로 찾음(상위 클래스 포함). 49-22차: (클래스, 이름)별 캐시("없음" 포함). */
	public static Method findNoArgMethod(Class<?> cls, String name) {
		if (cls == null || name == null) {
			return null;
		}
		java.util.concurrent.ConcurrentHashMap<String, Object> byName = perClass(NOARG_CACHE, cls);
		Object cached = byName.get(name);
		if (cached == null) {
			cached = MISS;
			List<String> names = memberNameCandidates(cls, name);
			search:
			for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
				for (Method m : c.getMethods()) {
					if (m.getParameterCount() == 0 && names.contains(m.getName())) {
						try {
							m.setAccessible(true);
						} catch (Throwable ignored) {
						}
						cached = m;
						break search;
					}
				}
			}
			byName.put(name, cached);
		}
		return cached == MISS ? null : (Method) cached;
	}

	// ==================== 38차: 1.15.2 전용 API 격차 ====================
	// 1.15.2는 1.16.x보다도 오래된 시대라 33~36차에서 다룬 것과는 또 다른, 훨씬 오래된 이름
	// 규칙을 씁니다(Fabric 공식 raw Yarn 매핑 파일 1.15.2 직접 대조 - claude/nova-mod-todo.md
	// 38차 참고). 아래 헬퍼들은 전부 "메서드가 컴파일 타임에 아예 존재하지 않는" 케이스라
	// (기존 helper들처럼 try/catch로 감싸는 게 아니라) TextRenderer/LivingEntity 클래스
	// "자체"는 모든 버전에 실존하므로 그 타입으로 리시버는 그대로 받되, 메서드 이름만
	// getMethod(...)로 리플렉션 탐색합니다.

	/** TextRenderer#getWidth(String)(1.16+) / getStringWidth(String)(1.15.2, method_1727) */
	// ==================== UI 폰트를 HUD 텍스트에도(48-2차) ====================
	// 동봉 TTF(assets/lunaslight/font/ui<배율>.json)를 Style#withFont로 입힘. 폰트는 GUI 배율과
	// oversample이 1:1일 때 가장 선명해서(마인크래프트 글리프 아틀라스가 NEAREST 필터) 배율별로
	// ui1~ui4 폰트를 두고 현재 배율에 맞는 걸 고름. withFont가 없는 1.15.2 등에서는 null → 기본 폰트.
	private static final java.util.Map<String, Object> UI_STYLE_CACHE = new java.util.HashMap<>();
	private static final Object UI_STYLE_FAIL = new Object();
	private static Method uiTextLiteral;         // Text.literal(String) (1.19+)
	private static java.lang.reflect.Constructor<?> uiLiteralTextCtor; // new LiteralText(String) (≤1.18.2)
	private static final java.util.Map<Class<?>, Method> UI_SET_STYLE = new java.util.HashMap<>();
	private static final java.util.Map<Class<?>, Method> UI_DRAW_SHADOW = new java.util.HashMap<>();
	private static Method uiMeasureMethod;
	private static boolean uiMeasureResolved;

	public static int currentGuiScale() {
		try {
			double sf = Minecraft.getInstance().getWindow().getGuiScale() * 1.0;
			return Math.max(1, Math.min(6, (int) Math.round(sf)));
		} catch (Throwable ignored) {
			return 2;
		}
	}

	// ==================== 49-13차: 폰트 모드(사용자 선택) ====================
	// 0 = 마크 기본 폰트(동봉 폰트 미적용 - Style null), 1 = 마크 + 한글 픽셀(갈무리7 서브셋),
	// 2 = 모던(Pretendard). InterfaceStyleModule이 setFontModeSupplier로 설정을 연결.
	public static final int FONT_MC = 0;
	public static final int FONT_MC_HANGUL = 1;
	public static final int FONT_MODERN = 2;

	private static java.util.function.IntSupplier fontModeSupplier;
	private static int lastFontMode = Integer.MIN_VALUE;

	public static void setFontModeSupplier(java.util.function.IntSupplier supplier) {
		fontModeSupplier = supplier;
	}

	// ==================== 49-80차(5-2): 갈무리 크기 ====================
	// 7(기본, hangul.ttf = mchan) / 9(hangul9.ttf = mchan9) / 11(hangul11.ttf = mchan11).
	// 값은 "한글 잉크 높이(px)"라 textBandBottom()이 그대로 세로 정렬에 쓴다.
	private static java.util.function.IntSupplier pixelSizeSupplier;
	private static int lastPixelSize = Integer.MIN_VALUE;

	public static void setPixelSizeSupplier(java.util.function.IntSupplier supplier) {
		pixelSizeSupplier = supplier;
	}

	/** 지금 고른 갈무리 잉크 높이(7 / 9 / 11). 설정이 없으면 7. */
	public static int pixelSize() {
		if (pixelSizeSupplier == null) {
			return 7;
		}
		try {
			int v = pixelSizeSupplier.getAsInt();
			return v == 9 || v == 11 ? v : 7;
		} catch (Throwable ignored) {
			return 7;
		}
	}

	/** 갈무리 크기별 글꼴 JSON 베이스 이름. */
	public static String pixelBase() {
		return switch (pixelSize()) {
			case 9 -> "mchan9";
			case 11 -> "mchan11";
			default -> "mchan";
		};
	}

	// ==================== 49-47차: 리소스팩 글꼴 우선 ====================
	// 사용자: "폰트 리소스팩 끼면 무조건 그게 우선이 되게 해주고 설정에서 끌 수 있게도 해줘".
	// 리소스팩이 minecraft:font/default.json을 덮어썼는지 본다 - 바닐라 한 장만 있으면 1개,
	// 팩이 끼어 있으면 2개 이상(같은 경로를 여러 팩이 제공하면 전부 나온다). 3초마다만 다시 확인.
	// 49-168차(사용자: "리소스팩 글꼴 우선 설정 없애자, 마크 기본 폰트로 하면 알아서 되니까"): 설정과 강제 우선은 뺐다.
	// 팩 글꼴은 [마크 기본]에서 그대로 보이고, 아래 resourcePackOverridesFont()는 기호 분리(packSplitText)에만 쓴다.
	private static long packFontCheckedAt;
	private static boolean packFontOverride;

	/** 지금 켜진 리소스팩 중 하나가 기본 글꼴을 덮어썼는가. */
	public static boolean resourcePackOverridesFont() {
		long now = System.currentTimeMillis();
		if (now - packFontCheckedAt < 3000) {
			return packFontOverride;
		}
		packFontCheckedAt = now;
		boolean found = false;
		try {
			Minecraft mc = Minecraft.getInstance();
			Object manager = mc == null ? null : callNoArg(mc, "getResourceManager");
			Identifier id = identifier("minecraft", "font/default.json");
			if (manager != null && id != null) {
				for (Method m : manager.getClass().getMethods()) {
					if (!nameMatches(manager.getClass(), "getResourceStack", m.getName())
							|| m.getParameterCount() != 1 || !m.getParameterTypes()[0].isInstance(id)) {
						continue;
					}
					Object list = m.invoke(manager, id);
					if (list instanceof java.util.List<?> l) {
						found = l.size() > 1;   // 바닐라 1장 + 팩 = 2장 이상
					}
					break;
				}
			}
		} catch (Throwable t) {
			warnOnce("packFont", t);
		}
		if (found != packFontOverride) {
			UI_STYLE_CACHE.clear();   // 팩을 끼거나 뺀 순간 바로 반영
		}
		packFontOverride = found;
		return found;
	}

	/** 지금 적용할 폰트 모드. mchan(한글 픽셀)은 reference 프로바이더가 있는 1.20+에서만. */
	public static int fontMode() {
		int mode = FONT_MC;
		if (fontModeSupplier != null) {
			try {
				mode = fontModeSupplier.getAsInt();
			} catch (Throwable ignored) {
				mode = FONT_MC;
			}
		}
		if (mode == FONT_MC_HANGUL && !LunaVersion.isWithin("1.20", null)) {
			return FONT_MC; // 구버전은 font "reference" 프로바이더가 없음 - 바닐라로
		}
		return mode;
	}

	/** 현재 GUI 배율용 동봉 폰트 Style(net.minecraft.text.Style). 마크 기본 모드/실패 시 null. */
	public static Object uiStyle() {
		int mode = fontMode();
		int px = pixelSize();
		if (mode != lastFontMode || px != lastPixelSize) {
			lastFontMode = mode;
			lastPixelSize = px;
			UI_STYLE_CACHE.clear(); // 모드·크기가 바뀌면 캐시된 Style 폐기(즉시 반영)
		}
		return switch (mode) {
			case FONT_MC -> null;
			case FONT_MC_HANGUL -> fontStyle(pixelBase());
			default -> fontStyle("ui");
		};
	}

	// ---- 글리프 세로 밴드(정렬 계산용) ----
	// 49-39차(사용자: "아이콘이랑 글 높낮이도 안 맞잖아"): TTF 글리프를 놓는 규칙이 1.20.5에서 stb_truetype → FreeType으로
	// 바뀌면서 두 가지가 달라진다(둘 다 바이트코드로 확인).
	//  · 크기: stb는 size/(hhea ascent−descent), FreeType은 size/unitsPerEm → 같은 size여도 픽셀 크기가 다름
	//    (갈무리7은 800/900, Pretendard는 2048/2444배 작게 나옴 → 픽셀 폰트가 6.2px로 흐려짐)
	//  · 세로: stb = 폰트 ascent − 3 + 2×shift(shift가 두 번 더해짐), FreeType = 7 + shift
	// 그래서 동봉 폰트 JSON을 두 벌 둔다(기본 = FreeType용, "<base>_stb<배율>" = 1.20.4 이하용, ttfEraStb()로 선택)
	// 그리고 두 벌 다 잉크(ink) 밴드가 아래처럼 같게 맞춰져 있다(drawY 기준, 실측: fontTools/freetype 시뮬레이션):
	//  · 마크 기본 비트맵: 대문자 0..7(베이스라인 7), 꼬리 글자 8까지 → 시각 중심 3.5
	//  · 마크 + 한글 픽셀(갈무리7, size 8·shift 0 / stb: size 9·shift 1): 한글 0..7 - 라틴과 동일, 선명 → 3.5
	//  · 모던(Pretendard 10px em, 베이스라인 8): 대문자 1..8, 한글 0..8.7 → 중심 약 4.4
	//  · 아이콘(Lucide 11px, 베이스라인 9): 잉크 −1.5..8.5 → 3.5 / 큰 아이콘(17px, 베이스라인 12): −4.5..11.5 → 3.5
	// 즉 모든 아이콘과 마크 글꼴의 시각 중심이 3.5로 같다 → LunaDraw.textY/iconY/iconLgY가 같은 값을 낸다.

	/** TTF 글리프 배치가 stb_truetype 방식인 버전(1.20.4 이하)인가. 1.20.5+는 FreeType. */
	public static boolean ttfEraStb() {
		return !LunaVersion.isWithin("1.20.5", null);
	}

	public static float textBandTop() {
		return fontMode() == FONT_MODERN ? 0.3f : 0f;
	}

	public static float textBandBottom() {
		int mode = fontMode();
		if (mode == FONT_MODERN) {
			return 8.5f;
		}
		// 49-80차: 갈무리 9·11은 잉크가 0..9 / 0..11 - 베이스라인을 그만큼 내려 구웠으니(shift 2 / 4) 밴드도 따라간다
		return mode == FONT_MC_HANGUL ? pixelSize() : 7f;
	}

	/** 글자의 시각적 세로 중심(drawY 기준 오프셋). */
	public static float textVisualCenter() {
		return (textBandTop() + textBandBottom()) / 2f;
	}

	/**
	 * 49-172차(사용자: "기능 세부 설정 화살표랑 글 위치가 안 맞음"): 마크 기본 글꼴에서 한글(유니폰트 8px)은 영문(7px)보다
	 * 잉크가 한 줄 더 내려와 중심이 0.5px 아래다. 제목처럼 아이콘 옆에 놓는 글은 이 문자열별 중심으로 맞춘다.
	 */
	public static float textVisualCenter(String s) {
		if (s != null && fontMode() == FONT_MC) {
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (c >= 0xAC00 && c <= 0xD7A3) {
					return (textBandTop() + 8f) / 2f;
				}
			}
		}
		return textVisualCenter();
	}

	/**
	 * 48-4차: 폰트 베이스 이름("ui"/"icons"/"iconslg")별 Style - 아이콘 폰트도 같은 배율별
	 * 선명도 시스템(assets/lunaslight/font/<base><배율>.json)을 씀.
	 */
	public static Object fontStyle(String base) {
		int scale = currentGuiScale();
		String key = base + scale;
		Object cached = UI_STYLE_CACHE.get(key);
		if (cached != null) {
			return cached == UI_STYLE_FAIL ? null : cached;
		}
		Object style = null;
		try {
			style = resolveUiStyle(base, scale);
		} catch (Throwable t) {
			warnOnce("uiStyle:" + base, t);
		}
		UI_STYLE_CACHE.put(key, style == null ? UI_STYLE_FAIL : style);
		return style;
	}

	private static Object resolveUiStyle(String base, int scale) throws Exception {
		// 49-39차: 1.20.4 이하는 stb 배치용 JSON("<base>_stb<배율>") - textBandTop 주석 참고
		return styleWithFontNamed(base + (ttfEraStb() ? "_stb" : "") + scale);
	}

	/**
	 * 49-81차(4-48): 배율 접미 없이 <b>이름 그대로</b>의 글꼴(lunaslight:&lt;fontName&gt;)을 입힌 Style. 채팅 얼굴 자리
	 * 띄우개("space" 프로바이더, 배율과 무관)가 쓴다. 실패(1.15.2: withFont 없음)면 null.
	 */
	public static Object styleWithFontNamed(String fontName) throws Exception {
		Class<?> styleClass = classForName("net.minecraft.network.chat.Style");
		Object font;
		try {
			font = getMethodCompat(Identifier.class, "of", String.class, String.class).invoke(null, "lunaslight", fontName);
		} catch (Throwable ignored) {
			font = Identifier.class.getConstructor(String.class, String.class).newInstance("lunaslight", fontName);
		}
		Method withFont = null;
		for (Method m : styleClass.getMethods()) {
			if (m.getParameterCount() == 1 && nameMatches(styleClass, "withFont", m.getName())) {
				withFont = m;
				break;
			}
		}
		if (withFont == null) {
			return null; // 1.15.2: withFont 자체가 없음
		}
		Object empty;
		try {
			empty = getFieldCompat(styleClass, "EMPTY").get(null);
		} catch (Throwable ignored) {
			empty = styleClass.getConstructor().newInstance(); // 1.15.x
		}
		Class<?> param = withFont.getParameterTypes()[0];
		Object arg;
		if (param.isAssignableFrom(Identifier.class)) {
			arg = font;
		} else {
			// 1.21.9+: StyleSpriteSource.Font(Identifier)
			Class<?> fontSource = classForName("net.minecraft.network.chat.FontDescription$Resource");
			arg = fontSource.getConstructor(Identifier.class).newInstance(font);
		}
		withFont.setAccessible(true);
		return withFont.invoke(empty, arg);
	}

	/** 동봉 폰트 Style이 입혀진 Text 객체(리플렉션 - 1.15.2 LiteralText까지 지원). 실패 시 null. */
	public static Object uiText(String text) {
		Component split = packSplitText(text);
		if (split != null) {
			return split;
		}
		return styledText(text, uiStyle());
	}

	// ==================== 49-156차: 리소스팩 글꼴은 글자와 숫자에만 ====================
	// 사용자: "내 리소스팩 우선은 특수문자 다 빼 - 글이랑 숫자만 변하게". 글꼴 리소스팩은 minecraft:default를 통째로
	// 갈아 끼워서 기호(: / % · → × 등)까지 그 팩 모양이 된다. [리소스팩 글꼴 우선]이 켜져 있고 팩이 글꼴을 바꿨으면
	// Luna가 그리는 글자는 글자·숫자·빈칸만 팩 글꼴(기본 글꼴)로 두고, 나머지 기호는 lunaslight:vanillasym
	// (바닐라 include/space, include/default, include/unifont만 모은 글꼴 - 팩은 보통 default.json만 바꾼다)로 그린다.
	// reference 프로바이더가 있는 1.20+에서만. § 색 코드는 조각마다 이어 붙여 색이 끊기지 않게 한다.
	private static Object vanillaSymStyle;
	private static boolean vanillaSymResolved;
	private static final java.util.Map<String, Component> PACK_SPLIT_CACHE = new java.util.LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, Component> eldest) {
			return size() > 512;
		}
	};

	private static boolean packSymbol(char c) {
		if (c == '\u00A7' || Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
			return false;
		}
		return !(c >= '\uE000' && c <= '\uF8FF') && !Character.isSurrogate(c);   // 아이콘(사용 영역)·이모지는 건드리지 않음
	}

	private static Component packSplitText(String text) {
		if (text == null || text.isEmpty() || fontMode() != FONT_MC) {
			return null;   // 49-168차: [마크 기본] 글꼴일 때만(그때만 팩 글꼴이 Luna 글자에 보인다)
		}
		boolean any = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u00A7') {
				i++;
				continue;
			}
			if (packSymbol(c)) {
				any = true;
				break;
			}
		}
		if (!any || !resourcePackOverridesFont() || !LunaVersion.isWithin("1.20", null)) {
			return null;
		}
		synchronized (PACK_SPLIT_CACHE) {
			Component hit = PACK_SPLIT_CACHE.get(text);
			if (hit != null) {
				return hit;
			}
		}
		if (!vanillaSymResolved) {
			vanillaSymResolved = true;
			try {
				vanillaSymStyle = styleWithFontNamed("vanillasym");
			} catch (Throwable t) {
				warnOnce("vanillaSym", t);
			}
		}
		if (vanillaSymStyle == null) {
			return null;
		}
		java.util.List<Component> parts = new java.util.ArrayList<>();
		StringBuilder run = new StringBuilder();
		boolean runSym = false;
		String fmt = "";   // 지금까지 켜진 § 코드(조각마다 앞에 다시 붙인다)
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u00A7' && i + 1 < text.length()) {
				char code = Character.toLowerCase(text.charAt(i + 1));
				if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
					fmt = code == 'r' ? "" : "\u00A7" + code;
				} else {
					fmt = fmt + "\u00A7" + code;
				}
				run.append(c).append(text.charAt(i + 1));
				i++;
				continue;
			}
			boolean sym = packSymbol(c);
			if (sym != runSym && run.length() > 0) {
				Component part = runSym ? styledText(run.toString(), vanillaSymStyle) : textLiteral(run.toString());
				if (part == null) {
					return null;
				}
				parts.add(part);
				run.setLength(0);
				run.append(fmt);
			}
			runSym = sym;
			run.append(c);
		}
		if (run.length() > 0) {
			Component part = runSym ? styledText(run.toString(), vanillaSymStyle) : textLiteral(run.toString());
			if (part == null) {
				return null;
			}
			parts.add(part);
		}
		Component joined = joinTexts(parts.toArray(new Component[0]));
		if (joined != null) {
			synchronized (PACK_SPLIT_CACHE) {
				PACK_SPLIT_CACHE.put(text, joined);
			}
		}
		return joined;
	}

	/**
	 * 49-36차: 임의 Style(fontStyle("icons") 등)을 입힌 Text - LunaGfx/LunaIcons가 Text.literal(1.19+)·
	 * MutableText(1.16+)를 직접 쓰지 않게 해서 1.15.2~1.18.2에서도 같은 코드가 컴파일된다. style이 null이거나
	 * 실패하면 null(호출부는 textLiteral 폴백).
	 */
	public static Component styledText(String text, Object style) {
		if (style == null) {
			return null;
		}
		try {
			Object txt;
			if (uiTextLiteral == null && uiLiteralTextCtor == null) {
				try {
					uiTextLiteral = getMethodCompat(Component.class, "literal", String.class);
				} catch (Throwable ignored) {
					uiLiteralTextCtor = classForName("net.minecraft.text.LiteralText").getConstructor(String.class);
				}
			}
			txt = uiTextLiteral != null ? uiTextLiteral.invoke(null, text) : uiLiteralTextCtor.newInstance(text);
			Method setStyle = UI_SET_STYLE.get(txt.getClass());
			if (setStyle == null) {
				for (Method m : txt.getClass().getMethods()) {
					if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(style)
						&& nameMatches(txt.getClass(), "setStyle", m.getName())) {
						setStyle = m;
						break;
					}
				}
				if (setStyle == null) {
					return null;
				}
				setStyle.setAccessible(true);
				UI_SET_STYLE.put(txt.getClass(), setStyle);
			}
			Object styled = setStyle.invoke(txt, style);
			Object out = styled != null ? styled : txt;
			return out instanceof Component t ? t : null;
		} catch (Throwable t) {
			warnOnce("uiText", t);
			return null;
		}
	}

	/**
	 * 49-36차: Text(스타일 포함) 폭. 1.16+ TextRenderer#getWidth(StringVisitable), 1.15.2 getStringWidth(String)
	 * (asFormattedString) - 헬퍼(LunaDraw/LunaIcons)가 tr.getWidth(Text)를 직접 부르면 1.15.2에서 컴파일이 안 된다.
	 */
	public static int textWidth(Font textRenderer, Object text) {
		if (text == null) {
			return 0;
		}
		try {
			if (!uiMeasureResolved) {
				uiMeasureResolved = true;
				for (Method m : Font.class.getMethods()) {
					if (m.getParameterCount() == 1 && m.getParameterTypes()[0] != String.class
						&& m.getParameterTypes()[0].isInstance(text)
						&& m.getReturnType() == int.class
						&& nameMatches(Font.class, "width", m.getName())) {
						m.setAccessible(true);
						uiMeasureMethod = m;
						break;
					}
				}
			}
			if (uiMeasureMethod != null) {
				Object w = uiMeasureMethod.invoke(textRenderer, text);
				if (w instanceof Integer i) {
					return i;
				}
			}
		} catch (Throwable t) {
			warnOnce("textWidth", t);
		}
		String plain = text instanceof Component t ? t.getString() : String.valueOf(text);
		for (String name : new String[]{"getWidth", "getStringWidth"}) {
			try {
				Method m = getMethodCompat(Font.class, name, String.class);
				Object result = m.invoke(textRenderer, plain);
				if (result instanceof Integer i) {
					return i;
				}
			} catch (Throwable ignored) {
			}
		}
		return 0;
	}

	// ==================== 49-257차: 크림 HUD 글자 ====================

	/** 지금 그리는 HUD 기능이 크림 상자를 쓰는 중인지(Module.renderHud가 켜고 끈다). */
	public static volatile boolean hudCream;

	/** 크림 판 위에서 안 보이는 밝은 색 코드를 짙은 짝으로(흰 → 검정, 회색 → 짙은 회색, 하늘 → 청록 …). */
	public static String creamText(String s) {
		if (s == null || s.indexOf('\u00A7') < 0) {
			return s;
		}
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			b.append(c);
			if (c == '\u00A7' && i + 1 < s.length()) {
				char k = Character.toLowerCase(s.charAt(i + 1));
				char r = switch (k) {
					case 'f' -> '0';
					case '7' -> '8';
					case 'a' -> '2';
					case 'b' -> '3';
					case 'e' -> '6';
					case 'd' -> '5';
					default -> s.charAt(i + 1);
				};
				b.append(r);
				i++;
			}
		}
		return b.toString();
	}

	/** 크림 판 위 글자 색: 흰색/회색 계열은 짙은 갈색, 밝은 유채색은 어둡게(색은 살림). 알파 0이면 불투명으로. */
	public static int creamColor(int argb) {
		int a = (argb >>> 24) & 0xFF;
		if (a == 0) {
			a = 0xFF;
		}
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
		double lum = 0.299 * r + 0.587 * g + 0.114 * bl;
		if (lum < 110) {
			return (a << 24) | (argb & 0xFFFFFF);
		}
		if (max - min < 40) {
			return (a << 24) | 0x3B2C1D;   // 크림 스킨 본문 글자색
		}
		float k = (float) Math.min(0.62, 95.0 / lum);
		return (a << 24) | (Math.round(r * k) << 16) | (Math.round(g * k) << 8) | Math.round(bl * k);
	}

	/**
	 * HUD용 텍스트 그리기 - 가능하면 동봉 폰트(Text 오버로드), 아니면 기본 폰트(String 오버로드).
	 * 모듈들의 context.drawTextWithShadow(client.textRenderer, ...) 호출을 이걸로 교체(48-2차).
	 */
	public static void drawHudText(GuiGraphicsExtractor ctx, Font textRenderer, String text, int x, int y, int color) {
		if (hudCream) {
			// 49-257차: 크림 HUD 상자 위 - 밝은 글자를 짙게, 그림자 없이(밝은 판에 그림자는 지저분하다)
			kr.lunaslight.mod.gui.LunaDraw.text(ctx, textRenderer, creamText(text), x, y, creamColor(color));
			return;
		}
		Object styled = uiText(text);
		if (styled != null) {
			try {
				Method m;
				if (UI_DRAW_SHADOW.containsKey(ctx.getClass())) {
					m = UI_DRAW_SHADOW.get(ctx.getClass());
				} else {
					m = null;
					for (Method cand : ctx.getClass().getMethods()) {
						if (cand.getParameterCount() != 5 || !nameMatches(ctx.getClass(), "text", cand.getName())) {
							continue;
						}
						Class<?>[] p = cand.getParameterTypes();
						if (p[0].isAssignableFrom(Font.class) && p[1] != String.class && p[1].isInstance(styled)
							&& p[2] == int.class && p[3] == int.class && p[4] == int.class) {
							m = cand;
							break;
						}
					}
					if (m != null) {
						m.setAccessible(true);
					}
					UI_DRAW_SHADOW.put(ctx.getClass(), m);
				}
				if (m != null) {
					m.invoke(ctx, textRenderer, styled, x, y, color);
					return;
				}
			} catch (Throwable t) {
				warnOnce("drawHudText", t);
			}
		}
		ctx.text(textRenderer, text, x, y, color);
	}

	public static int getTextWidth(Font textRenderer, String text) {
		// 48-2차: 동봉 폰트가 적용될 환경이면 같은 폰트로 폭을 재야 위치가 안 틀어짐.
		Object styled = uiText(text);
		if (styled != null) {
			try {
				if (!uiMeasureResolved) {
					uiMeasureResolved = true;
					for (Method m : Font.class.getMethods()) {
						if (m.getParameterCount() == 1 && m.getParameterTypes()[0] != String.class
							&& m.getParameterTypes()[0].isInstance(styled)
							&& m.getReturnType() == int.class
							&& nameMatches(Font.class, "width", m.getName())) {
							m.setAccessible(true);
							uiMeasureMethod = m;
							break;
						}
					}
				}
				if (uiMeasureMethod != null) {
					Object w = uiMeasureMethod.invoke(textRenderer, styled);
					if (w instanceof Integer i) {
						return i;
					}
				}
			} catch (Throwable t) {
				warnOnce("getTextWidth:styled", t);
			}
		}
		for (String name : new String[]{"getWidth", "getStringWidth"}) {
			try {
				Method m = getMethodCompat(Font.class, name, String.class);
				Object result = m.invoke(textRenderer, text);
				if (result instanceof Integer i) {
					return i;
				}
			} catch (Throwable ignored) {
				// 이 이름은 이 버전에 없었던 것 - 다음 후보로.
			}
		}
		return 0;
	}

	/** 49-36차: Box 경계 {minX,minY,minZ,maxX,maxY,maxZ} - 1.16+ 필드 minX…, 1.15.2는 x1,y1,z1,x2,y2,z2. */
	public static double[] boxBounds(Object box) {
		double[] out = new double[6];
		if (box == null) {
			return out;
		}
		String[][] names = {{"minX", "x1"}, {"minY", "y1"}, {"minZ", "z1"}, {"maxX", "x2"}, {"maxY", "y2"}, {"maxZ", "z2"}};
		for (int i = 0; i < 6; i++) {
			Object v = getFieldValue(box, names[i]);
			if (v instanceof Number n) {
				out[i] = n.doubleValue();
			}
		}
		return out;
	}

	/** 49-36차: Entity#isOnGround()(1.16+) / 필드 onGround(1.15.2). */
	public static boolean isOnGround(Object entity) {
		Object v = callNoArg(entity, "isOnGround");
		if (v == null) {
			v = getFieldValue(entity, "onGround");
		}
		return v instanceof Boolean b && b;
	}

	/** 49-36차: 지금 열린 ScreenHandler - 필드 currentScreenHandler(1.16+) / container(1.15.2). */
	public static Object currentScreenHandler(Object player) {
		return getFieldValue(player, "containerMenu", "container");
	}

	/** 49-36차: 플레이어 자기 인벤토리 핸들러 - playerScreenHandler(1.16+) / playerContainer(1.15.2). */
	public static Object playerScreenHandler(Object player) {
		return getFieldValue(player, "inventoryMenu", "playerContainer");
	}

	/** LivingEntity#getMaxHealth()(1.16+) / getMaximumHealth()(1.15.2, method_6063) */
	public static float getMaxHealth(LivingEntity entity) {
		for (String name : new String[]{"getMaxHealth", "getMaximumHealth"}) {
			try {
				Method m = getMethodCompat(LivingEntity.class, name);
				Object result = m.invoke(entity);
				if (result instanceof Float f) {
					return f;
				}
			} catch (Throwable ignored) {
			}
		}
		return 20f;
	}

	/**
	 * Inventory 계열(PlayerInventory 등)의 슬롯 접근 - 1.16+는 Inventory 인터페이스가
	 * getStack(int)/size()로 개명됐지만 1.15.2는 여전히 원래 이름 getInvStack(int)(method_5438)/
	 * getInvSize()(method_5439)를 씁니다. LunaCompat.getPlayerInventory가 반환하는 실제 타입
	 * (PlayerInventory)은 두 버전 다 존재하므로 리시버는 그대로 받고 메서드 이름만 리플렉션.
	 */
	public static ItemStack invGetStack(Object inventory, int slot) {
		if (inventory == null) {
			return ItemStack.EMPTY;
		}
		for (String name : new String[]{"getStack", "getInvStack"}) {
			try {
				Method m = getMethodCompat(inventory.getClass(), name, int.class);
				Object result = m.invoke(inventory, slot);
				if (result instanceof ItemStack stack) {
					return stack;
				}
			} catch (Throwable ignored) {
			}
		}
		return ItemStack.EMPTY;
	}

	public static int invSize(Object inventory) {
		if (inventory == null) {
			return 0;
		}
		for (String name : new String[]{"size", "getInvSize"}) {
			try {
				Method m = getMethodCompat(inventory.getClass(), name);
				Object result = m.invoke(inventory);
				if (result instanceof Integer i) {
					return i;
				}
			} catch (Throwable ignored) {
			}
		}
		return 0;
	}

	/**
	 * client.currentScreen이 "컨테이너류 화면"인지 판별 - 1.16+는 HandledScreen, 1.15.2는
	 * ContainerScreen(둘 다 net.minecraft.client.gui.screen.ingame 패키지, 이름만 다름).
	 * resolveClass로 존재하는 쪽 클래스를 찾아 isInstance로 확인(둘 다 컴파일 타임 참조 없음).
	 */
	public static boolean isHandledScreen(Object screen) {
		if (screen == null) {
			return false;
		}
		Class<?> cls = resolveClass(
				"net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
				"net.minecraft.client.gui.screen.ingame.ContainerScreen");
		return cls != null && cls.isInstance(screen);
	}

	/**
	 * 49-40차(사용자: "마우스휠로 아이템을 삭제시켜 버리는데?"): 크리에이티브 인벤토리인가.
	 * 크리에이티브 창의 칸들은 가짜(CreativeSlot, 서버엔 없는 핸들러 syncId 0)라, 그 칸 번호로
	 * interactionManager.clickSlot을 보내면 **서버의 플레이어 인벤토리 핸들러**가 엉뚱한 칸을 집고 버린다
	 * (= 아이템이 사라짐). 칸을 직접 클릭하는 기능(마우스 트윅스·일괄 정리 등)은 이 화면에선 손대지 않는다.
	 * 클래스 참조는 loom이 리매핑하므로 프로덕션(intermediary 이름)에서도 동작 - 이름 문자열 비교는 안 됨.
	 */
	public static boolean isCreativeInventory(Object screen) {
		try {
			return screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * InputUtil.Type.KEYSYM.createFromCode(int)가 반환하는 KeyCode 객체의 표시용 이름.
	 * 1.16+는 getLocalizedText()(Text 반환) 후 .getString(), 1.15.2는 getLocalizedText() 자체가
	 * 없고 getName()(String을 바로 반환, method_1441)만 있음 - keyCode 파라미터를 Object로 받아
	 * 어느 버전의 실제 KeyCode 타입이든(InputUtil 자체를 여기서 import하지 않음) 그대로 처리.
	 */
	/**
	 * 49-33차: GLFW 키 코드 하나의 표시 이름. 예전엔 KeybindSetting이
	 * <code>InputUtil.Type.KEYSYM.createFromCode(base)</code>를 직접 불렀는데,
	 * 26.x(Mojang 이름)에서는 그 클래스 이름 자체가 달라서 설정 클래스가 매핑에 묶여 버렸다.
	 * 이 한 겹을 여기로 옮겨서 setting 패키지 전체가 마인크래프트 타입을 안 쓰게 만든다
	 * (= 26.x 트리에 그대로 복사 가능).
	 */
	/** 49-33차: setting 패키지가 MinecraftClient 타입을 안 쓰게 하는 얇은 겹(위 keySymName과 같은 이유). */
	public static boolean isKeyPressedAny(Object client, int keyCode) {
		return client instanceof Minecraft mc && isKeyPressed(mc, keyCode);
	}

	public static String keySymName(int glfwCode) {
		try {
			return getKeyCodeDisplayName(LunaInput.keyboardKey(glfwCode));
		} catch (Throwable t) {
			warnOnce("keySymName", t);
			return "키 " + glfwCode;
		}
	}

	public static String getKeyCodeDisplayName(Object keyCode) {
		if (keyCode == null) {
			return "?";
		}
		try {
			Method getLocalizedText = getMethodCompat(keyCode.getClass(), "getDisplayName");
			Object text = getLocalizedText.invoke(keyCode);
			if (text != null) {
				Method getString = getMethodCompat(text.getClass(), "getString");
				Object str = getString.invoke(text);
				if (str instanceof String s) {
					return s;
				}
			}
		} catch (Throwable ignored) {
			// 1.15.2처럼 getLocalizedText() 자체가 없는 버전 - getName()으로 폴백.
		}
		try {
			Method getName = getMethodCompat(keyCode.getClass(), "getName");
			Object name = getName.invoke(keyCode);
			if (name instanceof String s) {
				return s;
			}
		} catch (Throwable ignored) {
		}
		return "?";
	}
	// ==================== 49-25차: 내 스킨 얼굴(타이틀 프로필 버튼) ====================
	// 세션 프로필의 스킨 텍스처를 한 번만 비동기로 받아 두고(1.20.2+ fetchSkinTextures → CompletableFuture,
	// ~1.20.1 loadSkin(profile, callback, false)), 받기 전엔 기본 스킨(DefaultSkinHelper / steve.png).
	private static Object skinFuture;
	private static Identifier skinTexture;
	private static boolean skinRequested;
	private static String skinRequestedFor;
	private static Identifier defaultSkinTexture;

	/** 지금 세션의 GameProfile(1.20.2+ MinecraftClient#getGameProfile, 그 전 Session#getProfile). 없으면 null. */
	public static Object sessionProfile(Minecraft client) {
		Object profile = callNoArg(client, "getGameProfile");
		if (profile == null) {
			Object session = callNoArg(client, "getUser");
			profile = callNoArg(session, "getProfile");
		}
		return profile;
	}

	/** 내 스킨 텍스처(64×64). 아직 못 받았으면 기본 스킨, 그것도 안 되면 null. 계정을 바꾸면 다시 받는다. */
	public static Identifier playerSkinTexture(Minecraft client) {
		try {
			Object profile = sessionProfile(client);
			if (profile == null) {
				return defaultSkin(null);
			}
			String key = String.valueOf(callNoArg(profile, "getId")) + "/" + callNoArg(profile, "getName");
			if (!key.equals(skinRequestedFor)) {
				skinRequestedFor = key;
				skinRequested = false;
				skinFuture = null;
				skinTexture = null;
			}
			if (skinTexture != null) {
				return skinTexture;
			}
			Object provider = callNoArg(client, "getSkinManager");
			if (provider == null) {
				return defaultSkin(profile);
			}
			if (!skinRequested) {
				skinRequested = true;
				requestSkin(provider, profile);
			}
			if (skinFuture instanceof java.util.concurrent.CompletableFuture<?> f && f.isDone() && !f.isCompletedExceptionally()) {
				Object r = f.getNow(null);
				if (r instanceof java.util.Optional<?> o) {
					r = o.orElse(null);
				}
				Identifier tex = skinTextureOf(r);
				if (tex != null) {
					skinTexture = tex;
					return tex;
				}
			}
			return defaultSkin(profile);
		} catch (Throwable t) {
			warnOnce("playerSkin", t);
			return null;
		}
	}

	private static void requestSkin(Object provider, Object profile) {
		Class<?> pc = provider.getClass();
		// 1.20.2+: fetchSkinTextures(GameProfile) → CompletableFuture<SkinTextures | Optional<SkinTextures>>
		for (Method m : pc.getMethods()) {
			if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(profile)
					&& nameMatches(pc, "fetchSkinTextures", m.getName())
					&& java.util.concurrent.CompletableFuture.class.isAssignableFrom(m.getReturnType())) {
				try {
					skinFuture = m.invoke(provider, profile);
					return;
				} catch (Throwable t) {
					warnOnce("playerSkin:fetch", t);
				}
			}
		}
		// ~1.20.1: loadSkin(GameProfile, SkinTextureAvailableCallback, boolean requireSecure) - 콜백 Proxy
		for (Method m : pc.getMethods()) {
			if (m.getParameterCount() == 3 && m.getParameterTypes()[0].isInstance(profile)
					&& m.getParameterTypes()[1].isInterface() && m.getParameterTypes()[2] == boolean.class
					&& nameMatches(pc, "loadSkin", m.getName())) {
				try {
					Class<?> cb = m.getParameterTypes()[1];
					Object proxy = Proxy.newProxyInstance(cb.getClassLoader(), new Class<?>[]{cb}, (InvocationHandler) (p, method, args) -> {
						if (method.getDeclaringClass() == Object.class) {
							return proxyObjectMethod(p, method, args);
						}
						if (args != null && args.length >= 2 && args[1] instanceof Identifier id
								&& args[0] != null && "SKIN".equals(String.valueOf(args[0]))) {
							skinTexture = id;
						}
						return null;
					});
					m.invoke(provider, profile, proxy, false);
					return;
				} catch (Throwable t) {
					warnOnce("playerSkin:load", t);
				}
			}
		}
	}

	/** SkinTextures(1.20.2~1.21.8 texture() / 1.21.9+ body().texturePath()) → Identifier. */
	private static Identifier skinTextureOf(Object skinTextures) {
		if (skinTextures == null) {
			return null;
		}
		Object tex = callNoArg(skinTextures, "texture");
		if (tex == null) {
			Object body = callNoArg(skinTextures, "body");
			tex = callNoArg(body, "texturePath");
		}
		return tex instanceof Identifier id ? id : null;
	}

	private static Identifier defaultSkin(Object profile) {
		if (defaultSkinTexture != null) {
			return defaultSkinTexture;
		}
		try {
			Class<?> helper = classOrNull("net.minecraft.client.resources.DefaultPlayerSkin");
			if (helper != null) {
				Object uuid = profile == null ? null : callNoArg(profile, "getId");
				if (!(uuid instanceof java.util.UUID)) {
					uuid = java.util.UUID.randomUUID();
				}
				for (Method m : helper.getMethods()) {
					if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == java.util.UUID.class
							&& (nameMatches(helper, "getSkinTextures", m.getName()) || nameMatches(helper, "getTexture", m.getName())
									|| nameMatches(helper, "getSkin", m.getName()))) {
						Object r = m.invoke(null, uuid);
						Identifier tex = r instanceof Identifier id ? id : skinTextureOf(r);
						if (tex != null) {
							defaultSkinTexture = tex;
							return tex;
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
		defaultSkinTexture = identifier("minecraft", LunaVersion.isWithin("1.20.2", null)
				? "textures/entity/player/wide/steve.png" : "textures/entity/steve.png");
		return defaultSkinTexture;
	}

	/** 세션 닉네임(없으면 "플레이어"). */
	public static String sessionName(Minecraft client) {
		try {
			Object session = callNoArg(client, "getUser");
			Object name = callNoArg(session, "getUsername");
			if (name instanceof String s && !s.isEmpty()) {
				return s;
			}
			Object profile = sessionProfile(client);
			Object n = callNoArg(profile, "getName");
			if (n instanceof String s && !s.isEmpty()) {
				return s;
			}
		} catch (Throwable ignored) {
		}
		return "플레이어";
	}

	// ==================== 49-22차: 화면 배경 블러 ====================
	// 사용자: "블러: 어둡기 대신 진짜 흐릿해지게". 바닐라 1.21+는 Screen#renderBackground(DrawContext,int,int,float)
	// 안에서 옵션 '메뉴 배경 흐림'(기본 5)만큼 실제 가우시안 블러(applyBlur)를 건 뒤 어둡게 한다. 우리 화면들은
	// 직접 검은 막만 깔고 있었으므로, 그리기 시작에 이걸 리플렉션으로 불러 같은 블러를 받는다.
	// (1.20.2~1.20.6은 어둡기만, 1.20.1은 renderBackground(DrawContext), 그 전은 없음 → false)
	//
	// 49-77차: 1.21.6+(새 GUI 렌더러, DrawContext#createNewRootLayer가 생긴 버전)부터는 바닐라가
	// Screen#renderWithTooltip 안에서 render() 전에 renderBackground를 이미 부른다. 우리가 한 번 더 부르면
	// "Can only blur once per frame"으로 터지고(1.21.11 로그), 그 뒤로는 dim만 한 겹 더 깔려 화면이 더 어두워졌다.
	// 그 버전에서는 부르지 않고 "이미 깔렸다"(true)로 답한다. 버전 문자열이 아니라 그 메서드의 유무로 가른다.
	private static Method screenBgMethod;
	private static int screenBgArity = -1;   // 4/1 = 우리가 부름, 5 = 바닐라가 먼저 깔아 둠, 0 = 없음

	public static boolean renderScreenBackground(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		if (screen == null || ctx == null) {
			return false;
		}
		try {
			if (screenBgArity == -1) {
				screenBgArity = 0;
				Class<?> screenClass = classOrNull("net.minecraft.client.gui.screens.Screen");
				if (findMethod(GuiGraphicsExtractor.class, "nextStratum") != null) {
					screenBgArity = 5;
				} else if (screenClass != null) {
					Method m4 = findMethod(screenClass, "extractBackground", GuiGraphicsExtractor.class, int.class, int.class, float.class);
					if (m4 != null) {
						screenBgMethod = m4;
						screenBgArity = 4;
					} else {
						Method m1 = findMethod(screenClass, "extractBackground", GuiGraphicsExtractor.class);
						if (m1 != null) {
							screenBgMethod = m1;
							screenBgArity = 1;
						}
					}
				}
			}
			if (screenBgArity == 5) {
				return true;   // renderWithTooltip이 이미 블러+어둡기를 깔았다
			}
			if (screenBgArity == 4) {
				screenBgMethod.invoke(screen, ctx, mouseX, mouseY, delta);
				return true;
			}
			if (screenBgArity == 1) {
				screenBgMethod.invoke(screen, ctx);
				return true;
			}
		} catch (Throwable t) {
			warnOnce("screenBackground", t);
			screenBgArity = 0;
		}
		return false;
	}

	// ==================== 49-81차(4-48): 채팅 얼굴 보조 ====================

	/** Style#withInsertion(String) - 보이지 않는 꼬리표(1.16+). 실패면 style 그대로. */
	public static Object styleWithInsertion(Object style, String insertion) {
		if (style == null || insertion == null) {
			return style;
		}
		try {
			Method m = findMethod(style.getClass(), "withInsertion", String.class);
			Object r = m == null ? null : m.invoke(style, insertion);
			return r != null ? r : style;
		} catch (Throwable ignored) {
			return style;
		}
	}

	/** Text 여러 개를 색이 안 새는 빈 뿌리 아래 나란히 잇는다(시각 접두와 같은 방식). 실패면 null. */
	public static Component joinTexts(Component... parts) {
		try {
			Component root = textLiteral("");
			if (root == null) {
				return null;
			}
			Method append = findMethod(root.getClass(), "append", Component.class);
			if (append == null) {
				return null;
			}
			Object cur = root;
			for (Component p : parts) {
				if (p == null) {
					continue;
				}
				Object r = append.invoke(cur, p);
				if (r instanceof Component t) {
					cur = t;
				}
			}
			return cur instanceof Component t ? t : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * 탭 목록에 있는 플레이어의 스킨 텍스처(64×64). 이름이 탭 목록에 없으면 null - 그 사람 얼굴을 지어내지 않는다.
	 * 1.20.1은 PlayerListEntry#getSkinTexture(), 1.20.2+는 getSkinTextures().texture(). 아직 못 받았으면 바닐라가
	 * 기본 스킨을 돌려준다(탭 목록과 같은 동작).
	 */
	public static Identifier playerSkinByName(Minecraft client, String name) {
		if (client == null || name == null || name.isEmpty()) {
			return null;
		}
		try {
			Object handler = callNoArg(client, "getConnection");
			if (handler == null) {
				return null;
			}
			Method m = findMethod(handler.getClass(), "getPlayerInfo", String.class);
			Object entry = m == null ? null : m.invoke(handler, name);
			if (entry == null) {
				return null;
			}
			Object tex = callNoArg(entry, "getSkinTexture");
			if (tex instanceof Identifier id) {
				return id;
			}
			return skinTextureOf(callNoArg(entry, "getSkinTextures"));
		} catch (Throwable t) {
			warnOnce("skinByName", t);
			return null;
		}
	}

	/** 탭 목록의 플레이어 이름들(없으면 빈 목록). 채팅 줄에서 보낸 사람을 찾는 폴백에 쓴다. */
	@SuppressWarnings("unchecked")
	public static java.util.List<String> playerListNames(Minecraft client) {
		java.util.List<String> out = new java.util.ArrayList<>();
		try {
			Object handler = client == null ? null : callNoArg(client, "getConnection");
			Object list = handler == null ? null : callNoArg(handler, "getPlayerList");
			if (list instanceof java.util.Collection<?> c) {
				for (Object e : c) {
					Object profile = callNoArg(e, "getProfile");
					Object n = profile == null ? null : callNoArg(profile, "getName");
					if (n instanceof String s && !s.isEmpty()) {
						out.add(s);
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	// ==================== 49-22차: RGB 색 Text ====================
	private static boolean coloredResolved;
	private static Method styleWithColor, textColorFromRgb, textSetStyle;
	private static Object styleEmpty;

	/**
	 * 임의 RGB 색이 입혀진 Text(채팅 시각 표시용). Style.EMPTY.withColor(TextColor.fromRgb(rgb)) +
	 * MutableText#setStyle - 전부 리플렉션(1.16+). 못 만들면 null(호출부가 §코드로 폴백).
	 */
	public static Component coloredText(String content, int rgb) {
		try {
			Component base = textLiteral(content);
			if (base == null) {
				return null;
			}
			if (!coloredResolved) {
				coloredResolved = true;
				Class<?> styleClass = classOrNull("net.minecraft.network.chat.Style");
				Class<?> textColorClass = classOrNull("net.minecraft.network.chat.TextColor");
				if (styleClass != null && textColorClass != null) {
					styleEmpty = findField(styleClass, "EMPTY").get(null);
					textColorFromRgb = findMethod(textColorClass, "fromRgb", int.class);
					styleWithColor = findMethod(styleClass, "withColor", textColorClass);
					for (Method m : base.getClass().getMethods()) {
						if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == styleClass
								&& nameMatches(base.getClass(), "setStyle", m.getName())) {
							m.setAccessible(true);
							textSetStyle = m;
							break;
						}
					}
				}
			}
			if (styleEmpty == null || textColorFromRgb == null || styleWithColor == null || textSetStyle == null) {
				return null;
			}
			Object color = textColorFromRgb.invoke(null, rgb & 0x00FFFFFF);
			Object style = styleWithColor.invoke(styleEmpty, color);
			Object styled = textSetStyle.invoke(base, style);
			return styled instanceof Component t ? t : base;
		} catch (Throwable t) {
			warnOnce("coloredText", t);
			return null;
		}
	}

	/**
	 * 49-32차: Text 여러 개를 하나로 잇는다. 색 없는 빈 조각을 부모로 두고 붙여서
	 * 앞 조각의 색이 뒤로 새지 않게 한다(채팅 회색 문제와 같은 이유). 실패하면 첫 조각.
	 */
	public static Component join(Component... parts) {
		Component root = textLiteral("");
		if (root == null) {
			return parts.length > 0 ? parts[0] : null;
		}
		try {
			Method append = findMethod(root.getClass(), "append", Component.class);
			if (append == null) {
				return parts.length > 0 ? parts[0] : root;
			}
			Object cur = root;
			for (Component p : parts) {
				if (p == null) {
					continue;
				}
				Object next = append.invoke(cur, p);
				if (next instanceof Component t) {
					cur = t;
				}
			}
			return cur instanceof Component t ? t : root;
		} catch (Throwable t) {
			warnOnce("text:join", t);
			return parts.length > 0 ? parts[0] : root;
		}
	}

	// ==================== 49-42차: 스타일 유지 Text 분해/재조립(채팅 강조) ====================
	// StringVisitable#visit(StyledVisitor, Style)(1.16.2+)로 Text 트리를 "(상속 해결된 Style, 문자열)"
	// 조각 순서로 펼친다. 조각마다 setStyle(style)로 다시 붙이면 서버가 준 색·클릭/호버 이벤트가
	// 그대로 살고, 우리가 원하는 구간만 색을 바꿔 끼울 수 있다. 1.15.2(visit 없음)는 false.
	private static boolean visitResolved;
	private static Class<?> styledVisitorClass;
	private static Method textVisitStyled;

	public static boolean visitStyled(Component text, java.util.function.BiConsumer<Object, String> sink) {
		if (text == null || sink == null) {
			return false;
		}
		try {
			if (!visitResolved) {
				visitResolved = true;
				Class<?> styleClass = classOrNull("net.minecraft.network.chat.Style");
				styledVisitorClass = classOrNull("net.minecraft.network.chat.FormattedText$StyledContentConsumer");
				if (styleClass != null && styledVisitorClass != null) {
					textVisitStyled = findMethod(text.getClass(), "visit", styledVisitorClass, styleClass);
					if (textVisitStyled == null) {
						for (Method m : text.getClass().getMethods()) {
							if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == styledVisitorClass
									&& m.getParameterTypes()[1] == styleClass) {
								m.setAccessible(true);
								textVisitStyled = m;
								break;
							}
						}
					}
					if (styleEmpty == null) {
						java.lang.reflect.Field f = findField(styleClass, "EMPTY");
						styleEmpty = f == null ? null : f.get(null);
					}
				}
			}
			if (textVisitStyled == null || styleEmpty == null) {
				return false;
			}
			Object visitor = java.lang.reflect.Proxy.newProxyInstance(styledVisitorClass.getClassLoader(),
				new Class<?>[]{styledVisitorClass}, (proxy, method, args) -> {
					if (args != null && args.length == 2) {
						sink.accept(args[0], String.valueOf(args[1]));
						return java.util.Optional.empty();
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("toString".equals(method.getName())) {
						return "LunaStyledVisitor";
					}
					return null;
				});
			textVisitStyled.invoke(text, visitor, styleEmpty);
			return true;
		} catch (Throwable t) {
			warnOnce("text:visit", t);
			return false;
		}
	}

	/** 주어진 Style 객체를 그대로 입힌 리터럴 Text(1.16+). 실패 시 색 없는 리터럴. */
	public static Component styledLiteral(String content, Object style) {
		Component base = textLiteral(content == null ? "" : content);
		if (base == null || style == null) {
			return base;
		}
		try {
			if (textSetStyle == null) {
				coloredText("", 0xFFFFFF); // 리졸브만 목적
			}
			if (textSetStyle == null) {
				return base;
			}
			Object styled = textSetStyle.invoke(base, style);
			return styled instanceof Component t ? t : base;
		} catch (Throwable t) {
			warnOnce("text:styledLiteral", t);
			return base;
		}
	}

	/** style.withColor(TextColor.fromRgb(rgb)) - 다른 속성(굵게·클릭 이벤트 등)은 유지. 실패 시 원래 style. */
	public static Object styleWithRgb(Object style, int rgb) {
		try {
			if (styleWithColor == null) {
				coloredText("", 0xFFFFFF);
			}
			if (styleWithColor == null || textColorFromRgb == null) {
				return style;
			}
			Object base = style != null ? style : styleEmpty;
			if (base == null) {
				return style;
			}
			Object color = textColorFromRgb.invoke(null, rgb & 0x00FFFFFF);
			Object out = styleWithColor.invoke(base, color);
			return out != null ? out : style;
		} catch (Throwable t) {
			warnOnce("text:styleWithRgb", t);
			return style;
		}
	}

	// ==================== 49-42차: UI 효과음 ====================
	// PositionedSoundInstance.master(SoundEvent, pitch)(≤1.21.10) / ui(SoundEvent, pitch)(1.21.11+) →
	// SoundManager#play. SoundEvents의 상수는 버전에 따라 SoundEvent 또는 RegistryEntry라 첫 인자
	// 타입은 isInstance로 고른다. 이름은 야른 기준으로 후보 검사(프로덕션은 intermediary 매핑).
	private static final java.util.Map<String, Method> UI_SOUND_FACTORY = new java.util.concurrent.ConcurrentHashMap<>();

	public static boolean playUiSound(Minecraft client, String soundEventsField, float pitch) {
		try {
			if (client == null) {
				return false;
			}
			Class<?> events = classOrNull("net.minecraft.sounds.SoundEvents");
			Class<?> positioned = classOrNull("net.minecraft.client.resources.sounds.SimpleSoundInstance");
			if (events == null || positioned == null) {
				return false;
			}
			java.lang.reflect.Field f = findField(events, soundEventsField);
			Object event = f == null ? null : f.get(null);
			if (event == null) {
				return false;
			}
			String key = soundEventsField + "@" + event.getClass().getName();
			Method factory = UI_SOUND_FACTORY.get(key);
			if (factory == null) {
				for (Method m : positioned.getMethods()) {
					if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 2) {
						continue;
					}
					Class<?>[] p = m.getParameterTypes();
					if (p[1] != float.class || !p[0].isInstance(event)) {
						continue;
					}
					String n = m.getName();
					if (nameMatches(positioned, "master", n) || nameMatches(positioned, "forUI", n)) {
						m.setAccessible(true);
						factory = m;
						break;
					}
				}
				if (factory == null) {
					return false;
				}
				UI_SOUND_FACTORY.put(key, factory);
			}
			Object instance = factory.invoke(null, event, pitch);
			Object manager = callNoArg(client, "getSoundManager");
			if (instance == null || manager == null) {
				return false;
			}
			Class<?> soundInstance = classOrNull("net.minecraft.client.resources.sounds.SoundInstance");
			Method play = soundInstance == null ? null : findMethod(manager.getClass(), "play", soundInstance);
			if (play == null) {
				return false;
			}
			play.invoke(manager, instance);
			return true;
		} catch (Throwable t) {
			warnOnce("sound:ui", t);
			return false;
		}
	}

	// ==================== 49-22차: DrawContext 2D 변환(이동/회전/배율) ====================
	// 안티앨리어싱 선(LunaProjection.lineAA)을 "픽셀 수천 개 fill" 대신 "회전한 사각형 3장(중심 + 양쪽
	// 페더 그라데이션)"으로 그리기 위한 행렬 스택 조작. 이름표 거리 축소(scale)에도 쓴다.
	//   1.21.6+  DrawContext#getMatrices() = org.joml.Matrix3x2fStack (pushMatrix/popMatrix/translate/rotate/scale)
	//   ≤1.21.5  MatrixStack (push/pop/translate(FFF)/scale(FFF)/multiply(Quaternionf))
	// 전부 리플렉션(한 번 해석 후 캐시) - 어느 쪽도 못 찾으면 guiTransformSupported()가 false를 돌려주고
	// 호출부는 픽셀 방식으로 폴백한다.
	private static boolean guiXformResolved;
	private static boolean guiXformOk;
	private static boolean guiXformJoml;
	private static Method guiGetMatrices, guiPush, guiPop, guiTranslate, guiRotate, guiScale, guiMultiply;
	private static java.lang.reflect.Constructor<?> guiQuatCtor;
	private static Method guiQuatRotZ;

	private static void resolveGuiTransform(GuiGraphicsExtractor ctx) {
		guiXformResolved = true;
		try {
			guiGetMatrices = findNoArgMethod(ctx.getClass(), "pose");
			if (guiGetMatrices == null) {
				return;
			}
			Object stack = guiGetMatrices.invoke(ctx);
			if (stack == null) {
				return;
			}
			Class<?> sc = stack.getClass();
			Class<?> joml = classOrNull("org.joml.Matrix3x2fStack");
			if (joml != null && joml.isInstance(stack)) {
				guiPush = joml.getMethod("pushMatrix");
				guiPop = joml.getMethod("popMatrix");
				guiTranslate = joml.getMethod("translate", float.class, float.class);
				guiRotate = joml.getMethod("rotate", float.class);
				guiScale = joml.getMethod("scale", float.class, float.class);
				guiXformJoml = true;
				guiXformOk = true;
				return;
			}
			guiPush = findNoArgMethod(sc, "push");
			guiPop = findNoArgMethod(sc, "pop");
			guiTranslate = findMethod(sc, "translate", float.class, float.class, float.class);
			if (guiTranslate == null) {
				guiTranslate = findMethod(sc, "translate", double.class, double.class, double.class);
			}
			guiScale = findMethod(sc, "scale", float.class, float.class, float.class);
			Class<?> quat = classOrNull("org.joml.Quaternionf");
			if (quat != null) {
				guiQuatCtor = quat.getConstructor();
				guiQuatRotZ = quat.getMethod("rotationZ", float.class);
				for (Method m : sc.getMethods()) {
					if (m.getParameterCount() == 1 && nameMatches(sc, "multiply", m.getName())
							&& m.getParameterTypes()[0].isAssignableFrom(quat)) {
						m.setAccessible(true);
						guiMultiply = m;
						break;
					}
				}
			}
			guiXformOk = guiPush != null && guiPop != null && guiTranslate != null && guiScale != null;
		} catch (Throwable t) {
			warnOnce("guiTransform", t);
			guiXformOk = false;
		}
	}

	/** 이 DrawContext에서 2D 변환(이동/배율)을 쓸 수 있는지. */
	public static boolean guiTransformSupported(GuiGraphicsExtractor ctx) {
		if (ctx == null) {
			return false;
		}
		if (!guiXformResolved) {
			resolveGuiTransform(ctx);
		}
		return guiXformOk;
	}

	/** 가위 자르기(enableScissor)가 지금 행렬(배율/이동)을 따르는지. 1.21.6+ (Matrix3x2fStack)만 따른다. */
	public static boolean guiScissorFollowsPose(GuiGraphicsExtractor ctx) {
		return guiTransformSupported(ctx) && guiXformJoml;
	}

	/** 회전까지 되는지(구버전 MatrixStack에서 Quaternionf multiply를 못 찾으면 false). */
	public static boolean guiRotateSupported(GuiGraphicsExtractor ctx) {
		return guiTransformSupported(ctx) && (guiXformJoml ? guiRotate != null : (guiMultiply != null && guiQuatCtor != null));
	}

	private static Object guiStack(GuiGraphicsExtractor ctx) throws Exception {
		return guiGetMatrices.invoke(ctx);
	}

	public static void guiPush(GuiGraphicsExtractor ctx) {
		if (!guiTransformSupported(ctx)) {
			return;
		}
		try {
			guiPush.invoke(guiStack(ctx));
		} catch (Throwable t) {
			warnOnce("guiPush", t);
		}
	}

	public static void guiPop(GuiGraphicsExtractor ctx) {
		if (!guiTransformSupported(ctx)) {
			return;
		}
		try {
			guiPop.invoke(guiStack(ctx));
		} catch (Throwable t) {
			warnOnce("guiPop", t);
		}
	}

	public static void guiTranslate(GuiGraphicsExtractor ctx, float x, float y) {
		if (!guiTransformSupported(ctx)) {
			return;
		}
		try {
			Object stack = guiStack(ctx);
			if (guiXformJoml) {
				guiTranslate.invoke(stack, x, y);
			} else if (guiTranslate.getParameterTypes()[0] == float.class) {
				guiTranslate.invoke(stack, x, y, 0f);
			} else {
				guiTranslate.invoke(stack, (double) x, (double) y, 0.0);
			}
		} catch (Throwable t) {
			warnOnce("guiTranslate", t);
		}
	}

	/** Z축(화면 평면) 회전 - 라디안. */
	public static void guiRotate(GuiGraphicsExtractor ctx, float radians) {
		if (!guiRotateSupported(ctx)) {
			return;
		}
		try {
			Object stack = guiStack(ctx);
			if (guiXformJoml) {
				guiRotate.invoke(stack, radians);
			} else {
				Object q = guiQuatCtor.newInstance();
				guiQuatRotZ.invoke(q, radians);
				guiMultiply.invoke(stack, q);
			}
		} catch (Throwable t) {
			warnOnce("guiRotate", t);
		}
	}

	public static void guiScale(GuiGraphicsExtractor ctx, float sx, float sy) {
		if (!guiTransformSupported(ctx)) {
			return;
		}
		try {
			Object stack = guiStack(ctx);
			if (guiXformJoml) {
				guiScale.invoke(stack, sx, sy);
			} else {
				guiScale.invoke(stack, sx, sy, 1f);
			}
		} catch (Throwable t) {
			warnOnce("guiScale", t);
		}
	}
	// ==================== 49-22차: 바닐라 키 지정 목록(겹침 경고 · 키보드 화면 표시) ====================
	/** 바닐라 키바인딩 하나: 키 코드(마우스면 MOUSE_KEY_BASE + 버튼) + 번역된 이름. */
	public record VanillaKey(int code, String label) {
	}

	private static List<VanillaKey> vanillaKeysCache = java.util.Collections.emptyList();
	private static long vanillaKeysStamp;
	private static Method boundKeyOfMethod;
	private static boolean boundKeyOfResolved;

	/** 지금 바닐라(및 다른 모드)의 키 지정 전부. 1초 캐시. */
	public static List<VanillaKey> vanillaKeys(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - vanillaKeysStamp < 1000 && !vanillaKeysCache.isEmpty()) {
			return vanillaKeysCache;
		}
		vanillaKeysStamp = now;
		List<VanillaKey> out = new java.util.ArrayList<>();
		try {
			if (client == null || client.options == null) {
				return out;
			}
			java.lang.reflect.Field f = findField(client.options.getClass(), "keyMappings");
			if (f == null) {
				f = findField(client.options.getClass(), "keysAll");
			}
			if (f == null) {
				return out;
			}
			Object arr = f.get(client.options);
			if (!(arr instanceof Object[] keys)) {
				return out;
			}
			for (Object kb : keys) {
				if (kb == null) {
					continue;
				}
				Object key = boundKeyOf(kb);
				if (key == null) {
					continue;
				}
				Method getCode = findNoArgMethod(key.getClass(), "getValue");
				if (getCode == null) {
					getCode = findNoArgMethod(key.getClass(), "getKeyCode");
				}
				Method getCat = findNoArgMethod(key.getClass(), "getCategory");
				if (getCode == null) {
					continue;
				}
				int code = ((Number) getCode.invoke(key)).intValue();
				if (code < 0) {
					continue;
				}
				Object cat = getCat == null ? null : getCat.invoke(key);
				if (cat != null && enumNameIs(cat, "MOUSE")) {
					code = mouseKeyCode(LunaInput.toGlfwButton(code));
				} else if (cat != null && !enumNameIs(cat, "KEYSYM") && !enumNameIs(cat, "KEYBOARD")) {
					continue; // 스캔코드 지정 등은 비교 대상 아님
				}
				Method idMethod = findNoArgMethod(kb.getClass(), "getTranslationKey");
				if (idMethod == null) {
					idMethod = findNoArgMethod(kb.getClass(), "getId");
				}
				String rawId = idMethod == null ? "?" : String.valueOf(idMethod.invoke(kb));
				// 49-34차(사용자: "F3이랑 연동하는 키들은 겹친다고 하면 안되지"): 1.21.9+는 F3+A(청크 다시 읽기)·
				// F3+B(히트박스)… 같은 조합이 진짜 키바인딩(key.debug.*)으로 등록돼 있어서, A·B·C…가
				// "마크 키"로 잡혀 우리 키와 겹친다고 나왔다. F3 자체(F3 키 하나)만 남기고 조합용은 뺀다.
				if (rawId.contains("debug") && code != com.mojang.blaze3d.platform.InputConstants.KEY_F3) {
					continue;
				}
				String label = translate(rawId);
				out.add(new VanillaKey(code, label));
			}
		} catch (Throwable t) {
			warnOnce("vanillaKeys", t);
		}
		vanillaKeysCache = out;
		return out;
	}

	// ==================== 49-89차(8-17): 마인크래프트 키마다 보조 키 ====================
	/** 바닐라 키바인딩 하나: 객체 + 번역 키(id) + 번역된 이름. */
	public record VanillaBinding(Object binding, String id, String label) {
	}

	/** 지금 options.allKeys에 등록된 키바인딩 전부(디버그 조합 F3+X 제외). 실패하면 빈 목록. */
	public static List<VanillaBinding> vanillaKeyBindings(Minecraft client) {
		List<VanillaBinding> out = new java.util.ArrayList<>();
		try {
			if (client == null || client.options == null) {
				return out;
			}
			java.lang.reflect.Field f = findField(client.options.getClass(), "keyMappings");
			if (f == null) {
				f = findField(client.options.getClass(), "keysAll");
			}
			if (f == null || !(f.get(client.options) instanceof Object[] keys)) {
				return out;
			}
			for (Object kb : keys) {
				if (kb == null) {
					continue;
				}
				Method idMethod = findNoArgMethod(kb.getClass(), "getTranslationKey");
				if (idMethod == null) {
					idMethod = findNoArgMethod(kb.getClass(), "getId");
				}
				String rawId = idMethod == null ? null : String.valueOf(idMethod.invoke(kb));
				if (rawId == null || rawId.contains("debug")) {
					continue;
				}
				out.add(new VanillaBinding(kb, rawId, translate(rawId)));
			}
		} catch (Throwable t) {
			warnOnce("vanillaKeyBindings", t);
		}
		return out;
	}

	/** KeyBinding.setKeyPressed(Key, boolean)을 리플렉션으로(믹스인이 보조 키를 원래 키로 흘려보낼 때). */
	public static void keyBindingSetPressed(Object key, boolean pressed) {
		try {
			Class<?> kb = resolveClass("net.minecraft.client.KeyMapping", "net.minecraft.client.options.KeyBinding");
			Method m = getMethodCompat(kb, "set", classForName("com.mojang.blaze3d.platform.InputConstants$Key"), boolean.class);
			m.invoke(null, key, pressed);
		} catch (Throwable t) {
			warnOnce("keyBindingSetPressed", t);
		}
	}

	public static void keyBindingOnPressed(Object key) {
		try {
			Class<?> kb = resolveClass("net.minecraft.client.KeyMapping", "net.minecraft.client.options.KeyBinding");
			Method m = getMethodCompat(kb, "click", classForName("com.mojang.blaze3d.platform.InputConstants$Key"));
			m.invoke(null, key);
		} catch (Throwable t) {
			warnOnce("keyBindingOnPressed", t);
		}
	}

	/** 키바인딩에 지금 묶인 InputUtil.Key(없으면 null). */
	public static Object boundKeyOfBinding(Object keyBinding) {
		try {
			return boundKeyOf(keyBinding);
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** Luna 키 코드(GLFW 키 또는 MOUSE_KEY_BASE+버튼; 보조키 비트는 무시) → InputUtil.Key. 실패하면 null. */
	public static Object inputKeyFromLunaCode(int lunaCode) {
		try {
			int base = lunaCode & KEY_MASK;
			Class<?> typeClass = classForName("com.mojang.blaze3d.platform.InputConstants$Type");
			Object type = isMouseKeyCode(base) ? getFieldCompat(typeClass, "MOUSE").get(null) : LunaInput.keyboardKey(-1).getType();
			// 49-215차: 우리 마우스 번호는 GLFW 기준 - 26.3(SDL) 게임 번호로 바꿔서
			int code = isMouseKeyCode(base) ? LunaInput.fromGlfwButton(base - MOUSE_KEY_BASE) : base;
			return getMethodCompat(typeClass, "getOrCreate", int.class).invoke(type, code);
		} catch (Throwable t) {
			warnOnce("inputKeyFromLunaCode", t);
			return null;
		}
	}

	/**
	 * 49-42차: 키바인딩 하나의 GLFW 키 코드(마우스 버튼이면 MOUSE_KEY_BASE + 버튼, 스캔코드/미지정이면 -1).
	 * 인벤토리 연 채 이동(InventoryMoveModule)이 이동 키 상태를 직접 폴링하는 데 쓴다.
	 */
	public static int boundKeyCode(Object keyBinding) {
		try {
			Object key = boundKeyOf(keyBinding);
			if (key == null) {
				return -1;
			}
			Method getCode = findNoArgMethod(key.getClass(), "getValue");
			if (getCode == null) {
				getCode = findNoArgMethod(key.getClass(), "getKeyCode");
			}
			if (getCode == null) {
				return -1;
			}
			int code = ((Number) getCode.invoke(key)).intValue();
			Method getCat = findNoArgMethod(key.getClass(), "getCategory");
			Object cat = getCat == null ? null : getCat.invoke(key);
			if (cat != null && enumNameIs(cat, "MOUSE")) {
				return mouseKeyCode(LunaInput.toGlfwButton(code));
			}
			if (cat != null && !enumNameIs(cat, "KEYSYM") && !enumNameIs(cat, "KEYBOARD")) {
				return -1;
			}
			return code;
		} catch (Throwable t) {
			warnOnce("boundKeyCode", t);
			return -1;
		}
	}

	private static Object[] movementKeysCache;

	/** 이동 키바인딩 7개: 앞/뒤/왼/오른/점프/웅크리기/달리기. 1.17+ forwardKey…, 1.16 이하 keyForward… (javap 실측). */
	public static Object[] movementKeys(Minecraft client) {
		if (movementKeysCache != null) {
			return movementKeysCache;
		}
		String[][] names = {{"forwardKey", "keyForward"}, {"backKey", "keyBack"}, {"leftKey", "keyLeft"}, {"rightKey", "keyRight"},
			{"jumpKey", "keyJump"}, {"sneakKey", "keySneak"}, {"sprintKey", "keySprint"}};
		Object[] out = new Object[names.length];
		try {
			for (int i = 0; i < names.length; i++) {
				java.lang.reflect.Field f = null;
				for (String n : names[i]) {
					f = findField(client.options.getClass(), n);
					if (f != null) {
						break;
					}
				}
				out[i] = f == null ? null : f.get(client.options);
			}
		} catch (Throwable t) {
			warnOnce("movementKeys", t);
		}
		movementKeysCache = out;
		return out;
	}

	/** KeyBinding#setPressed(boolean) - 전 버전 public (1.20.1 javap·1.21.11 tiny). */
	public static void setKeyPressed(Object keyBinding, boolean pressed) {
		if (keyBinding == null) {
			return;
		}
		try {
			Method m = getMethodCompat(keyBinding.getClass(), "setDown", boolean.class);
			m.setAccessible(true);
			m.invoke(keyBinding, pressed);
		} catch (Throwable t) {
			warnOnce("setKeyPressed", t);
		}
	}

	/** 화면에서 글자 입력칸이 포커스를 갖고 있는가(그럴 땐 WASD를 이동으로 쓰면 안 됨). */
	public static boolean isTextFieldFocused(Object screen) {
		if (screen == null) {
			return false;
		}
		try {
			Object focused = callNoArg(screen, "getFocused");
			return focused instanceof net.minecraft.client.gui.components.EditBox;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Fabric KeyBindingHelper.getBoundKeyOf(kb) → InputUtil.Key(없으면 KeyBinding#boundKey 필드). */
	private static Object boundKeyOf(Object keyBinding) throws Exception {
		if (!boundKeyOfResolved) {
			boundKeyOfResolved = true;
			Class<?> helper = resolveClass("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
					"net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
			if (helper != null) {
				for (Method m : helper.getMethods()) {
					if (m.getName().equals("getBoundKeyOf") && m.getParameterCount() == 1) {
						boundKeyOfMethod = m;
						break;
					}
				}
			}
		}
		if (boundKeyOfMethod != null) {
			try {
				return boundKeyOfMethod.invoke(null, keyBinding);
			} catch (Throwable ignored) {
			}
		}
		java.lang.reflect.Field bf = findField(keyBinding.getClass(), "key");
		return bf == null ? null : bf.get(keyBinding);
	}

	private static Method i18nTranslate;
	private static boolean i18nResolved;

	/**
	 * 49-32차: 지금 든 아이템이 그 블록을 캐기에 **맞는 도구**인지.
	 * 1.17+ ItemStack#isSuitableFor(BlockState) / 그 전 Item#isEffectiveOn(BlockState).
	 * 판단할 수 없으면 null(표시하지 않음).
	 */
	public static Boolean isSuitableTool(ItemStack stack, Object blockState) {
		if (stack == null || stack.isEmpty() || blockState == null) {
			return null;
		}
		try {
			for (Method m : stack.getClass().getMethods()) {
				if (m.getParameterCount() != 1 || m.getReturnType() != boolean.class) {
					continue;
				}
				if (!nameMatches(stack.getClass(), "isCorrectToolForDrops", m.getName())) {
					continue;
				}
				if (!m.getParameterTypes()[0].isInstance(blockState)) {
					continue;
				}
				m.setAccessible(true);
				return (Boolean) m.invoke(stack, blockState);
			}
			Object item = stack.getItem();
			for (Method m : item.getClass().getMethods()) {
				if (m.getParameterCount() != 1 || m.getReturnType() != boolean.class) {
					continue;
				}
				if (!nameMatches(item.getClass(), "isEffectiveOn", m.getName())) {
					continue;
				}
				if (!m.getParameterTypes()[0].isInstance(blockState)) {
					continue;
				}
				m.setAccessible(true);
				return (Boolean) m.invoke(item, blockState);
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** 필드 값 하나 읽기(이름이 버전마다 다를 수 있어 여러 개 시도). 못 읽으면 null. */
	public static Object getFieldValue(Object owner, String... names) {
		if (owner == null) {
			return null;
		}
		for (String n : names) {
			try {
				java.lang.reflect.Field f = findField(owner.getClass(), n);
				if (f != null) {
					f.setAccessible(true);
					Object v = f.get(owner);
					if (v != null) {
						return v;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/**
	 * 49-32차: 바닐라 채팅에 남아 있는 줄들을 문자열로(최신 → 과거 순).
	 * 우리 모듈이 켜지기 전에 온 메시지도 검색할 수 있게 하는 폴백.
	 */
	public static java.util.List<String> chatHudMessages(Minecraft client) {
		java.util.List<String> out = new java.util.ArrayList<>();
		try {
			Object hud = inGameHud(client);
			Object chatHud = hud == null ? null : callNoArg(hud, "getChatHud");
			if (chatHud == null) {
				return out;
			}
			Object messages = getFieldValue(chatHud, "messages");
			if (!(messages instanceof java.util.List<?> list)) {
				return out;
			}
			for (Object line : list) {
				Object content = callNoArg(line, "content");
				if (content == null) {
					content = getFieldValue(line, "content");
				}
				if (content == null) {
					content = getFieldValue(line, "text");
				}
				if (content instanceof Component t) {
					out.add(t.getString());
				} else if (content != null) {
					out.add(String.valueOf(content));
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** I18n.translate(key) - 실패하면 key 그대로. */
	public static String translate(String key) {
		if (key == null) {
			return "";
		}
		try {
			if (!i18nResolved) {
				i18nResolved = true;
				Class<?> i18n = classOrNull("net.minecraft.client.resources.language.I18n");
				if (i18n != null) {
					i18nTranslate = findMethod(i18n, "translate", String.class, Object[].class);
				}
			}
			if (i18nTranslate != null) {
				Object r = i18nTranslate.invoke(null, key, new Object[0]);
				if (r instanceof String str) {
					return str;
				}
			}
		} catch (Throwable ignored) {
		}
		return key;
	}

	/** 이 키 코드에 지정된 바닐라 기능 이름(없으면 null). 겹침 경고용. */
	public static String vanillaKeyLabelFor(Minecraft client, int code) {
		if (code < 0) {
			return null;
		}
		for (VanillaKey k : vanillaKeys(client)) {
			if (k.code() == code) {
				return k.label();
			}
		}
		return null;
	}

	// ==================== 49-23차: 툴팁 꾸미기 / 셜커 격자 지원 ====================

	/** 인챈트 하나: id("minecraft:sharpness"), 레벨, 최대 레벨, 바닐라 표시 텍스트("날카로움 V"). */
	public record EnchantInfo(String id, int level, int maxLevel, Component name) {
	}

	/**
	 * 스택의 인챈트를 id/레벨/최대/표시 텍스트까지 모아서 돌려줌(순서 = 툴팁 순서).
	 *  ~1.20.6: EnchantmentHelper.get(stack) → Map<Enchantment,Integer>, Enchantment#getName(int)/getTranslationKey()
	 *  1.21+  : EnchantmentHelper.getEnchantments(stack) → ItemEnchantmentsComponent(RegistryEntry), Enchantment.getName(entry, level) static
	 */
	public static List<EnchantInfo> enchantments(ItemStack stack) {
		List<EnchantInfo> out = new java.util.ArrayList<>();
		if (stack == null || stack.isEmpty()) {
			return out;
		}
		try {
			Class<?> helperClass = classForName("net.minecraft.world.item.enchantment.EnchantmentHelper");
			Class<?> enchClass = classOrNull("net.minecraft.world.item.enchantment.Enchantment");
			for (Method m : helperClass.getMethods()) {
				if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1
						|| !m.getParameterTypes()[0].isInstance(stack)) {
					continue;
				}
				if (!nameMatches(helperClass, "get", m.getName()) && !nameMatches(helperClass, "getEnchantments", m.getName())) {
					continue;
				}
				Object result;
				try {
					result = m.invoke(null, stack);
				} catch (Throwable ignored) {
					continue;
				}
				if (result == null) {
					continue;
				}
				if (result instanceof java.util.Map<?, ?> map) {
					for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
						Object ench = e.getKey();
						int level = e.getValue() instanceof Integer i ? i : 0;
						Object nameObj = call1(ench, "getName", level);
						String key = callNoArg(ench, "getTranslationKey") instanceof String k ? k : null;
						out.add(new EnchantInfo(enchantId(key, null), level, getEnchantmentMaxLevel(ench),
								nameObj instanceof Component t ? t : null));
					}
					return out;
				}
				Object entries = callNoArg(result, "getEnchantments");
				if (entries instanceof Iterable<?> iterable) {
					Method getName = null;
					if (enchClass != null) {
						for (Method mm : enchClass.getMethods()) {
							if (Modifier.isStatic(mm.getModifiers()) && mm.getParameterCount() == 2
									&& nameMatches(enchClass, "getName", mm.getName())) {
								getName = mm;
								break;
							}
						}
					}
					for (Object entry : iterable) {
						Object levelObj = call1(result, "getLevel", entry);
						int level = levelObj instanceof Integer i ? i : 0;
						Object ench = resolveMember(entry, "value");
						Component name = null;
						if (getName != null) {
							try {
								Object n = getName.invoke(null, entry, level);
								if (n instanceof Component t) {
									name = t;
								}
							} catch (Throwable ignored) {
							}
						}
						String id = null;
						Object keyOpt = callNoArg(entry, "getKey");
						if (keyOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
							Object ident = callNoArg(opt.get(), "getValue");
							if (ident != null) {
								id = ident.toString();
							}
						}
						String tkey = null;
						if (id == null && ench != null) {
							Object desc = callNoArg(ench, "description");
							if (desc instanceof Component t) {
								tkey = translationKeyOf(t);
							}
						}
						out.add(new EnchantInfo(enchantId(tkey, id), level,
								getEnchantmentMaxLevel(ench != null ? ench : entry), name));
					}
					return out;
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** "enchantment.minecraft.sharpness" 같은 번역 키 또는 Identifier 문자열 → "minecraft:sharpness". */
	private static String enchantId(String translationKey, String identifier) {
		if (identifier != null && !identifier.isEmpty()) {
			return identifier.contains(":") ? identifier : "minecraft:" + identifier;
		}
		if (translationKey == null) {
			return "";
		}
		String k = translationKey.startsWith("enchantment.") ? translationKey.substring("enchantment.".length()) : translationKey;
		int dot = k.indexOf('.');
		return dot > 0 ? k.substring(0, dot) + ":" + k.substring(dot + 1) : "minecraft:" + k;
	}

	/** Text가 번역 텍스트면 그 키("container.shulkerBox.itemCount" 등), 아니면 null. 1.19+ TextContent / 그 전 TranslatableText. */
	public static String translationKeyOf(Component text) {
		if (text == null) {
			return null;
		}
		try {
			Object content = callNoArg(text, "getContents");
			Object src = content != null ? content : text;
			Object key = callNoArg(src, "getKey");
			if (key instanceof String s) {
				return s;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** 49-25차: 번역 텍스트 조각(키 + 인자). 인자는 문자열이거나 Text. */
	public record TranslatablePart(String key, Object[] args) {
	}

	/**
	 * 49-25차: 텍스트와 그 형제(siblings) 전체에서 번역 조각을 순서대로 모음. 바닐라 능력치 줄이
	 * " " 리터럴 + 번역 형제(1.16~)라서 최상위 키만 봐서는 못 알아본다. 번역이 없으면 빈 리스트.
	 */
	public static List<TranslatablePart> translatableParts(Component text) {
		List<TranslatablePart> out = new java.util.ArrayList<>(2);
		collectTranslatable(text, out, 0);
		return out;
	}

	private static void collectTranslatable(Component text, List<TranslatablePart> out, int depth) {
		if (text == null || depth > 6) {
			return;
		}
		try {
			Object content = callNoArg(text, "getContents");
			Object src = content != null ? content : text;
			Object key = callNoArg(src, "getKey");
			if (key instanceof String k) {
				Object args = callNoArg(src, "getArgs");
				out.add(new TranslatablePart(k, args instanceof Object[] a ? a : new Object[0]));
			}
			Object siblings = callNoArg(text, "getSiblings");
			if (siblings instanceof Iterable<?> it) {
				for (Object o : it) {
					if (o instanceof Component t) {
						collectTranslatable(t, out, depth + 1);
					}
				}
			}
		} catch (Throwable ignored) {
		}
	}

	/** 49-25차: 텍스트가 빈 줄(내용 없음)인지. */
	public static boolean isBlankText(Component text) {
		if (text == null) {
			return true;
		}
		try {
			String s = text.getString();
			return s == null || s.trim().isEmpty();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static int toolMode = -1; // 0=TOOL 컴포넌트(1.21+), 1=ToolItem#getMaterial(~1.20.6), -2=없음
	private static Object toolComponentType;
	private static Method toolGet;

	/**
	 * 49-25차: 도구의 기본 채굴 속도(나무 2 · 돌 4 · 철 6 · 다이아 8 · 네더라이트 9 · 금 12). 1.21+ TOOL 컴포넌트
	 * defaultMiningSpeed(), 그 전 ToolItem#getMaterial()#getMiningSpeedMultiplier(). 모르면 -1.
	 */
	public static float miningSpeed(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return -1f;
		}
		if (toolMode == -1) {
			toolMode = -2;
			Class<?> typesClass = classOrNull("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classOrNull("net.minecraft.core.component.DataComponentType");
			if (typesClass != null && componentTypeClass != null) {
				try {
					java.lang.reflect.Field f = findField(typesClass, "TOOL");
					if (f != null) {
						toolComponentType = f.get(null);
						toolGet = getMethodCompat(ItemStack.class, "get", componentTypeClass);
						toolMode = 0;
					}
				} catch (Throwable ignored) {
				}
			}
			if (toolMode == -2 && classOrNull("net.minecraft.item.ToolItem") != null) {
				toolMode = 1;
			}
		}
		try {
			if (toolMode == 0) {
				Object tool = toolGet.invoke(stack, toolComponentType);
				if (tool == null) {
					return -1f;
				}
				Object v = callNoArg(tool, "defaultMiningSpeed");
				return v instanceof Number n ? n.floatValue() : -1f;
			}
			if (toolMode == 1) {
				Object material = callNoArg(stack.getItem(), "getMaterial");
				Object v = material == null ? null : callNoArg(material, "getDestroySpeed");
				if (v == null && material != null) {
					v = callNoArg(material, "getDestroySpeed"); // 1.15.2
				}
				return v instanceof Number n ? n.floatValue() : -1f;
			}
		} catch (Throwable ignored) {
		}
		return -1f;
	}

	/** 아이템 등급 이름(COMMON/UNCOMMON/RARE/EPIC). 못 읽으면 "COMMON". */
	public static String rarityName(ItemStack stack) {
		try {
			Object r = stack.getRarity();
			if (r instanceof Enum<?> e) {
				return e.name();
			}
		} catch (Throwable ignored) {
		}
		return "COMMON";
	}

	/** F3+H 고급 툴팁이 켜져 있는지(그러면 내구도/ID를 바닐라가 이미 보여줌). */
	public static boolean advancedTooltips(Minecraft client) {
		try {
			Object options = client.options;
			java.lang.reflect.Field f = findField(options.getClass(), "advancedItemTooltips");
			if (f != null) {
				Object v = f.get(options);
				return v instanceof Boolean b && b;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/**
	 * 아이템의 "NBT"를 요약 줄들로. 1.20.5+는 컴포넌트 변경분(getComponentChanges) 항목별로, 그 전은
	 * NBT 컴파운드(getNbt/getTag)의 SNBT 문자열을 maxChars 단위로 잘라서. 없으면 빈 리스트.
	 */
	public static List<String> nbtSummary(ItemStack stack, int maxLines, int maxChars) {
		List<String> out = new java.util.ArrayList<>();
		if (stack == null || stack.isEmpty() || maxLines <= 0) {
			return out;
		}
		try {
			Object changes = callNoArg(stack, "getComponentsPatch");
			if (changes != null) {
				Object set = callNoArg(changes, "entrySet");
				if (set instanceof Iterable<?> it) {
					for (Object o : it) {
						if (!(o instanceof java.util.Map.Entry<?, ?> e)) {
							continue;
						}
						String type = registryIdOf("DATA_COMPONENT_TYPE", e.getKey());
						Object v = e.getValue();
						String val = v instanceof java.util.Optional<?> opt ? (opt.isPresent() ? String.valueOf(opt.get()) : "(제거)") : String.valueOf(v);
						String line = (type == null ? "?" : type) + " = " + val;
						if (line.length() > maxChars) {
							line = line.substring(0, maxChars - 1) + "…";
						}
						out.add(line);
						if (out.size() >= maxLines) {
							break;
						}
					}
				}
				return out;
			}
			Object nbt = callNoArg(stack, "getNbt");
			if (nbt == null) {
				nbt = callNoArg(stack, "getTag");
			}
			if (nbt == null) {
				return out;
			}
			String snbt = nbt.toString();
			for (int i = 0; i < snbt.length() && out.size() < maxLines; i += maxChars) {
				String part = snbt.substring(i, Math.min(snbt.length(), i + maxChars));
				if (out.size() == maxLines - 1 && i + maxChars < snbt.length()) {
					part = part.substring(0, Math.max(0, part.length() - 1)) + "…";
				}
				out.add(part);
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** Registries.<registryField>(또는 구버전 Registry)에서 값의 Identifier 경로("minecraft:custom_data") 조회. */
	public static String registryIdOf(String registryField, Object value) {
		if (value == null) {
			return null;
		}
		for (String holderClassName : new String[]{"net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.util.registry.Registry"}) {
			try {
				Class<?> cls = classForName(holderClassName);
				Object registry = getFieldCompat(cls, registryField).get(null);
				Object id = registryKeyOf(registry, value);
				if (id != null) {
					return id.toString();
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/**
	 * 셜커 상자 등 컨테이너 아이템의 슬롯별 내용물(빈 칸은 ItemStack.EMPTY, 슬롯 순서 유지). 못 읽으면 null.
	 *  1.20.5+: CONTAINER 컴포넌트의 stream()(빈 칸 포함, 슬롯 순서)
	 *  ~1.20.4: NBT BlockEntityTag.Items[{Slot, id, Count}] → ItemStack.fromNbt/fromTag
	 */
	public static List<ItemStack> containerSlots(ItemStack stack, int slots) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		List<ItemStack> list = new java.util.ArrayList<>(slots);
		for (int i = 0; i < slots; i++) {
			list.add(ItemStack.EMPTY);
		}
		try {
			Class<?> typesClass = classOrNull("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classOrNull("net.minecraft.core.component.DataComponentType");
			if (typesClass != null && componentTypeClass != null) {
				Object containerType = getFieldCompat(typesClass, "CONTAINER").get(null);
				Method get = getMethodCompat(ItemStack.class, "get", componentTypeClass);
				Object container = get.invoke(stack, containerType);
				if (container == null) {
					return null;
				}
				Object stream = callNoArg(container, "stream");
				if (stream instanceof java.util.stream.Stream<?> st) {
					int i = 0;
					for (Object o : st.toList()) {
						if (i >= slots) {
							break;
						}
						if (o instanceof ItemStack is) {
							list.set(i, is);
						}
						i++;
					}
					return list;
				}
				Object iterable = callNoArg(container, "nonEmptyItems");
				if (iterable instanceof Iterable<?> it) {
					int i = 0;
					for (Object o : it) {
						if (i >= slots) {
							break;
						}
						if (o instanceof ItemStack is) {
							list.set(i++, is);
						}
					}
					return list;
				}
				return null;
			}
			// NBT 시대
			Object tag = call1(stack, "getSubNbt", "BlockEntityTag");
			if (tag == null) {
				tag = call1(stack, "getSubTag", "BlockEntityTag");
			}
			if (tag == null) {
				return list; // 빈 상자
			}
			Object items = null;
			for (Method m : tag.getClass().getMethods()) {
				if (m.getParameterCount() == 2 && nameMatches(tag.getClass(), "getList", m.getName())
						&& m.getParameterTypes()[0] == String.class && m.getParameterTypes()[1] == int.class) {
					items = m.invoke(tag, "Items", 10);
					break;
				}
			}
			if (!(items instanceof java.util.AbstractList<?> nbtList)) {
				return list;
			}
			Method fromNbt = null;
			for (Method m : ItemStack.class.getMethods()) {
				if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
						&& (nameMatches(ItemStack.class, "fromNbt", m.getName()) || nameMatches(ItemStack.class, "fromTag", m.getName()))
						&& m.getReturnType() == ItemStack.class) {
					fromNbt = m;
					break;
				}
			}
			if (fromNbt == null) {
				return list;
			}
			for (Object compound : nbtList) {
				Object slotObj = call1(compound, "getByte", "Slot");
				int slot = slotObj instanceof Number n ? n.intValue() : -1;
				if (slot < 0 || slot >= slots) {
					continue;
				}
				Object is = fromNbt.invoke(null, compound);
				if (is instanceof ItemStack st) {
					list.set(slot, st);
				}
			}
			return list;
		} catch (Throwable t) {
			warnOnce("containerSlots", t);
			return null;
		}
	}

	private static Method itemOverlayMethod;
	private static boolean itemOverlayResolved;

	/** 슬롯 안 아이템 위 개수/내구도 오버레이(drawItemInSlot ≤1.21.1 / drawStackOverlay 1.21.2+). 못 찾으면 개수만 글자로. */
	public static void drawItemOverlay(GuiGraphicsExtractor ctx, Font textRenderer, ItemStack stack, int x, int y) {
		if (!itemOverlayResolved) {
			itemOverlayResolved = true;
			itemOverlayMethod = findMethod(ctx.getClass(), "drawItemInSlot", Font.class, ItemStack.class, int.class, int.class);
			if (itemOverlayMethod == null) {
				itemOverlayMethod = findMethod(ctx.getClass(), "itemDecorations", Font.class, ItemStack.class, int.class, int.class);
			}
		}
		if (itemOverlayMethod != null) {
			try {
				itemOverlayMethod.invoke(ctx, textRenderer, stack, x, y);
				return;
			} catch (Throwable t) {
				warnOnce("drawItemOverlay", t);
				itemOverlayMethod = null;
			}
		}
		if (stack.getCount() > 1) {
			String s = String.valueOf(stack.getCount());
			drawHudText(ctx, textRenderer, s, x + 17 - getTextWidth(textRenderer, s), y + 9, 0xFFFFFFFF);
		}
	}

	/** TooltipData 마커 인터페이스(1.20.5+ net.minecraft.item.tooltip / 그 전 net.minecraft.client.item)를 구현한 Proxy. 없으면 null. */
	public static Object tooltipDataProxy(InvocationHandler handler) {
		Class<?> iface = classOrNull("net.minecraft.world.inventory.tooltip.TooltipComponent");
		if (iface == null) {
			iface = classOrNull("net.minecraft.client.item.TooltipData");
		}
		if (iface == null) {
			return null;
		}
		try {
			return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
		} catch (Throwable t) {
			warnOnce("tooltipDataProxy", t);
			return null;
		}
	}

	/**
	 * Fabric TooltipComponentCallback(TooltipData → TooltipComponent) 등록. factory가 null을 돌려주면 다른
	 * 처리기/바닐라로 넘어감. 등록 성공 여부 반환.
	 */
	public static boolean registerTooltipComponentCallback(java.util.function.Function<Object, Object> factory) {
		try {
			Class<?> callbackClass = resolveClass("net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback",
					"net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback");
			if (callbackClass == null) {
				throw new ClassNotFoundException("TooltipComponentCallback");
			}
			Object proxy = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
					(InvocationHandler) (p, method, args) -> {
						if (method.getDeclaringClass() == Object.class) {
							return proxyObjectMethod(p, method, args);
						}
						if (args == null || args.length != 1) {
							return null;
						}
						return factory.apply(args[0]);
					});
			Object event = getFieldCompat(callbackClass, "EVENT").get(null);
			invokeRegister(event, proxy);
			return true;
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 툴팁 컴포넌트 콜백 등록 실패", t);
			return false;
		}
	}

	/** MatrixStack 시대(≤1.21.5)의 z 이동 - 툴팁 배경을 z=400에 그릴 때. JOML 2D 스택(1.21.6+)에서는 무시. */
	public static void guiTranslateZ(GuiGraphicsExtractor ctx, float z) {
		if (!guiTransformSupported(ctx) || guiXformJoml) {
			return;
		}
		try {
			Object stack = guiStack(ctx);
			if (guiTranslate.getParameterTypes()[0] == float.class) {
				guiTranslate.invoke(stack, 0f, 0f, z);
			} else {
				guiTranslate.invoke(stack, 0.0, 0.0, (double) z);
			}
		} catch (Throwable t) {
			warnOnce("guiTranslateZ", t);
		}
	}

	// ==================== 49-23차: 부드러운 휠 지원 ====================

	private static boolean scrollOptsResolved;
	private static Method discreteGetter, sensitivityGetter;
	private static java.lang.reflect.Field discreteField, sensitivityField;

	/** 바닐라 Mouse#onMouseScroll이 화면에 넘기는 값과 같은 계산: (불연속이면 부호만) × 휠 감도. */
	public static double scrollAmount(Minecraft client, double vertical) {
		try {
			Object options = client.options;
			if (!scrollOptsResolved) {
				scrollOptsResolved = true;
				discreteGetter = findNoArgMethod(options.getClass(), "discreteMouseScroll");
				sensitivityGetter = findNoArgMethod(options.getClass(), "mouseWheelSensitivity");
				discreteField = findField(options.getClass(), "discreteMouseScroll");
				sensitivityField = findField(options.getClass(), "mouseWheelSensitivity");
			}
			boolean discrete = false;
			double sensitivity = 1.0;
			Object d = discreteGetter != null ? optionValue(discreteGetter.invoke(options)) : (discreteField != null ? discreteField.get(options) : null);
			if (d instanceof Boolean b) {
				discrete = b;
			}
			Object sv = sensitivityGetter != null ? optionValue(sensitivityGetter.invoke(options)) : (sensitivityField != null ? sensitivityField.get(options) : null);
			if (sv instanceof Number n) {
				sensitivity = n.doubleValue();
			}
			return (discrete ? Math.signum(vertical) : vertical) * sensitivity;
		} catch (Throwable ignored) {
			return vertical;
		}
	}

	/** SimpleOption(1.19+)이면 getValue(), 아니면 그대로. */
	private static Object optionValue(Object o) {
		if (o == null || o instanceof Boolean || o instanceof Number) {
			return o;
		}
		Object v = callNoArg(o, "getValue");
		return v != null ? v : o;
	}

	private static boolean screenScrollResolved;
	private static Method screenScroll3, screenScroll4;

	/** Screen#mouseScrolled(≤1.20.1: (x,y,amount) / 1.20.2+: (x,y,horizontal,vertical)) 호출. */
	public static boolean screenScroll(Object screen, double mouseX, double mouseY, double amount) {
		if (screen == null) {
			return false;
		}
		try {
			if (!screenScrollResolved) {
				screenScrollResolved = true;
				Class<?> sc = net.minecraft.client.gui.screens.Screen.class;
				screenScroll4 = findMethod(sc, "mouseScrolled", double.class, double.class, double.class, double.class);
				screenScroll3 = findMethod(sc, "mouseScrolled", double.class, double.class, double.class);
			}
			Object r;
			if (screenScroll4 != null) {
				r = screenScroll4.invoke(screen, mouseX, mouseY, 0.0, amount);
			} else if (screenScroll3 != null) {
				r = screenScroll3.invoke(screen, mouseX, mouseY, amount);
			} else {
				return false;
			}
			return r instanceof Boolean b && b;
		} catch (Throwable t) {
			warnOnce("screenScroll", t);
			return false;
		}
	}

	private static Class<?> entryListClass;
	private static boolean entryListResolved;

	/** 화면이 목록 위젯(EntryListWidget 계열)을 갖고 있는지 - 부드러운 휠을 적용할 화면 판별용. */
	public static boolean hasListWidget(Object screen) {
		if (screen == null) {
			return false;
		}
		if (!entryListResolved) {
			entryListResolved = true;
			entryListClass = classOrNull("net.minecraft.client.gui.components.AbstractSelectionList");
			if (entryListClass == null) {
				entryListClass = classOrNull("net.minecraft.client.gui.widget.AbstractParentElement"); // 없으면 아무거나 - 결국 false
			}
		}
		if (entryListClass == null) {
			return false;
		}
		try {
			Object children = callNoArg(screen, "children");
			if (children instanceof Iterable<?> it) {
				for (Object c : it) {
					if (entryListClass.isInstance(c)) {
						return true;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	// ==================== 49-24차: 스코어보드 사이드바 데이터 ====================
	// ScoreboardTweaksModule이 바닐라 사이드바를 대신 그리기 위해 제목/항목(이름 Text, 점수 Text)을 모은다.
	//  ~1.20.2: getObjectiveForSlot(int 1=사이드바, 3+색), getAllPlayerScores → ScoreboardPlayerScore(getPlayerName/getScore)
	//  1.20.2+: getObjectiveForSlot(ScoreboardDisplaySlot), 1.20.3+: getScoreboardEntries → ScoreboardEntry(owner/value/name/formatted)
	//  팀 색 장식: Team.decorateName(AbstractTeam, Text)(1.16+ 동일)

	public record SidebarRow(Component name, Component score, int value) {
	}

	public record Sidebar(Component title, List<SidebarRow> rows) {
	}

	private static Object objectiveForSlot(Object scoreboard, int legacySlot, Object formatting) {
		try {
			for (Method m : scoreboard.getClass().getMethods()) {
				if (m.getParameterCount() != 1 || !nameMatches(scoreboard.getClass(), "getDisplayObjective", m.getName())) {
					continue;
				}
				Class<?> pt = m.getParameterTypes()[0];
				if (pt == int.class) {
					return m.invoke(scoreboard, legacySlot);
				}
				Object slot = null;
				if (formatting != null) {
					for (Method sm : pt.getMethods()) {
						if (Modifier.isStatic(sm.getModifiers()) && sm.getParameterCount() == 1
								&& nameMatches(pt, "fromLegacyFormat", sm.getName())) {
							slot = sm.invoke(null, formatting);
							break;
						}
					}
				} else if (pt.isEnum()) {
					for (Object c : pt.getEnumConstants()) {
						if (((Enum<?>) c).name().equals("SIDEBAR")) {
							slot = c;
							break;
						}
					}
				}
				return slot == null ? null : m.invoke(scoreboard, slot);
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static Method teamDecorate;
	private static boolean teamDecorateResolved;
	private static Object redNumberFormat;
	private static boolean redNumberFormatResolved;

	private static Component decorateName(Object team, Component name) {
		if (!teamDecorateResolved) {
			teamDecorateResolved = true;
			Class<?> tc = classOrNull("net.minecraft.world.scores.PlayerTeam");
			if (tc != null) {
				for (Method m : tc.getMethods()) {
					if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2 && m.getParameterTypes()[1] == Component.class
							&& (nameMatches(tc, "decorateName", m.getName()) || nameMatches(tc, "modifyText", m.getName()))) {
						teamDecorate = m;
						break;
					}
				}
			}
		}
		if (teamDecorate == null) {
			return name;
		}
		try {
			Object r = teamDecorate.invoke(null, team, name);
			return r instanceof Component t ? t : name;
		} catch (Throwable ignored) {
			return name;
		}
	}

	/** 현재 사이드바(팀 색 슬롯 우선, 없으면 일반 사이드바). 없으면 null. 점수 Text는 바닐라 표기(빨강/서버 지정 형식). */
	public static Sidebar sidebar(Minecraft client) {
		if (client == null || client.level == null || client.player == null) {
			return null;
		}
		try {
			Object scoreboard = callNoArg(client.level, "getScoreboard");
			if (scoreboard == null) {
				return null;
			}
			Object holder = callNoArg(client.player, "getScoreboardName");
			if (holder == null) {
				holder = callNoArg(client.player, "getEntityName");
			}
			if (holder == null) {
				holder = client.player.getName().getString();
			}
			Object objective = null;
			Object myTeam = call1(scoreboard, "getPlayersTeam", holder);
			if (myTeam == null) {
				myTeam = call1(scoreboard, "getPlayerTeam", holder);
			}
			if (myTeam != null) {
				Object color = callNoArg(myTeam, "getColor");
				Object idx = color == null ? null : callNoArg(color, "getColorIndex");
				if (idx instanceof Integer i && i >= 0) {
					objective = objectiveForSlot(scoreboard, 3 + i, color);
				}
			}
			if (objective == null) {
				objective = objectiveForSlot(scoreboard, 1, null);
			}
			if (objective == null) {
				return null;
			}
			Object titleObj = callNoArg(objective, "getDisplayName");
			Component title = titleObj instanceof Component t ? t : textLiteral("");

			Object entries = call1(scoreboard, "listPlayerScores", objective);
			boolean modern = entries != null;
			if (entries == null) {
				entries = call1(scoreboard, "getAllPlayerScores", objective);
			}
			if (!(entries instanceof java.util.Collection<?> coll)) {
				return new Sidebar(title, List.of());
			}
			Object numberFormat = null;
			if (modern) {
				if (!redNumberFormatResolved) {
					redNumberFormatResolved = true;
					Class<?> snf = classOrNull("net.minecraft.network.chat.numbers.StyledFormat");
					if (snf != null) {
						java.lang.reflect.Field f = findField(snf, "RED");
						if (f != null) {
							redNumberFormat = f.get(null);
						}
					}
				}
				if (redNumberFormat != null) {
					numberFormat = call1(objective, "numberFormatOrDefault", redNumberFormat);
				}
			}
			List<Object[]> raw = new java.util.ArrayList<>(); // {owner, value, nameText, scoreText}
			for (Object e : coll) {
				String owner;
				int value;
				Component nameText;
				Component scoreText;
				if (modern) {
					owner = String.valueOf(callNoArg(e, "owner"));
					Object v = callNoArg(e, "value");
					value = v instanceof Integer i ? i : 0;
					Object n = callNoArg(e, "name");
					nameText = n instanceof Component t ? t : textLiteral(owner);
					Object f = numberFormat == null ? null : call1(e, "formatted", numberFormat);
					scoreText = f instanceof Component t ? t : coloredText(String.valueOf(value), 0xFF5555);
				} else {
					owner = String.valueOf(callNoArg(e, "getNameForDisplay"));
					Object v = callNoArg(e, "getScore");
					value = v instanceof Integer i ? i : 0;
					nameText = textLiteral(owner);
					scoreText = coloredText(String.valueOf(value), 0xFF5555);
				}
				if (owner.startsWith("#")) {
					continue;
				}
				raw.add(new Object[]{owner, value, nameText, scoreText});
			}
			raw.sort((a, b) -> {
				int c = Integer.compare((Integer) b[1], (Integer) a[1]);
				return c != 0 ? c : String.CASE_INSENSITIVE_ORDER.compare((String) a[0], (String) b[0]);
			});
			List<SidebarRow> rows = new java.util.ArrayList<>();
			for (Object[] r : raw) {
				if (rows.size() >= 15) {
					break;
				}
				Object team = call1(scoreboard, "getPlayersTeam", r[0]);
				if (team == null) {
					team = call1(scoreboard, "getPlayerTeam", r[0]);
				}
				rows.add(new SidebarRow(decorateName(team, (Component) r[2]), (Component) r[3], (Integer) r[1]));
			}
			return new Sidebar(title, rows);
		} catch (Throwable t) {
			warnOnce("sidebar", t);
			return null;
		}
	}

	/**
	 * 49-115차: 아이템 설명(lore) 줄들을 문자열 목록으로. 1.20.5+는 LORE 컴포넌트(LoreComponent#lines(),
	 * List&lt;Text&gt;)에서 읽는다. 그 전 버전엔 컴포넌트가 없어 빈 목록(사용자 주 버전 1.21.11은 컴포넌트
	 * 시대라 정상 동작). 클래스를 직접 참조하지 않고 전부 리플렉션이라 40버전 어디서도 컴파일된다. 못 읽으면 빈 목록.
	 */
	public static java.util.List<String> itemLore(ItemStack stack) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (stack == null || stack.isEmpty()) {
			return out;
		}
		try {
			Class<?> typesClass = classOrNull("net.minecraft.core.component.DataComponents");
			Class<?> componentTypeClass = classOrNull("net.minecraft.core.component.DataComponentType");
			if (typesClass == null || componentTypeClass == null) {
				return out;   // 1.20.4- : 컴포넌트 없음
			}
			java.lang.reflect.Field f = findField(typesClass, "LORE");
			if (f == null) {
				return out;
			}
			Object type = f.get(null);
			Method get = findMethod(ItemStack.class, "get", componentTypeClass);
			Object lore = get == null ? null : get.invoke(stack, type);
			if (lore == null) {
				return out;
			}
			Method lines = findMethod(lore.getClass(), "lines");
			Object list = lines == null ? null : lines.invoke(lore);
			if (list instanceof java.util.List<?> l) {
				for (Object t : l) {
					if (t == null) {
						continue;
					}
					try {
						Object s = invokeNoArg(t, "getString");
						if (s != null) {
							out.add(s.toString());
						}
					} catch (Throwable ignored) {
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/**
	 * 49-122차(사용자: "아이템 정보가 뜨면 핫바 위 바닐라 이름은 안 뜨게"): InGameHud.heldItemTooltipFade를
	 * value로 덮어쓴다. 바닐라는 이 값이 &gt;0일 때만 손에 든 것 이름을 핫바 위에 그리므로 0으로 두면 안 뜬다.
	 * 필드 이름은 findField가 야른맵으로 해석(runtime 난독화 대응). 못 찾으면 아무 일도 안 함(바닐라 이름 그대로).
	 */
	public static void setHeldItemTooltipFade(Minecraft client, int value) {
		try {
			Object hud = callNoArg(client, "inGameHud");
			if (hud == null) {
				hud = getFieldValue(client, "inGameHud");
			}
			if (hud == null) {
				return;
			}
			java.lang.reflect.Field f = findField(hud.getClass(), "heldItemTooltipFade");
			if (f != null) {
				f.setAccessible(true);
				f.setInt(hud, value);
			}
		} catch (Throwable ignored) {
		}
	}

	/**
	 * 49-122차: 지금 통합 서버(싱글)가 <b>LAN에 공개</b>돼 있는지. IntegratedServer.isRemote()가 LAN 공개 시 true.
	 * 서버가 아니거나(멀티) 못 읽으면 false. 못 읽으면 false(안전).
	 */
	public static boolean isLanOpen(Minecraft client) {
		try {
			Object server = callNoArg(client, "getServer");
			if (server == null) {
				return false;
			}
			Object v = callNoArg(server, "isRemote");
			return v instanceof Boolean b && b;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 49-27차: 지금 조준 중인 블록 위치(블록이 아니면 null). */
	/**
	 * 49-125차(인벤토리 탭): 블록을 <b>우클릭한 것처럼</b> 연다 - ClientPlayerInteractionManager#interactBlock(player[, world], hand, hit).
	 * 서버에는 평범한 블록 사용 패킷만 간다(바닐라 서버는 거리만 본다). 시그니처가 1.17+ (player, hand, hit) /
	 * 1.15~1.16 (player, world, hand, hit)로 갈려 이름으로 찾아 인자 수에 맞춰 부른다. 못 찾으면 false.
	 */
	public static boolean interactBlock(Minecraft client, BlockPos pos) {
		try {
			if (client == null || client.player == null || client.level == null || client.gameMode == null || pos == null) {
				return false;
			}
			Object hand = net.minecraft.world.InteractionHand.MAIN_HAND;
			Object hit = new net.minecraft.world.phys.BlockHitResult(
					new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
					net.minecraft.core.Direction.UP, pos, false);
			Object im = client.gameMode;
			for (Method m : im.getClass().getMethods()) {
				if (!nameMatches(im.getClass(), "interactBlock", m.getName())) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 3 && p[0].isInstance(client.player) && p[1].isInstance(hand) && p[2].isInstance(hit)) {
					m.setAccessible(true);
					m.invoke(im, client.player, hand, hit);
					return true;
				}
				if (p.length == 4 && p[0].isInstance(client.player) && p[1].isInstance(client.level)
						&& p[2].isInstance(hand) && p[3].isInstance(hit)) {
					m.setAccessible(true);
					m.invoke(im, client.player, client.level, hand, hit);
					return true;
				}
			}
		} catch (Throwable t) {
			warnOnce("interactBlock", t);
		}
		return false;
	}
}
