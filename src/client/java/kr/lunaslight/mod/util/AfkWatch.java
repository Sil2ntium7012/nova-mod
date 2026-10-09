package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.AfkModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

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
		/** 49-316차: 기준 자리/시점 - 여기서 조금 밀리거나(3블록 안) 시점이 몇 도 흔들리는 건 "움직임"으로 안 친다. */
		double ax, ay, az;
		float ryaw, rpitch, rhead;
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
	public static void tick(MinecraftClient mc) {
		if (mc == null || mc.world == null) {
			BODIES.clear();
			return;
		}
		long now = System.currentTimeMillis();
		for (Object o : mc.world.getPlayers()) {
			if (!(o instanceof Entity) || o == mc.player) {
				continue;
			}
			Entity p = (Entity) o;
			String k = key(p.getName().getString());
			if (k == null) {
				continue;
			}
			float yaw = LunaCompat.getYaw(p), pitch = LunaCompat.getPitch(p);
			Object hy = LunaCompat.callNoArg(p, "getHeadYaw");
			float head = hy instanceof Float ? (Float) hy : yaw;
			double x = EntityPos.x(p), y = EntityPos.y(p), z = EntityPos.z(p);
			Object sn = LunaCompat.callNoArg(p, "isSneaking");
			boolean sneak = sn instanceof Boolean && (Boolean) sn;
			Body b = BODIES.get(k);
			if (b == null) {
				b = new Body();
				BODIES.put(k, b);
				b.ax = x;
				b.ay = y;
				b.az = z;
				b.ryaw = yaw;
				b.rpitch = pitch;
				b.rhead = head;
			} else {
				// 49-316차(사용자: "잠수가 누가 건들어도 끝나고 고개를 떨어트리고 있지 않아"): 예전엔 틱마다 조금이라도 바뀌면 움직임이었다 -
				// 누가 밀거나 때리면 위치가 바뀌고, 서버가 보내는 시점 값의 반올림(1.4도)도 틱마다 흔들려 깨어난 걸로 봤다.
				// 이제 기준에서 시점이 4도 넘게 돌거나, 3블록 넘게 옮겨지거나, 손을 휘두르거나, 아이템을 쓰거나, 웅크리기를 바꿀 때만 움직임.
				boolean rot = angle(yaw, b.ryaw) > 4f || Math.abs(pitch - b.rpitch) > 4f || angle(head, b.rhead) > 4f;
				double dx = x - b.ax, dy = y - b.ay, dz = z - b.az;
				boolean far = dx * dx + dy * dy + dz * dz > 9.0;
				boolean act = rot || far || sneak != b.sneak || swinging(p) || usingItem(p);
				if (rot) {
					b.rotAt = now;
					b.ryaw = yaw;
					b.rpitch = pitch;
					b.rhead = head;
				}
				if (far) {
					b.ax = x;
					b.ay = y;
					b.az = z;
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

	/** 두 각도의 차이(0~180도). */
	private static float angle(float a, float b) {
		float d = Math.abs((a - b) % 360f);
		return d > 180f ? 360f - d : d;
	}

	/** 손을 휘두르는 중인가(LivingEntity.handSwinging). */
	public static boolean swinging(Object e) {
		Object f = LunaCompat.getFieldValue(e, "handSwinging", "isHandSwinging");
		return f instanceof Boolean && (Boolean) f;
	}

	/** 아이템을 쓰는 중인가(먹기, 활 당기기 등). */
	public static boolean usingItem(Object e) {
		Object r = LunaCompat.callNoArg(e, "isUsingItem");
		return r instanceof Boolean && (Boolean) r;
	}

	private static boolean isSelf(Object entity, String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
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
		if (!(entity instanceof Entity)) {
			return false;
		}
		try {
			return afk(entity, ((Entity) entity).getName().getString());
		} catch (Throwable t) {
			return false;
		}
	}

	/** 3인칭에서 내 머리 위에도 AFK를 띄울지(이름표 판정이 본다). F1로 화면을 숨겼으면 안 띄운다. */
	public static boolean selfLabel(Object entity) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc != null && mc.player != null && entity == mc.player && AfkModule.isAfkNow() && !LunaCompat.isHudHidden(mc);
	}

	// ---------------- 1.21.1 이하(렌더 상태가 없는 버전): AfkPoseLegacyMixin이 플레이어 하나 그리기 앞뒤로 부른다 ----------------
	/** 지금 그리고 있는 플레이어(AfkPlayerMixin의 getDisplayName이 이때만 AFK를 붙인다). */
	public static Object rendering;
	private static Object swapped;
	private static float savedPitch, savedPrev;
	private static java.lang.reflect.Field prevPitchField;
	private static boolean prevPitchLooked;

	public static void beginRender(Object entity) {
		rendering = null;
		if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
			return;
		}
		try {
			String name = ((Entity) entity).getName().getString();
			if (!afk(entity, name)) {
				return;
			}
			rendering = entity;
			if (!droop(entity, name)) {
				return;
			}
			if (!prevPitchLooked) {
				prevPitchLooked = true;
				prevPitchField = LunaCompat.findField(Entity.class, "prevPitch");
			}
			savedPitch = LunaCompat.getPitch(entity);
			savedPrev = prevPitchField == null ? savedPitch : prevPitchField.getFloat(entity);
			LunaCompat.setPitch(entity, DROOP_PITCH);
			if (prevPitchField != null) {
				prevPitchField.setFloat(entity, DROOP_PITCH);
			}
			swapped = entity;
		} catch (Throwable t) {
			LunaCompat.warnOnce("afkPoseBegin", t);
		}
	}

	public static void endRender(Object entity) {
		rendering = null;
		if (swapped == null || swapped != entity) {
			return;
		}
		swapped = null;
		try {
			LunaCompat.setPitch(entity, savedPitch);
			if (prevPitchField != null) {
				prevPitchField.setFloat(entity, savedPrev);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("afkPoseEnd", t);
		}
	}

	/**
	 * 49-317차(사용자: "AFK 글이 이름과 구분 잘되게 - 지금 너무 비슷"): 회색 " AFK"가 이름과 거의 같아 보였다. 이제 따뜻한 노란색 굵은
	 * "AFK"를 대괄호로 감싸 따로 보이게 한다(§l 굵게 - 글꼴이 글자 안의 서식 코드를 그대로 읽는다).
	 */
	public static Text tag() {
		return LunaCompat.coloredText("§l[AFK]", 0xFFC14D);
	}

	/** 머리 위 이름 뒤에 AFK 표(이름이 없으면 표만). */
	public static Text label(Text name) {
		if (name == null) {
			return tag();
		}
		return name.getString().endsWith("[AFK]") ? name : LunaCompat.join(name, LunaCompat.textLiteral(" "), tag());
	}

	/**
	 * 탭 목록에서 노바 별 대신 붙는 "ZZZ". 49-318차(사용자: "잠수할 때 ZZZ 크기 점점 작아지게"): 글자 대신 그림 한 글자(lunastar 글꼴의
	 * \uE101 = lunazzz.png) - 큰 Z, 조금 작은 Z, 작은 Z가 오른쪽 위로 올라가며 작아진다(옅은 회색, 가장자리 반투명).
	 * 글꼴을 못 쓰는 옛 버전은 회색 "Zzz" 글자.
	 */
	public static Text tabBadge() {
		try {
			Text z = LunaCompat.styledText("\uE101", LunaCompat.styleWithFontNamed("lunastar"));
			if (z != null) {
				return z;
			}
		} catch (Throwable ignored) {
		}
		return LunaCompat.coloredText("Zzz", 0xAAAAAA);
	}
}
