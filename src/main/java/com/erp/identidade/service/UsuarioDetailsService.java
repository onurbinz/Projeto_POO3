package com.erp.identidade.service;

import com.erp.identidade.model.Papel;
import com.erp.identidade.model.Usuario;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Implementação do {@link UserDetailsService} do Spring Security.
 *
 * <p>É o ponto de integração entre o Spring Security e a camada JPA do ERP.
 * Responsável por carregar os dados do usuário pelo email (usado como login)
 * e converter os {@link Papel}s JPA em {@link GrantedAuthority}s do Spring.</p>
 *
 * <p>Declarado como {@code @Stateless} EJB para participar das transações
 * JTA do WildFly e usar o {@link EntityManager} gerenciado pelo container.</p>
 *
 * @see SecurityConfig
 */
@Service
@Stateless
public class UsuarioDetailsService implements UserDetailsService {

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    /**
     * Carrega o usuário pelo email e constrói o {@link UserDetails} do Spring Security.
     *
     * @param email email usado como username de login
     * @return UserDetails com senha hash BCrypt e roles mapeadas
     * @throws UsernameNotFoundException se nenhum usuário ativo for encontrado
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            // JOIN FETCH garante que os papéis sejam carregados na mesma query
            // evitando LazyInitializationException fora do contexto de persistência
            Usuario usuario = em.createQuery(
                    "SELECT u FROM Usuario u JOIN FETCH u.papeis WHERE u.email = :email AND u.ativo = true",
                    Usuario.class)
                .setParameter("email", email)
                .getSingleResult();

            // Mapeia Papel JPA → GrantedAuthority Spring Security
            Set<GrantedAuthority> authorities = usuario.getPapeis().stream()
                .map(Papel::getNome)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

            return org.springframework.security.core.userdetails.User
                .withUsername(usuario.getEmail())
                .password(usuario.getSenha())   // Hash BCrypt — nunca texto puro
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(!usuario.isAtivo())
                .credentialsExpired(false)
                .disabled(!usuario.isAtivo())
                .build();

        } catch (NoResultException e) {
            throw new UsernameNotFoundException("Usuário não encontrado ou inativo: " + email);
        }
    }
}
