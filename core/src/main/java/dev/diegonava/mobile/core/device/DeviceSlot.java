package dev.diegonava.mobile.core.device;

import java.util.Optional;

/**
 * One device's worth of exclusive resources.
 *
 * <p>The port fields are the whole point of this type. Running two Appium sessions against two
 * devices concurrently fails in a confusing, intermittent way unless each session gets its own
 * {@code systemPort} (the UiAutomator2 server bridge) and {@code wdaLocalPort} (the WebDriverAgent
 * bridge). Appium's defaults are fixed values, so the second session quietly steals the first
 * one's channel and tests start failing on the wrong device. Deriving every port from the slot
 * index makes the collision structurally impossible rather than something to remember.
 *
 * @param index sequential slot number, starting at zero
 * @param udid device identifier, or empty to let Appium pick the only attached device
 * @param deviceName human-readable device name passed as a capability
 * @param platformVersion OS version to require, or empty for any
 * @param appiumPort the Appium server this slot talks to
 * @param systemPort Android only — the UiAutomator2 server port
 * @param wdaLocalPort iOS only — the local port forwarded to WebDriverAgent
 * @param mjpegServerPort screen-record stream port, distinct per slot
 */
public record DeviceSlot(
        int index,
        Optional<String> udid,
        String deviceName,
        Optional<String> platformVersion,
        int appiumPort,
        int systemPort,
        int wdaLocalPort,
        int mjpegServerPort) {

    public String label() {
        return udid.orElse(deviceName) + " (slot " + index + ")";
    }
}
