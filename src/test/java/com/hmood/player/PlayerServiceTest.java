package com.hmood.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerServiceTest {

    @Test
    void tempoIsMatchedWithinSixPercent() {
        assertEquals(1.0, PlayerService.matchRate(null, 120.0));
        assertEquals(120.0 / 116.0, PlayerService.matchRate(120.0, 116.0), 1e-9);   // +3,4 % : calé
        assertEquals(1.0, PlayerService.matchRate(140.0, 100.0));                    // trop loin : pas de calage
        assertEquals(2 * 70.0 / 136.0, PlayerService.matchRate(70.0, 136.0), 1e-9);   // tempo moitié / double
    }
}
