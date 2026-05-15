#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP
#endif

// This Shadertoy ( https://www.shadertoy.com/view/Ns2BWy ) shows "Artisanal Gummi" by Tommy Ettinger.
// It's a plasma that moves around smoothly.

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_seed;
uniform float u_time;
uniform vec2 u_resolution;
uniform vec4 u_adj;


vec3 swayRandomized(vec3 seed, vec3 value)
{
    return sin(seed.xyz + value.zxy - cos(seed.zxy + value.yzx) + cos(seed.yzx + value.xyz));
}

vec3 cosmic(vec3 c, vec3 con)
{
    return (con
    + swayRandomized(c, con.yzx)
    + swayRandomized(c + 1.1, con.zxy)
    + swayRandomized(c + 2.2, con.xyz)) * 0.25;
}

void main() {
    if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
    vec3 COEFFS = fract((u_seed + 23.4567) * vec3(0.8191725133961645, 0.6710436067037893, 0.5497004779019703)) + 0.5;
    // Thanks to FabriceNeyret2 for suggesting fragCoord/iResolution.y
    vec2 uv = gl_FragCoord.xy/u_resolution.y * 24.0 + swayRandomized(COEFFS.zxy, (u_time * 0.1875) * COEFFS.yzx + u_adj.a).xy * 8.0;
    // aTime, s, and c could be uniforms in some engines.
    float aTime = u_time * 0.0625;
    vec3 adj = u_adj.xyz;
    vec3 s = (swayRandomized(vec3(34.0, 76.0, 59.0), aTime + adj));
    vec3 c = (swayRandomized(vec3(27.0, 67.0, 45.0), aTime - adj));
    vec3 con = vec3(0.0004375, 0.0005625, 0.0008125) * adj * aTime + c * uv.x + s * uv.y;

    con = cosmic(COEFFS, con);
    con = cosmic(COEFFS + 1.618, con + COEFFS);

    gl_FragColor = vec4(swayRandomized(COEFFS + 3.0, con * 3.14159265) * 0.5 + 0.5, 1.0);
}
