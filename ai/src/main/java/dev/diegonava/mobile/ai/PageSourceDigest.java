package dev.diegonava.mobile.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reduces a page source to the part of it that could possibly identify an element.
 *
 * <p>A React Native screen's XML dump runs to tens of thousands of characters, most of it layout
 * containers with nothing but {@code bounds} and {@code class}. Sending it whole is expensive and,
 * more to the point, worse: the identifying attributes get buried among hundreds of indistinguishable
 * {@code ViewGroup} nodes.
 *
 * <p>So keep only the nodes carrying an identifier a permitted locator could actually use, and cap
 * the result. The cap is stated in the output rather than applied silently — a truncated hierarchy
 * that looks complete is how you end up debugging a confident answer about an element that was cut
 * off the bottom.
 */
public final class PageSourceDigest {

    /**
     * Attributes worth keeping a node for. Deliberately the identifier attributes and the two text
     * attributes: text is not a locator this framework will accept, but it is what lets the model
     * match what it sees in the screenshot to a node in the tree.
     */
    private static final Pattern IDENTIFYING =
            Pattern.compile("(resource-id|content-desc|accessibility-id|name|label|text)=\"[^\"]+\"");

    private PageSourceDigest() {}

    public static String of(String pageSource, int maxCharacters) {
        if (pageSource == null || pageSource.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String node : split(pageSource)) {
            String trimmed = node.strip();
            if (!trimmed.isEmpty() && IDENTIFYING.matcher(trimmed).find()) {
                kept.add(trimmed);
            }
        }
        String digest = String.join(System.lineSeparator(), kept);
        if (digest.length() <= maxCharacters) {
            return digest;
        }
        int dropped = digest.length() - maxCharacters;
        return digest.substring(0, maxCharacters)
                + System.lineSeparator()
                + "<!-- truncated: %d further characters omitted -->".formatted(dropped);
    }

    /**
     * One node per line.
     *
     * <p>Android's dump arrives pretty-printed; iOS's often arrives as a single line. Splitting on
     * the tag boundary handles both without needing to know which platform produced it.
     */
    private static List<String> split(String pageSource) {
        return List.of(pageSource.replace("><", ">\n<").split("\\R"));
    }
}
