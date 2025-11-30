-- Ajuste de constraint única em pix_event para considerar (event_id, end_to_end_id)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'pix_event_event_id_key'
          AND table_name = 'pix_event'
    ) THEN
        ALTER TABLE pix_event DROP CONSTRAINT pix_event_event_id_key;
    END IF;
EXCEPTION WHEN undefined_object THEN
    -- constraint não existe, seguir adiante
    NULL;
END$$;

-- Criar constraint única composta se ainda não existir
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uk_pix_event_eventid_endtoend'
          AND table_name = 'pix_event'
    ) THEN
        ALTER TABLE pix_event ADD CONSTRAINT uk_pix_event_eventid_endtoend UNIQUE (event_id, end_to_end_id);
    END IF;
END$$;
