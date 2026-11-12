package com.salesmanager.test.configuration;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JakartaMappingUpgradeTest {
    @Test
    void allFormerTextTypesUseLongVarchar() throws Exception {
        String[][] fields = {
            {"common.description.Description", "description"},
            {"order.OrderTotal", "text"}, {"order.orderstatus.OrderStatusHistory", "comments"},
            {"payments.Transaction", "details"}, {"system.IntegrationModule", "configDetails"},
            {"system.MerchantConfiguration", "value"}, {"system.MerchantLog", "log"},
            {"system.optin.CustomerOptin", "value"}
        };
        for (String[] field : fields) {
            var annotation = model(field[0]).getDeclaredField(field[1]).getAnnotation(JdbcTypeCode.class);
            assertNotNull(annotation, field[0] + "." + field[1]);
            assertEquals(SqlTypes.LONGVARCHAR, annotation.value(), field[0]);
        }
    }

    @Test
    void reservedValueColumnsAreQuoted() throws Exception {
        for (String entity : new String[]{"order.OrderTotal", "order.attributes.OrderAttribute",
                "system.MerchantConfiguration", "system.SystemConfiguration",
                "system.SystemNotification", "system.optin.CustomerOptin"}) {
            assertEquals("`VALUE`", model(entity).getDeclaredField("value").getAnnotation(Column.class).name(), entity);
        }
    }

    private Class<?> model(String name) throws Exception {
        return Class.forName("com.salesmanager.core.model." + name);
    }
}
