package dev.diegonava.mobile.core.visual;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.driver.DriverManager;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.openqa.selenium.OutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshot comparison against a per-device baseline.
 *
 * <p>Per device, not per app. This is the part of mobile visual testing that has no real web
 * equivalent and the part most often got wrong: a screenshot is a function of resolution, density,
 * notch or cutout shape, system font scale and OS version, so one golden image cannot serve a
 * fleet. A baseline captured on a Pixel at 420dpi is simply a different picture from the same
 * screen on a tablet, and a framework that compares them reports a hundred per cent difference and
 * teaches everyone to ignore it.
 *
 * <p>So baselines are filed under a device profile, and a size mismatch is an explicit error rather
 * than a score.
 */
public final class VisualRegression {

    private static final Logger log = LoggerFactory.getLogger(VisualRegression.class);

    private final ScreenshotComparator comparator;
    private final Path baselineDirectory;
    private final Path outputDirectory;
    private final String profile;
    private final boolean updating;
    private final double tolerancePercentage;

    private VisualRegression(
            ScreenshotComparator comparator,
            Path baselineDirectory,
            Path outputDirectory,
            String profile,
            boolean updating,
            double tolerancePercentage) {
        this.comparator = comparator;
        this.baselineDirectory = baselineDirectory;
        this.outputDirectory = outputDirectory;
        this.profile = profile;
        this.updating = updating;
        this.tolerancePercentage = tolerancePercentage;
    }

    /**
     * @param profile the device profile these baselines belong to, e.g. {@code android-api36-420dpi}
     * @param statusBarHeightPx excluded from comparison — the clock and battery change every run,
     *     so leaving the status bar in means every comparison fails on the time
     */
    public static VisualRegression forProfile(String profile, int statusBarHeightPx, int screenWidthPx) {
        FrameworkConfig config = FrameworkConfig.get();
        return new VisualRegression(
                ScreenshotComparator.withTolerance(config.integer("mobile.visual.channelTolerance", 8))
                        .ignoring(List.of(new Rectangle(0, 0, screenWidthPx, statusBarHeightPx))),
                Path.of(config.string("mobile.visual.baselines", "src/test/resources/visual-baselines"))
                        .toAbsolutePath(),
                config.artifactsDir().resolve("visual"),
                profile,
                config.bool("mobile.visual.update", false),
                config.optional("mobile.visual.maxDifferencePercent")
                        .map(Double::parseDouble)
                        .orElse(0.5d));
    }

    /**
     * Compares the current screen against its baseline.
     *
     * @return the comparison, or empty when a baseline was written rather than compared
     */
    public java.util.Optional<ImageComparison> check(String name) {
        BufferedImage screenshot = capture();
        Path baselineFile = baselineDirectory.resolve(profile).resolve(name + ".png");

        if (updating || !Files.exists(baselineFile)) {
            write(baselineFile, screenshot);
            log.warn(
                    "{} baseline for '{}' on profile '{}' — no comparison made this run",
                    updating ? "Rewrote the" : "Recorded a new",
                    name,
                    profile);
            return java.util.Optional.empty();
        }

        BufferedImage baseline = read(baselineFile);
        ImageComparison comparison = comparator.compare(baseline, screenshot);

        if (!comparison.matches(tolerancePercentage)) {
            // Write all three so a human can see what changed rather than reading a percentage.
            write(outputDirectory.resolve(name + "-baseline.png"), baseline);
            write(outputDirectory.resolve(name + "-actual.png"), screenshot);
            if (comparison.diff() != null) {
                write(outputDirectory.resolve(name + "-diff.png"), comparison.diff());
            }
            log.error("Visual difference on '{}': {} — images written to {}", name, comparison, outputDirectory);
        }
        return java.util.Optional.of(comparison);
    }

    public double tolerancePercentage() {
        return tolerancePercentage;
    }

    public boolean isUpdating() {
        return updating;
    }

    /** A device profile string that changes whenever the picture would. */
    public static String profileOf(String platform, String osVersion, int densityDpi, int widthPx, int heightPx) {
        return "%s-%s-%ddpi-%dx%d"
                .formatted(platform.toLowerCase(Locale.ROOT), osVersion, densityDpi, widthPx, heightPx);
    }

    private static BufferedImage capture() {
        byte[] png = DriverManager.driver().getScreenshotAs(OutputType.BYTES);
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new IllegalStateException("Could not decode the screenshot", e);
        }
    }

    private static BufferedImage read(Path file) {
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the baseline at " + file, e);
        }
    }

    private static void write(Path file, BufferedImage image) {
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file, e);
        }
    }
}
