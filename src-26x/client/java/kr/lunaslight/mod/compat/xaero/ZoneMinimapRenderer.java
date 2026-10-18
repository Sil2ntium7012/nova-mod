package kr.lunaslight.mod.compat.xaero;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;

/**
 * 49-407차: Xaero's Minimap 지도 안(IN_MINIMAP, 미니맵 프레임버퍼 - 미니맵 모양 밖은 저절로 잘림)에 자기장 원.
 * 원 하나 = 요소 하나(가운데 = 원 중심). Xaero가 요소 자리로 옮겨 준 행렬에서 반지름 = 블록 × 미니맵 확대(zoom)로 그린다.
 * 원은 돌려도 같은 모양이라 미니맵 회전은 신경 안 써도 된다.
 */
final class ZoneMinimapRenderer extends MinimapElementRenderer<LunaMapData.Circle, Object> {
	/** 지금 붙어 있는 Xaero 미니맵 안 요소 처리기(MinimapElementMapRendererHandler) - zoom 값을 읽는다. */
	static volatile Object handler;
	private static Field zoomField;
	private static double zoom = 1.0;

	ZoneMinimapRenderer() {
		super(new Reader(), new Provider(), new Object());
	}

	@Override
	public boolean shouldRender(MinimapElementRenderLocation location) {
		return location == MinimapElementRenderLocation.IN_MINIMAP;
	}

	@Override
	public void preRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider p) {
		try {
			Object h = handler;
			if (h != null) {
				if (zoomField == null || zoomField.getDeclaringClass() != h.getClass()) {
					zoomField = h.getClass().getDeclaredField("zoom");
					zoomField.setAccessible(true);
				}
				zoom = zoomField.getDouble(h);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("xaero:zoom", t);
		}
	}

	@Override
	public void postRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider p) {
	}

	@Override
	public boolean renderElement(LunaMapData.Circle c, boolean highlighted, boolean outOfBounds, double depth, float optionalScale,
			double partialX, double partialY, MinimapElementRenderInfo info, MinimapElementGraphics g, MultiBufferSource.BufferSource buffers) {
		try {
			XaeroDraw.ring(g.pose().last().pose(), partialX, partialY, c.r() * zoom, 3.0f, c.color());
		} catch (Throwable t) {
			LunaCompat.warnOnce("xaero:minimapRing", t);
		}
		return false;
	}

	private static final class Provider extends MinimapElementRenderProvider<LunaMapData.Circle, Object> {
		private Iterator<LunaMapData.Circle> it = Collections.emptyIterator();

		@Override
		public void begin(MinimapElementRenderLocation location, Object context) {
			Minecraft mc = Minecraft.getInstance();
			it = mc.level == null ? Collections.emptyIterator() : LunaMapData.circles(mc.level.dimension()).iterator();
		}

		@Override
		public boolean hasNext(MinimapElementRenderLocation location, Object context) {
			return it.hasNext();
		}

		@Override
		public LunaMapData.Circle getNext(MinimapElementRenderLocation location, Object context) {
			return it.next();
		}

		@Override
		public void end(MinimapElementRenderLocation location, Object context) {
			it = Collections.emptyIterator();
		}
	}

	private static final class Reader extends MinimapElementReader<LunaMapData.Circle, Object> {
		@Override
		public boolean isHidden(LunaMapData.Circle e, Object c) {
			return false;
		}

		@Override
		public double getRenderX(LunaMapData.Circle e, Object c, float pt) {
			return e.x();
		}

		@Override
		public double getRenderY(LunaMapData.Circle e, Object c, float pt) {
			Minecraft mc = Minecraft.getInstance();
			return mc.player == null ? 64 : mc.player.getY();
		}

		@Override
		public double getRenderZ(LunaMapData.Circle e, Object c, float pt) {
			return e.z();
		}

		@Override
		public int getInteractionBoxLeft(LunaMapData.Circle e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getInteractionBoxRight(LunaMapData.Circle e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getInteractionBoxTop(LunaMapData.Circle e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getInteractionBoxBottom(LunaMapData.Circle e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getRenderBoxLeft(LunaMapData.Circle e, Object c, float pt) {
			return -1;
		}

		@Override
		public int getRenderBoxRight(LunaMapData.Circle e, Object c, float pt) {
			return 1;
		}

		@Override
		public int getRenderBoxTop(LunaMapData.Circle e, Object c, float pt) {
			return -1;
		}

		@Override
		public int getRenderBoxBottom(LunaMapData.Circle e, Object c, float pt) {
			return 1;
		}

		@Override
		public int getLeftSideLength(LunaMapData.Circle e, Minecraft mc) {
			return 0;
		}

		@Override
		public String getMenuName(LunaMapData.Circle e) {
			return "";
		}

		@Override
		public String getFilterName(LunaMapData.Circle e) {
			return "";
		}

		@Override
		public int getMenuTextFillLeftPadding(LunaMapData.Circle e) {
			return 0;
		}

		@Override
		public int getRightClickTitleBackgroundColor(LunaMapData.Circle e) {
			return 0;
		}

		@Override
		public boolean shouldScaleBoxWithOptionalScale() {
			return false;
		}

		@Override
		public boolean isInteractable(MinimapElementRenderLocation location, LunaMapData.Circle e) {
			return false;
		}
	}
}
