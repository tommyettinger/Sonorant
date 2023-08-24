package com.github.tommyettinger;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.tommyettinger.anim8.AnimatedGif;
import com.github.tommyettinger.anim8.AnimatedPNG;
import com.github.tommyettinger.anim8.Dithered;
import com.github.tommyettinger.anim8.PaletteReducer;
import com.github.tommyettinger.digital.*;
import com.github.tommyettinger.ds.IntList;
import com.github.tommyettinger.ds.ObjectList;
import com.github.yellowstonegames.core.ColorGradients;
import com.github.yellowstonegames.core.DescriptiveColor;
import com.github.yellowstonegames.grid.*;

import java.io.IOException;

import static com.badlogic.gdx.Input.Keys.*;
import static com.badlogic.gdx.graphics.GL20.GL_POINTS;

/**
 */
public class NoiseViewer extends ApplicationAdapter {

    private final Noise noise = new Noise(322420472, 0.0625f);
    private final Noise varianceNoise = new Noise(-1, 0.025f, Noise.FOAM);
    private final IntPointHash iph = new IntPointHash();
    private final FlawedPointHash.CubeHash cube = new FlawedPointHash.CubeHash(1, 64);
    private final FlawedPointHash.FlowerHash flower = new FlawedPointHash.FlowerHash(1);
    private final IPointHash[] pointHashes = new IPointHash[] {iph, cube, flower};
    private int hashIndex = 0;
    private final ObjectList<Interpolations.Interpolator> interpolators = new ObjectList<>(Interpolations.getInterpolatorArray());
    private int interpolatorIndex = 58;
    private Interpolations.Interpolator interpolator = interpolators.get(interpolatorIndex);
    private float hue = 0;
    private float variance = 1f;
    private int divisions = 2;
    private int octaves = 0;
    private float freq = 0.125f;
    private boolean paused;
    private ImmediateModeRenderer20 renderer;

    private Clipboard clipboard;
//    public static final int width = 400, height = 400;
    public static final int width = 256, height = 256;
//    public static final int width = 64, height = 64;

//    private IntList colorList = new IntList(256);
//    private float[] colorFloats = new float[256];

    private Viewport view;
    private long startTime;

    private AnimatedGif gif;
    private AnimatedPNG apng;
    private PixmapIO.PNG png;
    private final Array<Pixmap> frames = new Array<>(256);

    public static float basicPrepare(float n)
    {
        return n * 0.5f + 0.5f;
    }

    public NoiseViewer(Clipboard clippy) {
        clipboard = clippy;
    }

    @Override
    public void create() {
        if(clipboard == null) clipboard = Gdx.app.getClipboard();

        noise.setNoiseType(Noise.VALUE_FRACTAL);
        noise.setFractalType(Noise.RIDGED_MULTI);
        noise.setPointHash(pointHashes[hashIndex]);

        apng = new AnimatedPNG();
        png = new PixmapIO.PNG();
        png.setCompression(2);
        gif = new AnimatedGif();
        gif.setDitherAlgorithm(Dithered.DitherAlgorithm.LOAF);
        gif.setDitherStrength(0.3f);
        gif.palette = new PaletteReducer();

        updateColor(Hasher.randomize3Float(TimeUtils.millis()));
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
                    case C: // color
                        updateColor(Hasher.randomize3Float(TimeUtils.millis()));
                        break;
                    case V: // color hue variance
                        variance *= (UIUtils.shift() ? 0.8f : 1.25f);
//                        varianceNoise.setFrequency(varianceNoise.getFrequency() + (UIUtils.shift() ? -0.25f : 0.25f));
                        updateColor(hue);
                        break;
                    case E: //earlier seed
                        s = (int) (ls = noise.getSeed() - 1);
                        noise.setSeed(s);
                        cube.setState(ls);
                        flower.setState(ls);
                        System.out.println("Using seed " + s);
                        break;
                    case S: //seed after
                        s = (int) (ls = noise.getSeed() + 1);
                        noise.setSeed(s);
                        cube.setState(ls);
                        flower.setState(ls);
                        System.out.println("Using seed " + s);
                        break;
                    case N: // noise type
                        noise.setNoiseType((noise.getNoiseType() + (UIUtils.shift() ? 16 : 2)) % 18);
                        break;
                    case ENTER:
                    case D: //dimension
                        divisions = (divisions + (UIUtils.shift() ? 9 : 1)) % 10;
                        break;
                    case B: //blur
                        noise.setSharpness(noise.getSharpness() + (UIUtils.shift() ? 0.05f : -0.05f));
                        break;
                    case F: // frequency
                        noise.setFrequency(freq *= (UIUtils.shift() ? 1.25f : 0.8f));
                        break;
                    case R: // fRactal type
                        noise.setFractalType((noise.getFractalType() + (UIUtils.shift() ? 3 : 1)) & 3);
                        break;
                    case G: // "glitch" (only affects Cubic Noise type)
                        noise.setPointHash(pointHashes[
                                hashIndex = (UIUtils.shift() ? hashIndex + pointHashes.length - 1 :hashIndex + 1) % pointHashes.length]);
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
                    case P: { // paste
                        if (clipboard.hasContents()) {
                            String paste = clipboard.getContents();
                            int last = paste.lastIndexOf('`');
                            if (last >= 1) {
                                noise.deserializeFromString(paste);
                                Base base = Base.BASE10;
                                divisions = base.readInt(paste, last + 2, last = paste.indexOf('_', last + 2));
                                interpolatorIndex = interpolators.indexOf(interpolator =
                                        Interpolations.get(paste.substring(last + 1, last = paste.indexOf('_', last + 1))));
                                hue = base.readFloat(paste, last + 1, last = paste.indexOf('_', last + 1));
                                variance = base.readFloat(paste, last + 1, last = paste.indexOf('_', last + 1));
                                updateColor(hue);
                                prettyPrint();
                            }
                        } else
                            System.out.println("Clipboard is empty!");
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
        noise.prettyPrint();
        System.out.println("Divisions: " + divisions);
        System.out.println("Gradient Interpolator: " + interpolator.tag + " (index " + interpolatorIndex + ")");
        System.out.println("Hue: " + hue);
        System.out.println("Gradient Variance: " + variance);
        System.out.println("Data for Copy/Paste: " + noise.serializeToString() + "_" + divisions + "_" + interpolator.tag + "_" + hue + "_" + variance + "_" + System.currentTimeMillis());
    }

    public void putMap() {
        if (Gdx.input.isKeyPressed(M))
            noise.setMutation(noise.getMutation() + (UIUtils.shift() ? -Gdx.graphics.getDeltaTime() : Gdx.graphics.getDeltaTime()));
        renderer.begin(view.getCamera().combined, GL_POINTS);
        float bright, nf = noise.getFrequency(), c = (paused ? startTime
                : TimeUtils.timeSinceMillis(startTime)) * 0x1p-10f / nf;
        for (int x = 0; x < width; x++) {
            float distX = x - (width - 1) * 0.5f;
            for (int y = 0; y < height; y++) {
                float distY = y - (height - 1) * 0.5f;
                float theta = TrigTools.atan2Turns(distY, distX) * (3 + divisions) + (c * 0x4p-8f);
                float len = (float) Math.sqrt(distX * distX + distY * distY);
                float shrunk = len / (3f + divisions);
                len = (len - c) * 0x1p-8f;
                int flip = -((int)theta & 1 & divisions) | 1;
                theta *= flip;
                float A, B, C, D;
                bright = Math.min(Math.max(interpolator.apply(basicPrepare(
                                noise.getConfiguredNoise(A = TrigTools.cosTurns(theta) * shrunk,
                                        B = TrigTools.sinTurns(theta) * shrunk, C = TrigTools.cosTurns(len) * 32f, D = TrigTools.sinTurns(len) * 32f)
                        )), 0), 1);
                renderer.color(DescriptiveColor.oklabIntToFloat(DescriptiveColor.oklabByHSL(
                        varianceNoise.getConfiguredNoise(A, B, C, D) * variance + hue,
                        TrigTools.sin(1 + bright * 1.375f),
                        TrigTools.sin(bright * 1.5f),
                        1f)));
//                renderer.color(colorFloats[(int) (bright * 255.99f)]);
                renderer.vertex(x, y, 0);
            }
        }
        if (Gdx.input.isKeyJustPressed(W)) {
            if (Gdx.files.isLocalStorageAvailable()) {
                for (int ct = 0; ct < 256; ct++) {
                    Pixmap p = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    for (int x = 0; x < width; x++) {
                        float distX = x - (width - 1) * 0.5f;
                        for (int y = 0; y < height; y++) {
                            float distY = y - (height - 1) * 0.5f;
                            float theta = TrigTools.atan2Turns(distY, distX) * (3 + divisions) + (ct * 0x4p-8f);
                            float len = (float) Math.sqrt(distX * distX + distY * distY);
                            float shrunk = len / (3f + divisions);
                            len = (len - ct) * 0x1p-8f;
                            int flip = -((int) theta & 1 & divisions) | 1;
                            theta *= flip;
                            float A, B, C, D;
                            bright = Math.min(Math.max(interpolator.apply(basicPrepare(
                                    noise.getConfiguredNoise(A = TrigTools.cosTurns(theta) * shrunk,
                                            B = TrigTools.sinTurns(theta) * shrunk, C = TrigTools.cosTurns(len) * 32f, D = TrigTools.sinTurns(len) * 32f)
                            )), 0), 1);

                            p.setColor(DescriptiveColor.toRGBA8888(DescriptiveColor.oklabByHSL(
                                    varianceNoise.getConfiguredNoise(A, B, C, D) * variance + hue,
                                    TrigTools.sin(1 + bright * 1.375f),
                                    TrigTools.sin(bright * 1.5f),
                                    1f)));
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
                String ser = noise.serializeToString() + "_" + divisions + "_" + interpolator.tag + "_" + hue + "_" + variance + "_" + System.currentTimeMillis();
                prettyPrint();
                gif.write(Gdx.files.local("out/gif/" + ser + ".gif"), frames, 16);
                if(apng != null) {
                    for (int i = 0; i < frames.size; i++) {
                        Pixmap frame = frames.get(i);
                        frame.setBlending(Pixmap.Blending.None);
                        int h = frame.getHeight(), w = frame.getWidth(), halfH = h >> 1, halfW = w >> 1;
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                int p = frame.getPixel(x, y);
                                frame.drawPixel(x, y, (p & 0xFFFFFF00) | Math.min(Math.max(
                                        (int) (300 - 0x2.8p7 / (halfH * halfH) * ((x - halfW) * (x - halfW) + (y - halfH) * (y - halfH))), 0), 255));
                            }

                        }
                    }
                    apng.write(Gdx.files.local("out/apng/" + ser + ".png"), frames, 16);
                }
                try {
                    png.write(Gdx.files.local("out/png/" + ser + ".png"), frames.get(0));
                } catch (IOException ignored) {
                }
                for (int i = 0; i < frames.size; i++) {
                    frames.get(i).dispose();
                }
                frames.clear();
            } else {
                String ser = noise.serializeToString() + "_" + divisions + "_" + interpolator.tag + "_" + hue + "_" + variance + "_" + System.currentTimeMillis();
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

    public void updateColor(float h) {
        hue = h;
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
    }
}
