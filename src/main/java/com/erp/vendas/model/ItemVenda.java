package com.erp.vendas.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.erp.catalogo.model.Produto;

/**
 * Entidade que representa um item individual dentro de uma venda.
 *
 * <p>Cada ItemVenda liga um {@link Produto} a uma {@link Venda},
 * registrando a quantidade vendida e o preço unitário no momento
 * da venda.</p>
 *
 * <h3>Por que guardar o preço aqui e não usar o preço do Produto?</h3>
 * <p>O preço de um produto pode mudar ao longo do tempo (reajustes,
 * promoções). Se usássemos apenas a referência ao produto, o histórico
 * financeiro ficaria inconsistente. O {@code precoUnitario} do item
 * representa o <b>preço que foi praticado no momento da venda</b>,
 * funcionando como um "snapshot" imutável.</p>
 *
 * <h3>Composição com Venda:</h3>
 * <p>ItemVenda não existe sozinho — é sempre parte de uma Venda.
 * A Venda gerencia o ciclo de vida dos itens via
 * {@code CascadeType.ALL} e {@code orphanRemoval = true}.</p>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code ManyToOne} com {@link Venda}   — venda à qual pertence (lado dono)</li>
 *   <li>{@code ManyToOne} com {@link Produto} — produto vendido</li>
 * </ul>
 *
 * @see Venda
 * @see Produto
 */
@Entity
@Table(name = "itens_venda")
public class ItemVenda implements Serializable {

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
     * Quantidade do produto vendida neste item.
     */
    @Column(name = "quantidade", nullable = false)
    private Integer quantidade;

    /**
     * Preço unitário praticado no momento da venda.
     *
     * <p>Este valor é um "snapshot" — não muda mesmo que o preço
     * do produto seja alterado posteriormente. Isso garante a
     * integridade do histórico financeiro.</p>
     *
     * <p>Armazenado como {@code NUMERIC(10,2)}.</p>
     */
    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Venda à qual este item pertence.
     *
     * <p><b>Lado dono</b> do relacionamento ManyToOne.
     * Gera a coluna FK {@code venda_id} na tabela {@code itens_venda}.</p>
     *
     * <p>{@code nullable = false}: todo item deve pertencer a uma venda.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    /**
     * Produto vendido neste item.
     *
     * <p>Referência cross-boundary (DDD): ItemVenda (módulo Vendas)
     * referencia Produto (módulo Catálogo).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public ItemVenda() {
    }

    /**
     * Construtor de conveniência.
     *
     * @param produto        produto vendido
     * @param quantidade     quantidade vendida
     * @param precoUnitario  preço praticado no momento da venda
     */
    public ItemVenda(Produto produto, Integer quantidade, BigDecimal precoUnitario) {
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }

    // ==========================================================
    // Métodos de negócio
    // ==========================================================

    /**
     * Calcula o subtotal deste item (preço × quantidade).
     *
     * @return subtotal do item
     */
    public BigDecimal getSubtotal() {
        if (precoUnitario == null || quantidade == null) {
            return BigDecimal.ZERO;
        }
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
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

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public Venda getVenda() {
        return venda;
    }

    public void setVenda(Venda venda) {
        this.venda = venda;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemVenda itemVenda = (ItemVenda) o;
        return Objects.equals(id, itemVenda.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ItemVenda{" +
               "id=" + id +
               ", produtoId=" + (produto != null ? produto.getId() : null) +
               ", quantidade=" + quantidade +
               ", precoUnitario=" + precoUnitario +
               ", subtotal=" + getSubtotal() +
               '}';
    }
}
