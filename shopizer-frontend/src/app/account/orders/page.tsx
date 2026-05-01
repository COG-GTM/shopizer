"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { getOrders, type Order } from "@/lib/api";

export default function OrdersPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) {
      router.push("/login");
      return;
    }
    getOrders(user.token)
      .then((data) => setOrders(data.orders || []))
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  }, [user, router]);

  if (!user) return null;

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="mb-8 text-2xl font-bold text-gray-900">Order History</h1>

      {loading ? (
        <p className="text-gray-500">Loading orders...</p>
      ) : orders.length === 0 ? (
        <p className="text-gray-500">No orders found.</p>
      ) : (
        <div className="divide-y divide-gray-200 border-y border-gray-200">
          {orders.map((order) => (
            <div key={order.id} className="flex items-center justify-between py-4">
              <div>
                <p className="text-sm font-medium text-gray-900">
                  Order #{order.id}
                </p>
                <p className="text-xs text-gray-500">{order.datePurchased}</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-semibold text-gray-900">
                  {order.currency} {order.total}
                </p>
                <span className="inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                  {order.orderStatus}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
