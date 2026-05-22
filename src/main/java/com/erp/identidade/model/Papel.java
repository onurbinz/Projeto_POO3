package com.erp.identidade.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

/**
 * Entidade que representa um papel (role) no sistema RBAC.
 *
 * <p>Papéis são utilizados para controle de acesso baseado em roles
 * (Role-Based Access Control). Cada papel define um conjunto de
 * permissões que são concedidas aos usuários vinculados.</p>
 *
 * <h3>Papéis padrão do sistema:</h3>
 * <ul>
 *   <li>{@code ROLE_ADMIN}    — Acesso total ao sistema</li>
 *   <li>{@code ROLE_GERENTE}  — Gestão de equipe, relatórios e financeiro</li>
 *   <li>{@code ROLE_BARBEIRO} — Agenda própria, registro de atendimentos</li>
 *   <li>{@code ROLE_CAIXA}    — Operações de venda e fechamento de caixa</li>
 * </ul>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code ManyToMany} (inverso) com {@link Usuario} — mapeado pelo campo {@code papeis} da entidade Usuario</li>
 * </ul>
 *
 * @see Usuario
 */
@Entity
@Table(name = "papeis")
public class Papel implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==========================================================
    // Chave Primária
    // ==========================================================

    /**
     * Identificador único do papel.
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
     * Nome do papel, seguindo a convenção do Spring Security: ROLE_*.
     * Deve ser único no sistema.
     * Exemplos: ROLE_ADMIN, ROLE_GERENTE, ROLE_BARBEIRO, ROLE_CAIXA.
     */
    @Column(name = "nome", nullable = false, unique = true, length = 50)
    private String nome;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Usuários que possuem este papel.
     *
     * <p>Lado inverso (mappedBy) do relacionamento ManyToMany.
     * O lado "dono" está em {@link Usuario#papeis}.</p>
     *
     * <p>O JPA não gera colunas extras nesta entidade — a tabela
     * intermediária {@code usuario_papel} é controlada pela entidade Usuario.</p>
     */
    @ManyToMany(mappedBy = "papeis")
    private Set<Usuario> usuarios = new HashSet<>();

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public Papel() {
    }

    /**
     * Construtor de conveniência.
     *
     * @param nome nome do papel (ex: "ROLE_ADMIN")
     */
    public Papel(String nome) {
        this.nome = nome;
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

    public Set<Usuario> getUsuarios() {
        return usuarios;
    }

    public void setUsuarios(Set<Usuario> usuarios) {
        this.usuarios = usuarios;
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Papel papel = (Papel) o;
        return Objects.equals(id, papel.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Papel{" +
               "id=" + id +
               ", nome='" + nome + '\'' +
               '}';
    }
}
