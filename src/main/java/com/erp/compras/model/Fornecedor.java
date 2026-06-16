package com.erp.compras.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.erp.catalogo.model.Produto;

/**
 * Entidade que representa um fornecedor de produtos para a barbearia.
 *
 * <p>Fornecedores são empresas ou distribuidores que abastecem
 * o estoque da barbearia com produtos como pomadas, lâminas,
 * shampoos, óleos, etc.</p>
 *
 * <p>Pertence ao módulo <b>Compras</b> no DDD, pois fornecedores
 * são parte do processo de aquisição/reposição de estoque.</p>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code OneToMany} com {@link Produto} — um fornecedor fornece N produtos</li>
 * </ul>
 *
 * <h3>Nota sobre DDD — referência entre módulos:</h3>
 * <p>Este é um caso de <b>referência cross-boundary</b>: Fornecedor
 * (módulo Compras) é referenciado por Produto (módulo Catálogo).
 * Em DDD estrito, usaríamos apenas o ID. Aqui mantemos a referência
 * JPA direta por simplicidade, já que ambos os módulos coexistem
 * na mesma unidade de deploy (WAR).</p>
 *
 * @see Produto
 */
@Entity
@Table(name = "fornecedores")
public class Fornecedor implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==========================================================
    // Chave Primária
    // ==========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ==========================================================
    // Atributos
    // ==========================================================

    /**
     * Nome fantasia ou razão social do fornecedor.
     */
    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    /**
     * CNPJ do fornecedor (14 dígitos, armazenado sem formatação).
     * Deve ser único no sistema para evitar cadastro duplicado.
     *
     * <p>Exemplo armazenado: "12345678000190"</p>
     * <p>Exemplo formatado (exibição): "12.345.678/0001-90"</p>
     */
    @Column(name = "cnpj", nullable = false, unique = true, length = 14)
    private String cnpj;

    /**
     * Email de contato do fornecedor para comunicação sobre
     * pedidos, cotações e negociações.
     */
    @Column(name = "email_contato", nullable = false, length = 200)
    private String emailContato;

    /**
     * Telefone de contato do fornecedor.
     * Armazenado sem formatação. Suporta DDD + número (fixo ou celular).
     * Exemplo armazenado: "21987654321"
     */
    @Column(name = "telefone", length = 20)
    private String telefone;

    /**
     * Flag de exclusão lógica (soft delete).
     * {@code true} = fornecedor ativo e visível no sistema.
     * {@code false} = inativo; preservado no banco por integridade referencial.
     */
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Lista de produtos fornecidos por este fornecedor.
     *
     * <p><b>mappedBy = "fornecedor"</b>: o lado dono está em
     * {@link Produto#fornecedor}. Esta lista é a visão inversa.</p>
     *
     * <p>Útil para consultas como "quais produtos o fornecedor X fornece?"
     * e para relatórios de dependência de fornecedores.</p>
     */
    @OneToMany(mappedBy = "fornecedor")
    private List<Produto> produtos = new ArrayList<>();

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public Fornecedor() {
    }

    /**
     * Construtor de conveniência.
     *
     * @param nome          nome fantasia ou razão social
     * @param cnpj          CNPJ sem formatação (14 dígitos)
     * @param emailContato  email de contato
     * @param telefone      telefone de contato (opcional)
     */
    public Fornecedor(String nome, String cnpj, String emailContato, String telefone) {
        this.nome = nome;
        this.cnpj = cnpj;
        this.emailContato = emailContato;
        this.telefone = telefone;
    }

    /**
     * Construtor de conveniência sem telefone.
     *
     * @param nome          nome fantasia ou razão social
     * @param cnpj          CNPJ sem formatação (14 dígitos)
     * @param emailContato  email de contato
     */
    public Fornecedor(String nome, String cnpj, String emailContato) {
        this.nome = nome;
        this.cnpj = cnpj;
        this.emailContato = emailContato;
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

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getEmailContato() {
        return emailContato;
    }

    public void setEmailContato(String emailContato) {
        this.emailContato = emailContato;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public List<Produto> getProdutos() {
        return produtos;
    }

    public void setProdutos(List<Produto> produtos) {
        this.produtos = produtos;
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fornecedor that = (Fornecedor) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Fornecedor{" +
               "id=" + id +
               ", nome='" + nome + '\'' +
               ", cnpj='" + cnpj + '\'' +
               ", emailContato='" + emailContato + '\'' +
               '}';
    }
}
