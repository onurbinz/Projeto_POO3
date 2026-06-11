package com.erp.identidade.security;

import javax.ejb.Singleton;
import javax.ejb.Lock;
import javax.ejb.LockType;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço de controle de tentativas falhas de login (brute-force protection).
 *
 * <h3>Estratégia:</h3>
 * <ul>
 *   <li>Rastreia tentativas por IP (não por usuário — evita account enumeration)</li>
 *   <li>Após {@value #MAX_TENTATIVAS} falhas consecutivas, bloqueia o IP por
 *       {@value #MINUTOS_BLOQUEIO} minutos</li>
 *   <li>Um login bem-sucedido zera o contador do IP</li>
 * </ul>
 *
 * <p>Implementado como {@code @Singleton} EJB para garantir thread-safety
 * e estado compartilhado entre todas as requisições no WildFly.</p>
 *
 * @see ErpAuthenticationFailureHandler
 * @see ErpAuthenticationSuccessHandler
 */
@Singleton
@Lock(LockType.READ)   // Leitura concorrente; escritas usam @Lock(WRITE) individualmente
public class LoginAttemptService {

    /** Número máximo de tentativas antes do bloqueio. */
    public static final int MAX_TENTATIVAS = 5;

    /** Duração do bloqueio em minutos após exceder o limite. */
    public static final long MINUTOS_BLOQUEIO = 15L;

    /**
     * Mapa thread-safe: IP → registro de tentativas.
     * Usamos ConcurrentHashMap como cache leve (sem dependência de Redis/Memcached).
     */
    private final Map<String, RegistroTentativa> tentativasPorIp = new ConcurrentHashMap<>();

    // =========================================================
    // API pública
    // =========================================================

    /**
     * Registra uma tentativa de login falha para o IP informado.
     * Incrementa o contador e marca o timestamp da última falha.
     *
     * @param ip endereço IP de origem da tentativa
     */
    @Lock(LockType.WRITE)
    public void registrarFalha(String ip) {
        RegistroTentativa registro = tentativasPorIp.computeIfAbsent(ip, k -> new RegistroTentativa());
        registro.incrementar();
    }

    /**
     * Registra um login bem-sucedido, zerando o contador do IP.
     *
     * @param ip endereço IP de origem do login
     */
    @Lock(LockType.WRITE)
    public void registrarSucesso(String ip) {
        tentativasPorIp.remove(ip);
    }

    /**
     * Verifica se o IP está atualmente bloqueado.
     *
     * <p>Um IP é considerado bloqueado se:
     * <ol>
     *   <li>Excedeu {@value #MAX_TENTATIVAS} tentativas; E</li>
     *   <li>Ainda está dentro do período de bloqueio de {@value #MINUTOS_BLOQUEIO} minutos</li>
     * </ol>
     * Após o período expirar, o bloqueio é removido automaticamente.</p>
     *
     * @param ip endereço IP a verificar
     * @return {@code true} se o IP está bloqueado
     */
    public boolean estaBloqueado(String ip) {
        RegistroTentativa registro = tentativasPorIp.get(ip);
        if (registro == null) return false;

        if (registro.getContador() < MAX_TENTATIVAS) return false;

        // Verifica se o período de bloqueio já expirou
        LocalDateTime expiracao = registro.getUltimaFalha().plusMinutes(MINUTOS_BLOQUEIO);
        if (LocalDateTime.now().isAfter(expiracao)) {
            // Bloqueio expirado — remove e libera o IP
            tentativasPorIp.remove(ip);
            return false;
        }

        return true;
    }

    /**
     * Retorna quantas tentativas restam antes do bloqueio para um IP.
     *
     * @param ip endereço IP
     * @return tentativas restantes (0 se já bloqueado)
     */
    public int tentativasRestantes(String ip) {
        RegistroTentativa registro = tentativasPorIp.get(ip);
        if (registro == null) return MAX_TENTATIVAS;
        return Math.max(0, MAX_TENTATIVAS - registro.getContador());
    }

    // =========================================================
    // Classe interna: registro de tentativas por IP
    // =========================================================

    /**
     * Representa o estado de tentativas de login de um único IP.
     * Não é uma entidade JPA — existe apenas em memória (cache local).
     */
    private static class RegistroTentativa {

        private int contador = 0;
        private LocalDateTime ultimaFalha;

        void incrementar() {
            this.contador++;
            this.ultimaFalha = LocalDateTime.now();
        }

        int getContador() { return contador; }

        LocalDateTime getUltimaFalha() { return ultimaFalha; }
    }
}
