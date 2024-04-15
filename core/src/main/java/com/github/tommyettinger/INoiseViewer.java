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
                0x00000000, 0x0E0D0FFF, 0x101112FF, 0x151515FF, 0x171615FF, 0x181819FF, 0x191819FF, 0x1C1A1AFF,
                0x1B1B1CFF, 0x1C1C1DFF, 0x1C1C1DFF, 0x1D1D1EFF, 0x1E1E1EFF, 0x202020FF, 0x222122FF, 0x2B2929FF,
                0x353735FF, 0x383839FF, 0x3A3D3CFF, 0x3C3D3CFF, 0x3C3D3BFF, 0x3D3C3DFF, 0x3C3E3EFF, 0x3D3F3FFF,
                0x3F3F3EFF, 0x3E403FFF, 0x403F41FF, 0x3F403FFF, 0x41403EFF, 0x3F4242FF, 0x434545FF, 0x444544FF,
                0x484745FF, 0x464848FF, 0x47494AFF, 0x484A49FF, 0x494B4AFF, 0x4B4C4BFF, 0x4B4C4BFF, 0x4B4C4EFF,
                0x4E4E4CFF, 0x535555FF, 0x545657FF, 0x888A8BFF, 0x939290FF, 0x909394FF, 0x939394FF, 0x919493FF,
                0x979898FF, 0x9A9797FF, 0x969A9AFF, 0x989A98FF, 0x9A9998FF, 0x9B9C9DFF, 0x9E9E9CFF, 0x9D9DA0FF,
                0x9E9E9BFF, 0x9C9F9FFF, 0x9EA2A3FF, 0xA2A2A3FF, 0xA0A3A3FF, 0xA1A3A2FF, 0xA2A2A5FF, 0xA1A4A4FF,
                0xA2A4A2FF, 0xA2A5A4FF, 0xA2A6A5FF, 0xA7AAA9FF, 0xAAADADFF, 0x35131FFF, 0xD31568FF, 0x65474EFF,
                0x410A1FFF, 0x420A20FF, 0xC63562FF, 0x804754FF, 0x632C39FF, 0xCA1E58FF, 0x5A1D2CFF, 0xE82160FF,
                0xCB6076FF, 0x3A0C19FF, 0xCD4163FF, 0x290D13FF, 0xCB4763FF, 0xCE3F5EFF, 0xD33158FF, 0xD6516AFF,
                0x4E111FFF, 0x420918FF, 0xDC405FFF, 0xC75667FF, 0x674A4CFF, 0xD23E59FF, 0xCE2D4FFF, 0x523B3DFF,
                0x78373FFF, 0x6A343AFF, 0xDE2B51FF, 0xDF264EFF, 0xE22E51FF, 0x3F171CFF, 0xD54A5BFF, 0xC4163CFF,
                0xE31042FF, 0xD23C4FFF, 0xCD5861FF, 0xC5525CFF, 0x853E43FF, 0x8C4247FF, 0x822C34FF, 0x852E36FF,
                0xDF2745FF, 0x763136FF, 0x451319FF, 0xC03F4AFF, 0xD71A3BFF, 0xCF293FFF, 0xC93E4AFF, 0x6A3738FF,
                0xC53E45FF, 0xDA464CFF, 0x7A4D4CFF, 0x421416FF, 0x5C2F2EFF, 0xE24B4FFF, 0xD3172DFF, 0xC23037FF,
                0x812729FF, 0x804340FF, 0xCE3136FF, 0xCB1E2CFF, 0xDD2631FF, 0xDB1D2CFF, 0x8C3B38FF, 0xD9252EFF,
                0xEB5450FF, 0x7E4541FF, 0xD63134FF, 0xD54844FF, 0x80413CFF, 0xD4504AFF, 0x6C2D29FF, 0x753D38FF,
                0x704E4AFF, 0xCA3B35FF, 0xED3E37FF, 0xE22F2BFF, 0x8A4841FF, 0x9C3F37FF, 0x722E28FF, 0xCE5044FF,
                0xF65A4AFF, 0xD64234FF, 0x7E3E34FF, 0x7F4137FF, 0x723E34FF, 0x8B4A3EFF, 0x8D3E30FF, 0xE83914FF,
                0x874A3CFF, 0x79493EFF, 0x28150FFF, 0x6A463CFF, 0x753929FF, 0xD15A39FF, 0x784738FF, 0xD8491CFF,
                0xA0401FFF, 0x894028FF, 0x894126FF, 0x944A2DFF, 0xFF8453FF, 0xFAA786FF, 0x864328FF, 0x7F4932FF,
                0x69331CFF, 0xE9A080FF, 0x7A452EFF, 0x8F4826FF, 0xF4A581FF, 0x7A3F22FF, 0xFF9053FF, 0x7C5037FF,
                0x70503CFF, 0xDF7F41FF, 0x884D26FF, 0x654C3DFF, 0xEC945AFF, 0xE78741FF, 0xD9915FFF, 0xCB9D7CFF,
                0xE18E51FF, 0x6C4E38FF, 0xD59E76FF, 0xE78534FF, 0xF9AD74FF, 0x744926FF, 0xF58924FF, 0xE68631FF,
                0xE6B083FF, 0xE39144FF, 0xF39437FF, 0xE0A875FF, 0xD48E4AFF, 0xF79938FF, 0xD09762FF, 0xE9B27CFF,
                0xF5B879FF, 0xF8B267FF, 0xE79B47FF, 0xE29D50FF, 0xFEB25BFF, 0xE5A75FFF, 0xEFAD5FFF, 0xF1AC5BFF,
                0x4E3D29FF, 0xEFBB79FF, 0xFDB456FF, 0xF1BA74FF, 0xFEBB64FF, 0xE2A859FF, 0xF9C276FF, 0xEAA331FF,
                0xEEAB40FF, 0xEBB96DFF, 0xE1A339FF, 0xEDCA90FF, 0xE2B158FF, 0xC7A057FF, 0xD8A543FF, 0xDFB86FFF,
                0xE5B755FF, 0xE6C37CFF, 0xDCBB74FF, 0x453C28FF, 0xD0B266FF, 0xCEAB50FF, 0xE7BB36FF, 0xDEBD58FF,
                0x524D2CFF, 0x666344FF, 0x9C9878FF, 0x403C10FF, 0x4A4935FF, 0x494928FF, 0x919264FF, 0x48482BFF,
                0xFBFDBEFF, 0x93947BFF, 0x616230FF, 0x3E3E0BFF, 0x989B28FF, 0x8B8F24FF, 0x3D3F28FF, 0x454821FF,
                0x43462EFF, 0x858F13FF, 0x8B953BFF, 0x939D56FF, 0x89981AFF, 0x3A3E27FF, 0x7D8D0DFF, 0x434929FF,
                0x8DA22DFF, 0xACB68FFF, 0x869A46FF, 0x383D28FF, 0x7E9726FF, 0x38421EFF, 0x85A328FF, 0x90B034FF,
                0x88A446FF, 0x373F25FF, 0xA3BB70FF, 0x799532FF, 0x82A712FF, 0x85AB1EFF, 0xA0AA8EFF, 0x85AE1FFF,
                0x3A4525FF, 0x83A442FF, 0x7E9F40FF, 0x88A94DFF, 0x424A36FF, 0x98BD59FF, 0x36441FFF, 0x7BA434FF,
                0x7BAA25FF, 0x3F4A30FF, 0x789F3BFF, 0x95A67FFF, 0x7CA04AFF, 0x7FAD37FF, 0x84B33EFF, 0x7AAC2CFF,
                0x343D29FF, 0x8CB752FF, 0x749745FF, 0x7AAD2CFF, 0x74A036FF, 0x74A825FF, 0x9BB57EFF, 0x7CAA3DFF,
                0x4C5642FF, 0x3C5023FF, 0x70A035FF, 0x3C5026FF, 0x76AA3AFF, 0x79AD3DFF, 0x88A56EFF, 0x58881AFF,
                0x6AA61CFF, 0x76A644FF, 0x36402EFF, 0x82AE58FF, 0x80AF50FF, 0x6CA629FF, 0x73A73EFF, 0x69AC0FFF,
                0x34491FFF, 0x70994AFF, 0x9BA890FF, 0x749E4FFF, 0x77A949FF, 0x416817FF, 0x5F9030FF, 0x6BAD22FF,
                0x75A24EFF, 0x729D4EFF, 0x6AA431FF, 0x78A94EFF, 0x546A44FF, 0x7DAD57FF, 0x69AB2AFF, 0x70B62DFF,
                0x79B449FF, 0x6DA937FF, 0x66AF18FF, 0x6FA344FF, 0x72A34BFF, 0x74A54FFF, 0x384B29FF, 0x3B4F2CFF,
                0x3E6221FF, 0x72AC46FF, 0x7AA35EFF, 0x27381BFF, 0x5DA41BFF, 0x5C9D25FF, 0x75A754FF, 0x5FAD18FF,
                0x6CC220FF, 0x3F4E36FF, 0x67AA35FF, 0x6AB72DFF, 0x6EA14CFF, 0x9DAD93FF, 0x6FAE43FF, 0x77A35BFF,
                0x6AA93EFF, 0x5D9A32FF, 0x6BAC3FFF, 0x66B52CFF, 0x2C411EFF, 0x67A93DFF, 0x3D6228FF, 0x5FA035FF,
                0x6CB33FFF, 0x4F9911FF, 0x64A240FF, 0x2E4323FF, 0x3C552FFF, 0x68B43AFF, 0x69A448FF, 0x354E28FF,
                0x69B43FFF, 0x6CB544FF, 0x73B054FF, 0x58A52DFF, 0x376121FF, 0x5DAA34FF, 0x54A226FF, 0x6BB149FF,
                0x5CA736FF, 0x5A9A3CFF, 0x52A623FF, 0x50AE16FF, 0x365429FF, 0x5BA837FF, 0x35681BFF, 0x374432FF,
                0x69BC46FF, 0x34432FFF, 0x40523AFF, 0x3F5C35FF, 0x3C5035FF, 0x61B542FF, 0x598C47FF, 0x58B534FF,
                0x306919FF, 0x62A14DFF, 0x6CAB56FF, 0x50A730FF, 0x356523FF, 0x42A612FF, 0x5EB63FFF, 0x366B24FF,
                0x3C4C37FF, 0x5FAF45FF, 0x63B349FF, 0x589E41FF, 0x609F4DFF, 0x50A733FF, 0x2A4921FF, 0x64B44DFF,
                0x5EAE47FF, 0x72A664FF, 0x66A454FF, 0x45A826FF, 0x5EAB4BFF, 0x2C5222FF, 0x49AD2FFF, 0x3B5C34FF,
                0x57B73FFF, 0x56AE41FF, 0x70A963FF, 0x68AD59FF, 0x84B579FF, 0x2F4729FF, 0x4DB634FF, 0x64B951FF,
                0x6CA760FF, 0x8EB585FF, 0x4CC82FFF, 0x699E5EFF, 0x2B4426FF, 0x3C6A33FF, 0x3A6233FF, 0x60A554FF,
                0x6CB260FF, 0x5CB54EFF, 0x4A6345FF, 0x54C143FF, 0x47BA38FF, 0x3B5238FF, 0x6AA362FF, 0x51A348FF,
                0x5FA857FF, 0x58A450FF, 0x56AC4DFF, 0x4A9E42FF, 0x64AC5DFF, 0x47BA3EFF, 0x62B25BFF, 0x53AC4CFF,
                0x4CA846FF, 0x55A04FFF, 0x88BE83FF, 0x62A85EFF, 0x20C722FF, 0x44AA41FF, 0x49B547FF, 0x384737FF,
                0x5CAA5AFF, 0x4AB34AFF, 0x4FAB4FFF, 0x284928FF, 0x49B14CFF, 0x56A257FF, 0x35AE3EFF, 0x88B288FF,
                0x4CAD50FF, 0x59BE5CFF, 0x3C5F3CFF, 0x85C084FF, 0x385838FF, 0x34B63FFF, 0x59B05CFF, 0x4EAE53FF,
                0x10AE2EFF, 0x49A84FFF, 0x43A94BFF, 0x2A682FFF, 0x5DAC62FF, 0x295B2DFF, 0x88A188FF, 0x3DA549FF,
                0x4AA052FF, 0x84C088FF, 0x45BF55FF, 0x97B498FF, 0x36663AFF, 0x44AE52FF, 0x2EA543FF, 0x254F29FF,
                0x55B960FF, 0x27412AFF, 0x58B063FF, 0x31A446FF, 0x2F6637FF, 0x316639FF, 0x48C860FF, 0x59B868FF,
                0x52BE66FF, 0x32AF50FF, 0x38533BFF, 0x44C963FF, 0x3FB65BFF, 0x2D4631FF, 0x4BB062FF, 0x335B3BFF,
                0x324535FF, 0x7BA482FF, 0x89BA92FF, 0x017131FF, 0x2EC660FF, 0x2E4934FF, 0x2E4B34FF, 0x4FAC67FF,
                0x3CA75BFF, 0x236235FF, 0x3AB05EFF, 0x86B690FF, 0x8EC89BFF, 0x34533CFF, 0x76B585FF, 0x60C57CFF,
                0x216638FF, 0x23B35AFF, 0x4AA464FF, 0x62A272FF, 0x17B357FF, 0x314736FF, 0x29AE5AFF, 0x235332FF,
                0x2C5437FF, 0x3FB266FF, 0x37A55EFF, 0x385540FF, 0x1BA054FF, 0x42C471FF, 0x2A4B34FF, 0x1D492CFF,
                0x4BBA72FF, 0x49C577FF, 0x40B96EFF, 0x375F44FF, 0x8DBE9BFF, 0x7AB88DFF, 0x31B066FF, 0x244830FF,
                0x365C43FF, 0x204F32FF, 0x1B5332FF, 0x324538FF, 0x2E553CFF, 0x7DA389FF, 0x0A4326FF, 0x18643BFF,
                0x739F82FF, 0x33A165FF, 0x3CC37AFF, 0x1DB96DFF, 0x195133FF, 0x28B56EFF, 0x17492FFF, 0x126C41FF,
                0x37AB6EFF, 0x36C47BFF, 0x314639FF, 0x2FC47AFF, 0x294234FF, 0x195336FF, 0x04AB67FF, 0x71CA97FF,
                0x3CB275FF, 0x175537FF, 0x04683FFF, 0x1A5A3AFF, 0x19B46FFF, 0x3AAF74FF, 0x41B178FF, 0x89B299FF,
                0x355A45FF, 0x52BE87FF, 0x68C895FF, 0x849F8FFF, 0x2E694BFF, 0x134A31FF, 0x2A553FFF, 0x236044FF,
                0x71C399FF, 0x8AB69EFF, 0x336049FF, 0x225E43FF, 0x085639FF, 0x2C4E3DFF, 0x2D6048FF, 0x41C188FF,
                0x8EC7A9FF, 0x124B34FF, 0x234E3AFF, 0x6EC49BFF, 0x234334FF, 0x254E3BFF, 0x70C79DFF, 0x276047FF,
                0x33C68BFF, 0x2A5B45FF, 0x2B513FFF, 0x36B380FF, 0x37B281FF, 0x81AB97FF, 0x1E4C39FF, 0x1B4A38FF,
                0x3EC591FF, 0x3B5F4FFF, 0x04422EFF, 0x175E44FF, 0x205742FF, 0x254C3CFF, 0x254E3EFF, 0x67CEA6FF,
                0x1D4F3DFF, 0x73C7A6FF, 0x5AA78AFF, 0x6EBA9CFF, 0x84A899FF, 0x295646FF, 0x63BC9CFF, 0x0F6950FF,
                0x94CEB8FF, 0x1B4738FF, 0x53B795FF, 0x73C4A6FF, 0x98BAACFF, 0x144A39FF, 0x8CB8A7FF, 0x2D5849FF,
                0x2B5A4BFF, 0x1B6751FF, 0x366656FF, 0x6AC0A3FF, 0x44C09BFF, 0x27BB93FF, 0x6CB49CFF, 0x72C5AAFF,
                0x25604FFF, 0x72B59FFF, 0x13493BFF, 0x205848FF, 0x52B597FF, 0x7AB7A3FF, 0x9FCEBEFF, 0x73BDA5FF,
                0x85A89CFF, 0x5FC0A3FF, 0x50C3A3FF, 0x276655FF, 0x70D2B4FF, 0x285347FF, 0x27594BFF, 0x39C19FFF,
                0x6BBEA6FF, 0x095A49FF, 0x6FB7A2FF, 0x81BBAAFF, 0x82B2A4FF, 0x67B9A3FF, 0x63CAAFFF, 0x1E5547FF,
                0x56C4A9FF, 0x415F57FF, 0x085042FF, 0x1B594BFF, 0x51C5A9FF, 0x71BCA8FF, 0x83ADA1FF, 0x395850FF,
                0x7CC2B1FF, 0x6ABEAAFF, 0x67CBB4FF, 0x65BDA8FF, 0x20473EFF, 0x1E564AFF, 0x4EC7AEFF, 0x83B2A7FF,
                0x82D1BFFF, 0x43B49DFF, 0x82D1BFFF, 0x1F5E51FF, 0x4EC6AEFF, 0x304A44FF, 0x6AC9B6FF, 0x2DC2ABFF,
                0x2C4D47FF, 0x26544CFF, 0x18554BFF, 0x73CABAFF, 0x2C524BFF, 0x7ABBAFFF, 0x53BBABFF, 0x27564FFF,
                0x1F6157FF, 0x58CDBBFF, 0x2FC5B1FF, 0x64B5A7FF, 0x235C54FF, 0x79C7BCFF, 0x106057FF, 0x1FC0B1FF,
                0x729E97FF, 0x70AFA6FF, 0x355551FF, 0x5DCCC0FF, 0x254541FF, 0x3D5854FF, 0x315F5AFF, 0x25605AFF,
                0x10BCAEFF, 0x225D57FF, 0x13534DFF, 0x264744FF, 0x5CC3BAFF, 0x124945FF, 0x234B48FF, 0x2E504DFF,
                0x84B8B3FF, 0x6CBFB8FF, 0x3EBAB1FF, 0x2D4D4AFF, 0x33504DFF, 0x59BBB4FF, 0x54B4ADFF, 0x5DBDB6FF,
                0x78B4AFFF, 0x236461FF, 0x095A56FF, 0x1AC8C1FF, 0x64B7B2FF, 0x71C0BBFF, 0x4ABEB9FF, 0x225350FF,
                0x215452FF, 0x185A59FF, 0x1C5654FF, 0x38615FFF, 0x0C5958FF, 0x34C0BDFF, 0x66C8C6FF, 0x3FC6C5FF,
                0x8CB7B6FF, 0x114A4AFF, 0x659E9EFF, 0x7FB2B1FF, 0x1D5151FF, 0x7CB4B4FF, 0x73AAAAFF, 0x1F5253FF,
                0x63B2B3FF, 0x70A5A7FF, 0x74ADAFFF, 0x82A6A7FF, 0x48CBD1FF, 0x81CACDFF, 0x8CB8BAFF, 0x79A1A3FF,
                0x35BFC6FF, 0x1B4D50FF, 0x2F5354FF, 0x60C8CEFF, 0x6BBBC0FF, 0x215659FF, 0x24585BFF, 0x254D50FF,
                0x6DB5BAFF, 0x61B3B9FF, 0x345659FF, 0x2A5559FF, 0x609FA5FF, 0x67A2A8FF, 0x36BECBFF, 0x5EA9B1FF,
                0x6BA8AFFF, 0x6AC3CEFF, 0x7FBCC4FF, 0x155962FF, 0x79ABB1FF, 0x75B4BDFF, 0x4AB7C5FF, 0x1D5A62FF,
                0x68A5AEFF, 0x79AFB7FF, 0x47CADFFF, 0x365257FF, 0x709BA2FF, 0x7DC5D3FF, 0x6CB0BDFF, 0x36555BFF,
                0x89ACB3FF, 0x63BACDFF, 0x6797A2FF, 0x66ACBBFF, 0x1EB5D2FF, 0x64ADBEFF, 0x619DACFF, 0x065A6AFF,
                0x31555FFF, 0x145969FF, 0x7DB2C0FF, 0x21B2D5FF, 0x5DABC1FF, 0x3DBEDFFF, 0x4A9EB6FF, 0x55A6BDFF,
                0x2FBFE5FF, 0x42C1E3FF, 0x63B8D2FF, 0x699AA9FF, 0x7CB7CBFF, 0x365863FF, 0x6EABBFFF, 0x34C7F3FF,
                0x1E6175FF, 0x7B9EABFF, 0x7CB3C8FF, 0x49A0BFFF, 0x34A7D0FF, 0x79ACC0FF, 0x7B9BA7FF, 0x67BADAFF,
                0x145E77FF, 0x6DB1CCFF, 0x7DABBDFF, 0x2B4A57FF, 0x2BB5E7FF, 0x73A4B9FF, 0x5BBAE2FF, 0x365F71FF,
                0x53AED6FF, 0x6EAAC5FF, 0x76B4D1FF, 0x75A2B8FF, 0x6097B1FF, 0x87B0C4FF, 0x7AA0B2FF, 0x6CB1D2FF,
                0x6E93A5FF, 0x7ABEDEFF, 0x79A4BAFF, 0x7AB7D6FF, 0x61BBEAFF, 0x689DB9FF, 0x285269FF, 0x2B5267FF,
                0x8FA9B9FF, 0x65A9D0FF, 0x789BB0FF, 0x619CC1FF, 0x70A7CAFF, 0x7AAAC9FF, 0x67AEDCFF, 0x6BB1DFFF,
                0x55B1ECFF, 0x59A7DCFF, 0x80A8C4FF, 0x051722FF, 0x86A2B9FF, 0x80A1BCFF, 0x90ADC7FF, 0x7FA8CCFF,
                0x849BAFFF, 0x6DACE9FF, 0x8AA3BDFF, 0x6BA2DEFF, 0x7EA7D4FF, 0x8193A7FF, 0x93A3BAFF, 0x304C72FF,
                0x0D1C31FF, 0x95A6C0FF, 0x849FCBFF, 0x8599B8FF, 0x7B9CD3FF, 0x3D5071FF, 0x8199C4FF, 0x2D4271FF,
                0x899DC6FF, 0x283C7BFF, 0x0C1226FF, 0x3C4665FF, 0x8F9ECBFF, 0x909CBFFF, 0x818DB6FF, 0x929BBFFF,
                0x96A1D4FF, 0x99A1CEFF, 0x959BC1FF, 0x060313FF, 0x111129FF, 0x191740FF, 0x999BD2FF, 0x8B8CB2FF,
                0x2C284EFF, 0x9391B5FF, 0x9691C6FF, 0xA9A4D6FF, 0x9C98BBFF, 0xA39BD0FF, 0x160A2FFF, 0xA297D0FF,
                0x37295CFF, 0x382A5BFF, 0xA798D1FF, 0x211534FF, 0x150D21FF, 0x1D122CFF, 0x2C2537FF, 0x241B32FF,
                0x261D33FF, 0x2A1840FF, 0x1E112BFF, 0x17111EFF, 0x0F0217FF, 0x1F122AFF, 0x20132AFF, 0x1C062BFF,
                0x322240FF, 0x251631FF, 0xA487BDFF, 0x170920FF, 0xB39EC4FF, 0x1C042AFF, 0x1F062EFF, 0x211828FF,
                0x271930FF, 0x291035FF, 0x2B0D39FF, 0x351F3FFF, 0x32183CFF, 0x510B68FF, 0x1F1124FF, 0x281B2BFF,
                0x250A2CFF, 0x17071BFF, 0x1A001FFF, 0x36183CFF, 0x4D1458FF, 0x3F1947FF, 0x341C38FF, 0x491F50FF,
                0x290F2EFF, 0x36183AFF, 0x201322FF, 0x230926FF, 0x522057FF, 0x1F0422FF, 0x2A092DFF, 0x29142AFF,
                0x3C1E3DFF, 0x291C29FF, 0x271627FF, 0x230B24FF, 0x200C20FF, 0x230E23FF, 0x290729FF, 0x54174FFF,
                0x451D41FF, 0x4D1B48FF, 0x3E1C3AFF, 0x533E4FFF, 0x3E1739FF, 0x2E142BFF, 0x3C1637FF, 0x2B1328FF,
                0x4D1747FF, 0x2E172AFF, 0x3B1E36FF, 0x55194BFF, 0x210C1DFF, 0x3F1336FF, 0x3D0F34FF, 0x22101EFF,
                0x251621FF, 0x512046FF, 0x49163EFF, 0x5E224FFF, 0x271121FF, 0x491F3CFF, 0x291A24FF, 0x280A1FFF,
                0x470C36FF, 0x36192CFF, 0x271520FF, 0x2E0E23FF, 0x24151EFF, 0x421631FF, 0x330923FF, 0x331425FF,
                0x42042CFF, 0x311023FF, 0x3C152AFF, 0x311A25FF, 0x3C1228FF, 0x3A1327FF, 0x331524FF, 0x45172EFF,
                0x4F1532FF, 0x43122AFF, 0x321A24FF, 0x45162CFF, 0x450827FF, 0x2A0D19FF, 0x34101FFF, 0x3F1928FF,
                0x28121AFF, 0x331B23FF, 0x401024FF, 0x1E0410FF, 0x431828FF, 0x50152CFF, 0x280715FF, 0x290615FF,
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
