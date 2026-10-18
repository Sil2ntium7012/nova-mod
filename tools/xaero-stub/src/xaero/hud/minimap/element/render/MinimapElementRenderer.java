package xaero.hud.minimap.element.render;

import net.minecraft.client.renderer.MultiBufferSource;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/** 노바 컴파일용 껍데기. */
public abstract class MinimapElementRenderer<E, RC> implements Comparable<MinimapElementRenderer<?, ?>> {
	protected final MinimapElementReader<E, RC> elementReader;
	protected final RC context;
	protected final MinimapElementRenderProvider<E, RC> provider;

	public MinimapElementRenderer(MinimapElementReader<E, RC> reader, MinimapElementRenderProvider<E, RC> provider, RC context) {
		this.elementReader = reader;
		this.provider = provider;
		this.context = context;
	}

	public int getOrder() {
		return 0;
	}

	@Override
	public int compareTo(MinimapElementRenderer<?, ?> o) {
		return 0;
	}

	public abstract boolean renderElement(E e, boolean highlighted, boolean outOfBounds, double optionalDepth, float optionalScale,
			double partialX, double partialY, MinimapElementRenderInfo info, MinimapElementGraphics graphics, MultiBufferSource.BufferSource buffers);

	public abstract void preRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider provider);

	public abstract void postRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider provider);

	public abstract boolean shouldRender(MinimapElementRenderLocation location);
}
