package dev.diegonava.mobile.core.device;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thin, targeted wrapper around {@code adb}.
 *
 * <p>Most device work goes through Appium, and should. This exists for the operations that sit
 * outside a driver session by definition — installing a different build of the app than the one
 * the session was started with, toggling radios, enrolling a fingerprint. An upgrade test in
 * particular cannot be expressed as a driver command, because the whole point is that the binary
 * changes underneath a running session.
 *
 * <p>Every call is scoped to a specific device with {@code -s}, so this stays correct when the
 * pool is running more than one.
 */
public final class AdbClient {

    private static final Logger log = LoggerFactory.getLogger(AdbClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final String serial;

    private AdbClient(String serial) {
        this.serial = serial;
    }

    /** An adb client bound to the device backing the given slot. */
    public static AdbClient forSlot(DeviceSlot slot) {
        return new AdbClient(slot.udid().orElseGet(AdbClient::soleAttachedDevice));
    }

    public static AdbClient forSerial(String serial) {
        return new AdbClient(serial);
    }

    /** Binds to the only attached device, failing loudly if that assumption does not hold. */
    public static AdbClient forSoleAttachedDevice() {
        return new AdbClient(soleAttachedDevice());
    }

    public String serial() {
        return serial;
    }

    // ------------------------------------------------------------ app packages

    /**
     * Installs over the top of an existing install, keeping app data.
     *
     * <p>{@code -r} is the entire upgrade scenario: it is what a Play Store update does, and what
     * distinguishes an upgrade test from an install test. {@code -d} additionally permits a
     * downgrade, which is how the suite resets itself to the older build.
     */
    public void installKeepingData(Path apk, boolean allowDowngrade) {
        if (!Files.exists(apk)) {
            throw new IllegalArgumentException("APK not found: " + apk);
        }
        List<String> args = new ArrayList<>(List.of("install", "-r"));
        if (allowDowngrade) {
            args.add("-d");
        }
        args.add(apk.toString());
        String output = run(args.toArray(String[]::new));
        if (!output.contains("Success")) {
            throw new IllegalStateException("adb install failed for %s:%n%s".formatted(apk.getFileName(), output));
        }
        log.info("Installed {} over the existing app on {}", apk.getFileName(), serial);
    }

    public void uninstall(String appPackage) {
        String output = run("uninstall", appPackage);
        log.info("Uninstalled {} from {}: {}", appPackage, serial, output.strip());
    }

    public boolean isInstalled(String appPackage) {
        return run("shell", "pm", "list", "packages", appPackage).contains("package:" + appPackage);
    }

    /** The {@code versionName} the package manager reports — the assertion target of an upgrade test. */
    public String installedVersionName(String appPackage) {
        String dump = run("shell", "dumpsys", "package", appPackage);
        return dump.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("versionName="))
                .map(line -> line.substring("versionName=".length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No versionName for " + appPackage + " on " + serial));
    }

    public void clearAppData(String appPackage) {
        run("shell", "pm", "clear", appPackage);
    }

    // -------------------------------------------------------------- conditions

    public void setAirplaneMode(boolean enabled) {
        run("shell", "cmd", "connectivity", "airplane-mode", enabled ? "enable" : "disable");
    }

    public void setWifi(boolean enabled) {
        run("shell", "svc", "wifi", enabled ? "enable" : "disable");
    }

    public void setMobileData(boolean enabled) {
        run("shell", "svc", "data", enabled ? "enable" : "disable");
    }

    // ------------------------------------------------------------------ runner

    public String run(String... args) {
        List<String> command = new ArrayList<>();
        command.add(adbExecutable());
        command.add("-s");
        command.add(serial);
        command.addAll(Arrays.asList(args));

        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!process.waitFor(DEFAULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("adb timed out: " + String.join(" ", args));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "adb %s exited %d:%n%s".formatted(String.join(" ", args), process.exitValue(), output));
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Could not run adb. Is the Android SDK platform-tools on PATH?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running adb", e);
        }
    }

    private static String soleAttachedDevice() {
        String output = runGlobal("devices");
        List<String> serials = output.lines()
                .skip(1)
                .map(String::strip)
                .filter(line -> line.endsWith("\tdevice"))
                .map(line -> line.substring(0, line.indexOf('\t')))
                .toList();

        if (serials.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one attached device but found %d %s. Set mobile.android.devices explicitly."
                            .formatted(serials.size(), serials));
        }
        return serials.get(0);
    }

    private static String runGlobal(String... args) {
        List<String> command = new ArrayList<>();
        command.add(adbExecutable());
        command.addAll(Arrays.asList(args));
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(30, TimeUnit.SECONDS);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Could not run adb. Is the Android SDK platform-tools on PATH?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running adb", e);
        }
    }

    /**
     * Resolves the adb binary.
     *
     * <p>Prefers {@code ANDROID_HOME}/{@code ANDROID_SDK_ROOT} over a bare {@code adb} on PATH,
     * because the SDK location is the thing CI actually sets and a PATH lookup is the thing that
     * silently finds the wrong one.
     */
    private static String adbExecutable() {
        String sdk = System.getenv("ANDROID_HOME");
        if (sdk == null || sdk.isBlank()) {
            sdk = System.getenv("ANDROID_SDK_ROOT");
        }
        if (sdk != null && !sdk.isBlank()) {
            Path candidate = Path.of(sdk, "platform-tools", isWindows() ? "adb.exe" : "adb");
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return "adb";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }
}
