# Cat Care simulation

This is the first MRL simulation project for Cat Care. It models one evolving
care environment rather than one application per slice.

## Run

```bash
PYTHONPATH=sandboxes/simulation/src python3 -m pytest sandboxes/simulation/tests
mrl-simulation supervise
```

The runtime adapter is `app.simulation.mrl_runtime_scenario:create_simulation`.

## Current slice

`responsibility-status` tests the smallest useful promise: given a cat's
responsibilities and the current time, produce a trustworthy calm status. It
deliberately represents incomplete information as uncertainty rather than
false reassurance.

