package dev.diegonava.mobile.core.junit;

/**
 * How long one Appium session lives.
 *
 * <p>Session creation is the dominant cost of the iOS lane: 14 sessions accounted for 58-63% of a
 * 36 minute run, and roughly two thirds of each session is spent attaching to WebDriverAgent —
 * work that is identical every time and is thrown away when the session ends. Reusing a session
 * across a class removes that repetition; it does not make any individual session faster, and
 * nothing here tries to.
 *
 * <p>The trade is isolation, so it is opt-in per class rather than a default. See
 * {@link #PER_CLASS} for what a class is promising when it chooses it.
 */
public enum SessionScope {

    /**
     * A new session per test method. The default, and the only choice that is free of assumptions.
     *
     * <p>Appium's own reset (this framework leaves {@code noReset} and {@code fullReset} at
     * {@code false}) reinstalls the app between sessions, so every test starts against an app with
     * no history — no signed-in user, no cart, no cached anything.
     */
    PER_TEST,

    /**
     * One session for the whole class, with the app reset to a clean state between tests.
     *
     * <p>The reset is a real one — {@link dev.diegonava.mobile.core.device.AppLifecycle#resetToCleanState()}
     * removes and reinstalls the app, which is what Appium's between-session reset does anyway. A
     * cold restart would not be enough: {@code AppLifecycle.coldRestart()} exists precisely to
     * preserve whatever the app persists, so a class relying on it would leak a signed-in session
     * or a filled cart into the next test.
     *
     * <p>What a class does give up:
     *
     * <ul>
     *   <li>Device-level state outside the app — permission grants, clipboard, orientation,
     *       simulated conditions — carries over, because only the app is reinstalled.
     *   <li>A session that dies takes the rest of the class with it. The extension reopens one
     *       rather than cascading failures, but the first casualty is still a real failure.
     *   <li>The first test in the class pays for the session; the timing is no longer comparable
     *       between the first test and the rest.
     * </ul>
     *
     * <p>So this is wrong for a class that tests the app's process lifecycle or an install itself
     * — the upgrade suite, cold-start measurement, backgrounding and process death. Those want the
     * session and the install to be exactly what they say they are.
     */
    PER_CLASS
}
