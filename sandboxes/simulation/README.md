# Cat Care simulation

This is the first MRL simulation project for Cat Care. It models one evolving
care environment rather than one application per slice.

## Run

```bash
PYTHONPATH="$HOME/.wnt/runtime/mrl:sandboxes/simulation/src" \
  python3 -m pytest sandboxes/simulation/tests
mrl-simulation supervise
```

The runtime adapter is `app.simulation.mrl_runtime_scenario:create_simulation`.

## Current model

The release candidate combines responsibility status, care history, owner
notification decisions, data stewardship, and provisional care triage in one
deterministic environment. The current scope and release evidence are mapped in
`work/changes/model-release-2026-09-01/model_slice_map.md`.
