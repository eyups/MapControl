package com.mapcontrol.assistant.tools;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssistantSafetyPolicyTest {

    private static Map<String, String> args(String key, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static VehicleContext parkedOn() {
        return new VehicleContext(true, 2, 0, 1, true, 20f);
    }

    private static VehicleContext driving() {
        return new VehicleContext(true, 2, 90, 5, true, 20f);
    }

    @Test
    public void sunroofIsAllowedWhenParked() {
        AssistantSafetyPolicy.Decision decision = AssistantSafetyPolicy.evaluate(
                "sunroof_set", args("action", "open"), parkedOn());
        assertEquals(AssistantSafetyPolicy.Kind.ALLOW, decision.kind);
    }

    @Test
    public void writesBlockedWhenPowerOff() {
        VehicleContext off = new VehicleContext(true, 1, 0, 1, false, 20f);
        assertEquals(AssistantSafetyPolicy.Kind.BLOCK,
                AssistantSafetyPolicy.evaluate("sunroof_set", args("action", "open"), off).kind);
    }

    @Test
    public void writesBlockedWhenNotConnected() {
        VehicleContext disconnected = new VehicleContext(false, 2, 0, 1, true, 20f);
        assertEquals(AssistantSafetyPolicy.Kind.BLOCK,
                AssistantSafetyPolicy.evaluate("door_lock_set", args("action", "lock"),
                        disconnected).kind);
    }

    @Test
    public void lockAllowedWhileMovingButUnlockBlocked() {
        assertEquals(AssistantSafetyPolicy.Kind.ALLOW,
                AssistantSafetyPolicy.evaluate("door_lock_set", args("action", "lock"),
                        driving()).kind);
        assertEquals(AssistantSafetyPolicy.Kind.BLOCK,
                AssistantSafetyPolicy.evaluate("door_lock_set", args("action", "unlock"),
                        driving()).kind);
    }

    @Test
    public void unlockAsksForConfirmationWhenParked() {
        assertEquals(AssistantSafetyPolicy.Kind.CONFIRM,
                AssistantSafetyPolicy.evaluate("door_lock_set", args("action", "unlock"),
                        parkedOn()).kind);
    }

    @Test
    public void driveModeRequiresConfirmation() {
        assertEquals(AssistantSafetyPolicy.Kind.CONFIRM,
                AssistantSafetyPolicy.evaluate("drive_mode_set", args("mode", "sport"),
                        parkedOn()).kind);
    }

    @Test
    public void seatMemoryBlockedOutsidePark() {
        assertEquals(AssistantSafetyPolicy.Kind.BLOCK,
                AssistantSafetyPolicy.evaluate("seat_memory_restore", args("slot", "1"),
                        driving()).kind);
        assertEquals(AssistantSafetyPolicy.Kind.CONFIRM,
                AssistantSafetyPolicy.evaluate("seat_memory_restore", args("slot", "1"),
                        parkedOn()).kind);
    }

    @Test
    public void allWindowsAtHighSpeedRequiresConfirmation() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("which", "all");
        args.put("action", "open");
        assertEquals(AssistantSafetyPolicy.Kind.CONFIRM,
                AssistantSafetyPolicy.evaluate("window_set", args, driving()).kind);
    }

    @Test
    public void adasToolsAreAlwaysBlocked() {
        assertTrue(AssistantSafetyPolicy.evaluate("aeb_set", null, parkedOn()).isBlocked());
        assertTrue(AssistantSafetyPolicy.evaluate("adas_set", null, parkedOn()).isBlocked());
        assertTrue(AssistantSafetyPolicy.evaluate("raw_vdbus_write", null, parkedOn()).isBlocked());
        assertTrue(AssistantSafetyPolicy.evaluate("app_uninstall", null, parkedOn()).isBlocked());
    }

    @Test
    public void readOnlyToolsAllowedWithoutVehicleLink() {
        VehicleContext unknown = VehicleContext.unknown();
        assertEquals(AssistantSafetyPolicy.Kind.ALLOW,
                AssistantSafetyPolicy.evaluate("vehicle_status", null, unknown).kind);
        assertEquals(AssistantSafetyPolicy.Kind.ALLOW,
                AssistantSafetyPolicy.evaluate("media_control", args("action", "play"), unknown).kind);
    }

    @Test
    public void unknownToolBlocked() {
        assertEquals(AssistantSafetyPolicy.Kind.BLOCK,
                AssistantSafetyPolicy.evaluate("make_coffee", null, parkedOn()).kind);
        assertEquals(AssistantSafetyPolicy.Kind.BLOCK,
                AssistantSafetyPolicy.evaluate("", null, parkedOn()).kind);
    }
}
