package com.github.tommyettinger.sonorant;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Clipboard;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.github.tommyettinger.digital.Base;
import com.github.tommyettinger.digital.BitConversion;
import com.github.tommyettinger.digital.MathTools;
import com.github.tommyettinger.digital.TrigTools;
import com.github.tommyettinger.lwjgl3.StartupHelper;
import com.github.tommyettinger.random.LineWobble;
import games.rednblack.miniaudio.MAAudioBuffer;
import games.rednblack.miniaudio.MASound;
import games.rednblack.miniaudio.MAVisualizerListener;
import games.rednblack.miniaudio.MiniAudio;
import games.rednblack.miniaudio.config.MAContextConfiguration;
import games.rednblack.miniaudio.config.MAEngineConfiguration;
import games.rednblack.miniaudio.config.MAiOSSessionCategory;
import games.rednblack.miniaudio.effect.MACompressorNode;
import games.rednblack.miniaudio.mix.MASplitter;
import games.rednblack.miniaudio.mix.MAVisualizerNode;

import static com.badlogic.gdx.Input.Keys.*;
import static com.github.tommyettinger.sonorant.InputShaderNoise.height;
import static com.github.tommyettinger.sonorant.InputShaderNoise.width;

/**
 * Credit for the shader adaptation goes to angelickite , a very helpful user on the libGDX Discord.
 * The Discord can be found at <a href="https://discord.gg/crTrDEK">this link</a>.
 */
public class MusicVisualizerNoise extends ApplicationAdapter {

    private SpriteBatch batch;
    private Texture pixel;
    private ShaderProgram shader;

    private long startTime;
    private float seed = 3.1337f;
    private float rMod = 0f;
    private float gMod = 0f;
    private float bMod = 0f;
    private float twist = 0.6f;
    //	public static final int WIDTH = 1920, HEIGHT = 1080;
    public static final int WIDTH = 600, HEIGHT = 600;
    public static int width = WIDTH, height = HEIGHT;
    private Clipboard clipboard;

    private MiniAudio miniAudio;
    private MASound backgroundMusic;
    private final float[] data = new float[16];
    private final float[] visualizerData = new float[8192];
    private volatile int visualizerSamples;
    private volatile int visualizerChannels;


    public MusicVisualizerNoise(Clipboard clippy, long initialSeed) {
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

        MAContextConfiguration contextConfiguration = new MAContextConfiguration();
        contextConfiguration.iOSSessionCategory = MAiOSSessionCategory.AMBIENT;
        MAEngineConfiguration engineConfiguration = new MAEngineConfiguration();
        miniAudio = new MiniAudio(contextConfiguration, null);
//        listDevices();
        miniAudio.initEngine(engineConfiguration);

        MAVisualizerNode maVisualizerNode = new MAVisualizerNode(miniAudio);
        maVisualizerNode.setListener(new MAVisualizerListener() {
            @Override
            public void onVisualizerData(float[] pcmData, int len, int channels) {
                for (int i = 0; i < len; i++) {
                    data[(i << 4) / len] += pcmData[i];
                }
                float averagingFactor = 16f / len;
                for (int i = 0; i < 16; i++) {
                    data[i] *= averagingFactor;
                }
                visualizerChannels = channels;
                visualizerSamples = len;
            }
        });


        FileHandle file = Gdx.files.classpath("Komiku - Helice Awesome Dance Adventure !! - 26 Road 4 Fight.ogg");
        byte[] data = file.readBytes(); //any supported bytes (e.g. mp3, wav, etc.)
        MAAudioBuffer decodedBuffer = miniAudio.decodeBytes(data, 2);
        backgroundMusic = miniAudio.createSound(decodedBuffer);
        backgroundMusic.setLinkedAudioBuffer(decodedBuffer); //Important!
        MACompressorNode compressorNode = new MACompressorNode(miniAudio);
        compressorNode.setRatio(4f);
        compressorNode.setThreshold(-50);
        compressorNode.setRelease(200);

        compressorNode.attachToThisNode(backgroundMusic, 0, 0);

        maVisualizerNode.attachToThisNode(compressorNode, 0);

        miniAudio.attachToEngineOutput(maVisualizerNode, 0);
        backgroundMusic.loop();


        ShaderProgram.pedantic = true;
        shader = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sanaviz_fragment.glsl"));
        if (!shader.isCompiled()) {
            Gdx.app.error("Shader", "error compiling shaderStandard:\n" + shader.getLog());
            Gdx.app.exit();
            return;
        }
        batch.setShader(shader);

        // System.nanoTime() is supported by GWT 2.10.0 .
//		long state = System.nanoTime() + startTime;//-1234567890L;
        long state = BitConversion.doubleToLongBits(seed * 1234567890.0987654321);
        // Sarong's DiverRNG.randomize()
        reseed(state);
        // changes the start time (in milliseconds) by up to 65535 ms, based on state (which uses nanoseconds).
//        startTime -= (state ^ state >>> 11) & 0xFFFFL;
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
        MusicVisualizerNoise.width = width;
        MusicVisualizerNoise.height = height;
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
            twist = Math.min(Math.max(0.0f, twist + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.001f : -0.001f)), 1f);
        else if(Gdx.input.isKeyJustPressed(V)) { // ctrl-v
            if(clipboard.hasContents()){
                loadClipboard();
            }
        }
        else if(Gdx.input.isKeyJustPressed(C)) { // ctrl-c
            System.out.println(seed + "_" + rMod + "_" + gMod + "_" + bMod + "_" + twist + "_" + width + "_" + height);
            clipboard.setContents(seed + "_" + rMod + "_" + gMod + "_" + bMod + "_" + twist + "_" + width + "_" + height);
        }

        final float fTime = TimeUtils.timeSinceMillis(startTime) * TrigTools.PI2 * 0x1p-13f;
        batch.begin();
        shader.setUniformf("u_seed", seed);
        shader.setUniformf("u_time", fTime);
        shader.setUniformMatrix4fv("u_music", data, 0, 16);
        shader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shader.setUniformf("u_adj",
            LineWobble.bicubicWobble(1234, fTime * 0.031f) * 0.5f + 0.5f,
            LineWobble.bicubicWobble(6789, fTime * 0.033f) * 0.5f + 0.5f,
            LineWobble.bicubicWobble(-987, fTime * 0.035f) * 0.5f + 0.5f,
            twist);
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
    }

    @Override
    public void dispose() {
        //Always dispose everything! First all sounds and then the engine
        backgroundMusic.dispose();
        miniAudio.dispose();
    }

    @Override
    public void pause() {
        miniAudio.stopEngine();
    }

    @Override
    public void resume() {
        miniAudio.startEngine();
    }

    public static void main(String[] args) {
        // Needed for macOS support, but also Windows with non-ASCII usernames.
        if (StartupHelper.startNewJvmIfRequired()) return;

//        new Lwjgl3Application(new ApngShaderNoise(null, 1L), getDefaultConfiguration());
//        new Lwjgl3Application(new ShaderNoise(new Lwjgl3Clipboard(), System.currentTimeMillis()), getDefaultConfiguration());
//        new Lwjgl3Application(new EndlessShaderNoise(new Lwjgl3Clipboard(), System.currentTimeMillis()), getDefaultConfiguration());
        new Lwjgl3Application(new MusicVisualizerNoise(new Lwjgl3Clipboard(), System.currentTimeMillis()), getDefaultConfiguration());
//        new Lwjgl3Application(new INoiseViewer(null), getDefaultConfiguration());
//        new Lwjgl3Application(new SoloViewer(null), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Music and Noise!");
//        configuration.useVsync(false);
//        configuration.setForegroundFPS(0);
        configuration.useVsync(true);
        configuration.disableAudio(false);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        configuration.setWindowedMode(width, height);
//        configuration.setWindowedMode(width>>>2, height>>>2);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }

}
