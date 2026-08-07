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
    assert completed_event.payload["action_key"] == "mimi-vaccine-2026"
    assert "notification_recorded" in names
    assert {
        item.payload["outcome"]
        for item in result.observations.observations
        if item.name == "notification_recorded"
    } == {"failed", "delivered"}
    notification_review = next(
        item
        for item in result.observations.observations
        if item.type == "query" and item.name == "notification"
    )
    assert notification_review.source == "review-notifications"
    assert notification_review.payload["notification_count"] == 2
    assert set(notification_review.payload["outcomes"]) == {"failed", "delivered"}
    assert notification_review.payload["newest_outcome"] == "delivered"
    assert notification_review.payload["newest_attempted_at"] == "2026-01-05T10:00:00+00:00"
    care_review = next(
        item
        for item in result.observations.observations
        if item.type == "query" and item.name == "care-event"
    )
    assert care_review.source == "review-care"
    assert care_review.payload["care_event_count"] == 1
    assert care_review.payload["responsibility_ids"] == ["mimi-vaccine-1"]
    assert care_review.payload["action_keys"] == ["mimi-vaccine-2026"]
    assert care_review.payload["newest_event_type"] == "weight_measured"
    assert care_review.payload["newest_description"] == "4.2 kg"
    assert care_review.payload["newest_occurred_at"] == "2026-01-04T12:00:00+00:00"
    note_review = next(
        item for item in result.observations.observations
        if item.type == "query" and item.name == "note"
    )
    assert note_review.source == "review-notes"
    assert note_review.payload["note_count"] == 1
    assert note_review.payload["newest_description"] == "eating less"
    assert note_review.payload["is_diagnosis"] is False
    profile_review = next(
        item for item in result.observations.observations
        if item.type == "query" and item.name == "cat-profile"
    )
    assert profile_review.source == "review-profile"
    assert profile_review.correlation_id == "use-case:review_cat_profile"
    assert profile_review.payload["name"] == "Mimi"
    assert profile_review.payload["birth_date"] == "2021-05-01"
    assert profile_review.payload["adoption_date"] == "2021-07-10"
    assert profile_review.payload["photo_ref"] == "mimi-profile-updated.jpg"
    triage_assessed = next(
        item for item in result.observations.observations if item.name == "triage_assessed"
    )
    assert triage_assessed.payload["urgency"] == "needs_attention"
    assert triage_assessed.payload["review_status"] == "pending"
    assert triage_assessed.payload["uncertainty"] == "No examination or vital signs are available."
    triage_reviewed = next(
        item for item in result.observations.observations if item.name == "triage_reviewed"
    )
    assert triage_reviewed.payload["decision"] == "modified"
    assert triage_reviewed.payload["final_urgency"] == "urgent"
    assert triage_reviewed.payload["rationale"] == "Escalate after reviewing the history."
    delivered_notification = next(
        item
        for item in result.observations.observations
        if item.name == "notification_recorded"
        and item.payload["outcome"] == "delivered"
    )
    assert delivered_notification.payload["action_key"] == "mimi-appointment-2026"
    assert "responsibility_deferred" in names
    assert "responsibility_cancelled" in names
    assert "note_recorded" in names
    assert "data_exported" in names
    assert "data_deleted" in names
    deletion = next(
        item for item in result.observations.observations if item.name == "data_deleted"
    )
    assert deletion.payload["deleted_at"] == "2026-01-05T12:00:00+00:00"
    assert deletion.payload["notifications_removed"] == 2
    assert deletion.payload["notes_removed"] == 1
    assert deletion.payload["direct_care_removed"] == 1
    assert deletion.payload["triage_assessments_removed"] == 1
    assert deletion.payload["veterinarian_reviews_removed"] == 1
    notification = next(
        item for item in result.observations.observations if item.name == "notification_recorded"
    )
    assert notification.payload["action_key"] == "mimi-vaccine-2026"
    care_event = next(
        item for item in result.observations.observations if item.name == "weight_measured"
    )
    assert care_event.payload["action_key"] == "mimi-vaccine-2026"
    cancellation = next(
        item for item in result.observations.observations if item.name == "responsibility_cancelled"
    )
    assert cancellation.payload["action_key"] == "mimi-appointment-2026"
    deferral = next(
        item for item in result.observations.observations if item.name == "responsibility_deferred"
    )
    assert deferral.payload["action_key"] == "mimi-vaccine-2026"
    created_food = next(
        item for item in result.observations.observations if item.name == "responsibility_created"
    )
    edited_food = next(
        item for item in result.observations.observations if item.name == "responsibility_edited"
    )
    assert created_food.payload["action_key"] == "mimi-food-2026"
    assert edited_food.payload["action_key"] == "mimi-food-2026"
    rejection = next(
        item for item in result.observations.observations if item.type == "command_rejected"
    )
    assert rejection.name == "complete_responsibility"
    assert rejection.payload["action_key"] == "mimi-vaccine-2026"
    assert "already completed" in rejection.payload["reason"]
    assert rejection.payload["attempted_at"] == "2026-01-04T13:00:00+00:00"
    assert rejection.payload["responsibility_state"] == "completed"
    assert rejection.payload["action_key"] == "mimi-vaccine-2026"
    assert any(
        item.type == "command_rejected"
        and item.name == "responsibility"
        and item.source == "manage-responsibility"
        and item.correlation_id == "use-case:complete_responsibility"
        for item in result.observations.observations
    )
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
        "review-notifications",
        "review-care",
        "review-notes",
        "review-profile",
        "triage-care",
        "review-triage",
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


def test_observatory_graph_exposes_integration_adapters_in_their_own_rank():
    scenario = create_simulation()
    adapters = {
        node.id: node
        for node in scenario.observatory_nodes
        if node.kind == "outbound_adapter"
    }
    assert set(adapters) == {
        "profile-adapter",
        "notification-adapter",
        "triage-adapter",
        "recurrence-adapter",
    }
    assert {node.layer for node in adapters.values()} == {"external_providers"}
    projection_ids = {node.id for node in scenario.observatory_nodes if node.layer == "projections"}
    assert all(
        node.layer != "projections"
        for node in adapters.values()
    )
    assert projection_ids
    assert {
        (edge.from_node, edge.to_node)
        for edge in scenario.observatory_edges
        if edge.label == "translates-to"
    } == {
        ("profile-adapter", "cat-profile"),
        ("notification-adapter", "notification"),
        ("triage-adapter", "triage-assessment"),
        ("recurrence-adapter", "responsibility"),
    }


def test_runtime_adapter_handoffs_preserve_use_case_correlation():
    result = SimulationRunner().run(create_simulation())
    adapter_commands = [
        item
        for item in result.observations.observations
        if item.type == "command" and item.payload.get("boundary") == "integration_adapter"
    ]
    assert adapter_commands
    assert {item.name for item in adapter_commands} >= {
        "profile-adapter",
        "notification-adapter",
        "triage-adapter",
        "recurrence-adapter",
    }
    assert all(item.correlation_id.startswith("use-case:") for item in adapter_commands)
