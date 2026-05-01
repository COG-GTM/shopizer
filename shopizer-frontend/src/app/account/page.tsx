"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function AccountPage() {
  const { user } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!user) router.push("/login");
  }, [user, router]);

  if (!user) return null;

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="mb-8 text-2xl font-bold text-gray-900">My Account</h1>
      <div className="grid gap-4 sm:grid-cols-2">
        <Link
          href="/account/orders"
          className="rounded-lg border border-gray-200 p-6 hover:border-indigo-300 hover:shadow-sm transition"
        >
          <h2 className="text-lg font-semibold text-gray-900">Order History</h2>
          <p className="mt-1 text-sm text-gray-500">View your past orders</p>
        </Link>
        <div className="rounded-lg border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900">Profile</h2>
          <p className="mt-1 text-sm text-gray-500">
            Role: {user.role}
          </p>
        </div>
      </div>
    </div>
  );
}
