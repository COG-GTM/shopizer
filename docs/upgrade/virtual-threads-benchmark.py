import concurrent.futures
import json
import math
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8081"
ENDPOINTS = ["/api/v1/products?store=DEFAULT", "/api/v1/category?store=DEFAULT"]

def read(path):
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(BASE + path, timeout=30) as response:
            data = json.loads(response.read())
            ok = response.status == 200 and bool(data)
    except Exception:
        ok = False
    return (time.perf_counter() - start) * 1000, ok

mode = sys.argv[1]
for path in ENDPOINTS:
    elapsed, ok = read(path)
    print(json.dumps(dict(mode=mode, smoke=path, ok=ok)), flush=True)
request = urllib.request.Request(BASE + "/api/v1/private/login",
    data=json.dumps(dict(username="admin@shopizer.com", password="password")).encode(),
    headers={"Content-Type": "application/json"})
with urllib.request.urlopen(request, timeout=30) as response:
    body = json.loads(response.read())
    print(json.dumps(dict(mode=mode, login_status=response.status,
        token_present=bool(body.get("token")))), flush=True)
for path in ENDPOINTS:
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(read, [path] * 100))
    for concurrency in [4, 32]:
        count = 2000
        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
            start = time.perf_counter()
            results = list(pool.map(read, [path] * count))
            seconds = time.perf_counter() - start
        times = sorted(result[0] for result in results)
        print(json.dumps(dict(mode=mode, endpoint=path, concurrency=concurrency,
            requests=count, requests_per_second=round(count / seconds, 2),
            p50_ms=round(times[math.ceil(count * .5) - 1], 2),
            p99_ms=round(times[math.ceil(count * .99) - 1], 2),
            errors=sum(not result[1] for result in results))), flush=True)
