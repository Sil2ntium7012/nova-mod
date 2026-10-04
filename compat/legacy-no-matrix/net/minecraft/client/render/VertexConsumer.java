package net.minecraft.client.render;

/**
 * 49-163차(1.14.4 추가): 1.14.4엔 VertexConsumer가 없다(1.15부터, intermediary class_4588). 공유 소스(LunaCompat의
 * 선/광선 버퍼 헬퍼, 웨이포인트, 조준점 윤곽, 아이템 빛기둥)가 타입으로만 들고 다니므로 이름만 같은 껍데기를 얹는다.
 * 1.14.4에서는 LunaCompat.getLineBuffer/getBeamBuffer가 RenderLayer를 못 찾아 null을 돌려주고, 쓰는 쪽은 null이면 안 그린다.
 * 직접 부르는 메서드는 color(float,float,float,float) 하나뿐(나머지는 리플렉션).
 */
public interface VertexConsumer {
	VertexConsumer color(float r, float g, float b, float a);
}
