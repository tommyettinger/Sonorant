package com.github.tommyettinger.sonorant;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.github.tommyettinger.digital.*;

import static com.badlogic.gdx.Input.Keys.*;

/**
 * Credit for the shader adaptation goes to angelickite , a very helpful user on the libGDX Discord.
 * The Discord can be found at <a href="https://discord.gg/crTrDEK">this link</a>.
 */
public class InputShaderNoise extends ApplicationAdapter {

    private SpriteBatch batch;
    private Texture pixel;
    private ShaderProgram shader;

    private long startTime;
    private float seed = 3.1337f;
    private float rMod = 0f;
    private float gMod = 0f;
    private float bMod = 0f;
    private float lastX = 0f;
    private float lastY = 0f;
    private long inputMillis;
    private float twist = 0.6f;
    //	public static final int WIDTH = 1920, HEIGHT = 1080;
    public static final int WIDTH = 600, HEIGHT = 600;
    public static int width = WIDTH, height = HEIGHT;
    private Clipboard clipboard;

    public InputShaderNoise(Clipboard clippy, long initialSeed) {
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
        inputMillis = startTime;

        ShaderProgram.pedantic = true;
        shader = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sanaradj_fragment.glsl"));
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
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if(pointer == 0 && button == Input.Buttons.LEFT) {
                    lastX = screenX * 0.3f / Gdx.graphics.getWidth();
                    lastY = screenY * 0.3f / Gdx.graphics.getHeight();
                    inputMillis = TimeUtils.millis();
                } else {
                    seed += 1 - (pointer & 2);
                }
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if(pointer == 0 && !Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                    lastX = gMod = screenX * 0.3f / Gdx.graphics.getWidth();
                    lastY = bMod = screenY * 0.3f / Gdx.graphics.getHeight();
                    inputMillis = TimeUtils.millis();
                }
                return false;
            }
        });
    }

    public void reseed(long state) {
        final long dim = MathTools.longFloor(seed);
        seed = MathTools.fract(((((state = (state ^ (state << 41 | state >>> 23) ^ (state << 17 | state >>> 47) ^ 0xD1B54A32D192ED03L) * 0xAEF17502108EF2D9L) ^ state >>> 43 ^ state >>> 31 ^ state >>> 23) * 0xDB4F0B9175AE2165L) >>> 36) * 0x1.1p-17f) + dim;
        System.out.println("Now using seed " + seed);
    }

    @Override public void resize (int width, int height) {
        InputShaderNoise.width = width;
        InputShaderNoise.height = height;
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

        long since = TimeUtils.timeSinceMillis(inputMillis);
        if(since < 500L){
            float alpha = since * 0.002f;
            gMod = Interpolations.smooth.apply(gMod, lastX, alpha);
            bMod = Interpolations.smooth.apply(bMod, lastY, alpha);
        }
        final float fTime = TimeUtils.timeSinceMillis(startTime) * TrigTools.PI2 * 0x1p-13f;
        batch.begin();
        shader.setUniformf("u_seed", seed);
        shader.setUniformf("u_time", fTime);
        shader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shader.setUniformf("u_adj",
            rMod,
            gMod,
            bMod,
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
        int w = Base.BASE10.readInt(s, gap+1, gap = s.indexOf('_', gap+1));
        int h = Base.BASE10.readInt(s, gap+1, s.length());
//        if(Gdx.app.getType() != Application.ApplicationType.WebGL && (w != 0 && h != 0 && (w != width || h != height)))
//            Gdx.graphics.setWindowedMode(w, h);
    }
}
