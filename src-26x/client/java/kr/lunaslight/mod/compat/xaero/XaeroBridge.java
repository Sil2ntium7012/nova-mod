package kr.lunaslight.mod.compat.xaero;

import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaMapData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.map.element.render.ElementRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 49-407차: LUNA FESTIVAL 지도 정보({@link LunaMapData})를 Xaero's Minimap / World Map에 붙인다.
 * <ul>
 *   <li>미니맵: Xaero 미니맵 안 요소 처리기(MinimapFBORenderer.minimapElementMapRendererHandler - 프레임버퍼를 처음 만들 때 생김)에
 *       {@link ZoneMinimapRenderer}를 넣는다. 처리기가 새로 만들어지면 다시 넣는다(1초마다 확인).</li>
 *   <li>월드맵: WorldMap.mapElementRenderHandler에 {@link ZoneWorldMapRenderer}.</li>
 *   <li>월드맵 화면(GuiMap): 맵 범위가 있으면 열 때 범위 전체가 한 화면에 들어오게 가운데/배율을 맞추고, 그 뒤로는
 *       카메라 가운데가 범위 밖으로 못 나가고 범위보다 더 작게(멀리) 축소되지 않게 한다(매 프레임 그리기 전에).</li>
 * </ul>
 * Xaero 공정 모드(§f§a§i§r§x§a§e§r§o / §r§e§s§e§t§x§a§e§r§o)는 건드리지 않는다 - Xaero가 시스템 채팅(ChatListener)에서 직접 읽고,
 * 노바는 그보다 뒤(채팅창)에서만 채팅을 만진다. 공정 모드면 Xaero가 레이더 목록을 비워 미니맵과 월드맵 둘 다 엔티티가 안 나온다.
 * 여기서 쓰는 Xaero 클래스는 컴파일할 때만 libs/xaero-api-stub.jar(시그니처 껍데기)를 보고, 게임에선 깔린 Xaero 것을 쓴다.
 */
public final class XaeroBridge {
	private XaeroBridge() {
	}

	private static final ZoneMinimapRenderer MINIMAP = new ZoneMinimapRenderer();
	private static final ZoneWorldMapRenderer WORLDMAP = new ZoneWorldMapRenderer();
	private static Object minimapHandler;
	private static Object worldMapHandler;
	private static int tick;
	private static boolean minimapBroken, worldMapBroken;
	/** 월드맵 화면마다 "처음 프레임에 맞춤"을 했는지(범위 판 번호). */
	private static final Map<Screen, Long> FITTED = Collections.synchronizedMap(new WeakHashMap<>());

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (++tick % 20 == 0) {
				attach();
			}
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if ("xaero.map.gui.GuiMap".equals(screen.getClass().getName())) {
				ScreenEvents.beforeExtract(screen).register((s, g, mx, my, d) -> frame(s));
			}
		});
		LunaClientMod.LOGGER.info("[Nova] Xaero 지도 연동 켬(자기장 원, 맵 범위)");
	}

	private static Field field(Class<?> c, String name) throws NoSuchFieldException {
		for (Class<?> k = c; k != null; k = k.getSuperclass()) {
			try {
				Field f = k.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static void attach() {
		if (!minimapBroken) {
			try {
				Class<?> hudCls = Class.forName("xaero.common.HudMod");
				Object hud = hudCls.getField("INSTANCE").get(null);
				Object minimap = hud == null ? null : hudCls.getMethod("getMinimap").invoke(hud);
				Object fbo = minimap == null ? null : minimap.getClass().getMethod("getMinimapFBORenderer").invoke(minimap);
				Object h = fbo == null ? null : field(fbo.getClass(), "minimapElementMapRendererHandler").get(fbo);
				if (h != null && h != minimapHandler) {
					h.getClass().getMethod("add", MinimapElementRenderer.class).invoke(h, MINIMAP);
					minimapHandler = h;
					ZoneMinimapRenderer.handler = h;
				}
			} catch (ClassNotFoundException e) {
				minimapBroken = true;   // 미니맵 없음(월드맵만)
			} catch (Throwable t) {
				minimapBroken = true;
				LunaCompat.warnOnce("xaero:attachMinimap", t);
			}
		}
		if (!worldMapBroken) {
			try {
				Object h = Class.forName("xaero.map.WorldMap").getField("mapElementRenderHandler").get(null);
				if (h != null && h != worldMapHandler) {
					h.getClass().getMethod("add", ElementRenderer.class).invoke(h, WORLDMAP);
					worldMapHandler = h;
				}
			} catch (ClassNotFoundException e) {
				worldMapBroken = true;
			} catch (Throwable t) {
				worldMapBroken = true;
				LunaCompat.warnOnce("xaero:attachWorldMap", t);
			}
		}
	}

	// ==================== 월드맵 화면: 범위에 맞추기 / 가두기 ====================

	private static Field fCamX, fCamZ, fUserScale, fDestScale, fReset, fAttached;
	private static boolean viewBroken;

	private static void frame(Screen s) {
		LunaMapData.Bounds b = LunaMapData.boundsNow();
		if (b == null || viewBroken) {
			return;
		}
		try {
			if (fCamX == null) {
				Class<?> c = s.getClass();
				fCamX = field(c, "cameraX");
				fCamZ = field(c, "cameraZ");
				fUserScale = field(c, "userScale");
				fDestScale = field(c, "destScale");
				fReset = field(c, "shouldResetCameraPos");
				fAttached = field(c, "attachedCamera");
				if (!Modifier.isStatic(fDestScale.getModifiers()) || !Modifier.isStatic(fAttached.getModifiers())) {
					throw new IllegalStateException("GuiMap 필드 모양이 다름");
				}
			}
			Minecraft mc = Minecraft.getInstance();
			double winW = mc.getWindow().getWidth(), winH = mc.getWindow().getHeight();
			double shortSide = Math.min(winW, winH);
			double mult = shortSide <= 1100 ? 1.0 : shortSide / 1080.0;   // GuiMap.getScaleMultiplier와 같은 식
			double spanX = Math.max(1, b.maxX() - b.minX()), spanZ = Math.max(1, b.maxZ() - b.minZ());
			double fitUser = Math.min(winW / spanX, winH / spanZ) * 0.92 / mult;   // 범위 전체가 화면에 들어오는 배율
			boolean attached = fAttached.getBoolean(null);
			Long done = FITTED.get(s);
			long ver = LunaMapData.boundsVersion();
			if (done == null || done != ver) {
				FITTED.put(s, ver);
				// 처음 열 때(또는 범위가 새로 왔을 때): 범위 가운데, 범위 전체가 보이는 배율
				if (!attached) {
					fReset.setBoolean(s, false);
					fCamX.setDouble(s, b.centerX());
					fCamZ.setDouble(s, b.centerZ());
				}
				fDestScale.setDouble(null, fitUser);
				fUserScale.setDouble(s, fitUser);
			}
			// 범위보다 더 멀리 축소 못 함
			if (fDestScale.getDouble(null) < fitUser) {
				fDestScale.setDouble(null, fitUser);
			}
			if (fUserScale.getDouble(s) < fitUser) {
				fUserScale.setDouble(s, fitUser);
			}
			// 카메라 가운데는 범위 안에서만
			if (!attached) {
				double x = fCamX.getDouble(s), z = fCamZ.getDouble(s);
				double cx = Math.max(b.minX(), Math.min(b.maxX(), x)), cz = Math.max(b.minZ(), Math.min(b.maxZ(), z));
				if (cx != x) {
					fCamX.setDouble(s, cx);
				}
				if (cz != z) {
					fCamZ.setDouble(s, cz);
				}
			}
		} catch (Throwable t) {
			viewBroken = true;
			LunaCompat.warnOnce("xaero:worldMapView", t);
		}
	}
}
