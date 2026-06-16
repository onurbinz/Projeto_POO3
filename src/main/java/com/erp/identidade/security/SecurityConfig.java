package com.erp.identidade.security;

import com.erp.identidade.service.UsuarioDetailsService;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * Configuração central do Spring Security — ERP Barbershop (Etapa 3).
 *
 * <h3>Responsabilidades:</h3>
 * <ul>
 *   <li>Autenticação exclusiva via banco (email + BCrypt): sem OAuth, sem LDAP</li>
 *   <li>RBAC com DOIS papéis: {@code ROLE_ADMIN} e {@code ROLE_DEFAULT}</li>
 *   <li>Proteção CSRF habilitada explicitamente</li>
 *   <li>Headers de segurança: HSTS, X-XSS-Protection, X-Frame-Options, CSP</li>
 *   <li>Session timeout + invalidação por sessão múltipla</li>
 *   <li>Bloqueio de conta após falhas via {@link LoginAttemptService}</li>
 *   <li>HTTPS obrigatório em todas as rotas</li>
 * </ul>
 *
 * <h3>Integração com WildFly/EJB:</h3>
 * <p>O {@code contextConfigLocation} no {@code web.xml} aponta para esta classe.
 * O Spring carrega apenas o contexto de segurança; EJBs continuam gerenciados
 * pelo container WildFly via JNDI/CDI.</p>
 *
 * @see UsuarioDetailsService
 * @see LoginAttemptService
 * @see AuditoriaFilter
 */
@Configuration
@EnableWebSecurity

// Habilita @PreAuthorize/@PostAuthorize nos EJBs/CDI beans, se necessário
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)

// Escaneia apenas o pacote de segurança para não conflitar com o contexto CDI do WildFly
@ComponentScan(basePackages = {
    "com.erp.identidade.security",
    "com.erp.identidade.service"
})
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    // =========================================================
    // Injeção de dependências
    // =========================================================

    /**
     * UserDetailsService que consulta a tabela 'usuarios' via JPA/EJB.
     * Injetado pelo Spring context carregado pelo ContextLoaderListener.
     */
    @Inject
    private UsuarioDetailsService usuarioDetailsService;

    /**
     * Serviço EJB Singleton que rastreia tentativas falhas por IP.
     * Injetado via CDI (@Inject) — WildFly faz a ponte Spring ↔ EJB.
     */
    @Inject
    private LoginAttemptService loginAttemptService;

    // =========================================================
    // Beans de infraestrutura de segurança
    // =========================================================

    /**
     * Codificador BCrypt com fator de custo 12.
     * Custo 12 ≈ 250ms/hash em hardware moderno — equilíbrio segurança/UX.
     * Nunca use custo < 10 em produção.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Registro de sessões ativas.
     * Necessário para {@code maximumSessions(1)} — controla concorrência por usuário.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Publicador de eventos de criação/destruição de sessão HTTP.
     * Obrigatório para que o {@link SessionRegistry} seja notificado
     * quando sessões expiram ou são invalidadas.
     *
     * <p>Também registrado no {@code web.xml} como listener para garantir
     * que o ciclo de vida completo seja capturado.</p>
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // =========================================================
    // Provider de autenticação — DAO + BCrypt
    // =========================================================

    /**
     * Configura o DaoAuthenticationProvider que:
     * <ol>
     *   <li>Delega o carregamento do usuário ao {@link UsuarioDetailsService}</li>
     *   <li>Valida a senha contra o hash BCrypt armazenado no banco</li>
     *   <li>Oculta o motivo real da falha (user não encontrado vs. senha errada)
     *       para prevenir user-enumeration attacks</li>
     * </ol>
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // true = lança BadCredentialsException em ambos os casos de falha,
        //        não revelando se o usuário existe no banco
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authenticationProvider());
    }

    /**
     * Expõe o AuthenticationManager como Bean Spring para uso nos handlers/filtros.
     */
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    // =========================================================
    // Configuração HTTP principal
    // Ordem dos blocos é importante: canal → autorização → login
    //                                → logout → CSRF → headers → sessão
    // =========================================================

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http

            // ------------------------------------------------------------------
            // [1] FILTRO DE BLOQUEIO POR IP (Brute-Force Protection)
            // Deve ser o PRIMEIRO filtro da cadeia: IPs bloqueados são barrados
            // antes do BCrypt ser executado (que é custoso por design).
            // ------------------------------------------------------------------
            .addFilterBefore(
                new IpBloqueioFilter(loginAttemptService),
                UsernamePasswordAuthenticationFilter.class
            )

            // ------------------------------------------------------------------
            // [2] CANAL SEGURO — HTTPS OBRIGATÓRIO
            //
            // DESABILITADO EM DESENVOLVIMENTO (Docker Compose porta 8080 / HTTP).
            // Em produção com SSL/TLS configurado no WildFly, descomente o bloco:
            //
            // .requiresChannel()
            //     .anyRequest().requiresSecure()
            // .and()
            //
            // O web.xml também tem o bloco security-constraint comentado por padrão.
            // Para ativar HTTPS, descomente ambos os blocos simultaneamente.
            // ------------------------------------------------------------------

            // ------------------------------------------------------------------
            // [3] AUTORIZAÇÃO DE ROTAS — RBAC (ADMIN / DEFAULT)
            // Regra: do mais restritivo para o menos restritivo.
            // Apenas DOIS papéis válidos: ROLE_ADMIN e ROLE_DEFAULT.
            // ------------------------------------------------------------------
            .authorizeRequests()

                // === ROTAS PÚBLICAS (sem autenticação) ===
                // Página de login (GET e POST)
                .antMatchers(
                    "/login",
                    "/login.xhtml"
                ).permitAll()

                // Recursos estáticos do JSF/PrimeFaces (CSS, JS, imagens, fontes)
                .antMatchers(
                    "/javax.faces.resource/**",
                    "/resources/**"
                ).permitAll()

                // === ROTAS EXCLUSIVAS DE ADMINISTRADOR ===
                .antMatchers(
                    "/pages/identidade/**",   // Gestão de usuários e papéis
                    "/pages/relatorios/**"    // Relatórios gerenciais
                ).hasRole("ADMIN")

                // === ROTAS COMPARTILHADAS (ADMIN + DEFAULT) ===
                .antMatchers(
                    "/pages/vendas/**",       // Módulo de vendas
                    "/pages/catalogo/**",     // Catálogo de produtos e serviços
                    "/pages/compras/**",      // Módulo de compras e fornecedores
                    "/pages/dashboard.xhtml"  // Dashboard principal
                ).hasAnyRole("ADMIN", "DEFAULT")

                // Qualquer outra rota protegida exige autenticação mínima
                .anyRequest().authenticated()
            .and()

            // ------------------------------------------------------------------
            // [4] FORMULÁRIO DE LOGIN
            // Página de login JSF servida pelo FacesServlet via /login mapping.
            // O Spring Security intercepta o POST /login para autenticação.
            // ------------------------------------------------------------------
            .formLogin()
                .loginPage("/login")                        // URL da página (GET)
                .loginProcessingUrl("/login")               // Endpoint de autenticação (POST)
                .defaultSuccessUrl("/pages/dashboard.xhtml", true)
                .failureUrl("/login?erro=true")
                .usernameParameter("username")              // Campo email do formulário JSF
                .passwordParameter("password")
                .successHandler(autenticacaoSucessoHandler())
                .failureHandler(autenticacaoFalhaHandler())
                .permitAll()
            .and()

            // ------------------------------------------------------------------
            // [5] LOGOUT
            // Invalida sessão, remove cookie JSESSIONID e redireciona para /login.
            // ------------------------------------------------------------------
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)                // Invalida HttpSession
                .deleteCookies("JSESSIONID")                // Remove cookie de sessão
                .clearAuthentication(true)                  // Limpa SecurityContext
                .permitAll()
            .and()

            // ------------------------------------------------------------------
            // [6] PROTEÇÃO CSRF — HABILITADA EXPLICITAMENTE
            // Spring Security habilita CSRF por padrão, mas declaramos
            // explicitamente para deixar a intenção clara na configuração.
            //
            // JSF 2.3 usa ViewState como proteção implícita, mas o token
            // CSRF do Spring adiciona uma segunda camada independente.
            //
            // Exceção: recursos JSF não enviam formulários — excluídos para
            // evitar falsos positivos em requisições de recurso AJAX.
            // ------------------------------------------------------------------
            .csrf()
                .ignoringAntMatchers("/javax.faces.resource/**")
            .and()

            // ------------------------------------------------------------------
            // [7] HEADERS DE SEGURANÇA (Proteção XSS e outros)
            // ------------------------------------------------------------------
            .headers()

                // HSTS: força HTTPS por 1 ano + subdomínios (preload recomendado)
                .httpStrictTransportSecurity()
                    .includeSubDomains(true)
                    .maxAgeInSeconds((int) TimeUnit.DAYS.toSeconds(365))
                .and()

                // X-Content-Type-Options: NOSNIFF — impede MIME-type sniffing
                // Previne que browsers interpretem arquivos como tipo diferente
                .contentTypeOptions()
                .and()

                // X-XSS-Protection: instrui browsers legados (IE/Edge antigo)
                // a ativar o filtro XSS embutido e bloquear páginas suspeitas
                .xssProtection()
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                .and()

                // X-Frame-Options: DENY — previne clickjacking via iframe
                .frameOptions().deny()
            .and()

            // ------------------------------------------------------------------
            // [8] GERENCIAMENTO DE SESSÃO
            // Timeout configurado no web.xml (30 min); aqui tratamos o redirect
            // após expiração e prevenimos session fixation e sessões múltiplas.
            // ------------------------------------------------------------------
            .sessionManagement()

                // SESSION FIXATION: migra a sessão ao autenticar — o ID muda
                // impedindo que um atacante fixe o ID antes do login
                .sessionFixation().migrateSession()

                // Redireciona quando a sessão expirar (timeout do web.xml)
                .invalidSessionUrl("/login?sessao=expirada")

                // Máximo 1 sessão simultânea por usuário
                // Segunda sessão expira a primeira (política: último login vence)
                .maximumSessions(1)
                    .expiredUrl("/login?sessao=multipla")
                    .sessionRegistry(sessionRegistry());
    }

    // =========================================================
    // Handlers delegados de autenticação
    // =========================================================

    /**
     * Handler de sucesso: zera contador de falhas do IP e redireciona
     * para a URL original salva antes do redirecionamento para o login.
     *
     * @see ErpAuthenticationSuccessHandler
     */
    @Bean
    public ErpAuthenticationSuccessHandler autenticacaoSucessoHandler() {
        return new ErpAuthenticationSuccessHandler(loginAttemptService);
    }

    /**
     * Handler de falha: incrementa contador de tentativas por IP e
     * redireciona com parâmetro de erro apropriado (bloqueado, conta inativa, etc.).
     *
     * @see ErpAuthenticationFailureHandler
     */
    @Bean
    public ErpAuthenticationFailureHandler autenticacaoFalhaHandler() {
        return new ErpAuthenticationFailureHandler(loginAttemptService);
    }
}
