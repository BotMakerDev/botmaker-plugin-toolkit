package com.botmaker.plugin.toolkit.graph;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PngTest {

    @Test
    void aReaderGetsThePixelsBack() throws IOException {
        int[] argb = {0xFFFF0000, 0x8000FF00, 0xFF0000FF, 0x00000000, 0xFFFFFFFF, 0xFF123456};

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Png.encode(3, 2, argb)));

        assertEquals(3, image.getWidth());
        assertEquals(2, image.getHeight());
        for (int i = 0; i < argb.length; i++) {
            int read = image.getRGB(i % 3, i / 3);
            // A fully clear pixel's colour is not kept by every reader; its alpha is.
            int expected = (argb[i] >>> 24) == 0 ? 0 : argb[i];
            assertEquals(Integer.toHexString(expected), Integer.toHexString((read >>> 24) == 0 ? 0 : read), "pixel " + i);
        }
    }

    @Test
    void tooFewPixelsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Png.encode(2, 2, new int[3]));
    }
}
