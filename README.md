# ✂️ ERP Barbershop

ERP corporativo modular para o back-office de uma **Barbearia Moderna**.

## 🏗️ Stack Tecnológica

| Camada         | Tecnologia                              |
|----------------|-----------------------------------------|
| Front-end      | JSF 2.3 + PrimeFaces 12                |
| Back-end       | EJB 3.2                                |
| Persistência   | JPA 2.2 / Hibernate (WildFly 26.1)     |
| Segurança      | Spring Security 5.8                    |
| Servidor       | WildFly 26.1 (Jakarta EE 8)           |
| Banco de Dados | PostgreSQL 15                          |
| Infra          | Docker Compose + Maven                 |

## 📦 Módulos DDD

| Módulo         | Pacote                   | Responsabilidade                          |
|----------------|--------------------------|-------------------------------------------|
| Identidade     | `com.erp.identidade`     | Usuários, papéis, autenticação, auditoria |
| Catálogo       | `com.erp.catalogo`       | Serviços, produtos, preços                |
| Compras        | `com.erp.compras`        | Fornecedores, pedidos de compra, estoque  |
| Vendas         | `com.erp.vendas`         | Agendamentos, comandas, pagamentos        |
| Relatórios     | `com.erp.relatorios`     | Dashboards, extrações financeiras         |

## 🚀 Como Rodar

```bash
# Subir tudo (build + deploy + banco)
docker compose up --build -d

# Ver logs
docker compose logs -f

# Parar tudo
docker compose down
```

**Acessos:**
- Aplicação: http://localhost:8080/erp-barbershop
- Admin Console WildFly: http://localhost:9990 (`admin` / `Admin#2026`)
- PostgreSQL: `localhost:5432` — banco `erp_db` (`erp_admin` / `erp_secret_2026`)

## 📁 Estrutura do Projeto

```
Projeto_POO3/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── src/main/
│   ├── java/com/erp/
│   │   ├── identidade/   (model, service, repository, controller)
│   │   ├── catalogo/      ...
│   │   ├── compras/       ...
│   │   ├── vendas/        ...
│   │   └── relatorios/    ...
│   ├── resources/META-INF/persistence.xml
│   └── webapp/
│       ├── WEB-INF/ (web.xml, beans.xml, faces-config.xml)
│       ├── resources/ (css, js, img)
│       └── pages/ (telas XHTML por módulo)
└── docs/
```
