-- Templates library (DOCX/HTML) + generated documents

CREATE TABLE IF NOT EXISTS template_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    label VARCHAR(200) NOT NULL,
    category VARCHAR(40) NOT NULL,
    format VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    default_locale VARCHAR(10),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, code)
);

CREATE TABLE IF NOT EXISTS template_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES template_definitions(id) ON DELETE CASCADE,
    version INT NOT NULL,
    content_document_id UUID,
    content_object_name TEXT,
    content_mime VARCHAR(120) NOT NULL,
    checksum VARCHAR(80),
    comment TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (template_id, version)
);

CREATE INDEX IF NOT EXISTS idx_template_revisions_template ON template_revisions(template_id);

CREATE TABLE IF NOT EXISTS generated_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL,
    template_revision_id UUID NOT NULL REFERENCES template_revisions(id) ON DELETE RESTRICT,
    subject_type VARCHAR(80) NOT NULL,
    subject_id UUID NOT NULL,
    output_document_id UUID,
    output_format VARCHAR(10) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_generated_documents_subject ON generated_documents(subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_generated_documents_org ON generated_documents(organisation_id);

