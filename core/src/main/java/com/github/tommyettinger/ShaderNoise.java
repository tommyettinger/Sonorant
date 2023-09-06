package com.github.tommyettinger;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * Credit for the shader adaptation goes to angelickite , a very helpful user on the libGDX Discord.
 * The Discord can be found at <a href="https://discord.gg/crTrDEK">this link</a>.
 */
public class ShaderNoise extends ApplicationAdapter {

	private SpriteBatch batch;
	private Texture pixel;
	private ShaderProgram shader;

	private long startTime;
	private float seed;
	private int width, height;
	private Clipboard clipboard;

	public ShaderNoise(Clipboard clippy) {
		clipboard = clippy;
	}

	@Override public void create () {
		Gdx.app.setLogLevel(Application.LOG_ERROR);
		batch = new SpriteBatch();
		
		Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixmap.drawPixel(0, 0, 0xFFFFFFFF);
		pixel = new Texture(pixmap);
		startTime = TimeUtils.millis();
		ShaderProgram.pedantic = true;
		shader = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("sonorant_fragment.glsl"));
//		shader = new ShaderProgram(Gdx.files.internal("foam_vertex.glsl"), Gdx.files.internal("foam_fragment.glsl"));
		if (!shader.isCompiled()) {
			Gdx.app.error("Shader", "error compiling shader:\n" + shader.getLog());
			Gdx.app.exit();
			return;
		}
		batch.setShader(shader);

		// System.nanoTime() is supported by GWT 2.10.0 .
		long state = System.nanoTime() + startTime;//-1234567890L;
		// Sarong's DiverRNG.randomize()
		seed = ((((state = (state ^ (state << 41 | state >>> 23) ^ (state << 17 | state >>> 47) ^ 0xD1B54A32D192ED03L) * 0xAEF17502108EF2D9L) ^ state >>> 43 ^ state >>> 31 ^ state >>> 23) * 0xDB4F0B9175AE2165L) >>> 36) * 0x1.5bf0a8p-16f;
		// changes the start time (in milliseconds) by up to 65535 ms, based on state (which uses nanoseconds).
		startTime -= (state ^ state >>> 11) & 0xFFFFL;
		width = Gdx.graphics.getWidth();
		height = Gdx.graphics.getHeight();
	}

	@Override public void resize (int width, int height) {
		this.width = width;
		this.height = height;
		batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
	}

	@Override public void render () {
		ScreenUtils.clear(0f, 0f, 0f, 0f);
		if((Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.F11)) && UIUtils.alt())
		{
			if(Gdx.graphics.isFullscreen()) {
				Gdx.graphics.setWindowedMode(480, 480);
			} else {
				Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
			}
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.S)){ // seed
			seed += UIUtils.shift() ? 1 : -1;
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.R)){ // reset
			startTime = TimeUtils.millis();
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.F)){ // fps
			System.out.println(Gdx.graphics.getFramesPerSecond());
		} else if(Gdx.input.isKeyJustPressed(Input.Keys.Q) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){ // quit
			Gdx.app.exit();
		}
		final float ftm = TimeUtils.timeSinceMillis(startTime) * (0x1p-10f);
		batch.begin();
		shader.setUniformf("u_seed", seed);
		shader.setUniformf("u_time", ftm);
		shader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		batch.draw(pixel, 0, 0, width, height);
		batch.end();
	}
}
