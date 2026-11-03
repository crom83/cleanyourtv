package io.github.savioronezero.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.Test;

public class MapMathTest {
    @Test
    public void projectionCentreAndEdges() {
        float[] p = new float[2];
        MapMath.project(0, 0, p);
        assertEquals(0, p[0], 1e-6);
        assertEquals(0, p[1], 1e-6);
        MapMath.project(180, 0, p);
        assertEquals(MapMath.HALF_W, p[0], 0.001);
        MapMath.project(0, 90, p);
        assertEquals(-MapMath.HALF_H, p[1], 0.001);   // north is up
    }

    @Test
    public void sunAtTheSolsticesAndNoon() {
        double[] june = MapMath.subsolar(Instant.parse("2026-06-21T12:00:00Z").toEpochMilli());
        assertEquals(23.4, june[0], 0.3);
        assertEquals(0, june[1], 3);                     // noon at Greenwich: the sun over longitude 0
        double[] december = MapMath.subsolar(Instant.parse("2026-12-21T00:00:00Z").toEpochMilli());
        assertEquals(-23.4, december[0], 0.3);
        assertEquals(180, Math.abs(december[1]), 3);     // midnight at Greenwich: over the date line
    }

    @Test
    public void nightLineFollowsTheSun() {
        // Northern summer, sun over 0: far side's line is south of the pole...
        assertTrue(MapMath.terminator(180, 23, 0) > 60);
        // ...and under the sun the line sits far south, so most of that longitude is day
        assertTrue(MapMath.terminator(0, 23, 0) < -60);
        // At the equinox the line runs along the meridians 90 degrees from the sun
        assertEquals(0, MapMath.terminator(90, 0, 0), 1);
    }
}
