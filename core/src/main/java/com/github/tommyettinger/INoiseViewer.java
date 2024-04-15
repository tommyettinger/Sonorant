package com.github.tommyettinger;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.tommyettinger.anim8.*;
import com.github.tommyettinger.digital.*;
import com.github.tommyettinger.ds.ObjectList;
import com.github.yellowstonegames.grid.*;

import static com.badlogic.gdx.Input.Keys.*;
import static com.badlogic.gdx.graphics.GL20.GL_POINTS;
import static com.github.tommyettinger.digital.MathTools.fract;

/**
 */
public class INoiseViewer extends ApplicationAdapter {

    private final INoise[] noises = new INoise[]{new CyclicNoise(1234567890L, 3), new CyclicNoise(1234567890L, 4), new CyclicNoise(1234567890L, 5),
            new SorbetNoise(1234567890L, 5), new SorbetNoise(1234567890L, 4), new SorbetNoise(1234567890L, 3),
            new FlanNoise(1234567890L, 4), new TaffyNoise(1234567890L, 4), new FoamplexNoise(1234567890L),
            new FoamNoise(1234567890L), new HoneyNoise(1234567890L), new PerlinNoise(1234567890L), new SimplexNoise(1234567890L),
            new SnakeNoise(1234567890L), new BadgerNoise(1234567890L), new ValueNoise(1234567890L)
    };
    private int noiseIndex = 0;
    private final NoiseWrapper noise = new NoiseWrapper(noises[noiseIndex], 1234567890L, 0.0625f, 2, 1);
    private final Noise varianceNoise = new Noise(-1, 0.025f, Noise.VALUE);
    private final ObjectList<Interpolations.Interpolator> interpolators = new ObjectList<>(Interpolations.getInterpolatorArray());
    private int interpolatorIndex = 58;
    private Interpolations.Interpolator interpolator = interpolators.get(interpolatorIndex);
    private float hue = 0;
    private float variance = 1f;
    private float hard = 0f;
    private float saturation = 1f;
    private int divisions = 2;
    private int octaves = 0;
    private float freq = 0.125f;
    private float a = 1f;
    private float b = 1f;
    private boolean paused;
    private boolean hueCycle = false;
    private ImmediateModeRenderer20 renderer;

    private Clipboard clipboard;
    public static final int width = 400, height = 400;
//    public static final int width = 512, height = 512;
//    public static final int width = 256, height = 256;
//    public static final int width = 64, height = 64;

//    private IntList colorList = new IntList(256);
//    private float[] colorFloats = new float[256];

    private Viewport view;
    private long startTime;

    private AnimatedGif gif;
//    private AnimatedPNG apng;
    private FastPNG png;
    private final Array<Pixmap> frames = new Array<>(256);

    public static float basicPrepare(float n)
    {
        return n * 0.5f + 0.5f;
    }

    public static int rgba8888 (float r, float g, float b, float a) {
        return ((int)(r * 255.999f) << 24) | ((int)(g * 255.999f) << 16) | ((int)(b * 255.999f) << 8) | (int)(a * 127.999f) << 1;
    }
    /**
     * Converts the four HSLA components, each in the 0.0 to 1.0 range, to an int in RGBA8888 format.
     * I brought this over from colorful-gdx's FloatColors class. I can't recall where I got the original HSL(A) code
     * from, but there's a strong chance it was written by cypherdare/cyphercove for their color space comparison.
     * It also includes a change to the hue (the fractional part of {@code h}) so cyan is less frequent and orange is
     * more frequent.
     *
     * @param h hue, usually from 0.0 to 1.0, but only the fractional part is used
     * @param s saturation, from 0.0 to 1.0
     * @param l lightness, from 0.0 to 1.0
     * @param a alpha, from 0.0 to 1.0
     * @return an RGBA8888-format int
     */
    public static int hsl2rgb(final float h, final float s, final float l, final float a) {
        // note: the spline is used here to change hue distribution so there's more orange, less cyan.
//        final float hue = (float) Math.pow((h - MathUtils.floor(h)) * 0.8f + 0.225f, 2f) - 0.050625f;
        final float hue = MathTools.barronSpline(h - MathUtils.floor(h), 1.7f, 0.9f);
        float x = Math.min(Math.max(Math.abs(hue * 6f - 3f) - 1f, 0f), 1f);
        float y = hue + (2f / 3f);
        float z = hue + (1f / 3f);
        y -= (int) y;
        z -= (int) z;
        y = Math.min(Math.max(Math.abs(y * 6f - 3f) - 1f, 0f), 1f);
        z = Math.min(Math.max(Math.abs(z * 6f - 3f) - 1f, 0f), 1f);
        float v = (l + Math.min(Math.max(s, 0f), 1f) * Math.min(l, 1f - l));
        float d = 2f * (1f - l / (v + 1e-10f));
        return rgba8888(v * MathUtils.lerp(1f, x, d), v * MathUtils.lerp(1f, y, d), v * MathUtils.lerp(1f, z, d), a);
    }

    static {
        int[] BIG_CAL = new int[] {
                0x00000000, 0x070708FF, 0x0B0A0AFF, 0x1C1A1BFF, 0x1D1D1EFF, 0x2D2C2EFF, 0x323231FF, 0x363839FF,
                0x3D3F3EFF, 0x424241FF, 0x4A4748FF, 0x494B4AFF, 0x4F4D4CFF, 0x555352FF, 0x555554FF, 0x605D5EFF,
                0x626361FF, 0x656666FF, 0x737676FF, 0x797A79FF, 0x807F7DFF, 0x9A9997FF, 0xA1A19EFF, 0xA6AAA9FF,
                0xABA9A9FF, 0xABACAEFF, 0xB1B2AFFF, 0xB4B2B1FF, 0xB6B3B3FF, 0xB7B5B7FF, 0xB5B9BAFF, 0xB8BBBAFF,
                0xBABDBBFF, 0xC8C8C5FF, 0xCAC7C8FF, 0xCBC9CBFF, 0xCBCAC8FF, 0xE4E6E5FF, 0xE5E6E6FF, 0xE9EAEBFF,
                0xEEECEBFF, 0xAA1D58FF, 0x3F1F29FF, 0xC22264FF, 0xEF367CFF, 0xF6307BFF, 0xA4084DFF, 0xA33458FF,
                0xFB2579FF, 0x8D5262FF, 0xD62669FF, 0xBC919BFF, 0xF8357BFF, 0xE5256DFF, 0x5C3D44FF, 0x84183FFF,
                0xD76986FF, 0xF66E93FF, 0xFD8AA6FF, 0xF65B87FF, 0x965766FF, 0x7A0635FF, 0x5D4348FF, 0xBB4C69FF,
                0xEB6083FF, 0xC997A0FF, 0xB2164CFF, 0xF81F69FF, 0xEEA4B1FF, 0xF05179FF, 0x421922FF, 0xE692A1FF,
                0xF37790FF, 0xD61B59FF, 0xD51256FF, 0xB59499FF, 0x3C2126FF, 0xD93B64FF, 0xB87580FF, 0xA91E46FF,
                0xAB5263FF, 0xD7929CFF, 0xE17B8CFF, 0xFA91A3FF, 0xF592A2FF, 0xFA3569FF, 0xAB2D4CFF, 0xFF2766FF,
                0xE41F57FF, 0xC13755FF, 0x422A2DFF, 0x7C102EFF, 0xA4364BFF, 0xB45F6BFF, 0x632E36FF, 0xA92A45FF,
                0xE8164FFF, 0xBF4556FF, 0x5F3A3DFF, 0xE03A54FF, 0xC44D5AFF, 0xFE184DFF, 0xBC7E81FF, 0xC99E9FFF,
                0xBE0D38FF, 0xE03850FF, 0xD23C4FFF, 0xF4264CFF, 0xC56D71FF, 0xDB7076FF, 0xFC3352FF, 0xAC676AFF,
                0xEFAFB0FF, 0xF93150FF, 0x62252AFF, 0x8C373DFF, 0xFD4157FF, 0x5F3A3BFF, 0xD64451FF, 0xFFA3A4FF,
                0xEA8486FF, 0xF84456FF, 0xE6787AFF, 0xE37778FF, 0xD3565AFF, 0xED8081FF, 0x37030DFF, 0x580B16FF,
                0xD31E34FF, 0x7D2D2FFF, 0xF67877FF, 0x824241FF, 0xF1444BFF, 0xAE2B32FF, 0x8C3737FF, 0x1C0C0CFF,
                0x6E181AFF, 0x93413DFF, 0xB9252AFF, 0xF25C57FF, 0xDDA8A3FF, 0xD82F31FF, 0xCA3937FF, 0xD49D97FF,
                0x7F3C37FF, 0x753D38FF, 0xAB5850FF, 0xFB1A20FF, 0xB35349FF, 0xEB685BFF, 0xE85B4FFF, 0xDF473CFF,
                0xB2090EFF, 0xF36A5AFF, 0xD64639FF, 0xD64235FF, 0x5B3D38FF, 0xB93F32FF, 0x58160FFF, 0xECCAC4FF,
                0xAB1C0EFF, 0x965F55FF, 0x6A3D35FF, 0xCB2814FF, 0xF89C8AFF, 0xD04733FF, 0xDB9384FF, 0x792D20FF,
                0x4E190FFF, 0x8B3F30FF, 0xEA502FFF, 0x3A1810FF, 0xF4866AFF, 0x784C3FFF, 0xF94E1FFF, 0xBF664EFF,
                0x401D14FF, 0x341209FF, 0x673324FF, 0x7F4332FF, 0x27150FFF, 0xB04E2FFF, 0xAF6A54FF, 0x815040FF,
                0xDB6841FF, 0x8A4027FF, 0xEA6231FF, 0x9C5E48FF, 0x89391BFF, 0xFFB192FF, 0xDC5C1DFF, 0x773E23FF,
                0xF3C0AAFF, 0x5B2C15FF, 0x7C5745FF, 0xF27638FF, 0xF6712AFF, 0xC96734FF, 0xF9C0A4FF, 0xD5713CFF,
                0xC25E26FF, 0xC4947BFF, 0xA35C35FF, 0xCF6013FF, 0xD06A2BFF, 0xDF6D21FF, 0x4F2B15FF, 0xCCAA97FF,
                0xC38D6EFF, 0xD26820FF, 0x533725FF, 0xE27E3DFF, 0xDA9971FF, 0xF8A068FF, 0x8F7564FF, 0xE9853FFF,
                0x915328FF, 0xE47C2FFF, 0x7B5E4AFF, 0xECA876FF, 0xD87319FF, 0x9F560CFF, 0x85674DFF, 0x854F1AFF,
                0xEDAA6EFF, 0x7B5027FF, 0x8C551AFF, 0xFDC38CFF, 0xC9AC91FF, 0x7C6046FF, 0x674827FF, 0x6C4210FF,
                0xAE712BFF, 0xE99021FF, 0xBA9976FF, 0x79562FFF, 0xC97C13FF, 0x4F3A22FF, 0xF49A25FF, 0xEFAD5FFF,
                0xDA9F58FF, 0x94662BFF, 0xCF9246FF, 0x725632FF, 0xD7AF7CFF, 0xFBB04AFF, 0xC08C45FF, 0xC78C36FF,
                0xBC9A69FF, 0xF4AE3DFF, 0xBC8529FF, 0x9E8E76FF, 0xF4C16DFF, 0xDFB778FF, 0x4E3F28FF, 0xF9B330FF,
                0xC0A982FF, 0xD7AA59FF, 0xC49C4AFF, 0xB9AB8FFF, 0xBFA46FFF, 0xDDC389FF, 0xC5992EFF, 0x614D19FF,
                0x916F12FF, 0xD4B461FF, 0xE8CE8BFF, 0x9C7B0CFF, 0x836D2CFF, 0x9C874BFF, 0x544413FF, 0xEBE1C3FF,
                0xEFC114FF, 0xFFDC67FF, 0x917928FF, 0xEDCA4BFF, 0xD1C596FF, 0xB59C3CFF, 0xDFC24BFF, 0x9A8A46FF,
                0xE9CF5FFF, 0x7A6E3DFF, 0xB79C10FF, 0xCBB965FF, 0x5E531FFF, 0xF4D84AFF, 0x6F642FFF, 0xFFEC88FF,
                0x655B25FF, 0xF4D942FF, 0xFCE45FFF, 0xB4A343FF, 0x7D6E0EFF, 0x988A33FF, 0x8C834CFF, 0x6C6530FF,
                0x6C6A4EFF, 0xB7A93AFF, 0xA6A15EFF, 0xFEEE3AFF, 0x8C842AFF, 0x898760FF, 0x928A25FF, 0x5D591DFF,
                0x87843FFF, 0xB9B55CFF, 0xF9F699FF, 0x6C6C4BFF, 0xDDD940FF, 0xC4C58DFF, 0xF4F365FF, 0xFBFDBEFF,
                0xA8AA59FF, 0xF2F45FFF, 0x939642FF, 0xD6DC30FF, 0xBCC17EFF, 0x81853DFF, 0xD6E032FF, 0xA0A761FF,
                0x636919FF, 0x7C853AFF, 0x3C4028FF, 0xA7B558FF, 0x646F27FF, 0xE2F590FF, 0x515C0DFF, 0x99AB3CFF,
                0xDBFD57FF, 0x717B52FF, 0x9AA774FF, 0xB6BEA2FF, 0x87A02FFF, 0x788559FF, 0xAFBD8EFF, 0xDBFF77FF,
                0xC5ED4DFF, 0xA7C068FF, 0x909C74FF, 0x84A70AFF, 0xDDFB9DFF, 0xA4BC6EFF, 0x5C7320FF, 0x657348FF,
                0x3B4919FF, 0xAED554FF, 0xD1FD6DFF, 0x637C20FF, 0x839858FF, 0xB3E042FF, 0x7B983BFF, 0xCCFD64FF,
                0x8AAD3FFF, 0x759236FF, 0x709220FF, 0x9ED021FF, 0x788660FF, 0x576C34FF, 0x93AA70FF, 0xA5E22EFF,
                0xAFBE9DFF, 0x759348FF, 0xC2F576FF, 0x536E28FF, 0x4B622AFF, 0x5E8322FF, 0x415D11FF, 0x84AF4DFF,
                0x677756FF, 0x5D8328FF, 0x8AB358FF, 0xC3E4A2FF, 0xB8D798FF, 0x6C8552FF, 0x76AA3AFF, 0x70A339FF,
                0x6D904DFF, 0x69A921FF, 0xD9F5C4FF, 0x80D028FF, 0x77AA4BFF, 0x6EB522FF, 0x8BBE63FF, 0x76BF33FF,
                0x15200BFF, 0x405C29FF, 0x81B15DFF, 0xA3E377FF, 0x829576FF, 0x95DE68FF, 0x93B97EFF, 0x98B38AFF,
                0x38641CFF, 0x95B884FF, 0x71C444FF, 0x31541DFF, 0x53962FFF, 0x6FAD51FF, 0x355A23FF, 0x9EB494FF,
                0x1E2F16FF, 0x90FC63FF, 0x1C3412FF, 0x87FD56FF, 0xC6FFB2FF, 0x83EB5BFF, 0x65C640FF, 0x3C6C2EFF,
                0x94D085FF, 0x308019FF, 0x7CE864FF, 0x5FFB3BFF, 0x54E23AFF, 0x63C455FF, 0x207C16FF, 0x0E3A09FF,
                0x41823AFF, 0x5BA256FF, 0x1BD919FF, 0x1C781BFF, 0xC4FCBFFF, 0x265224FF, 0x45BF43FF, 0x2B6E2CFF,
                0xA2D5A0FF, 0x64C663FF, 0x097515FF, 0x37C53FFF, 0x3FFC4FFF, 0x48BB4EFF, 0x246528FF, 0x59BE5EFF,
                0x2D9F39FF, 0x10A22DFF, 0x477449FF, 0x6EB272FF, 0x24B83FFF, 0x4A754DFF, 0x60D26DFF, 0x247F35FF,
                0x18B342FF, 0x2B9240FF, 0x18FA5CFF, 0x459752FF, 0x44A656FF, 0x53E972FF, 0x1CBC4BFF, 0x35C859FF,
                0x6FF889FF, 0x0E8A38FF, 0x68BA77FF, 0x45BF63FF, 0x97F0ADFF, 0x47BF6DFF, 0x50AB6CFF, 0x31854EFF,
                0x37CC6FFF, 0x339255FF, 0xA7D7B4FF, 0xA3E0B4FF, 0x2BF182FF, 0x4FA46CFF, 0x93E2ABFF, 0x143922FF,
                0x6EA881FF, 0x4DF692FF, 0x3FB06EFF, 0x477457FF, 0x10D176FF, 0x10BA6AFF, 0x80EAA8FF, 0x70B88BFF,
                0x11C672FF, 0x33F090FF, 0x1D784AFF, 0x5BDC95FF, 0x14482DFF, 0x13B16AFF, 0x42594BFF, 0x61B283FF,
                0x77E9A7FF, 0x4EA675FF, 0x3EB678FF, 0x5AB080FF, 0x44A371FF, 0x2C8258FF, 0x14DD93FF, 0x82E8B6FF,
                0x80AC95FF, 0x62CA99FF, 0x6FB492FF, 0x257C57FF, 0x2EF9ABFF, 0x2F6950FF, 0x4DE5AAFF, 0xC5FCE2FF,
                0x216249FF, 0x74F4C0FF, 0x227154FF, 0x1FB285FF, 0x2FBB8FFF, 0x4B7767FF, 0x4DAA8AFF, 0x245D4BFF,
                0x19A783FF, 0x87D2BBFF, 0x78B3A1FF, 0x3F7F6EFF, 0x61A995FF, 0xAEFEE7FF, 0x6A9B8DFF, 0x26A78CFF,
                0x085F50FF, 0x55CAB2FF, 0x68B1A2FF, 0x58B8A6FF, 0x1D8B7AFF, 0x2C776AFF, 0x48BEAAFF, 0x26544CFF,
                0x1E8677FF, 0x739B95FF, 0x5DAEA4FF, 0x0E766EFF, 0x49C8C0FF, 0x6FF9F1FF, 0x3D5D5BFF, 0x3CE9E5FF,
                0x8EC8C6FF, 0x9ECFCEFF, 0x193D3EFF, 0x6BACAEFF, 0x3B7375FF, 0x224C4EFF, 0x25A8AFFF, 0x6CC3C9FF,
                0x17575BFF, 0x6DB5BAFF, 0x4CBDC5FF, 0x91E7EFFF, 0x4ACBD9FF, 0x9DE5EEFF, 0x99CAD1FF, 0x65DFF5FF,
                0x12BFDAFF, 0x3DAABFFF, 0x032C34FF, 0x25505AFF, 0x32D5F8FF, 0x47BAD5FF, 0x3C8294FF, 0x6ECDE7FF,
                0x68C4DEFF, 0x4C889AFF, 0x2D4C55FF, 0x22849FFF, 0x45CBF1FF, 0x42B2D3FF, 0x42BBE3FF, 0x0E627DFF,
                0x154354FF, 0x2BC4FFFF, 0x1BADE9FF, 0x1F729AFF, 0x47B1E9FF, 0x476B80FF, 0x2D8BBFFF, 0x98C8E7FF,
                0x072A3CFF, 0x2E6384FF, 0x03344CFF, 0x84C8F6FF, 0x397DA8FF, 0xC4E3F7FF, 0x698BA2FF, 0x1885C4FF,
                0x9FC9EBFF, 0x5FA8E0FF, 0x7FA5C4FF, 0x103C5DFF, 0x06121CFF, 0x6FABE0FF, 0x4186C2FF, 0x1163A1FF,
                0x63B5FFFF, 0x1181DCFF, 0x46A1F8FF, 0x67B1F9FF, 0x22415FFF, 0x8DC4FDFF, 0x0F3457FF, 0x2071C5FF,
                0x1D5187FF, 0x91A8C1FF, 0x27558AFF, 0x0D3158FF, 0x125499FF, 0x426D9FFF, 0x549DF3FF, 0x235CA0FF,
                0x226BC9FF, 0x68A1F7FF, 0xB4C5DCFF, 0x78A8F0FF, 0x8799B3FF, 0x0D6AEDFF, 0x2865C8FF, 0x113C81FF,
                0x6491DDFF, 0x3E557BFF, 0x203D71FF, 0x90B5F5FF, 0x0E2C66FF, 0x3B69C5FF, 0x0E2044FF, 0x050E22FF,
                0x305FC3FF, 0x4577E4FF, 0x2560F4FF, 0x81A3E8FF, 0x2861FFFF, 0x77A0F9FF, 0x3F70EEFF, 0x21409BFF,
                0x3F68D8FF, 0x6D87C4FF, 0x1632A4FF, 0x2246EAFF, 0x2950E9FF, 0x7088C2FF, 0xACBCE1FF, 0x0D184CFF,
                0x1713AFFF, 0x3854C2FF, 0x2B44D1FF, 0x0F0E6DFF, 0x324DF3FF, 0x334AF4FF, 0x2118D2FF, 0x7F9DFCFF,
                0x172167FF, 0x2119CEFF, 0x3A4A86FF, 0x171188FF, 0x2104C2FF, 0x2510D8FF, 0x3241E2FF, 0x19224BFF,
                0x495DC0FF, 0x4457E9FF, 0x3333DFFF, 0x4454E6FF, 0x7382BDFF, 0x1E1888FF, 0x3036B0FF, 0x464FF7FF,
                0x5160DBFF, 0x8999E1FF, 0x232088FF, 0x2D3461FF, 0x4750D4FF, 0x3D46A6FF, 0x3F4996FF, 0x0B052EFF,
                0x3E2AF2FF, 0x3536ACFF, 0x7585FFFF, 0x292E6DFF, 0x27109AFF, 0x110842FF, 0x414879FF, 0x2C2697FF,
                0x9BA6E3FF, 0xB6BEEDFF, 0x3F449BFF, 0x4527FCFF, 0xA9B3FDFF, 0x838CD3FF, 0x747BF6FF, 0x4E45E3FF,
                0x9FA7ECFF, 0x4F47DBFF, 0x6C72D4FF, 0x4437C8FF, 0x8086C5FF, 0x33346BFF, 0x5757C4FF, 0x5B5ACAFF,
                0x6462E3FF, 0xCCCFEFFF, 0x797BEAFF, 0x4C2FDEFF, 0x5B4AEFFF, 0x554BCFFF, 0x383383FF, 0xCACDFDFF,
                0x4B03E6FF, 0x403B8DFF, 0x4F4C9BFF, 0x4D2BD5FF, 0x8587C0FF, 0x4430AFFF, 0x6E60EBFF, 0x4729B7FF,
                0x51507DFF, 0x5F5D93FF, 0x5F33EDFF, 0x646195FF, 0x5743B9FF, 0xC3C2E2FF, 0x564F8DFF, 0x6A3BF6FF,
                0x7B63EFFF, 0x382875FF, 0x5A24D7FF, 0x2A2645FF, 0x0E0224FF, 0x5631BFFF, 0x9F95E3FF, 0x6622F1FF,
                0x7B69D1FF, 0x6A4DD0FF, 0x362867FF, 0x7A4CFDFF, 0x9081DDFF, 0x724CE2FF, 0x7240ECFF, 0x4A14A8FF,
                0x7143E6FF, 0x3A167FFF, 0x45049DFF, 0x8A72E2FF, 0xABA3D8FF, 0x612DCBFF, 0x784DE8FF, 0x8573C9FF,
                0x9982E8FF, 0xA58AFFFF, 0x482E87FF, 0x946FFCFF, 0x6D0BEBFF, 0x652FCAFF, 0x642EC8FF, 0x1E103AFF,
                0x8555F1FF, 0xAFA1E2FF, 0x1D182DFF, 0xD3C7FEFF, 0x5F5481FF, 0x7140D0FF, 0x8879B5FF, 0x6125BFFF,
                0x6B28D0FF, 0x9772ECFF, 0xB19FE4FF, 0x634B9AFF, 0x4A3871FF, 0x6A23CBFF, 0x352D48FF, 0x9B74EFFF,
                0x9768F1FF, 0x6D32C7FF, 0x7711E9FF, 0x9C73E7FF, 0x7F4FCDFF, 0x7714E0FF, 0x885ECCFF, 0x9248F8FF,
                0x8D6ACAFF, 0x7C21E1FF, 0x6929B7FF, 0x591B9FFF, 0x6E5697FF, 0x8038DBFF, 0x8C39F0FF, 0x4B376CFF,
                0x4A326EFF, 0x9146EFFF, 0x59358BFF, 0x8D56D9FF, 0x8712F0FF, 0x855EC0FF, 0x754CADFF, 0x8B0EF6FF,
                0x5B2996FF, 0x52416CFF, 0x301250FF, 0x52307FFF, 0x8D21F4FF, 0x8356BEFF, 0x3D1B62FF, 0x2E1449FF,
                0x5D457DFF, 0x564A67FF, 0x824BBEFF, 0xA491BDFF, 0x652A9CFF, 0x472A65FF, 0x825EAAFF, 0x9C3AF2FF,
                0xB173F3FF, 0x8E39D5FF, 0x9D41EAFF, 0x7124ADFF, 0x6B359CFF, 0x9864CAFF, 0x3C095DFF, 0xC297EEFF,
                0xA56ED7FF, 0xAF8BD2FF, 0x8B53BBFF, 0xC4B3D5FF, 0xB24EFFFF, 0x8D1BD7FF, 0x783BA8FF, 0x7D39B2FF,
                0xB06EE7FF, 0xB455FCFF, 0x2F1E3DFF, 0x9C55D0FF, 0xA78FBAFF, 0x9B24E2FF, 0xC996F2FF, 0xB96EF0FF,
                0x8A1BCAFF, 0x9823DDFF, 0xA51EF0FF, 0xA53BE8FF, 0xB79BCDFF, 0xAB3FEEFF, 0xDBB0FBFF, 0x612D82FF,
                0xA92FECFF, 0xB93EFFFF, 0xC88BF2FF, 0x8134ADFF, 0xA221E3FF, 0x5F2480FF, 0xB84CF6FF, 0xB04BE8FF,
                0xD8C2E7FF, 0x711D9AFF, 0x9661B4FF, 0xBB92D4FF, 0xD39CF2FF, 0x9D44CAFF, 0x7A3A9AFF, 0xC967FAFF,
                0xC563F6FF, 0x70348BFF, 0x9831C3FF, 0xA230CFFF, 0xB269D1FF, 0xC1A4CEFF, 0xAB4BD0FF, 0xAB10DDFF,
                0xDB98F5FF, 0xBC28EDFF, 0xA249C2FF, 0x83319FFF, 0xBF83D4FF, 0x744585FF, 0xDA90F3FF, 0x500A63FF,
                0xC16CDCFF, 0x70487DFF, 0x9A31B8FF, 0xC058DFFF, 0xD95AFDFF, 0xA54EBCFF, 0xDAA4E8FF, 0xD627FFFF,
                0xD193E1FF, 0x511060FF, 0xE88BFFFF, 0x8C479DFF, 0xB54ECEFF, 0xC04FD9FF, 0x902BA5FF, 0xBD75CDFF,
                0x7D5287FF, 0xDA3DFAFF, 0xB04CC5FF, 0xDC6AF2FF, 0xE396F0FF, 0xAC57BCFF, 0xF4AAFFFF, 0xC048D6FF,
                0x70317AFF, 0xCF77DCFF, 0xB372BDFF, 0xBD32D1FF, 0xD73BEDFF, 0xBE3FCEFF, 0xA121B0FF, 0xCD21E0FF,
                0xEF70FCFF, 0xBE0BCEFF, 0xE050EDFF, 0xE171EAFF, 0x9A539EFF, 0xECB2EEFF, 0xD683D9FF, 0xF56AFDFF,
                0xD10ADEFF, 0x674768FF, 0x9B36A1FF, 0xEC24F8FF, 0xB828C0FF, 0x7B0F81FF, 0xA536A9FF, 0xF12CF8FF,
                0xFA6DFCFF, 0xD863D8FF, 0xF278F1FF, 0xD618D9FF, 0x371A37FF, 0xAA69A8FF, 0x862186FF, 0xDABED8FF,
                0x531853FF, 0xCF1DCEFF, 0xCD4CC9FF, 0xE558E0FF, 0x845081FF, 0x972294FF, 0xF632EFFF, 0xC864C1FF,
                0xE04DD8FF, 0xFCBFF5FF, 0xF2C6EBFF, 0x5A2D54FF, 0xF748E7FF, 0xF476E5FF, 0x813279FF, 0xD735C7FF,
                0x885180FF, 0x2B1328FF, 0xEC23DAFF, 0xF97FE8FF, 0x9F1692FF, 0xE15AD0FF, 0xF03BDCFF, 0xF461E1FF,
                0xF230DDFF, 0xE40FCEFF, 0x472A42FF, 0xD38EC6FF, 0xE987D6FF, 0xC93AB4FF, 0x6E3D65FF, 0xCD82BDFF,
                0xA42C91FF, 0xDD43C3FF, 0xCC3CB3FF, 0xF085D9FF, 0xCE91BFFF, 0xB5299DFF, 0xC841ADFF, 0xB23C9AFF,
                0xDB23BBFF, 0xBA3AA0FF, 0xF93DD5FF, 0xF23FCFFF, 0x431639FF, 0xF96FD7FF, 0xE994D2FF, 0xB13896FF,
                0xFC35D3FF, 0x821F6DFF, 0x921B79FF, 0xF634CBFF, 0xEC85CFFF, 0xE856C1FF, 0xC149A0FF, 0xF852CAFF,
                0xDE2EB1FF, 0xF90EC4FF, 0xFF71D3FF, 0x963F7CFF, 0xCF80B3FF, 0xE21EAFFF, 0x591546FF, 0xE27FBFFF,
                0xE835B2FF, 0xCD399EFF, 0xD635A4FF, 0xF616B5FF, 0xE812A9FF, 0xC3659DFF, 0xE334A3FF, 0x952F6DFF,
                0xEC16A5FF, 0x760C52FF, 0xF582C2FF, 0xCC278DFF, 0x492839FF, 0xFC42ADFF, 0x3A0D27FF, 0xDE3C98FF,
                0xFC69B7FF, 0xB05E86FF, 0xA5156AFF, 0xFA83BDFF, 0xE43997FF, 0xD2308AFF, 0xC82081FF, 0x502B3CFF,
                0xF753A5FF, 0x4A0A2EFF, 0x894865FF, 0x491730FF, 0xB02068FF, 0xFA4498FF, 0x6D153FFF, 0xAF1F64FF,
                0x942557FF, 0xEC72A2FF, 0xFC2D8EFF, 0x4C162DFF, 0xF25394FF, 0x753B4FFF, 0x562236FF, 0xD7407AFF,
        };
        System.arraycopy(BIG_CAL, 0, PaletteReducer.BIG_AURORA, 0, BIG_CAL.length);
    }

    public INoiseViewer(Clipboard clippy) {
        clipboard = clippy;
    }

    @Override
    public void create() {
        if(clipboard == null) clipboard = Gdx.app.getClipboard();

        noise.setWrapped(noises[noiseIndex]);
        noise.setFractalType(Noise.RIDGED_MULTI);

//        apng = new AnimatedPNG();
        if(Gdx.app.getType() != Application.ApplicationType.WebGL) {
            gif = new AnimatedGif();
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.WREN);
            gif.setDitherStrength(0.2f);
            gif.palette = new QualityPalette();
//            gif.fastAnalysis = false;
//            png = new FastPNG();
//            png.setCompression(2);
        }

//        colorList.toArray(gif.palette.paletteArray);
//
//        IntList g = ColorGradients.toRGBA8888(ColorGradients.appendGradientChain(new IntList(256), 256, Interpolation.smooth::apply,
//                // cool blue
//                DescriptiveColor.oklabByHSL(0.68f, 0.85f, 0.2f, 1f),
//                DescriptiveColor.oklabByHSL(0.70f, 0.95f, 0.4f, 1f),
//                DescriptiveColor.oklabByHSL(0.62f, 1f, 0.55f, 1f),
//                DescriptiveColor.oklabByHSL(0.65f, 0.7f, 0.8f, 1f)
//                // rosy
////                DescriptiveColor.oklabByHSL(0.98f, 0.85f, 0.2f, 1f),
////                DescriptiveColor.oklabByHSL(0.00f, 0.95f, 0.4f, 1f),
////                DescriptiveColor.oklabByHSL(0.02f, 1f, 0.55f, 1f),
////                DescriptiveColor.oklabByHSL(0.01f, 0.7f, 0.8f, 1f)
//        ));
//        g.toArray(gif.palette.paletteArray);

        startTime = TimeUtils.millis();
        renderer = new ImmediateModeRenderer20(width * height * 2, false, true, 0);
        ShaderProgram hslShader = new ShaderProgram(
                "attribute vec4 a_position;\n" +
                        "attribute vec4 a_color;\n" +
                        "uniform mat4 u_projModelView;\n" +
                        "varying vec4 v_col;\n" +
                        "void main() {\n" +
                        "   gl_Position = u_projModelView * a_position;\n" +
                        "   v_col = a_color;\n" +
                        "   v_col.a *= 255.0 / 254.0;\n" +
                        "   gl_PointSize = 1.0;\n" +
                        "}",
                "#ifdef GL_ES\n" +
                        "precision mediump float;\n" +
                        "#endif\n" +
                        "varying vec4 v_col;\n" +
                        "float barronSpline(float x) {\n" +
                        "    const float shape = 1.7;\n" +
                        "    const float turning = 0.9;\n" +
                        "    float d = turning - x;\n" +
                        "    return mix(\n" +
                        "      ((1. - turning) * (x - 1.)) / (1. - (x + shape * d)) + 1.,\n" +
                        "      (turning * x) / (1.0e-20 + (x + shape * d)),\n" +
                        "      step(0.0, d));\n" +
                        "}\n" +
                        "vec4 hsl2rgb(vec4 c)\n" +
                        "{\n" +
                        "    const vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);\n" +
                        "    vec3 p = abs(fract(barronSpline(c.x) + K.xyz) * 6.0 - K.www);\n" +
                        "    float v = (c.z + c.y * min(c.z, 1.0 - c.z));\n" +
                        "    return vec4(v * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), 2.0 * (1.0 - c.z / (v + 1e-10))), c.w);\n" +
                        "}" +
                        "void main() {\n" +
                        "   gl_FragColor = hsl2rgb(v_col);\n" +
                        "}");
        if(!hslShader.isCompiled())
            System.out.println("HSL Shader compilation failed: " + hslShader.getLog());
        renderer.setShader(hslShader);
//        System.out.println();
//        System.out.println(renderer.getShader().getVertexShaderSource());
//        System.out.println();
//        System.out.println(renderer.getShader().getFragmentShaderSource());
//        System.out.println();
        view = new ScreenViewport();
        InputAdapter input = new InputAdapter() {
            @Override
            public boolean keyUp(int keycode) {
                int s;
                long ls;
                switch (keycode) {
                    case SPACE: // pause
                        paused = !paused;
                        break;
                    case E: //earlier seed
                        ls = noise.getSeed() - 1;
                        noise.setSeed(ls);
                        System.out.println("Using seed " + ls);
                        break;
                    case S: //seed after
                        ls = noise.getSeed() + 1;
                        noise.setSeed(ls);
                        System.out.println("Using seed " + ls);
                        break;
                    case SLASH: //random seed
                        ls = Hasher.randomize3(noise.getSeed());
                        noise.setSeed(ls);
                        System.out.println("Using seed " + ls);
                        break;
                    case N: // noise type
                        noise.setWrapped(noises[noiseIndex = (noiseIndex + (UIUtils.shift() ? noises.length - 1 : 1)) % noises.length]);
                        break;
                    case ENTER:
                    case D: //dimension
                        divisions = (divisions + (UIUtils.shift() ? 9 : 1)) % 10;
                        break;
                        // commented out because changing this makes the looping break.
//                    case F: // frequency
//                        noise.setFrequency(freq *= (UIUtils.shift() ? 1.25f : 0.8f));
//                        break;
                    case R: // fRactal type
                        noise.setFractalType((noise.getFractalType() + (UIUtils.shift() ? 3 : 1)) & 3);
                        break;
                    case H: // higher octaves
                        noise.setFractalOctaves((octaves = octaves + 1 & 7) + 1);
                        break;
                    case L: // lower octaves
                        noise.setFractalOctaves((octaves = octaves + 7 & 7) + 1);
                        break;
                    case I: // interpolator
                        interpolatorIndex = (interpolatorIndex + (UIUtils.shift() ? interpolators.size() - 1 : 1)) % interpolators.size();
                        interpolator = interpolators.get(interpolatorIndex);
                        break;
                    case BACKSLASH: // fractal spiral mode, I don't know if there is a mnemonic
                        noise.setFractalSpiral(!noise.isFractalSpiral());
                        break;
                    case Y:
                        hueCycle = !hueCycle;
                        break;
                    case P: { // paste
                        if (clipboard.hasContents()) {
                            String paste = clipboard.getContents();
                            int last = paste.lastIndexOf('`');
                            if (last >= 1) {
                                Base base = Base.BASE10;
                                //9~`FoaN`1234567890`~-2520166047429742850~0.0625~3~2~0`~4~circleIn~-0.098345466~1.0~2.4474833~0.8675451~1.0~0.1~1712959125737
                                noiseIndex = (base.readInt(paste) % noises.length + noises.length) % noises.length;
                                noise.stringDeserialize(paste.substring(paste.indexOf('~') + 1));
                                divisions = base.readInt(paste, last + 2, last = paste.indexOf('~', last + 2));
                                interpolatorIndex = interpolators.indexOf(interpolator =
                                        Interpolations.get(paste.substring(last + 1, last = paste.indexOf('~', last + 1))));
                                hue = base.readFloat(paste, last + 1, last = paste.indexOf('~', last + 1));
                                variance = base.readFloat(paste, last + 1, last = paste.indexOf('~', last + 1));
                                a = base.readFloat(paste, last + 1, last = paste.indexOf('~', last + 1));
                                if(a <= 0) a = 1f;
                                b = base.readFloat(paste, last + 1, last = paste.indexOf('~', last + 1));
                                if(b <= 0) b = 1f;
                                hard = base.readFloat(paste, last + 1, last = paste.indexOf('~', last + 1));
                                if(hard <= 0) hard = 0f;
                                saturation = base.readFloat(paste, last + 1, last = paste.indexOf('~', last + 1));
                                if(saturation <= 0) saturation = 0f;
                                prettyPrint();
                            }
                        } else
                            System.out.println("Clipboard is empty!");
                    }
                    //`322420472~11~1~2~1~0~0~0~32829~64~63~-466088384~1701193279~164548413`~2~pow0_75~0.7793921~0.1937514~1.0~1.0~1700476003842
                    break;
                    case A: // analyze
                        prettyPrint();
                        break;
                    case Q: // quit
                    case ESCAPE: {
                        Gdx.app.exit();
                    }
                    break;
                }
                return true;
            }
        };
        Gdx.input.setInputProcessor(input);
    }
    public void prettyPrint() {
        System.out.println("Noise Tag: " + noise.getTag());
        System.out.println("Fractal Type: " + noise.getMode());
        System.out.println("Frequency: " + noise.getFrequency());
        System.out.println("Octaves: " + noise.getFractalOctaves());
        System.out.println("Seed: " + noise.getSeed());
        System.out.println("Fractal Spiral: " + noise.fractalSpiral);
        System.out.println("Divisions: " + divisions);
        System.out.println("Gradient Interpolator: " + interpolator.tag + " (index " + interpolatorIndex + ")");
        System.out.println("Hue: " + hue);
        System.out.println("Saturation: " + saturation);
        System.out.println("Gradient Variance: " + variance);
        System.out.println("Gradient Hardness: " + hard);
        System.out.println("Kumaraswamy a: " + a + ", b: " + b);
        System.out.println("Data for Copy/Paste: " + noiseIndex + "~" + noise.stringSerialize() + "~" + divisions + "~" + interpolator.tag + "~" + hue + "~" + variance + "~" + a + "~" + b + "~" + hard + "~"  + saturation + "~" + System.currentTimeMillis());
    }

    public void putMap() {
        if (Gdx.input.isKeyPressed(C))
            hue = (hue + 0.25f * (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()));
        if (Gdx.input.isKeyPressed(V))
            variance = Math.max(0.001f, variance + 0.25f * (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()));
        if (Gdx.input.isKeyPressed(A))
            hard = Math.min(Math.max(hard + 0.125f * (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()), 0f), 1f);
        if (Gdx.input.isKeyPressed(Z))
            saturation = Math.min(Math.max(saturation + 0.125f * (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()), 0f), 1f);
        if (Gdx.input.isKeyPressed(NUM_0))
            a = Math.max(0.001f, a + 0.25f * (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()));
        if (Gdx.input.isKeyPressed(NUM_1))
            b = Math.max(0.001f, b + 0.25f * (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()));
        renderer.begin(view.getCamera().combined, GL_POINTS);
        float bright, nf = noise.getFrequency(), counter = (paused ? startTime
                : TimeUtils.timeSinceMillis(startTime)) * 0x1p-10f / nf,
                c = counter * (1 + (divisions & 1));
        float hc = hue;
        if(hueCycle) hc = counter * 0x1p-8f;

        double aa = 1.0/a, bb = 1.0/b;

        for (int x = 0; x < width; x++) {
            float distX = x - (width - 1) * 0.5f; // x distance from center
            for (int y = 0; y < height; y++) {
                float distY = y - (height - 1) * 0.5f; // y distance from center
                // this is the angle to get from the center to our current point, multiplies by the number of times the
                // pattern needs to repeat (which is 3 + divisions), plus a slowly increasing value to make it rotate.
                float theta = TrigTools.atan2Turns(distY, distX) * (3 + divisions) + (c * 0x4p-8f);
                // not actually the length, but like it. It "should" use square root, but cube root looks better.
                float len = MathTools.cbrt(distX * distX + distY * distY) * 4f;
//                float len = (float) Math.sqrt(distX * distX + distY * distY);
                // this is used to expand each "pizza slice" of noise as it gets further from the center.
                float shrunk = len / (3f + divisions);
                // we need to subtract counter to make increasing time appear to "zoom in" forever. I don't know why.
                len = (len - counter) * 0x1p-8f;
                // can be ignored; when there are an even number of slices, this reverses every other slice.
                int flip = -((int) theta & 1 & divisions) | 1;
                // if the above found it needs to reverse a slice, it does so here.
                theta *= flip;
                float A, B, C, D; // these are used later, they get assigned the 4D position's x, y, z, w coordinates
                // the interpolator is used to adjust brightness, like ramps or curves in an image editor.
                bright = Math.min(Math.max(interpolator.apply(basicPrepare(
                        noise.getNoiseWithSeed(
                                // A and B are given the angle going around the center, and get split into sin and cos.
                                A = TrigTools.cosTurns(theta) * shrunk,
                                B = TrigTools.sinTurns(theta) * shrunk,
                                // C and D also get split, but are given the distance from the center going out.
                                C = TrigTools.cosTurns(len) * 32f,
                                D = TrigTools.sinTurns(len) * 32f,
                                // the noise seed allows us to make a different "random" pattern by changing the seed.
                                noise.getSeed())
                )), 0), 1);

                bright = (float)Math.pow(1.0 - Math.pow(1.0 - bright, bb), aa);

                float n = varianceNoise.getConfiguredNoise(A, B, C, D);
                renderer.color(
//                        BitConversion.reversedIntBitsToFloat(hsl2rgb(
                                fract((n / (hard * Math.abs(n) + (1f - hard))) * variance + hc),
                                TrigTools.sin(1 + bright * 1.375f) * saturation,
                                TrigTools.sin(bright * 1.5f),
                                1f
//                        ))
                );
//                renderer.color(colorFloats[(int) (bright * 255.99f)]);
                renderer.vertex(x, y, 0);
            }
        }
        if (Gdx.input.isKeyJustPressed(W)) {
            if (Gdx.files.isLocalStorageAvailable()) {
                for (int ctr = 0; ctr < 256; ctr++) {
                    int ct = ctr * (1 + (divisions & 1));
                    if(hueCycle) hc = ctr * 0x1p-8f;
                    else hc = hue;
                    Pixmap p = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    for (int x = 0; x < width; x++) {
                        float distX = x - (width - 1) * 0.5f;
                        for (int y = 0; y < height; y++) {
                            float distY = y - (height - 1) * 0.5f;
                            float theta = TrigTools.atan2Turns(distY, distX) * (3 + divisions) + (ct * 0x1p-8f);
//                float len = 0x1p-9f * (distX * distX + distY * distY);
                            float len = MathTools.cbrt(distX * distX + distY * distY) * 4f;
//                float len = (float) Math.sqrt(distX * distX + distY * distY);
                            float shrunk = len / (3f + divisions);
                            len = (len - ctr) * 0x1p-8f;
                            int flip = -((int) theta & 1 & divisions) | 1;
                            theta *= flip;
                            float A, B, C, D;
                            bright = Math.min(Math.max(interpolator.apply(basicPrepare(
                                    noise.getNoiseWithSeed(A = TrigTools.cosTurns(theta) * shrunk,
                                            B = TrigTools.sinTurns(theta) * shrunk, C = TrigTools.cosTurns(len) * 32f, D = TrigTools.sinTurns(len) * 32f, noise.getSeed())
                            )), 0), 1);

                            bright = (float)Math.pow(1.0 - Math.pow(1.0 - bright, bb), aa);
                            float n = varianceNoise.getConfiguredNoise(A, B, C, D);
                            p.setColor(
                                    hsl2rgb(//DescriptiveColor.toRGBA8888(DescriptiveColor.oklabByHCL(
                                            fract((n / (hard * Math.abs(n) + (1f - hard))) * variance + hc),
                                            TrigTools.sin(1 + bright * 1.375f) * saturation,
                                            TrigTools.sin(bright * 1.5f),
                                            1f))
//                            )
                            ;
                            p.drawPixel(x, y);
                        }
                    }
                    frames.add(p);
                }
//            float hue = (TimeUtils.millis() & 1023) * 0x1p-10f;
//            IntList g = ColorGradients.toRGBA8888(ColorGradients.appendGradientChain(new IntList(256), 256, Interpolation.smooth::apply,
//                    DescriptiveColor.oklabByHSL(0.05f + hue, 0.85f, 0.2f, 1f),
//                    DescriptiveColor.oklabByHSL(0.02f + hue, 0.95f, 0.4f, 1f),
//                    DescriptiveColor.oklabByHSL(0.10f + hue, 1f, 0.55f, 1f),
//                    DescriptiveColor.oklabByHSL(0.08f + hue, 0.7f, 0.8f, 1f)
//            ));
//            g.toArray(gif.palette.paletteArray);
//            colorList.toArray(gif.palette.paletteArray);
//                gif.palette.exact(colorList.items, colorList.size());

                Gdx.files.local("out/").mkdirs();
                String ser = noiseIndex + "~" + noise.stringSerialize() + "~" + divisions + "~" + interpolator.tag + "~" + hue + "~" + variance + "~" + a + "~" + b + "~" + hard + "~" + saturation + "~" + System.currentTimeMillis();
                prettyPrint();
                if(Gdx.app.getType() != Application.ApplicationType.WebGL)
                {
                    if(gif != null) {
                        gif.palette.analyzeReductive(frames);
                        gif.write(Gdx.files.local("out/gif/" + ser + ".gif"), frames, 30);
                    }
                    if(png != null) {
                        for(int i = 0; i < frames.size; i++){
                            png.write(Gdx.files.local("out/png/"+ser+"/frame_" + i + ".png"), frames.get(i));
                        }
                    }
                }
//                if(apng != null) {
//                    for (int i = 0; i < frames.size; i++) {
//                        Pixmap frame = frames.get(i);
//                        frame.setBlending(Pixmap.Blending.None);
//                        int h = frame.getHeight(), w = frame.getWidth(), halfH = h >> 1, halfW = w >> 1;
//                        for (int y = 0; y < h; y++) {
//                            for (int x = 0; x < w; x++) {
//                                int p = frame.getPixel(x, y);
//                                frame.drawPixel(x, y, (p & 0xFFFFFF00) | Math.min(Math.max(
//                                        (int) (300 - 0x2.8p7 / (halfH * halfH) * ((x - halfW) * (x - halfW) + (y - halfH) * (y - halfH))), 0), 255));
//                            }
//
//                        }
//                    }
//                    apng.write(Gdx.files.local("out/apng/" + ser + ".png"), frames, 16);
//                }
//                try {
//                    png.write(Gdx.files.local("out/png/" + ser + ".png"), frames.get(0));
//                } catch (IOException ignored) {
//                }
                for (int i = 0; i < frames.size; i++) {
                    frames.get(i).dispose();
                }
                frames.clear();
            } else {
                String ser = noiseIndex + "~" + noise.stringSerialize() + "~" + divisions + "~" + interpolator.tag + "~" + hue + "~" + variance + "~" + a + "~" + b + "~" + hard + "~" + saturation + "~" + System.currentTimeMillis();
                System.out.println(ser);
                clipboard.setContents(ser);
            }
        }
        renderer.end();
    }

    @Override
    public void render() {
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.graphics.setTitle(Gdx.graphics.getFramesPerSecond() + " FPS");
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        putMap();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        view.update(width, height, true);
        view.apply(true);
    }

//    public void updateColor(float h) {
//        hue = h;
//        colorList.clear();
//        ColorGradients.toRGBA8888(ColorGradients.appendGradientChain(colorList, 256, Interpolations.smooth,
//                DescriptiveColor.oklabByHSL(variance * 0.05f + hue, 0.85f, 0.2f, 1f),
//                DescriptiveColor.oklabByHSL(variance * 0.02f + hue, 0.95f, 0.4f, 1f),
//                DescriptiveColor.oklabByHSL(variance * 0.10f + hue, 1f, 0.55f, 1f),
//                DescriptiveColor.oklabByHSL(variance * 0.08f + hue, 0.7f, 0.8f, 1f)
//        ));
//        for (int i = 0; i < 256; i++) {
//            colorFloats[i] = BitConversion.reversedIntBitsToFloat(colorList.get(i) & -2);
//        }
//    }
}
