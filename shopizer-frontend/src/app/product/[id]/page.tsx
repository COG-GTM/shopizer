import Link from "next/link";
import AddToCartButton from "./AddToCartButton";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface Product {
  id: number;
  description: { name: string; friendlyUrl: string; description: string };
  finalPrice: string;
  image?: { imageUrl: string };
  images?: { imageUrl: string }[];
}

async function fetchProduct(id: string): Promise<Product | null> {
  try {
    const res = await fetch(`${API_URL}/api/v1/product/${id}?store=DEFAULT&lang=en`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

export default async function ProductPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const product = await fetchProduct(id);

  if (!product) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 text-center">
        <h1 className="text-2xl font-bold text-gray-900">Product not found</h1>
        <Link href="/" className="mt-4 inline-block text-indigo-600 hover:underline">
          Back to Home
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <nav className="mb-6 text-sm text-gray-500">
        <Link href="/" className="hover:text-indigo-600">Home</Link>
        <span className="mx-2">/</span>
        <span className="text-gray-900">{product.description?.name}</span>
      </nav>

      <div className="grid gap-8 lg:grid-cols-2">
        <div className="aspect-square overflow-hidden rounded-xl bg-gray-100">
          {product.image?.imageUrl ? (
            <img
              src={product.image.imageUrl}
              alt={product.description?.name || "Product"}
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-gray-400">
              No image available
            </div>
          )}
        </div>

        <div>
          <h1 className="text-3xl font-bold text-gray-900">
            {product.description?.name}
          </h1>
          <p className="mt-4 text-3xl font-semibold text-indigo-600">
            ${product.finalPrice}
          </p>
          <div className="mt-6 prose prose-sm text-gray-600">
            <p>{product.description?.description || "No description available."}</p>
          </div>
          <AddToCartButton productId={product.id} />
        </div>
      </div>
    </div>
  );
}
