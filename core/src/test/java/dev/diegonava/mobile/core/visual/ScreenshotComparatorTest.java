package dev.diegonava.mobile.core.visual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure image comparison, tested without a device.
 *
 * <p>An off-by-one in an ignore-region bounds check or a tolerance applied to the wrong channel is
 * invisible on a real screenshot and obvious against a 10x10 fixture.
 */
class ScreenshotComparatorTest {

    private static final ScreenshotComparator EXACT = ScreenshotComparator.withTolerance(0);

    @Test
    @DisplayName("identical images differ nowhere and produce no diff")
    void identicalImagesMatch() {
        BufferedImage image = solid(10, 10, 0x112233);

        ImageComparison result = EXACT.compare(image, solid(10, 10, 0x112233));

        assertThat(result.differingPixels()).isZero();
        assertThat(result.comparedPixels()).isEqualTo(100);
        assertThat(result.differencePercentage()).isZero();
        assertThat(result.diff()).as("no diff image when nothing differs").isNull();
    }

    @Test
    @DisplayName("a wholly different image differs everywhere")
    void completelyDifferentImages() {
        ImageComparison result = EXACT.compare(solid(10, 10, 0x000000), solid(10, 10, 0xFFFFFF));

        assertThat(result.differingPixels()).isEqualTo(100);
        assertThat(result.differencePercentage()).isEqualTo(100d);
        assertThat(result.diff()).isNotNull();
    }

    @Test
    @DisplayName("a small colour shift is absorbed by the tolerance")
    void toleranceAbsorbsAntiAliasing() {
        BufferedImage baseline = solid(10, 10, 0x808080);
        BufferedImage candidate = solid(10, 10, 0x848484); // 4 per channel

        // Anti-aliased text is never bit-identical between renders; zero tolerance would fail
        // every single run.
        assertThat(EXACT.compare(baseline, candidate).differingPixels()).isEqualTo(100);
        assertThat(ScreenshotComparator.withTolerance(5)
                        .compare(baseline, candidate)
                        .differingPixels())
                .isZero();
    }

    @Test
    @DisplayName("tolerance is applied per channel, not to the packed value")
    void toleranceIsPerChannel() {
        // Differs by 4 in red only. A comparator that subtracted packed ints would see a huge
        // delta here and call it a difference.
        BufferedImage baseline = solid(4, 4, 0x800000);
        BufferedImage candidate = solid(4, 4, 0x840000);

        assertThat(ScreenshotComparator.withTolerance(5)
                        .compare(baseline, candidate)
                        .differingPixels())
                .isZero();
    }

    @Test
    @DisplayName("ignored regions are excluded from the comparison and from the total")
    void ignoredRegionsAreNotCompared() {
        BufferedImage baseline = solid(10, 10, 0x000000);
        BufferedImage candidate = solid(10, 10, 0x000000);
        // Change the top two rows, as a clock would.
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 10; x++) {
                candidate.setRGB(x, y, 0xFFFFFF);
            }
        }

        ImageComparison withoutIgnore = EXACT.compare(baseline, candidate);
        assertThat(withoutIgnore.differingPixels()).isEqualTo(20);

        ImageComparison withIgnore =
                EXACT.ignoring(List.of(new Rectangle(0, 0, 10, 2))).compare(baseline, candidate);

        assertThat(withIgnore.differingPixels()).isZero();
        assertThat(withIgnore.comparedPixels())
                .as("ignored pixels must leave the denominator too, or the percentage lies")
                .isEqualTo(80);
    }

    @Test
    @DisplayName("the difference percentage is of compared pixels, not of the whole image")
    void percentageIsOfComparedPixels() {
        BufferedImage baseline = solid(10, 10, 0x000000);
        BufferedImage candidate = solid(10, 10, 0x000000);
        for (int x = 0; x < 8; x++) {
            candidate.setRGB(x, 5, 0xFFFFFF);
        }

        ImageComparison result =
                EXACT.ignoring(List.of(new Rectangle(0, 0, 10, 2))).compare(baseline, candidate);

        // 8 differing out of 80 compared = 10%, not 8 out of 100.
        assertThat(result.differencePercentage()).isEqualTo(10d);
    }

    @Test
    @DisplayName("a size mismatch is rejected with an explanation, not scored")
    void sizeMismatchIsRejected() {
        assertThatThrownBy(() -> EXACT.compare(solid(10, 10, 0), solid(20, 10, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20x10")
                .hasMessageContaining("10x10")
                .hasMessageContaining("per device profile");
    }

    @Test
    @DisplayName("matches() applies the caller's tolerance to the percentage")
    void matchesAppliesPercentageTolerance() {
        BufferedImage baseline = solid(10, 10, 0x000000);
        BufferedImage candidate = solid(10, 10, 0x000000);
        for (int x = 0; x < 5; x++) {
            candidate.setRGB(x, 0, 0xFFFFFF);
        }

        ImageComparison result = EXACT.compare(baseline, candidate);

        assertThat(result.differencePercentage()).isEqualTo(5d);
        assertThat(result.matches(5d)).isTrue();
        assertThat(result.matches(4.9d)).isFalse();
    }

    @Test
    @DisplayName("an out-of-range tolerance is rejected")
    void toleranceMustBeInRange() {
        assertThatThrownBy(() -> ScreenshotComparator.withTolerance(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenshotComparator.withTolerance(256)).isInstanceOf(IllegalArgumentException.class);
    }

    private static BufferedImage solid(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
