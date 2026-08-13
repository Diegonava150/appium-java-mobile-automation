package dev.diegonava.mobile.core.visual;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Compares two screenshots pixel by pixel, with tolerance and ignore regions.
 *
 * <p>Pure image logic with no device involved, which is what makes it testable: an off-by-one in a
 * bounds check or a tolerance that is accidentally squared is invisible on a device and obvious
 * against a fixture.
 *
 * <p>Two things make mobile visual comparison harder than the web version, and both are handled
 * here rather than papered over. Anti-aliasing and GPU differences mean identical renders are
 * rarely bit-identical, so a strict equality check produces constant false positives — hence a
 * per-channel colour tolerance. And a device's clock, battery and signal indicators change every
 * single run, so the status bar has to be excluded or every comparison fails on the time.
 */
public final class ScreenshotComparator {

    /**
     * How far apart two pixels can be, per colour channel, before they count as different.
     *
     * <p>Non-zero on purpose. Two renders of the same screen differ slightly at the edges of
     * anti-aliased text, and a tolerance of zero turns that into a failure on every run.
     */
    private final int channelTolerance;

    private final List<Rectangle> ignoredRegions;

    private ScreenshotComparator(int channelTolerance, List<Rectangle> ignoredRegions) {
        if (channelTolerance < 0 || channelTolerance > 255) {
            throw new IllegalArgumentException("Channel tolerance must be 0-255, got " + channelTolerance);
        }
        this.channelTolerance = channelTolerance;
        this.ignoredRegions = List.copyOf(ignoredRegions);
    }

    public static ScreenshotComparator withTolerance(int channelTolerance) {
        return new ScreenshotComparator(channelTolerance, List.of());
    }

    /** Regions excluded from comparison — the status bar, most importantly. */
    public ScreenshotComparator ignoring(List<Rectangle> regions) {
        return new ScreenshotComparator(channelTolerance, regions);
    }

    /**
     * @throws IllegalArgumentException when the images differ in size, which almost always means
     *     the baseline belongs to a different device rather than that the UI changed. Comparing
     *     them anyway would produce a meaningless percentage and hide the real problem.
     */
    public ImageComparison compare(BufferedImage baseline, BufferedImage candidate) {
        if (baseline.getWidth() != candidate.getWidth() || baseline.getHeight() != candidate.getHeight()) {
            throw new IllegalArgumentException("""
                    Screenshot is %dx%d but the baseline is %dx%d. That is a different device or \
                    density, not a visual change — baselines are per device profile for exactly \
                    this reason.""".formatted(
                            candidate.getWidth(), candidate.getHeight(), baseline.getWidth(), baseline.getHeight()));
        }

        int width = baseline.getWidth();
        int height = baseline.getHeight();
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int differing = 0;
        int compared = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int baselineRgb = baseline.getRGB(x, y);

                if (isIgnored(x, y)) {
                    // Grey out ignored regions so a human reading the diff can see at a glance
                    // which parts were never in scope.
                    diff.setRGB(x, y, 0x00404040);
                    continue;
                }

                compared++;
                int candidateRgb = candidate.getRGB(x, y);

                if (withinTolerance(baselineRgb, candidateRgb)) {
                    // Keep unchanged pixels visible but muted, so differences stand out.
                    diff.setRGB(x, y, dim(baselineRgb));
                } else {
                    differing++;
                    diff.setRGB(x, y, 0x00FF0000);
                }
            }
        }

        return new ImageComparison(differing, compared, differing == 0 ? null : diff);
    }

    private boolean isIgnored(int x, int y) {
        for (Rectangle region : ignoredRegions) {
            if (region.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private boolean withinTolerance(int left, int right) {
        if (left == right) {
            return true;
        }
        int deltaRed = Math.abs(((left >> 16) & 0xFF) - ((right >> 16) & 0xFF));
        int deltaGreen = Math.abs(((left >> 8) & 0xFF) - ((right >> 8) & 0xFF));
        int deltaBlue = Math.abs((left & 0xFF) - (right & 0xFF));
        return deltaRed <= channelTolerance && deltaGreen <= channelTolerance && deltaBlue <= channelTolerance;
    }

    private static int dim(int rgb) {
        int red = ((rgb >> 16) & 0xFF) / 4 + 160;
        int green = ((rgb >> 8) & 0xFF) / 4 + 160;
        int blue = (rgb & 0xFF) / 4 + 160;
        return (red << 16) | (green << 8) | blue;
    }
}
