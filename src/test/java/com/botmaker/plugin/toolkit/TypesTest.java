package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.EditableType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypesTest {

    public record Spot(int x, int y) {}

    public record Tuning(double scale, long ticks, boolean on, String name) {}

    record Hidden(int n) {}

    public record Positive(int n) {
        public Positive {
            if (n < 0) throw new IllegalArgumentException("negative");
        }
    }

    enum Speed { SLOW, FAST }

    /** A varargs factory, as {@code Combo.of(Key...)} is. */
    public static final class Row {
        final List<String> cells;

        Row(List<String> cells) { this.cells = cells; }

        public static Row of(String... cells) { return new Row(List.of(cells)); }
    }

    @Test
    void aRecordRoundTripsThroughItsCanonicalConstructor() {
        ComponentType<Tuning> call = Types.record(Tuning.class);
        Tuning value = new Tuning(1.5, 40L, true, "fast");

        assertEquals(List.of(double.class, long.class, boolean.class, String.class), call.componentTypes());
        assertEquals(List.of(1.5, 40L, true, "fast"), call.components(value));
        assertEquals(value, call.build(call.components(value)));
        assertEquals(Tuning.class.getRecordComponents().length, call.factory().getParameterCount());
    }

    @Test
    void aShortOrMistypedRecordReadsAsZeroAndNeverThrows() {
        ComponentType<Spot> call = Types.record(Spot.class);

        assertEquals(new Spot(3, 0), call.build(List.of(3)));
        assertEquals(new Spot(0, 0), call.build(List.of("x", "y")));
        assertEquals(new Spot(2, 0), call.build(List.of(2.4)), "a number is rounded, as whole() rounds");
    }

    @Test
    void aRecordWhoseConstructorRefusesBuildsNothing() {
        assertNull(Types.record(Positive.class).build(List.of(-1)));
        assertEquals(new Positive(4), Types.record(Positive.class).build(List.of(4)));
    }

    @Test
    void aCallsPartsAreItsFactorysParametersWithVarargsAsTheElement() {
        Types.Call<Row> call = Types.call(Row.class, Types.method(Row.class, "of", String[].class),
                row -> new ArrayList<>(row.cells), parts -> {
                    List<String> cells = Types.each(parts, String.class);
                    return cells == null ? null : new Row(cells);
                });

        assertEquals(List.of(String.class), call.componentTypes());
        assertEquals(List.of("a", "b"), call.build(List.of("a", "b")).cells);
        assertNull(call.build(List.of("a", 2)), "a part of another kind is a call this does not build");
        assertNull(call.build(List.of()), "no parts is not a row");
    }

    @Test
    void anInstanceFactoryHasItsReceiverAsPartZero() {
        assertEquals(List.of(Duration.class, long.class),
                Types.parts(Types.method(Duration.class, "plusMillis", long.class)));
    }

    @Test
    void aMissingMemberFailsWhenLookedUpNamingIt() {
        IllegalStateException method = assertThrows(IllegalStateException.class,
                () -> Types.method(Duration.class, "ofFortnights", long.class));
        assertTrue(method.getMessage().contains("java.time.Duration.ofFortnights"));

        assertThrows(IllegalStateException.class, () -> Types.constructor(Spot.class, String.class));
        assertThrows(IllegalStateException.class, () -> Types.constant(ZoneOffset.class, "MARS"));
        assertEquals("UTC", Types.constant(ZoneOffset.class, "UTC").getName());
    }

    @Test
    void aRecordTheHostCannotCallIsRefusedWhenDeclared() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Types.record(Hidden.class));
        assertTrue(refused.getMessage().contains("not a public record"));
    }

    @Test
    void anEnumsFreshValueIsItsFirstConstant() {
        EditableType<Speed> type = Types.enumType(Speed.class, () -> ctx -> null);

        assertSame(Speed.SLOW, type.fresh());
        assertEquals(Speed.class, type.type());
        assertNull(type.preview(null));
    }

    /**
     * The editor is not touched until the host draws: evaluating it is what would link JavaFX, and a headless
     * host builds every declaration without it.
     */
    @Test
    void declaringATypeNeverReadsItsEditor() {
        Types.Drawn untouchable = () -> {
            throw new AssertionError("the editor was read while declaring");
        };

        Types.DeclaredCall<Spot> type = Types.editable(Spot.class, () -> new Spot(0, 0), untouchable)
                .preview(untouchable)
                .writtenAs(Types.record(Spot.class));

        assertEquals(new Spot(0, 0), type.fresh());
        assertThrows(AssertionError.class, () -> type.editor(null), "drawing is what reads it");
    }

    @Test
    void aTypeWrittenAsACallIsOneObjectTheHostListsAsBoth() {
        Types.Call<Spot> call = Types.record(Spot.class);
        Types.DeclaredCall<Spot> type = Types.editable(Spot.class, () -> new Spot(0, 0), () -> ctx -> null)
                .writtenAs(call)
                .preview(() -> ctx -> null);

        assertInstanceOf(ComponentType.class, type, "preview() after writtenAs() keeps the call");
        assertEquals(call.factory(), type.factory());
        assertEquals(new Spot(1, 2), type.build(type.components(new Spot(1, 2))));
        assertEquals(new Spot(0, 0), type.fresh());
    }

    @Test
    void aSeededTypeAnswersNoValueAndItsCall() {
        EditableType<Duration> type = Types.editable(Duration.class, () -> null, () -> ctx -> null)
                .freshCall(Types.method(Duration.class, "ofMillis", long.class));

        assertNull(type.fresh());
        assertEquals("ofMillis", type.freshCall().getName());
    }

    @Test
    void constantsAreCarriedByTheCall() {
        Types.Call<ZoneOffset> call = Types.call(ZoneOffset.class,
                        Types.method(ZoneOffset.class, "ofHoursMinutes", int.class, int.class),
                        offset -> List.of(offset.getTotalSeconds() / 3600, 0),
                        parts -> ZoneOffset.ofHoursMinutes(Types.whole(parts, 0), Types.whole(parts, 1)))
                .constants(Types.constant(ZoneOffset.class, "UTC"));

        assertEquals(List.of("UTC"), call.constants().stream().map(java.lang.reflect.Field::getName).toList());
        assertEquals(ZoneOffset.ofHours(2), call.build(call.components(ZoneOffset.ofHours(2))));
    }
}
