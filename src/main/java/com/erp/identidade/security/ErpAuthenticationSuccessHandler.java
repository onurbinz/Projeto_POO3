package com.erp.identidade.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Handler executado após autenticação bem-sucedida.
 *
 * <h3>Responsabilidades:</h3>
 * <ol>
 *   <li>Zera o contador de tentativas falhas do IP no {@link LoginAttemptService}
 *       — libera o IP imediatamente após login com sucesso.</li>
 *   <li>Armazena o email do usuário autenticado na sessão HTTP como atributo
 *       {@code "erp.usuario.email"} (usado pelo {@link AuditoriaFilter}).</li>
 *   <li>Delega o redirecionamento ao {@link SavedRequestAwareAuthenticationSuccessHandler},
 *       que respeita a URL original que o usuário tentou acessar antes de ser
 *       redirecionado para o login.</li>
 * </ol>
 *
 * @see SecurityConfig
 * @see LoginAttemptService
 */
public class ErpAuthenticationSuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger LOG = Logger.getLogger(ErpAuthenticationSuccessHandler.class.getName());

    private final LoginAttemptService loginAttemptService;

    /**
     * @param loginAttemptService serviço de controle de tentativas por IP
     */
    public ErpAuthenticationSuccessHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
        // URL de destino padrão caso não haja URL salva antes do login
        setDefaultTargetUrl("/pages/relatorios/dashboard.xhtml");
        // false = redireciona para a URL original salva, não sempre para o default
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest  request,
                                        HttpServletResponse response,
                                        Authentication      authentication)
            throws IOException, ServletException {

        String ip = resolverIp(request);

        // [1] Zera o contador de tentativas falhas deste IP
        loginAttemptService.registrarSucesso(ip);

        // [2] Armazena o email na sessão para uso no AuditoriaFilter
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            HttpSession session = request.getSession(true);
            session.setAttribute("erp.usuario.email", userDetails.getUsername());
            LOG.info(String.format("[AUTH] Login bem-sucedido: '%s' | IP: %s",
                userDetails.getUsername(), ip));
        }

        // [3] Redireciona para a URL original ou para o dashboard
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Resolve o IP real do cliente, respeitando proxies e load-balancers.
     * Prioriza o header {@code X-Forwarded-For} (padrão em infraestrutura com proxy reverso).
     *
     * <p>Delegado ao {@link AuditoriaFilter#resolverIp(HttpServletRequest)} para
     * centralizar a lógica de resolução de IP em um único ponto.</p>
     *
     * @param request requisição HTTP
     * @return IP de origem do cliente real
     */
    static String resolverIp(HttpServletRequest request) {
        return AuditoriaFilter.resolverIp(request);
    }
}
