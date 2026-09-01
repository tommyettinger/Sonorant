#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP
#endif

const float PHI = 0.61803398874989484820459; // phi, the Golden Ratio
const vec4 H4 = vec4(0.8566748838545029, 0.7338918566271260, 0.6287067210378087, 0.5385972572236101); // harmonious numbers for 4D

// This Shadertoy ( https://www.shadertoy.com/view/wssBz8 ) shows "Foam Noise" by Tommy Ettinger.
// It's just value noise that's rotated and domain warps the next result.

// Based on a Shadertoy: https://www.shadertoy.com/view/4dS3Wd
// By Morgan McGuire @morgan3d, http://graphicscodex.com
// Reuse permitted under the BSD license.

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_seed;
uniform float u_time;
uniform vec2 u_resolution;
uniform vec4 u_adj;

float hash(float seed, float p) {
    return fract(fract((p - seed) * PHI + seed) * (PHI - p) - seed);
}

float noise(float seed, vec4 x) {
    const vec4 step = vec4(59.0, 43.0, 37.0, 53.0); //vec3(110.0, 241.0, 171.0);

    vec4 i = floor(x);
    vec4 f = fract(x);

    float n = dot(i, step);

    vec4 u = f * f * (3.0 - 2.0 * f);
    return mix(
            mix(mix(mix( hash(seed, n                                  ), hash(seed, n + dot(step, vec4(1., 0., 0., 0.))), u.x),
                    mix( hash(seed, n + dot(step, vec4(0., 1., 0., 0.))), hash(seed, n + dot(step, vec4(1., 1., 0., 0.))), u.x), u.y),
                mix(mix( hash(seed, n + dot(step, vec4(0., 0., 1., 0.))), hash(seed, n + dot(step, vec4(1., 0., 1., 0.))), u.x),
                    mix( hash(seed, n + dot(step, vec4(0., 1., 1., 0.))), hash(seed, n + dot(step, vec4(1., 1., 1., 0.))), u.x), u.y), u.z),
            mix(mix(mix( hash(seed, n + dot(step, vec4(0., 0., 0., 1.))), hash(seed, n + dot(step, vec4(1., 0., 0., 1.))), u.x),
                    mix( hash(seed, n + dot(step, vec4(0., 1., 0., 1.))), hash(seed, n + dot(step, vec4(1., 1., 0., 1.))), u.x), u.y),
                mix(mix( hash(seed, n + dot(step, vec4(0., 0., 1., 1.))), hash(seed, n + dot(step, vec4(1., 0., 1., 1.))), u.x),
                    mix( hash(seed, n + dot(step, vec4(0., 1., 1., 1.))), hash(seed, n + dot(step, vec4(1., 1., 1., 1.))), u.x), u.y), u.z),
            u.w);
}

float foam(float seed, vec4 i) {
    float p0 = i.x;
    float p1 = i.x * -0.25 + i.y *  0.9682458365518543;
    float p2 = i.x * -0.25 + i.y * -0.3227486121839514 + i.z *  0.91287092917527690;
    float p3 = i.x * -0.25 + i.y * -0.3227486121839514 + i.z * -0.45643546458763834 + i.w *  0.7905694150420949;
    float p4 = i.x * -0.25 + i.y * -0.3227486121839514 + i.z * -0.45643546458763834 + i.w * -0.7905694150420947;
    float a = noise(seed, vec4(p1, p2, p3, p4));
    float b = noise(seed + 42.1,  vec4(p0 + a, p2, p3, p4));
    float c = noise(seed + 84.2,  vec4(p0 + b, p1, p3, p4));
    float d = noise(seed + 126.3, vec4(p0 + c, p2, p3, p4));
    float e = noise(seed + 168.4, vec4(p0 + d, p1, p2, p3));
    return smoothstep(0.0, 1.0, smoothstep(0.0, 1.0, (a + b + c + d + e) * 0.2));
}

void main() {
    if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
    vec4 adj = u_adj * 11.0;
    adj.a = adj.a + u_seed;
    vec4 i = vec4((gl_FragCoord.xy - u_resolution * 0.5) * 0.03125 + 3.0, sin(u_time) + adj.g, cos(u_time) + adj.b);
    float lightness = (0.5 * sin(u_adj.r * PI2));
    gl_FragColor.r = foam(4.0   + adj.a, i) + lightness;
    gl_FragColor.g = foam(61.0  + adj.a, i) + lightness;
    gl_FragColor.b = foam(257.0 + adj.a, i) + lightness;
    gl_FragColor.a = v_color.a;
}
