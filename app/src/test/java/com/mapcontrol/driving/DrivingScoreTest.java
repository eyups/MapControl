package com.mapcontrol.driving;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrivingScoreTest {

    @Test
    public void referenceTripProducesSokakYariscisi() {
        DrivingTrip trip = new DrivingTrip();
        trip.distanceKm = 5.5;
        trip.durationSec = 32 * 60 + 28;
        trip.avgSpeedKmh = 19;
        trip.maxSpeedKmh = 50;
        trip.consumptionL100 = 10.4;
        trip.harshBrakes = 1;
        trip.harshAccels = 1;

        DrivingScore.evaluate(trip);

        assertEquals(84, trip.score);
        assertEquals("B", trip.grade);
        assertEquals("Sakin Kaptan", trip.title);
        assertTrue(trip.description.matches("(?s).*5[.,]5 km.*"));
        assertTrue(trip.description.contains("litre"));
        assertTrue(trip.description.contains("1 kez sert fren"));
        assertTrue(trip.description.contains("1 kez sert kalkış"));
    }

    @Test
    public void cityConsumptionIsJudgedAgainstTiggo8Band() {
        DrivingTrip trip = new DrivingTrip();
        trip.distanceKm = 8.0;
        trip.durationSec = 1800;
        trip.avgSpeedKmh = 20;
        trip.maxSpeedKmh = 55;
        trip.consumptionL100 = 10.4;

        DrivingScore.evaluate(trip);

        assertEquals(100, trip.score);
        assertEquals("A", trip.grade);
    }

    @Test
    public void cleanDriveScoresFullMarks() {
        DrivingTrip trip = new DrivingTrip();
        trip.distanceKm = 20.0;
        trip.durationSec = 1800;
        trip.avgSpeedKmh = 40;
        trip.maxSpeedKmh = 70;
        trip.consumptionL100 = 5.0;

        DrivingScore.evaluate(trip);

        assertEquals(100, trip.score);
        assertEquals("A", trip.grade);
        assertEquals("Efsane Şoför", trip.title);
    }

    @Test
    public void standingStillDoesNotGetPerfectScore() {
        DrivingTrip trip = new DrivingTrip();
        trip.distanceKm = 0.0;
        trip.durationSec = 85;

        DrivingScore.evaluate(trip);

        assertEquals(0, trip.score);
        assertEquals("—", trip.grade);
        assertEquals("Veri Bekleniyor", trip.title);
    }

    @Test
    public void aggressiveDriveCapsAtFloor() {
        DrivingTrip trip = new DrivingTrip();
        trip.distanceKm = 12.0;
        trip.durationSec = 600;
        trip.avgSpeedKmh = 120;
        trip.maxSpeedKmh = 180;
        trip.consumptionL100 = 25.0;
        trip.harshBrakes = 10;
        trip.harshAccels = 10;

        DrivingScore.evaluate(trip);

        assertEquals(20, trip.score);
        assertEquals("F", trip.grade);
    }
}
