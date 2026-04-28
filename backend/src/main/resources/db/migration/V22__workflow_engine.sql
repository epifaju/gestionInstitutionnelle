-- Workflow engine (definitions, rules, instances, actions, delegations)

CREATE TABLE IF NOT EXISTS workflow_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL,
    process_key VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, process_key)
);

CREATE TABLE IF NOT EXISTS workflow_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id UUID NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    min_amount_eur NUMERIC(14,2),
    max_amount_eur NUMERIC(14,2),
    levels INT NOT NULL DEFAULT 1,
    level1_role VARCHAR(40) NOT NULL,
    level2_role VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_workflow_rules_definition ON workflow_rules(definition_id);

CREATE TABLE IF NOT EXISTS workflow_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL,
    process_key VARCHAR(80) NOT NULL,
    subject_type VARCHAR(80) NOT NULL,
    subject_id UUID NOT NULL,
    amount_eur NUMERIC(14,2),
    status VARCHAR(40) NOT NULL,
    current_level INT NOT NULL DEFAULT 1,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE (organisation_id, process_key, subject_type, subject_id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_instances_subject ON workflow_instances(subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_workflow_instances_org_key ON workflow_instances(organisation_id, process_key);

CREATE TABLE IF NOT EXISTS workflow_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    level INT NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_role VARCHAR(40) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_workflow_actions_instance ON workflow_actions(instance_id);

CREATE TABLE IF NOT EXISTS workflow_delegations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL,
    from_role VARCHAR(40) NOT NULL,
    to_user_id UUID NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_workflow_delegations_org_role ON workflow_delegations(organisation_id, from_role);
CREATE INDEX IF NOT EXISTS idx_workflow_delegations_to_user ON workflow_delegations(organisation_id, to_user_id);

