package xaero.map.element.render;

/** 노바 컴파일용 껍데기. */
public abstract class ElementRenderProvider<E, C> {
	public abstract void begin(ElementRenderLocation location, C context);

	public abstract boolean hasNext(ElementRenderLocation location, C context);

	public abstract E getNext(ElementRenderLocation location, C context);

	public abstract void end(ElementRenderLocation location, C context);
}
