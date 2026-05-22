package com.erp.identidade.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

/**
 * Entidade que representa um usuário do sistema ERP.
 *
 * <p>Cada usuário possui credenciais de acesso (email/senha),
 * um status de ativação e um conjunto de papéis (RBAC) que
 * definem suas permissões no sistema.</p>
 *
 * <p>A senha é armazenada como hash BCrypt — nunca em texto puro.</p>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code ManyToMany} com {@link Papel} via tabela intermediária {@code usuario_papel}</li>
 * </ul>
 *
 * @see Papel
 * @see LogAcesso
 */
@Entity
@Table(name = "usuarios")
public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==========================================================
    // Chave Primária
    // ==========================================================

    /**
     * Identificador único do usuário.
     * Gerado automaticamente pelo PostgreSQL (BIGSERIAL).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ==========================================================
    // Atributos
    // ==========================================================

    /**
     * Nome completo do usuário.
     */
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    /**
     * Email do usuário — utilizado como login.
     * Deve ser único no sistema.
     */
    @Column(name = "email", nullable = false, unique = true, length = 200)
    private String email;

    /**
     * Senha armazenada como hash BCrypt.
     * Exemplo de valor armazenado: "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
     * Nunca armazenar texto puro.
     */
    @Column(name = "senha", nullable = false, length = 255)
    private String senha;

    /**
     * Indica se o usuário está ativo no sistema.
     * Usuários inativos não conseguem fazer login.
     */
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Papéis (roles) atribuídos ao usuário para controle de acesso RBAC.
     *
     * <p>Relacionamento ManyToMany com tabela intermediária {@code usuario_papel}.</p>
     *
     * <ul>
     *   <li>{@code FetchType.LAZY}: papéis só são carregados quando acessados,
     *       evitando consultas desnecessárias ao banco.</li>
     * </ul>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usuario_papel",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "papel_id")
    )
    private Set<Papel> papeis = new HashSet<>();

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public Usuario() {
    }

    /**
     * Construtor de conveniência para criação de usuários.
     *
     * @param nome  nome completo
     * @param email email (será usado como login)
     * @param senha hash BCrypt da senha
     */
    public Usuario(String nome, String email, String senha) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.ativo = true;
    }

    // ==========================================================
    // Getters e Setters
    // ==========================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Set<Papel> getPapeis() {
        return papeis;
    }

    public void setPapeis(Set<Papel> papeis) {
        this.papeis = papeis;
    }

    // ==========================================================
    // Métodos utilitários
    // ==========================================================

    /**
     * Adiciona um papel ao usuário.
     * Mantém a consistência bidirecional do relacionamento.
     */
    public void adicionarPapel(Papel papel) {
        this.papeis.add(papel);
        papel.getUsuarios().add(this);
    }

    /**
     * Remove um papel do usuário.
     * Mantém a consistência bidirecional do relacionamento.
     */
    public void removerPapel(Papel papel) {
        this.papeis.remove(papel);
        papel.getUsuarios().remove(this);
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(id, usuario.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Usuario{" +
               "id=" + id +
               ", nome='" + nome + '\'' +
               ", email='" + email + '\'' +
               ", ativo=" + ativo +
               '}';
    }
}
