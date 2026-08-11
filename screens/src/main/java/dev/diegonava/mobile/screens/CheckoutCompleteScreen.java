package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import org.openqa.selenium.By;

/** Order confirmation — the end of the deepest journey in the app. */
public final class CheckoutCompleteScreen extends BaseScreen {

    private static final By ROOT = id("checkout complete screen");
    private static final By CONTINUE_SHOPPING = id("Continue Shopping button");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    public CatalogScreen continueShopping() {
        scrollAndTap(CONTINUE_SHOPPING);
        CatalogScreen catalog = new CatalogScreen();
        catalog.awaitLoaded();
        return catalog;
    }
}
