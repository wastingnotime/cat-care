from mrl_simulation_runtime.runner import SimulationRunner

from app.simulation.mrl_runtime_scenario import USE_CASE_NODE_IDS, create_simulation


def test_first_slice_produces_status_transition_and_invariant_evidence():
    result = SimulationRunner().run(create_simulation())
    names = [item.name for item in result.observations.observations]
    assert "care_status_derived" in names
    assert "responsibility_completed" in names
    assert "notification_recorded" in names
    assert "responsibility_deferred" in names
    assert "responsibility_cancelled" in names
    assert "note_recorded" in names
    assert "data_exported" in names
    assert "data_deleted" in names
    assert "use_case_invoked" in [item.type for item in result.observations.observations]
    use_cases = {item.name for item in result.observations.observations if item.type == "use_case_invoked"}
    assert {"review_care_status", "create_responsibility", "complete_responsibility"}.issubset(use_cases)
    assert any(
        item.type == "command"
        and item.name == "manage-responsibility"
        and item.source == "owner"
        for item in result.observations.observations
    )
    declared_use_case_ids = {
        node.id for node in create_simulation().observatory_nodes if node.kind == "use_case"
    }
    command_targets = {
        item.name for item in result.observations.observations if item.type == "command"
    }
    assert command_targets <= declared_use_case_ids
    assert set(USE_CASE_NODE_IDS.values()) <= declared_use_case_ids
    assert any(item.name == "timeline_is_newest_first" and item.payload["passed"] for item in result.observations.observations)
    assert any(item.name == "completed_responsibility_is_not_overdue" and item.payload["passed"] for item in result.observations.observations)


def test_observatory_graph_exposes_application_use_cases():
    scenario = create_simulation()
    use_case_ids = {node.id for node in scenario.observatory_nodes if node.kind == "use_case"}
    assert use_case_ids == {
        "review-status",
        "manage-responsibility",
        "manage-cat-profile",
        "record-care",
        "deliver-notification",
        "manage-data",
    }
