from mrl_simulation_runtime.runner import SimulationRunner

from app.simulation.mrl_runtime_scenario import (
    USE_CASE_COMMAND_TARGETS,
    USE_CASE_NODE_IDS,
    create_simulation,
)


def test_first_slice_produces_status_transition_and_invariant_evidence():
    result = SimulationRunner().run(create_simulation())
    names = [item.name for item in result.observations.observations]
    assert "care_status_derived" in names
    assert "responsibility_completed" in names
    completed_event = next(
        item for item in result.observations.observations if item.name == "responsibility_completed"
    )
    assert completed_event.payload["details"] == {
        "next_responsibility_id": "mimi-vaccine-1-next",
        "next_due_at": "2026-02-10T09:00:00+00:00",
    }
    assert "notification_recorded" in names
    assert "responsibility_deferred" in names
    assert "responsibility_cancelled" in names
    assert "note_recorded" in names
    assert "data_exported" in names
    assert "data_deleted" in names
    deletion = next(
        item for item in result.observations.observations if item.name == "data_deleted"
    )
    assert deletion.payload["notifications_removed"] == 1
    assert deletion.payload["notes_removed"] == 1
    assert deletion.payload["direct_care_removed"] == 1
    assert "use_case_invoked" in [item.type for item in result.observations.observations]
    assert any(
        item.type == "query"
        and item.name == "timeline"
        and item.source == "review-history"
        and item.payload["event_count"] > 0
        and item.payload["newest_event_type"] == "cat_profile_edited"
        and item.payload["newest_event_at"] == "2026-01-01T14:00:00+00:00"
        for item in result.observations.observations
    )
    assert any(
        item.type == "query"
        and item.name == "status"
        and item.source == "review-status"
        and item.payload["kind"] == "overdue"
        and item.payload["nearest_responsibility_id"] == "mimi-vaccine-1"
        and item.correlation_id == "use-case:review_care_status"
        for item in result.observations.observations
    )
    assert any(
        item.type == "event"
        and item.name == "status"
        and item.source == "responsibility"
        and item.payload["domain_event"] == "responsibility_completed"
        and item.payload["responsibility_id"] == "mimi-vaccine-1"
        and item.payload["description"] == "vaccine"
        for item in result.observations.observations
    )
    transitions = [
        item.payload
        for item in result.observations.observations
        if item.name == "care_status_transition"
    ]
    assert {("planned", "due_soon"), ("due_soon", "overdue")} <= {
        (item["from"], item["to"]) for item in transitions
    }
    overdue_transition = next(
        item for item in transitions if item["from"] == "due_soon" and item["to"] == "overdue"
    )
    assert overdue_transition["previous_nearest_responsibility_id"] == "mimi-vaccine-1"
    assert overdue_transition["nearest_responsibility_id"] == "mimi-vaccine-1"
    assert overdue_transition["sentence"] == "Something important is overdue."
    use_cases = {item.name for item in result.observations.observations if item.type == "use_case_invoked"}
    assert {"review_care_status", "create_responsibility", "complete_responsibility"}.issubset(use_cases)
    assert any(
        item.type == "command"
        and item.name == "manage-responsibility"
        and item.source == "owner"
        for item in result.observations.observations
    )
    assert any(
        item.type == "command"
        and item.name == "responsibility"
        and item.source == "manage-responsibility"
        and item.payload["use_case"] == "complete_responsibility"
        for item in result.observations.observations
    )
    assert any(
        item.type == "command"
        and item.name == "cat-profile"
        and item.source == "manage-cat-profile"
        and item.payload["use_case"] == "edit_cat_profile"
        for item in result.observations.observations
    )
    assert any(
        item.type == "command"
        and item.name == "notification"
        and item.source == "deliver-notification"
        and item.payload["use_case"] == "record_notification"
        for item in result.observations.observations
    )
    assert {
        item.payload["use_case"]
        for item in result.observations.observations
        if item.type == "command" and item.name == "data-lifecycle"
    } >= {"export_data", "delete_data"}
    assert any(
        item.type == "command"
        and item.name == "care-event"
        and item.source == "record-care"
        and item.payload["use_case"] == "record_care_event"
        for item in result.observations.observations
    )
    assert any(
        item.type == "command"
        and item.name == "note"
        and item.source == "record-care"
        and item.payload["use_case"] == "record_note"
        for item in result.observations.observations
    )
    responsibility_commands = [
        item
        for item in result.observations.observations
        if item.type == "command" and item.name == "responsibility"
    ]
    assert responsibility_commands
    assert all(item.correlation_id.startswith("use-case:") for item in responsibility_commands)
    declared_use_case_ids = {
        node.id for node in create_simulation().observatory_nodes if node.kind == "use_case"
    }
    use_case_command_targets = {
        item.name for item in result.observations.observations if item.type == "command"
        and item.source == "owner"
    }
    assert use_case_command_targets <= declared_use_case_ids
    assert set(USE_CASE_NODE_IDS.values()) <= declared_use_case_ids
    declared_node_ids = {node.id for node in create_simulation().observatory_nodes}
    assert set(USE_CASE_COMMAND_TARGETS.values()) <= declared_node_ids
    assert any(item.name == "timeline_is_newest_first" and item.payload["passed"] for item in result.observations.observations)
    assert any(item.name == "completed_responsibility_is_not_overdue" and item.payload["passed"] for item in result.observations.observations)


def test_observatory_graph_exposes_application_use_cases():
    scenario = create_simulation()
    use_case_ids = {node.id for node in scenario.observatory_nodes if node.kind == "use_case"}
    assert use_case_ids == {
        "review-status",
        "review-history",
        "manage-responsibility",
        "manage-cat-profile",
        "record-care",
        "deliver-notification",
        "manage-data",
    }
    assert not any(
        edge.from_node == "review-status" and edge.to_node == "status"
        for edge in scenario.observatory_edges
    )
    status = next(node for node in scenario.observatory_nodes if node.id == "status")
    assert status.layer == "projections"
