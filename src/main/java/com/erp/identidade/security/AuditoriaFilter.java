package com.erp.identidade.security;

import com.erp.identidade.model.LogAcesso;
import com.erp.identidade.model.ResultadoAcesso;
import com.erp.identidade.model.Usuario;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.Priority;
import javax.ejb.EJB;
import javax.inject.Inject;
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
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Filtro de auditoria que intercepta todas as requisições autenticadas
 * e grava registros na tabela {@code log_acessos}.
 *
 * <h3>O que é auditado:</h3>
 * <ul>
 *   <li><b>Usuário</b>     — principal autenticado no Spring Security Context</li>
 *   <li><b>Data/Hora</b>   — {@link LocalDateTime#now()} no momento da requisição</li>
 *   <li><b>Ação</b>        — método HTTP + URI (ex: "GET /pages/vendas/lista.xhtml")</li>
 *   <li><b>IP</b>          — resolvido via X-Forwarded-For ou RemoteAddr</li>
 *   <li><b>Resultado</b>   — {@link ResultadoAcesso#SUCESSO} ou {@link ResultadoAcesso#ERRO}</li>
 * </ul>
 *
 * <h3>Estratégia do filtro:</h3>
 * <p>O filtro usa o padrão "pós-processamento": deixa a requisição prosseguir
 * normalmente com {@code chain.doFilter()} e só persiste o log após a resposta
 * ser gerada, quando o status HTTP já é conhecido.</p>
 *
 * <p>Recursos estáticos (CSS, JS, imagens, fontes) são ignorados para não
 * poluir a tabela de auditoria com centenas de entradas irrelevantes.</p>
 *
 * @see LogAcesso
 * @see ResultadoAcesso
 */
@WebFilter(
    filterName = "AuditoriaFilter",
    urlPatterns = "/*",              // Intercepta todas as requisições
    asyncSupported = true            // Suporte a Servlet 3.x async
)
@Priority(1)                        // Executado após o filtro do Spring Security
public class AuditoriaFilter implements Filter {

    // =========================================================
    // Prefixos de URI que NÃO devem ser auditados
    // (recursos estáticos e endpoints públicos de infra)
    // =========================================================
    private static final String[] IGNORAR_PREFIXOS = {
        "/javax.faces.resource/",
        "/resources/",
        "/login",
        "/logout"
    };

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    @Override
    public void init(FilterConfig config) throws ServletException {
        // Sem inicialização especial necessária
    }

    @Override
    public void destroy() {
        // Sem recursos para liberar
    }

    // =========================================================
    // Lógica principal do filtro
    // =========================================================

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1. Ignora recursos estáticos e rotas públicas
        if (deveIgnorar(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Ignora requisições não-autenticadas (ex: tentativas de login ainda não resolvidas)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }

        // 3. Deixa a requisição prosseguir e captura o status da resposta
        try {
            chain.doFilter(request, response);
            // Pós-processamento: resposta gerada com sucesso
            gravarLog(request, response, auth, ResultadoAcesso.SUCESSO);

        } catch (IOException | ServletException | RuntimeException ex) {
            // Exceção durante o processamento — registra como ERRO
            gravarLog(request, response, auth, ResultadoAcesso.ERRO);
            throw ex; // Re-lança para não engolir a exceção
        }
    }

    // =========================================================
    // Persistência do log — transação própria para garantir
    // que o log seja salvo mesmo se a transação principal falhou
    // =========================================================

    /**
     * Persiste o registro de auditoria no banco de dados.
     *
     * <p>Usa {@link Transactional} para garantir que o log seja gravado em uma
     * transação independente, mesmo que a transação da requisição tenha sido
     * revertida por erro de negócio.</p>
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void gravarLog(HttpServletRequest  request,
                              HttpServletResponse response,
                              Authentication      auth,
                              ResultadoAcesso     resultado) {
        try {
            String email  = auth.getName();
            String ip     = ErpAuthenticationSuccessHandler.resolverIp(request);
            String acao   = construirDescricaoAcao(request, response);

            // Carrega a entidade Usuario para o relacionamento ManyToOne
            Usuario usuario = buscarUsuarioPorEmail(email);
            if (usuario == null) return; // Segurança defensiva

            LogAcesso log = new LogAcesso(
                usuario,
                LocalDateTime.now(),
                acao,
                ip,
                resultado
            );

            em.persist(log);

        } catch (Exception ex) {
            // Log de auditoria NUNCA pode derrubar a aplicação
            // Falhas aqui são logadas no servidor, não propagadas
            System.err.println("[AuditoriaFilter] ERRO ao gravar log de auditoria: " + ex.getMessage());
        }
    }

    // =========================================================
    // Métodos auxiliares
    // =========================================================

    /**
     * Constrói a descrição da ação auditada no formato:
     * {@code "GET /pages/vendas/lista.xhtml [HTTP 200]"}
     */
    private String construirDescricaoAcao(HttpServletRequest  request,
                                          HttpServletResponse response) {
        String metodo = request.getMethod();
        String uri    = request.getRequestURI();
        int    status = response.getStatus();

        // Trunca a URI para o tamanho máximo da coluna 'acao' (100 chars)
        if (uri.length() > 85) {
            uri = uri.substring(0, 82) + "...";
        }

        return String.format("%s %s [HTTP %d]", metodo, uri, status);
    }

    /**
     * Busca o usuario no banco pelo email.
     * Retorna {@code null} se não encontrado (não lança exceção — defensive).
     */
    private Usuario buscarUsuarioPorEmail(String email) {
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
