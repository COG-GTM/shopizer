"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { getCart, type Cart } from "@/lib/api";

export default function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const code = localStorage.getItem("shopizer_cart");
    if (!code) {
      setLoading(false);
      return;
    }
    getCart(code)
      .then(setCart)
      .catch(() => setCart(null))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <p className="text-gray-500">Loading cart...</p>
      </div>
    );
  }

  if (!cart || !cart.products?.length) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <h1 className="text-2xl font-bold text-gray-900">Your cart is empty</h1>
        <Link
          href="/"
          className="mt-4 inline-block text-indigo-600 hover:underline"
        >
          Continue Shopping
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="mb-8 text-2xl font-bold text-gray-900">Shopping Cart</h1>

      <div className="divide-y divide-gray-200 border-y border-gray-200">
        {cart.products.map((item) => (
          <div key={item.id} className="flex items-center gap-4 py-4">
            <div className="flex-1">
              <h3 className="text-sm font-medium text-gray-900">
                {item.product?.description?.name || `Product #${item.product?.id}`}
              </h3>
              <p className="text-sm text-gray-500">Qty: {item.quantity}</p>
            </div>
            <p className="text-sm font-semibold text-gray-900">${item.subTotal}</p>
          </div>
        ))}
      </div>

      <div className="mt-6 flex items-center justify-between">
        <span className="text-lg font-semibold text-gray-900">Total</span>
        <span className="text-lg font-bold text-indigo-600">${cart.total}</span>
      </div>

      <Link
        href="/checkout"
        className="mt-6 block w-full rounded-md bg-indigo-600 px-6 py-3 text-center text-sm font-semibold text-white hover:bg-indigo-500"
      >
        Proceed to Checkout
      </Link>
    </div>
  );
}
