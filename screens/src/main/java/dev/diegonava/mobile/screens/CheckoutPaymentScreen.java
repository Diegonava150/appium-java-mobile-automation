package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.time.Duration;
import org.openqa.selenium.By;

/** Card details, the second step of checkout. */
public final class CheckoutPaymentScreen extends BaseScreen {

    private static final By ROOT = id("checkout payment screen");
    private static final By FULL_NAME = id("Full Name* input field");
    private static final By CARD_NUMBER = id("Card Number* input field");
    private static final By EXPIRY = id("Expiration Date* input field");
    private static final By SECURITY_CODE = id("Security Code* input field");
    private static final By REVIEW_ORDER = id("Review Order button");

    private static final By BILLING_SAME_AS_SHIPPING =
            id("checkbox for My billing address is the same as my shipping address.");

    private static final By CARD_NUMBER_ERROR = id("Card Number*-error-message");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    public CheckoutPaymentScreen fillCard(Card card) {
        scrollAndType(FULL_NAME, card.nameOnCard());
        scrollAndType(CARD_NUMBER, card.number());
        scrollAndType(EXPIRY, card.expiry());
        scrollAndType(SECURITY_CODE, card.securityCode());
        return this;
    }

    /**
     * Ticks "billing address is the same as shipping".
     *
     * <p>Worth having as its own step: unticking it reveals a second address form, which is the
     * kind of conditional layout that a scroll-and-tap flow gets wrong if it assumes a fixed page.
     */
    public CheckoutPaymentScreen useShippingAddressForBilling() {
        scrollAndTap(BILLING_SAME_AS_SHIPPING);
        return this;
    }

    public CheckoutReviewOrderScreen reviewOrder() {
        scrollAndTap(REVIEW_ORDER);
        CheckoutReviewOrderScreen review = new CheckoutReviewOrderScreen();
        review.awaitLoaded();
        return review;
    }

    public CheckoutPaymentScreen submitExpectingValidationErrors() {
        scrollAndTap(REVIEW_ORDER);
        return this;
    }

    public boolean hasCardNumberError() {
        return isDisplayed(CARD_NUMBER_ERROR, Duration.ofSeconds(5));
    }

    /** Test card details. The app accepts any well-formed values. */
    public record Card(String nameOnCard, String number, String expiry, String securityCode) {

        public static Card sample() {
            return new Card("Diego Navarro", "4111111111111111", "03/28", "123");
        }
    }
}
