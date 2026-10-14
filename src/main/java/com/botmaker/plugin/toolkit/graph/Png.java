package com.botmaker.plugin.toolkit.graph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * A PNG file from ARGB pixels, written here because the usual way — {@code SwingFXUtils} and {@code ImageIO} — needs
 * {@code javafx-swing}, which no plugin has and this module may not bring (it has no dependency at all).
 */
final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private Png() {}

    /** {@code argb}, {@code width} pixels a row, as an 8-bit RGBA PNG. */
    static byte[] encode(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0 || argb.length < width * height) {
            throw new IllegalArgumentException("not a " + width + "×" + height + " image");
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(SIGNATURE);
        chunk(file, "IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array());

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(packed)) {
            byte[] row = new byte[1 + width * 4];   // filter byte 0: the row as it is
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = argb[y * width + x];
                    int at = 1 + x * 4;
                    row[at] = (byte) (pixel >> 16);
                    row[at + 1] = (byte) (pixel >> 8);
                    row[at + 2] = (byte) pixel;
                    row[at + 3] = (byte) (pixel >>> 24);
                }
                deflate.write(row);
            }
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);   // a byte array stream never fails
        }
        chunk(file, "IDAT", packed.toByteArray());
        chunk(file, "IEND", new byte[0]);
        return file.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream file, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        file.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        file.writeBytes(name);
        file.writeBytes(data);
        file.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
