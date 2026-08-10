package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import org.openqa.selenium.By;

/** A single product's detail screen, including the quantity stepper. */
public final class ItemDetailsScreen extends BaseScreen {

    private static final By ROOT = id("product screen");
    private static final By HEADER = id("container header");
    private static final By BACK = id("navigation back button");
    private static final By INCREMENT = id("counter plus button");
    private static final By DECREMENT = id("counter minus button");
    private static final By QUANTITY = id("counter amount");
    private static final By ADD_TO_CART = id("Add To Cart button");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    public String title() {
        return textOf(HEADER);
    }

    public int quantity() {
        return Integer.parseInt(textOf(QUANTITY).trim());
    }

    public ItemDetailsScreen increaseQuantity(int times) {
        for (int i = 0; i < times; i++) {
            tap(INCREMENT);
        }
        return this;
    }

    public ItemDetailsScreen decreaseQuantity(int times) {
        for (int i = 0; i < times; i++) {
            tap(DECREMENT);
        }
        return this;
    }

    public ItemDetailsScreen addToCart() {
        tap(ADD_TO_CART);
        return this;
    }

    public CatalogScreen goBack() {
        tap(BACK);
        CatalogScreen catalog = new CatalogScreen();
        catalog.awaitLoaded();
        return catalog;
    }
}
