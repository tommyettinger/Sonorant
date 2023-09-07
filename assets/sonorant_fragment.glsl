#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP 
#endif

const float divisions = 5.0;
const float PHI = 0.61803398874989484820459; // phi, the Golden Ratio
const vec2 H2 = vec2(1.324717957244746, 1.754877666246693); // harmonious numbers for 2D
const vec3 H3 = vec3(0.8191725134, 0.6710436067, 0.5497004779); // harmonious numbers for 3D
const vec4 H4 = vec4(0.8566748838545, 0.7338918566271, 0.6287067210378, 0.5385972572236); // harmonious numbers for 4D

// Based on a Shadertoy: https://www.shadertoy.com/view/4dS3Wd
// By Morgan McGuire @morgan3d, http://graphicscodex.com
// Reuse permitted under the BSD license.

varying LOWP vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;

uniform float u_seed;
uniform float u_time;
uniform vec2 u_resolution;

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
                  dot(x, vec3(-0.333, -0.471,  0.816)),
                  dot(x, vec3(-0.333, -0.471, -0.816)));
    float a = noise(seed, p.yzw);
    float b = noise(seed + 42.1, p.xzw + a * H3.x);
    float c = noise(seed + 84.2, p.xyw + b * H3.y);
    float d = noise(seed + 126.3, p.xyz + c * H3.z);
    return smoothstep(0.0, 1.0, smoothstep(0.0, 1.0, (a + b + c + d) * 0.25));
}

float ridged(float seed, vec3 x) {
  float f = foam(seed, x);
  return 1. - abs(1. - 2. * f);
}

float barronSpline(float x) {
    const float shape = 1.7;
    const float turning = 0.9;
    float d = turning - x;
    return mix(
      ((1. - turning) * (x - 1.)) / (1. - (x + shape * d)) + 1.,
      (turning * x) / (1.0e-20 + (x + shape * d)),
      step(0.0, d));
}
vec4 hsl2rgb(vec4 c)
{
    const vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(barronSpline(c.x) + K.xyz) * 6.0 - K.www);
    float v = (c.z + c.y * min(c.z, 1.0 - c.z));
    return vec4(v * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), 2.0 * (1.0 - c.z / (v + 1e-10))), c.w);
}


float swayRandomized(float seed, float value)
{
    float f = floor(value);
    float start = sin((cos(f + seed) * 12.973 + seed) * 31.413);
    float end   = sin((cos(f + 1.0 + seed) * 12.973 + seed) * 31.413);
    return mix(start, end, smoothstep(0.0, 1.0, value - f)) * 0.625;
}


void main() {
  if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
  vec2 center = (gl_FragCoord.xy - u_resolution * 0.5) / 400.;
  float c = u_time, hc = c * (1.0/64.0), kc = c * (1.0/8.0);
  float theta = atan(center.y, center.x) * divisions + hc;
  float len = length(center);
  float shrunk = len * 16.0 / divisions;
  float adj = (len * 16. - c) * 0.5;
  float aa = (swayRandomized(-2.618 - -u_seed, adj) + 1.125) / 0.1;
  float bb = (swayRandomized(u_seed, 1.618 - adj) + 1.125) / 0.9;
  vec3 i = vec3(sin(theta) * shrunk, cos(theta) * shrunk, adj);
  float bright = pow(1.0 - pow(1.0 - ridged(4.3 + u_seed, i), bb), aa);
  gl_FragColor = hsl2rgb(vec4(
    fract(foam(1.111 + u_seed, i) * v_color.r + hc),
    sin(1.0 + bright * 1.375),
    sin(bright * 1.5),
    v_color.a));
}
