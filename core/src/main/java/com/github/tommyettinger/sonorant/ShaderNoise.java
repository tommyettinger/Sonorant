package com.github.tommyettinger.sonorant;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.github.tommyettinger.anim8.AnimatedGif;
import com.github.tommyettinger.anim8.Dithered;
import com.github.tommyettinger.anim8.QualityPalette;
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
	private ShaderProgram shader;
	private ShaderProgram shaderStandard;
	private ShaderProgram shaderRidged;
	private AnimatedGif gif;

	private long startTime;
	private float seed = 3.1337f;
	private float variance = 0.5f;
	private float a = 0.07f;
	private float b = 0.9f;
	private float frequency = 0.6f;
	public static final int WIDTH = 350, HEIGHT = 350;
	public static int width = WIDTH, height = HEIGHT;
	private final Array<Pixmap> frames = new Array<>(256);
	private Clipboard clipboard;

	public ShaderNoise(Clipboard clippy, long initialSeed) {
		clipboard = clippy;
		reseed(initialSeed);
	}

	@Override public void create () {
		Gdx.app.setLogLevel(Application.LOG_INFO);
		batch = new SpriteBatch();
		
		Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixmap.drawPixel(0, 0, 0xFFFFFFFF);
		pixel = new Texture(pixmap);
		startTime = TimeUtils.millis();

		if(Gdx.app.getType() != Application.ApplicationType.WebGL) {
			gif = new LoafGif();
			gif.setDitherAlgorithm(Dithered.DitherAlgorithm.LOAF);
			gif.setDitherStrength(1f);
			gif.fastAnalysis = false;
//			gif.palette = new QualityPalette();
		}


		ShaderProgram.pedantic = true;
//		shaderStandard = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("northern_fragment.glsl"));
//		shaderStandard = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sonorant_fragment.glsl"));
		shaderStandard = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sanarant_fragment.glsl"));
//		shader = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("foam_fragment.glsl"));
		if (!shaderStandard.isCompiled()) {
			Gdx.app.error("Shader", "error compiling shaderStandard:\n" + shaderStandard.getLog());
			Gdx.app.exit();
			return;
		}
		shaderRidged = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("ana_fragment.glsl"));
//		shaderRidged = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sonorant_fragment_ridged.glsl"));
		if (!shaderRidged.isCompiled()) {
			Gdx.app.error("Shader", "error compiling shaderRidged:\n" + shaderRidged.getLog());
			Gdx.app.exit();
			return;
		}
		shader = shaderStandard;
		batch.setShader(shader);

		// System.nanoTime() is supported by GWT 2.10.0 .
//		long state = System.nanoTime() + startTime;//-1234567890L;
		long state = BitConversion.doubleToLongBits(seed * 1234567890.0987654321);
		// Sarong's DiverRNG.randomize()
		reseed(state);
		// changes the start time (in milliseconds) by up to 65535 ms, based on state (which uses nanoseconds).
		startTime -= (state ^ state >>> 11) & 0xFFFFL;
		width = Gdx.graphics.getWidth();
		height = Gdx.graphics.getHeight();
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
		if((Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.F11)) && UIUtils.alt())
		{
			if(Gdx.graphics.isFullscreen()) {
				Gdx.graphics.setWindowedMode(WIDTH, HEIGHT);
			} else {
				Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
			}
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.S)){ // seed
			seed += UIUtils.shift() ? 0.001f : -0.001f;
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.D)){ // divisions
			seed += UIUtils.shift() ? 1f : -1f;
		} else if(Gdx.input.isKeyJustPressed(SLASH)){ // seed
			long state = BitConversion.doubleToLongBits(System.nanoTime() * MathTools.fract(seed)) + 0xD1B54A32D192ED03L;
			reseed(state);
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.R)){ // reset
			startTime = TimeUtils.millis();
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.P)){ // performance
			Gdx.app.log("FPS", String.valueOf(Gdx.graphics.getFramesPerSecond()));
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.Q) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){ // quit
			Gdx.app.exit();
		}
		else if (Gdx.input.isKeyPressed(F))
			frequency = Math.min(Math.max(0.001f, frequency + (UIUtils.shift() ? Gdx.graphics.getDeltaTime() : -Gdx.graphics.getDeltaTime())), 1f);
		else if (Gdx.input.isKeyPressed(V))
			variance = Math.min(Math.max(0.001f, variance + 0.25f * (UIUtils.shift() ? Gdx.graphics.getDeltaTime() : -Gdx.graphics.getDeltaTime())), 1f);
		else if (Gdx.input.isKeyPressed(A))
			a = Math.min(Math.max(0.001f, a + 0.25f * (UIUtils.shift() ? Gdx.graphics.getDeltaTime() : -Gdx.graphics.getDeltaTime())), 1f);
		else if (Gdx.input.isKeyPressed(B))
			b = Math.min(Math.max(0.001f, b + Gdx.graphics.getDeltaTime() * (UIUtils.shift() ? 0.25f : -0.25f)), 1f);
		else if(Gdx.input.isKeyJustPressed(C))
			batch.setShader(shader = (shader == shaderStandard) ? shaderRidged : shaderStandard);
		else if(Gdx.input.isKeyJustPressed(W) && Gdx.app.getType() != Application.ApplicationType.WebGL) {
			if (gif != null) {
				frames.clear();
				long millis = TimeUtils.timeSinceMillis(startTime) & -1024L;
				for (int i = 0; i < 256; i++) {
					batch.begin();
					shader.setUniformf("u_seed", seed);
					shader.setUniformf("u_time", i * TrigTools.PI2 * 0x1p-7f);
					shader.setUniformf("u_resolution", WIDTH, HEIGHT);
					batch.setColor(variance, a, b, frequency);
					batch.draw(pixel, 0f, 0f, WIDTH<<1, HEIGHT<<1);
					batch.end();
					frames.add(Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT));
				}
//				gif.palette.analyzeHueWise(frames);
				gif.write(Gdx.files.local("out/gif/" + seed + "_" + frequency + "_" + variance + "_" + a + "_" + b + "_" + ".gif"), frames, 24);
			}

		}

		final float ftm = TimeUtils.timeSinceMillis(startTime) * TrigTools.PI2 * 0x1p-12f;
		batch.begin();
		shader.setUniformf("u_seed", seed);
		shader.setUniformf("u_time", ftm);
		shader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		batch.setColor(variance, a, b, frequency);
		batch.draw(pixel, 0, 0, width, height);
		batch.end();
	}
}
