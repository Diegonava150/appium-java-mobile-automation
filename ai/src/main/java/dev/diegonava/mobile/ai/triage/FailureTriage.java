package dev.diegonava.mobile.ai.triage;

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
import dev.diegonava.mobile.ai.AiCredentials;
import dev.diegonava.mobile.ai.PageSourceDigest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Reads a run's failure-artifact bundles and writes a triage note beside each one.
 *
 * <p>Deliberately an offline tool rather than a JUnit extension. Triage is not part of the test
 * lifecycle: it does not need a device, it must not slow a suite down, and it must not be able to
 * change whether a test passed. Running it after the fact also means it works on artifacts
 * downloaded from CI — which is the case that matters, because the failures worth triaging are
 * the ones that happened on hardware you do not have. Six rounds of iOS failures in this project
 * were diagnosed exactly that way, from a machine with no Mac attached.
 *
 * <p>Run it with {@code ./gradlew triageFailures}. With no key it prints what it would have
 * examined and exits zero — it is a diagnostic aid, and a diagnostic aid that fails the build
 * because a credential is missing is a liability.
 *
 * <p>Notes are written as {@code triage.md} inside each bundle, next to the evidence they cite.
 * They are never uploaded anywhere and never gate anything. See ADR-009.
 */
public final class FailureTriage {

    private static final int LOGCAT_TAIL_LINES = 400;
    private static final int HIERARCHY_CHARS = 12_000;

    private final AiCredentials credentials;
    private final String model;

    public FailureTriage(AiCredentials credentials, String model) {
        this.credentials = credentials;
        this.model = model;
    }

    /**
     * @param args {@code <artifacts-dir> [model]}
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: FailureTriage <artifacts-dir> [model]");
            System.exit(2);
        }
        Path artifacts = Path.of(args[0]);
        String model = args.length > 1 ? args[1] : "claude-opus-5";

        List<FailureBundle> bundles = FailureBundle.discover(artifacts);
        if (bundles.isEmpty()) {
            System.out.println("No failure artifacts under " + artifacts.toAbsolutePath() + ". Nothing to triage.");
            return;
        }

        AiCredentials credentials = AiCredentials.fromEnvironment();
        if (!credentials.isPresent()) {
            System.out.println(
                    "No Anthropic API key, so no triage was performed. " + bundles.size() + " bundle(s) are waiting:");
            bundles.forEach(bundle -> System.out.println("  " + bundle.name() + describeEvidence(bundle)));
            System.out.println("Set ANTHROPIC_API_KEY and run again. The artifacts are readable by hand either way.");
            return;
        }

        new FailureTriage(credentials, model).triageAll(bundles);
    }

    private void triageAll(List<FailureBundle> bundles) {
        List<String> failed = new ArrayList<>();
        for (FailureBundle bundle : bundles) {
            try {
                Diagnosis diagnosis = triage(bundle);
                Path note = noteFor(bundle);
                Files.writeString(note, render(bundle, diagnosis), StandardCharsets.UTF_8);
                System.out.println("Triaged " + bundle.name() + " -> " + note);
            } catch (RuntimeException | IOException e) {
                // One bundle failing must not cost the diagnosis of the other nineteen.
                failed.add(bundle.name() + ": " + e);
            }
        }
        failed.forEach(message -> System.out.println("Could not triage " + message));
    }

    Diagnosis triage(FailureBundle bundle) throws IOException {
        List<ContentBlockParam> content = new ArrayList<>();

        Optional<Path> screenshot = bundle.screenshot();
        if (screenshot.isPresent()) {
            content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                            .data(Base64.getEncoder().encodeToString(Files.readAllBytes(screenshot.get())))
                            .build())
                    .build()));
        }

        String hierarchy = bundle.pageSource().isPresent()
                ? PageSourceDigest.of(
                        Files.readString(bundle.pageSource().get(), StandardCharsets.UTF_8), HIERARCHY_CHARS)
                : "";

        content.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(TriagePrompt.user(
                        bundle.testId(), readMessage(bundle), hierarchy, bundle.logcatTail(LOGCAT_TAIL_LINES)))
                .build()));

        StructuredMessageCreateParams<Diagnosis> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(TriagePrompt.SYSTEM)
                .outputConfig(Diagnosis.class)
                .addUserMessageOfBlockParams(content)
                .build();

        return client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The model returned no structured diagnosis"));
    }

    private AnthropicClient client() {
        return AnthropicOkHttpClient.builder()
                .apiKey(credentials.require())
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    /** The failure message, if the run happened to record one next to the artifacts. */
    private static String readMessage(FailureBundle bundle) {
        return bundle.screenshot()
                .or(bundle::pageSource)
                .map(Path::getParent)
                .map(dir -> dir.resolve("failure.txt"))
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return "";
                    }
                })
                .orElse("");
    }

    private static Path noteFor(FailureBundle bundle) {
        Path dir = bundle.screenshot()
                .or(bundle::pageSource)
                .or(bundle::logcat)
                .orElseThrow()
                .getParent();
        return dir.resolve("triage.md");
    }

    static String render(FailureBundle bundle, Diagnosis diagnosis) {
        return """
                # Triage — %s

                **Category:** %s
                **Confidence:** %s

                ## What appears to have happened

                %s

                ## Evidence cited

                %s

                ## Suggested next step

                %s

                ---
                Generated by the optional AI layer (ADR-009). This is a hypothesis, not a finding:
                the evidence it cites is in this directory, and it is the evidence that decides.
                """.formatted(
                        bundle.name(),
                        diagnosis.category(),
                        diagnosis.confidence(),
                        diagnosis.summary(),
                        diagnosis.evidence(),
                        diagnosis.suggestedNextStep());
    }

    private static String describeEvidence(FailureBundle bundle) {
        List<String> parts = new ArrayList<>();
        bundle.screenshot().ifPresent(p -> parts.add("screenshot"));
        bundle.pageSource().ifPresent(p -> parts.add("hierarchy"));
        bundle.logcat().ifPresent(p -> parts.add("logcat"));
        return "  (" + String.join(", ", parts) + ")";
    }

    /** The shape a triage note has to have, so it cannot come back as an unstructured opinion. */
    public record Diagnosis(
            @JsonPropertyDescription(
                    "One of: app-defect, broken-locator, timing, environment, obstruction, inconclusive.")
            String category,

            @JsonPropertyDescription("One of: high, medium, low.")
            String confidence,

            @JsonPropertyDescription("Two or three sentences on what appears to have happened.")
            String summary,

            @JsonPropertyDescription(
                    "The specific things in the screenshot, hierarchy or log that support this. Say plainly if the evidence is thin.")
            String evidence,

            @JsonPropertyDescription("What a person should do next. Never 'retry the test'.")
            String suggestedNextStep) {}
}
