package kr.lunaslight.mod.compat.xaero;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 49-407차: Xaero's World Map 위에 자기장 원(파랑 = 지금, 흰색 = 다음)과 맵 범위 밖 어둡게.
 * Xaero가 요소마다 행렬을 그 요소 자리(창 픽셀)로 옮겨 주므로 반지름 = 블록 × info.scale(창 픽셀/블록).
 * 요소는 어디서든 "화면 안"으로 답한다(원이 화면보다 커도 그려지게) - 마우스 올림/클릭 대상은 아님.
 */
final class ZoneWorldMapRenderer extends ElementRenderer<Object, Object, ZoneWorldMapRenderer> {
	ZoneWorldMapRenderer() {
		super(new Object(), new Provider(), new Reader());
	}

	@Override
	public int getOrder() {
		return -100;   // 웨이포인트/플레이어 표시보다 먼저(아래에)
	}

	@Override
	public boolean shouldRender(ElementRenderLocation location, boolean pre) {
		return !pre && location == ElementRenderLocation.WORLD_MAP;
	}

	@Override
	public void preRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider p, boolean pre) {
	}

	@Override
	public void postRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider p, boolean pre) {
	}

	@Override
	public void renderElementShadow(Object e, boolean highlighted, float scale, double partialX, double partialY, ElementRenderInfo info,
			MapElementGraphics g, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider p) {
	}

	@Override
	public boolean renderElement(Object e, boolean highlighted, double depth, float optionalScale, double partialX, double partialY,
			ElementRenderInfo info, MapElementGraphics g, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider p) {
		try {
			// 월드맵은 다른 차원도 볼 수 있다 - 서버 정보는 지금 있는 차원 것이라 같은 차원을 볼 때만
			Minecraft mc = Minecraft.getInstance();
			if (info.mapDimension != null && mc.level != null && !info.mapDimension.equals(mc.level.dimension())) {
				return false;
			}
			Matrix4f m = g.pose().last().pose();
			double s = info.scale;
			float gui = (float) Math.max(1, mc.getWindow().getGuiScale());
			if (e instanceof LunaMapData.Circle c) {
				XaeroDraw.ring(m, partialX, partialY, c.r() * s, 2.0f * gui, c.color());
			} else if (e instanceof LunaMapData.Bounds b) {
				float hw = (float) ((b.maxX() - b.minX()) / 2.0 * s), hh = (float) ((b.maxZ() - b.minZ()) / 2.0 * s);
				float px = (float) partialX, py = (float) partialY;
				XaeroDraw.outside(m, px - hw, py - hh, px + hw, py + hh, 200_000f, 0xA0000000, 0xC0FFFFFF, gui);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("xaero:worldmapDraw", t);
		}
		return false;
	}

	/** 지금 그릴 것: 범위 밖 어둡게(먼저) + 원들. 월드맵이 보고 있는 차원 기준. */
	private static List<Object> elements(ResourceKey<Level> dim) {
		List<Object> out = new ArrayList<>(3);
		LunaMapData.Bounds b = LunaMapData.bounds(dim);
		if (b != null) {
			out.add(b);
		}
		out.addAll(LunaMapData.circles(dim));
		return out;
	}

	private static double x(Object e) {
		return e instanceof LunaMapData.Circle c ? c.x() : e instanceof LunaMapData.Bounds b ? b.centerX() : 0;
	}

	private static double z(Object e) {
		return e instanceof LunaMapData.Circle c ? c.z() : e instanceof LunaMapData.Bounds b ? b.centerZ() : 0;
	}

	private static final class Provider extends ElementRenderProvider<Object, Object> {
		private Iterator<Object> it = Collections.emptyIterator();

		@Override
		public void begin(ElementRenderLocation location, Object context) {
			Minecraft mc = Minecraft.getInstance();
			ResourceKey<Level> dim = mc.level == null ? null : mc.level.dimension();
			it = dim == null ? Collections.emptyIterator() : elements(dim).iterator();
		}

		@Override
		public boolean hasNext(ElementRenderLocation location, Object context) {
			return it.hasNext();
		}

		@Override
		public Object getNext(ElementRenderLocation location, Object context) {
			return it.next();
		}

		@Override
		public void end(ElementRenderLocation location, Object context) {
			it = Collections.emptyIterator();
		}
	}

	private static final class Reader extends ElementReader<Object, Object, ZoneWorldMapRenderer> {
		@Override
		public boolean isHidden(Object e, Object c) {
			return false;
		}

		@Override
		public double getRenderX(Object e, Object c, float pt) {
			return x(e);
		}

		@Override
		public double getRenderZ(Object e, Object c, float pt) {
			return z(e);
		}

		@Override
		public int getInteractionBoxLeft(Object e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getInteractionBoxRight(Object e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getInteractionBoxTop(Object e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getInteractionBoxBottom(Object e, Object c, float pt) {
			return 0;
		}

		@Override
		public int getRenderBoxLeft(Object e, Object c, float pt) {
			return -1;
		}

		@Override
		public int getRenderBoxRight(Object e, Object c, float pt) {
			return 1;
		}

		@Override
		public int getRenderBoxTop(Object e, Object c, float pt) {
			return -1;
		}

		@Override
		public int getRenderBoxBottom(Object e, Object c, float pt) {
			return 1;
		}

		@Override
		public int getLeftSideLength(Object e, Minecraft mc) {
			return 0;
		}

		@Override
		public String getMenuName(Object e) {
			return "";
		}

		@Override
		public String getFilterName(Object e) {
			return "";
		}

		@Override
		public int getMenuTextFillLeftPadding(Object e) {
			return 0;
		}

		@Override
		public int getRightClickTitleBackgroundColor(Object e) {
			return 0;
		}

		@Override
		public boolean shouldScaleBoxWithOptionalScale() {
			return false;
		}

		@Override
		public boolean isInteractable(ElementRenderLocation location, Object e) {
			return false;
		}

		@Override
		public boolean isHoveredOnMap(ElementRenderLocation location, Object e, double mouseX, double mouseZ, double scale,
				double screenSizeBasedScale, double rendererDimDiv, Object c, float pt) {
			return false;
		}

		@Override
		public boolean isOnScreen(Object e, double cameraX, double cameraZ, int width, int height, double scale,
				double screenSizeBasedScale, double rendererDimDiv, Object c, float pt) {
			return true;
		}
	}
}
