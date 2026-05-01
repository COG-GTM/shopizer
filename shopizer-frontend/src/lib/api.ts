const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface FetchOptions extends RequestInit {
  token?: string;
}

export async function api<T>(path: string, options: FetchOptions = {}): Promise<T> {
  const { token, headers: customHeaders, ...rest } = options;
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...customHeaders,
  };
  if (token) {
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_URL}${path}`, {
    headers,
    ...rest,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`API ${res.status}: ${text}`);
  }

  const contentType = res.headers.get("content-type");
  if (contentType?.includes("application/json")) {
    return res.json();
  }
  return {} as T;
}

export interface Product {
  id: number;
  description: { name: string; friendlyUrl: string; description: string };
  productPrice: string;
  finalPrice: string;
  image?: { imageUrl: string };
  images?: { imageUrl: string }[];
  categories?: Category[];
}

export interface ProductList {
  products: Product[];
  recordsTotal: number;
  number: number;
  recordsFiltered: number;
  totalPages: number;
}

export interface Category {
  id: number;
  code: string;
  description: { name: string; friendlyUrl: string; description: string };
  children?: Category[];
  productCount?: number;
}

export interface CartItem {
  id: number;
  product: { id: number; description: { name: string }; finalPrice: string };
  quantity: number;
  subTotal: string;
}

export interface Cart {
  code: string;
  total: string;
  subTotal: string;
  products: CartItem[];
}

export interface Order {
  id: number;
  orderStatus: string;
  datePurchased: string;
  total: string;
  currency: string;
}

export interface Customer {
  id: number;
  emailAddress: string;
  firstName: string;
  lastName: string;
  billing: Address;
}

export interface Address {
  firstName: string;
  lastName: string;
  address: string;
  city: string;
  postalCode: string;
  stateProvince: string;
  country: string;
  phone: string;
}

export interface AuthResponse {
  token: string;
  id?: number;
}

export function getProducts(store = "DEFAULT", lang = "en", page = 0, count = 20) {
  return api<ProductList>(
    `/api/v1/products?store=${store}&lang=${lang}&page=${page}&count=${count}`
  );
}

export function getProduct(id: number, store = "DEFAULT", lang = "en") {
  return api<Product>(`/api/v1/product/${id}?store=${store}&lang=${lang}`);
}

export function getCategories(store = "DEFAULT", lang = "en") {
  return api<Category[]>(`/api/v1/category?store=${store}&lang=${lang}`);
}

export function getCategoryProducts(code: string, store = "DEFAULT", lang = "en", page = 0) {
  return api<ProductList>(
    `/api/v1/category/${code}/products?store=${store}&lang=${lang}&page=${page}`
  );
}

export function getCart(code: string, store = "DEFAULT") {
  return api<Cart>(`/api/v1/cart/${code}?store=${store}`);
}

export function addToCart(
  body: { product: number; quantity: number; code?: string },
  store = "DEFAULT"
) {
  return api<Cart>(`/api/v1/cart?store=${store}`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function loginCustomer(username: string, password: string, store = "DEFAULT") {
  return api<AuthResponse>(`/api/v1/customer/login?store=${store}`, {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function registerCustomer(
  data: {
    emailAddress: string;
    password: string;
    firstName: string;
    lastName: string;
    billing: Address;
  },
  store = "DEFAULT"
) {
  return api<Customer>(`/api/v1/customer?store=${store}`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function loginAdmin(username: string, password: string) {
  return api<AuthResponse>("/api/v1/private/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function getOrders(token: string, store = "DEFAULT") {
  return api<{ orders: Order[] }>(`/api/v1/orders?store=${store}`, { token });
}
