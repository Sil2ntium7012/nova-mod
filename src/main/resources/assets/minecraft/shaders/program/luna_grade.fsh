#version 150

// Luna 색 보정(49-170차) - 1.17~1.21.1(옛 program 포맷). 값은 ColorGradeShader가 GlUniform으로 넣는다.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec4 P0;
uniform vec4 P1;
uniform vec4 P2;

in vec2 texCoord;

out vec4 fragColor;

// P0: x 색온도(-1~1) y 색조(-1~1) z 채도(0~2) w 대비(0~2)
// P1: x 밝기(-1~1) y 생동감(-1~1) z 비네트(0~1) w 선명도(0~1)
// P2: x 페이드(0~1) y 감마 보정(-1~1) z,w 예비
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 grade(vec3 c, vec2 uv, vec3 blur) {
    // 선명도: 언샤프 마스크(주변 평균과의 차이를 더한다)
    c += (c - blur) * (P1.w * 1.6);
    // 밝기: 노출처럼 곱하고 살짝 들어 올린다(검정은 거의 그대로)
    c = c * (1.0 + P1.x * 0.55) + P1.x * 0.04;
    // 색온도: 화이트밸런스(곱) - 따뜻하면 빨강 위, 파랑 아래
    c *= vec3(1.0 + 0.20 * P0.x, 1.0 + 0.05 * P0.x, 1.0 - 0.22 * P0.x);
    // 색조: 자홍(+) / 초록(-)
    c *= vec3(1.0 + 0.08 * P0.y, 1.0 - 0.11 * P0.y, 1.0 + 0.08 * P0.y);
    // 감마: 중간 톤만 밝게/어둡게
    c = pow(max(c, 0.0), vec3(1.0 - P2.y * 0.35));
    // 대비: 가운데(0.5) 기준 S자
    float k = P0.w;
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = k >= 1.0 ? mix(c, s, k - 1.0) : mix(vec3(0.5), c, k);
    // 생동감: 덜 물든 색일수록 더 밀어 준다
    float l = dot(c, LUMA);
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(c, l + (c - l) * (1.0 + P1.y), clamp(1.0 - sat * 1.5, 0.0, 1.0));
    // 채도
    l = dot(c, LUMA);
    c = l + (c - l) * P0.z;
    // 페이드: 검정을 살짝 들어 올리고 눌러서 필름 느낌
    c = c * (1.0 - P2.x * 0.18) + P2.x * 0.07;
    // 비네트: 가장자리 어둡게
    float d = distance(uv, vec2(0.5)) * 1.35;
    c *= 1.0 - P1.z * smoothstep(0.45, 1.05, d) * 0.85;
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    vec2 px = 1.0 / InSize;
    vec3 blur = (texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb + texture(DiffuseSampler, texCoord - vec2(px.x, 0.0)).rgb
        + texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb + texture(DiffuseSampler, texCoord - vec2(0.0, px.y)).rgb) * 0.25;
    fragColor = vec4(grade(c.rgb, texCoord, blur), 1.0);
}
