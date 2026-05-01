"use client";

import { useState } from "react";
import Link from "next/link";

type Step = "shipping" | "payment" | "confirm";

export default function CheckoutPage() {
  const [step, setStep] = useState<Step>("shipping");
  const [shipping, setShipping] = useState({
    firstName: "",
    lastName: "",
    address: "",
    city: "",
    postalCode: "",
    country: "US",
    phone: "",
  });

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="mb-8 text-2xl font-bold text-gray-900">Checkout</h1>

      <div className="mb-8 flex gap-2">
        {(["shipping", "payment", "confirm"] as Step[]).map((s, i) => (
          <div
            key={s}
            className={`flex-1 rounded-full py-2 text-center text-xs font-medium ${
              step === s
                ? "bg-indigo-600 text-white"
                : "bg-gray-100 text-gray-500"
            }`}
          >
            {i + 1}. {s.charAt(0).toUpperCase() + s.slice(1)}
          </div>
        ))}
      </div>

      {step === "shipping" && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Shipping Address</h2>
          <div className="grid grid-cols-2 gap-4">
            <input
              placeholder="First name"
              value={shipping.firstName}
              onChange={(e) => setShipping({ ...shipping, firstName: e.target.value })}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <input
              placeholder="Last name"
              value={shipping.lastName}
              onChange={(e) => setShipping({ ...shipping, lastName: e.target.value })}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <input
            placeholder="Address"
            value={shipping.address}
            onChange={(e) => setShipping({ ...shipping, address: e.target.value })}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
          <div className="grid grid-cols-2 gap-4">
            <input
              placeholder="City"
              value={shipping.city}
              onChange={(e) => setShipping({ ...shipping, city: e.target.value })}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
            <input
              placeholder="Postal code"
              value={shipping.postalCode}
              onChange={(e) => setShipping({ ...shipping, postalCode: e.target.value })}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <input
            placeholder="Phone"
            value={shipping.phone}
            onChange={(e) => setShipping({ ...shipping, phone: e.target.value })}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
          <button
            onClick={() => setStep("payment")}
            className="w-full rounded-md bg-indigo-600 py-3 text-sm font-semibold text-white hover:bg-indigo-500"
          >
            Continue to Payment
          </button>
        </div>
      )}

      {step === "payment" && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Payment</h2>
          <div className="rounded-lg border border-gray-200 p-6 text-center text-sm text-gray-500">
            Payment integration will be configured with your payment provider.
          </div>
          <div className="flex gap-4">
            <button
              onClick={() => setStep("shipping")}
              className="flex-1 rounded-md border border-gray-300 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50"
            >
              Back
            </button>
            <button
              onClick={() => setStep("confirm")}
              className="flex-1 rounded-md bg-indigo-600 py-3 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              Place Order
            </button>
          </div>
        </div>
      )}

      {step === "confirm" && (
        <div className="text-center space-y-4">
          <div className="text-5xl">🎉</div>
          <h2 className="text-xl font-bold text-gray-900">Order Confirmed!</h2>
          <p className="text-sm text-gray-500">
            Thank you for your purchase. You will receive a confirmation email shortly.
          </p>
          <Link
            href="/"
            className="inline-block rounded-md bg-indigo-600 px-6 py-3 text-sm font-semibold text-white hover:bg-indigo-500"
          >
            Continue Shopping
          </Link>
        </div>
      )}
    </div>
  );
}
