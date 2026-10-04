package net.minecraft.client.util.math;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * 49-158차(1.14.4 추가): 1.14.4엔 MatrixStack이 없다(yarn 1.14.4 mapping 404, intermediary에 class_4587 없음 - 1.15부터).
 * 공유 소스가 GUI 그리기 인자로 MatrixStack을 들고 다니므로 이름만 같은 shim을 얹어 GlStateManager로 넘긴다
 * (loom-common.gradle legacy_no_matrix). 다른 버전엔 진짜가 있으니 이 파일은 1.14.4 빌드에만 들어간다.
 * 49-163차: peek()는 LunaCompat의 리플렉션 경로가 부르는데 1.14.4에는 Entry가 없으니 null(아무 클래스의 인스턴스도 아님 →
 * "Entry를 받는 vertex" 후보에서 자연히 빠진다).
 */
public class MatrixStack {
	public void push() {
		GlStateManager.pushMatrix();
	}

	public void pop() {
		GlStateManager.popMatrix();
	}

	public void translate(double x, double y, double z) {
		GlStateManager.translated(x, y, z);
	}

	public void scale(float x, float y, float z) {
		GlStateManager.scalef(x, y, z);
	}

	public Object peek() {
		return null;
	}
}
