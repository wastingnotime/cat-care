from mrl_simulation_runtime.runner import SimulationRunner

from app.simulation.mrl_runtime_scenario import create_simulation


def test_first_slice_produces_status_transition_and_invariant_evidence():
    result = SimulationRunner().run(create_simulation())
    names = [item.name for item in result.observations.observations]
    assert "care_status_derived" in names
    assert "responsibility_completed" in names
    assert any(item.name == "completed_responsibility_is_not_overdue" and item.payload["passed"] for item in result.observations.observations)

