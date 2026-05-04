// Just hash functions that can be copied into other files.
// The floo hashes use no bit math, so they work on GLSL ES 2.0.
// Each floo hash takes an arbitrary-range vec2 and produces a float or floats between 0 inclusive and 1 exclusive.
// All mantissa bits for each of these hashes seem quite random.
// About 24 decent-quality random bits can be obtained from any of these hashes if the inputs are not too close.

vec3 floohash31(float p1)
{
    return fract(13.7548776662466927 * fract(15.5698402909980532 *
    exp(fract(vec3(0.8191725133961645, 0.6710436067037893, 0.5497004779019703) * p1)
    -fract(vec3(0.8566748838545029, 0.7338918566271260, 0.6287067210378087) * p1))
    ));
}

float floohash12(vec2 p2)
{
    return fract(13.7548776662466927 * fract(15.5698402909980532 *
    exp(fract(0.5497004779019703 * p2.x)
    -fract(0.8566748838545029 * p2.y))
    ));
}

vec2 floohash22(vec2 p2)
{
    return fract(13.7548776662466927 * fract(15.5698402909980532 *
    exp(fract(vec2(0.5497004779019703, 0.8191725133961645) * p2.x)
    -fract(vec2(0.8566748838545029, 0.6287067210378087) * p2.y))
    ));
}

vec3 floohash32(vec2 p2)
{
    return fract(13.7548776662466927 * fract(15.5698402909980532 *
    exp(fract(vec3(0.8191725133961645, 0.6710436067037893, 0.5497004779019703) * p2.x)
    -fract(vec3(0.8566748838545029, 0.7338918566271260, 0.6287067210378087) * p2.y))
    ));
}

vec4 floohash42(vec2 p2)
{
    return fract(13.7548776662466927 * fract(15.5698402909980532 *
    exp(fract(vec4(0.8566748838545029, 0.7338918566271260, 0.6287067210378087, 0.5385972572236101) * p2.x)
    -fract(vec4(0.8812714616335696, 0.7766393890897682, 0.6844301295853426, 0.6031687406857282) * p2.y))
    ));
}

// The triffid hashes work on int or ivec input and return one 32-bit int, all bits of which should be random.
// They don't use any bit math or unsigned ints, so should be compatible with GLSL ES 2.0.
// The hex constants are the top 32 bits of the constants for the R4 sequence by Martin Roberts.

int triffidhash1(int p)
{
    ivec4 v = p * p + (ivec4(0xDB4F0B91, 0xBBE05633, 0xA0F2EC75, 0x89E18285));
    v = (v * v + v) / ivec4(5, 3, 2, 1);
    return v.x + v.y + v.z + v.w;
}

int triffidhash2(ivec2 p)
{
    ivec4 v = p.xxyy * p.yxxy + (p.xyyx * ivec4(0xDB4F0B91, 0xBBE05633, 0xA0F2EC75, 0x89E18285));
    v = (v * v + v) / ivec4(5, 3, 2, 1);
    return v.x + v.y + v.z + v.w;
}

int triffidhash3(ivec3 p)
{
    ivec4 v = p.xyzz * p.yzxy + (p.zxyx * ivec4(0xDB4F0B91, 0xBBE05633, 0xA0F2EC75, 0x89E18285));
    v = (v * v + v) / ivec4(5, 3, 2, 1);
    return v.x + v.y + v.z + v.w;
}

int triffidhash4(ivec4 p)
{
    ivec4 v = p.xyzw * p.yzwx + (p.zwxy * ivec4(0xDB4F0B91, 0xBBE05633, 0xA0F2EC75, 0x89E18285));
    v = (v * v + v) / ivec4(5, 3, 2, 1);
    return v.x + v.y + v.z + v.w;
}
