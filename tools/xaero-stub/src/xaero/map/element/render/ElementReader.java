package xaero.map.element.render;

import net.minecraft.client.Minecraft;

/** 노바 컴파일용 껍데기(추상 메서드는 실제와 같게). */
public abstract class ElementReader<E, C, R extends ElementRenderer<E, ?, R>> {
	public abstract boolean isHidden(E e, C c);

	public abstract double getRenderX(E e, C c, float pt);

	public abstract double getRenderZ(E e, C c, float pt);

	public abstract int getInteractionBoxLeft(E e, C c, float pt);

	public abstract int getInteractionBoxRight(E e, C c, float pt);

	public abstract int getInteractionBoxTop(E e, C c, float pt);

	public abstract int getInteractionBoxBottom(E e, C c, float pt);

	public abstract int getRenderBoxLeft(E e, C c, float pt);

	public abstract int getRenderBoxRight(E e, C c, float pt);

	public abstract int getRenderBoxTop(E e, C c, float pt);

	public abstract int getRenderBoxBottom(E e, C c, float pt);

	public abstract int getLeftSideLength(E e, Minecraft mc);

	public abstract String getMenuName(E e);

	public abstract String getFilterName(E e);

	public abstract int getMenuTextFillLeftPadding(E e);

	public abstract int getRightClickTitleBackgroundColor(E e);

	public abstract boolean shouldScaleBoxWithOptionalScale();

	public boolean isInteractable(ElementRenderLocation location, E e) {
		return false;
	}

	public boolean isHoveredOnMap(ElementRenderLocation location, E e, double mouseX, double mouseZ, double scale,
			double screenSizeBasedScale, double rendererDimDiv, C c, float pt) {
		return false;
	}

	public boolean isOnScreen(E e, double cameraX, double cameraZ, int width, int height, double scale,
			double screenSizeBasedScale, double rendererDimDiv, C c, float pt) {
		return true;
	}
}
