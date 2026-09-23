package com.salesmanager.test.integration.snowflake;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.salesmanager.core.business.modules.integration.snowflake.SnowflakeExportService;
import com.salesmanager.core.business.modules.integration.snowflake.SnowflakeExportServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.reference.currency.Currency;

public class SnowflakeExportServiceImplTest {

	private DataSource dataSource;
	private Connection connection;
	private PreparedStatement statement;
	private MerchantStore store;

	@Before
	public void setUp() throws Exception {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(PreparedStatement.class);
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);

		store = new MerchantStore();
		store.setCode("DEFAULT");
	}

	private SnowflakeExportService service(boolean enabled) {
		return new SnowflakeExportServiceImpl(dataSource, enabled);
	}

	private Order order() {
		Order order = new Order();
		order.setId(42L);
		order.setStatus(OrderStatus.ORDERED);
		order.setTotal(new BigDecimal("199.99"));
		order.setCustomerId(7L);
		order.setDatePurchased(new Date());
		order.setMerchant(store);

		Currency currency = new Currency();
		currency.setCurrency(java.util.Currency.getInstance("USD"));
		order.setCurrency(currency);

		OrderProduct orderProduct = new OrderProduct();
		orderProduct.setId(11L);
		orderProduct.setSku("SKU-1");
		orderProduct.setProductName("Product one");
		orderProduct.setProductQuantity(2);
		orderProduct.setOneTimeCharge(new BigDecimal("99.99"));

		Set<OrderProduct> orderProducts = new HashSet<OrderProduct>();
		orderProducts.add(orderProduct);
		order.setOrderProducts(orderProducts);

		return order;
	}

	@Test
	public void exportOrderDoesNothingWhenDisabled() throws Exception {
		service(false).exportOrder(order(), store);
		verifyNoInteractions(dataSource);
	}

	@Test
	public void exportOrderStatusChangeDoesNothingWhenDisabled() throws Exception {
		service(false).exportOrderStatusChange(order(), OrderStatus.ORDERED, OrderStatus.PROCESSED, store);
		verifyNoInteractions(dataSource);
	}

	@Test
	public void exportOrderMergesOrderAndLineItems() throws Exception {
		service(true).exportOrder(order(), store);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(connection, times(2)).prepareStatement(sql.capture());
		assertEquals(true, sql.getAllValues().get(0).startsWith("MERGE INTO ORDERS"));
		assertEquals(true, sql.getAllValues().get(1).startsWith("MERGE INTO ORDER_PRODUCTS"));

		verify(statement).setLong(1, 42L);
		verify(statement).setString(2, "DEFAULT");
		verify(statement).setLong(3, 7L);
		verify(statement).setString(4, OrderStatus.ORDERED.name());
		verify(statement).setBigDecimal(5, new BigDecimal("199.99"));
		verify(statement).setString(6, "USD");

		verify(statement).setString(3, "SKU-1");
		verify(statement).setString(4, "Product one");
		verify(statement).setInt(5, 2);
		verify(statement).setBigDecimal(6, new BigDecimal("99.99"));
		verify(statement).addBatch();
		verify(statement).executeBatch();
		verify(statement).executeUpdate();
		verify(connection).close();
	}

	@Test
	public void exportOrderStatusChangeInsertsHistory() throws Exception {
		service(true).exportOrderStatusChange(order(), OrderStatus.ORDERED, OrderStatus.PROCESSED, store);

		verify(connection)
				.prepareStatement(eq("INSERT INTO ORDER_STATUS_HISTORY (ORDER_ID, STORE_CODE, OLD_STATUS, NEW_STATUS, "
						+ "CHANGE_DATE) VALUES (?, ?, ?, ?, ?)"));
		verify(statement).setLong(1, 42L);
		verify(statement).setString(2, "DEFAULT");
		verify(statement).setString(3, OrderStatus.ORDERED.name());
		verify(statement).setString(4, OrderStatus.PROCESSED.name());
		verify(statement).setTimestamp(eq(5), any(Timestamp.class));
		verify(statement).executeUpdate();
	}

	@Test
	public void exportIsSkippedWhenNoDatasourceIsConfigured() throws Exception {
		new SnowflakeExportServiceImpl(null, true).exportOrder(order(), store);

		verify(statement, never()).setLong(anyInt(), anyLong());
	}

}
