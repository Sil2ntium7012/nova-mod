package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.AfkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 49-312차: 누가 자리 비움(AFK)인지, 그중 고개를 떨굴지(잠듦)를 한곳에서 판단한다.
 *
 * <p><b>나</b>는 AfkModule이 직접 안다(isAfkNow / isIdleNow).
 *
 * <p><b>남</b>은 접속 정보(site_presence.game_afk, LunaSocial)가 "AFK"라고 해야 AFK다. 다만 그 정보는 늦게 온다(런처 90초,
 * 탭 별 조회 5분 - 10-08에 정한 간격, 되돌리지 않는다). 그래서 보이는 범위의 몸으로 보정한다.
 * <ul>
 *   <li><b>깨어남</b>: AFK라는 걸 안 뒤에 시점(몸, 머리 방향)이 움직였으면 사람이 돌아온 것 - 바로 AFK를 내린다.
 *       다시 3분 동안 시점이 그대로면 다시 AFK(정보가 아직 AFK일 때).</li>
 *   <li><b>고개</b>: AFK이면서 몸이 3분 동안 아무것도 안 했으면(위치, 시점, 휘두르기, 아이템 쓰기, 웅크리기) 떨군다.
 *       되풀이(매크로)하는 사람은 계속 뭔가 하니까 안 떨군다. 처음 보는 몸은 움직이는 걸 볼 때까지 떨군 채로 둔다.</li>
 * </ul>
 * 같은 렌더 스레드에서만 부른다(틱, 렌더 상태 만들기, 탭 목록).
 */
public final class AfkWatch {
	private AfkWatch() {
	}

	private static final class Body {
		double x, y, z;
		float yaw, pitch, head;
		boolean sneak;
		long rotAt;    // 시점이 마지막으로 바뀐 때(0 = 본 적 없음)
		long actAt;    // 뭐든 마지막으로 한 때(0 = 본 적 없음)
		long seenAt;
		long flagAt;   // 접속 정보가 AFK라고 처음 알려 준 때(0 = 아님)
	}

	private static final Map<String, Body> BODIES = new HashMap<>();
	private static int ticks;
	/** 고개 떨굼 각도(아래로). */
	public static final float DROOP_PITCH = 55f;

	private static String key(String name) {
		return name == null || name.isEmpty() ? null : name.toLowerCase(Locale.ROOT);
	}

	/** 매 틱(AfkModule): 주변 플레이어의 몸을 기록한다. */
	public static void tick(Minecraft mc) {
		if (mc == null || mc.level == null) {
			BODIES.clear();
			return;
		}
		long now = System.currentTimeMillis();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			String k = key(p.getName().getString());
			if (k == null) {
				continue;
			}
			float yaw = p.getYRot(), pitch = p.getXRot(), head = p.getYHeadRot();
			double x = p.getX(), y = p.getY(), z = p.getZ();
			boolean sneak = p.isCrouching();
			Body b = BODIES.get(k);
			if (b == null) {
				b = new Body();
				BODIES.put(k, b);
			} else {
				boolean rot = yaw != b.yaw || pitch != b.pitch || head != b.head;
				boolean act = rot || sneak != b.sneak || swinging(p) || p.isUsingItem()
						|| Math.abs(x - b.x) > 1e-3 || Math.abs(y - b.y) > 1e-3 || Math.abs(z - b.z) > 1e-3;
				if (rot) {
					b.rotAt = now;
				}
				if (act) {
					b.actAt = now;
				}
			}
			b.x = x;
			b.y = y;
			b.z = z;
			b.yaw = yaw;
			b.pitch = pitch;
			b.head = head;
			b.sneak = sneak;
			b.seenAt = now;
		}
		if (++ticks % 200 == 0) {
			BODIES.values().removeIf(b -> now - b.seenAt > 10L * 60_000L);
		}
	}

	/** 손을 휘두르는 중인가(26.1~26.2 swinging 필드, 26.3 isSwinging()). */
	public static boolean swinging(Object e) {
		Object r = LunaCompat.callNoArg(e, "isSwinging");
		if (r instanceof Boolean b) {
			return b;
		}
		Object f = LunaCompat.getFieldValue(e, "swinging");
		return f instanceof Boolean b && b;
	}

	private static boolean isSelf(Object entity, String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return false;
		}
		if (entity != null) {
			return entity == mc.player;
		}
		return name != null && name.equalsIgnoreCase(mc.player.getName().getString());
	}

	/** 이 사람이 AFK인가(나 = 이 컴퓨터, 남 = 접속 정보 + 몸 보정). entity가 없으면 이름만으로(탭 목록). */
	public static boolean afk(Object entity, String name) {
		if (isSelf(entity, name)) {
			return AfkModule.isAfkNow();
		}
		String k = key(name);
		if (k == null) {
			return false;
		}
		Body b = BODIES.get(k);
		if (!LunaSocial.isAfkLunaPlayer(k)) {
			if (b != null) {
				b.flagAt = 0;
			}
			return false;
		}
		if (b == null) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (b.flagAt == 0) {
			b.flagAt = now;
		}
		return b.rotAt <= b.flagAt + 2000L || now - b.rotAt >= AfkModule.WAIT_MS;
	}

	/** 고개를 떨굴지(AFK이면서 몸이 가만히 있음). 되풀이 중이면 false. */
	public static boolean droop(Object entity, String name) {
		if (isSelf(entity, name)) {
			return AfkModule.isIdleNow();
		}
		if (!afk(entity, name)) {
			return false;
		}
		Body b = BODIES.get(key(name));
		return b == null || b.actAt == 0 || System.currentTimeMillis() - b.actAt >= AfkModule.WAIT_MS;
	}

	/** 개체로(날개, 망토 숨김용). */
	public static boolean afkEntity(Object entity) {
		if (!(entity instanceof net.minecraft.world.entity.Entity e)) {
			return false;
		}
		try {
			return afk(e, e.getName().getString());
		} catch (Throwable t) {
			return false;
		}
	}

	/** 3인칭에서 내 머리 위에도 AFK를 띄울지(이름표 판정이 본다). F1로 화면을 숨겼으면 안 띄운다. */
	public static boolean selfLabel(Object entity) {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.player != null && entity == mc.player && AfkModule.isAfkNow() && !LunaCompat.isHudHidden(mc);
	}

	/** 머리 위 이름 뒤에 붙는 회색 " AFK". */
	public static Component label(Component name) {
		Component tag = LunaCompat.coloredText(" AFK", 0xAAAAAA);
		if (name == null) {
			return LunaCompat.coloredText("AFK", 0xAAAAAA);
		}
		return name.getString().endsWith(" AFK") ? name : LunaCompat.join(name, tag);
	}

	/** 탭 목록에서 노바 별 대신 붙는 회색 "ZZZ". */
	public static Component tabBadge() {
		return LunaCompat.coloredText("ZZZ", 0xAAAAAA);
	}
}
