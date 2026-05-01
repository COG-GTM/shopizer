import Link from "next/link";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface ProductDesc {
  name: string;
  friendlyUrl: string;
}

interface ProductItem {
  id: number;
  description: ProductDesc;
  finalPrice: string;
  image?: { imageUrl: string };
}

interface CategoryDesc {
  name: string;
  friendlyUrl: string;
}

interface CategoryItem {
  id: number;
  code: string;
  description: CategoryDesc;
}

async function fetchProducts(): Promise<ProductItem[]> {
  try {
    const res = await fetch(`${API_URL}/api/v1/products?store=DEFAULT&lang=en&count=12`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data.products || [];
  } catch {
    return [];
  }
}

async function fetchCategories(): Promise<CategoryItem[]> {
  try {
    const res = await fetch(`${API_URL}/api/v1/category?store=DEFAULT&lang=en`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

export default async function HomePage() {
  const [products, categories] = await Promise.all([
    fetchProducts(),
    fetchCategories(),
  ]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <section className="mb-12 rounded-2xl bg-gradient-to-r from-indigo-600 to-purple-600 px-8 py-16 text-white">
        <h1 className="text-4xl font-bold tracking-tight">Welcome to Shopizer</h1>
        <p className="mt-4 text-lg text-indigo-100">
          Discover our curated collection of products
        </p>
        <Link
          href="#products"
          className="mt-6 inline-block rounded-md bg-white px-6 py-3 text-sm font-semibold text-indigo-600 hover:bg-indigo-50"
        >
          Shop Now
        </Link>
      </section>

      {categories.length > 0 && (
        <section className="mb-12">
          <h2 className="mb-6 text-2xl font-bold text-gray-900">Categories</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {categories.map((cat) => (
              <Link
                key={cat.id}
                href={`/category/${cat.code}`}
                className="rounded-lg border border-gray-200 p-4 text-center hover:border-indigo-300 hover:shadow-sm transition"
              >
                <span className="text-sm font-medium text-gray-900">
                  {cat.description?.name || cat.code}
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section id="products">
        <h2 className="mb-6 text-2xl font-bold text-gray-900">Featured Products</h2>
        {products.length === 0 ? (
          <p className="text-gray-500">
            No products available. Start the backend at port 8080 to see products.
          </p>
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {products.map((product) => (
              <Link
                key={product.id}
                href={`/product/${product.id}`}
                className="group rounded-lg border border-gray-200 p-4 hover:shadow-md transition"
              >
                {product.image?.imageUrl && (
                  <div className="mb-3 aspect-square overflow-hidden rounded-md bg-gray-100">
                    <img
                      src={product.image.imageUrl}
                      alt={product.description?.name || "Product"}
                      className="h-full w-full object-cover group-hover:scale-105 transition"
                    />
                  </div>
                )}
                <h3 className="text-sm font-medium text-gray-900">
                  {product.description?.name}
                </h3>
                <p className="mt-1 text-sm font-semibold text-indigo-600">
                  ${product.finalPrice}
                </p>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
