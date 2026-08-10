package dev.diegonava.mobile.core.config;

import java.util.Locale;

/**
 * The two platforms under test.
 *
 * <p>Deliberately named {@code MobilePlatform} rather than {@code Platform} so it never collides
 * with {@code org.openqa.selenium.Platform} at an import site.
 */
public enum MobilePlatform {
    ANDROID,
    IOS;

    public boolean isAndroid() {
        return this == ANDROID;
    }

    public boolean isIos() {
        return this == IOS;
    }

    public static MobilePlatform parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Platform must be 'android' or 'ios', but was empty");
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "android" -> ANDROID;
            case "ios", "iphoneos" -> IOS;
            default ->
                throw new IllegalArgumentException("Unknown platform '" + raw + "'. Expected 'android' or 'ios'.");
        };
    }
}
