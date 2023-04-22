package com.github.tommyettinger;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.tommyettinger.anim8.Dithered;
import com.github.tommyettinger.anim8.FastGif;
import com.github.tommyettinger.anim8.PaletteReducer;
import com.github.tommyettinger.digital.*;
import com.github.tommyettinger.ds.IntList;
import com.github.tommyettinger.random.LineWobble;
import com.github.yellowstonegames.core.ColorGradients;
import com.github.yellowstonegames.core.DescriptiveColor;
import com.github.yellowstonegames.core.Interpolations;
import com.github.yellowstonegames.grid.FlawedPointHash;
import com.github.yellowstonegames.grid.IPointHash;
import com.github.yellowstonegames.grid.IntPointHash;
import com.github.yellowstonegames.grid.Noise;

import static com.badlogic.gdx.Input.Keys.*;
import static com.badlogic.gdx.graphics.GL20.GL_POINTS;
import static com.github.tommyettinger.digital.TrigTools.cosTurns;
import static com.github.tommyettinger.digital.TrigTools.sinTurns;

/**
 */
public class Sonorant extends ApplicationAdapter {

    private final Noise noise = new Noise(24, 0.0625f);
    private final IntPointHash iph = new IntPointHash();
    private final FlawedPointHash.CubeHash cube = new FlawedPointHash.CubeHash(24, 64);
    private final IPointHash[] pointHashes = new IPointHash[] {iph, cube};
    private int hashIndex = 0;
    private int octaves = 3;
    private int divisions = 2;
    private float freq = 0.125f;
    private float baseContribution = 0.125f;
    private boolean inverse;
    private boolean paused;
    private boolean alternate = false;
    private ImmediateModeRenderer20 renderer;

//    private static final int width = 400, height = 400;
//    private static final int width = 512, height = 512;
    public static final int width = 256, height = 256;
    private IntList colorList = new IntList(256);
    private float[] colorFloats = new float[256];
    private int kernelSize = 32;
    private final float[][] kernel = new float[kernelSize][kernelSize];

    private float[][] pigment = new float[width][height];
    private float[][] previousPigment = new float[width][height];

    private Viewport view;
    private long startTime;
    private int steps;

    private FastGif gif;
    private final Array<Pixmap> frames = new Array<>(256);

    public static float basicPrepare(float n)
    {
        return n * 0.5f + 0.5f;
    }
    public static float clampingPrepare(float n)
    {
        return Math.min(Math.max(n * 0.5f + 0.5f, 0f), 1f);
    }

    public void buildKernel() {
        int oldFractalType = noise.getFractalType();
//        float oldFrequency = noise.getFrequency();
        noise.setFractalType(Noise.RIDGED_MULTI);
//        noise.setFrequency(256f * oldFrequency / kernelSize);
        float angleAdj = Hasher.randomize3Float(~noise.getSeed());
        float hs = (kernelSize-1)*0.5f;
        for (int x = 0; x < kernelSize; x++) {
            float distX = x - hs;
            for (int y = 0; y < kernelSize; y++) {
                float distY = y - hs;
                float len = (float) Math.sqrt(distX * distX + distY * distY);
                float window = cosTurns(len * (0.25f / hs));
                if(window <= 0.0f) {
                    kernel[x][y] = 0f;
                    continue;
                }
                window *= window; // makes a Hann window

                float theta = (TrigTools.atan2Turns(distY, distX)+angleAdj) * (3 + divisions);
                float shrunk = len / (3f + divisions);
                len *= 0x1p-8f;
                int flip = -((int)theta & 1 & divisions) | 1;
                theta *= flip;
                kernel[x][y] = window * noise.getConfiguredNoise(
                        cosTurns(theta) * shrunk,
                        sinTurns(theta) * shrunk,
                        cosTurns(len) * 32f, sinTurns(len) * 32f);
            }
        }
        noise.setFractalType(oldFractalType);
//        noise.setFrequency(oldFrequency);
    }

    public float kernelSum(final float[][] from, int x, int y) {
        float sum = 0f;
        final int w = from.length, h = from[0].length;
//                startX = Math.max(0, x - 32), startY = Math.max(0, y - 32),
//                startI = startX - (x-32),
//                startJ = startY - (y-32);
        int hs = kernelSize>>>1;
        for (int i = 0, xx = x - hs; i < kernelSize; i++, xx++) {
            for (int j = 0, yy = y - hs; j < kernelSize; j++, yy++) {
                sum += kernel[i][j] * from[xx&255][yy&255];
            }
        }
        return sum;
    }

    public void randomizeColor(long seed) {
        float hue = Hasher.randomize3Float(seed);
        colorList.clear();
        ColorGradients.toRGBA8888(ColorGradients.appendGradientChain(colorList, 256, Interpolations.smooth,
                DescriptiveColor.oklabByHSL(0.05f + hue, 0.85f, 0.2f, 1f),
                DescriptiveColor.oklabByHSL(0.02f + hue, 0.95f, 0.4f, 1f),
                DescriptiveColor.oklabByHSL(0.10f + hue, 1f, 0.55f, 1f),
                DescriptiveColor.oklabByHSL(0.08f + hue, 0.7f, 0.8f, 1f)
        ));
        for (int i = 0; i < 256; i++) {
            colorFloats[i] = BitConversion.reversedIntBitsToFloat(colorList.get(i) & -2);
        }

    }

    @Override
    public void create() {
        noise.setNoiseType(Noise.FOAM_FRACTAL);
        noise.setPointHash(pointHashes[hashIndex]);

        buildKernel();

        gif = new FastGif();
        gif.setDitherAlgorithm(Dithered.DitherAlgorithm.BLUE_NOISE);
        gif.setDitherStrength(0.2f);
        // Ugh, this is ugly.
        gif.palette = new PaletteReducer(
                new int[] {
                        0x000000FF, 0x010101FF, 0x020202FF, 0x030303FF, 0x040404FF, 0x050505FF, 0x060606FF, 0x070707FF,
                        0x080808FF, 0x090909FF, 0x0A0A0AFF, 0x0B0B0BFF, 0x0C0C0CFF, 0x0D0D0DFF, 0x0E0E0EFF, 0x0F0F0FFF,
                        0x101010FF, 0x111111FF, 0x121212FF, 0x131313FF, 0x141414FF, 0x151515FF, 0x161616FF, 0x171717FF,
                        0x181818FF, 0x191919FF, 0x1A1A1AFF, 0x1B1B1BFF, 0x1C1C1CFF, 0x1D1D1DFF, 0x1E1E1EFF, 0x1F1F1FFF,
                        0x202020FF, 0x212121FF, 0x222222FF, 0x232323FF, 0x242424FF, 0x252525FF, 0x262626FF, 0x272727FF,
                        0x282828FF, 0x292929FF, 0x2A2A2AFF, 0x2B2B2BFF, 0x2C2C2CFF, 0x2D2D2DFF, 0x2E2E2EFF, 0x2F2F2FFF,
                        0x303030FF, 0x313131FF, 0x323232FF, 0x333333FF, 0x343434FF, 0x353535FF, 0x363636FF, 0x373737FF,
                        0x383838FF, 0x393939FF, 0x3A3A3AFF, 0x3B3B3BFF, 0x3C3C3CFF, 0x3D3D3DFF, 0x3E3E3EFF, 0x3F3F3FFF,
                        0x404040FF, 0x414141FF, 0x424242FF, 0x434343FF, 0x444444FF, 0x454545FF, 0x464646FF, 0x474747FF,
                        0x484848FF, 0x494949FF, 0x4A4A4AFF, 0x4B4B4BFF, 0x4C4C4CFF, 0x4D4D4DFF, 0x4E4E4EFF, 0x4F4F4FFF,
                        0x505050FF, 0x515151FF, 0x525252FF, 0x535353FF, 0x545454FF, 0x555555FF, 0x565656FF, 0x575757FF,
                        0x585858FF, 0x595959FF, 0x5A5A5AFF, 0x5B5B5BFF, 0x5C5C5CFF, 0x5D5D5DFF, 0x5E5E5EFF, 0x5F5F5FFF,
                        0x606060FF, 0x616161FF, 0x626262FF, 0x636363FF, 0x646464FF, 0x656565FF, 0x666666FF, 0x676767FF,
                        0x686868FF, 0x696969FF, 0x6A6A6AFF, 0x6B6B6BFF, 0x6C6C6CFF, 0x6D6D6DFF, 0x6E6E6EFF, 0x6F6F6FFF,
                        0x707070FF, 0x717171FF, 0x727272FF, 0x737373FF, 0x747474FF, 0x757575FF, 0x767676FF, 0x777777FF,
                        0x787878FF, 0x797979FF, 0x7A7A7AFF, 0x7B7B7BFF, 0x7C7C7CFF, 0x7D7D7DFF, 0x7E7E7EFF, 0x7F7F7FFF,
                        0x808080FF, 0x818181FF, 0x828282FF, 0x838383FF, 0x848484FF, 0x858585FF, 0x868686FF, 0x878787FF,
                        0x888888FF, 0x898989FF, 0x8A8A8AFF, 0x8B8B8BFF, 0x8C8C8CFF, 0x8D8D8DFF, 0x8E8E8EFF, 0x8F8F8FFF,
                        0x909090FF, 0x919191FF, 0x929292FF, 0x939393FF, 0x949494FF, 0x959595FF, 0x969696FF, 0x979797FF,
                        0x989898FF, 0x999999FF, 0x9A9A9AFF, 0x9B9B9BFF, 0x9C9C9CFF, 0x9D9D9DFF, 0x9E9E9EFF, 0x9F9F9FFF,
                        0xA0A0A0FF, 0xA1A1A1FF, 0xA2A2A2FF, 0xA3A3A3FF, 0xA4A4A4FF, 0xA5A5A5FF, 0xA6A6A6FF, 0xA7A7A7FF,
                        0xA8A8A8FF, 0xA9A9A9FF, 0xAAAAAAFF, 0xABABABFF, 0xACACACFF, 0xADADADFF, 0xAEAEAEFF, 0xAFAFAFFF,
                        0xB0B0B0FF, 0xB1B1B1FF, 0xB2B2B2FF, 0xB3B3B3FF, 0xB4B4B4FF, 0xB5B5B5FF, 0xB6B6B6FF, 0xB7B7B7FF,
                        0xB8B8B8FF, 0xB9B9B9FF, 0xBABABAFF, 0xBBBBBBFF, 0xBCBCBCFF, 0xBDBDBDFF, 0xBEBEBEFF, 0xBFBFBFFF,
                        0xC0C0C0FF, 0xC1C1C1FF, 0xC2C2C2FF, 0xC3C3C3FF, 0xC4C4C4FF, 0xC5C5C5FF, 0xC6C6C6FF, 0xC7C7C7FF,
                        0xC8C8C8FF, 0xC9C9C9FF, 0xCACACAFF, 0xCBCBCBFF, 0xCCCCCCFF, 0xCDCDCDFF, 0xCECECEFF, 0xCFCFCFFF,
                        0xD0D0D0FF, 0xD1D1D1FF, 0xD2D2D2FF, 0xD3D3D3FF, 0xD4D4D4FF, 0xD5D5D5FF, 0xD6D6D6FF, 0xD7D7D7FF,
                        0xD8D8D8FF, 0xD9D9D9FF, 0xDADADAFF, 0xDBDBDBFF, 0xDCDCDCFF, 0xDDDDDDFF, 0xDEDEDEFF, 0xDFDFDFFF,
                        0xE0E0E0FF, 0xE1E1E1FF, 0xE2E2E2FF, 0xE3E3E3FF, 0xE4E4E4FF, 0xE5E5E5FF, 0xE6E6E6FF, 0xE7E7E7FF,
                        0xE8E8E8FF, 0xE9E9E9FF, 0xEAEAEAFF, 0xEBEBEBFF, 0xECECECFF, 0xEDEDEDFF, 0xEEEEEEFF, 0xEFEFEFFF,
                        0xF0F0F0FF, 0xF1F1F1FF, 0xF2F2F2FF, 0xF3F3F3FF, 0xF4F4F4FF, 0xF5F5F5FF, 0xF6F6F6FF, 0xF7F7F7FF,
                        0xF8F8F8FF, 0xF9F9F9FF, 0xFAFAFAFF, 0xFBFBFBFF, 0xFCFCFCFF, 0xFDFDFDFF, 0xFEFEFEFF, 0xFFFFFFFF,
                });

        randomizeColor(TimeUtils.millis());
        colorList.toArray(gif.palette.paletteArray);

        startTime = TimeUtils.millis();
        renderer = new ImmediateModeRenderer20(width * height * 2, false, true, 0);
        view = new ScreenViewport();

        InputAdapter input = new InputAdapter() {
            @Override
            public boolean keyUp(int keycode) {
                int s;
                boolean change = false;
                switch (keycode) {
                    case SPACE:
                        paused = !paused;
                        startTime = TimeUtils.millis();
                        break;
                    case C:
                        randomizeColor(TimeUtils.millis());
                        break;
                    case E: //earlier seed
                        s = (int) (noise.getSeed() - 1);
                        noise.setSeed(s);
                        cube.setState(s);
                        System.out.println("Using seed " + s);
                        change = true;
                        break;
                    case S: //seed after
                        s = (int) (noise.getSeed() + 1);
                        noise.setSeed(s);
                        cube.setState(s);
                        System.out.println("Using seed " + s);
                        change = true;
                        break;
                    case N: // noise type
                        noise.setNoiseType((noise.getNoiseType() + (UIUtils.shift() ? 17 : 1)) % 18);
                        change = true;
                        break;
                    case B: // blur
                        noise.setSharpness(noise.getSharpness() + (UIUtils.shift() ? 0.05f : -0.05f));
                        change = true;
                        break;
                    case D: // divisions
                        divisions = (divisions + (UIUtils.shift() ? 9 : 1)) % 10;
                        change = true;
                        break;
                    case F: // frequency
                        noise.setFrequency(freq *= (UIUtils.shift() ? 1.25f : 0.8f));
                        change = true;
                        break;
                    case R: // fRactal type
                        noise.setFractalType((noise.getFractalType() + (UIUtils.shift() ? 3 : 1)) & 3);
                        change = true;
                        break;
                    case G: // Glitch!
                        noise.setPointHash(pointHashes[hashIndex ^= 1]);
                        change = true;
                        break;
                    case H: // higher octaves
                        noise.setFractalOctaves((octaves = octaves + 1 & 7) + 1);
                        change = true;
                        break;
                    case L: // lower octaves
                        noise.setFractalOctaves((octaves = octaves + 7 & 7) + 1);
                        change = true;
                        break;
                    case I: // inverse mode
                        if (inverse = !inverse) {
                            noise.setFractalLacunarity(0.5f);
                            noise.setFractalGain(2f);
                        } else {
                            noise.setFractalLacunarity(2f);
                            noise.setFractalGain(0.5f);
                        }
                        change = true;
                        break;
                    case K: // sKip
                        steps += 1000;
                        break;
                    case W: // whirl, like a spiral
                        noise.setFractalSpiral(!noise.isFractalSpiral());
                        change = true;
                        break;
                    case Z:
                        baseContribution = MathTools.isZero(baseContribution) ? 0.125f : 0f;
                        break;
                    case P:
                        System.out.println("Noise in use: " + noise);
                        break;
                    case A:
                        alternate = !alternate;
                        break;
                    case Q:
                    case ESCAPE: {
                        Gdx.app.exit();
                    }
                    break;
                }
                if(change){
                    buildKernel();
                    ArrayTools.fill(previousPigment, 0f);
                }
                return true;
            }
        };
        Gdx.input.setInputProcessor(input);
    }

    public void putMap() {
        if (Gdx.input.isKeyPressed(M))
            noise.setMutation(noise.getMutation() + (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()));
        renderer.begin(view.getCamera().combined, GL_POINTS);
        float bright, nf = noise.getFrequency(), c = (paused ? steps : ++steps) * 0.25f / nf;

        if(!MathTools.isZero(baseContribution)) {
            for (int x = 0; x < width; x++) {
                float xx = x / (float) width;
                for (int y = 0; y < height; y++) {
                    float yy = y / (float) height;
                    previousPigment[x][y] += noise.getConfiguredNoise(
                            cosTurns(xx) * 32f, sinTurns(xx) * 32f, cosTurns(yy) * 32f, sinTurns(yy) * 32f, c) * baseContribution;
                }
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pigment[x][y] = Math.min(Math.max(previousPigment[x][y] +
                        LineWobble.wobble(noise.getSeed(), kernelSum(previousPigment, x, y) * 0x1p-4f) * 0.25f, 0f), 1f);
                bright = (alternate && (steps & 1) == 1 ? Math.min(Math.max(previousPigment[x][y], 0), 1) : pigment[x][y]) * 255;
                renderer.color(colorFloats[(int)bright]);

                renderer.vertex(x, y, 0);
            }
        }
        float[][] old = previousPigment;
        previousPigment = pigment;
        pigment = old;
        if (Gdx.input.isKeyJustPressed(W)) {
            for (int ct = 0; ct < 256; ct++) {
                int w = 256, h = 256;
                float cf = ct * 0x1p-4f / nf;
                Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        float color = (previousPigment[x][y] = Math.min(Math.max(previousPigment[x][y] + noise.getConfiguredNoise(x, y, cf) * 0x1p-6f, 0f), 0.25f)) * 4f;
                        p.setColor(color, color, color, 1f);
                        p.drawPixel(x, y);
                    }
                }
                frames.add(p);
            }
            colorList.toArray(gif.palette.paletteArray);


            Gdx.files.local("out/").mkdirs();
            String ser = noise.serializeToString() + "_" + TimeUtils.millis();
            System.out.println(ser);
            gif.write(Gdx.files.local("out/" + ser + ".gif"), frames, 16);
            for (int i = 0; i < frames.size; i++) {
                frames.get(i).dispose();
            }
            frames.clear();
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
}
