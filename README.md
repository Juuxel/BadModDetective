# Bad Mod Detective

Detects and crashes potentially bugged Fabric mods containing these issues:

- Missing `${version}` replacements (`${version}` in production environments)
- Unnamed mixin refmaps caused by Jitpack (`build-refmap.json`)
- Outdated schemas (v0)
- Using `requires` from v0 schema in v1 fabric.mod.json (instead of `depends`)
