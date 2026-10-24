package com.mapcontrol.driving;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Aggregates saved trips into weekly/monthly trend windows. */
public final class DrivingTrends {

    public static final class Period {
        public int tripCount;
        public double distanceKm;
        public int avgScore;
        public double avgConsumptionL100;
        public boolean hasConsumption;

        public boolean hasData() {
            return tripCount > 0;
        }
    }

    public static final class Snapshot {
        public Period thisWeek = new Period();
        public Period lastWeek = new Period();
        public Period thisMonth = new Period();
        public Period lastMonth = new Period();
        public DrivingTrip bestTrip;
        public DrivingTrip worstTrip;

        public double weekScoreDelta() {
            return thisWeek.avgScore - lastWeek.avgScore;
        }

        public double weekConsumptionDelta() {
            return thisWeek.avgConsumptionL100 - lastWeek.avgConsumptionL100;
        }

        public double monthScoreDelta() {
            return thisMonth.avgScore - lastMonth.avgScore;
        }

        public double monthConsumptionDelta() {
            return thisMonth.avgConsumptionL100 - lastMonth.avgConsumptionL100;
        }
    }

    private DrivingTrends() {
    }

    public static Snapshot calculate(List<DrivingTrip> trips) {
        Snapshot snapshot = new Snapshot();
        if (trips == null) {
            return snapshot;
        }
        long now = System.currentTimeMillis();
        long weekStart = startOfWeek(now);
        long lastWeekStart = weekStart - 7L * 24L * 60L * 60L * 1000L;
        long monthStart = startOfMonth(now);
        long lastMonthStart = startOfPreviousMonth(now);

        snapshot.thisWeek = summarize(trips, weekStart, now + 1);
        snapshot.lastWeek = summarize(trips, lastWeekStart, weekStart);
        snapshot.thisMonth = summarize(trips, monthStart, now + 1);
        snapshot.lastMonth = summarize(trips, lastMonthStart, monthStart);

        for (DrivingTrip trip : trips) {
            if (trip == null || trip.distanceKm < 0.1) {
                continue;
            }
            if (snapshot.bestTrip == null || trip.score > snapshot.bestTrip.score
                    || (trip.score == snapshot.bestTrip.score
                    && trip.startTimeMs > snapshot.bestTrip.startTimeMs)) {
                snapshot.bestTrip = trip;
            }
            if (snapshot.worstTrip == null || trip.score < snapshot.worstTrip.score
                    || (trip.score == snapshot.worstTrip.score
                    && trip.startTimeMs < snapshot.worstTrip.startTimeMs)) {
                snapshot.worstTrip = trip;
            }
        }
        return snapshot;
    }

    private static Period summarize(List<DrivingTrip> trips, long fromMs, long toMs) {
        Period period = new Period();
        double weightedScore = 0.0;
        double scoreWeight = 0.0;
        double weightedConsumption = 0.0;
        double consumptionDistance = 0.0;
        for (DrivingTrip trip : trips) {
            if (trip == null || trip.startTimeMs < fromMs || trip.startTimeMs >= toMs
                    || trip.distanceKm < 0.1) {
                continue;
            }
            period.tripCount++;
            period.distanceKm += trip.distanceKm;
            double weight = Math.max(0.1, trip.distanceKm);
            weightedScore += trip.score * weight;
            scoreWeight += weight;
            if (trip.consumptionL100 > 0.0) {
                weightedConsumption += trip.consumptionL100 * weight;
                consumptionDistance += weight;
            }
        }
        if (scoreWeight > 0.0) {
            period.avgScore = (int) Math.round(weightedScore / scoreWeight);
        }
        if (consumptionDistance > 0.0) {
            period.avgConsumptionL100 = weightedConsumption / consumptionDistance;
            period.hasConsumption = true;
        }
        period.distanceKm = Math.round(period.distanceKm * 10.0) / 10.0;
        return period;
    }

    /** Seven daily average scores for the current week; -1 means no trip. */
    public static int[] weeklyDailyScores(List<DrivingTrip> trips, long nowMs) {
        long start = startOfWeek(nowMs);
        return scoresForBuckets(trips, start, 7L * 24L * 60L * 60L * 1000L, 7);
    }

    /** Five weekly average scores for the current month; -1 means no trip. */
    public static int[] monthlyWeeklyScores(List<DrivingTrip> trips, long nowMs) {
        long start = startOfMonth(nowMs);
        return scoresForBuckets(trips, start, 7L * 24L * 60L * 60L * 1000L, 5);
    }

    private static int[] scoresForBuckets(List<DrivingTrip> trips, long startMs,
            long bucketMs, int bucketCount) {
        double[] weighted = new double[bucketCount];
        double[] weights = new double[bucketCount];
        if (trips != null) {
            for (DrivingTrip trip : trips) {
                if (trip == null || trip.distanceKm < 0.1) {
                    continue;
                }
                long elapsed = trip.startTimeMs - startMs;
                if (elapsed < 0 || elapsed >= bucketMs * bucketCount) {
                    continue;
                }
                int bucket = (int) (elapsed / bucketMs);
                double weight = Math.max(0.1, trip.distanceKm);
                weighted[bucket] += trip.score * weight;
                weights[bucket] += weight;
            }
        }
        int[] result = new int[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            result[i] = weights[i] > 0.0
                    ? (int) Math.round(weighted[i] / weights[i]) : -1;
        }
        return result;
    }

    public static long startOfWeek(long timeMs) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (day + 5) % 7;
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        return calendar.getTimeInMillis();
    }

    public static long startOfMonth(long timeMs) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long startOfPreviousMonth(long timeMs) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTimeInMillis(startOfMonth(timeMs));
        calendar.add(Calendar.MONTH, -1);
        return calendar.getTimeInMillis();
    }
}
