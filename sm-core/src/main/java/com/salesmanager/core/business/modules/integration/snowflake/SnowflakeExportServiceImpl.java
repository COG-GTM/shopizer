package com.salesmanager.core.business.modules.integration.snowflake;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;

/**
 * Exports orders to a Snowflake data warehouse over JDBC. Orders are upserted
 * with a MERGE so replaying an export is idempotent, status changes are
 * appended to an history table.
 *
 */
@Service("snowflakeExportService")
public class SnowflakeExportServiceImpl implements SnowflakeExportService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeExportServiceImpl.class);

	static final String MERGE_ORDER_SQL = "MERGE INTO ORDERS t USING (SELECT ? AS ORDER_ID, ? AS STORE_CODE, ? AS CUSTOMER_ID, ? AS STATUS, ? AS TOTAL, ? AS CURRENCY, ? AS DATE_PURCHASED, ? AS LAST_MODIFIED) s ON t.ORDER_ID = s.ORDER_ID AND t.STORE_CODE = s.STORE_CODE WHEN MATCHED THEN UPDATE SET t.CUSTOMER_ID = s.CUSTOMER_ID, t.STATUS = s.STATUS, t.TOTAL = s.TOTAL, t.CURRENCY = s.CURRENCY, t.DATE_PURCHASED = s.DATE_PURCHASED, t.LAST_MODIFIED = s.LAST_MODIFIED WHEN NOT MATCHED THEN INSERT (ORDER_ID, STORE_CODE, CUSTOMER_ID, STATUS, TOTAL, CURRENCY, DATE_PURCHASED, LAST_MODIFIED) VALUES (s.ORDER_ID, s.STORE_CODE, s.CUSTOMER_ID, s.STATUS, s.TOTAL, s.CURRENCY, s.DATE_PURCHASED, s.LAST_MODIFIED)";

	static final String MERGE_ORDER_PRODUCT_SQL = "MERGE INTO ORDER_PRODUCTS t USING (SELECT ? AS ORDER_PRODUCT_ID, ? AS ORDER_ID, ? AS SKU, ? AS PRODUCT_NAME, ? AS QUANTITY, ? AS ONE_TIME_CHARGE) s ON t.ORDER_PRODUCT_ID = s.ORDER_PRODUCT_ID WHEN MATCHED THEN UPDATE SET t.ORDER_ID = s.ORDER_ID, t.SKU = s.SKU, t.PRODUCT_NAME = s.PRODUCT_NAME, t.QUANTITY = s.QUANTITY, t.ONE_TIME_CHARGE = s.ONE_TIME_CHARGE WHEN NOT MATCHED THEN INSERT (ORDER_PRODUCT_ID, ORDER_ID, SKU, PRODUCT_NAME, QUANTITY, ONE_TIME_CHARGE) VALUES (s.ORDER_PRODUCT_ID, s.ORDER_ID, s.SKU, s.PRODUCT_NAME, s.QUANTITY, s.ONE_TIME_CHARGE)";

	static final String INSERT_ORDER_STATUS_SQL = "INSERT INTO ORDER_STATUS_HISTORY (ORDER_ID, STORE_CODE, OLD_STATUS, NEW_STATUS, CHANGE_DATE) VALUES (?, ?, ?, ?, ?)";

	@Value("${snowflake.export.enabled:false}")
	private boolean exportEnabled;

	@Autowired(required = false)
	@Qualifier("snowflakeDataSource")
	private DataSource snowflakeDataSource;

	public SnowflakeExportServiceImpl() {
	}

	public SnowflakeExportServiceImpl(DataSource snowflakeDataSource, boolean exportEnabled) {
		this.snowflakeDataSource = snowflakeDataSource;
		this.exportEnabled = exportEnabled;
	}

	@Override
	public void exportOrder(Order order, MerchantStore store) {

		if (!enabled(order)) {
			return;
		}

		try (Connection connection = snowflakeDataSource.getConnection()) {
			mergeOrder(connection, order, store);
			mergeOrderProducts(connection, order);
		} catch (SQLException e) {
			throw new SnowflakeExportException("Could not export order id [" + order.getId() + "] to Snowflake", e);
		}

	}

	@Override
	public void exportOrderStatusChange(Order order, OrderStatus oldStatus, OrderStatus newStatus,
			MerchantStore store) {

		if (!enabled(order)) {
			return;
		}

		try (Connection connection = snowflakeDataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(INSERT_ORDER_STATUS_SQL)) {

			statement.setLong(1, order.getId());
			statement.setString(2, storeCode(order, store));
			statement.setString(3, oldStatus != null ? oldStatus.name() : null);
			statement.setString(4, newStatus != null ? newStatus.name() : null);
			statement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));

			statement.executeUpdate();

		} catch (SQLException e) {
			throw new SnowflakeExportException(
					"Could not export status change of order id [" + order.getId() + "] to Snowflake", e);
		}

	}

	private void mergeOrder(Connection connection, Order order, MerchantStore store) throws SQLException {

		try (PreparedStatement statement = connection.prepareStatement(MERGE_ORDER_SQL)) {

			statement.setLong(1, order.getId());
			statement.setString(2, storeCode(order, store));
			setLongOrNull(statement, 3, order.getCustomerId());
			statement.setString(4, order.getStatus() != null ? order.getStatus().name() : null);
			setBigDecimalOrNull(statement, 5, order.getTotal());
			statement.setString(6, order.getCurrency() != null ? order.getCurrency().getCode() : null);
			setTimestampOrNull(statement, 7, order.getDatePurchased());
			setTimestampOrNull(statement, 8, order.getLastModified());

			statement.executeUpdate();
		}

	}

	private void mergeOrderProducts(Connection connection, Order order) throws SQLException {

		if (order.getOrderProducts() == null || order.getOrderProducts().isEmpty()) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(MERGE_ORDER_PRODUCT_SQL)) {

			for (OrderProduct orderProduct : order.getOrderProducts()) {
				setLongOrNull(statement, 1, orderProduct.getId());
				statement.setLong(2, order.getId());
				statement.setString(3, orderProduct.getSku());
				statement.setString(4, orderProduct.getProductName());
				statement.setInt(5, orderProduct.getProductQuantity());
				setBigDecimalOrNull(statement, 6, orderProduct.getOneTimeCharge());
				statement.addBatch();
			}

			statement.executeBatch();
		}

	}

	private boolean enabled(Order order) {

		if (!exportEnabled) {
			LOGGER.debug("Snowflake export disabled, skipping export of order id [{}]",
					order != null ? order.getId() : null);
			return false;
		}

		if (snowflakeDataSource == null) {
			LOGGER.warn("Snowflake export is enabled but no Snowflake datasource is configured, skipping export");
			return false;
		}

		return true;
	}

	private String storeCode(Order order, MerchantStore store) {
		if (store != null) {
			return store.getCode();
		}
		return order.getMerchant() != null ? order.getMerchant().getCode() : null;
	}

	private void setLongOrNull(PreparedStatement statement, int index, Long value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.BIGINT);
		} else {
			statement.setLong(index, value);
		}
	}

	private void setBigDecimalOrNull(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.DECIMAL);
		} else {
			statement.setBigDecimal(index, value);
		}
	}

	private void setTimestampOrNull(PreparedStatement statement, int index, Date value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.TIMESTAMP);
		} else {
			statement.setTimestamp(index, new Timestamp(value.getTime()));
		}
	}

}
