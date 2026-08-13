package dev.diegonava.mobile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageSourceDigestTest {

    private static final String ANDROID_SOURCE = """
            <hierarchy rotation="0">
              <android.widget.FrameLayout class="android.widget.FrameLayout" bounds="[0,0][1080,2400]" />
              <android.view.ViewGroup class="android.view.ViewGroup" bounds="[0,63][1080,231]" />
              <android.widget.ImageView content-desc="cart tab" bounds="[880,90][976,186]" />
              <android.widget.TextView resource-id="com.example:id/title" text="Products" bounds="[42,300][500,360]" />
              <android.view.ViewGroup class="android.view.ViewGroup" bounds="[0,231][1080,2400]" />
            </hierarchy>
            """;

    @Test
    @DisplayName("nodes carrying an identifier survive")
    void keepsIdentifiedNodes() {
        String digest = PageSourceDigest.of(ANDROID_SOURCE, 10_000);

        assertThat(digest).contains("cart tab").contains("com.example:id/title");
    }

    @Test
    @DisplayName("layout containers with nothing but bounds are dropped")
    void dropsAnonymousContainers() {
        // The reason the digest exists. On a real React Native screen these outnumber the useful
        // nodes by more than ten to one, and they are what buries the answer.
        String digest = PageSourceDigest.of(ANDROID_SOURCE, 10_000);

        assertThat(digest).doesNotContain("[0,231][1080,2400]");
        assertThat(digest.lines()).hasSize(2);
    }

    @Test
    @DisplayName("a single-line hierarchy is split, so iOS is handled too")
    void splitsSingleLineSource() {
        String oneLine = "<XCUIElementTypeOther type=\"Other\"><XCUIElementTypeButton name=\"cart tab\" "
                + "label=\"cart tab\"/><XCUIElementTypeOther type=\"Other\"/></XCUIElementTypeOther>";

        String digest = PageSourceDigest.of(oneLine, 10_000);

        assertThat(digest.lines()).hasSize(1);
        assertThat(digest).contains("cart tab");
    }

    @Test
    @DisplayName("the cap is honoured and the truncation is stated, never silent")
    void truncationIsAnnounced() {
        String digest = PageSourceDigest.of(ANDROID_SOURCE, 40);

        assertThat(digest).contains("truncated").contains("characters omitted");
        // A hierarchy that was cut off but looks complete is worse than one that is obviously
        // partial: the model answers confidently about a tree that ended early.
        assertThat(digest.lines().findFirst().orElseThrow()).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    @DisplayName("an unbroken digest carries no truncation marker")
    void noMarkerWhenItFits() {
        assertThat(PageSourceDigest.of(ANDROID_SOURCE, 10_000)).doesNotContain("truncated");
    }

    @Test
    @DisplayName("empty and null sources produce an empty digest rather than an exception")
    void handlesNothing() {
        assertThat(PageSourceDigest.of(null, 100)).isEmpty();
        assertThat(PageSourceDigest.of("", 100)).isEmpty();
        assertThat(PageSourceDigest.of("<hierarchy />", 100)).isEmpty();
    }
}
