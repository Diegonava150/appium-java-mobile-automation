package dev.diegonava.mobile.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.diegonava.mobile.core.ai.LocatorDebtLedger;
import dev.diegonava.mobile.core.ai.LocatorHealingEvent;
import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.junit.CurrentTest;
import dev.diegonava.mobile.core.ui.LocatorFallback;
import io.appium.java_client.AppiumDriver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks Claude to find an element that a locator missed, and books the answer as debt.
 *
 * <p>Off unless {@code -Dmobile.ai.locatorFallback=true} is set <em>and</em> a key is present.
 * Defaulting to on would mean that anyone who happens to have {@code ANTHROPIC_API_KEY} exported
 * gets network calls out of a test run they did not ask to make network calls in, which is exactly
 * the kind of surprise this framework criticises elsewhere.
 *
 * <p>Three constraints keep this from becoming the self-healing anti-pattern:
 *
 * <ol>
 *   <li>It only ever runs after a locator has already failed. Nothing on the happy path calls it.
 *   <li>Its answer is filtered through {@link LocatorSuggestion#toBy()}, so it cannot introduce a
 *       locator strategy the build bans — including the XPath a model is most likely to reach for.
 *   <li>Every success is recorded in {@link LocatorDebtLedger} with the screenshot that justified
 *       it, and unrecognised debt fails the build. The run is rescued; the change is not hidden.
 * </ol>
 *
 * <p>It never throws. A fallback that fails must leave the original locator failure standing —
 * that error is the one worth reading, and burying it under an HTTP timeout would be a strictly
 * worse outcome than having no fallback at all.
 *
 * <p>See ADR-009.
 */
public final class ClaudeVisionLocator implements LocatorFallback {

    private static final Logger log = LoggerFactory.getLogger(ClaudeVisionLocator.class);

    private final AiCredentials credentials;
    private final FrameworkConfig config;
    private volatile AnthropicClient client;

    /** Called by {@link java.util.ServiceLoader}. Must stay cheap: it runs even with no key. */
    public ClaudeVisionLocator() {
        this(AiCredentials.fromEnvironment(), FrameworkConfig.get());
    }

    ClaudeVisionLocator(AiCredentials credentials, FrameworkConfig config) {
        this.credentials = credentials;
        this.config = config;
    }

    @Override
    public boolean isAvailable() {
        return config.bool("mobile.ai.locatorFallback", false) && credentials.isPresent();
    }

    @Override
    public Optional<WebElement> locate(AppiumDriver driver, By failedLocator, String description) {
        try {
            byte[] screenshot = driver.getScreenshotAs(OutputType.BYTES);
            String digest =
                    PageSourceDigest.of(driver.getPageSource(), config.integer("mobile.ai.hierarchyChars", 12_000));

            LocatorSuggestion suggestion = ask(screenshot, digest, failedLocator, description);
            Optional<By> permitted = suggestion.toBy();
            if (permitted.isEmpty()) {
                log.warn("Discarding the fallback's suggestion for '{}': {}", description, suggestion.rejection());
                return Optional.empty();
            }

            List<WebElement> found = driver.findElements(permitted.get());
            if (found.isEmpty()) {
                log.warn(
                        "The fallback suggested {} for '{}', which also matched nothing.",
                        permitted.get(),
                        description);
                return Optional.empty();
            }

            Path evidence = writeEvidence(screenshot, description);
            LocatorDebtLedger.record(new LocatorHealingEvent(
                    CurrentTest.id(),
                    description,
                    failedLocator.toString(),
                    permitted.get().toString(),
                    evidence.toString()));
            return Optional.of(found.get(0));
        } catch (RuntimeException e) {
            // Deliberately swallowed. The caller is already on a failure path and its exception
            // names the locator that broke; replacing that with "connect timed out" helps nobody.
            log.warn("The locator fallback could not run for '{}': {}", description, e.toString());
            return Optional.empty();
        }
    }

    private LocatorSuggestion ask(byte[] screenshot, String digest, By failedLocator, String description) {
        StructuredMessageCreateParams<VisionAnswer> params = MessageCreateParams.builder()
                .model(config.string("mobile.ai.model", "claude-opus-5"))
                .maxTokens(2048L)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(LocatorPrompt.SYSTEM)
                .outputConfig(VisionAnswer.class)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                                        .data(Base64.getEncoder().encodeToString(screenshot))
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(LocatorPrompt.user(failedLocator, description, digest))
                                .build())))
                .build();

        return client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text().toSuggestion())
                .findFirst()
                .orElseGet(() -> new LocatorSuggestion("", "", "the model returned no structured answer"));
    }

    private AnthropicClient client() {
        AnthropicClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                client = AnthropicOkHttpClient.builder()
                        .apiKey(credentials.require())
                        .timeout(Duration.ofSeconds(config.integer("mobile.ai.timeout.seconds", 90)))
                        .build();
            }
            return client;
        }
    }

    /**
     * The screenshot the answer was based on, kept next to the failure artifacts.
     *
     * <p>Without it the ledger entry is an assertion nobody can check. With it, reviewing a heal is
     * a matter of opening the image and seeing what the element actually became — which is how the
     * iOS "Save Password?" dialog was found from a Linux machine.
     */
    private Path writeEvidence(byte[] screenshot, String description) throws RuntimeException {
        String name = "locator-heal-%s-%s.png"
                .formatted(
                        CurrentTest.id().replaceAll("[^A-Za-z0-9._-]", "_"),
                        description.replaceAll("[^A-Za-z0-9._-]", "_").toLowerCase(Locale.ROOT));
        Path target = config.artifactsDir().resolve(name);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, screenshot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the fallback's evidence to " + target, e);
        }
        return target;
    }

    /**
     * The shape the model must answer in.
     *
     * <p>A schema rather than free text, because "parse a locator out of prose" is a source of
     * failure that structured outputs remove entirely.
     */
    record VisionAnswer(
            @JsonPropertyDescription("Exactly \"accessibility id\" or \"id\". Empty if the element cannot be located.")
            String strategy,

            @JsonPropertyDescription("The locator value. Empty if the element cannot be located.")
            String value,

            @JsonPropertyDescription("One sentence on how this element was identified, or why it could not be.")
            String reasoning) {

        LocatorSuggestion toSuggestion() {
            return new LocatorSuggestion(strategy, value, reasoning);
        }
    }
}
