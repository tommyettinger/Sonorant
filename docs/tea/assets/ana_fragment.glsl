#ifdef GL_ES
#define LOWP lowp
precision highp float;
#else
#define LOWP 
#endif

// CC0 licensed, do what thou wilt.

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

void main() {
    if(texture2D(u_texture, v_texCoords).a <= 0.) discard;
    // Normalized pixel coordinates (from 0 to 1)
    vec2 uv = (gl_FragCoord.xy/u_resolution) * 6.283185307179586;
    // Used to show that this tiles on x and y.
    uv *= 2.0;
    float aTime = u_time * 0.125;
    vec4 s = vec4(swayRandomized(-16405.3157, aTime - 1.11),
                  swayRandomized(-77664.8142, aTime + 1.41),
                  swayRandomized(-50993.5190, aTime + 2.61),
                  swayRandomized(-42069.1984, aTime - 2.31)) * 1.5 * (vec4(sin(uv.xy), cos(uv.xy)) + 1.25);
    vec4 con = vec4(0.0004375, 0.0005625, 0.0008125, 0.000625) * aTime + s;
    con.x = cosmic(u_seed, con);
    con.y = cosmic(u_seed, con);
    con.z = cosmic(u_seed, con);

    gl_FragColor = vec4(sin(con.xyz * 3.14159265) * 0.5 + 0.5,1.0);
}
