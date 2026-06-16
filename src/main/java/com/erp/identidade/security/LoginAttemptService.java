package com.erp.identidade.security;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Serviço de controle de tentativas falhas de login — proteção contra brute-force.
 *
 * <h3>Estratégia de bloqueio:</h3>
 * <ul>
 *   <li>Rastreia tentativas por <strong>IP de origem</strong> (não por usuário —
 *       evita account enumeration e não impede o dono da conta de tentar de outro IP)</li>
 *   <li>Após {@value #MAX_TENTATIVAS} falhas consecutivas, bloqueia o IP por
 *       {@value #MINUTOS_BLOQUEIO} minutos</li>
 *   <li>Um login bem-sucedido zera o contador do IP imediatamente</li>
 *   <li>O bloqueio expira automaticamente após o período, sem intervenção manual</li>
 * </ul>
 *
 * <h3>Implementação:</h3>
 * <p>EJB {@code @Singleton} para garantir estado compartilhado e thread-safety
 * entre todas as requisições concorrentes no WildFly. O {@link ConcurrentHashMap}
 * é o cache em memória — sem dependência de Redis/Memcached para este projeto.</p>
 *
 * <p><b>Nota de produção:</b> Em ambientes clusterizados (múltiplas instâncias WildFly),
 * este cache local por nó não sincroniza bloqueios entre os nós. Para clusters,
 * substituir o ConcurrentHashMap por um cache distribuído (Infinispan, Redis).</p>
 *
 * @see IpBloqueioFilter
 * @see ErpAuthenticationFailureHandler
 * @see ErpAuthenticationSuccessHandler
 */
@Singleton
@Lock(LockType.READ)  // Leitura concorrente por padrão; escritas usam @Lock(WRITE)
public class LoginAttemptService {

    private static final Logger LOG = Logger.getLogger(LoginAttemptService.class.getName());

    /** Número máximo de tentativas falhas antes do bloqueio por IP. */
    public static final int  MAX_TENTATIVAS   = 5;

    /** Duração do bloqueio em minutos após exceder {@link #MAX_TENTATIVAS}. */
    public static final long MINUTOS_BLOQUEIO = 15L;

    /**
     * Cache thread-safe: IP → registro de tentativas.
     * Inicializado como ConcurrentHashMap para leitura sem bloqueio.
     */
    private final Map<String, RegistroTentativa> tentativasPorIp = new ConcurrentHashMap<>();

    // =========================================================
    // API pública
    // =========================================================

    /**
     * Registra uma tentativa de login falha para o IP informado.
     * Incrementa o contador e atualiza o timestamp da última falha.
     *
     * @param ip endereço IP de origem da tentativa
     */
    @Lock(LockType.WRITE)
    public void registrarFalha(String ip) {
        RegistroTentativa registro = tentativasPorIp.computeIfAbsent(ip, k -> new RegistroTentativa());
        registro.incrementar();

        LOG.fine(String.format(
            "[LoginAttemptService] Falha registrada para IP '%s': %d/%d tentativas.",
            ip, registro.getContador(), MAX_TENTATIVAS
        ));
    }

    /**
     * Registra um login bem-sucedido, zerando o contador do IP.
     *
     * @param ip endereço IP de origem do login
     */
    @Lock(LockType.WRITE)
    public void registrarSucesso(String ip) {
        tentativasPorIp.remove(ip);
        LOG.fine(String.format("[LoginAttemptService] Contador zerado para IP '%s' após login com sucesso.", ip));
    }

    /**
     * Verifica se o IP está atualmente bloqueado.
     *
     * <p>Um IP é bloqueado se:
     * <ol>
     *   <li>Excedeu {@value #MAX_TENTATIVAS} tentativas; <b>E</b></li>
     *   <li>O período de bloqueio de {@value #MINUTOS_BLOQUEIO} min ainda não expirou</li>
     * </ol>
     * Se o período expirou, o bloqueio é removido automaticamente (lazy expiration).</p>
     *
     * @param ip endereço IP a verificar
     * @return {@code true} se o IP está bloqueado
     */
    public boolean estaBloqueado(String ip) {
        RegistroTentativa registro = tentativasPorIp.get(ip);
        if (registro == null) return false;
        if (registro.getContador() < MAX_TENTATIVAS) return false;

        // Verifica expiração do bloqueio
        LocalDateTime expiracao = registro.getUltimaFalha().plusMinutes(MINUTOS_BLOQUEIO);
        if (LocalDateTime.now().isAfter(expiracao)) {
            // Bloqueio expirado — lazy removal (sem lock: accept race condition aqui,
            // pois no pior caso o IP passa uma requisição a mais antes de ser removido)
            tentativasPorIp.remove(ip);
            LOG.info(String.format("[LoginAttemptService] Bloqueio expirado para IP '%s' — liberado.", ip));
            return false;
        }

        return true;
    }

    /**
     * Retorna quantas tentativas restam antes do bloqueio para um determinado IP.
     * Útil para exibir avisos progressivos na UI antes de atingir o limite.
     *
     * @param ip endereço IP
     * @return número de tentativas restantes (mínimo 0)
     */
    public int tentativasRestantes(String ip) {
        RegistroTentativa registro = tentativasPorIp.get(ip);
        if (registro == null) return MAX_TENTATIVAS;
        return Math.max(0, MAX_TENTATIVAS - registro.getContador());
    }

    // =========================================================
    // Classe interna: registro de tentativas por IP (in-memory)
    // =========================================================

    /**
     * Representa o estado de tentativas de login de um único IP.
     * Não é entidade JPA — vive apenas em memória durante o runtime do servidor.
     */
    private static class RegistroTentativa {

        /** Contador de tentativas falhas consecutivas. */
        private int contador = 0;

        /** Timestamp da última falha registrada. */
        private LocalDateTime ultimaFalha;

        /**
         * Incrementa o contador e atualiza o timestamp.
         */
        void incrementar() {
            this.contador++;
            this.ultimaFalha = LocalDateTime.now();
        }

        int getContador() {
            return contador;
        }

        LocalDateTime getUltimaFalha() {
            return ultimaFalha;
        }
    }
}
