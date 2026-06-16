package com.erp.identidade.security;

import com.erp.identidade.model.LogAcesso;
import com.erp.identidade.model.ResultadoAcesso;
import com.erp.identidade.model.Usuario;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.annotation.Priority;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceUnit;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Filtro especializado para auditoria de tentativas de LOGIN e LOGOUT.
 *
 * <h3>Escopo:</h3>
 * <p>Intercepta exclusivamente o endpoint {@code POST /login} para registrar
 * tentativas de autenticação — tanto bem-sucedidas quanto falhas.</p>
 *
 * <h3>Separação de responsabilidades:</h3>
 * <ul>
 *   <li>{@link LoginAuditoriaFilter} — audita LOGIN/LOGIN_FALHA (este filtro)</li>
 *   <li>{@link AuditoriaFilter}      — audita todas as demais ações autenticadas</li>
 * </ul>
 *
 * <h3>Dados capturados:</h3>
 * <ul>
 *   <li><b>Usuário</b>    — email digitado no formulário (identificado pelo parâmetro "username")</li>
 *   <li><b>Data/Hora</b>  — momento da tentativa</li>
 *   <li><b>Ação</b>       — "LOGIN" (sucesso) ou "LOGIN_FALHA" (credenciais inválidas)</li>
 *   <li><b>IP de origem</b> — resolvido via X-Forwarded-For ou RemoteAddr</li>
 *   <li><b>Resultado</b>  — {@link ResultadoAcesso#SUCESSO} ou {@link ResultadoAcesso#ERRO}</li>
 * </ul>
 *
 * @see AuditoriaFilter
 * @see LoginAttemptService
 */
@WebFilter(
    filterName     = "LoginAuditoriaFilter",
    urlPatterns    = "/login",
    asyncSupported = true
)
@Priority(2)  // Executa logo após IpBloqueioFilter (Priority 1)
public class LoginAuditoriaFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(LoginAuditoriaFilter.class.getName());

    /**
     * @see AuditoriaFilter — mesma estratégia: EntityManagerFactory para controle
     * manual de transação em filtros Servlet (não-EJB).
     */
    @PersistenceUnit(unitName = "erpBarbershopPU")
    private EntityManagerFactory emf;

    @Override
    public void init(FilterConfig config) throws ServletException { }

    @Override
    public void destroy() { }

    @Override
    public void doFilter(ServletRequest  servletRequest,
                         ServletResponse servletResponse,
                         FilterChain     chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        // Audita apenas requisições POST (tentativa de autenticação)
        // GET /login é a exibição da página — não há credenciais a auditar
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Captura os dados ANTES de processar a requisição
        // (após o chain.doFilter, o InputStream pode estar consumido)
        String emailFormulario = request.getParameter("username");
        String ip              = AuditoriaFilter.resolverIp(request);

        // Processa o login (Spring Security valida as credenciais aqui)
        chain.doFilter(servletRequest, servletResponse);

        // Pós-processamento: determina o resultado verificando o SecurityContext
        // Se o Spring autenticou com sucesso, o principal será um UserDetails
        Object principal = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }

        boolean        loginSucesso = (principal instanceof UserDetails);
        ResultadoAcesso resultado   = loginSucesso
                                      ? ResultadoAcesso.SUCESSO
                                      : ResultadoAcesso.ERRO;

        gravarLogLogin(emailFormulario, ip, resultado);
    }

    // =========================================================
    // Persistência do log de login — transação independente
    // =========================================================

    /**
     * Grava o registro de tentativa de login com transação manual.
     *
     * <p>Emails desconhecidos (usuário inexistente no banco) são registrados
     * apenas no log do servidor — não no banco — para não criar registros
     * órfãos sem FK válida para {@code usuario_id}.</p>
     *
     * @param email     email digitado no formulário de login
     * @param ip        IP de origem da tentativa
     * @param resultado SUCESSO ou ERRO
     */
    protected void gravarLogLogin(String         email,
                                  String         ip,
                                  ResultadoAcesso resultado) {

        if (email == null || email.isBlank()) return;
        if (emf == null) {
            LOG.warning("[LoginAuditoriaFilter] EntityManagerFactory não injetado — log ignorado.");
            return;
        }

        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();

            Usuario usuario = buscarPorEmail(em, email);
            if (usuario == null) {
                // Tentativa com email inexistente — alerta de segurança no servidor
                LOG.warning(String.format(
                    "[SEGURANÇA] Tentativa de login com email inexistente: '%s' | IP: %s | Resultado: %s",
                    email, ip, resultado
                ));
                em.getTransaction().rollback();
                return;
            }

            String acao = (resultado == ResultadoAcesso.SUCESSO) ? "LOGIN" : "LOGIN_FALHA";

            LogAcesso log = new LogAcesso(
                usuario,
                LocalDateTime.now(),
                acao,
                ip,
                resultado
            );
            em.persist(log);

            em.getTransaction().commit();

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "[LoginAuditoriaFilter] Erro ao gravar log de login", ex);
            try {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
            } catch (Exception rollbackEx) {
                LOG.log(Level.SEVERE, "[LoginAuditoriaFilter] Falha no rollback", rollbackEx);
            }
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    // =========================================================
    // Auxiliar — busca de usuário
    // =========================================================

    private Usuario buscarPorEmail(EntityManager em, String email) {
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
