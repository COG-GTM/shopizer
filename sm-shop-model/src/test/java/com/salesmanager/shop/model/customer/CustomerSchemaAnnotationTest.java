package com.salesmanager.shop.model.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;

import org.junit.Test;

import com.salesmanager.shop.model.customer.address.Address;

import io.swagger.v3.oas.annotations.media.Schema;

public class CustomerSchemaAnnotationTest {

	@Test
	public void customerEmailSchemaAndValidationAnnotationsArePresent() throws Exception {
		Field emailAddress = CustomerEntity.class.getDeclaredField("emailAddress");

		assertEquals("Customer email address. Required for registration",
				emailAddress.getAnnotation(Schema.class).description());
		assertNotNull(emailAddress.getAnnotation(NotEmpty.class));
		assertNotNull(emailAddress.getAnnotation(Email.class));
	}

	@Test
	public void persistableCustomerAndAddressFieldsHaveSchemaDescriptions() throws Exception {
		Field password = PersistableCustomer.class.getDeclaredField("password");
		Field firstName = Address.class.getDeclaredField("firstName");

		assertEquals("Customer password", password.getAnnotation(Schema.class).description());
		assertEquals("Customer billing first name", firstName.getAnnotation(Schema.class).description());
	}
}
