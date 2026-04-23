# Changelog

## [0.4.0](https://github.com/SpongePowered/SystemOfADownload/compare/v0.3.3...v0.4.0) (2026-04-23)


### Features

* **sync:** split version polling into fast metadata + hourly search schedules ([e620bfa](https://github.com/SpongePowered/SystemOfADownload/commit/e620bfa6bbd0ac0e132203a341707167746c4295))

## [0.3.3](https://github.com/SpongePowered/SystemOfADownload/compare/v0.3.2...v0.3.3) (2026-04-22)


### Bug Fixes

* **logging:** raise 4xx HTTP log levels via httplog v3 ([f768c51](https://github.com/SpongePowered/SystemOfADownload/commit/f768c51a2e2717d8991407571e6d6cd0da819b8d))


### Performance Improvements

* **repository:** paginate before joining assets in version listing ([25e9027](https://github.com/SpongePowered/SystemOfADownload/commit/25e9027942156865d10298c21b3104b4d9934733))

## [0.3.2](https://github.com/SpongePowered/SystemOfADownload/compare/v0.3.1...v0.3.2) (2026-04-20)


### Bug Fixes

* **gitcache:** restore Windows build by isolating POSIX process-group logic ([f410530](https://github.com/SpongePowered/SystemOfADownload/commit/f410530fa17c6066c86f864b93ac87024fcbed21))
* **otel:** honor OTEL_METRICS_EXPORTER=none to skip OTLP metric push ([6524673](https://github.com/SpongePowered/SystemOfADownload/commit/65246739dfdafe57de9c51d786c67ff7e00fed2f))

## [0.3.1](https://github.com/SpongePowered/SystemOfADownload/compare/v0.3.0...v0.3.1) (2026-04-17)


### Bug Fixes

* **api:** clamp GetVersions limit above max instead of rejecting ([c2b9ead](https://github.com/SpongePowered/SystemOfADownload/commit/c2b9ead734c89c70f815d958fb5158a42a34df0c))

## [0.3.0](https://github.com/SpongePowered/SystemOfADownload/compare/v0.2.0...v0.3.0) (2026-04-14)


### Features

* add ForceReindex flag to VersionSyncWorkflow ([aef937a](https://github.com/SpongePowered/SystemOfADownload/commit/aef937a53dcc3810c6f2e5c6ef68d92fcc40609e))
* add recommended filter to getLatestVersion endpoint ([85cd0f1](https://github.com/SpongePowered/SystemOfADownload/commit/85cd0f1ab9eb064021a20659ec682c7251c5fef7))
* add scope-based token authorization ([060e600](https://github.com/SpongePowered/SystemOfADownload/commit/060e600d8b2599437b9bdbf1192b71161c7e3497))
* add SSR frontend binary for downloads UI ([eb280f0](https://github.com/SpongePowered/SystemOfADownload/commit/eb280f007a3fc4a0d0281597a316228a3064bd28))
* add TriggerSync endpoint for on-demand artifact syncing ([5240bbd](https://github.com/SpongePowered/SystemOfADownload/commit/5240bbd857a2cfd45c796a878c2fa9a6d44770b0))
* capture asset checksums and extension, filter checksum files ([fb2b57b](https://github.com/SpongePowered/SystemOfADownload/commit/fb2b57b94191bdbd4d9da288e45ca4c196dbdce3))
* enable Temporal Worker Deployment Versioning for safe rolling updates ([46d333e](https://github.com/SpongePowered/SystemOfADownload/commit/46d333e788715af32043a236bfd807cd37dda198))
* **frontend:** content-hashed embedded assets for safe immutable caching ([3aa64a0](https://github.com/SpongePowered/SystemOfADownload/commit/3aa64a0ea37d4707bf3be8d0f65663b6edbedbf2))
* **frontend:** render settings page with preference toggles ([40df6f1](https://github.com/SpongePowered/SystemOfADownload/commit/40df6f1354283158c018d6fd045a51d2826fec68))
* **frontend:** windowed pagination with ellipsis on downloads page ([0ca2c5b](https://github.com/SpongePowered/SystemOfADownload/commit/0ca2c5be7405d1aa99d9d2ee439898368fe79232))
* **gitcache:** extract RepoReader interface and add go-git read backend ([ac6eb68](https://github.com/SpongePowered/SystemOfADownload/commit/ac6eb6820dad65750f840e5a6f8bca57b7955f54))
* persist GitHub commit URLs on enriched commits ([fbe6894](https://github.com/SpongePowered/SystemOfADownload/commit/fbe68946222ec7aa6d792a702df2f98b295ec43a))
* Prometheus metrics, trace-correlated logging, request logging ([990b109](https://github.com/SpongePowered/SystemOfADownload/commit/990b109bd1602fe36b31ea5dac9c50d7466b21d1))
* render sponsors on the SSR frontend ([ea64aa6](https://github.com/SpongePowered/SystemOfADownload/commit/ea64aa6b5d83e2ab861ded6039d5a19816f7bd2f))
* replace fire-and-forget sync with Temporal Schedule ([873b199](https://github.com/SpongePowered/SystemOfADownload/commit/873b199242f118aae8de61a2a06b2d890fc5322d))
* support multiple admin API tokens ([c9a3bab](https://github.com/SpongePowered/SystemOfADownload/commit/c9a3bab7e95daee95ba1e672049ecc74f5d573f3))
* surface changelog commits in version info response ([7457b8d](https://github.com/SpongePowered/SystemOfADownload/commit/7457b8d7a60aaa8692147aa02776edae74b1d810))
* **workflow:** add ForceChangelog flag to re-compute changelogs without full reindex ([293354d](https://github.com/SpongePowered/SystemOfADownload/commit/293354dbe3fab81b1f6fe9a5d05661c86ea7fcf0))


### Bug Fixes

* break slog/log.Default() deadlock in otelslog integration ([3a075be](https://github.com/SpongePowered/SystemOfADownload/commit/3a075bec4f6eff999f6fc8e168053b014bd04415))
* **docker:** install tini as orphan reaper for SOAD containers ([d5455b8](https://github.com/SpongePowered/SystemOfADownload/commit/d5455b8de434a2beebd3467f6f27e215d590f51b))
* **frontend:** recognise bare MC version filters as legacy ([ef23bc6](https://github.com/SpongePowered/SystemOfADownload/commit/ef23bc6c148a5ae94424b05d47bcecb3a9eeabd2))
* **gitcache:** normalize UTC Z/+00:00 in dual-backend comparison test ([e6cf362](https://github.com/SpongePowered/SystemOfADownload/commit/e6cf362e8aff96c11cb4511daf5be2535cb142cd))
* **gitcache:** prevent zombie git processes from exhausting node PIDs ([cf0fcba](https://github.com/SpongePowered/SystemOfADownload/commit/cf0fcba36a04efcc6fae1825e20c32ab6b20bd0e))
* **gitcache:** remove exclusion set cap in ComputeChangelog to match git log A..B semantics ([92f1b54](https://github.com/SpongePowered/SystemOfADownload/commit/92f1b546ce9fb3bb760b2b43ec0450a6307ba9c1))
* gracefully handle corrupt/placeholder JARs in commit extraction ([882ef2e](https://github.com/SpongePowered/SystemOfADownload/commit/882ef2ea0354998ce57a1e1111ad79e29d9f1f04))
* increase Fx startup timeout and bound external service connections ([f758d48](https://github.com/SpongePowered/SystemOfADownload/commit/f758d48a0d107046a797d969ec3abab319083fe2))
* populate empty webfont binaries for SSR frontend ([1c0c6a3](https://github.com/SpongePowered/SystemOfADownload/commit/1c0c6a3fd4a18ce15ced11600c9bb390c978b7fa))
* preserve enrichment fields on artifact version upsert ([0ae4e29](https://github.com/SpongePowered/SystemOfADownload/commit/0ae4e293c5714506e9559255626a04670f8803a5))
* preserve error statuses in StoreChangelog and skip empty commit messages in frontend ([bc95415](https://github.com/SpongePowered/SystemOfADownload/commit/bc9541508c76d5f5b56968bdbd88c07754990871))
* resolve OTel schema URL conflict, instrument DB and HTTP clients ([eb45a5f](https://github.com/SpongePowered/SystemOfADownload/commit/eb45a5f2d812733a101179d35221b8c8aa1338fb))
* serve favicon for SSR frontend ([05f7d1f](https://github.com/SpongePowered/SystemOfADownload/commit/05f7d1f4927b78386ddcf2c1c489d3b8fbfbe9f9))
* set DefaultVersioningBehavior to AutoUpgrade for workflow registration ([9ff3ecf](https://github.com/SpongePowered/SystemOfADownload/commit/9ff3ecfd6e99f873b4acb8c02261db3f5f376309))
* suppress legacy Nexus hosted repos from asset search ([765d249](https://github.com/SpongePowered/SystemOfADownload/commit/765d249ea598aafc2287d496de9fc5d76590cbf6))


### Performance Improvements

* cross-compile in Dockerfile, eliminate QEMU for Go builds ([072ce66](https://github.com/SpongePowered/SystemOfADownload/commit/072ce66ec5b75bb103214527bd59c2f43d03b032))

## Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This file is automatically generated by [release-please](https://github.com/googleapis/release-please).
