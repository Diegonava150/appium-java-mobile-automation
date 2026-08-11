package dev.diegonava.mobile.screens;

import static dev.diegonava.mobile.core.ui.Locators.id;

import dev.diegonava.mobile.core.ui.BaseScreen;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** Order review, the last step before the order is placed. */
public final class CheckoutReviewOrderScreen extends BaseScreen {

    private static final By ROOT = id("checkout review order screen");
    private static final By PRODUCT_ROW = id("product row");
    private static final By DELIVERY_ADDRESS = id("checkout delivery address");
    private static final By PAYMENT_INFO = id("checkout payment info");
    private static final By DELIVERY_DETAILS = id("checkout delivery details");
    private static final By FOOTER = id("checkout footer");
    private static final By PLACE_ORDER = id("Place Order button");

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

    public String deliveryAddress() {
        return textOf(scrollUntilVisible(DELIVERY_ADDRESS));
    }

    public String paymentInfo() {
        return textOf(scrollUntilVisible(PAYMENT_INFO));
    }

    public String deliveryDetails() {
        return textOf(scrollUntilVisible(DELIVERY_DETAILS));
    }

    public String orderTotal() {
        return textOf(scrollUntilVisible(FOOTER));
    }

    public CheckoutCompleteScreen placeOrder() {
        scrollAndTap(PLACE_ORDER);
        CheckoutCompleteScreen complete = new CheckoutCompleteScreen();
        complete.awaitLoaded();
        return complete;
    }
}
