#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP
#endif

const float PI2 = 6.283185307179586;
const float SCALE = 1.5;
const float POINTINESS = 11.0;

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_seed;
uniform float u_time;
uniform vec2 u_resolution;
uniform vec4 u_adj;

// A hue rotation on a vec3 representing RGB colors, returning an RGB vec3.
// Credit for HLSL version of applyHue() goes to Andrey-Postelzhuk,
// https://forum.unity.com/threads/hue-saturation-brightness-contrast-shader.260649/
vec3 applyHue(vec3 rgb, float hue)
{
    float h = fract(hue) * PI2;
    vec3 k = vec3(0.57735);
    float c = cos(h);
    //Rodrigues' rotation formula
    return rgb * c + cross(k, rgb) * sin(h) + k * dot(k, rgb) * (1.0 - c);
}

// 1D noise, ranging from -1.0 to 1.0.
// Changes to seed will change abruptly.
// Changes to value will change smoothly, but unpredictably, if those changes are smooth.
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
    // Only needed so v_texCoords and u_texture don't get eliminated for lack of use.
    if (texture2D(u_texture, v_texCoords).a <= 0.) discard;
    // The seed uniform includes the number of divisions, stored as the first digit, and made betweeen 2 and 12.
    float DIVISIONS = mod(floor(u_seed), 10.0) + 2.0;
    // This goes up and down with the a uniform, between 1 and 11.
    float TWISTINESS = sin(PI2 * u_adj.a) * 5.0 + 6.0;

    // Normalized pixel coordinates (from SCALE times -0.5 to 0.5 on y, typically less on x)
    vec2 center = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / u_resolution.y * SCALE;
    // The magnitude polar coordinate; how far the fragment we are rendering is from the center of the screen
    float len = length(center);
    // The angle from the center of the screen to the fragment we are rendering, times divisions, and rotating over time
    float theta = atan(center.y, center.x) * DIVISIONS + u_time;
    // With more divisions, we need a smaller length to avoid making changing areas too dense
    float shrunk = len * (0.375 * POINTINESS / DIVISIONS);
    // Time-adjusted angles; we call sin and cos on these.
    vec2 rel = vec2(theta + len * 5., len * PI2 * 0.75 - u_time);
    // No good name for this; it's sin and cos called on rel's angles, with the first two shrunk down based on divisions
    vec4 v = vec4(sin(rel.x) * shrunk, cos(rel.x) * shrunk, sin(rel.y), cos(rel.y));
    // A big part of the chaotic appearance comes from this.
    // All of the constants should be different, otherwise they don't really matter.
    vec4 s = vec4(sin(v.x - 1.11 + TWISTINESS * cos(v.x - 5.3157)),
                  sin(v.y + 1.41 + TWISTINESS * cos(v.y + 4.8142)),
                  sin(v.z + 2.61 + TWISTINESS * cos(v.z - 3.5190)),
                  sin(v.w - 2.31 + TWISTINESS * cos(v.w + 9.1984))) * 1.5;
    // Our g and b uniforms, taken from the 0-1 range to the 0-PI2 range so we can call sin and cos on them.
    vec2 angles = (u_adj.gb * PI2);
    // This incorporates everything so far except the seed at first, and then uses it too.
    vec4 con = vec4(0.4375, 0.5625, 0.8125, 0.625) + s + vec4(sin(angles), cos(angles)) * 4.0;
    con.x = cosmic(u_seed, con);
    con.y = cosmic(u_seed, con);
    con.z = cosmic(u_seed, con);

    // Gets con into a 0-1 range.
    con.xyz = sin((con.xyz) * 3.14159265) * 0.5 + 0.5;
    // Hue-rotates by the r uniform, if non-0, and sets alpha to 1, then tints by u_color.
    gl_FragColor = vec4(vec3(step(3.0, (con.x + con.y + con.z + u_adj.r))), 1.0) * v_color;
}
