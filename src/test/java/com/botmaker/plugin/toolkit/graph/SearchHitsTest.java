package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphCanvas.Hit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHitsTest {

    private static final Map<String, List<String>> TERMS = Map.of(
            "tavern", List.of("Tavern", "input beerBuy"),
            "market", List.of("Market", "input beerBuy", "picture BEER_BUY"),
            "home", List.of("Home", "input market"),
            "battle", List.of("Battle"));
    private static final List<String> ORDER = List.of("tavern", "market", "home", "battle");
    private static final Function<String, List<String>> OF = TERMS::get;

    @Test
    void aNameThatBeginsWithTheQueryComesFirstThenANameThatHoldsItThenAnotherTerm() {
        List<Hit<String>> hits = GraphSearch.hits(ORDER, OF, "mar");
        assertEquals(List.of(new Hit<>("market", ""), new Hit<>("home", "input market")), hits);
        List<Hit<String>> inside = GraphSearch.hits(ORDER, OF, "a");
        assertEquals(List.of("tavern", "market", "battle", "home"), inside.stream().map(Hit::node).toList());
    }

    @Test
    void eachItemIsListedOnceByItsFirstMatchingTermIgnoringCase() {
        assertEquals(List.of(new Hit<>("tavern", "input beerBuy"), new Hit<>("market", "input beerBuy")),
                GraphSearch.hits(ORDER, OF, "  BEERBUY "));
    }

    @Test
    void aTermsLabelIsShownButNotSearchedAndALongTermIsOneLine() {
        assertTrue(GraphSearch.hits(ORDER, OF, "input").isEmpty(), "every screen has inputs: the label finds none");
        Function<String, List<String>> noted = n -> List.of(n, "notes line one\n  line two");
        assertEquals(List.of(new Hit<>("home", "notes line one line two")),
                GraphSearch.hits(List.of("home"), noted, "two"));
    }

    @Test
    void anEmptyQueryFindsNothing() {
        assertTrue(GraphSearch.hits(ORDER, OF, " ").isEmpty());
        assertTrue(GraphSearch.hits(ORDER, OF, null).isEmpty());
    }

    @Test
    void aLongTermShowsThePartAroundWhatMatched() {
        String notes = "notes " + "x".repeat(60) + " the beer stall restocks at noon " + "y".repeat(60);
        String shown = GraphSearch.around(notes, notes.indexOf("restocks"), "restocks".length());
        assertTrue(shown.contains("restocks"), shown);
        assertTrue(shown.startsWith("…") && shown.endsWith("…"), shown);
        assertEquals("short", GraphSearch.around("short", 0, 2));
    }
}
