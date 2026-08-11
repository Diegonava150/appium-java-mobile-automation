package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.time.Duration;
import org.openqa.selenium.By;

/**
 * Shipping address, the first step of checkout.
 *
 * <p>The asterisks in these testIDs are literal — the app names the field after its visible
 * label, required marker included.
 */
public final class CheckoutAddressScreen extends BaseScreen {

    private static final By ROOT = id("checkout address screen");
    private static final By FULL_NAME = id("Full Name* input field");
    private static final By ADDRESS_1 = id("Address Line 1* input field");
    private static final By ADDRESS_2 = id("Address Line 2 input field");
    private static final By CITY = id("City* input field");
    private static final By STATE = id("State/Region input field");
    private static final By ZIP = id("Zip Code* input field");
    private static final By COUNTRY = id("Country* input field");
    private static final By TO_PAYMENT = id("To Payment button");

    private static final By FULL_NAME_ERROR = id("Full Name*-error-message");
    private static final By ZIP_ERROR = id("Zip Code*-error-message");

    @Override
    protected By rootLocator() {
        return ROOT;
    }

    /** Fills every required field with valid values. */
    public CheckoutAddressScreen fillRequiredFields(Address address) {
        scrollAndType(FULL_NAME, address.fullName());
        scrollAndType(ADDRESS_1, address.addressLine1());
        scrollAndType(CITY, address.city());
        scrollAndType(ZIP, address.zipCode());
        scrollAndType(COUNTRY, address.country());
        return this;
    }

    public CheckoutAddressScreen fillOptionalFields(String addressLine2, String stateOrRegion) {
        scrollAndType(ADDRESS_2, addressLine2);
        scrollAndType(STATE, stateOrRegion);
        return this;
    }

    public CheckoutPaymentScreen continueToPayment() {
        scrollAndTap(TO_PAYMENT);
        CheckoutPaymentScreen payment = new CheckoutPaymentScreen();
        payment.awaitLoaded();
        return payment;
    }

    /** Submits without filling anything, to exercise the validation path. */
    public CheckoutAddressScreen submitExpectingValidationErrors() {
        scrollAndTap(TO_PAYMENT);
        return this;
    }

    public boolean hasFullNameError() {
        return isDisplayed(FULL_NAME_ERROR, Duration.ofSeconds(5));
    }

    public boolean hasZipCodeError() {
        return isDisplayed(ZIP_ERROR, Duration.ofSeconds(5));
    }

    /** A shipping address. A record keeps the call site readable at seven fields. */
    public record Address(String fullName, String addressLine1, String city, String zipCode, String country) {

        public static Address sample() {
            return new Address("Diego Navarro", "1 Test Street", "Bogota", "110111", "Colombia");
        }
    }
}
