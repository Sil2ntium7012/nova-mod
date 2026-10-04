package kr.lunaslight.mod.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 49-23차: 설치된 모드 목록 보조 - "실질적이지 않은 모드(jcpp, antlr4-runtime, glsl-transformer…)는 라이브러리로
 * 빼고, 모드 수정(설정) 기능이 있으면 표시".
 *
 *  라이브러리 판정(하나라도 맞으면):
 *   ① 다른 모드 jar 안에 중첩(Jar-in-Jar)된 모드 - ModContainer#getContainingMod()가 있음(로더 0.12+)
 *   ② Mod Menu 메타데이터(custom "modmenu")에 badges:["library"] 또는 parent가 있음
 *   ③ 알려진 id(fabric-*, fabricloader, java, minecraft, mixinextras, jcpp, cloth-config, architectury…)
 *   ④ 메이븐 좌표식 id(org_…, io_github_…, com_…)나 -runtime/-api/-lib 꼬리
 *
 *  설정 화면: Mod Menu API 진입점(modmenu → ModMenuApi#getModConfigScreenFactory / getProvidedConfigScreenFactories)을
 *  리플렉션으로 모아 모드 id → 팩토리 맵을 만든다(Mod Menu가 없으면 빈 맵). openConfig가 create(parent)로 화면을 연다.
 */
public final class ModListSupport {
	private ModListSupport() {
	}

	private static final Set<String> KNOWN_LIBRARIES = Set.of(
			"fabricloader", "java", "minecraft", "mixinextras", "jcpp", "cloth-config", "cloth-config2", "cloth-basic-math",
			"architectury", "fabric-language-kotlin", "fabric-language-scala", "fabric-language-groovy",
			"yet_another_config_lib_v3", "yet-another-config-lib", "midnightlib", "owo", "owo-lib", "resourcefullib",
			"placeholder-api", "glsl-transformer", "antlr4-runtime", "libjf", "badpackets", "forgeconfigapiport",
			"fabric-permissions-api-v0", "libgui", "cardinal-components", "trinkets-api", "geckolib", "kotlinforforge",
			"balm-fabric", "puzzleslib", "collective", "supermartijn642corelib", "supermartijn642configlib", "creativecore",
			"moonlight", "lithium-api", "indium-api", "silk-api", "cicada", "jankson", "bookshelf", "toml4j", "night-config",
			"mixinsquared", "conditional-mixin", "reflection-util", "spruceui", "cotton-config", "kubejs", "rhino",
			"iceberg", "prism", "prism-lib", "luna-bridge");

	private static final String[] LIBRARY_PREFIXES = {"fabric-", "org_", "io_github_", "io_", "com_", "net_", "de_",
			"dev_", "me_", "eu_", "org.", "com.", "io.", "net."};

	/** 이 컨테이너가 라이브러리(목록에서 기본 숨김)인지. */
	public static boolean isLibrary(ModContainer container) {
		if (container == null) {
			return false;
		}
		ModMetadata meta = container.getMetadata();
		String id = meta.getId();
		// ① 중첩 jar
		try {
			Object containing = LunaCompat.invokeNoArg(container, "getContainingMod");
			if (containing instanceof java.util.Optional<?> opt && opt.isPresent()) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		// ② Mod Menu 메타
		try {
			Object cv = meta.getCustomValue("modmenu");
			if (cv != null) {
				Object obj = LunaCompat.invokeNoArg(cv, "getAsObject");
				if (obj != null) {
					Object parent = call1(obj, "get", "parent");
					if (parent != null) {
						return true;
					}
					Object badges = call1(obj, "get", "badges");
					if (badges != null) {
						Object arr = LunaCompat.invokeNoArg(badges, "getAsArray");
						if (arr instanceof Iterable<?> it) {
							for (Object b : it) {
								Object str = LunaCompat.invokeNoArg(b, "getAsString");
								if ("library".equalsIgnoreCase(String.valueOf(str))) {
									return true;
								}
							}
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
		// ③ 알려진 id
		if (KNOWN_LIBRARIES.contains(id)) {
			return true;
		}
		// ④ 이름 꼴
		String lower = id.toLowerCase(Locale.ROOT);
		for (String p : LIBRARY_PREFIXES) {
			if (lower.startsWith(p)) {
				return true;
			}
		}
		if (lower.endsWith("-runtime") || lower.endsWith("_runtime") || lower.endsWith("-lib") || lower.endsWith("lib")
				&& lower.length() > 6 && !lower.equals("lithium") || lower.endsWith("-api") || lower.endsWith("_api")) {
			return true;
		}
		String name = meta.getName() == null ? "" : meta.getName().toLowerCase(Locale.ROOT);
		return name.endsWith(" api") || name.endsWith(" library") || name.endsWith("-runtime") || name.contains("(library)");
	}

	private static Object call1(Object target, String name, Object arg) {
		if (target == null) {
			return null;
		}
		for (Method m : target.getClass().getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(arg)) {
				try {
					m.setAccessible(true);
					return m.invoke(target, arg);
				} catch (Throwable ignored) {
					return null;
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- 설정 화면(Mod Menu API)

	private static Map<String, Object> factories;

	/** 모드 id → Mod Menu ConfigScreenFactory. Mod Menu API가 없으면 빈 맵. 한 번만 수집. */
	public static synchronized Map<String, Object> configFactories() {
		if (factories != null) {
			return factories;
		}
		Map<String, Object> map = new HashMap<>();
		try {
			Class<?> apiClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
			Method getFactory = apiClass.getMethod("getModConfigScreenFactory");
			Method getProvided = null;
			try {
				getProvided = apiClass.getMethod("getProvidedConfigScreenFactories");
			} catch (Throwable ignored) {
			}
			for (Object container : FabricLoader.getInstance().getEntrypointContainers("modmenu", apiClass)) {
				try {
					Object api = LunaCompat.invokeNoArg(container, "getEntrypoint");
					Object provider = LunaCompat.invokeNoArg(container, "getProvider");
					if (api == null || !(provider instanceof ModContainer mc)) {
						continue;
					}
					Object factory = getFactory.invoke(api);
					if (factory != null && isRealFactory(factory)) {
						map.put(mc.getMetadata().getId(), factory);
					}
					if (getProvided != null) {
						Object provided = getProvided.invoke(api);
						if (provided instanceof Map<?, ?> pm) {
							for (Map.Entry<?, ?> e : pm.entrySet()) {
								if (e.getKey() instanceof String k && e.getValue() != null) {
									map.putIfAbsent(k, e.getValue());
								}
							}
						}
					}
				} catch (Throwable t) {
					LunaCompat.warnOnce("modmenu:entry", t);
				}
			}
		} catch (ClassNotFoundException e) {
			// Mod Menu 없음 - 설정 화면 기능 비활성
		} catch (Throwable t) {
			LunaCompat.warnOnce("modmenu:factories", t);
		}
		factories = map;
		return map;
	}

	/** ModMenuApi 기본 구현은 "parent -> null"을 돌려주는 람다라 create가 null을 주면 설정 없음으로 본다(열 때 판정). */
	private static boolean isRealFactory(Object factory) {
		return factory != null;
	}

	public static boolean hasConfig(String modId) {
		return configFactories().containsKey(modId);
	}

	/** 모드의 설정 화면을 연다. 성공하면 true. */
	public static boolean openConfig(MinecraftClient client, String modId, Screen parent) {
		Object factory = configFactories().get(modId);
		if (factory == null) {
			return false;
		}
		try {
			Method create = null;
			for (Method m : factory.getClass().getMethods()) {
				if (m.getName().equals("create") && m.getParameterCount() == 1) {
					create = m;
					break;
				}
			}
			if (create == null) {
				return false;
			}
			create.setAccessible(true);
			Object screen = create.invoke(factory, parent);
			if (screen instanceof Screen s) {
				LunaCompat.setScreen(s); // 49-36차: 1.16은 openScreen
				return true;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("modmenu:open:" + modId, t);
		}
		return false;
	}
}
