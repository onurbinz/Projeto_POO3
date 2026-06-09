# 📋 ERP Barbershop — Documentação do Projeto

## 🎯 Visão Geral

**ERP Barbershop** é um sistema ERP (Enterprise Resource Planning) corporativo modular desenvolvido como projeto acadêmico para a disciplina **POO3 (Programação Orientada a Objetos III)** da **UERJ — 5º Período**.

O objetivo é gerenciar o **back-office de uma Barbearia Moderna**: PDV (ponto de venda), controle de estoque, gestão de fornecedores e relatórios financeiros. O agendamento de clientes é assumido como feito via apps externos (ex: Tua Agenda), então o foco do ERP é o **fluxo de caixa, estoque e operações internas**.

---

## 🏗️ Stack Tecnológica

| Camada | Tecnologia |
|--------|-----------|
| **Front-end** | JSF 2.3 + PrimeFaces 12 (tema `saga`) + Font Awesome |
| **Back-end** | EJB 3.2 (Stateless para serviços, Stateful para carrinho) |
| **Persistência** | JPA 2.2 / Hibernate 5.3 (provido pelo WildFly) |
| **Banco de Dados** | PostgreSQL 15 |
| **Segurança** | Spring Security 5.8.x (RBAC + BCrypt) |
| **Servidor** | WildFly 26.1 (Jakarta EE 8, Java 17) |
| **Infraestrutura** | Docker Compose (dev) |
| **Build** | Maven 3.9 + `maven-war-plugin` |
| **Linguagem** | Java 17 |

---

## 📦 Arquitetura — Domain-Driven Design (DDD)

O projeto segue estritamente a abordagem DDD, com o domínio dividido em **5 módulos lógicos** dentro do mesmo WAR (monolito modular):

```
com.erp/
├── identidade/   → Usuários, papéis RBAC, autenticação, auditoria
├── catalogo/     → Produtos, serviços, categorias, preços, estoque
├── compras/      → Fornecedores, pedidos de reposição de estoque
├── vendas/       → Frente de caixa (PDV), itens de venda, pagamentos
└── relatorios/   → Dashboards e extrações financeiras
```

Cada módulo possui a estrutura em camadas:
```
<modulo>/
├── model/       → Entidades JPA (tabelas do banco)
├── repository/  → DAOs / acesso ao banco
├── service/     → EJBs com regras de negócio
└── controller/  → Managed Beans JSF (interface)
```

---

## ✅ O Que Foi Implementado Até Agora

### Fase 1: Infraestrutura e Build

#### [Dockerfile](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/Dockerfile)
Build multi-stage:
- **Stage 1** — `maven:3.9-eclipse-temurin-17`: compila o projeto e gera o `erp-barbershop.war`
- **Stage 2** — `quay.io/wildfly/wildfly:26.1.3.Final-jdk17`: recebe o WAR e o deploya automaticamente. Usuário admin criado (`Admin#2026`). Portas `8080` (HTTP) e `9990` (Admin Console) expostas.

#### [docker-compose.yml](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/docker-compose.yml)
Orquestra dois serviços em rede isolada (`erp-network`):
- **`postgres`** — PostgreSQL 15 Alpine. Banco `erp_db`, usuário `erp_admin`, senha `erp_secret_2026`. Volume persistente `pgdata`. Healthcheck configurado.
- **`wildfly`** — Servidor de aplicação. Sobe **após** o PostgreSQL estar saudável (`depends_on: condition: service_healthy`). Variáveis de ambiente passam as credenciais do banco.

#### [pom.xml](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/pom.xml)
- `groupId`: `br.com.barbershop`, `artifactId`: `erp-barbershop`, `version`: `1.0.0-SNAPSHOT`
- **Dependências `provided`** (fornecidas pelo WildFly): `javax:javaee-api:8.0` (inclui EJB 3.2, JPA 2.2, JSF 2.3, Servlet 4.0, CDI 2.0)
- **Dependências `compile`** (empacotadas no WAR):
  - `org.primefaces:primefaces:12.0.0` — componentes UI ricos
  - `org.postgresql:postgresql:42.7.3` — driver JDBC
  - `spring-security-core/web/config:5.8.14` — segurança RBAC
- Repositório adicional: PrimeFaces Community Repository

### Fase 2: Configuração Web

#### [web.xml](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/webapp/WEB-INF/web.xml)
- Registra o `FacesServlet` do JSF mapeado para `*.xhtml`
- JSF em modo `Development` (para erros detalhados)
- Tema PrimeFaces: `saga`
- Font Awesome habilitado
- Welcome file: `index.xhtml`

#### [faces-config.xml](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/webapp/WEB-INF/faces-config.xml)
- Arquivo criado e pronto para receber navegação, conversores e validators customizados do JSF 2.3

#### [beans.xml](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/webapp/WEB-INF/beans.xml)
- Ativa o CDI 2.0 no contexto da aplicação

### Fase 3: Entidades de Domínio (Camada `model`)

Esta é a fase mais desenvolvida. Foram criadas **8 classes** de domínio (7 entidades JPA + 2 enums):

---

#### 🔐 Módulo `identidade`

##### [Usuario.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/Usuario.java) — `@Entity` → tabela `usuarios`
Representa um operador do sistema ERP.

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `nome` | `String` | `NOT NULL`, max 150 |
| `email` | `String` | `NOT NULL`, `UNIQUE`, max 200 |
| `senha` | `String` | `NOT NULL`, hash BCrypt, max 255 |
| `ativo` | `boolean` | `NOT NULL`, default `true` |

- **Relacionamento**: `ManyToMany` (LAZY) com `Papel` via tabela intermediária `usuario_papel`
- **Métodos utilitários**: `adicionarPapel(papel)` e `removerPapel(papel)` — mantêm consistência bidirecional

##### [Papel.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/Papel.java) — `@Entity` → tabela `papeis`
Representa um papel RBAC (Role-Based Access Control).

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `nome` | `String` | `NOT NULL`, `UNIQUE`, max 50 |

Papéis padrão planejados: `ROLE_ADMIN`, `ROLE_GERENTE`, `ROLE_BARBEIRO`, `ROLE_CAIXA`

- **Relacionamento**: `ManyToMany` inverso (`mappedBy = "papeis"`) com `Usuario`

##### [LogAcesso.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/LogAcesso.java) — `@Entity` → tabela `log_acessos`
Auditoria obrigatória de todas as ações financeiras e logins.

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `usuario` | FK `Usuario` | `ManyToOne` LAZY, `NOT NULL` |
| `dataHora` | `LocalDateTime` | `NOT NULL` |
| `acao` | `String` | `NOT NULL`, max 100 |
| `ip` | `String` | `NOT NULL`, max 45 (suporta IPv6) |
| `resultado` | `ResultadoAcesso` | `NOT NULL`, `EnumType.STRING` |

Ações auditadas: `LOGIN`, `LOGOUT`, `VENDA`, `COMPRA`, `ESTORNO`, `ALTERACAO_PRECO`

##### [ResultadoAcesso.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/identidade/model/ResultadoAcesso.java) — `enum`
```java
public enum ResultadoAcesso { SUCESSO, ERRO }
```

---

#### 🛍️ Módulo `catalogo`

##### [Categoria.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/catalogo/model/Categoria.java) — `@Entity` → tabela `categorias`
Organiza o catálogo em grupos lógicos (ex: Cortes, Pomadas, Bebidas, Combos).

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `nome` | `String` | `NOT NULL`, `UNIQUE`, max 100 |
| `descricao` | `String` | opcional, max 500 |

- **Relacionamento**: `OneToMany` (`mappedBy = "categoria"`) com `Produto`

##### [Produto.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/catalogo/model/Produto.java) — `@Entity` → tabela `produtos`
Item central do catálogo — tanto produtos físicos (cosméticos, bebidas) quanto serviços (corte, barba).

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `nome` | `String` | `NOT NULL`, max 150 |
| `descricao` | `String` | opcional, max 500 |
| `preco` | `BigDecimal` | `NOT NULL`, `NUMERIC(10,2)` |
| `quantidadeEstoque` | `Integer` | opcional (null para serviços) |
| `quantidadeMinima` | `Integer` | limiar de alerta de reposição |
| `categoria` | FK `Categoria` | `ManyToOne` LAZY, `NOT NULL` |
| `fornecedor` | FK `Fornecedor` | `ManyToOne` LAZY, opcional (serviços não têm) |

**Métodos de negócio**:
- `isEstoqueBaixo()` → `true` quando `quantidadeEstoque <= quantidadeMinima`
- `isServico()` → `true` quando não tem estoque nem fornecedor

> **Nota DDD**: `Produto` faz referência cross-boundary a `Fornecedor` (módulo Compras). Aceitável em monolito WAR.

---

#### 🏭 Módulo `compras`

##### [Fornecedor.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/compras/model/Fornecedor.java) — `@Entity` → tabela `fornecedores`
Empresas/distribuidoras que abastecem o estoque.

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `nome` | `String` | `NOT NULL`, max 200 |
| `cnpj` | `String` | `NOT NULL`, `UNIQUE`, 14 dígitos sem formatação |
| `emailContato` | `String` | `NOT NULL`, max 200 |

- **Relacionamento**: `OneToMany` (`mappedBy = "fornecedor"`) com `Produto`

---

#### 💰 Módulo `vendas`

##### [FormaPagamento.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/vendas/model/FormaPagamento.java) — `enum`
```java
public enum FormaPagamento { BOLETO, CARTAO_CREDITO, PIX }
```

##### [Venda.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/vendas/model/Venda.java) — `@Entity` → tabela `vendas`
**Aggregate Root** do módulo Vendas. Representa uma comanda/transação completa.

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `dataVenda` | `LocalDateTime` | `NOT NULL` |
| `valorTotal` | `BigDecimal` | `NOT NULL`, `NUMERIC(12,2)` |
| `formaPagamento` | `FormaPagamento` | `NOT NULL`, `EnumType.STRING` |
| `usuario` | FK `Usuario` | `ManyToOne` LAZY, `NOT NULL` |
| `itens` | `List<ItemVenda>` | `OneToMany` cascade ALL + orphanRemoval |

**Métodos de negócio**:
- `adicionarItem(item)` — adiciona item e seta a referência bidirecional
- `removerItem(item)` — remove item e seta `item.venda = null`
- `recalcularTotal()` — soma `precoUnitario × quantidade` de todos os itens via Stream API

##### [ItemVenda.java](file:///d:/UERJ/5%20periodo/POO3/Projeto_POO3/src/main/java/com/erp/vendas/model/ItemVenda.java) — `@Entity` → tabela `itens_venda`
Linha de uma venda — liga um `Produto` a uma `Venda`.

| Campo | Tipo | Restrição |
|-------|------|-----------|
| `id` | `Long` | PK, `IDENTITY` |
| `quantidade` | `Integer` | `NOT NULL` |
| `precoUnitario` | `BigDecimal` | `NOT NULL`, `NUMERIC(10,2)` — **snapshot do preço no momento da venda** |
| `venda` | FK `Venda` | `ManyToOne` LAZY, `NOT NULL` (lado dono) |
| `produto` | FK `Produto` | `ManyToOne` LAZY, `NOT NULL` |

**Métodos de negócio**:
- `getSubtotal()` → `precoUnitario × quantidade`

> **Decisão de design**: O `precoUnitario` é armazenado no item como "snapshot" — mesmo que o preço do produto mude futuramente, o histórico financeiro permanece correto.

---

## 🗄️ Mapa de Tabelas do Banco (Gerado via JPA)

```
┌──────────────────────────────────────────┐
│  usuarios          papeis                │
│  ─────────         ──────                │
│  id (PK)           id (PK)               │
│  nome              nome (UNIQUE)         │
│  email (UNIQUE)                          │
│  senha                                   │
│  ativo             ◄──────── usuario_papel (tabela M:N)
│                              usuario_id FK
│                              papel_id FK
├──────────────────────────────────────────┤
│  log_acessos                             │
│  ────────────                            │
│  id (PK)                                 │
│  usuario_id (FK → usuarios)              │
│  data_hora                               │
│  acao                                    │
│  ip                                      │
│  resultado                               │
├──────────────────────────────────────────┤
│  categorias        produtos              │
│  ────────────      ─────────             │
│  id (PK)           id (PK)               │
│  nome (UNIQUE)     nome                  │
│  descricao         descricao             │
│                    preco                 │
│                    quantidade_estoque    │
│                    quantidade_minima     │
│                    categoria_id (FK)     │
│                    fornecedor_id (FK)    │
├──────────────────────────────────────────┤
│  fornecedores                            │
│  ─────────────                           │
│  id (PK)                                 │
│  nome                                    │
│  cnpj (UNIQUE)                           │
│  email_contato                           │
├──────────────────────────────────────────┤
│  vendas            itens_venda           │
│  ────────          ────────────          │
│  id (PK)           id (PK)               │
│  data_venda        quantidade            │
│  valor_total       preco_unitario        │
│  forma_pagamento   venda_id (FK)         │
│  usuario_id (FK)   produto_id (FK)       │
└──────────────────────────────────────────┘
```

---

## 🗂️ Estrutura de Pastas (Estado Atual)

```
Projeto_POO3/
├── Dockerfile                          ✅ Implementado
├── docker-compose.yml                  ✅ Implementado
├── pom.xml                             ✅ Implementado
├── README.md                           ✅ Documentação geral
├── planner-agent.md                    ✅ Contexto do projeto
├── docs/                               🔲 Vazio (reservado)
└── src/main/
    ├── java/com/erp/
    │   ├── identidade/
    │   │   ├── model/
    │   │   │   ├── Usuario.java        ✅ Implementado
    │   │   │   ├── Papel.java          ✅ Implementado
    │   │   │   ├── LogAcesso.java      ✅ Implementado
    │   │   │   └── ResultadoAcesso.java✅ Implementado
    │   │   ├── repository/             🔲 Vazio (a implementar)
    │   │   ├── service/                🔲 Vazio (a implementar)
    │   │   └── controller/             🔲 Vazio (a implementar)
    │   ├── catalogo/
    │   │   ├── model/
    │   │   │   ├── Categoria.java      ✅ Implementado
    │   │   │   └── Produto.java        ✅ Implementado
    │   │   ├── repository/             🔲 Vazio (a implementar)
    │   │   ├── service/                🔲 Vazio (a implementar)
    │   │   └── controller/             🔲 Vazio (a implementar)
    │   ├── compras/
    │   │   ├── model/
    │   │   │   └── Fornecedor.java     ✅ Implementado
    │   │   ├── repository/             🔲 Vazio (a implementar)
    │   │   ├── service/                🔲 Vazio (a implementar)
    │   │   └── controller/             🔲 Vazio (a implementar)
    │   ├── vendas/
    │   │   ├── model/
    │   │   │   ├── Venda.java          ✅ Implementado
    │   │   │   ├── ItemVenda.java      ✅ Implementado
    │   │   │   └── FormaPagamento.java ✅ Implementado
    │   │   ├── repository/             🔲 Vazio (a implementar)
    │   │   ├── service/                🔲 Vazio (a implementar)
    │   │   └── controller/             🔲 Vazio (a implementar)
    │   └── relatorios/
    │       ├── model/                  🔲 Vazio (a implementar)
    │       ├── repository/             🔲 Vazio (a implementar)
    │       ├── service/                🔲 Vazio (a implementar)
    │       └── controller/             🔲 Vazio (a implementar)
    ├── resources/
    │   └── META-INF/                   🔲 persistence.xml a implementar
    └── webapp/
        ├── WEB-INF/
        │   ├── web.xml                 ✅ Implementado
        │   ├── faces-config.xml        ✅ Implementado (vazio)
        │   └── beans.xml               ✅ Implementado
        ├── pages/
        │   ├── catalogo/               🔲 Vazio (páginas XHTML)
        │   ├── compras/                🔲 Vazio
        │   ├── identidade/             🔲 Vazio
        │   ├── vendas/                 🔲 Vazio
        │   └── relatorios/             🔲 Vazio
        └── resources/
            ├── css/                    🔲 Vazio
            ├── js/                     🔲 Vazio
            └── img/                    🔲 Vazio
```

---

## 🔄 Fases de Desenvolvimento — Progresso

| Fase | Descrição | Status |
|------|-----------|--------|
| **1** | Infraestrutura (`docker-compose.yml`, `Dockerfile`, `pom.xml`) | ✅ Concluída |
| **2** | Configuração Web (`web.xml`, `faces-config.xml`, `beans.xml`) | ✅ Concluída |
| **3** | Entidades JPA — Fase de Domínio (camada `model`) | ✅ Concluída |
| **4** | `persistence.xml` + DataSource WildFly | 🔲 Pendente |
| **5** | Repositórios (DAOs com JPA `EntityManager`) | 🔲 Pendente |
| **6** | Serviços (EJBs Stateless + Stateful para carrinho) | 🔲 Pendente |
| **7** | Controllers JSF (Managed Beans) | 🔲 Pendente |
| **8** | Views PrimeFaces (páginas XHTML) | 🔲 Pendente |
| **9** | Segurança Spring Security (filtros, login, RBAC) | 🔲 Pendente |
| **10** | Relatórios e Dashboard | 🔲 Pendente |

---

## ⚙️ Como Executar o Projeto

```bash
# Na raiz do projeto (Projeto_POO3/)

# 1. Subir tudo (build Maven + deploy WildFly + PostgreSQL)
docker compose up --build -d

# 2. Acompanhar os logs
docker compose logs -f

# 3. Parar tudo
docker compose down
```

### Endpoints após subir:
| Serviço | URL | Credenciais |
|---------|-----|-------------|
| Aplicação | http://localhost:8080/erp-barbershop | — |
| WildFly Admin Console | http://localhost:9990 | `admin` / `Admin#2026` |
| PostgreSQL | `localhost:5432` — banco `erp_db` | `erp_admin` / `erp_secret_2026` |

---

## 🏛️ Decisões de Arquitetura Tomadas

1. **`BigDecimal` para valores monetários** — nunca `double/float`, para evitar erros de arredondamento
2. **`EnumType.STRING` nos bancos** — enums armazenados como `VARCHAR` para legibilidade em SQL direto
3. **`FetchType.LAZY` nos relacionamentos** — evita consultas desnecessárias em listagens
4. **`precoUnitario` no `ItemVenda`** — snapshot do preço na hora da venda, garantindo integridade histórica
5. **`CascadeType.ALL + orphanRemoval`** em `Venda → ItemVenda` — itens sem venda são removidos automaticamente
6. **Referências cross-boundary aceitas** — `Produto` referencia `Fornecedor`, `ItemVenda` referencia `Produto`, `Venda` referencia `Usuario`. Aceitável em monolito WAR.
7. **Auditoria obrigatória** — todas as operações financeiras e logins devem gerar registros em `log_acessos`
8. **Hash BCrypt** — senhas nunca armazenadas em texto plano
