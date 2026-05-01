"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth";

export default function Header() {
  const { user, logout } = useAuth();

  return (
    <header className="bg-white border-b border-gray-200">
      <nav className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-8">
            <Link href="/" className="text-xl font-bold text-indigo-600">
              Shopizer
            </Link>
            <div className="hidden sm:flex gap-6">
              <Link href="/" className="text-sm text-gray-700 hover:text-indigo-600">
                Home
              </Link>
              <Link href="/cart" className="text-sm text-gray-700 hover:text-indigo-600">
                Cart
              </Link>
            </div>
          </div>
          <div className="flex items-center gap-4">
            {user ? (
              <>
                {user.role === "admin" && (
                  <Link
                    href="/admin/products"
                    className="text-sm text-gray-700 hover:text-indigo-600"
                  >
                    Admin
                  </Link>
                )}
                <Link href="/account" className="text-sm text-gray-700 hover:text-indigo-600">
                  Account
                </Link>
                <button
                  onClick={logout}
                  className="text-sm text-gray-500 hover:text-gray-700"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link href="/login" className="text-sm text-gray-700 hover:text-indigo-600">
                  Login
                </Link>
                <Link
                  href="/register"
                  className="rounded-md bg-indigo-600 px-3 py-2 text-sm text-white hover:bg-indigo-500"
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>
    </header>
  );
}
