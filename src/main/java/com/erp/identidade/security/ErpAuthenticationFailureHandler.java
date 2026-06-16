package com.erp.identidade.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Handler executado após cada falha de autenticação.
 *
 * <h3>Responsabilidades:</h3>
 * <ol>
 *   <li>Registra a falha no {@link LoginAttemptService} — incrementa o contador por IP.</li>
 *   <li>Verifica se o IP atingiu o limite após esta tentativa e redireciona
 *       com parâmetro {@code erro=bloqueado} se for o caso.</li>
 *   <li>Distingue conta bloqueada no banco ({@link LockedException}) de
 *       senha simplesmente incorreta — parâmetros de redirect diferentes.</li>
 *   <li>Informa quantas tentativas restam ao usuário via parâmetro {@code restantes=N}
 *       para que a UI exiba um aviso progressivo antes do bloqueio.</li>
 * </ol>
 *
 * <h3>Política de informação ao usuário:</h3>
 * <p>Mensagens de erro são genéricas o suficiente para não confirmar se o email
 * existe no banco (user enumeration prevention), mas específicas o suficiente
 * para orientar o usuário legítimo.</p>
 *
 * @see SecurityConfig
 * @see LoginAttemptService
 */
public class ErpAuthenticationFailureHandler
        extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger LOG = Logger.getLogger(ErpAuthenticationFailureHandler.class.getName());

    private final LoginAttemptService loginAttemptService;

    /**
     * @param loginAttemptService serviço de controle de tentativas por IP
     */
    public ErpAuthenticationFailureHandler(LoginAttemptService loginAttemptService) {
        super("/login?erro=true");  // URL padrão de falha
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest  request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {

        String ip = ErpAuthenticationSuccessHandler.resolverIp(request);

        // [1] Registra a falha — incrementa o contador do IP
        loginAttemptService.registrarFalha(ip);

        LOG.warning(String.format(
            "[AUTH] Falha de login | IP: %s | Causa: %s",
            ip, exception.getClass().getSimpleName()
        ));

        // [2] Conta bloqueada no banco (ativo = false)
        // Verificado antes do bloqueio por IP pois tem tratamento diferente
        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?erro=conta_bloqueada");
            return;
        }

        // [3] IP bloqueado por excesso de tentativas
        if (loginAttemptService.estaBloqueado(ip)) {
            LOG.warning(String.format(
                "[BLOQUEIO] IP '%s' bloqueado após exceder %d tentativas falhas.",
                ip, LoginAttemptService.MAX_TENTATIVAS
            ));
            response.sendRedirect(request.getContextPath() + "/login?erro=bloqueado");
            return;
        }

        // [4] Falha comum — informa quantas tentativas restam
        int restantes = loginAttemptService.tentativasRestantes(ip);
        setDefaultFailureUrl("/login?erro=true&restantes=" + restantes);

        // Delega o redirecionamento para a superclasse
        super.onAuthenticationFailure(request, response, exception);
    }
}
