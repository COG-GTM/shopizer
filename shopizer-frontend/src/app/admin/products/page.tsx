"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { api, type ProductList } from "@/lib/api";

export default function AdminProductsPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [products, setProducts] = useState<ProductList | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user || user.role !== "admin") {
      router.push("/admin/login");
      return;
    }
    api<ProductList>("/api/v1/products?store=DEFAULT&lang=en&count=50", {
      token: user.token,
    })
      .then(setProducts)
      .catch(() => setProducts(null))
      .finally(() => setLoading(false));
  }, [user, router]);

  if (!user || user.role !== "admin") return null;

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Products</h1>
      </div>

      {loading ? (
        <p className="text-gray-500">Loading products...</p>
      ) : !products?.products?.length ? (
        <p className="text-gray-500">No products found.</p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                  ID
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                  Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">
                  Price
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {products.products.map((p) => (
                <tr key={p.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm text-gray-500">{p.id}</td>
                  <td className="px-4 py-3 text-sm font-medium text-gray-900">
                    {p.description?.name}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">
                    ${p.finalPrice}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
