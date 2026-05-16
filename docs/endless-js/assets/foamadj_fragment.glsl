#ifdef GL_ES
#define LOWP lowp
precision highp float;
//const float PHI = 1.61803; // phi, the Golden Ratio
//const vec2 H2 = vec2(1.32471, 1.75487); // harmonious numbers for 2D
//const vec3 H3 = vec3(0.81917, 0.67104, 0.54970); // harmonious numbers for 3D
#else
#define LOWP
#endif

const float PHI = 0.61803398874989484820459; // phi, the Golden Ratio
const vec2 H2 = vec2(1.324717957244746, 1.754877666246693); // harmonious numbers for 2D
const vec3 H3 = vec3(0.8191725134, 0.6710436067, 0.5497004779); // harmonious numbers for 3D

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

float noise(float seed, vec3 x) {
    const vec3 step = vec3(59.0, 43.0, 37.0); //vec3(110.0, 241.0, 171.0);

    vec3 i = floor(x);
    vec3 f = fract(x);

    float n = dot(i, step);

    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix( hash(seed, n                              ), hash(seed, n + dot(step, vec3(1., 0., 0.))), u.x),
                   mix( hash(seed, n + dot(step, vec3(0., 1., 0.))), hash(seed, n + dot(step, vec3(1., 1., 0.))), u.x), u.y),
               mix(mix( hash(seed, n + dot(step, vec3(0., 0., 1.))), hash(seed, n + dot(step, vec3(1., 0., 1.))), u.x),
                   mix( hash(seed, n + dot(step, vec3(0., 1., 1.))), hash(seed, n + dot(step, vec3(1., 1., 1.))), u.x), u.y), u.z);
}

float foam(float seed, vec3 x) {
    vec4 p = vec4(x.x,
                 dot(x.xy, vec2(-0.333, 0.942)),
                 dot(x,    vec3(-0.333, -0.471,  0.816)),
                 dot(x,    vec3(-0.333, -0.471, -0.816)));
    float a = noise(seed, p.yzw);
    float b = noise(seed + 42.1, p.xzw + a * H3.x);
    float c = noise(seed + 84.2, p.xyw + b * H3.y);
    float d = noise(seed + 126.3, p.xyz + c * H3.z);
    return smoothstep(0.0, 1.0, smoothstep(0.0, 1.0, (a + b + c + d) * 0.25));
}

void main() {
    if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
    vec3 i = vec3((gl_FragCoord.xy - u_resolution * 0.5) * 0.03125 + 3.0, u_time) + u_adj.a;
    gl_FragColor.r = foam(4.0   + u_seed, i + u_adj.r);
    gl_FragColor.g = foam(61.0  + u_seed, i + u_adj.g);
    gl_FragColor.b = foam(257.0 + u_seed, i + u_adj.b);
    gl_FragColor.a = v_color.a;
}
