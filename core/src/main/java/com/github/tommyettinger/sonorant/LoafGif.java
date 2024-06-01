package com.github.tommyettinger.sonorant;

import com.github.tommyettinger.anim8.AnimatedGif;

public class LoafGif extends AnimatedGif {
    public LoafGif() {
        super();
    }

    @Override
    protected void analyzeLoaf() {
        final int nPix = indexedPixels.length;
        int color;
        int flipped = flipY ? height - 1 : 0;
        int flipDir = flipY ? -1 : 1;
        final int[] paletteArray = palette.paletteArray;
        final byte[] paletteMapping = palette.paletteMapping;
        boolean hasTransparent = paletteArray[0] == 0;
//        final float strength = ditherStrength;
        final int strength = (int) (8 * ditherStrength + 0.5f);
        for (int y = 0, i = 0; y < height && i < nPix; y++) {
            for (int px = 0; px < width & i < nPix; px++) {
                color = image.getPixel(px, flipped + flipDir * y);
                if ((color & 0x80) == 0 && hasTransparent)
                    indexedPixels[i++] = 0;
                else {
                    int adj = ((px + y & 1) - (px & y & 1)) * (strength + (((px + y ^ px + y >>> 1) & 3)) * (1 + ((px * 5 ^ y * 3) >>> 1 & 3)));
                    int rr = Math.min(Math.max(((color >>> 24)) + adj, 0), 255);
                    int gg = Math.min(Math.max(((color >>> 16) & 0xFF) + adj, 0), 255);
                    int bb = Math.min(Math.max(((color >>> 8) & 0xFF) + adj, 0), 255);
                    int rgb555 = ((rr << 7) & 0x7C00) | ((gg << 2) & 0x3E0) | ((bb >>> 3));
                    usedEntry[(indexedPixels[i] = paletteMapping[rgb555]) & 255] = true;
                    i++;
                }
            }
        }
    }
}
