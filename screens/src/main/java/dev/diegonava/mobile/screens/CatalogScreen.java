package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** The product catalog — the app's landing screen after login. */
public final class CatalogScreen extends BaseScreen {

    private static final By ROOT = id("products screen");
    private static final By ITEM = id("store item");
    private static final By SORT = id("sort button");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    public List<WebElement> items() {
        return allVisible(ITEM);
    }

    public int visibleItemCount() {
        return items().size();
    }

    /**
     * Opens the item at {@code index} among those currently on screen.
     *
     * <p>Index rather than name on purpose: the catalog's contents are the app's data, not the
     * test's, and a test that hard-codes "Sauce Labs Backpack" fails for a reason that has nothing
     * to do with the behaviour it was written to cover.
     */
    public ItemDetailsScreen openItem(int index) {
        List<WebElement> items = items();
        if (index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException(
                    "Requested catalog item %d but only %d are visible".formatted(index, items.size()));
        }
        // Located through a container lookup, so it has to be confirmed interactive before the
        // tap. On iOS that lookup accepts presence, and a present-but-unlaid-out row swallows
        // the tap silently.
        awaitClickable(items.get(index)).click();
        ItemDetailsScreen details = new ItemDetailsScreen();
        details.awaitLoaded();
        return details;
    }

    public ItemDetailsScreen openFirstItem() {
        return openItem(0);
    }

    public void openSortMenu() {
        tap(SORT);
    }
}
