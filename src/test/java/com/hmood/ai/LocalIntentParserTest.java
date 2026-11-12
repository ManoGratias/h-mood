package com.hmood.ai;

import com.hmood.model.Mood;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalIntentParserTest {

    private final LocalIntentParser parser = new LocalIntentParser();

    @ParameterizedTest
    @CsvSource(quoteCharacter = '"', value = {
            "Play something for a rainy day, RELAXED",
            "I need motivation, MOTIVATED",
            "Give me a romantic playlist, ROMANTIC",
            "Play music to help me concentrate, FOCUSED",
            "j'ai besoin de me concentrer pour réviser, FOCUSED",
            "un truc pour la séance de sport, MOTIVATED",
            "je suis triste ce soir, SAD",
            "give me something dreamy, DREAMY",
            "je me sens plutôt rêveur, DREAMY",
            "je suis en colère, ANGRY"
    })
    void recognisesMoods(String text, Mood expected) {
        assertEquals(expected, parser.parse(text).orElseThrow().mood());
    }

    @Test
    void recognisesSurprise() {
        assertTrue(parser.parse("Surprise me with something new").orElseThrow().surprise());
    }

    @Test
    void unknownRequestGivesNoIntent() {
        assertTrue(parser.parse("quelle heure est-il ?").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }
}
