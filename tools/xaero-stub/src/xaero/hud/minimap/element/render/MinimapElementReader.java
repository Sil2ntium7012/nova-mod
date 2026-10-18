package xaero.hud.minimap.element.render;

import net.minecraft.client.Minecraft;

/** 노바 컴파일용 껍데기(추상 메서드는 실제와 같게 - 하나라도 빠지면 게임에서 AbstractMethodError). */
public abstract class MinimapElementReader<E, RC> {
	public abstract boolean isHidden(E e, RC c);

	public abstract double getRenderX(E e, RC c, float pt);

	public abstract double getRenderY(E e, RC c, float pt);

	public abstract double getRenderZ(E e, RC c, float pt);

	public abstract int getInteractionBoxLeft(E e, RC c, float pt);

	public abstract int getInteractionBoxRight(E e, RC c, float pt);

	public abstract int getInteractionBoxTop(E e, RC c, float pt);

	public abstract int getInteractionBoxBottom(E e, RC c, float pt);

	public abstract int getRenderBoxLeft(E e, RC c, float pt);

	public abstract int getRenderBoxRight(E e, RC c, float pt);

	public abstract int getRenderBoxTop(E e, RC c, float pt);

	public abstract int getRenderBoxBottom(E e, RC c, float pt);

	public abstract int getLeftSideLength(E e, Minecraft mc);

	public abstract String getMenuName(E e);

	public abstract String getFilterName(E e);

	public abstract int getMenuTextFillLeftPadding(E e);

	public abstract int getRightClickTitleBackgroundColor(E e);

	public abstract boolean shouldScaleBoxWithOptionalScale();

	public boolean isInteractable(MinimapElementRenderLocation location, E e) {
		return false;
	}
}
