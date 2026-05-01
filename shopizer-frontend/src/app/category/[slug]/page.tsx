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

interface ProductList {
  products: ProductItem[];
  recordsTotal: number;
  totalPages: number;
}

async function fetchCategoryProducts(code: string): Promise<ProductList> {
  try {
    const res = await fetch(
      `${API_URL}/api/v1/category/${code}/products?store=DEFAULT&lang=en`,
      { next: { revalidate: 60 } }
    );
    if (!res.ok) return { products: [], recordsTotal: 0, totalPages: 0 };
    return await res.json();
  } catch {
    return { products: [], recordsTotal: 0, totalPages: 0 };
  }
}

export default async function CategoryPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const data = await fetchCategoryProducts(slug);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <nav className="mb-6 text-sm text-gray-500">
        <Link href="/" className="hover:text-indigo-600">Home</Link>
        <span className="mx-2">/</span>
        <span className="text-gray-900">{slug}</span>
      </nav>

      <h1 className="mb-8 text-3xl font-bold text-gray-900 capitalize">{slug}</h1>

      {data.products.length === 0 ? (
        <p className="text-gray-500">No products found in this category.</p>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {data.products.map((product) => (
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

      <p className="mt-6 text-sm text-gray-500">
        {data.recordsTotal} product{data.recordsTotal !== 1 ? "s" : ""} found
      </p>
    </div>
  );
}
