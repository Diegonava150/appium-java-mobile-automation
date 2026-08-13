package dev.diegonava.mobile.core.a11y;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Audits an Appium page source for accessibility problems.
 *
 * <p>Runs against the XML rather than a live driver, which is what makes it testable: the awkward
 * cases — a 20dp button, two controls with the same label — are trivial to write as a fixture and
 * a nuisance to reproduce on a device. It also means an audit can be re-run later against a
 * hierarchy captured by the failure-artifact extension.
 *
 * <p>Why this matters beyond compliance: the European Accessibility Act brought mobile apps into
 * scope in June 2025. But the more immediate argument is that a control with no accessibility
 * label is unusable by a screen reader <i>and</i> unreachable by this framework's own locator
 * strategy. Accessibility and testability are the same requirement seen from two sides.
 */
public final class AccessibilityAudit {

    /** Material and WCAG 2.1 minimum target size, in density-independent pixels. */
    public static final int MINIMUM_TOUCH_TARGET_DP = 48;

    private final int densityDpi;

    private AccessibilityAudit(int densityDpi) {
        this.densityDpi = densityDpi;
    }

    /**
     * @param densityDpi the device's density, needed to convert the pixel bounds in a page source
     *     into the dp that the guidance is written in. Assuming a fixed density is how an audit
     *     reports phantom failures on a tablet and misses real ones on a high-density phone.
     */
    public static AccessibilityAudit forDensity(int densityDpi) {
        if (densityDpi <= 0) {
            throw new IllegalArgumentException("Density must be positive, got " + densityDpi);
        }
        return new AccessibilityAudit(densityDpi);
    }

    public List<AccessibilityFinding> audit(String pageSource) {
        Document document = parse(pageSource);
        List<AccessibilityFinding> findings = new ArrayList<>();
        Map<String, String> labelsSeen = new HashMap<>();
        Set<String> duplicatesReported = new HashSet<>();

        NodeList all = document.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if (!isInteractive(element)) {
                continue;
            }

            String label = labelOf(element);
            String type = element.getTagName();
            String location = attribute(element, "bounds");

            if (label.isBlank()) {
                findings.add(new AccessibilityFinding(
                        AccessibilityFinding.Rule.MISSING_LABEL,
                        type,
                        "",
                        location,
                        "interactive element announces nothing to a screen reader"));
            } else {
                String previous = labelsSeen.putIfAbsent(label, type + location);
                if (previous != null && duplicatesReported.add(label)) {
                    findings.add(new AccessibilityFinding(
                            AccessibilityFinding.Rule.DUPLICATE_LABEL,
                            type,
                            label,
                            location,
                            "shares the label \"%s\" with %s".formatted(label, previous)));
                }
            }

            Bounds bounds = Bounds.parse(location);
            if (bounds != null) {
                int widthDp = toDp(bounds.width());
                int heightDp = toDp(bounds.height());
                if (widthDp < MINIMUM_TOUCH_TARGET_DP || heightDp < MINIMUM_TOUCH_TARGET_DP) {
                    findings.add(new AccessibilityFinding(
                            AccessibilityFinding.Rule.TOUCH_TARGET_TOO_SMALL,
                            type,
                            label,
                            location,
                            "%dx%ddp, below the %ddp minimum".formatted(widthDp, heightDp, MINIMUM_TOUCH_TARGET_DP)));
                }
            }
        }
        return List.copyOf(findings);
    }

    private int toDp(int pixels) {
        return Math.round(pixels * 160f / densityDpi);
    }

    /**
     * Whether an element is something a user acts on.
     *
     * <p>Only interactive elements are audited. Applying target-size and label rules to every
     * layout container would bury three real findings under four hundred irrelevant ones, and an
     * audit nobody reads is worth nothing.
     */
    private static boolean isInteractive(Element element) {
        if (Boolean.parseBoolean(attribute(element, "clickable"))
                || Boolean.parseBoolean(attribute(element, "long-clickable"))
                || Boolean.parseBoolean(attribute(element, "checkable"))) {
            return true;
        }
        String type = element.getTagName();
        return type.equals("XCUIElementTypeButton")
                || type.equals("XCUIElementTypeTextField")
                || type.equals("XCUIElementTypeSecureTextField")
                || type.equals("XCUIElementTypeSwitch");
    }

    /** The text a screen reader would announce, from whichever attribute the platform uses. */
    private static String labelOf(Element element) {
        for (String candidate : List.of("content-desc", "name", "label", "text", "value")) {
            String value = attribute(element, candidate);
            if (!value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String attribute(Element element, String name) {
        NamedNodeMap attributes = element.getAttributes();
        Node item = attributes.getNamedItem(name);
        return item == null ? "" : item.getNodeValue();
    }

    private static Document parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Page source was empty");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // The page source is machine-generated and trusted, but a parser that resolves
            // external entities is an XXE waiting to happen the first time it is pointed at
            // something else.
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse the page source as XML", e);
        }
    }

    /** Android page-source bounds, of the form {@code [left,top][right,bottom]}. */
    private record Bounds(int left, int top, int right, int bottom) {

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        static Bounds parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)]")
                    .matcher(raw);
            if (!matcher.find()) {
                return null;
            }
            return new Bounds(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)));
        }
    }
}
