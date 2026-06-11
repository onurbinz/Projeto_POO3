package com.erp.identidade.security;

import com.erp.identidade.model.LogAcesso;
import com.erp.identidade.model.ResultadoAcesso;
import com.erp.identidade.model.Usuario;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.annotation.Priority;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Filtro especializado para auditoria de tentativas de LOGIN.
 *
 * <p>Enquanto o {@link AuditoriaFilter} audita requisições de usuários já
 * autenticados, este filtro intercepta especificamente o endpoint {@code POST /login}
 * para registrar tanto logins bem-sucedidos quanto falhas de autenticação.</p>
 *
 * <p>O log de login usa o email informado no formulário como identificador
 * do usuário, mesmo antes da autenticação ser confirmada.</p>
 *
 * @see AuditoriaFilter
 * @see LoginAttemptService
 */
@WebFilter(
    filterName = "LoginAuditoriaFilter",
    urlPatterns = "/login"
)
@Priority(2)
public class LoginAuditoriaFilter implements Filter {

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    @Override
    public void init(FilterConfig config) throws ServletException { }

    @Override
    public void destroy() { }

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        // Somente audita requisições POST (tentativa de login)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Captura o email antes de a requisição ser processada
        String emailFormulario = request.getParameter("username");
        String ip = ErpAuthenticationSuccessHandler.resolverIp(request);

        // Processa o login normalmente
        chain.doFilter(servletRequest, servletResponse);

        // Pós-processamento: verifica se o login foi bem-sucedido
        // O Spring Security popula o SecurityContext se a autenticação teve sucesso
        Object principal = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }

        boolean loginSucesso = principal instanceof UserDetails;
        ResultadoAcesso resultado = loginSucesso ? ResultadoAcesso.SUCESSO : ResultadoAcesso.ERRO;

        gravarLogLogin(emailFormulario, ip, resultado);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void gravarLogLogin(String email, String ip, ResultadoAcesso resultado) {
        try {
            if (email == null || email.isBlank()) return;

            // Tenta localizar o usuário no banco — pode não existir (tentativa com email inválido)
            Usuario usuario = buscarPorEmail(email);
            if (usuario == null) {
                // Log de segurança no servidor: tentativa com email desconhecido
                System.err.printf("[SEGURANÇA] Tentativa de login com email desconhecido: %s | IP: %s%n",
                    email, ip);
                return;
            }

            LogAcesso log = new LogAcesso(
                usuario,
                LocalDateTime.now(),
                resultado == ResultadoAcesso.SUCESSO ? "LOGIN" : "LOGIN_FALHA",
                ip,
                resultado
            );
            em.persist(log);

        } catch (Exception ex) {
            System.err.println("[LoginAuditoriaFilter] Erro ao gravar log: " + ex.getMessage());
        }
    }

    private Usuario buscarPorEmail(String email) {
        try {
            return em.createQuery(
                    "SELECT u FROM Usuario u WHERE u.email = :email",
                    Usuario.class)
                .setParameter("email", email)
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
