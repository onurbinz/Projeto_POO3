#!/usr/bin/env bash
# =============================================================================
# gerar-certificado-https.sh
#
# Gera um certificado TLS autoassinado para desenvolvimento local com HTTPS.
# Compatível com Git Bash e WSL em máquinas Windows.
#
# Pré-requisito: OpenSSL instalado (Git Bash já inclui; WSL: sudo apt install openssl)
#
# USO:
#   bash gerar-certificado-https.sh
#
# SAÍDA:
#   ./ssl/erp-barbershop.key   — Chave privada RSA 2048-bit (sem passphrase)
#   ./ssl/erp-barbershop.crt   — Certificado X.509 autoassinado (validade: 365 dias)
#   ./ssl/erp-barbershop.p12   — Keystore PKCS#12 para WildFly (senha: changeit)
#
# ATENÇÃO: Certificados autoassinados são exclusivos para desenvolvimento.
#          Em produção, use Let's Encrypt (Certbot) ou uma CA reconhecida.
# =============================================================================

set -euo pipefail  # Aborta em erro, variável não declarada ou falha em pipe

# ---------------------------------------------------------------------------
# Configurações do certificado
# ---------------------------------------------------------------------------
readonly CERT_DIR="./ssl"
readonly KEY_FILE="${CERT_DIR}/erp-barbershop.key"
readonly CRT_FILE="${CERT_DIR}/erp-barbershop.crt"
readonly P12_FILE="${CERT_DIR}/erp-barbershop.p12"
readonly P12_ALIAS="erp-barbershop"
readonly P12_PASS="changeit"   # Senha padrão do WildFly (standalone.xml)
readonly DAYS_VALID=365
readonly KEY_BITS=2048

# Informações do subject do certificado (Distinguished Name)
readonly COUNTRY="BR"
readonly STATE="Rio de Janeiro"
readonly CITY="Rio de Janeiro"
readonly ORG="ERP Barbershop Desenvolvimento"
readonly ORG_UNIT="TI"
readonly CN="localhost"           # Common Name: deve bater com o hostname usado
readonly ALT_DNS="localhost"      # Subject Alternative Name (requerido por browsers modernos)
readonly ALT_IP="127.0.0.1"

# ---------------------------------------------------------------------------
# Funções utilitárias
# ---------------------------------------------------------------------------
log_info()  { echo "[INFO]  $*"; }
log_ok()    { echo "[OK]    $*"; }
log_error() { echo "[ERRO]  $*" >&2; }

# ---------------------------------------------------------------------------
# Pré-verificações
# ---------------------------------------------------------------------------
if ! command -v openssl &>/dev/null; then
    log_error "OpenSSL não encontrado. Instale com: sudo apt install openssl (WSL) ou via Git Bash."
    exit 1
fi

log_info "OpenSSL versão: $(openssl version)"

# ---------------------------------------------------------------------------
# [1] Cria o diretório de saída (se não existir)
# ---------------------------------------------------------------------------
mkdir -p "${CERT_DIR}"
log_info "Diretório de saída: ${CERT_DIR}/"

# ---------------------------------------------------------------------------
# [2] Gera a chave privada RSA 2048-bit SEM passphrase
#     (WildFly lê a keystore sem interação humana — passphrase impediria o boot)
# ---------------------------------------------------------------------------
log_info "Gerando chave privada RSA ${KEY_BITS}-bit..."
openssl genrsa \
    -out "${KEY_FILE}" \
    "${KEY_BITS}"
log_ok "Chave privada: ${KEY_FILE}"

# ---------------------------------------------------------------------------
# [3] Gera o certificado X.509 autoassinado com Subject Alternative Names
#     SAN é obrigatório no Chrome/Firefox/Edge modernos; sem ele, o browser
#     rejeita o certificado mesmo que o CN bata com o hostname.
# ---------------------------------------------------------------------------
log_info "Gerando certificado autoassinado (${DAYS_VALID} dias)..."

# Cria arquivo de configuração temporário com as extensões de SAN
readonly OPENSSL_CONF=$(mktemp /tmp/erp-openssl-XXXXXX.cnf)

cat > "${OPENSSL_CONF}" <<EOF
[req]
default_bits       = ${KEY_BITS}
prompt             = no
default_md         = sha256
distinguished_name = dn
x509_extensions    = v3_req

[dn]
C  = ${COUNTRY}
ST = ${STATE}
L  = ${CITY}
O  = ${ORG}
OU = ${ORG_UNIT}
CN = ${CN}

[v3_req]
subjectAltName = @alt_names
keyUsage       = keyEncipherment, dataEncipherment, digitalSignature
extendedKeyUsage = serverAuth

[alt_names]
DNS.1 = ${ALT_DNS}
IP.1  = ${ALT_IP}
EOF

openssl req \
    -new \
    -x509 \
    -sha256 \
    -days   "${DAYS_VALID}" \
    -key    "${KEY_FILE}" \
    -out    "${CRT_FILE}" \
    -config "${OPENSSL_CONF}"

# Remove o arquivo de configuração temporário
rm -f "${OPENSSL_CONF}"

log_ok "Certificado: ${CRT_FILE}"

# ---------------------------------------------------------------------------
# [4] Gera o Keystore PKCS#12 (.p12) para importação no WildFly
#     O WildFly 26.1 suporta nativamente keystores PKCS#12 no standalone.xml
# ---------------------------------------------------------------------------
log_info "Gerando Keystore PKCS#12 para WildFly..."
openssl pkcs12 \
    -export \
    -in      "${CRT_FILE}" \
    -inkey   "${KEY_FILE}" \
    -out     "${P12_FILE}" \
    -name    "${P12_ALIAS}" \
    -passout "pass:${P12_PASS}"

log_ok "Keystore PKCS#12: ${P12_FILE} (alias: ${P12_ALIAS}, senha: ${P12_PASS})"

# ---------------------------------------------------------------------------
# [5] Exibe informações do certificado gerado (verificação rápida)
# ---------------------------------------------------------------------------
log_info "Resumo do certificado gerado:"
openssl x509 -in "${CRT_FILE}" -noout -subject -issuer -dates -fingerprint -sha256

# ---------------------------------------------------------------------------
# [6] Instruções de configuração no WildFly standalone.xml
# ---------------------------------------------------------------------------
cat <<'INSTRUCOES'

===========================================================================
  PRÓXIMOS PASSOS: Configurar HTTPS no WildFly 26.1
===========================================================================

1. Copie o keystore para o diretório de configuração do WildFly:

   cp ./ssl/erp-barbershop.p12 $WILDFLY_HOME/standalone/configuration/

2. Adicione o security-realm no standalone.xml (bloco <management>):

   <security-realm name="ErpSSLRealm">
       <server-identities>
           <ssl>
               <keystore path="erp-barbershop.p12"
                         relative-to="jboss.server.config.dir"
                         keystore-password="changeit"
                         alias="erp-barbershop"
                         key-password="changeit"
                         type="PKCS12"/>
           </ssl>
       </server-identities>
   </security-realm>

3. Configure o HTTPS listener no subsistema undertow (standalone.xml):

   <https-listener name="https"
                   socket-binding="https"
                   security-realm="ErpSSLRealm"
                   enable-http2="true"/>

4. Reinicie o WildFly e acesse: https://localhost:8443/erp-barbershop/login

5. ATENÇÃO (browser): O browser exibirá aviso de segurança para certificados
   autoassinados. Clique em "Avançado" → "Prosseguir para localhost" (Chrome)
   ou "Aceitar o risco e continuar" (Firefox). Em produção, use Let's Encrypt.

===========================================================================
INSTRUCOES

log_ok "Certificado autoassinado gerado com sucesso!"
