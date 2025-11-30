-- Remove todas as constraints UNIQUE antigas de event_id na tabela pix_event
-- e garante que apenas a constraint composta (event_id, end_to_end_id) exista

-- Primeiro, remover a constraint única simples se existir (criada pela V1)
ALTER TABLE pix_event DROP CONSTRAINT IF EXISTS pix_event_event_id_key;

-- Remover outras possíveis variações de nome da constraint
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_name = 'pix_event'
          AND constraint_type = 'UNIQUE'
          AND constraint_name != 'uk_pix_event_eventid_endtoend'
    LOOP
        EXECUTE 'ALTER TABLE pix_event DROP CONSTRAINT IF EXISTS ' || constraint_record.constraint_name;
    END LOOP;
END$$;

-- Criar constraint única composta se ainda não existir
ALTER TABLE pix_event DROP CONSTRAINT IF EXISTS uk_pix_event_eventid_endtoend;
ALTER TABLE pix_event ADD CONSTRAINT uk_pix_event_eventid_endtoend UNIQUE (event_id, end_to_end_id);

-- Log de sucesso
DO $$
BEGIN
    RAISE NOTICE 'Constraint única composta (event_id, end_to_end_id) aplicada com sucesso em pix_event';
END$$;

