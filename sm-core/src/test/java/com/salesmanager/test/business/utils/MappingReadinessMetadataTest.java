package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.common.description.Description;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.connection.AbstractUserConnectionWithCompositeKey;
import com.salesmanager.core.model.customer.connection.UserConnection;
import com.salesmanager.core.model.user.User;

class MappingReadinessMetadataTest {

    @Test
    void descriptionRemainsMappedSuperclassWithoutEntityInheritance() {
        assertNotNull(Description.class.getDeclaredAnnotation(MappedSuperclass.class));
        assertNull(Description.class.getDeclaredAnnotation(Inheritance.class));
    }

    @Test
    void userConnectionOwnsTheOriginalTableAndUniqueConstraint() {
        assertNull(AbstractUserConnectionWithCompositeKey.class.getDeclaredAnnotation(Table.class));
        Table table = UserConnection.class.getDeclaredAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("USERCONNECTION", table.name());
        assertEquals(1, table.uniqueConstraints().length);
        assertArrayEquals(new String[] {"userId", "providerId", "userRank"},
                table.uniqueConstraints()[0].columnNames());
    }

    static Stream<Arguments> associations() {
        return Stream.of(
                Arguments.of(Customer.class, "groups", "CUSTOMER_GROUP", "CUSTOMER_ID", "GROUP_ID"),
                Arguments.of(Product.class, "categories", "PRODUCT_CATEGORY", "PRODUCT_ID", "CATEGORY_ID"),
                Arguments.of(User.class, "groups", "USER_GROUP", "USER_ID", "GROUP_ID"));
    }

    @ParameterizedTest(name = "{0}.{1} retains writable join columns")
    @MethodSource("associations")
    void associationNamesArePreservedAndBothSidesAreWritable(Class<?> entity, String fieldName,
            String tableName, String ownerColumn, String inverseColumn) throws Exception {
        Field field = entity.getDeclaredField(fieldName);
        assertNotNull(field.getDeclaredAnnotation(ManyToMany.class));
        JoinTable table = field.getDeclaredAnnotation(JoinTable.class);
        assertNotNull(table);
        assertEquals(tableName, table.name());
        assertEquals(1, table.joinColumns().length);
        assertEquals(1, table.inverseJoinColumns().length);
        assertWritableColumn(ownerColumn, table.joinColumns()[0]);
        assertWritableColumn(inverseColumn, table.inverseJoinColumns()[0]);
    }

    private static void assertWritableColumn(String name, JoinColumn column) {
        assertEquals(name, column.name());
        assertTrue(column.insertable(), name + " must be insertable");
        assertTrue(column.updatable(), name + " must be updatable");
    }
}
