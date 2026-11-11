#!/usr/bin/env python3
"""Run curl journeys against a disposable, seeded Shopizer and emit redacted contracts.

python3 sm-shop/src/test/uat/upgrade_journeys.py http://localhost:8082 base.json
python3 sm-shop/src/test/uat/upgrade_journeys.py http://localhost:8083 head.json
python3 sm-shop/src/test/uat/upgrade_journeys.py --compare base.json head.json

Requires Python 3 and curl; creates test customers/products/carts/orders. Never use
against production. Tokens and passwords are not written into the result files.
"""
import base64
import json
import subprocess
import sys
import uuid
from pathlib import Path
from urllib.parse import urlparse


def shape(value):
    if isinstance(value, dict):
        return {key: shape(item) for key, item in sorted(value.items())}
    if isinstance(value, list):
        return sorted({json.dumps(shape(item), sort_keys=True) for item in value})
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    return "string"


class Journeys:
    def __init__(self, url):
        if urlparse(url).hostname not in ("localhost", "127.0.0.1", "::1"):
            raise ValueError("Use a disposable local Shopizer instance")
        self.url = url.rstrip("/")
        self.contracts = {}
        self.results = {}
        self.admin = None

    def request(self, name, method, path, expected, body=None, auth=None, accept="application/json"):
        command = ["curl", "--silent", "--show-error", "--max-time", "30", "-X", method,
                   "-H", "Content-Type: application/json", "-H", "Accept: " + accept,
                   "--write-out", "\n%{http_code}", self.url + path]
        if auth:
            command += ["-H", "Authorization: " + auth]
        payload = None
        if body is not None:
            command += ["--data-binary", "@-"]
            payload = json.dumps(body)
        process = subprocess.run(command, input=payload, text=True, capture_output=True)
        if process.returncode:
            raise AssertionError(name + ": curl failed: " + process.stderr)
        raw, status = process.stdout.rsplit("\n", 1)
        status = int(status)
        try:
            data = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            data = raw
        self.contracts[name] = {"status": status, "shape": shape(data)}
        assert status in expected, name + ": expected " + str(expected) + ", got " + str(status)
        return data

    def values(self, name, **values):
        self.contracts[name]["values"] = values

    def admin_journey(self):
        login = self.request("admin.login", "POST", "/api/v1/private/login", (200,),
                             {"username": "admin@shopizer.com", "password": "password"})
        assert len(login["token"].split(".")) == 3
        self.admin = "Bearer " + login["token"]
        modules = self.request("admin.shipping", "GET", "/api/v1/private/modules/shipping", (200,), auth=self.admin)
        codes = sorted(module["code"] for module in modules)
        assert "canadapost" in codes
        self.values("admin.shipping", codes=codes)
        self.request("admin.anonymous", "GET", "/api/v1/private/modules/shipping", (401, 403))

    def customer(self, prefix):
        email = "upgrade-" + uuid.uuid4().hex[:12] + "@example.test"
        self.request(prefix + ".register", "POST", "/api/v1/customer/register", (200,), {
            "emailAddress": email, "password": "clear123", "gender": "M", "language": "en",
            "storeCode": "DEFAULT", "billing": {"firstName": "Upgrade", "lastName": "Test",
            "country": "US", "zone": "NY", "address": "1 Test St", "city": "Albany", "postalCode": "12207"}})
        login = self.request(prefix + ".login", "POST", "/api/v1/customer/login", (200,),
                             {"username": email, "password": "clear123"})
        assert len(login["token"].split(".")) == 3
        return email, "Bearer " + login["token"]

    def customer_journey(self):
        email, auth = self.customer("customer")
        profile = self.request("customer.profile", "GET", "/api/v1/auth/customer/profile", (200,), auth=auth)
        assert profile["emailAddress"] == email

    def browse_journey(self):
        store = self.request("browse.store", "GET", "/api/v1/store/DEFAULT", (200,))
        assert store["code"] == "DEFAULT"
        self.values("browse.store", code=store["code"], currency=store["currency"])
        categories = self.request("browse.categories", "GET", "/api/v1/category/", (200,))
        assert categories["categories"]
        products = self.request("browse.products", "GET", "/api/v1/products?store=DEFAULT", (200,))
        assert products["products"]
        self.values("browse.products", products=sorted((p["sku"], p["price"]) for p in products["products"]))
        product = products["products"][0]
        detail = self.request("browse.detail", "GET", "/api/v2/product/" + product["sku"], (200,))
        assert detail["sku"] == product["sku"]
        self.values("browse.detail", sku=detail["sku"], price=detail["price"])

    def cart(self, prefix):
        sku = "upgrade-" + uuid.uuid4().hex[:12]
        product = {"sku": sku, "available": True, "quantity": 100, "price": 10,
                   "productSpecifications": {"manufacturer": "DEFAULT"},
                   "descriptions": [{"language": "en", "name": sku}],
                   "inventory": {"sku": sku, "quantity": 100,
                                 "price": {"defaultPrice": True, "price": 10}}}
        self.request(prefix + ".product", "POST", "/api/v1/private/product", (201,), product, self.admin)
        cart = self.request(prefix + ".create", "POST", "/api/v1/cart", (201,), {"product": sku, "quantity": 1})
        assert cart["code"] and cart["quantity"] == 1
        return sku, cart

    def cart_journey(self):
        sku, cart = self.cart("cart")
        path = "/api/v1/cart/" + cart["code"]
        updated = self.request("cart.update", "PUT", path, (201,), {"product": sku, "quantity": 2})
        assert updated["quantity"] == 2
        read = self.request("cart.read", "GET", path, (200,))
        assert read["code"] == cart["code"] and read["quantity"] == 2
        self.values("cart.read", quantity=read["quantity"], total=read["total"])

    def checkout_journey(self):
        original = self.request("checkout.payment-config", "GET", "/api/v1/private/modules/payment/moneyorder",
                                (200,), auth=self.admin)
        original["integrationKeys"].setdefault("address", "1 Test Street")
        self.request("checkout.enable-offline", "POST", "/api/v1/private/modules/payment", (200,),
                     {"code": "moneyorder", "active": True, "integrationKeys": {"address": "1 Test Street"}, "integrationOptions": {}}, self.admin)
        try:
            _, auth = self.customer("checkout.customer")
            _, cart = self.cart("checkout.cart")
            path = "/api/v1/cart/" + cart["code"]
            summary = self.request("checkout.total", "GET", path + "/total", (200,))
            amount = "".join(c for c in summary["total"] if c in "0123456789.")
            order = self.request("checkout.place", "POST", "/api/v1/auth/cart/" + cart["code"] + "/checkout", (200,),
                                 {"customerAgreement": True, "currency": "USD", "payment": {"paymentModule": "moneyorder",
                                  "paymentType": "MONEYORDER", "transactionType": "AUTHORIZECAPTURE", "amount": amount}}, auth)
            assert order["id"] > 0
            read = self.request("checkout.read", "GET", "/api/v1/auth/orders/" + str(order["id"]), (200,), auth=auth)
            assert read["id"] == order["id"]
            self.values("checkout.place", payment=order["payment"], total=order["total"])
        finally:
            self.request("checkout.restore-offline", "POST", "/api/v1/private/modules/payment", (200,), original, self.admin)

    def basic_journey(self):
        for suffix, password, expected in (("valid", "password", (200,)), ("invalid", "wrong-password", (401,))):
            auth = "Basic " + base64.b64encode(("admin@shopizer.com:" + password).encode()).decode()
            response = self.request("services." + suffix, "POST", "/services/private/system/optin", expected, {}, auth)
            if suffix == "valid":
                assert response["status"] == 200

    def operations_journey(self):
        health = self.request("operations.health", "GET", "/actuator/health", (200,))
        assert health["status"] == "UP"
        self.values("operations.health", status=health["status"])
        docs = self.request("operations.docs", "GET", "/v2/api-docs", (200,))
        assert docs["swagger"] == "2.0" and docs["paths"]
        self.values("operations.docs", swagger=docs["swagger"], paths=sorted(docs["paths"]))
        self.request("operations.xml", "GET", "/api/v1/products?store=DEFAULT", (406,), accept="application/xml")

    def run(self):
        for name in ("admin", "customer", "browse", "cart", "checkout", "basic", "operations"):
            try:
                getattr(self, name + "_journey")()
                self.results[name] = "PASS"
            except (AssertionError, KeyError, TypeError, ValueError) as error:
                self.results[name] = "FAIL: " + str(error)
        return {"journeys": self.results, "contracts": self.contracts}


def main():
    if len(sys.argv) == 4 and sys.argv[1] == "--compare":
        base, head = (json.loads(Path(path).read_text()) for path in sys.argv[2:])
        differences = [key for key in sorted(set(base["contracts"]) | set(head["contracts"]))
                       if base["contracts"].get(key) != head["contracts"].get(key)]
        print(json.dumps({"contract_differences": differences}, indent=2))
        return bool(differences)
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    result = Journeys(sys.argv[1]).run()
    Path(sys.argv[2]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result["journeys"], indent=2))
    return any(value != "PASS" for value in result["journeys"].values())


if __name__ == "__main__":
    sys.exit(main())
