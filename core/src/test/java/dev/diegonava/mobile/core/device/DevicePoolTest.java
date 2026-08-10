package dev.diegonava.mobile.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Unit tests for slot allocation.
 *
 * <p>Port collision between parallel Appium sessions is the bug this whole type exists to prevent,
 * and it is exactly the kind of thing that is painful to diagnose on a device but trivial to pin
 * down here. These run in milliseconds with no emulator attached.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class DevicePoolTest {

    @AfterEach
    void clearOverrides() {
        List.of(
                        "mobile.platform",
                        "mobile.device.count",
                        "mobile.android.devices",
                        "mobile.ios.devices",
                        "mobile.appium.basePort")
                .forEach(System::clearProperty);
    }

    @Test
    @DisplayName("derives a distinct port trio per slot so parallel sessions cannot collide")
    void allocatesDistinctPortsPerSlot() {
        System.setProperty("mobile.platform", "android");
        System.setProperty("mobile.device.count", "3");

        List<DeviceSlot> slots = DevicePool.buildSlots(FrameworkConfig.get());

        assertThat(slots).hasSize(3);
        assertThat(slots).extracting(DeviceSlot::appiumPort).containsExactly(4723, 4724, 4725);
        assertThat(slots).extracting(DeviceSlot::systemPort).containsExactly(8200, 8201, 8202);
        assertThat(slots).extracting(DeviceSlot::wdaLocalPort).containsExactly(8100, 8101, 8102);
        assertThat(slots).extracting(DeviceSlot::mjpegServerPort).containsExactly(9100, 9101, 9102);
    }

    @Test
    @DisplayName("no port is reused across any two slots")
    void noPortIsSharedBetweenSlots() {
        System.setProperty("mobile.platform", "android");
        System.setProperty("mobile.device.count", "8");

        List<DeviceSlot> slots = DevicePool.buildSlots(FrameworkConfig.get());

        Set<Integer> allPorts = slots.stream()
                .flatMap(slot -> java.util.stream.Stream.of(
                        slot.appiumPort(), slot.systemPort(), slot.wdaLocalPort(), slot.mjpegServerPort()))
                .collect(Collectors.toSet());

        assertThat(allPorts)
                .as("every slot's four ports must be globally unique")
                .hasSize(8 * 4);
    }

    @Test
    @DisplayName("an explicit device list decides the pool size and pins each udid")
    void explicitDeviceListWins() {
        System.setProperty("mobile.platform", "android");
        System.setProperty("mobile.device.count", "1");
        System.setProperty("mobile.android.devices", "emulator-5554, emulator-5556");

        List<DeviceSlot> slots = DevicePool.buildSlots(FrameworkConfig.get());

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).udid()).contains("emulator-5554");
        assertThat(slots.get(1).udid()).contains("emulator-5556");
        assertThat(slots.get(1).systemPort()).isEqualTo(8201);
    }

    @Test
    @DisplayName("an empty device list is valid — Appium picks the only attached device")
    void emptyDeviceListLeavesUdidUnset() {
        System.setProperty("mobile.platform", "android");
        System.setProperty("mobile.device.count", "1");

        List<DeviceSlot> slots = DevicePool.buildSlots(FrameworkConfig.get());

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).udid()).isEmpty();
    }

    @Test
    @DisplayName("the base port is configurable, and shifts every slot with it")
    void basePortIsConfigurable() {
        System.setProperty("mobile.platform", "android");
        System.setProperty("mobile.device.count", "2");
        System.setProperty("mobile.appium.basePort", "4900");

        List<DeviceSlot> slots = DevicePool.buildSlots(FrameworkConfig.get());

        assertThat(slots).extracting(DeviceSlot::appiumPort).containsExactly(4900, 4901);
    }

    @Test
    @DisplayName("a slot label identifies the device for log output")
    void slotLabelIsReadable() {
        System.setProperty("mobile.platform", "android");
        System.setProperty("mobile.android.devices", "emulator-5554");

        DeviceSlot slot = DevicePool.buildSlots(FrameworkConfig.get()).get(0);

        assertThat(slot.label()).isEqualTo("emulator-5554 (slot 0)");
    }

    @Test
    @DisplayName("an unrecognised platform fails loudly rather than defaulting")
    void unknownPlatformIsRejected() {
        System.setProperty("mobile.platform", "windows-phone");

        assertThatThrownBy(() -> FrameworkConfig.get().platform())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windows-phone");
    }
}
