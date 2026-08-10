package dev.diegonava.mobile.core.junit;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.driver.DriverManager;
import io.appium.java_client.AppiumDriver;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.openqa.selenium.OutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures device evidence at the moment a test fails.
 *
 * <p>This is a {@link TestExecutionExceptionHandler}, not a {@code TestWatcher}, and the
 * distinction is the whole reason it works. {@code TestWatcher.testFailed} fires after every
 * {@code AfterEachCallback} has run — by which point {@code DriverExtension} has already quit the
 * session and there is nothing left to screenshot. The exception handler fires the instant the
 * test method throws, while the device is still sitting on the failing screen.
 *
 * <p>What a mobile failure can produce that a web one cannot is the point of the whole category:
 * the screenshot, the native view hierarchy, and the device log are three independent views of the
 * same moment. The AI triage layer in v2 consumes exactly this directory.
 */
public final class FailureArtifactExtension
        implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FailureArtifactExtension.class);

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        capture(context);
        throw throwable;
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        capture(context);
        throw throwable;
    }

    private void capture(ExtensionContext context) {
        FrameworkConfig config = FrameworkConfig.get();
        if (!config.captureOnFailure() || !DriverManager.isOpen()) {
            return;
        }

        Path dir = config.artifactsDir().resolve(folderNameFor(context));
        AppiumDriver driver = DriverManager.driver();

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Could not create the artifact directory {}: {}", dir, e.getMessage());
            return;
        }

        // Each capture is independent: a device that will not screenshot might still
        // hand over its page source, and a partial bundle beats no bundle.
        write(dir.resolve("screenshot.png"), () -> driver.getScreenshotAs(OutputType.BYTES));
        write(dir.resolve("page-source.xml"), () -> driver.getPageSource().getBytes(StandardCharsets.UTF_8));

        if (DriverManager.platform().isAndroid()) {
            write(dir.resolve("logcat.txt"), () -> {
                StringBuilder sb = new StringBuilder();
                driver.manage().logs().get("logcat").forEach(entry -> sb.append(entry.toString())
                        .append(System.lineSeparator()));
                return sb.toString().getBytes(StandardCharsets.UTF_8);
            });
        }

        log.info("Failure artifacts written to {}", dir);
    }

    private interface Capture {
        byte[] run() throws Exception;
    }

    private void write(Path target, Capture capture) {
        try {
            Files.write(target, capture.run());
        } catch (Exception e) {
            log.warn("Could not capture {}: {}", target.getFileName(), e.getMessage());
        }
    }

    /**
     * A directory name a human can scan: {@code AddToCartParityTest.quantityStepperIsRespected}.
     *
     * <p>JUnit's unique id is precise but unreadable once it is filesystem-escaped, and these
     * folders exist specifically to be opened by someone diagnosing a red build.
     */
    private static String folderNameFor(ExtensionContext context) {
        String type = context.getTestClass().map(Class::getSimpleName).orElse("UnknownClass");
        String method = context.getTestMethod().map(Method::getName).orElse("unknownMethod");
        return sanitise(
                type + "." + method + "." + DriverManager.platform().name().toLowerCase(Locale.ROOT));
    }

    private static String sanitise(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
