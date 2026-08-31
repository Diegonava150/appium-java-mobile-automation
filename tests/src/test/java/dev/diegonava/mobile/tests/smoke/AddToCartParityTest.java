package dev.diegonava.mobile.tests.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.core.junit.SessionScope;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CartScreen;
import dev.diegonava.mobile.screens.CatalogScreen;
import dev.diegonava.mobile.screens.ItemDetailsScreen;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The core commerce path, run unchanged on both platforms. */
@MobileTest(session = SessionScope.PER_CLASS)
@Epic("Commerce")
@Feature("Cart")
@DisplayName("Add to cart")
class AddToCartParityTest {

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("an item added from its detail screen appears in the cart")
    void addingAnItemPutsItInTheCart() {
        CatalogScreen catalog = App.launch();

        ItemDetailsScreen details = catalog.openFirstItem();
        details.addToCart();

        CartScreen cart = App.navigation().openCart();

        assertThat(cart.itemCount())
                .as("the cart should hold exactly the one item added")
                .isEqualTo(1);
        assertThat(cart.isCheckoutAvailable())
                .as("a non-empty cart should offer checkout")
                .isTrue();
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("the quantity stepper carries through to the cart")
    void quantityStepperIsRespected() {
        CatalogScreen catalog = App.launch();

        ItemDetailsScreen details = catalog.openFirstItem();
        details.increaseQuantity(2);

        assertThat(details.quantity())
                .as("two taps on the stepper should take the quantity from 1 to 3")
                .isEqualTo(3);

        details.addToCart();

        CartScreen cart = App.navigation().openCart();
        assertThat(cart.itemCount())
                .as("three of one product is still one line in the cart")
                .isEqualTo(1);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("removing the only item empties the cart")
    void removingTheLastItemEmptiesTheCart() {
        CatalogScreen catalog = App.launch();

        catalog.openFirstItem().addToCart();

        CartScreen cart = App.navigation().openCart();
        assertThat(cart.itemCount()).isEqualTo(1);

        cart.removeFirstItem();

        assertThat(cart.isEmpty())
                .as("an emptied cart should show its go-shopping call to action")
                .isTrue();
    }
}
