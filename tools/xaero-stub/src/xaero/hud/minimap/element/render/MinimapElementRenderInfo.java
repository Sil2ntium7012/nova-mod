package xaero.hud.minimap.element.render;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 노바 컴파일용 껍데기. 필드 값은 생성자에서(상수로 두면 javac가 값을 박아 넣는다). */
public class MinimapElementRenderInfo {
	public final MinimapElementRenderLocation location;
	public final ResourceKey<Level> mapDimension;

	protected MinimapElementRenderInfo(MinimapElementRenderLocation location, ResourceKey<Level> mapDimension) {
		this.location = location;
		this.mapDimension = mapDimension;
	}
}
