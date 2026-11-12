#!/usr/bin/env python3
"""AB-548: packaged H2/MySQL verification. Only touches an owned disposable MySQL container.

Requires Python 3, Docker, Maven, and JAVA_HOME pointing to JDK 21. No Python packages.
The full build runs first; evidence is preserved before the external Surefire runs.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
from email.parser import BytesHeaderParser
from email.policy import default as email_policy
from email.utils import parseaddr
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import socketserver
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASELINE = {"LINE": (8602, 30779), "BRANCH": (1286, 7652)}  # AB-533, baseline-tests.md
MYSQL_IMAGE = "mysql:8.0.44"
SEGMENTS = {"product": ("PRODUCT", "PRODUCT_ID", "PRODUCT_SEQ_NEXT_VAL"),
            "customer": ("CUSTOMER", "CUSTOMER_ID", "CUSTOMER_SEQ_NEXT_VAL"),
            "order": ("ORDERS", "ORDER_ID", "ORDER_ID_SEQ_NEXT_VAL")}


def run(args, *, cwd=ROOT, env=None, log=None):
    if log:
        with Path(log).open("w") as out:
            subprocess.run(args, cwd=cwd, env=env, stdout=out, stderr=subprocess.STDOUT, check=True)
    else:
        return subprocess.check_output(args, cwd=cwd, env=env, text=True).strip()


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def request(url, method="GET", body=None, token=None):
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as response:
        payload = response.read()
        return json.loads(payload) if payload else None


def wait_ready(check, label, process=None, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError(label + " exited; inspect its app log")
        try:
            if check():
                return
        except (OSError, ValueError, subprocess.CalledProcessError):
            pass
        time.sleep(1)
    raise TimeoutError(label + " did not become ready")


class SmtpSink(socketserver.StreamRequestHandler):
    """Discard bodies; retain only subject/envelope metadata in memory for checkout correlation."""
    def handle(self):
        def reply(text):
            self.wfile.write((text + "\r\n").encode())
            self.wfile.flush()
        reply("220 parity.local ESMTP")
        recipients = []
        while line := self.rfile.readline():
            verb = line.split(b" ", 1)[0].strip().upper()
            if verb == b"DATA":
                reply("354 End with a dot")
                headers = bytearray()
                in_headers = True
                while (line := self.rfile.readline()) and line != b".\r\n":
                    if in_headers:
                        headers.extend(line)
                        in_headers = line not in (b"\r\n", b"\n")
                if line != b".\r\n":
                    return  # An interrupted DATA transfer is not a delivery.
                subject = str(BytesHeaderParser(policy=email_policy).parsebytes(bytes(headers)).get("Subject", ""))
                with self.server.lock:
                    self.server.deliveries += 1
                    self.server.messages.append(dict(subject=subject, recipients=recipients))
                recipients = []
                reply("250 accepted")
            elif verb == b"RCPT":
                recipients.append(parseaddr(line.decode().split(":", 1)[1])[1].casefold())
                reply("250 recipient accepted")
            elif verb == b"QUIT":
                reply("221 bye")
                break
            else:
                if verb in (b"MAIL", b"RSET"):
                    recipients = []
                reply("250 parity.local")


@contextmanager
def smtp_sink():
    with socketserver.ThreadingTCPServer(("127.0.0.1", 0), SmtpSink) as server:
        server.daemon_threads = True
        server.deliveries = 0
        server.messages = []
        server.lock = threading.Lock()
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield server
        finally:
            server.shutdown()


@contextmanager
def mysql_container(evidence):
    name = "ab548-parity-" + secrets.token_hex(5)
    password = secrets.token_urlsafe(24)
    env = dict(os.environ, MYSQL_ROOT_PASSWORD=password, MYSQL_PWD=password)
    run(["docker", "run", "-d", "--name", name, "--env", "MYSQL_ROOT_PASSWORD",
         "--env", "MYSQL_DATABASE=SALESMANAGER", "--publish", "127.0.0.1::3306", MYSQL_IMAGE], env=env)
    try:
        def sql(query):
            return run(["docker", "exec", "--env", "MYSQL_PWD", name, "mysql", "-uroot",
                        "--batch", "--skip-column-names", "SALESMANAGER", "-e", query], env=env)
        wait_ready(lambda: sql("SELECT 1") == "1", "MySQL 8")
        port = run(["docker", "port", name, "3306/tcp"]).rsplit(":", 1)[1]
        image = run(["docker", "image", "inspect", MYSQL_IMAGE, "--format", "{{index .RepoDigests 0}}"])
        (evidence / "mysql-image.txt").write_text(image + "\n")
        print("Owned MySQL container:", name, flush=True)
        yield sql, port, password
    finally:
        # Stop only this uniquely named container; retain it for local diagnostics.
        run(["docker", "stop", name])


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def copy_h2_seed(directory):
    # Embedded integration tests write cache files under sm-shop/files. Read committed
    # seed bytes, so the packaged run never inherits that generated test state.
    names = run(["git", "ls-files", "sm-shop/SALESMANAGER.mv.db", "sm-shop/files"]).splitlines()
    require("sm-shop/SALESMANAGER.mv.db" in names, "Committed H2 2.x seed is required")
    for name in names:
        dest = directory / Path(name).relative_to("sm-shop")
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(subprocess.check_output(["git", "show", "HEAD:" + name], cwd=ROOT))


@contextmanager
def app(mode, directory, smtp_port, db_port=None, password=None, label="app"):
    config = directory / "config"
    config.mkdir(parents=True, exist_ok=True)
    profile = "mysql" if mode == "mysql" else "docker"
    # Java .properties resources in this repository include ISO-8859-1 comments.
    props = (ROOT / f"sm-shop/src/main/resources/profiles/{profile}/database.properties").read_text(encoding="iso-8859-1")
    if mode == "mysql":
        props += (f"\ndb.jdbcUrl=jdbc:mysql://127.0.0.1:{db_port}/SALESMANAGER?"
                  "allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC\ndb.user=root\n"
                  "db.password=${DB_PASSWORD}\n")
    database_file = config / ("profiles/mysql/database.properties" if mode == "mysql" else "database.properties")
    database_file.parent.mkdir(parents=True, exist_ok=True)
    database_file.write_text(props + "\ndb.show.sql=false\n", encoding="iso-8859-1")
    if mode == "mysql":
        core = (ROOT / "sm-core/src/main/resources/profiles/mysql/shopizer-core.properties").read_text(encoding="iso-8859-1")
        # Match the AB-533 boundary: exercise persistence/HTTP, use local SMTP and
        # omit outbound search indexing. MySQL's deployment template defaults to SES.
        (config / "profiles/mysql/shopizer-core.properties").write_text(
            core + "\nconfig.emailSender=default\nsearch.noindex=true\nelasticsearch.security.password=\n",
            encoding="iso-8859-1")
    # The legacy XML PropertyPlaceholderConfigurer reads classpath resources directly;
    # Boot command-line mail properties do not override them. Put test SMTP config first
    # on the external loader path, keeping real secrets out of files.
    (config / "email.properties").write_text(
        "mailSender.protocol=smtp\nmailSender.host=127.0.0.1\n"
        f"mailSender.port={smtp_port}\nmailSender.username=\nmailSender.password=\n"
        "mailSender.mail.smtp.auth=false\nmail.smtp.starttls.enable=false\n")
    port = free_port()
    env = os.environ.copy()
    if password:
        env["DB_PASSWORD"] = password  # Not put in a file, command line or report.
    command = [str(Path(os.environ["JAVA_HOME"]) / "bin/java"), "-Xmx768m",
               f"-Dloader.path={config}", "-cp", str(ROOT / "sm-shop/target/shopizer.jar"),
               "org.springframework.boot.loader.launch.PropertiesLauncher", f"--server.port={port}",
               "--server.address=127.0.0.1"]
    if mode == "mysql":
        command.append("--spring.profiles.active=mysql")
    with (directory / f"{label}.log").open("w") as log:
        process = subprocess.Popen(command, cwd=directory, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            url = f"http://127.0.0.1:{port}"
            wait_ready(lambda: request(url + "/actuator/health").get("status") == "UP", label, process)
            yield url
        finally:
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


def suite_counts(paths):
    counts = dict.fromkeys(("tests", "skipped", "failures", "errors"), 0)
    require(bool(paths), "No Surefire reports found")
    for path in paths:
        suite = ET.parse(path).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, 0))
    return counts


def checkout_mail_counts(messages, expected):
    """Match this checkout's exact order subject and both recipients, not registration mail."""
    matching = [message for message in messages if message["subject"] == expected["subject"]]
    counts = {role: sum(expected[role].casefold() in message["recipients"] for message in matching)
              for role in ("customer", "merchant")}
    return dict(messages=len(matching), **counts)


def smoke(url, evidence, smtp):
    evidence.mkdir(parents=True, exist_ok=True)
    with smtp.lock:
        messages_before = len(smtp.messages)
    run(["mvn", "-B", "-pl", "sm-shop", "-Dtest=PackagedSmokeTest,PackagedContractTest",
         "-Djacoco.skip=true", f"-Dparity.url={url}", f"-Dparity.output={evidence}",
         f"-Dparity.database={'MySQL' if evidence.name == 'mysql' else 'H2'}", "test"],
        log=evidence / "smoke.log")
    expected = json.loads((evidence / "checkout-mail.json").read_text())
    require(expected["customer"] and expected["merchant"] and expected["subject"]
            and expected["customer"].casefold() != expected["merchant"].casefold(),
            "Checkout mail evidence must identify its subject and distinct customer/merchant recipients")
    def deliveries():
        with smtp.lock:
            return checkout_mail_counts(smtp.messages[messages_before:], expected)
    wait_ready(lambda: deliveries() == {"messages": 2, "customer": 1, "merchant": 1},
               "Checkout-specific customer and merchant SMTP deliveries", timeout=15)
    paths = sorted((ROOT / "sm-shop/target/surefire-reports").glob("TEST-*Packaged*.xml"))
    counts = suite_counts(paths)
    require(counts == {"tests": 10, "skipped": 1, "failures": 0, "errors": 0},
            f"Packaged suite must execute all nine checks (KF-1 remains skipped): {counts}")
    for path in paths:
        shutil.copy2(path, evidence / path.name)
    diff = json.loads((evidence / "contract-diff.json").read_text())
    return dict(counts=counts, contract=diff, health="UP", swagger=200, info=200,
                checkout_mail=deliveries())


def fixture(url):
    """Durable products, registered customers and real offline-checkout orders via HTTP."""
    token = request(url + "/api/v1/private/login", "POST",
                    {"username": "admin@shopizer.com", "password": "password"})["token"]
    sku = "parity" + secrets.token_hex(6)
    product = {"sku": sku, "available": True, "price": 10, "quantity": 100,
               "productSpecifications": {"manufacturer": "DEFAULT"},
               "descriptions": [{"name": sku, "language": "en"}],
               "inventory": {"sku": sku, "quantity": 100, "price": {"defaultPrice": True, "price": 10}}}
    product_id = request(url + "/api/v1/private/product", "POST", product, token)["id"]
    customer = {"emailAddress": sku + "@example.test", "gender": "M", "language": "en", "storeCode": "DEFAULT",
                "password": "clear123", "repeatPassword": "clear123",
                "billing": {"firstName": "Parity", "lastName": "Customer", "country": "BE", "city": "Brussels",
                            "address": "1 Test Street", "postalCode": "1000", "phone": "123456789"}}
    customer_id = request(url + "/api/v1/customer/register", "POST", customer)["id"]
    request(url + "/api/v1/private/modules/payment", "POST",
            {"code": "moneyorder", "active": True, "integrationKeys": {"transaction": "AUTHORIZECAPTURE",
             "address": "1 Test Street"}, "integrationOptions": {}}, token)
    cart = request(url + "/api/v1/cart", "POST", {"product": sku, "quantity": 1})
    # Checkout as the registered customer so it does not allocate a second customer ID.
    customer_token = request(url + "/api/v1/customer/login", "POST",
                             {"username": customer["emailAddress"], "password": "clear123"})["token"]
    customer["id"] = customer_id
    order = {"currency": "CAD", "customerAgreement": True, "customer": customer,
             "payment": {"paymentModule": "moneyorder", "paymentType": "MONEYORDER",
                         "transactionType": "AUTHORIZECAPTURE", "amount": "10.00"}}
    order_id = request(url + f"/api/v1/auth/cart/{cart['code']}/checkout", "POST", order, customer_token)["id"]
    for path, expected in [(f"/api/v2/private/product/{product_id}", product_id),
                           (f"/api/v1/private/customer/{customer_id}", customer_id),
                           (f"/api/v1/private/orders/{order_id}", order_id)]:
        require(request(url + path, token=token)["id"] == expected, "Durable fixture readback failed")
    return dict(product=product_id, customer=customer_id, order=order_id)


def dataset(sql):
    result = {}
    for kind, (table, column, segment) in SEGMENTS.items():
        rows = sql(f"SELECT {column} FROM {table} ORDER BY {column}")
        ids = [int(value) for value in rows.splitlines()] if rows else []
        counter = int(sql(f"SELECT SEQ_COUNT FROM SM_SEQUENCER WHERE SEQ_NAME='{segment}'"))
        result[kind] = dict(ids=ids, counter=counter)
    return result


def verify_continuity(before, after, fixtures):
    measurements = {}
    for kind in SEGMENTS:
        prior, current = before[kind], after[kind]
        require(bool(prior["ids"]), f"Existing {kind} fixture rows are required")
        expected = prior["counter"] * 50  # JPA default allocation 50; legacy-hilo maxLo=49.
        require(set(prior["ids"]).issubset(current["ids"]), f"Existing {kind} rows lost")
        # Fixtures create one entity at a time, with no competing allocator. Verify
        # the whole block: checking only the first/minimum can hide later resets.
        expected_ids = list(range(expected, expected + len(fixtures[kind])))
        require(bool(fixtures[kind]) and fixtures[kind] == expected_ids,
                f"{kind} IDs must continue existing counter * 50 in allocation order")
        require(not set(prior["ids"]).intersection(fixtures[kind]), f"{kind} ID collision")
        require(set(fixtures[kind]).issubset(current["ids"]), f"New {kind} rows missing")
        require(current["counter"] > prior["counter"], f"{kind} sequencer did not advance")
        measurements[kind] = dict(existing_rows=len(prior["ids"]), counter_before=prior["counter"],
                                   counter_after=current["counter"], new_ids=fixtures[kind],
                                   first_expected=expected, collisions=0)
    return measurements


def coverage(path):
    root = ET.parse(path).getroot()
    result = {}
    for kind, (old_covered, old_total) in BASELINE.items():
        node = root.find(f"counter[@type='{kind}']")
        require(node is not None, "Missing aggregate coverage counter: " + kind)
        covered, missed = int(node.get("covered")), int(node.get("missed"))
        total = covered + missed
        require(total > 0, "Empty aggregate coverage")
        result[kind] = dict(covered=covered, total=total, baseline_covered=old_covered,
                            baseline_total=old_total, passes=covered * old_total >= old_covered * total)
    return result


def write_report(path, state):
    lines = ["# Runtime parity report (AB-548)", "", "**Initial Wave 3 verification; final epic sign-off is pending.**", "",
             f"Generated: {state['time']}. Source commit: `{state['commit']}`.",
             f"Working-tree patch SHA-256: `{state['patch_sha256']}`; packaged JAR SHA-256: `{state.get('jar_sha256', 'not built')}`.", "",
             "This report measures the checked-out integration snapshot plus the AB-548 harness. "
             "Re-run on the integration commit containing every other merged Wave 3 ticket before closing AB-531.", "",
             f"Result: **{state['result']}**.", ""]
    if state.get("error"):
        lines += [f"Incomplete gate: `{state['error']}`. Inspect the preserved run logs.", ""]
    if "build" in state:
        lines += ["## Full Java 21 reactor", "", "Command: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean install`.", "",
                  "```text", "BUILD SUCCESS", json.dumps(state["build"], sort_keys=True), "```", ""]
    if "coverage" in state:
        lines += ["## Coverage against AB-533", "", "Aggregate JaCoCo counters from the full build, captured before packaged tests. "
                  "Both exact fractions must meet the baseline; no rounded-percentage comparison or lowered threshold.", "",
                  "| Counter | Baseline covered / total | Current covered / total | Baseline | Current | Gate |",
                  "| --- | ---: | ---: | ---: | ---: | --- |"]
        for kind, value in state["coverage"].items():
            lines.append(f"| {kind} | {value['baseline_covered']} / {value['baseline_total']} | {value['covered']} / {value['total']} "
                         f"| {100 * value['baseline_covered'] / value['baseline_total']:.4f}% "
                         f"| {100 * value['covered'] / value['total']:.4f}% | {'PASS' if value['passes'] else 'FAIL'} |")
        lines.append("")
    lines += ["## Packaged runtime and REST/JWT contract", "",
              "The same eight enabled AB-533 scenarios run over HTTP against the packaged JAR: admin JWT, customer registration/login/profile, "
              "admin authorization denials, product CRUD, cart quantity persistence, offline moneyorder checkout, services Basic Auth, and health. "
              "KF-1 (deleted-product GET) stays disabled as in the baseline. The ninth enabled check compares OpenAPI and operational endpoints. "
              "The embedded suite still mocks SMTP; packaged runs deliver real composed mail to a local SMTP sink. "
              "Each checkout must deliver two confirmations with its exact order subject: one to its unique customer and one to the merchant. "
              "Registration messages and confirmations for other orders cannot satisfy the gate. Bodies are discarded; only subject/envelope metadata is held in memory.", "",
              "| Runtime | Executed / skipped | Failed / errors | Operations (baseline / current) | Removed | Added | Health / Swagger / Info |",
              "| --- | ---: | ---: | ---: | ---: | ---: | --- |"]
    for mode in ("h2", "mysql"):
        if mode not in state:
            lines.append(f"| {mode} | untested | — | — | — | — | — |")
            continue
        value = state[mode]
        c, d = value["counts"], value["contract"]
        lines.append(f"| {mode} | {c['tests'] - c['skipped']} / {c['skipped']} | {c['failures']} / {c['errors']} "
                     f"| {d['baselineCount']} / {d['currentCount']} | {len(d['removed'])} | {len(d['added'])} | UP / 200 / 200 |")
    lines += ["", "H2 uses a copy of the tracked H2 2.x seed and files with the default/docker H2 profile. "
              f"MySQL uses the repository MySQL profile in an isolated `{MYSQL_IMAGE}` container, bound to loopback. "
              "The harness never accepts an external database address and never changes production configuration.", ""]
    for mode in ("h2", "mysql"):
        if mode in state:
            diff = state[mode]["contract"]
            lines += [f"Checkout SMTP ({mode}): `" + json.dumps(state[mode]["checkout_mail"], sort_keys=True) + "`.", ""]
            lines += [f"### {mode} additive operations", ""]
            lines += [f"- `{key}`" for key in diff["added"]] or ["None."]
            lines += ["", f"Metadata changed on {len(diff['metadataChanged'])} preserved operations; the complete normalized "
                      "inventory and metadata-change list are saved in this run's evidence directory. Operation identities include exact path variable names.", ""]
            lines += ["Changed metadata fields: `" + json.dumps(diff.get("metadataFieldCounts", {}), sort_keys=True) + "`.", "",
                      "The Swagger 2 → OpenAPI 3 normalization exposes media-type declarations and corrected parameter locations "
                      "(for example, an unspecified `store` parameter is now explicitly `query`). These are documentation differences; "
                      "the AB-533 HTTP scenarios check the representative REST/JWT behavior separately.", ""]
    if "ids" in state:
        lines += ["## Existing MySQL dataset and process restart", "",
                  "The first process creates durable products/customers/orders and completes the smoke suite. After it stops, "
                  "the harness snapshots all existing entity IDs and SM_SEQUENCER values. A second process opens the same database, "
                  "creates three more of each entity through the real APIs and checkout, then stops. Read-only SQL checks every prior ID is "
                  "retained, new IDs are disjoint and sequential starting at the existing source counter × 50, and counters advance. "
                  "A third process verifies new entities still read back after another restart. "
                  "This is an existing fixture dataset on the upgraded schema, not a production dump or a Hibernate 5 cutover rehearsal.", "",
                  "| Entity | Existing rows | Counter before → after | First expected | New IDs | Collisions |",
                  "| --- | ---: | --- | ---: | --- | ---: |"]
        for kind, value in state["ids"].items():
            lines.append(f"| {kind} | {value['existing_rows']} | {value['counter_before']} → {value['counter_after']} "
                         f"| {value['first_expected']} | {', '.join(map(str, value['new_ids']))} | 0 |")
        lines.append("")
    lines += ["## Reproduce and final sign-off", "",
              "```sh", "export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64",
              "python3 tools/runtime-parity/run.py --report docs/upgrade/parity-report.md", "```", "",
              "Requires system Maven, Docker and Python 3; no additional test dependencies. MySQL is pulled if needed. "
              "Evidence (build and app logs, Surefire XML, OpenAPI/inventories/diffs, raw coverage, dataset snapshots, result JSON) "
              "is preserved under `.runtime-parity/<run>/`, outside Maven's cleaned target directories. Tokens and database passwords are not written to the report. "
              "The runner exits nonzero on any failed gate, preserves diagnostics, terminates app processes, and stops only its own "
              "uniquely named MySQL container. Stopped containers are retained for diagnosis and can be removed explicitly by their printed name.", "",
              "Before final sign-off, check that AB-545/546/547/549 (and AB-550 if included) are merged into `epic/ab-531-v2`, "
              "run the command on that exact commit, review all additive/metadata differences and commit the regenerated report. "
              "A passing initial report does not close the epic. No load/performance or frontend repository verification is claimed.", ""]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "docs/upgrade/parity-report.md")
    args = parser.parse_args()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    evidence = ROOT / ".runtime-parity" / (stamp + "-" + secrets.token_hex(3))
    evidence.mkdir(parents=True)
    patch = run(["git", "diff", "HEAD", "--", ".", ":(exclude)docs/upgrade/parity-report.md",
                 ":(exclude)sm-shop/files/store/DownlaodRepository.dat"]).encode()
    for filename in run(["git", "ls-files", "--others", "--exclude-standard"]).splitlines():
        if (ROOT / filename).resolve() == args.report.resolve():
            continue
        patch += filename.encode() + b"\0" + (ROOT / filename).read_bytes()
    state = dict(time=stamp, commit=run(["git", "rev-parse", "HEAD"]),
                 patch_sha256=hashlib.sha256(patch).hexdigest(), result="FAIL")
    print("Parity evidence:", evidence, flush=True)
    try:
        require("JAVA_HOME" in os.environ, "Set JAVA_HOME to JDK 21")
        version = subprocess.run([str(Path(os.environ["JAVA_HOME"]) / "bin/java"),
                                  "-XshowSettings:properties", "-version"], capture_output=True, text=True, check=True)
        require("java.specification.version = 21" in version.stderr, "Parity requires exactly JDK 21")
        (evidence / "java-version.txt").write_text(version.stderr)
        run([sys.executable, str(Path(__file__).with_name("test_run.py"))], log=evidence / "harness-tests.log")
        # A clean reactor excludes stale classes, reports and JaCoCo execution data.
        run(["mvn", "-B", "clean", "install"], log=evidence / "build.log")
        paths = sorted(ROOT.glob("*/target/surefire-reports/TEST-*.xml"))
        state["build"] = suite_counts(paths)
        shutil.copytree(ROOT / "sm-shop/target/surefire-reports", evidence / "full-sm-shop-surefire")
        shutil.copytree(ROOT / "sm-core/target/surefire-reports", evidence / "full-sm-core-surefire")
        xml = evidence / "jacoco-aggregate.xml"
        shutil.copy2(ROOT / "coverage/target/site/jacoco-aggregate/jacoco.xml", xml)
        state["coverage"] = coverage(xml)
        state["jar_sha256"] = hashlib.sha256((ROOT / "sm-shop/target/shopizer.jar").read_bytes()).hexdigest()
        with smtp_sink() as smtp:
            h2 = evidence / "h2-runtime"
            h2.mkdir()
            copy_h2_seed(h2)
            with app("h2", h2, smtp.server_address[1]) as url:
                state["h2"] = smoke(url, evidence / "h2", smtp)
            with mysql_container(evidence) as (sql, port, password):
                mysql = evidence / "mysql-runtime"
                mysql.mkdir()
                with app("mysql", mysql, smtp.server_address[1], port, password, "bootstrap") as url:
                    fixture(url)
                    state["mysql"] = smoke(url, evidence / "mysql", smtp)
                before = dataset(sql)
                (evidence / "mysql-before.json").write_text(json.dumps(before, indent=2))
                with app("mysql", mysql, smtp.server_address[1], port, password, "restart") as url:
                    fixtures = [fixture(url) for _ in range(3)]
                after = dataset(sql)
                (evidence / "mysql-after.json").write_text(json.dumps(after, indent=2))
                state["ids"] = verify_continuity(before, after,
                                                {kind: [row[kind] for row in fixtures] for kind in SEGMENTS})
                with app("mysql", mysql, smtp.server_address[1], port, password, "readback") as url:
                    token = request(url + "/api/v1/private/login", "POST",
                                    {"username": "admin@shopizer.com", "password": "password"})["token"]
                    for ids in fixtures:
                        for kind, path in {"product": "/api/v2/private/product/", "customer": "/api/v1/private/customer/",
                                           "order": "/api/v1/private/orders/"}.items():
                            require(request(url + path + str(ids[kind]), token=token)["id"] == ids[kind],
                                    "Restart readback failed: " + kind)
            state["smtp_deliveries"] = smtp.deliveries
        require(all(value["passes"] for value in state["coverage"].values()), "Coverage below AB-533 baseline")
        state["result"] = "PASS"
    except Exception as error:
        # Avoid serializing subprocess arguments, which can contain sensitive configuration.
        state["error"] = type(error).__name__ if isinstance(error, subprocess.CalledProcessError) else str(error)
        print("Parity failed:", state["error"], file=sys.stderr)
    finally:
        (evidence / "result.json").write_text(json.dumps(state, indent=2) + "\n")
        write_report(args.report.resolve(), state)
    print("Parity result:", state["result"], flush=True)
    return 0 if state["result"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
