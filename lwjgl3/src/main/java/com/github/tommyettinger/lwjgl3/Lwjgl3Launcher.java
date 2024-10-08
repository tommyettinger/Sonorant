package com.github.tommyettinger.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

//import com.github.tommyettinger.sonorant.INoiseViewer;
//import static com.github.tommyettinger.sonorant.INoiseViewer.width;
//import static com.github.tommyettinger.sonorant.INoiseViewer.height;

import com.github.tommyettinger.sonorant.ShaderNoise;
import static com.github.tommyettinger.sonorant.ShaderNoise.width;
import static com.github.tommyettinger.sonorant.ShaderNoise.height;

//import com.github.tommyettinger.sonorant.SoloViewer;
//import static com.github.tommyettinger.sonorant.SoloViewer.width;
//import static com.github.tommyettinger.sonorant.SoloViewer.height;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        // Needed for macOS support, but also Windows with non-ASCII usernames.
        if (StartupHelper.startNewJvmIfRequired()) return;

        new Lwjgl3Application(new ShaderNoise(null, 1L), getDefaultConfiguration());
//        new Lwjgl3Application(new INoiseViewer(null), getDefaultConfiguration());
//        new Lwjgl3Application(new SoloViewer(null), getDefaultConfiguration());
    }


    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Noise");
//        configuration.useVsync(false);
//        configuration.setForegroundFPS(0);
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);
        configuration.setWindowedMode(width, height);
//        configuration.setWindowedMode(width>>>2, height>>>2);
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
