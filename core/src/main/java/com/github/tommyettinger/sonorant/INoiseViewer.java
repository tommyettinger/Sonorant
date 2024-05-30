package com.github.tommyettinger.sonorant;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
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
import static com.github.tommyettinger.digital.MathTools.floor;
import static com.github.tommyettinger.digital.MathTools.fract;

/**
 */
public class INoiseViewer extends ApplicationAdapter {

    public static final int width = 350, height = 350;
//    public static final int width = 512, height = 512;
//    public static final int width = 256, height = 256;
//    public static final int width = 64, height = 64;

    private final INoise[] noises = new INoise[]{new CyclicNoise(1234567890L, 3), new CyclicNoise(1234567890L, 4), new CyclicNoise(1234567890L, 5),
            new SorbetNoise(1234567890L, 5), new SorbetNoise(1234567890L, 4), new SorbetNoise(1234567890L, 3),
            new FlanNoise(1234567890L, 4), new TaffyNoise(1234567890L, 4), new FoamplexNoise(1234567890L),
            new FoamNoise(1234567890L), new HoneyNoise(1234567890L), new PerlinNoise(1234567890L), new SimplexNoise(1234567890L),
            new SnakeNoise(1234567890L), new BadgerNoise(1234567890L), new ValueNoise(1234567890L), new CellularNoise(1234567890L)
    };
    private int noiseIndex = 16;
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

    private final ObjectList<Vector2> centers =
//            ObjectList.with(new Vector2((width-1) * 0.5f, (height-1) * 0.5f));
            ObjectList.with(new Vector2((width-1) / 3f, (height-1) * 0.5f), new Vector2((width-1) * 2f / 3f, (height-1) * 0.5f));

    private ImmediateModeRenderer20 renderer;

    private Clipboard clipboard;

//    private IntList colorList = new IntList(256);
//    private float[] colorFloats = new float[256];

    private Viewport view;
    private long startTime;

    private AnimatedGif gif;
    private AnimatedPNG apng;
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

    public INoiseViewer(Clipboard clippy) {
        clipboard = clippy;
    }

    @Override
    public void create() {
        if(clipboard == null) clipboard = Gdx.app.getClipboard();

        noise.setWrapped(noises[noiseIndex]);
        noise.setFractalType(Noise.RIDGED_MULTI);

        if(Gdx.app.getType() != Application.ApplicationType.WebGL) {
            gif = new AnimatedGif();
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.DODGY);
            gif.setDitherStrength(0.5f);
            gif.palette = new QualityPalette();
//            gif.fastAnalysis = false;
//            png = new FastPNG();
//            png.setCompression(2);
            apng = new AnimatedPNG();
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
                    case EQUALS:
                        centers.add(new Vector2());
                        break;
                    case MINUS:
                        centers.pop();
                        break;
                    case SEMICOLON:
                        if(noise.wrapped instanceof CellularNoise){
                            CellularNoise cn = (CellularNoise) noise.wrapped;
                            cn.setNoiseType(cn.getNoiseType().ordinal() + (UIUtils.shift() ? -1 : 1));
                        }
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

        final int cenSize = centers.size();

        if(cenSize == 1) {
            centers.get(0).set((width - 1f) * 0.5f, (height - 1f) * 0.5f);
        }
        else {
            for (int i = 0; i < cenSize; i++) {
                float rot = (counter * 0x1p-8f + i) / (float) cenSize;
                centers.get(i).set(TrigTools.cosTurns(rot) * width * 0.35f + (width - 1f) * 0.5f,
                        TrigTools.sinTurns(rot) * height * 0.35f + (height - 1f) * 0.5f);
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float n = 0f, bSum = 0f;
                for (int cen = 0; cen < cenSize; cen++) {
                    int direction = 1 - ((~cenSize & cen & 1) << 1);

                    float distX = x - centers.get(cen).x; // x distance from center
                    float distY = y - centers.get(cen).y; // y distance from center
                    // this is the angle to get from the center to our current point, multiplies by the number of times the
                    // pattern needs to repeat (which is 3 + divisions), plus a slowly increasing value to make it rotate.
                    float theta = (TrigTools.atan2Turns(distY, distX) * (3 + divisions) + (c * 0x4p-8f)) * direction;
                    // not actually the length, but like it. It "should" use square root, but cube root looks better.
                    float len = MathTools.cbrt(distX * distX + distY * distY) * 4f;
//                float len = (float) Math.sqrt(distX * distX + distY * distY);
                    // this is used to expand each "pizza slice" of noise as it gets further from the center.
                    float shrunk = len / (3f + divisions);
                    // we need to subtract counter to make increasing time appear to "zoom in" forever. I don't know why.
                    len = (len - counter) * 0x1p-8f;
                    // can be ignored; when there are an even number of slices, this reverses every other slice.
                    int flip = -(MathUtils.floor(theta) & 1 & divisions) | 1;
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
                    bSum += (float)Math.pow(1.0 - Math.pow(1.0 - bright, bb), aa) - 0.4f;
                    n += varianceNoise.getConfiguredNoise(A, B, C, D);
                }
//                bright = (float) TrigTools.atanUncheckedTurns(bSum - cenSize * 0.5f) * 2f + 0.5f;
//                bright = bSum / cenSize;
                bright = screenistic(bSum * 1.9f / cenSize);

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

                    if(cenSize == 1) {
                        centers.get(0).set((width - 1f) * 0.5f, (height - 1f) * 0.5f);
                    }
                    else {
                        for (int i = 0; i < cenSize; i++) {
                            float rot = (ctr * 0x1p-8f + i) / (float) cenSize;
                            centers.get(i).set(TrigTools.cosTurns(rot) * width * 0.35f + (width - 1f) * 0.5f,
                                    TrigTools.sinTurns(rot) * height * 0.35f + (height - 1f) * 0.5f);
                        }
                    }

                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            float n = 0f, bSum = 0f;
                            for (int cen = 0; cen < cenSize; cen++) {
                                int direction = 1 - ((~cenSize & cen & 1) << 1);

                                float distX = x - centers.get(cen).x; // x distance from center
                                float distY = y - centers.get(cen).y; // y distance from center
                                // this is the angle to get from the center to our current point, multiplies by the number of times the
                                // pattern needs to repeat (which is 3 + divisions), plus a slowly increasing value to make it rotate.
                                float theta = (TrigTools.atan2Turns(distY, distX) * (3 + divisions) + (ct * 0x1p-8f)) * direction;
                                // not actually the length, but like it. It "should" use square root, but cube root looks better.
                                float len = MathTools.cbrt(distX * distX + distY * distY) * 4f;
//                float len = (float) Math.sqrt(distX * distX + distY * distY);
                                // this is used to expand each "pizza slice" of noise as it gets further from the center.
                                float shrunk = len / (3f + divisions);
                                // we need to subtract counter to make increasing time appear to "zoom in" forever. I don't know why.
                                len = (len - ctr) * 0x1p-8f;
                                // can be ignored; when there are an even number of slices, this reverses every other slice.
                                int flip = -(MathUtils.floor(theta) & 1 & divisions) | 1;
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
                                bSum += (float)Math.pow(1.0 - Math.pow(1.0 - bright, bb), aa) - 0.4f;
                                n += varianceNoise.getConfiguredNoise(A, B, C, D);
                            }
//                            bright = (float) TrigTools.atanUncheckedTurns(bSum - cenSize * 0.5f) * 2f + 0.5f;
//                            bright = bSum / cenSize;
                            bright = screenistic(bSum * 1.9f / cenSize);

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
                if(Gdx.app.getType() != Application.ApplicationType.WebGL) {
                    if (gif != null) {
                        gif.palette.analyze(frames);
                        gif.write(Gdx.files.local("out/gif/" + ser + ".gif"), frames, 24);
                    }
                    if (png != null) {
                        for (int i = 0; i < frames.size; i++) {
                            png.write(Gdx.files.local("out/png/" + ser + "/frame_" + i + ".png"), frames.get(i));
                        }
                    }

                    if (apng != null) {
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
                        apng.write(Gdx.files.local("out/apng/" + ser + ".png"), frames, 24);
                    }
                }
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

    /**
     *
     * @param n centered on 0, so the midpoint of the domain should already be subtracted; min and max should be -1 and 1
     * @return a float between 0 and 1
     */
    public static float screenistic(float n) {
        return 1.1045f / (1f + BitConversion.intBitsToFloat((int)((1 << 23) * (Math.max(-126.0f, -4.328085f * n) + 126.94269504f)))) - 0.05177f;
    }

    /**
     * Mixes two values (each between 0 and 1) to get another value that will be lower if both inputs are low, higher
     * if both inputs are high, or balanced if both are in the center.
     * @param a between 0 and 1
     * @param b between 0 and 1
     * @return between 0 and 1
     */
    public static float screenistic(float a, float b) {
        return 1.1045f / (1f + BitConversion.intBitsToFloat((int)((1 << 23) * (Math.max(-126.0f, -4.328085f * (a + b - 1f)) + 126.94269504f)))) - 0.05177f;
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
