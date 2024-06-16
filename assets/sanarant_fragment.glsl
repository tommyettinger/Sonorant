#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP
#endif

const float PI2 = 6.283185307179586;
const float SCALE = 0.875;
const float POINTINESS = 11.0;

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_seed;
uniform float u_time;
uniform vec2 u_resolution;

float swayRandomized(float seed, float value)
{
    float f = floor(value);
    float start = sin((cos(f * seed) + sin(f * 1024.)) * 345. + seed);
    float end   = sin((cos((f+1.) * seed) + sin((f+1.) * 1024.)) * 345. + seed);
    return mix(start, end, smoothstep(0., 1., value - f));
}

float cosmic(float seed, vec4 con)
{
    float sum = swayRandomized(seed, con.w + con.x);
    sum = sum + swayRandomized(seed, con.z + con.y + sum);
    sum = sum + swayRandomized(seed, con.x + con.z + sum);
    sum = sum + swayRandomized(seed, con.y + con.w + sum);
    return sum * 0.25 + 0.5;
}

// 1D noise, range is -1.0 to 1.0
//float swayRandomized(float seed, float value)
//{
//    float f = floor(value);
//    float start = sin((cos(f + seed) * 12.973 + seed) * 31.413);
//    float end   = sin((cos(f + 1.0 + seed) * 12.973 + seed) * 31.413);
//    return mix(start, end, smoothstep(0.0, 1.0, value - f));
//}

void main() {
  if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
  float DIVISIONS = mod(floor(u_seed), 10.0) + 3.0;
  float TWISTINESS = sin(PI2 * v_color.a) * 5.0 + 6.0;

    // Normalized pixel coordinates (from 0 to 1)
    vec2 center = (gl_FragCoord.xy - 0.5 * u_resolution.xy)/u_resolution.y * SCALE;
  float c = u_time;
  float len = length(center);
  float theta = atan(center.y, center.x) * DIVISIONS + c;
  float shrunk = len * (0.375 * POINTINESS / DIVISIONS);
  float adj = (len * PI2 * 1.5 - c) * 0.5;
  vec2 i = vec2(theta + len * 5., adj);

    vec4 v = vec4(sin(i.x) * shrunk, cos(i.x) * shrunk, sin(i.y), cos(i.y));
    vec4 s = vec4(sin(v.x - 1.11 + TWISTINESS * cos(v.x - 5.3157)),
                  sin(v.y + 1.41 + TWISTINESS * cos(v.y + 4.8142)),
                  sin(v.z + 2.61 + TWISTINESS * cos(v.z - 3.5190)),
                  sin(v.w - 2.31 + TWISTINESS * cos(v.w + 9.1984))) * 1.5;
//    float aTime = c * 0.1 - len;
//    vec4 s = vec4(swayRandomized(-16405.3157, aTime - 1.11),
//                  swayRandomized(-77664.8142, aTime + 1.41),
//                  swayRandomized(-50993.5190, aTime + 2.61),
//                  swayRandomized(-42069.1984, aTime - 2.31)) * 1.5 * (vec4(sin(i.xy), cos(i.xy)) + 1.25);
//    vec4 con = vec4(0.0004375, 0.0005625, 0.0008125, 0.000625) + s;
    vec4 con = vec4(0.4375, 0.5625, 0.8125, 0.625) + s;
    con.x = cosmic(u_seed, con);
    con.y = cosmic(u_seed, con);
    con.z = cosmic(u_seed, con);

    con.xyz = sin((con.xyz + v_color.ggg) * 3.14159265) * 0.5 + 0.5;
    gl_FragColor = vec4(pow(con.xyz, 1.0 + 16.0 * (v_color.rrr)), 1.0);
}
