"""Compare disposable, seeded base/head runtimes using curl; needs Python 3 only.

Usage: python3 sm-shop/src/test/uat/compare_upgrade_contract.py BASE_URL HEAD_URL
Creates customers and carts. Checkout is covered by the Java H2 fixture;
the packaged seed inventory/order-line ID defect is reported separately.
Do not point this at production. Tokens and full response bodies are not printed.
"""

import difflib
import json
import subprocess
import sys
import uuid


def shape(value):
    if isinstance(value, dict):
        return {key: shape(item) for key, item in sorted(value.items())}
    if isinstance(value, list):
        return sorted({json.dumps(shape(item), sort_keys=True) for item in value})
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (float, int)):
        return "number"
    return "string"


def journey(base, email):
    observations = {}

    def call(name, method, path, status, data=None, token=None, basic=None, values=None):
        command = ["curl", "--silent", "--show-error", "--max-time", "60",
                   "--request", method, "--write-out", "\n%{http_code}", base.rstrip("/") + path]
        if data is not None:
            command += ["--header", "Content-Type: application/json", "--data", json.dumps(data)]
        if token:
            command += ["--header", "Authorization: Bearer " + token]
        if basic:
            command += ["--user", basic]
        raw, code = subprocess.check_output(command, text=True).rsplit("\n", 1)
        assert int(code) == status, name + ": expected " + str(status) + ", got " + code
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = raw
        observation = {"status": int(code), "shape": shape(body)}
        if values:
            observation["values"] = values(body)
        observations[name] = observation
        return body

    health = call("health", "GET", "/actuator/health", 200, values=lambda b: b["status"])
    assert health["status"] == "UP"
    docs = call("swagger", "GET", "/v2/api-docs", 200,
                values=lambda b: {"version": b["swagger"], "pathCount": len(b["paths"])})
    assert docs["swagger"] == "2.0"
    call("swagger-ui", "GET", "/swagger-ui.html", 200)
    admin = call("admin-login", "POST", "/api/v1/private/login", 200,
                 {"username": "admin@shopizer.com", "password": "password"})["token"]
    assert admin
    call("admin-users", "GET", "/api/v1/private/users", 200, token=admin)
    call("admin-anonymous", "GET", "/api/v1/private/users", 401)
    call("customer-register", "POST", "/api/v1/customer/register", 200, {
        "emailAddress": email, "password": "Upgrade1!", "gender": "M", "language": "en",
        "storeCode": "DEFAULT", "billing": {"firstName": "Upgrade", "lastName": "Customer",
        "country": "CA", "zone": "QC", "stateProvince": "Quebec", "city": "Montreal",
        "postalCode": "H2X1Y4", "address": "1 Test Street"}})
    customer = call("customer-login", "POST", "/api/v1/customer/login", 200,
                    {"username": email, "password": "Upgrade1!"})["token"]
    assert customer
    profile = call("customer-profile", "GET", "/api/v1/auth/customer/profile", 200, token=customer,
                   values=lambda b: {"emailAddress": b["emailAddress"]})
    assert profile["emailAddress"] == email
    call("customer-anonymous", "GET", "/api/v1/auth/customer/profile", 401)
    call("customer-not-admin", "GET", "/api/v1/private/users", 401, token=customer)
    call("store", "GET", "/api/v1/store/DEFAULT", 200, values=lambda b: b["code"])
    call("categories", "GET", "/api/v1/category", 200,
         values=lambda b: len(b["categories"]))
    products = call("products", "GET", "/api/v1/products", 200,
                    values=lambda b: sorted(p["sku"] for p in b["products"]))["products"]
    assert products, "A seeded catalogue is required"
    sku = sorted(p["sku"] for p in products)[0]
    detail = call("product-detail", "GET", "/api/v2/product/" + sku, 200,
                  values=lambda b: b["sku"])
    assert detail["sku"] == sku
    cart = call("cart-create", "POST", "/api/v1/cart", 201, {"product": sku, "quantity": 1},
                values=lambda b: {"quantity": b["quantity"], "total": b["total"]})
    assert cart["quantity"] == 1
    code = cart["code"]
    call("cart-update", "POST", "/api/v1/cart/" + code + "/multi", 201,
         [{"product": sku, "quantity": 2}],
         values=lambda b: {"quantity": b["quantity"], "total": b["total"]})
    saved = call("cart-read", "GET", "/api/v1/cart/" + code, 200,
                 values=lambda b: {"quantity": b["quantity"], "total": b["total"]})
    assert saved["quantity"] == 2 and saved["code"] == code
    call("services-anonymous", "GET", "/services/private/upgrade-auth-probe", 401)
    call("services-bad-password", "GET", "/services/private/upgrade-auth-probe", 401,
         basic="admin@shopizer.com:incorrect")
    # No services controller exists; authenticated requests reach MVC's 404.
    call("services-authenticated", "GET", "/services/private/upgrade-auth-probe", 404,
         basic="admin@shopizer.com:password")
    return observations


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    email = "upgrade-" + uuid.uuid4().hex[:16] + "@example.test"
    base = journey(sys.argv[1], email)
    head = journey(sys.argv[2], email)
    before = json.dumps(base, sort_keys=True, indent=2).splitlines()
    after = json.dumps(head, sort_keys=True, indent=2).splitlines()
    differences = list(difflib.unified_diff(before, after, fromfile="base", tofile="head"))
    print(json.dumps({"base": base, "head": head, "differences": differences}, indent=2))
    assert not differences, "Breaking status, JSON field/type or selected value difference"
    print("PASS: " + str(len(base)) + " curl checkpoints per revision; contracts match")


if __name__ == "__main__":
    main()
