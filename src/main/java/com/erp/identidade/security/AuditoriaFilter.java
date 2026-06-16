package com.erp.identidade.security;

import com.erp.identidade.model.LogAcesso;
import com.erp.identidade.model.ResultadoAcesso;
import com.erp.identidade.model.Usuario;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.Priority;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interceptador de auditoria que grava logs na entidade {@link LogAcesso}
 * para todas as requisições de usuários autenticados.
 *
 * <h3>Dados capturados obrigatoriamente:</h3>
 * <ul>
 *   <li><b>Usuário</b>    — principal autenticado no Spring Security Context</li>
 *   <li><b>Data/Hora</b>  — {@code LocalDateTime.now()} no momento da requisição</li>
 *   <li><b>Ação</b>       — método HTTP + URI (ex: "GET /pages/vendas/lista.xhtml [HTTP 200]")</li>
 *   <li><b>IP de origem</b> — resolvido via X-Forwarded-For ou RemoteAddr</li>
 *   <li><b>Resultado</b>  — {@link ResultadoAcesso#SUCESSO} ou {@link ResultadoAcesso#ERRO}</li>
 * </ul>
 *
 * <h3>Estratégia pós-processamento:</h3>
 * <p>O filtro deixa a requisição fluir normalmente e só persiste o log
 * após a resposta ser gerada — quando o status HTTP já é conhecido.
 * Isso garante que o resultado (SUCESSO/ERRO) reflita o que realmente ocorreu.</p>
 *
 * <h3>Filtragem de ruído:</h3>
 * <p>Recursos estáticos (CSS, JS, imagens, fontes JSF/PrimeFaces) e rotas
 * de login/logout são ignorados para não poluir a tabela com centenas de
 * entradas irrelevantes por página acessada.</p>
 *
 * <h3>Resiliência:</h3>
 * <p>O log de auditoria usa {@code REQUIRES_NEW} — transação independente da
 * requisição principal. Mesmo se a transação de negócio for revertida (rollback),
 * o log de auditoria é persistido. Falhas na gravação do log são logadas mas
 * NUNCA propagadas para o usuário final.</p>
 *
 * @see LogAcesso
 * @see ResultadoAcesso
 * @see LoginAuditoriaFilter
 */
@WebFilter(
    filterName   = "AuditoriaFilter",
    urlPatterns  = "/*",              // Intercepta todas as requisições
    asyncSupported = true             // Suporte a Servlet 3.x async requests
)
@Priority(10)  // Executado APÓS o filtro do Spring Security (prioridade mais alta = número maior)
public class AuditoriaFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(AuditoriaFilter.class.getName());

    // =========================================================
    // Prefixos/sufixos de URI ignorados pela auditoria
    // (recursos estáticos e endpoints de infraestrutura)
    // =========================================================
    private static final String[] IGNORAR_PREFIXOS = {
        "/javax.faces.resource/",  // Recursos do JSF (CSS, JS, componentes PrimeFaces)
        "/resources/",             // Recursos estáticos da aplicação
        "/login",                  // Auditado pelo LoginAuditoriaFilter especializado
        "/logout"                  // Logout é simples, não carece de log de ação
    };

    /**
     * EntityManagerFactory injetado via @PersistenceUnit para criar
     * EntityManagers com gerenciamento de transação manual.
     *
     * <p>ATENÇÃO: Filtros Servlet NÃO são EJBs nem CDI beans gerenciados.
     * Usar @PersistenceContext aqui injetaria um EM de escopo de requisição
     * que pode não ter contexto transacional ativo. Por isso usamos
     * EntityManagerFactory + transação manual via REQUIRES_NEW.</p>
     *
     * <p>O @PersistenceUnit funciona em qualquer componente dentro do WAR
     * no WildFly — o container injeta a factory via JNDI.</p>
     */
    @PersistenceUnit(unitName = "erpBarbershopPU")
    private EntityManagerFactory emf;

    @Override
    public void init(FilterConfig config) throws ServletException {
        // Sem inicialização especial
    }

    @Override
    public void destroy() {
        // Sem recursos para liberar
    }

    // =========================================================
    // Lógica principal do filtro
    // =========================================================

    @Override
    public void doFilter(ServletRequest  servletRequest,
                         ServletResponse servletResponse,
                         FilterChain     chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // [1] Ignora recursos estáticos e rotas gerenciadas por outros filtros
        if (deveIgnorar(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // [2] Ignora requisições anônimas (ex: antes do login ser processado)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }

        // [3] Pós-processamento: deixa a requisição fluir e captura o status
        ResultadoAcesso resultado = ResultadoAcesso.SUCESSO;
        try {
            chain.doFilter(request, response);

            // HTTP 4xx e 5xx são tratados como ERRO de acesso
            if (response.getStatus() >= 400) {
                resultado = ResultadoAcesso.ERRO;
            }

        } catch (IOException | ServletException | RuntimeException ex) {
            resultado = ResultadoAcesso.ERRO;
            throw ex;  // Re-lança sem engolir a exceção original

        } finally {
            // [4] Grava o log independente do resultado da requisição
            gravarLog(request, response, auth, resultado);
        }
    }

    // =========================================================
    // Persistência do log — transação independente (REQUIRES_NEW)
    // =========================================================

    /**
     * Persiste o registro de auditoria em transação própria.
     *
     * <p>Usa EntityManager manual (não injetado via @PersistenceContext)
     * para garantir controle total do ciclo de vida da transação dentro
     * de um filtro Servlet, que não é gerenciado pelo container EJB.</p>
     *
     * @param request   requisição HTTP
     * @param response  resposta HTTP (para capturar status code)
     * @param auth      autenticação do Spring Security (contém o usuário)
     * @param resultado SUCESSO ou ERRO
     */
    protected void gravarLog(HttpServletRequest  request,
                              HttpServletResponse response,
                              Authentication      auth,
                              ResultadoAcesso     resultado) {

        if (emf == null) {
            LOG.warning("[AuditoriaFilter] EntityManagerFactory não injetado — log ignorado.");
            return;
        }

        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();

            String  email  = auth.getName();
            String  ip     = resolverIp(request);
            String  acao   = construirDescricaoAcao(request, response);

            // Localiza a entidade Usuario para o relacionamento ManyToOne
            Usuario usuario = buscarUsuarioPorEmail(em, email);
            if (usuario == null) {
                // Usuário não encontrado no banco — situação anômala, loga no servidor
                LOG.warning("[AuditoriaFilter] Usuário autenticado não encontrado no banco: " + email);
                em.getTransaction().rollback();
                return;
            }

            LogAcesso log = new LogAcesso(usuario, LocalDateTime.now(), acao, ip, resultado);
            em.persist(log);

            em.getTransaction().commit();

        } catch (Exception ex) {
            // Log de auditoria NUNCA pode derrubar a aplicação
            LOG.log(Level.SEVERE, "[AuditoriaFilter] Falha ao gravar log de auditoria", ex);
            try {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
            } catch (Exception rollbackEx) {
                LOG.log(Level.SEVERE, "[AuditoriaFilter] Falha no rollback", rollbackEx);
            }
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    // =========================================================
    // Métodos auxiliares
    // =========================================================

    /**
     * Constrói a descrição da ação auditada no formato padronizado:
     * {@code "GET /pages/vendas/lista.xhtml [HTTP 200]"}
     *
     * <p>A URI é truncada em 85 caracteres para respeitar o limite de 100
     * caracteres da coluna {@code acao} na tabela {@code log_acessos}.</p>
     */
    private String construirDescricaoAcao(HttpServletRequest  request,
                                          HttpServletResponse response) {
        String metodo = request.getMethod();
        String uri    = request.getRequestURI();
        int    status = response.getStatus();

        if (uri != null && uri.length() > 85) {
            uri = uri.substring(0, 82) + "...";
        }

        return String.format("%s %s [HTTP %d]", metodo, uri, status);
    }

    /**
     * Busca a entidade {@link Usuario} pelo email no banco de dados.
     *
     * @param em    EntityManager com transação ativa
     * @param email email do usuário autenticado
     * @return entidade Usuario ou {@code null} se não encontrada
     */
    private Usuario buscarUsuarioPorEmail(EntityManager em, String email) {
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

    /**
     * Resolve o IP real do cliente, respeitando proxies e load-balancers.
     * Prioriza {@code X-Forwarded-For} (padrão em infraestruturas com proxy reverso).
     *
     * @param request requisição HTTP
     * @return IP de origem do cliente
     */
    static String resolverIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — pega apenas o primeiro (cliente real)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Verifica se a URI deve ser ignorada pelo filtro de auditoria.
     *
     * @param uri URI da requisição
     * @return {@code true} se a URI é de recurso estático ou rota pública
     */
    private boolean deveIgnorar(String uri) {
        if (uri == null) return true;
        for (String prefixo : IGNORAR_PREFIXOS) {
            if (uri.contains(prefixo)) return true;
        }
        return false;
    }
}
