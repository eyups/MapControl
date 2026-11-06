package com.eyups.roaddeck.assistant.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LocalCommandParserTest {

    @Test
    public void parsesSunroofVariants() {
        LocalCommandParser.Command c = LocalCommandParser.parse("cam tavanı aç");
        assertNotNull(c);
        assertEquals("sunroof_set", c.tool);
        assertEquals("open", c.args.get("action"));
        assertEquals("close", LocalCommandParser.parse("tavanı kapat").args.get("action"));
        assertEquals("vent", LocalCommandParser.parse("cam tavanı havalandırma").args.get("action"));
    }

    @Test
    public void parsesSeatAndSteering() {
        LocalCommandParser.Command seat = LocalCommandParser.parse("yolcu koltuğunu ısıt");
        assertEquals("seat_heat_set", seat.tool);
        assertEquals("passenger", seat.args.get("seat"));
        assertEquals("steering_heat_set", LocalCommandParser.parse("direksiyon kapat").tool);
    }

    @Test
    public void parsesMediaAndVolume() {
        assertEquals("media_control", LocalCommandParser.parse("müzik çal").tool);
        assertEquals("pause", LocalCommandParser.parse("müziği durdur").args.get("action"));
        assertEquals("volume_set", LocalCommandParser.parse("sesi aç").tool);
        assertEquals("up", LocalCommandParser.parse("sesi aç").args.get("action"));
    }

    @Test
    public void parsesStatusAndUnknown() {
        assertEquals("vehicle_status", LocalCommandParser.parse("araç durumu").tool);
        assertEquals("vehicle_status", LocalCommandParser.parse("hız kaç").tool);
        assertNull(LocalCommandParser.parse("bugün hava nasıl"));
    }
}
