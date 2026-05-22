package com.erp.catalogo.model;

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

/**
 * Entidade que representa uma categoria de produtos/serviços da barbearia.
 *
 * <p>Categorias organizam o catálogo em grupos lógicos, facilitando
 * a navegação e a geração de relatórios por segmento.</p>
 *
 * <h3>Exemplos de categorias:</h3>
 * <ul>
 *   <li><b>Serviços</b>: Corte, Barba, Hidratação, Coloração</li>
 *   <li><b>Produtos</b>: Pomadas, Shampoos, Óleos para barba, Lâminas</li>
 *   <li><b>Combos</b>: Corte + Barba, Dia do Noivo</li>
 * </ul>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code OneToMany} com {@link Produto} — uma categoria agrupa N produtos</li>
 * </ul>
 *
 * @see Produto
 */
@Entity
@Table(name = "categorias")
public class Categoria implements Serializable {

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
     * Nome da categoria. Deve ser único para evitar duplicatas.
     * Exemplos: "Cortes", "Barba", "Pomadas", "Combos".
     */
    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    /**
     * Descrição detalhada da categoria.
     * Útil para exibição no catálogo e relatórios.
     */
    @Column(name = "descricao", length = 500)
    private String descricao;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Lista de produtos que pertencem a esta categoria.
     *
     * <p><b>mappedBy = "categoria"</b>: o lado dono do relacionamento
     * está no campo {@link Produto#categoria}. Esta lista é apenas
     * a visão inversa — não gera colunas extras nesta tabela.</p>
     *
     * <p>Útil para consultas como "listar todos os produtos de Pomadas".</p>
     */
    @OneToMany(mappedBy = "categoria")
    private List<Produto> produtos = new ArrayList<>();

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public Categoria() {
    }

    /**
     * Construtor de conveniência.
     *
     * @param nome      nome da categoria
     * @param descricao descrição da categoria
     */
    public Categoria(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
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

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
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
        Categoria categoria = (Categoria) o;
        return Objects.equals(id, categoria.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Categoria{" +
               "id=" + id +
               ", nome='" + nome + '\'' +
               '}';
    }
}
