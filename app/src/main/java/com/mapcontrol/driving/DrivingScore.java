package com.mapcontrol.driving;

import java.util.Locale;
import java.util.Random;

/** Turns raw trip statistics into a score, a grade and a deliberately cheeky report. */
public final class DrivingScore {

    private DrivingScore() {
    }

    /**
     * Realistic real-world bands for a Chery Tiggo 8 (1.6T/2.0T):
     * heavy city traffic ~10-11 L/100 km, mixed ~8-9 L/100 km, highway ~7-8 L/100 km.
     * The expected value rises as average speed falls because idling/stop-go
     * naturally raises consumption.
     */
    public static double expectedConsumptionL100(int avgSpeedKmh) {
        if (avgSpeedKmh <= 20) {
            return 10.5;
        } else if (avgSpeedKmh <= 35) {
            return 9.2;
        } else if (avgSpeedKmh <= 60) {
            return 8.0;
        }
        return 7.2;
    }

    /** Consumption above the Tiggo 8 band + 1 L tolerance costs points. */
    public static int consumptionPenalty(DrivingTrip trip) {
        if (trip == null || trip.consumptionL100 <= 0.0) {
            return 0;
        }
        double expected = expectedConsumptionL100(trip.avgSpeedKmh);
        double excess = trip.consumptionL100 - expected - 1.0;
        if (excess <= 0.0) {
            return 0;
        }
        return (int) Math.round(excess * 2.5);
    }

    public static void evaluate(DrivingTrip trip) {
        if (trip == null) {
            return;
        }
        if (trip.distanceKm < 0.1) {
            trip.score = 0;
            trip.grade = "—";
            trip.title = "Veri Bekleniyor";
            trip.description = "Henüz yol almadan puan vermek olmaz. Birkaç yüz metre sür, "
                    + "sonra frenlerine ve turbo alışkanlıklarına bakalım.";
            return;
        }
        int score = 100;
        score -= trip.harshBrakes * 4;
        score -= trip.harshAccels * 3;
        score -= consumptionPenalty(trip);
        if (trip.maxSpeedKmh > 110) {
            score -= (trip.maxSpeedKmh - 110) / 3;
        }
        if (trip.avgSpeedKmh > 80) {
            score -= (trip.avgSpeedKmh - 80) / 2;
        }
        trip.score = Math.max(20, Math.min(100, score));

        if (trip.score >= 90) {
            trip.title = "Efsane Şoför";
            trip.grade = "A";
        } else if (trip.score >= 80) {
            trip.title = "Sakin Kaptan";
            trip.grade = "B";
        } else if (trip.score >= 70) {
            trip.title = "Trafik Canavarı";
            trip.grade = "C";
        } else if (trip.score >= 60) {
            trip.title = "Sokak Yarışçısı";
            trip.grade = "D";
        } else {
            trip.title = "Ehliyet Askıda";
            trip.grade = "F";
        }
        trip.description = describe(trip);
    }

    private static final String[] BRAKES_OK = {
            "Frenlere gereksiz yere dokunmadın; balatalar tatilde.",
            "Fren pedalına nazik davrandın; balatalar sağlığına kavuştu.",
            "Sert fren yok; balatalar bu ay prim alacak.",
            "Frenler dinlendi; balatalar senden memnun.",
            "Yumuşak frenlemeler; balatalar \"devam\" dedi.",
            "Frenlere hiç yüklenmedin; balatalar sana teşekkür ediyor."
    };
    private static final String[] BRAKES_BAD = {
            "%d kez sert fren yaptın; balatalar senden şikayetçi.",
            "%d kez sert frende balatalar imza attı; emekli olmak istiyorlar.",
            "%d kez ani fren yaptın; arka koltuk tiyatro izledi.",
            "%d kez frene yüklendin; ABS'ye \"sakin ol\" dedik.",
            "%d kez sert fren; balataların tatil planları suya düştü.",
            "%d kez sert fren yaptın; kahve bardağı ön cama yapıştı."
    };
    private static final String[] ACCELS_OK = {
            "Kalkışların pamuk gibiydi; turbo mutlu.",
            "Gaz pedalına centilmen davrandın; turbo rahat.",
            "Yumuşak kalkışlar; turbo sana gülümsüyor.",
            "Ani gaz yok; turbo kahvesini içiyor.",
            "Kalkışların akıcı; turbo \"işte bu\" dedi.",
            "Gazda acele yok; turbo memnun."
    };
    private static final String[] ACCELS_BAD = {
            "%d kez sert kalkış yaptın; turbo sana küstü.",
            "%d kez gazı kökledin; turbo \"ben burada çalışıyorum\" dedi.",
            "%d kez ani hızlandın; lastikler yola sitem etti.",
            "%d kez roket kalkışı; turbo fazla mesai yaptı.",
            "%d kez sert gaz; yakıt göstergesi gözlerini devirdi.",
            "%d kez ani gazlama; turbo nazikçe \"yavaş\" dedi."
    };
    private static final String[] SPEED_BAD = {
            "En yüksek hızın %d km/h; radar sana selam söylüyor.",
            "%d km/h; rüzgâr saçlarını taradı, radar not aldı.",
            "Son hız %d km/h; radarın objektifine poz verdin.",
            "%d km/h; hız göstergesi \"biraz yavaş\" diye fısıldadı.",
            "%d km/h; radar seni artık tanıyor.",
            "%d km/h; trafik polisi \"yine mi sen\" dedi."
    };
    private static final String[] AVG_SPEED = {
            "Ortalama %d km/h ile aktın.",
            "Ortalama %d km/h; ne çok yavaş ne çok hızlı.",
            "Ortalama %d km/h ile akıp gittin.",
            "Ortalaman %d km/h; tempo gayet yerinde.",
            "Ortalama %d km/h; trafikle dans ettin.",
            "Ortalama %d km/h; sabırlı bir sürüş."
    };

    private static String pick(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static long seed(DrivingTrip trip) {
        long seed = trip.id != 0L ? trip.id : trip.startTimeMs;
        if (seed == 0L) seed = trip.endTimeMs;
        if (seed == 0L) {
            seed = ((long) (trip.distanceKm * 1000)) ^ trip.durationSec ^ trip.score;
        }
        return seed;
    }

    private static String describe(DrivingTrip trip) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.getDefault(), "%.1f km yol yaptın", trip.distanceKm));
        if (trip.consumptionL100 > 0.0) {
            text.append(String.format(Locale.getDefault(),
                    ", yüz kilometrede ortalama %.1f litre yaktın", trip.consumptionL100));
        }
        text.append(". ");

        Random random = new Random(seed(trip));
        if (trip.harshBrakes > 0) {
            text.append(String.format(Locale.getDefault(), pick(random, BRAKES_BAD),
                    trip.harshBrakes)).append(' ');
        } else {
            text.append(pick(random, BRAKES_OK)).append(' ');
        }
        if (trip.harshAccels > 0) {
            text.append(String.format(Locale.getDefault(), pick(random, ACCELS_BAD),
                    trip.harshAccels)).append(' ');
        } else {
            text.append(pick(random, ACCELS_OK)).append(' ');
        }
        if (trip.maxSpeedKmh > 120) {
            text.append(String.format(Locale.getDefault(), pick(random, SPEED_BAD),
                    trip.maxSpeedKmh)).append(' ');
        }
        if (trip.avgSpeedKmh > 0) {
            text.append(String.format(Locale.getDefault(), pick(random, AVG_SPEED),
                    trip.avgSpeedKmh)).append(' ');
        }
        text.append("Puanın ").append(trip.score)
                .append(", notun ").append(trip.grade)
                .append(". Unvanın: ").append(trip.title).append('.');
        return text.toString();
    }

}
