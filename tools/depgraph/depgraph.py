#!/usr/bin/env python3
"""Import-level dependency audit for the Shopizer multi-module build.

Scans main sources of every module, resolves `com.salesmanager.*` imports and
fully-qualified references to known classes, then reports:

  * module-to-module usage vs. declared <dependency> entries
  * split packages (one package defined in more than one module)
  * package-level dependency cycles (Tarjan SCC)
  * layering violations against the rank table in LAYERS

Usage: python3 tools/depgraph/depgraph.py [--mermaid] [repo-root]
"""
import collections
import os
import re
import sys

MODULES = ['sm-core-model', 'sm-core-modules', 'sm-core', 'sm-shop-model', 'sm-shop']
PKG_RE = re.compile(r'^\s*package\s+([\w.]+)\s*;')
IMP_RE = re.compile(r'^\s*import\s+(static\s+)?([\w.]+?)(\.\*)?\s*;')
FQN_RE = re.compile(r'\b(com\.salesmanager(?:\.\w+)+)')
DEP_RE = re.compile(r'<dependency>\s*<groupId>com\.shopizer</groupId>\s*<artifactId>(sm-[\w-]+)</artifactId>', re.S)

# (rank, layer, package prefix). Longest matching prefix wins; a reference from
# a lower rank to a strictly higher rank is a layering violation.
LAYERS = [
    (0, 'core-model', 'com.salesmanager.core.model'),
    (0, 'core-model', 'com.salesmanager.core.constants'),
    (0, 'core-model', 'com.salesmanager.core.utils'),
    (0, 'core-model', 'com.salesmanager.core.business.exception'),
    (1, 'module-spi', 'com.salesmanager.core.modules'),
    (1, 'shop-model', 'com.salesmanager.shop.model'),
    (1, 'shop-model', 'com.salesmanager.shop.validation'),
    (1, 'shop-model', 'com.salesmanager.shop.util'),
    (2, 'core-utils', 'com.salesmanager.core.business.utils'),
    (2, 'core-utils', 'com.salesmanager.core.business.constants'),
    (3, 'repositories', 'com.salesmanager.core.business.repositories'),
    (4, 'module-impl', 'com.salesmanager.core.business.modules'),
    (5, 'services', 'com.salesmanager.core.business.services'),
    (6, 'core-config', 'com.salesmanager.core.business.configuration'),
    (7, 'shop-exception', 'com.salesmanager.shop.store.api.exception'),
    (7, 'shop-constants', 'com.salesmanager.shop.constants'),
    (8, 'shop-utils', 'com.salesmanager.shop.utils'),
    (9, 'mapper/populator', 'com.salesmanager.shop.mapper'),
    (9, 'mapper/populator', 'com.salesmanager.shop.populator'),
    (10, 'facade', 'com.salesmanager.shop.store.facade'),
    (11, 'web', 'com.salesmanager.shop.store.api'),
    (11, 'web', 'com.salesmanager.shop.store.controller'),
    (11, 'web', 'com.salesmanager.shop.controller'),
    (12, 'shop-config', 'com.salesmanager.shop.application'),
]
SHOP_FACADE_PACKAGES = re.compile(r'^com\.salesmanager\.shop\.store\.controller(\..*)?(\.facade(\..*)?|\.optin|\.system|\.configurations)$')


def layer(pkg):
    if SHOP_FACADE_PACKAGES.match(pkg):
        return 10, 'facade'
    best = None
    for rank, name, prefix in LAYERS:
        if (pkg == prefix or pkg.startswith(prefix + '.')) and (best is None or len(prefix) > len(best[2])):
            best = (rank, name, prefix)
    return (best[0], best[1]) if best else None


def scan(root):
    classes, files = {}, []
    for m in MODULES:
        base = os.path.join(root, m, 'src', 'main', 'java')
        for dp, _, fs in os.walk(base):
            for f in sorted(fs):
                if not f.endswith('.java'):
                    continue
                path = os.path.join(dp, f)
                with open(path, encoding='utf-8', errors='replace') as fh:
                    lines = fh.readlines()
                pkg = next((mm.group(1) for mm in map(PKG_RE.match, lines) if mm), '')
                fqn = pkg + '.' + f[:-5]
                rel = os.path.relpath(path, root)
                classes[fqn] = (m, pkg, rel)
                files.append((m, pkg, rel, fqn, lines))
    by_pkg = collections.defaultdict(list)
    for fqn, (_, pkg, _) in classes.items():
        by_pkg[pkg].append(fqn)

    def resolve(name):
        parts = name.split('.')
        for i in range(len(parts), 2, -1):
            cand = '.'.join(parts[:i])
            if cand in classes:
                return cand
        return None

    edges = []
    for m, pkg, rel, fqn, lines in files:
        for no, line in enumerate(lines, 1):
            mm = IMP_RE.match(line)
            targets = set()
            if mm:
                if not mm.group(2).startswith('com.salesmanager'):
                    continue
                if mm.group(3) and not mm.group(1):
                    targets.update(by_pkg.get(mm.group(2), ()))
                else:
                    r = resolve(mm.group(2))
                    if r:
                        targets.add(r)
            elif not line.lstrip().startswith(('package', '//', '*', '/*')):
                targets.update(r for r in map(resolve, FQN_RE.findall(line)) if r and r != fqn)
            for t in targets:
                tm, tp, _ = classes[t]
                edges.append((m, pkg, rel, no, tm, tp, t))
    return classes, edges


def declared(root):
    out = {}
    for m in MODULES:
        with open(os.path.join(root, m, 'pom.xml'), encoding='utf-8') as fh:
            out[m] = set(DEP_RE.findall(fh.read()))
    return out


def sccs(adj):
    sys.setrecursionlimit(100000)
    index, low, stack, on, out, counter = {}, {}, [], set(), [], [0]
    nodes = sorted(set(adj) | {w for ws in adj.values() for w in ws})

    def visit(v):
        index[v] = low[v] = counter[0]
        counter[0] += 1
        stack.append(v)
        on.add(v)
        for w in sorted(adj.get(v, ())):
            if w not in index:
                visit(w)
                low[v] = min(low[v], low[w])
            elif w in on:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                on.discard(w)
                comp.append(w)
                if w == v:
                    break
            if len(comp) > 1:
                out.append(sorted(comp))
    for n in nodes:
        if n not in index:
            visit(n)
    return out


def short(name):
    return name.replace('com.salesmanager.', '')


def main(argv):
    mermaid = '--mermaid' in argv
    args = [a for a in argv if not a.startswith('--')]
    root = args[0] if args else os.path.join(os.path.dirname(__file__), '..', '..')
    classes, edges = scan(root)
    decl = declared(root)

    mod = collections.Counter((e[0], e[4]) for e in edges if e[0] != e[4])
    if mermaid:
        print('graph BT')
        for (a, b), n in sorted(mod.items()):
            style = '-->' if b in decl[a] else '-.->'
            print(f'  {a.replace("-", "_")}[{a}] {style}|{n}| {b.replace("-", "_")}[{b}]')
        return 0

    print(f'# {len(classes)} main classes, {len(edges)} internal references\n')
    print('## Module usage (observed imports vs declared dependencies)')
    for (a, b), n in sorted(mod.items()):
        print(f'  {a} -> {b}: {n} refs{"" if b in decl[a] else "  [UNDECLARED: relies on transitive dependency]"}')
    for a, b in sorted((a, b) for a, bs in decl.items() for b in bs):
        if (a, b) not in mod:
            print(f'  {a} -> {b}: declared but unused')
    for (a, b), n in sorted(mod.items()):
        if b not in decl[a]:
            for e in edges:
                if (e[0], e[4]) == (a, b):
                    print(f'    {e[2]}:{e[3]} -> {short(e[6])}')

    print('\n## Split packages')
    pm = collections.defaultdict(set)
    for m, p, _ in classes.values():
        pm[p].add(m)
    split = {p: ms for p, ms in pm.items() if len(ms) > 1}
    for p, ms in sorted(split.items()):
        print(f'  {p}: {sorted(ms)}')
        for fqn, (m, pk, rel) in sorted(classes.items()):
            if pk == p:
                print(f'    {rel}')
    if not split:
        print('  none')

    print('\n## Package cycles (strongly connected components)')
    adj = collections.defaultdict(set)
    for e in edges:
        if e[1] != e[5]:
            adj[e[1]].add(e[5])
    comps = sccs(adj)
    for comp in comps:
        members = set(comp)
        print(f'  SCC of {len(comp)}: {", ".join(short(p) for p in comp)}')
        if len(comp) <= 5:
            for e in edges:
                if e[1] in members and e[5] in members and e[1] != e[5]:
                    print(f'    {short(e[1])} -> {short(e[5])}  {e[2]}:{e[3]} ({e[6].rsplit(".", 1)[1]})')

    print('\n## Layering violations (lower layer importing higher layer)')
    viol = collections.defaultdict(list)
    for e in edges:
        a, b = layer(e[1]), layer(e[5])
        if a and b and a[0] < b[0] and b[1] != 'shop-exception':
            viol[(a[1], b[1])].append(e)
    for e in edges:
        a = layer(e[1])
        if layer(e[5]) == (7, 'shop-exception') and a and a[0] < 11:
            viol[(a[1], 'web package (store.api.exception)')].append(e)
    for e in edges:
        if e[0] == 'sm-shop-model' and e[4] == 'sm-core-model':
            viol[('shop-model (DTO module)', 'core-model (JPA entities)')].append(e)
    for (a, b), es in sorted(viol.items(), key=lambda kv: -len(kv[1])):
        print(f'  {a} -> {b}: {len(es)} refs in {len({e[2] for e in es})} files')
        for e in es:
            print(f'    {e[2]}:{e[3]} -> {short(e[6])}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
