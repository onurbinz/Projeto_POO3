package com.erp.identidade.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filtro de verificação de bloqueio por IP — executado antes do Spring Security.
 *
 * <p>Intercepta TODA requisição para o endpoint de login e verifica se o IP
 * de origem está bloqueado pelo {@link LoginAttemptService}. Se estiver,
 * redireciona imediatamente para a página de login com parâmetro de bloqueio,
 * <b>sem processar a tentativa de autenticação</b>.</p>
 *
 * <h3>Posicionamento na cadeia de filtros:</h3>
 * <p>Este filtro deve ser adicionado ao {@code SecurityFilterChain} ANTES do
 * {@code UsernamePasswordAuthenticationFilter} do Spring Security. Isso é feito
 * via {@link org.springframework.security.config.annotation.web.builders.HttpSecurity#addFilterBefore}.</p>
 *
 * @see LoginAttemptService
 * @see SecurityConfig
 */
@WebFilter(filterName = "IpBloqueioFilter", urlPatterns = "/login")
public class IpBloqueioFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;

    public IpBloqueioFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        // Só bloqueia requisições POST (tentativas de autenticação)
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String ip = ErpAuthenticationSuccessHandler.resolverIp(request);

            if (loginAttemptService.estaBloqueado(ip)) {
                // Aborta o processamento e redireciona sem tentar autenticar
                response.sendRedirect(request.getContextPath() + "/login?erro=bloqueado");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
