package dev.diegonava.mobile.screens;

/**
 * Entry points into the app under test.
 *
 * <p>My Demo App opens on the catalog, not on login — login lives behind the drawer. Tests should
 * not have to know that, so {@link #launch()} is the one thing they call to get a handle on a
 * ready app.
 */
public final class App {

    private App() {}

    /** Waits for the app's first screen and returns it. */
    public static CatalogScreen launch() {
        CatalogScreen catalog = new CatalogScreen();
        catalog.awaitLoaded();
        return catalog;
    }

    /** The persistent chrome, available from any screen. */
    public static Navigation navigation() {
        return new Navigation();
    }

    /** Launches, then signs in as the standard user, landing back on the catalog. */
    public static CatalogScreen launchAndLogIn() {
        launch();
        return navigation().openLogin().loginAsValidUser();
    }
}
