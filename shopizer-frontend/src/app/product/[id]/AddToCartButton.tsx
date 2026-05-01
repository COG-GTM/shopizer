"use client";

import { useState } from "react";
import { addToCart } from "@/lib/api";

export default function AddToCartButton({ productId }: { productId: number }) {
  const [qty, setQty] = useState(1);
  const [loading, setLoading] = useState(false);
  const [added, setAdded] = useState(false);

  const handleAdd = async () => {
    setLoading(true);
    try {
      const cartCode = localStorage.getItem("shopizer_cart") || undefined;
      const cart = await addToCart({ product: productId, quantity: qty, code: cartCode });
      localStorage.setItem("shopizer_cart", cart.code);
      setAdded(true);
      setTimeout(() => setAdded(false), 2000);
    } catch (err) {
      console.error("Failed to add to cart:", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mt-8 flex items-center gap-4">
      <div className="flex items-center rounded-md border border-gray-300">
        <button
          onClick={() => setQty(Math.max(1, qty - 1))}
          className="px-3 py-2 text-gray-600 hover:text-gray-900"
        >
          -
        </button>
        <span className="px-4 py-2 text-sm font-medium">{qty}</span>
        <button
          onClick={() => setQty(qty + 1)}
          className="px-3 py-2 text-gray-600 hover:text-gray-900"
        >
          +
        </button>
      </div>
      <button
        onClick={handleAdd}
        disabled={loading}
        className="rounded-md bg-indigo-600 px-6 py-3 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
      >
        {loading ? "Adding..." : added ? "Added!" : "Add to Cart"}
      </button>
    </div>
  );
}
