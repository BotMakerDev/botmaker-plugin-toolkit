package com.botmaker.plugin.toolkit;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A label whose state changes its look wears one state's class at a time. */
class StylesTest {

    @Test
    void pickLeavesOnlyTheChosenClassOfItsSet() {
        Region node = Styles.on(new Region(), Styles.STRONG_TEXT, Styles.WARNING_TEXT);

        Styles.pick(node, Styles.OK_TEXT, Styles.WARNING_TEXT, Styles.OK_TEXT);
        Styles.pick(node, Styles.OK_TEXT, Styles.WARNING_TEXT, Styles.OK_TEXT);

        assertEquals(List.of(Styles.STRONG_TEXT, Styles.OK_TEXT), List.copyOf(node.getStyleClass()));
    }

    @Test
    void pickingNothingClearsTheSet() {
        Region node = Styles.on(new Region(), Styles.WARNING_TEXT);

        Styles.pick(node, null, Styles.WARNING_TEXT, Styles.OK_TEXT);

        assertEquals(List.of(), List.copyOf(node.getStyleClass()));
    }
}
