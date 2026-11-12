# 0004. Drools 7.74 → 10.2 (Apache KIE, Jakarta) for shipping and promo rules

- **Status:** Proposed
- **Date:** 2026-11-11
- **ARB ticket:** TO BE CREATED
- **Authors:** Devin (AB-545)
- **Owning team:** TBD — owner to confirm before ARB
- **Related ADRs:** 0003 (Spring Boot 3.5 / Java 21 / Jakarta cut-over, the direct predecessor). Epic AB-531.

## Context

After AB-544, Drools 7.74.1.Final (Red Hat KIE, javax line) is the last rules-engine component on the
pre-Jakarta line. Shopizer uses it in `sm-core` for the shipping decision / price-by-distance DRL rules
(`DroolsBeanFactory`, `ShippingDecisionPreProcessorImpl`, `CustomShippingQuoteRules`) and the promo-code
decision table (`PromoCodeCalculatorModule`). Drools 10.x is the Apache KIE (incubating) Jakarta line.

ARB trigger: **T7 — major framework upgrade** (Drools 7 → 10, project governance moved from Red Hat
KIE to the Apache Software Foundation). Not triggered: T1, T2, T4, T5, T6, T8, T9 (no new service, data
store, integration, infrastructure, network or auth change; same vendor role, rule logic unchanged).

## Decision

- Import `org.drools:drools-bom` 10.2.0 in the root `dependencyManagement`; `sm-core` depends on
  `drools-engine`, `drools-xml-support`, `drools-mvel` and `drools-decisiontables`. MVEL resolves to
  2.5.2.Final from the BOM (JDK 21 safe), so the explicit MVEL pin is removed.
- `DroolsBeanFactory` builds with the classic `DrlProject` (no executable model), fails fast on build
  errors, and creates containers from the built module's `ReleaseId` instead of the global default
  `KieRepository` release.
- Three `sm-shop` classes that imported `org.drools.core.util.StringUtils` (removed in Drools 10) use
  Commons Lang 3 `StringUtils.isEmpty` with identical semantics.
- DRL files, decision table and rule tests are unchanged.

## Alternatives considered

- **Stay on Drools 7.74:** javax-line, no further OSS fixes; blocks the epic's "no javax Drools" goal.
- **Drools 8.x:** Jakarta-compatible but superseded by the Apache 10.x line.
- **Executable model (`drools-model-compiler` build):** larger behavioural surface; classic DRL keeps
  the existing MVEL semantics. Can be revisited separately.
- **Replace Drools with plain Java:** a rule-logic rewrite, out of scope.

## Consequences / NFRs

- Behaviour parity is pinned by `ShippingMethodDecisionTest` and `DroolsRuleParityTest` (unchanged, green on JDK 21).
- Rules are compiled once at bean creation; startup log shows `defaultKieBase` creation without errors.
- Security: Apache 2.0 licence unchanged; no new network access.
- Rollback: revert this PR (POM + `DroolsBeanFactory` + three imports).
