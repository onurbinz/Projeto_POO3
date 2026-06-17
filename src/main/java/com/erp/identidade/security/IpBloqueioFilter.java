package com.erp.identidade.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Filtro de verificação de bloqueio por IP — barreira contra brute-force.
 *
 * <h3>Posicionamento na cadeia de filtros:</h3>
 * <p>Inserido via {@code SecurityConfig.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)}.
 * É o PRIMEIRO filtro a executar, antes que o BCrypt seja acionado.</p>
 *
 * <p>Isso é crítico por design: BCrypt tem custo computacional alto (~250ms com
 * fator 12). Se o IP estivesse bloqueado mas o filtro não barrasse antes,
 * cada tentativa ainda executaria o hash — facilitando ataques de CPU exhaustion.</p>
 *
 * <h3>Escopo de atuação:</h3>
 * <p>Intercepta APENAS requisições {@code POST /login} (tentativas de autenticação).
 * Requisições GET (exibição da página de login) são sempre permitidas para que
 * o usuário possa ver a mensagem de bloqueio.</p>
 *
 * @see LoginAttemptService
 * @see SecurityConfig
 */
public class IpBloqueioFilter extends OncePerRequestFilter {

    private static final Logger LOG = Logger.getLogger(IpBloqueioFilter.class.getName());

    /** Serviço que rastreia tentativas falhas e gerencia o estado de bloqueio por IP. */
    private final LoginAttemptService loginAttemptService;

    /**
     * Construtor com injeção explícita — o filtro é instanciado pelo SecurityConfig,
     * não pelo CDI/Spring, então a dependência é passada pelo construtor.
     *
     * @param loginAttemptService serviço de controle de tentativas
     */
    public IpBloqueioFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        // Aplica bloqueio apenas em tentativas de autenticação (POST)
        if ("POST".equalsIgnoreCase(request.getMethod())) {

            String ip = AuditoriaFilter.resolverIp(request);

            if (loginAttemptService.estaBloqueado(ip)) {
                LOG.warning(String.format(
                    "[BLOQUEIO] IP '%s' bloqueado — tentativa de login recusada.", ip
                ));
                // Interrompe a cadeia e redireciona sem processar a autenticação
                response.sendRedirect(request.getContextPath() + "/login.xhtml?erro=bloqueado");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
