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

float valueNoise(float seed, vec3 x) {
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

float foamNoise(float seed, vec3 x) {
    vec4 p = vec4(x.x,
                  dot(x.xy, vec2(-0.333, 0.942)),
                  dot(x, vec3(-0.333, -0.471,  0.816)),
                  dot(x, vec3(-0.333, -0.471, -0.816)));
    float a = valueNoise(seed, p.yzw);
    float b = valueNoise(seed + 42.1, p.xzw + a * H3.x);
    float c = valueNoise(seed + 84.2, p.xyw + b * H3.y);
    float d = valueNoise(seed + 126.3, p.xyz + c * H3.z);
    return smoothstep(0.0, 1.0, smoothstep(0.0, 1.0, (a + b + c + d) * 0.25));
}

// START MIT-LICENSED CODE BY QUILEZ

// The MIT License
// Copyright © 2013 Inigo Quilez
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

//vec3 hash(vec3 seed, vec3 p) {
//    return fract((dot((p + seed), H3) + seed) * (0.5 + fract(dot(H3.zxy - seed, p.yzx))));
//}

vec3 hash(float s, vec3 p) {
    return fract(fract((s - p) * PHI + p) * (PHI - s) - p);
}

//vec3 hash( vec3 p ) // replace this by something better
//{
//	p = vec3( dot(p,vec3(127.1,311.7, 74.7)),
//			  dot(p,vec3(269.5,183.3,246.1)),
//			  dot(p,vec3(113.5,271.9,124.6)));
//
//	return -1.0 + 2.0*fract(sin(p)*43758.5453123);
//}

float perlinNoise(float seed, vec3 p)
{
    vec3 i = floor( p );
    vec3 f = fract( p );

    // quintic interpolant
    vec3 u = f*f*f*(f*(f*6.0-15.0)+10.0);

    return mix( mix( mix( dot( hash(seed, i + vec3(0.0,0.0,0.0) ), f - vec3(0.0,0.0,0.0) ),
                          dot( hash(seed, i + vec3(1.0,0.0,0.0) ), f - vec3(1.0,0.0,0.0) ), u.x),
                     mix( dot( hash(seed, i + vec3(0.0,1.0,0.0) ), f - vec3(0.0,1.0,0.0) ),
                          dot( hash(seed, i + vec3(1.0,1.0,0.0) ), f - vec3(1.0,1.0,0.0) ), u.x), u.y),
                mix( mix( dot( hash(seed, i + vec3(0.0,0.0,1.0) ), f - vec3(0.0,0.0,1.0) ),
                          dot( hash(seed, i + vec3(1.0,0.0,1.0) ), f - vec3(1.0,0.0,1.0) ), u.x),
                     mix( dot( hash(seed, i + vec3(0.0,1.0,1.0) ), f - vec3(0.0,1.0,1.0) ),
                          dot( hash(seed, i + vec3(1.0,1.0,1.0) ), f - vec3(1.0,1.0,1.0) ), u.x), u.y), u.z );
}



float ridgedValueNoise(float seed, vec3 x) {
  float f = valueNoise(seed, x);
  return 1. - abs(1. - 2. * f);
}

float ridgedFoamNoise(float seed, vec3 x) {
  float f = foamNoise(seed, x);
  return 1. - abs(1. - 2. * f);
}

float ridgedPerlinNoise(float seed, vec3 x) {
  float f = perlinNoise(seed, x);
  return 1. - abs(1. - 2. * f);
}

// START HSL AND HUE CHANGES

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

// END HSL AND HUE CHANGES

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
  float c = u_time, dc = c * (1.0/64.0), hc = c * (1.0/32.0);
  float theta = atan(center.y, center.x) * divisions + dc;
  float len = length(center);
  float shrunk = len * 16.0 / divisions;
  float adj = (len * 16. - c) * 0.5;
  float aa = (swayRandomized(-2.618 - u_seed, adj) + 1.125) / (0.1 + v_color.g);
  float bb = (swayRandomized(u_seed, 1.618 - adj) + 1.125) / (0.9 + v_color.b);
  vec3 i = vec3(sin(theta) * shrunk, cos(theta) * shrunk, adj);
  float bright = pow(1.0 - pow(1.0 -
    mix(
      ridgedFoamNoise(4.3 + u_seed, i),
      ridgedPerlinNoise(-3.4 - u_seed, i),
      swayRandomized(u_seed * 3.618, dc + len))
    , bb), aa);
  gl_FragColor = hsl2rgb(vec4(
    fract(foamNoise(1.111 + u_seed, i) * v_color.r * 2.0 + hc),
    sin(1.0 + bright * 1.375),
    sin(bright * 1.5),
    v_color.a));
}
