package dev.diegonava.mobile.core.device;

import dev.diegonava.mobile.core.driver.DriverManager;
import io.appium.java_client.remote.SupportsRotation;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Screen orientation, via the driver rather than adb so it works on both platforms. */
public final class Display {

    private static final Logger log = LoggerFactory.getLogger(Display.class);

    private Display() {}

    private static SupportsRotation rotation() {
        return (SupportsRotation) DriverManager.driver();
    }

    public static ScreenOrientation orientation() {
        return rotation().getOrientation();
    }

    /**
     * Rotates the device.
     *
     * <p>On Android a rotation destroys and recreates the current Activity, so anything the app
     * failed to put in its saved instance state is silently lost. It is one of the oldest and most
     * persistent bug classes on the platform, it has no web analogue at all, and it is trivially
     * cheap to test — which is a strange combination of facts for something so widely skipped.
     */
    public static void rotateTo(ScreenOrientation orientation) {
        log.info("Rotating to {}", orientation);
        rotation().rotate(orientation);
    }

    public static void landscape() {
        rotateTo(ScreenOrientation.LANDSCAPE);
    }

    public static void portrait() {
        rotateTo(ScreenOrientation.PORTRAIT);
    }

    /**
     * Attempts a rotation, reporting whether the foreground app allowed it.
     *
     * <p>An app that declares a fixed {@code android:screenOrientation} refuses to rotate, and
     * Appium surfaces that as a thrown {@code InvalidElementStateException} rather than a return
     * value. That is an awkward shape to assert against when "it refused" is the expected and
     * correct answer, so this turns the refusal into a boolean.
     *
     * @return true if the device is now in the requested orientation
     */
    public static boolean tryRotateTo(ScreenOrientation orientation) {
        try {
            rotateTo(orientation);
            return orientation() == orientation;
        } catch (WebDriverException e) {
            log.info("Rotation to {} was refused: {}", orientation, e.getMessage());
            return false;
        }
    }
}
