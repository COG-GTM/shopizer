package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.salesmanager.core.business.utils.ProductPriceUtils;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class Java21PricingCompatibilityTest {
    private final ProductPriceUtils prices = new ProductPriceUtils();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "10.005", "1234.567", "-99.99", "999999999.99"})
    void replacementAmountFormatterPreservesLegacyOutput(String amount) throws Exception {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);
        assertEquals(prices.getAdminFormatedAmount(null, value), prices.getStringAmount(value));
    }

    @ParameterizedTest
    @CsvSource({"100,50,50", "3,2,33", "100,99.99,0", "100,0.01,99"})
    void discountKeepsFloatConversionAndIntegerTruncation(String regular, String special, int percent)
            throws Exception {
        ProductAvailability availability = new ProductAvailability();
        availability.setRegion("*");
        ProductPrice price = new ProductPrice();
        price.setDefaultPrice(true);
        price.setProductPriceAmount(new BigDecimal(regular));
        price.setProductPriceSpecialAmount(new BigDecimal(special));
        price.setProductAvailability(availability);
        availability.getPrices().add(price);
        var result = prices.getFinalPrice(availability);
        assertTrue(result.isDiscounted());
        assertEquals(percent, result.getDiscountPercent());
        assertEquals(0, new BigDecimal(special).compareTo(result.getFinalPrice()));
        assertEquals(0, new BigDecimal(regular).compareTo(result.getOriginalPrice()));
    }
}
