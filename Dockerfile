# =============================================================
# Stage 1: Build com Maven
# =============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copia o pom.xml primeiro para cachear as dependências
# (só re-baixa se o pom.xml mudar)
COPY pom.xml .
RUN mvn dependency:resolve -B

# Copia o código-fonte e compila o WAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================
# Stage 2: Deploy no WildFly 26.1 (Jakarta EE 8)
# =============================================================
FROM quay.io/wildfly/wildfly:26.1.3.Final-jdk17

# Cria usuário admin para o Management Console
RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#2026 --silent

# Copia o WAR compilado para a pasta de auto-deploy do WildFly
COPY --from=builder /build/target/erp-barbershop.war \
     /opt/jboss/wildfly/standalone/deployments/

# Portas: 8080 (HTTP) | 9990 (Admin Console)
EXPOSE 8080 9990

# Inicia o WildFly aceitando conexões externas
CMD ["/opt/jboss/wildfly/bin/standalone.sh", \
     "-b", "0.0.0.0", \
     "-bmanagement", "0.0.0.0"]
