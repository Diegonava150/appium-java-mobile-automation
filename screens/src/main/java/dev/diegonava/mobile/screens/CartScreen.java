package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** The shopping cart. */
public final class CartScreen extends BaseScreen {

    private static final By ROOT = id("cart screen");
    private static final By PRODUCT_ROW = id("product row");
    private static final By REMOVE_ITEM = id("remove item");
    private static final By GO_SHOPPING = id("Go Shopping button");
    private static final By CHECKOUT = id("Proceed To Checkout button");
    private static final By FOOTER = id("checkout footer");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    public List<WebElement> rows() {
        return driver.findElements(PRODUCT_ROW);
    }

    public int itemCount() {
        return rows().size();
    }

    public boolean isEmpty() {
        // The empty cart shows a call to action rather than an empty list, so absence of
        // rows alone would also be true while the screen is still rendering.
        return isDisplayed(GO_SHOPPING, Duration.ofSeconds(5));
    }

    public CartScreen removeFirstItem() {
        tap(REMOVE_ITEM);
        return this;
    }

    public boolean isCheckoutAvailable() {
        return isDisplayed(FOOTER, Duration.ofSeconds(5)) && isDisplayed(CHECKOUT, Duration.ofSeconds(5));
    }

    public void proceedToCheckout() {
        tap(CHECKOUT);
    }

    public CatalogScreen goShopping() {
        tap(GO_SHOPPING);
        CatalogScreen catalog = new CatalogScreen();
        catalog.awaitLoaded();
        return catalog;
    }
}
