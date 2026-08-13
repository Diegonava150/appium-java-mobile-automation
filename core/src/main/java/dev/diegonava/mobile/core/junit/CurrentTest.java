package dev.diegonava.mobile.core.junit;

/**
 * Which test is running on this thread.
 *
 * <p>Set by {@link DriverExtension} for the duration of each test. It exists so that things
 * happening deep inside a screen object — a healed locator, an accessibility finding — can be
 * attributed to a test without every method signature growing a {@code testId} parameter it does
 * not otherwise care about.
 *
 * <p>Thread-scoped rather than static because the suite runs one test per device slot in parallel;
 * a single shared field would attribute one test's findings to another the moment two devices are
 * in play.
 */
public final class CurrentTest {

    private static final ThreadLocal<String> ID = new ThreadLocal<>();

    private CurrentTest() {}

    public static void set(String testId) {
        ID.set(testId);
    }

    public static void clear() {
        ID.remove();
    }

    /** The running test, or {@code "unknown"} outside a test — never null. */
    public static String id() {
        String id = ID.get();
        return id == null ? "unknown" : id;
    }
}
