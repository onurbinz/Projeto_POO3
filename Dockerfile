# ==============================================================================
# ERP Barbershop — Dockerfile (Etapa 6: Conteinerização)
# Estratégia: Multi-Stage Build
#
# STAGE 1 — builder  : Maven 3.9 + JDK 17 compila o projeto e gera o WAR
# STAGE 2 — runtime  : WildFly 26.1 recebe apenas o WAR compilado
#
# Vantagens do multi-stage:
#   • Imagem final NÃO contém Maven, JDK completo, nem código-fonte
#   • Redução drástica de tamanho (builder ~700 MB → runtime ~450 MB)
#   • Cache inteligente: dependências só são re-baixadas se o pom.xml mudar
#
# Compatibilidade garantida:
#   • WildFly 26.1 = Jakarta EE 8 = JSF 2.3 + EJB 3.2 + JPA 2.2 + CDI 2.0
#   • JDK 17 (LTS) — configurado nas propriedades do pom.xml
# ==============================================================================

# ==============================================================================
# STAGE 1: BUILD
# Imagem oficial Maven com JDK 17 Eclipse Temurin (distribuição livre da OpenJDK)
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# Diretório de trabalho do build
WORKDIR /build

# ─── Camada 1: Baixa dependências (cache inteligente) ─────────────────────────
# Copiar APENAS o pom.xml antes do código-fonte.
# O Docker cria uma camada de cache aqui. Se o pom.xml não mudar, esta
# camada é reutilizada em builds subsequentes — evita re-download de JARs.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# ─── Camada 2: Compila o projeto ──────────────────────────────────────────────
# Copia o código-fonte APÓS o download das dependências.
# Só invalida o cache desta camada quando src/ mudar.
COPY src ./src

# -DskipTests : não executa testes no build de produção (CI/CD faz isso separado)
# -B          : modo batch (sem prompts interativos)
# --no-transfer-progress : logs mais limpos em CI
RUN mvn clean package -DskipTests -B --no-transfer-progress

# Valida que o WAR foi gerado (falha rápida se o build quebrar silenciosamente)
RUN test -f /build/target/erp-barbershop.war \
    && echo "✓ WAR gerado: $(du -sh /build/target/erp-barbershop.war | cut -f1)" \
    || (echo "✗ ERRO: WAR não encontrado após mvn package" && exit 1)


# ==============================================================================
# STAGE 2: RUNTIME
# Imagem oficial WildFly 26.1.3.Final com JDK 17
# Baseada em ubi8-minimal (Red Hat UBI) — produção hardened
# ==============================================================================
FROM quay.io/wildfly/wildfly:26.1.3.Final-jdk17

# Metadados da imagem (OCI Image Spec)
LABEL org.opencontainers.image.title="ERP Barbershop" \
      org.opencontainers.image.description="ERP Web - JSF 2.3 + EJB 3.2 + WildFly 26.1" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="UERJ — POO3 2026"

# ─── Usuário Admin do Management Console ──────────────────────────────────────
# Cria usuário admin para o WildFly Management Console (porta 9990).
# Em produção, remova ou restrinja o acesso à porta 9990 via NetworkPolicy.
# A senha Admin#2026 deve ser movida para um Secret em produção real.
RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#2026 --silent

# ─── Configuração de Datasource via CLI ───────────────────────────────────────
# O WildFly lê variáveis de ambiente na configuração standalone.xml via
# expressões ${env.NOME_VAR:valor_padrao}. Usamos um CLI batch para inserir
# o datasource de forma idempotente, sem editar o XML manualmente.
#
# Alternativa Enterprise: usar o operador WildFly no Kubernetes que
# gerencia standalone.xml via CRDs (não necessário neste projeto acadêmico).
COPY docker/wildfly-cli/configure-datasource.cli /tmp/configure-datasource.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh \
        --file=/tmp/configure-datasource.cli; \
    chmod 777 /tmp/configure-datasource.cli; \
    rm -f /tmp/configure-datasource.cli || true

# ─── WAR da aplicação ─────────────────────────────────────────────────────────
# Copia APENAS o WAR do stage builder — nenhum código-fonte ou JAR intermediário
COPY --from=builder /build/target/erp-barbershop.war \
     /opt/jboss/wildfly/standalone/deployments/erp-barbershop.war

# Cria o marcador de auto-deploy do WildFly (garante deploy sem reiniciar)
RUN touch /opt/jboss/wildfly/standalone/deployments/erp-barbershop.war.dodeploy

# ─── Portas expostas ──────────────────────────────────────────────────────────
# 8080 : HTTP da aplicação
# 8443 : HTTPS da aplicação (requer keystore configurado)
# 9990 : Management Console do WildFly
EXPOSE 8080 8443 9990

# ─── Health check interno ─────────────────────────────────────────────────────
# O Kubernetes usa readinessProbe/livenessProbe (definidas no Deployment),
# mas este HEALTHCHECK serve para o Docker Compose e docker run.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/erp-barbershop/ \
        || curl -f http://localhost:9990/management \
        || exit 1

# ─── Entrypoint ───────────────────────────────────────────────────────────────
# -b 0.0.0.0          : bind da aplicação em todas as interfaces (necessário em container)
# -bmanagement 0.0.0.0: bind do console de admin (restringir em produção!)
CMD ["/opt/jboss/wildfly/bin/standalone.sh", \
     "-b", "0.0.0.0", \
     "-bmanagement", "0.0.0.0"]

