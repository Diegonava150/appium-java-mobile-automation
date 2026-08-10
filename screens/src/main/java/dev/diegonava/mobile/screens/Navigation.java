package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.time.Duration;
import org.openqa.selenium.By;

/**
 * The persistent app chrome: the drawer menu and the cart affordance.
 *
 * <p>This is the one place in the week-one scope where the platforms genuinely diverge, and it is
 * worth being precise about why. Android renders a header with a hamburger and a cart badge; iOS
 * renders a bottom tab bar. That is not two names for one control — it is two different navigation
 * patterns, each idiomatic to its platform, and the app is right to implement both.
 *
 * <p>Per ADR-003, that divergence is absorbed here, at the locator, rather than leaking into tests
 * as {@code if (isAndroid())}. Tests say {@code navigation.openCart()} on both platforms.
 */
public final class Navigation extends BaseScreen {

    private static final By ANDROID_MENU_BUTTON = id("open menu");
    private static final By IOS_MENU_TAB = id("tab bar option menu");

    private static final By ANDROID_CART_BADGE = id("cart badge");
    private static final By IOS_CART_TAB = id("tab bar option cart");

    private static final By CLOSE_MENU = id("close menu");
    private static final By MENU_CATALOG = id("menu item catalog");
    private static final By MENU_LOGIN = id("menu item log in");
    private static final By MENU_LOGOUT = id("menu item log out");
    private static final By MENU_BIOMETRICS = id("menu item biometrics");
    private static final By MENU_RESET = id("menu item reset app");

    @Override
    protected By rootLocator() {
        return menuButton();
    }

    private By menuButton() {
        return isAndroid() ? ANDROID_MENU_BUTTON : IOS_MENU_TAB;
    }

    private By cartButton() {
        return isAndroid() ? ANDROID_CART_BADGE : IOS_CART_TAB;
    }

    public Navigation openMenu() {
        tap(menuButton());
        return this;
    }

    public Navigation closeMenu() {
        tap(CLOSE_MENU);
        return this;
    }

    public CartScreen openCart() {
        tap(cartButton());
        CartScreen cart = new CartScreen();
        cart.awaitLoaded();
        return cart;
    }

    /**
     * Whether the cart affordance shows a non-zero item count.
     *
     * <p>Android exposes the count in the badge's accessibility label; iOS's tab bar does not
     * expose a count at all. Rather than fake a shared number, this returns presence only, and
     * tests that care about the exact quantity assert it inside the cart where both platforms
     * agree. Asserting on what both platforms actually expose is what keeps a parity suite honest.
     */
    public boolean isCartAffordanceVisible() {
        return isDisplayed(cartButton(), Duration.ofSeconds(5));
    }

    public LoginScreen openLogin() {
        openMenu();
        tap(MENU_LOGIN);
        LoginScreen login = new LoginScreen();
        login.awaitLoaded();
        return login;
    }

    public CatalogScreen openCatalog() {
        openMenu();
        tap(MENU_CATALOG);
        CatalogScreen catalog = new CatalogScreen();
        catalog.awaitLoaded();
        return catalog;
    }

    public void logOut() {
        openMenu();
        tap(MENU_LOGOUT);
    }

    public void openBiometrics() {
        openMenu();
        tap(MENU_BIOMETRICS);
    }

    public void resetApp() {
        openMenu();
        tap(MENU_RESET);
    }
}
