-- Adicionar índices para melhorar performance de consultas de auditoria

-- Índice para consultas de ledger por carteira e timestamp (saldo histórico)
CREATE INDEX IF NOT EXISTS idx_ledger_wallet_created ON ledger_entry(wallet_id, created_at);

-- Índice para consultas de ledger por transação (rastreamento de transação específica)
CREATE INDEX IF NOT EXISTS idx_ledger_transaction ON ledger_entry(transaction_id);

-- Índice para consultas de eventos Pix por endToEndId (rastreamento de transação)
CREATE INDEX IF NOT EXISTS idx_pix_event_end_to_end ON pix_event(end_to_end_id);

-- Índice para consultas de transações por carteira de origem
CREATE INDEX IF NOT EXISTS idx_pix_transaction_from_wallet ON pix_transaction(from_wallet_id);

-- Índice para consultas de transações por carteira de destino
CREATE INDEX IF NOT EXISTS idx_pix_transaction_to_wallet ON pix_transaction(to_wallet_id);

-- Índice para consultas de transações por status e timestamp (queries de auditoria)
CREATE INDEX IF NOT EXISTS idx_pix_transaction_status_initiated ON pix_transaction(status, initiated_at);

