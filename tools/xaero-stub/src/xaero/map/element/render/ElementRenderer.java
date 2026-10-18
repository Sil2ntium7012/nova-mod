package xaero.map.element.render;

import net.minecraft.client.renderer.MultiBufferSource;
import xaero.map.element.MapElementGraphics;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/** 노바 컴파일용 껍데기. */
public abstract class ElementRenderer<E, C, R extends ElementRenderer<E, C, R>> implements Comparable<ElementRenderer<?, ?, ?>> {
	protected final ElementReader<E, C, R> reader;
	protected final C context;
	protected final ElementRenderProvider<E, C> provider;

	protected ElementRenderer(C context, ElementRenderProvider<E, C> provider, ElementReader<E, C, R> reader) {
		this.context = context;
		this.provider = provider;
		this.reader = reader;
	}

	public int getOrder() {
		return 0;
	}

	@Override
	public int compareTo(ElementRenderer<?, ?, ?> o) {
		return 0;
	}

	public abstract void preRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider provider, boolean pre);

	public abstract void postRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider provider, boolean pre);

	public abstract void renderElementShadow(E e, boolean highlighted, float scale, double partialX, double partialY, ElementRenderInfo info,
			MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider provider);

	public abstract boolean renderElement(E e, boolean highlighted, double optionalDepth, float optionalScale, double partialX, double partialY,
			ElementRenderInfo info, MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider provider);

	public abstract boolean shouldRender(ElementRenderLocation location, boolean pre);
}
