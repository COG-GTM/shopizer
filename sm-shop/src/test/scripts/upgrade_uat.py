#!/usr/bin/env python3
"""Curl-only journeys against an isolated, seeded Shopizer instance.

Usage: python3 sm-shop/src/test/scripts/upgrade_uat.py http://localhost:8081 result.json
Creates a customer, product, cart and offline order. Never point at production.
Snapshots omit tokens and dynamic values; compare outputs with diff -u.
"""
import base64
import json
import subprocess
import sys
from pathlib import Path


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


def run(url):
    snapshots = {}
    failures = []

    def request(name, method, path, body=None, token=None, expected=200, basic=None):
        command = ["curl", "--silent", "--show-error", "--max-time", "30", "-X", method,
                   "-H", "Content-Type: application/json", "-H", "Accept: application/json",
                   "-w", "\n%{http_code}", url.rstrip("/") + path]
        if token:
            command += ["-H", "Authorization: Bearer " + token]
        if basic:
            command += ["--user", basic]
        if body is not None:
            command += ["--data", json.dumps(body)]
        response = subprocess.run(command, capture_output=True, text=True, check=True).stdout
        raw, status = response.rsplit("\n", 1)
        data = json.loads(raw) if raw else None
        snapshots[name] = {"status": int(status), "shape": shape(data)}
        if int(status) != expected:
            raise AssertionError(f"{name}: expected {expected}, got {status}")
        return data

    def login(realm, username, password):
        result = request(realm + " login", "POST", "/api/v1/" + realm + "/login",
                         {"username": username, "password": password})
        token = result["token"]
        payload = token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        snapshots[realm + " JWT"] = {"shape": shape(claims), "sub": claims["sub"], "aud": claims["aud"]}
        assert claims["sub"] == username
        return token

    def scenario(name, function):
        try:
            function()
        except Exception as error:
            failures.append(f"{name}: {error}")

    admin = login("private", "admin@shopizer.com", "password")
    email = "upgradeuat@example.test"
    customer_body = {"emailAddress": email, "gender": "M", "language": "en", "storeCode": "DEFAULT",
                     "password": "clear123", "repeatPassword": "clear123",
                     "billing": {"firstName": "Upgrade", "lastName": "Customer", "country": "BE",
                                 "city": "Brussels", "address": "1 Test Street", "postalCode": "1000", "phone": "123456789"}}
    request("register", "POST", "/api/v1/customer/register", customer_body)
    customer = login("customer", email, "clear123")

    def authorization():
        assert "customers" in request("private customers", "GET", "/api/v1/private/customers", token=admin)
        profile = request("customer profile", "GET", "/api/v1/auth/customer/profile", token=customer)
        assert profile["emailAddress"] == email
        for realm, path in [("private", "/api/v1/private/customers"), ("customer", "/api/v1/auth/customer/profile")]:
            request(realm + " missing token", "GET", path, expected=401)

    scenario("authorization", authorization)

    def browse():
        assert request("store", "GET", "/api/v1/store/DEFAULT")["code"] == "DEFAULT"
        assert "categories" in request("categories", "GET", "/api/v1/category?store=DEFAULT")
        products = request("products", "GET", "/api/v1/products?store=DEFAULT")["products"]
        assert products
        detail = request("product detail", "GET", "/api/v2/product/" + products[0]["sku"])
        assert detail["sku"] == products[0]["sku"]
        snapshots["catalog keys"] = {"sku": detail["sku"], "price": detail["price"]}

    scenario("browse", browse)
    sku = "upgradeuatproduct"
    product = {"sku": sku, "available": True, "price": 10, "quantity": 100,
               "productSpecifications": {"manufacturer": "DEFAULT"},
               "descriptions": [{"name": sku, "language": "en"}],
               "inventory": {"quantity": 100, "sku": sku, "price": {"defaultPrice": True, "price": 10}}}
    request("create product", "POST", "/api/v1/private/product", product, admin, 201)
    cart = request("create cart", "POST", "/api/v1/cart", {"product": sku, "quantity": 1}, expected=201)
    cart_path = "/api/v1/cart/" + cart["code"]

    def cart_journey():
        updated = request("update cart", "PUT", cart_path, {"product": sku, "quantity": 3}, expected=201)
        assert updated["quantity"] == 3
        assert request("read cart", "GET", cart_path)["quantity"] == 3
        snapshots["cart quantity"] = 3

    scenario("cart", cart_journey)

    def checkout():
        config = {"code": "moneyorder", "active": True,
                  "integrationKeys": {"transaction": "AUTHORIZECAPTURE", "address": "1 Test Street"},
                  "integrationOptions": {}}
        request("offline payment", "POST", "/api/v1/private/modules/payment", config, admin)
        order = {"currency": "CAD", "customerAgreement": True,
                 "payment": {"paymentModule": "moneyorder", "paymentType": "MONEYORDER",
                             "transactionType": "AUTHORIZECAPTURE", "amount": "30.00"}}
        confirmation = request("checkout", "POST", "/api/v1/auth/cart/" + cart["code"] + "/checkout", order, customer)
        order_id = confirmation["id"]
        assert order_id > 0
        assert request("customer order", "GET", "/api/v1/auth/orders/" + str(order_id), token=customer)["id"] == order_id
        assert request("admin order", "GET", "/api/v1/private/orders/" + str(order_id), token=admin)["id"] == order_id
        snapshots["order products"] = len(confirmation["products"])

    scenario("checkout", checkout)

    def operations():
        request("basic invalid", "POST", "/services/private/system/optin", {}, basic="admin@shopizer.com:incorrect", expected=401)
        request("basic valid", "POST", "/services/private/system/optin", {}, basic="admin@shopizer.com:password")
        assert request("health", "GET", "/actuator/health")["status"] == "UP"
        assert "paths" in request("api docs", "GET", "/v3/api-docs")

    scenario("operations", operations)
    return {"snapshots": snapshots, "failures": failures}


if __name__ == "__main__":
    result = run(sys.argv[1])
    Path(sys.argv[2]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"requests": sum(isinstance(x, dict) and "status" in x for x in result["snapshots"].values()),
                      "failures": result["failures"]}))
    sys.exit(bool(result["failures"]))
