package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.*;

import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.orderstatus.OrderStatusHistory;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderCollectionOrderingIntegrationTest extends ServicesTestSupport {
    @PersistenceContext private EntityManager em;

    @Test
    void reloadingOrderSortsTotalsBySortOrderAndHistoryById() {
        MerchantStore store = em.createQuery("from MerchantStore where code='DEFAULT'", MerchantStore.class)
                .getSingleResult();
        Order order = new Order();
        order.setMerchant(store);
        order.setCustomerEmailAddress("ordering@example.test");
        Billing billing = new Billing();
        billing.setFirstName("Order");
        billing.setLastName("Sorting");
        billing.setCountry(store.getCountry());
        order.setBilling(billing);
        for (int sort : new int[] {30, 10, 20}) {
            OrderTotal total = new OrderTotal();
            total.setOrder(order);
            total.setSortOrder(sort);
            total.setOrderTotalCode("total" + sort);
            total.setValue(BigDecimal.valueOf(sort));
            order.getOrderTotal().add(total);
        }
        em.persist(order);
        OrderStatusHistory first = new OrderStatusHistory();
        first.setOrder(order);
        first.setDateAdded(new java.util.Date());
        em.persist(first);
        OrderStatusHistory second = new OrderStatusHistory();
        second.setOrder(order);
        second.setDateAdded(new java.util.Date());
        em.persist(second);
        order.getOrderHistory().add(second);
        order.getOrderHistory().add(first);
        em.flush();
        Long id = order.getId();
        em.clear();
        Order reloaded = em.find(Order.class, id);
        assertEquals(List.of(10, 20, 30), reloaded.getOrderTotal().stream()
                .map(OrderTotal::getSortOrder).toList());
        assertEquals(List.of(first.getId(), second.getId()), reloaded.getOrderHistory().stream()
                .map(OrderStatusHistory::getId).toList());
    }
}
