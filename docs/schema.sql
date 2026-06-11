-- ============================================================
-- ERP Barbershop — Script DDL (PostgreSQL 15)
-- Módulo: Modelagem de Dados e Persistência — Etapa 2
--
-- Convenções:
--   • Todas as tabelas possuem PK BIGSERIAL (auto-incremento de 64 bits)
--   • FKs seguem o padrão: <tabela_referenciada>_id
--   • Enums armazenados como VARCHAR (legibilidade em queries diretas)
--   • Soft delete implementado via coluna 'ativo' em 'produtos'
--   • Tabela 'log_acessos' é append-only (sem UPDATE/DELETE)
--
-- Ordem de criação respeita as dependências (FKs):
--   1. papeis
--   2. usuarios
--   3. usuario_papel (tabela de junção ManyToMany)
--   4. categorias
--   5. fornecedores
--   6. produtos
--   7. vendas
--   8. itens_venda
--   9. log_acessos
-- ============================================================

-- ============================================================
-- Garante schema limpo para re-execução em desenvolvimento
-- ============================================================
DROP TABLE IF EXISTS log_acessos    CASCADE;
DROP TABLE IF EXISTS itens_venda    CASCADE;
DROP TABLE IF EXISTS vendas         CASCADE;
DROP TABLE IF EXISTS produtos       CASCADE;
DROP TABLE IF EXISTS fornecedores   CASCADE;
DROP TABLE IF EXISTS categorias     CASCADE;
DROP TABLE IF EXISTS usuario_papel  CASCADE;
DROP TABLE IF EXISTS usuarios       CASCADE;
DROP TABLE IF EXISTS papeis         CASCADE;

-- ============================================================
-- 1. MÓDULO IDENTIDADE — Papéis (RBAC)
-- ============================================================

CREATE TABLE papeis (
    id   BIGSERIAL    NOT NULL,
    nome VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_papeis        PRIMARY KEY (id),
    CONSTRAINT uq_papeis_nome   UNIQUE      (nome)
);

COMMENT ON TABLE  papeis      IS 'Papéis (roles) do sistema RBAC. Ex: ROLE_ADMIN, ROLE_GERENTE.';
COMMENT ON COLUMN papeis.nome IS 'Nome do papel seguindo convenção Spring Security: ROLE_*.';

-- ============================================================
-- 2. MÓDULO IDENTIDADE — Usuários
-- ============================================================

CREATE TABLE usuarios (
    id    BIGSERIAL     NOT NULL,
    nome  VARCHAR(150)  NOT NULL,
    email VARCHAR(200)  NOT NULL,
    senha VARCHAR(255)  NOT NULL,  -- Hash BCrypt: nunca texto puro
    ativo BOOLEAN       NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_usuarios      PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_email UNIQUE     (email)
);

COMMENT ON TABLE  usuarios       IS 'Usuários do sistema ERP com credenciais e status de ativação.';
COMMENT ON COLUMN usuarios.senha IS 'Hash BCrypt da senha. Nunca armazenar texto puro.';
COMMENT ON COLUMN usuarios.ativo IS 'FALSE = usuário desativado (soft delete de conta).';

-- ============================================================
-- 3. MÓDULO IDENTIDADE — Tabela de Junção Usuario ↔ Papel
--    Implementa o ManyToMany bidirecional (RBAC)
-- ============================================================

CREATE TABLE usuario_papel (
    usuario_id BIGINT NOT NULL,
    papel_id   BIGINT NOT NULL,

    CONSTRAINT pk_usuario_papel PRIMARY KEY (usuario_id, papel_id),

    -- FK para usuarios: se o usuário for deletado, remove a associação
    CONSTRAINT fk_usuario_papel_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES  usuarios (id)
        ON DELETE CASCADE,

    -- FK para papeis: se o papel for deletado, remove a associação
    CONSTRAINT fk_usuario_papel_papel
        FOREIGN KEY (papel_id)
        REFERENCES  papeis (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE usuario_papel IS 'Tabela de junção do relacionamento ManyToMany entre usuarios e papeis (RBAC).';

-- ============================================================
-- 4. MÓDULO CATÁLOGO — Categorias de Produtos/Serviços
-- ============================================================

CREATE TABLE categorias (
    id       BIGSERIAL     NOT NULL,
    nome     VARCHAR(100)  NOT NULL,
    descricao VARCHAR(500),

    CONSTRAINT pk_categorias      PRIMARY KEY (id),
    CONSTRAINT uq_categorias_nome UNIQUE      (nome)
);

COMMENT ON TABLE categorias IS 'Categorias que organizam o catálogo. Ex: Cortes, Barba, Pomadas, Combos.';

-- ============================================================
-- 5. MÓDULO COMPRAS — Fornecedores
-- ============================================================

CREATE TABLE fornecedores (
    id            BIGSERIAL     NOT NULL,
    nome          VARCHAR(200)  NOT NULL,
    cnpj          CHAR(14)      NOT NULL,     -- 14 dígitos sem formatação
    email_contato VARCHAR(200)  NOT NULL,
    telefone      VARCHAR(20),               -- Opcional: DDD + número

    CONSTRAINT pk_fornecedores      PRIMARY KEY (id),
    CONSTRAINT uq_fornecedores_cnpj UNIQUE      (cnpj),
    CONSTRAINT ck_fornecedores_cnpj CHECK       (cnpj ~ '^[0-9]{14}$')  -- Apenas dígitos numéricos
);

COMMENT ON TABLE  fornecedores      IS 'Fornecedores de produtos para reposição de estoque.';
COMMENT ON COLUMN fornecedores.cnpj IS 'CNPJ armazenado sem formatação (14 dígitos numéricos).';

-- ============================================================
-- 6. MÓDULO CATÁLOGO — Produtos / Serviços
--    Implementa soft delete via coluna 'ativo'
-- ============================================================

CREATE TABLE produtos (
    id                  BIGSERIAL       NOT NULL,
    nome                VARCHAR(150)    NOT NULL,
    descricao           VARCHAR(500),
    preco               NUMERIC(10, 2)  NOT NULL,   -- BigDecimal: precisão monetária
    quantidade_estoque  INTEGER,                    -- NULL para serviços sem estoque físico
    quantidade_minima   INTEGER,                    -- Trigger de alerta de reposição
    ativo               BOOLEAN         NOT NULL DEFAULT TRUE,  -- Soft delete

    -- FKs de relacionamento
    categoria_id        BIGINT          NOT NULL,
    fornecedor_id       BIGINT,                     -- NULL para serviços internos

    CONSTRAINT pk_produtos              PRIMARY KEY (id),
    CONSTRAINT ck_produtos_preco        CHECK       (preco >= 0),
    CONSTRAINT ck_produtos_estoque      CHECK       (quantidade_estoque  IS NULL OR quantidade_estoque  >= 0),
    CONSTRAINT ck_produtos_qtd_minima   CHECK       (quantidade_minima   IS NULL OR quantidade_minima   >= 0),

    -- ManyToOne com Categoria (lado dono: Produto)
    CONSTRAINT fk_produtos_categoria
        FOREIGN KEY (categoria_id)
        REFERENCES  categorias (id),

    -- ManyToOne com Fornecedor (lado dono: Produto — nullable para serviços)
    CONSTRAINT fk_produtos_fornecedor
        FOREIGN KEY (fornecedor_id)
        REFERENCES  fornecedores (id)
        ON DELETE SET NULL
);

COMMENT ON TABLE  produtos       IS 'Catálogo de produtos físicos e serviços da barbearia.';
COMMENT ON COLUMN produtos.ativo IS 'FALSE = produto excluído logicamente (soft delete). Histórico de vendas preservado.';
COMMENT ON COLUMN produtos.quantidade_estoque IS 'NULL indica serviço sem controle de estoque.';

-- Índice parcial: acelera consultas que filtram apenas produtos ativos
CREATE INDEX idx_produtos_ativo        ON produtos (ativo) WHERE ativo = TRUE;
CREATE INDEX idx_produtos_categoria_id ON produtos (categoria_id);
CREATE INDEX idx_produtos_fornecedor_id ON produtos (fornecedor_id);

-- ============================================================
-- 7. MÓDULO VENDAS — Vendas (Aggregate Root / Carrinho)
-- ============================================================

CREATE TABLE vendas (
    id              BIGSERIAL       NOT NULL,
    data_venda      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valor_total     NUMERIC(12, 2)  NOT NULL DEFAULT 0.00,
    forma_pagamento VARCHAR(20)     NOT NULL,  -- Enum: DINHEIRO, CARTAO_CREDITO, etc.
    status          VARCHAR(15)     NOT NULL DEFAULT 'ABERTA',  -- Enum: ABERTA, FECHADA, CANCELADA
    usuario_id      BIGINT          NOT NULL,

    CONSTRAINT pk_vendas                PRIMARY KEY (id),
    CONSTRAINT ck_vendas_valor_total    CHECK       (valor_total >= 0),
    CONSTRAINT ck_vendas_forma_pag      CHECK       (forma_pagamento IN ('DINHEIRO', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'PIX', 'TRANSFERENCIA')),
    CONSTRAINT ck_vendas_status         CHECK       (status IN ('ABERTA', 'FECHADA', 'CANCELADA')),

    -- ManyToOne com Usuario (quem realizou a venda)
    CONSTRAINT fk_vendas_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES  usuarios (id)
);

COMMENT ON TABLE  vendas        IS 'Representa uma venda/comanda. Status ABERTA = carrinho em composição.';
COMMENT ON COLUMN vendas.status IS 'ABERTA: em composição. FECHADA: paga e finalizada. CANCELADA: anulada.';

CREATE INDEX idx_vendas_usuario_id  ON vendas (usuario_id);
CREATE INDEX idx_vendas_data_venda  ON vendas (data_venda);
CREATE INDEX idx_vendas_status      ON vendas (status);

-- ============================================================
-- 8. MÓDULO VENDAS — Itens da Venda
--    Lado dono do relacionamento bidirecional com Venda
-- ============================================================

CREATE TABLE itens_venda (
    id              BIGSERIAL       NOT NULL,
    quantidade      INTEGER         NOT NULL,
    preco_unitario  NUMERIC(10, 2)  NOT NULL,  -- Snapshot do preço no momento da venda
    venda_id        BIGINT          NOT NULL,
    produto_id      BIGINT          NOT NULL,

    CONSTRAINT pk_itens_venda           PRIMARY KEY (id),
    CONSTRAINT ck_itens_quantidade      CHECK       (quantidade > 0),
    CONSTRAINT ck_itens_preco_unitario  CHECK       (preco_unitario >= 0),

    -- ManyToOne com Venda (lado dono — gera FK venda_id)
    -- CASCADE DELETE: ao deletar a Venda, todos os itens são removidos
    CONSTRAINT fk_itens_venda_venda
        FOREIGN KEY (venda_id)
        REFERENCES  vendas (id)
        ON DELETE CASCADE,

    -- ManyToOne com Produto (o produto pode ser desativado sem perder o histórico)
    CONSTRAINT fk_itens_venda_produto
        FOREIGN KEY (produto_id)
        REFERENCES  produtos (id)
);

COMMENT ON TABLE  itens_venda             IS 'Itens individuais de uma venda. Ciclo de vida gerenciado pela Venda (CascadeType.ALL).';
COMMENT ON COLUMN itens_venda.preco_unitario IS 'Snapshot do preço no momento da venda. Não muda se o produto for reajustado.';

CREATE INDEX idx_itens_venda_venda_id   ON itens_venda (venda_id);
CREATE INDEX idx_itens_venda_produto_id ON itens_venda (produto_id);

-- ============================================================
-- 9. MÓDULO IDENTIDADE — Log de Acesso (Auditoria)
--    Entidade append-only: nunca atualizada ou deletada
-- ============================================================

CREATE TABLE log_acessos (
    id         BIGSERIAL    NOT NULL,
    data_hora  TIMESTAMP    NOT NULL,
    acao       VARCHAR(100) NOT NULL,  -- Ex: LOGIN, LOGOUT, VENDA, COMPRA, ESTORNO
    ip         VARCHAR(45)  NOT NULL,  -- IPv4 (15 chars) ou IPv6 (45 chars)
    resultado  VARCHAR(10)  NOT NULL,  -- Enum: SUCESSO, ERRO
    usuario_id BIGINT       NOT NULL,

    CONSTRAINT pk_log_acessos           PRIMARY KEY (id),
    CONSTRAINT ck_log_resultado         CHECK       (resultado IN ('SUCESSO', 'ERRO')),

    -- ManyToOne com Usuario (quem executou a ação — RESTRICT: log nunca perde o autor)
    CONSTRAINT fk_log_acessos_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES  usuarios (id)
        ON DELETE RESTRICT  -- Impede deletar usuário com registros de log
);

COMMENT ON TABLE  log_acessos IS 'Tabela de auditoria imutável. Registros NUNCA devem ser alterados ou deletados.';
COMMENT ON COLUMN log_acessos.resultado  IS 'SUCESSO = operação concluída. ERRO = falha na operação.';
COMMENT ON COLUMN log_acessos.ip         IS 'Suporta IPv4 (até 15 chars) e IPv6 (até 45 chars).';

-- Índices para queries de auditoria (filtragem por usuário, data e ação)
CREATE INDEX idx_log_usuario_id ON log_acessos (usuario_id);
CREATE INDEX idx_log_data_hora  ON log_acessos (data_hora);
CREATE INDEX idx_log_acao       ON log_acessos (acao);

-- ============================================================
-- DADOS INICIAIS (Seed) — Papéis padrão do sistema
-- ============================================================

INSERT INTO papeis (nome) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_GERENTE'),
    ('ROLE_BARBEIRO'),
    ('ROLE_CAIXA')
ON CONFLICT (nome) DO NOTHING;

-- ============================================================
-- Fim do script DDL — ERP Barbershop v1.0.0
-- ============================================================
