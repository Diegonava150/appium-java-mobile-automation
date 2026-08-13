package dev.diegonava.mobile.tests.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.junit.Flaky;
import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CartScreen;
import dev.diegonava.mobile.screens.CatalogScreen;
import dev.diegonava.mobile.screens.CheckoutAddressScreen;
import dev.diegonava.mobile.screens.CheckoutCompleteScreen;
import dev.diegonava.mobile.screens.CheckoutPaymentScreen;
import dev.diegonava.mobile.screens.CheckoutReviewOrderScreen;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deepest journey in the app, end to end: catalog to order confirmation.
 *
 * <p>Runs unchanged on both platforms. Long forms need scrolling, and that goes through W3C
 * pointer gestures rather than {@code UiScrollable} — the usual Android answer is the
 * {@code -android uiautomator} strategy, which ADR-003 bans and which has no iOS equivalent
 * anyway, so leaning on it grows a platform branch at every form.
 */
@MobileTest
@Epic("Commerce")
@Feature("Checkout")
@DisplayName("Purchase flow")
class PurchaseFlowTest {

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("a signed-in user can buy an item end to end")
    void completePurchase() {
        CatalogScreen catalog = App.launchAndLogIn();

        catalog.openFirstItem().addToCart();

        CartScreen cart = App.navigation().openCart();
        assertThat(cart.itemCount()).isEqualTo(1);

        CheckoutAddressScreen address = cart.proceedToCheckout();
        CheckoutPaymentScreen payment = address.fillRequiredFields(CheckoutAddressScreen.Address.sample())
                .continueToPayment();

        CheckoutReviewOrderScreen review =
                payment.fillCard(CheckoutPaymentScreen.Card.sample()).reviewOrder();

        assertThat(review.itemCount())
                .as("the review step should show the item that was actually in the cart")
                .isEqualTo(1);
        assertThat(review.deliveryAddress())
                .as("the address entered two screens ago should be carried through")
                .contains("Diego Navarro");

        CheckoutCompleteScreen complete = review.placeOrder();

        assertThat(complete.isDisplayed())
                .as("placing the order should reach the confirmation screen")
                .isTrue();
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("checkout is gated on being signed in")
    void checkoutRequiresAuthentication() {
        CatalogScreen catalog = App.launch();
        catalog.openFirstItem().addToCart();

        CartScreen cart = App.navigation().openCart();

        assertThat(cart.proceedToCheckoutExpectingLogin().isDisplayed())
                .as("an anonymous user should be diverted to login rather than into checkout")
                .isTrue();
    }

    @Flaky(
            maxAttempts = 2,
            reason = "heaviest test in the suite - login, catalog, cart and checkout in one - and on"
                    + " a shared two-core CI emulator the drawer animation plus the login round-trip"
                    + " intermittently outruns even the 40s CI element timeout. Seen twice on API 34,"
                    + " never on API 31, never locally. Environment speed, not app behaviour.",
            expires = "2026-09-30")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the address form refuses to advance while required fields are empty")
    void addressFormValidatesRequiredFields() {
        CatalogScreen catalog = App.launchAndLogIn();
        catalog.openFirstItem().addToCart();

        CheckoutAddressScreen address = App.navigation().openCart().proceedToCheckout();

        address.submitExpectingValidationErrors();

        assertThat(address.hasFullNameError())
                .as("an empty required name should be reported on the field")
                .isTrue();
        assertThat(address.isDisplayed())
                .as("a form with errors should stay put rather than advancing to payment")
                .isTrue();
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("continuing to shop after an order returns to a usable catalog")
    void continueShoppingAfterOrder() {
        CatalogScreen catalog = App.launchAndLogIn();
        catalog.openFirstItem().addToCart();

        CheckoutCompleteScreen complete = App.navigation()
                .openCart()
                .proceedToCheckout()
                .fillRequiredFields(CheckoutAddressScreen.Address.sample())
                .continueToPayment()
                .fillCard(CheckoutPaymentScreen.Card.sample())
                .reviewOrder()
                .placeOrder();

        CatalogScreen back = complete.continueShopping();

        assertThat(back.visibleItemCount())
                .as("the catalog should be usable again after an order")
                .isPositive();
    }
}
