package dev.diegonava.mobile.core.visual;

import java.awt.image.BufferedImage;

/**
 * The result of comparing a screenshot against its baseline.
 *
 * @param differingPixels pixels that exceeded the colour tolerance
 * @param comparedPixels pixels actually examined, excluding ignored regions
 * @param diff a visualisation of what changed, or null when nothing did
 */
public record ImageComparison(int differingPixels, int comparedPixels, BufferedImage diff) {

    public double differencePercentage() {
        return comparedPixels == 0 ? 0d : (differingPixels * 100d) / comparedPixels;
    }

    public boolean matches(double tolerancePercentage) {
        return differencePercentage() <= tolerancePercentage;
    }

    @Override
    public String toString() {
        return "%d of %d compared pixels differ (%.4f%%)"
                .formatted(differingPixels, comparedPixels, differencePercentage());
    }
}
