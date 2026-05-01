"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { registerCustomer } from "@/lib/api";

export default function RegisterPage() {
  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    emailAddress: "",
    password: "",
    address: "",
    city: "",
    postalCode: "",
    country: "US",
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const update = (field: string, value: string) =>
    setForm((f) => ({ ...f, [field]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await registerCustomer({
        emailAddress: form.emailAddress,
        password: form.password,
        firstName: form.firstName,
        lastName: form.lastName,
        billing: {
          firstName: form.firstName,
          lastName: form.lastName,
          address: form.address,
          city: form.city,
          postalCode: form.postalCode,
          stateProvince: "",
          country: form.country,
          phone: "",
        },
      });
      router.push("/login");
    } catch {
      setError("Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-md px-4 py-16">
      <h1 className="mb-8 text-center text-2xl font-bold text-gray-900">
        Create Account
      </h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <p className="rounded-md bg-red-50 px-4 py-2 text-sm text-red-600">
            {error}
          </p>
        )}
        <div className="grid grid-cols-2 gap-4">
          <input
            placeholder="First name"
            value={form.firstName}
            onChange={(e) => update("firstName", e.target.value)}
            required
            className="rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
          <input
            placeholder="Last name"
            value={form.lastName}
            onChange={(e) => update("lastName", e.target.value)}
            required
            className="rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        <input
          type="email"
          placeholder="Email"
          value={form.emailAddress}
          onChange={(e) => update("emailAddress", e.target.value)}
          required
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
        />
        <input
          type="password"
          placeholder="Password"
          value={form.password}
          onChange={(e) => update("password", e.target.value)}
          required
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
        />
        <input
          placeholder="Address"
          value={form.address}
          onChange={(e) => update("address", e.target.value)}
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
        />
        <div className="grid grid-cols-2 gap-4">
          <input
            placeholder="City"
            value={form.city}
            onChange={(e) => update("city", e.target.value)}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
          <input
            placeholder="Postal code"
            value={form.postalCode}
            onChange={(e) => update("postalCode", e.target.value)}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-md bg-indigo-600 py-3 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-50"
        >
          {loading ? "Creating..." : "Create Account"}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-500">
        Already have an account?{" "}
        <Link href="/login" className="text-indigo-600 hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}
