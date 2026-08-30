package com.github.tommyettinger.teavm;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;

/**
 * Launches the TeaVM/HTML application.
 */
public class TeaVMLauncher {
    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        // change these to both 0 to use all available space, or both -1 for the canvas size.
        config.width = 0;
        config.height = 0;
//        new WebApplication(new ShaderNoise(new ReadWriteClipboard(), System.currentTimeMillis()), config);
//        config.width = 750;
//        config.height = 1000;
        new WebApplication(new com.github.tommyettinger.sonorant.InputShaderNoise(new ReadWriteClipboard(), System.currentTimeMillis()), config);
    }
}
