package com.erp.identidade.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handler executado após cada falha de autenticação.
 *
 * <h3>Responsabilidades:</h3>
 * <ol>
 *   <li>Registra a falha no {@link LoginAttemptService} (incrementa contador por IP)</li>
 *   <li>Verifica se o IP atingiu o limite e redireciona com parâmetro adequado</li>
 *   <li>Propaga a falha para a página de login com contexto da causa</li>
 * </ol>
 *
 * @see SecurityConfig
 * @see LoginAttemptService
 */
public class ErpAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    /** Delega o redirecionamento para a URL de falha configurada. */
    private final SimpleUrlAuthenticationFailureHandler delegate =
        new SimpleUrlAuthenticationFailureHandler("/login?erro=true");

    public ErpAuthenticationFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {

        String ip = ErpAuthenticationSuccessHandler.resolverIp(request);

        // 1. Registra a falha (incrementa contador do IP)
        loginAttemptService.registrarFalha(ip);

        // 2. Verifica se o IP foi bloqueado após esta última tentativa
        if (loginAttemptService.estaBloqueado(ip)) {
            // Redireciona com parâmetro específico de bloqueio
            response.sendRedirect(request.getContextPath() + "/login?erro=bloqueado");
            return;
        }

        // 3. Conta quantas tentativas restam (para exibir aviso progressivo na UI)
        int restantes = loginAttemptService.tentativasRestantes(ip);
        String redirectUrl = "/login?erro=true&restantes=" + restantes;

        // 4. Se a conta foi bloqueada no banco (ativo = false), redireciona diferente
        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?erro=conta_bloqueada");
            return;
        }

        delegate.setDefaultFailureUrl(redirectUrl);
        delegate.onAuthenticationFailure(request, response, exception);
    }
}
