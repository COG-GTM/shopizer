# Track 5 – Frontend modernization: blocker and decision record

Status: **Blocked – needs a decision** · Backend baseline: Shopizer 3.2.7 → Spring Boot 3.5 / Java 21 (Tracks 1–4)

## Context

Shopizer's UIs live outside this repository (see `README.md`):

| UI | Docker image | Source repo (upstream) | Stack at last release |
|---|---|---|---|
| Admin | `shopizerecomm/shopizer-admin` | `shopizer-ecommerce/shopizer-admin` | Angular 11, ngx-admin / Nebular, Node 12-era build |
| Shop | `shopizerecomm/shopizer-shop-reactjs` | `shopizer-ecommerce/shopizer-shop-reactjs` | React 16 + Create React App, Redux, Node 12-era build |

Track 5 asked for both apps to be upgraded to current Angular/React LTS and Node LTS, their API clients regenerated from the new springdoc spec, and their Docker builds updated, with PRs opened against the frontend repos.

## Blocker

* `COG-GTM` has no forks of either frontend, and none of the expected COG-GTM repo names resolve.
* `shopizerecomm/shopizer-admin` returns **403** through the GitHub proxy available to Devin.
* The upstream `shopizer-ecommerce/*` repos are public and readable, but Devin cannot (and should not) push to them.
* No one has approved creating new repos or forks under `COG-GTM`.

So there is currently no repo where a Track 5 PR can land.

## What the backend now exposes (input for any frontend option)

* OpenAPI 3 spec: `GET /v3/api-docs` (full) and `GET /v3/api-docs/shopizer` (the `shopizer` group: `/api/v1/**`, `/api/v2/**`).
* Swagger UI: `/swagger-ui/index.html` (`/swagger-ui.html` redirects there).
* REST paths, payloads and JWT auth (`/api/v1/private/login`, `/api/v1/customer/login`, `Authorization: Bearer …`) are **unchanged** by Tracks 1–4. The existing 3.2.x admin and shop images should keep working against the upgraded backend. They were not re-tested in this session.
* Two behaviour changes a client may notice: request DTOs annotated with `@Valid` are now validated (Hibernate Validator ships with springdoc), so malformed bodies get a 400 instead of being accepted; trailing slashes are still accepted via Spring's `UrlHandlerFilter`.

## Options

### A. Fork the upstream repos into COG-GTM and upgrade in place (recommended)
1. Fork `shopizer-ecommerce/shopizer-admin` and `shopizer-ecommerce/shopizer-shop-reactjs` into `COG-GTM`.
2. Admin: run `ng update` one major version at a time, 11 → 20 (Angular's supported path), then upgrade ngx-admin/Nebular, RxJS 7 and TypeScript 5.x.
3. Shop: move from CRA to Vite on React 18/19, update react-redux/redux-toolkit and react-router 6+.
4. Both: switch the Dockerfiles to `node:22-alpine` for the build stage and `nginx:stable-alpine` for the runtime stage, and pin Node with `.nvmrc` and `engines`.
5. Generate typed clients from `/v3/api-docs/shopizer` with `openapi-generator-cli` (`typescript-angular` for admin, `typescript-fetch` or `typescript-axios` for shop) and replace the hand-written services one at a time.

*Pros:* keeps the existing UX and history, and each step can be reviewed on its own.
*Cons:* the jump from Angular 11 to 20 touches many files, and ngx-admin's upstream maintenance is limited.

### B. Vendor the frontends into this repo (monorepo)
Import both apps under `frontend/admin` and `frontend/shop` (git subtree, keeping history), then do the upgrades from Option A here, and build them in CI next to the backend.

*Pros:* one PR flow; the OpenAPI client can be generated during the Maven/CI build, so the frontends can't drift from the backend.
*Cons:* adds Node tooling to a Maven repo; CircleCI needs a second executor.

### C. Rebuild a minimal frontend from scratch
Build new admin and storefront apps (for example Angular 20 standalone and Next.js or Vite + React 19) on top of the generated OpenAPI client.

*Pros:* modern from day one, with no legacy dependency debt.
*Cons:* the most effort, and no feature parity at the start.

## Decision needed

| # | Question | Default if no answer |
|---|---|---|
| 1 | May Devin create forks or repos under `COG-GTM` for the two frontends? | No action |
| 2 | Option A (fork), B (vendor) or C (rebuild)? | **A** |
| 3 | Target versions | Angular 20 LTS, React 19, Node 22 LTS |

Once (1) and (2) are answered, Track 5 can run fully in parallel with Tracks 1–4. The only input it needs is the springdoc spec above.
