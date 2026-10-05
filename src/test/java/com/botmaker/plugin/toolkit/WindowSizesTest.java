package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class WindowSizesTest {

    @Test
    void opensAtItsOwnSizeUntilOneIsRemembered() {
        assertArrayEquals(new double[] {800, 600}, WindowSizes.opening("WindowSizesTest fresh", 800, 600));
    }

    @Test
    void reopensAtTheSizeItClosedAt() {
        WindowSizes.remember("WindowSizesTest resized", 1024, 700);
        assertArrayEquals(new double[] {1024, 700}, WindowSizes.opening("WindowSizesTest resized", 800, 600));
    }

    @Test
    void eachTitleKeepsItsOwnSize() {
        WindowSizes.remember("WindowSizesTest a", 900, 500);
        assertArrayEquals(new double[] {400, 300}, WindowSizes.opening("WindowSizesTest b", 400, 300));
    }

    @Test
    void aWindowThatNeverLaidOutIsNotRemembered() {
        WindowSizes.remember("WindowSizesTest unlaid", 0, 0);
        WindowSizes.remember("WindowSizesTest unlaid", Double.NaN, 500);
        WindowSizes.remember(null, 900, 500);
        assertArrayEquals(new double[] {640, 480}, WindowSizes.opening("WindowSizesTest unlaid", 640, 480));
        assertArrayEquals(new double[] {640, 480}, WindowSizes.opening(null, 640, 480));
    }
}
