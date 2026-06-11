package com.erp.identidade.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Handler executado após autenticação bem-sucedida.
 *
 * <h3>Responsabilidades:</h3>
 * <ol>
 *   <li>Zera o contador de tentativas falhas do IP no {@link LoginAttemptService}</li>
 *   <li>Armazena na sessão o email do usuário autenticado (para auditoria)</li>
 *   <li>Delega o redirecionamento para o {@link SavedRequestAwareAuthenticationSuccessHandler},
 *       que respeita a URL original que o usuário tentou acessar antes do login</li>
 * </ol>
 *
 * @see SecurityConfig
 * @see LoginAttemptService
 */
public class ErpAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    /** Delega o redirecionamento preservando a URL pré-login. */
    private final SavedRequestAwareAuthenticationSuccessHandler delegate =
        new SavedRequestAwareAuthenticationSuccessHandler();

    public ErpAuthenticationSuccessHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
        // URL de destino padrão caso não haja URL salva
        this.delegate.setDefaultTargetUrl("/pages/dashboard.xhtml");
        this.delegate.setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        String ip = resolverIp(request);

        // 1. Zera o contador de falhas deste IP
        loginAttemptService.registrarSucesso(ip);

        // 2. Armazena o username na sessão para uso no filtro de auditoria
        if (authentication.getPrincipal() instanceof UserDetails) {
            HttpSession session = request.getSession(true);
            session.setAttribute("erp.usuario.email",
                ((UserDetails) authentication.getPrincipal()).getUsername());
        }

        // 3. Redireciona para a URL original ou para o dashboard
        delegate.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Resolve o IP real do cliente, considerando proxies e load-balancers.
     * Prioriza o header {@code X-Forwarded-For} (padrão em infraestrutura com proxy).
     */
    static String resolverIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For pode conter múltiplos IPs: "client, proxy1, proxy2"
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
