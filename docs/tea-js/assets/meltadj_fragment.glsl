#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP
#endif

// CC0 licensed, do what thou wilt.
// Derived from "Northern Melter" at https://www.shadertoy.com/view/7stSzN by Tommy Ettinger.

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_seed;
uniform float u_time;
uniform vec2 u_resolution;
uniform vec4 u_adj;


// Quilez Basic Noise, from https://www.shadertoy.com/view/3sd3Rs (MIT-license)
vec3 bas(vec3 x, vec3 section )
{
    // setup
    vec3 i = floor(x);
    vec3 f = fract(x);
    vec3 s = sign(fract(x/2.0)-0.5);

    // use some hash to create a random value k in [0..1] from i
    vec3 k = fract(section * i + i.yzx);

    // quartic polynomial
    return s*f*(f-1.0)*((16.0*k-4.0)*f*(f-1.0)-1.0);
}

// this is different from other swayRandomized in Northern demos because it uses Quilez basic noise instead of trigonometry.
vec3 swayRandomized(vec3 seed, vec3 value, vec3 section)
{
    return bas(seed.xyz + value.zxy - bas(seed.zxy + value.yzx, section) + bas(seed.yzx + value.xyz, section), section);
}

// this function, if given steadily-increasing values in con, may return exponentially-rapidly-changing results.
// even though it should always return a vec3 with components between -1 and 1, we use it carefully.
vec3 cosmic(vec3 c, vec3 con, vec3 section)
{
    return (con
    + swayRandomized(c, con, section)
    ) * 0.5;
}

void main() {
    if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
    // Normalized pixel coordinates (from 0 to 1)
    vec2 fragCoord = gl_FragCoord.xy * 512. / u_resolution.y;
    vec3 COEFFS = fract((u_seed + 23.4567) * vec3(0.8191725133961645, 0.6710436067037893, 0.5497004779019703)) + 0.5;
    vec3 section = fract(COEFFS.zxy - COEFFS.yzx * 1.618);
    vec2 uv = (fragCoord * 0.1) + swayRandomized(COEFFS.zxy, (u_time * 0.1875) * COEFFS.yzx - fragCoord.yxy * 0.004, section).xy * 42.0;
    // aTime, s, and c could be uniforms in some engines.
    float aTime = u_time * 0.0625;
    vec3 adj = u_adj.xyz;
    vec3 s = (swayRandomized(vec3(34.0, 76.0, 59.0), aTime + adj, section)) * 0.25;
    vec3 c = (swayRandomized(vec3(27.0, 67.0, 45.0), aTime - adj.yzx, section)) * 0.25;
    vec3 con = vec3(0.0004375, 0.0005625, 0.0008125) * aTime + c * uv.x + s * uv.y;

    con = cosmic(COEFFS, con, section);
    con = cosmic(COEFFS + 1.618, con, section);

    gl_FragColor = vec4(sin(con * 3.1416) * 0.5 + 0.5,1.0);
}
