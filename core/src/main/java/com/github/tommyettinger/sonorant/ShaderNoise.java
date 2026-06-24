package com.github.tommyettinger.sonorant;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.github.tommyettinger.anim8.AnimatedGif;
import com.github.tommyettinger.anim8.Dithered;
import com.github.tommyettinger.anim8.QualityPalette;
import com.github.tommyettinger.digital.Base;
import com.github.tommyettinger.digital.BitConversion;
import com.github.tommyettinger.digital.MathTools;
import com.github.tommyettinger.digital.TrigTools;

import static com.badlogic.gdx.Input.Keys.*;

/**
 * Credit for the shader adaptation goes to angelickite , a very helpful user on the libGDX Discord.
 * The Discord can be found at <a href="https://discord.gg/crTrDEK">this link</a>.
 */
public class ShaderNoise extends ApplicationAdapter {

    private SpriteBatch batch;
    private Texture pixel;
    private int shaderIndex = 0;
    private final ShaderProgram[] shaders = new ShaderProgram[7];
    private AnimatedGif gif;

    private long startTime;
    private float seed = 3.1337f;
    private float rMod = 0f;
    private float gMod = 0f;
    private float bMod = 0f;
    private float twist = 0.6f;
    private float speed = 1f;
    //	public static final int WIDTH = 1920, HEIGHT = 1080;
    public static final int WIDTH = 200, HEIGHT = 200;
    public static int width = WIDTH, height = HEIGHT;
    public static int FRAMES = 200;
    private final Array<Pixmap> frames = new Array<>(FRAMES);
    private Clipboard clipboard;

    public ShaderNoise(Clipboard clippy, long initialSeed) {
        clipboard = clippy;
        reseed(initialSeed);
    }

    @Override public void create () {
        if(clipboard == null) clipboard = Gdx.app.getClipboard();
        batch = new SpriteBatch();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.drawPixel(0, 0, 0xFFFFFFFF);
        pixel = new Texture(pixmap);
        startTime = TimeUtils.millis();

        if(Gdx.app.getType() != Application.ApplicationType.WebGL) {
            gif = new AnimatedGif();
            gif.setDitherAlgorithm(Dithered.DitherAlgorithm.MARTEN);
            gif.setDitherStrength(0.7f);
            gif.palette = new QualityPalette();
        }


        ShaderProgram.pedantic = true;
        ShaderProgram shaderInsanerAdj;
        shaders[0] = shaderInsanerAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("insaneradj_fragment.glsl"));
        if (!shaderInsanerAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderInsanerAdj:\n" + shaderInsanerAdj.getLog());
            Gdx.app.exit();
            return;
        }
        ShaderProgram shaderSahahAdj;
        shaders[1] = shaderSahahAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sahahadj_fragment.glsl"));
        if (!shaderSahahAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderSahahAdj:\n" + shaderSahahAdj.getLog());
            Gdx.app.exit();
            return;
        }
        ShaderProgram shaderSanarAdj;
        shaders[2] = shaderSanarAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sanaradj_fragment.glsl"));
        if (!shaderSanarAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderSanarAdj:\n" + shaderSanarAdj.getLog());
            Gdx.app.exit();
            return;
        }
        ShaderProgram shaderNorthAdj;
        shaders[3] = shaderNorthAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("northadj_fragment.glsl"));
        if (!shaderNorthAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderNorthAdj:\n" + shaderNorthAdj.getLog());
            Gdx.app.exit();
            return;
        }

        ShaderProgram shaderFoamAdj;
        shaders[4] = shaderFoamAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("foamadj_fragment.glsl"));
        if (!shaderFoamAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderFoamAdj:\n" + shaderFoamAdj.getLog());
            Gdx.app.exit();
            return;
        }

        ShaderProgram shaderGummiAdj;
        shaders[5] = shaderGummiAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("gummiadj_fragment.glsl"));
        if (!shaderGummiAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderGummiAdj:\n" + shaderGummiAdj.getLog());
            Gdx.app.exit();
            return;
        }

        ShaderProgram shaderMeltAdj;
        shaders[6] = shaderMeltAdj = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("meltadj_fragment.glsl"));
        if (!shaderMeltAdj.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderMeltAdj:\n" + shaderMeltAdj.getLog());
            Gdx.app.exit();
            return;
        }

        batch.setShader(shaders[shaderIndex]);

        // System.nanoTime() is supported by GWT 2.10.0 .
//		long state = System.nanoTime() + startTime;//-1234567890L;
        long state = BitConversion.doubleToLongBits(seed * 1234567890.0987654321);
        // Sarong's DiverRNG.randomize()
        reseed(state);
        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();

        if(clipboard.hasContents()) {
            loadClipboard();
        }

    }

    public void reseed(long state) {
        final long dim = MathTools.longFloor(seed);
        seed = MathTools.fract(((((state = (state ^ (state << 41 | state >>> 23) ^ (state << 17 | state >>> 47) ^ 0xD1B54A32D192ED03L) * 0xAEF17502108EF2D9L) ^ state >>> 43 ^ state >>> 31 ^ state >>> 23) * 0xDB4F0B9175AE2165L) >>> 36) * 0x1.1p-17f) + dim;
        System.out.println("Now using seed " + seed);
    }

    @Override public void resize (int width, int height) {
        ShaderNoise.width = width;
        ShaderNoise.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override public void render () {
        ScreenUtils.clear(0f, 0f, 0f, 0f);
        if((Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.F11)) && UIUtils.alt()) {
            if(Gdx.graphics.isFullscreen()) {
                Gdx.graphics.setWindowedMode(WIDTH, HEIGHT);
            } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
        } else if(Gdx.input.isKeyJustPressed(Input.Keys.S)){ // seed
            seed += UIUtils.shift() ? 0.0009765625f : -0.0009765625f;
        } else if(Gdx.input.isKeyJustPressed(Input.Keys.D)){ // divisions
            seed += UIUtils.shift() ? 1f : -1f;
        } else if(Gdx.input.isKeyJustPressed(SLASH)){ // seed, but jumps out of alignment (or back into it)
            seed += UIUtils.shift() ? 0.005f : -0.005f;
        } else if(Gdx.input.isKeyJustPressed(NUM_1) || Gdx.input.isKeyJustPressed(NUMPAD_1)){ // set seed to 1
            reseed(1L);
        } else if(Gdx.input.isKeyJustPressed(Input.Keys.O)){ // start Over
            startTime = TimeUtils.millis();
        } else if(Gdx.input.isKeyJustPressed(Input.Keys.F)){ // FPS log
            System.out.println("FPS = " + Gdx.graphics.getFramesPerSecond());
        } else if(Gdx.input.isKeyJustPressed(Input.Keys.Q) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){ // quit
            Gdx.app.exit();
        }
        else if (Gdx.input.isKeyPressed(T)) // twist
            twist = Math.min(Math.max(0.0f, twist + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.01f : -0.01f)), 1f);
        else if(Gdx.input.isKeyPressed(R)) // rate
            speed = Math.min(Math.max(0.0f, speed + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.5f : -0.5f)), 5f);
        else if (Gdx.input.isKeyPressed(H)) // hue rotation
            rMod = MathTools.fract(rMod + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.125f : -0.125f));
        else if (Gdx.input.isKeyPressed(G)) // color fidget
            gMod = Math.min(Math.max(0.0f, gMod + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.03125f : -0.03125f)), 1f);
        else if (Gdx.input.isKeyPressed(B)) // color fidget
            bMod = Math.min(Math.max(0.0f, bMod + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.03125f : -0.03125f)), 1f);
        else if(Gdx.input.isKeyJustPressed(A)) // alternate shader
            batch.setShader(shaders[shaderIndex = (shaderIndex + (UIUtils.shift() ? 1 : shaders.length - 1)) % shaders.length]);
        else if(Gdx.input.isKeyJustPressed(V)) { // ctrl-v
            if(clipboard.hasContents()){
                loadClipboard();
            }
        }
        else if(Gdx.input.isKeyJustPressed(C)) { // ctrl-c
            System.out.println(seed + "_" + rMod + "_" + gMod + "_" + bMod + "_" + twist + "_" + ((speed - 1) * 600) + "_" + shaderIndex);
            clipboard.setContents(seed + "_" + rMod + "_" + gMod + "_" + bMod + "_" + twist + "_" + ((speed - 1) * 600) + "_" + shaderIndex);
            if (Gdx.app.getType() != Application.ApplicationType.WebGL && gif != null) {
                frames.clear();
                FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGB888, width<<1, height<<1, false);
                for (int i = 0; i < FRAMES; i++) {
                    fb.begin();
                    batch.begin();
                    batch.getShader().setUniformf("u_seed", seed);
                    batch.getShader().setUniformf("u_time", i * (TrigTools.PI2 / FRAMES) + 5.25f);
                    batch.getShader().setUniformf("u_resolution", width << 1, height << 1);
                    batch.getShader().setUniformf("u_adj",
                        rMod,
                        gMod,
                        bMod,
                        twist);
                    batch.setPackedColor(Color.WHITE_FLOAT_BITS);
                    batch.draw(pixel, 0f, 0f, width << 1, height << 1);
                    batch.end();
                    Pixmap tp = Pixmap.createFromFrameBuffer(0, 0, width<<1, height<<1), np = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    np.drawPixmap(tp, 0, 0, width<<1, height<<1, 0, 0, width, height);
                    frames.add(np);
                    tp.dispose();
                    fb.end();
                }
                gif.palette.analyzeHueWise(frames, 80);
                gif.write(Gdx.files.local("out/gif/" +
                    (seed + "_" + rMod + "_" + gMod + "_" + bMod + "_" + twist + "_" + 0 + "_" + shaderIndex)
                    + ".gif"), frames, 20);
            }
        }

        final float fTime = TimeUtils.timeSinceMillis(startTime) * 0x1p-11f * speed;
        batch.begin();
        batch.getShader().setUniformf("u_seed", seed);
        batch.getShader().setUniformf("u_time", fTime);
        batch.getShader().setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.getShader().setUniformf("u_adj",
            rMod,
            gMod,
            bMod,
            twist);
        batch.setPackedColor(Color.WHITE_FLOAT_BITS);
        batch.draw(pixel, 0, 0, width, height);
        batch.end();
    }

    private void loadClipboard() {
        String s = clipboard.getContents();
        int gap;
        seed = Base.BASE10.readFloat(s, 0, gap = s.indexOf('_'));
        rMod = Base.BASE10.readFloat(s, gap+1, gap = s.indexOf('_', gap+1));
        gMod = Base.BASE10.readFloat(s, gap+1, gap = s.indexOf('_', gap+1));
        bMod = Base.BASE10.readFloat(s, gap+1, gap = s.indexOf('_', gap+1));
        twist = Base.BASE10.readFloat(s, gap+1, gap = s.indexOf('_', gap+1));
        speed = Base.BASE10.readFloat(s, gap+1, gap = s.indexOf('_', gap+1)) / 600f + 1f;
        batch.setShader(shaders[shaderIndex = (Base.BASE10.readInt(s, gap+1, s.length()) % shaders.length + shaders.length) % shaders.length]);
    }
}
