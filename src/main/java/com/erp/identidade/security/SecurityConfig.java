package com.erp.identidade.security;

import com.erp.identidade.service.UsuarioDetailsService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * Configuração central do Spring Security para o ERP Barbershop.
 *
 * <h3>Responsabilidades:</h3>
 * <ul>
 *   <li>Autenticação via banco (email + BCrypt) — sem OAuth, sem LDAP</li>
 *   <li>RBAC com dois papéis: {@code ROLE_ADMIN} e {@code ROLE_DEFAULT}</li>
 *   <li>Proteção CSRF habilitada + headers de segurança (XSS, HSTS)</li>
 *   <li>Bloqueio de conta após falhas de login (via {@link LoginAttemptService})</li>
 *   <li>Timeout de sessão configurado e invalidação pós-logout</li>
 * </ul>
 *
 * @see UsuarioDetailsService
 * @see LoginAttemptService
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    // =========================================================
    // Injeção de dependências
    // =========================================================

    @Inject
    private UsuarioDetailsService usuarioDetailsService;

    @Inject
    private LoginAttemptService loginAttemptService;

    // =========================================================
    // Beans de infraestrutura de segurança
    // =========================================================

    /**
     * Codificador de senhas BCrypt com custo 12.
     * Custo 12 = ~250ms por hash em hardware moderno (equilíbrio segurança/performance).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Registro de sessões ativas — necessário para controle de concorrência.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Publicador de eventos de sessão HTTP.
     * Registrado no contexto para que o Spring Security rastreie criação/destruição de sessões.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // =========================================================
    // Provider de autenticação — DAO com BCrypt
    // =========================================================

    /**
     * Configura o provider de autenticação que busca o usuário no banco
     * e valida a senha com BCrypt.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Esconde o motivo real da falha (user não encontrado vs senha errada)
        // evitando user enumeration attacks
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authenticationProvider());
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    // =========================================================
    // Configuração HTTP — Autorização, CSRF, Headers, Sessão
    // =========================================================

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http

            // ---- IP BLOQUEIO (brute-force) ------------------------------------
            // Deve ser o PRIMEIRO filtro: IPs bloqueados são barrados antes do BCrypt
            .addFilterBefore(
                new IpBloqueioFilter(loginAttemptService),
                UsernamePasswordAuthenticationFilter.class
            )

            // ---- HTTPS -------------------------------------------------------
            // Redireciona todo tráfego HTTP para HTTPS em produção
            .requiresChannel()
                .anyRequest().requiresSecure()
            .and()

            // ---- AUTORIZAÇÃO DE ROTAS (RBAC) ---------------------------------
            .authorizeRequests()
                // Recursos públicos — CSS, JS, imagens, fontes (sem autenticação)
                .antMatchers("/login",
                             "/login.xhtml",
                             "/javax.faces.resource/**",
                             "/resources/**").permitAll()

                // Rotas exclusivas de ADMIN
                .antMatchers(
                    "/pages/identidade/**",    // Gestão de usuários e papéis
                    "/pages/relatorios/**"     // Relatórios gerenciais
                ).hasRole("ADMIN")

                // Rotas acessíveis por ADMIN e DEFAULT
                .antMatchers(
                    "/pages/vendas/**",        // Módulo de vendas
                    "/pages/catalogo/**",      // Catálogo de produtos
                    "/pages/compras/**"        // Módulo de compras
                ).hasAnyRole("ADMIN", "DEFAULT")

                // Qualquer outra rota exige autenticação mínima
                .anyRequest().authenticated()
            .and()

            // ---- FORMULÁRIO DE LOGIN -----------------------------------------
            .formLogin()
                .loginPage("/login")                     // Página personalizada (JSF)
                .loginProcessingUrl("/login")            // POST endpoint processado pelo Spring
                .defaultSuccessUrl("/pages/dashboard.xhtml", true)
                .failureUrl("/login?erro=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(autenticacaoSucessoHandler())
                .failureHandler(autenticacaoFalhaHandler())
                .permitAll()
            .and()

            // ---- LOGOUT -------------------------------------------------------
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)             // Invalida sessão completa
                .deleteCookies("JSESSIONID")             // Remove cookie de sessão
                .clearAuthentication(true)
                .permitAll()
            .and()

            // ---- PROTEÇÃO CSRF -----------------------------------------------
            // HABILITADA por padrão no Spring Security — explicitamente declarada
            // JSF 2.3 já possui ViewState como token implícito, mas CSRF do Spring
            // adiciona uma camada extra via header X-CSRF-TOKEN
            .csrf()
                .ignoringAntMatchers("/javax.faces.resource/**") // Recursos JSF não enviam CSRF
            .and()

            // ---- HEADERS DE SEGURANÇA ----------------------------------------
            .headers()
                // Strict-Transport-Security: força HTTPS por 1 ano + subdomínios
                .httpStrictTransportSecurity()
                    .includeSubDomains(true)
                    .maxAgeInSeconds((int) TimeUnit.DAYS.toSeconds(365))
                .and()
                // X-Content-Type-Options: impede MIME sniffing
                .contentTypeOptions()
                .and()
                // X-XSS-Protection: instrui browsers legados a bloquear XSS
                .xssProtection()
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                .and()
                // X-Frame-Options: DENY — previne clickjacking em iframes
                .frameOptions().deny()
                // Content-Security-Policy: whitelist explícita de fontes permitidas
                .contentSecurityPolicy(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +   // JSF/PrimeFaces requerem inline
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data:; " +
                    "frame-ancestors 'none';"
                )
            .and()

            // ---- GERENCIAMENTO DE SESSÃO ------------------------------------
            .sessionManagement()
                // Invalida sessão antiga ao fazer novo login (evita session fixation)
                .sessionFixation().migrateSession()
                // Timeout tratado pelo web.xml (30 min); redireciona após expirar
                .invalidSessionUrl("/login?sessao=expirada")
                // Máximo de 1 sessão simultânea por usuário
                .maximumSessions(1)
                    .expiredUrl("/login?sessao=multipla")
                    .sessionRegistry(sessionRegistry());
    }

    // =========================================================
    // Handlers de Sucesso e Falha de Autenticação
    // (delegam para LoginAttemptService para RBAC + auditoria)
    // =========================================================

    @Bean
    public ErpAuthenticationSuccessHandler autenticacaoSucessoHandler() {
        return new ErpAuthenticationSuccessHandler(loginAttemptService);
    }

    @Bean
    public ErpAuthenticationFailureHandler autenticacaoFalhaHandler() {
        return new ErpAuthenticationFailureHandler(loginAttemptService);
    }
}
