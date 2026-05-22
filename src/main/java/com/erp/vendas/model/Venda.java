package com.erp.vendas.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.erp.identidade.model.Usuario;

/**
 * Entidade que representa uma venda realizada na barbearia.
 *
 * <p>Uma venda é composta por um ou mais {@link ItemVenda} e registra
 * quem atendeu/vendeu ({@link Usuario}), a data, a forma de pagamento
 * e o valor total.</p>
 *
 * <h3>Composição (Aggregate Root — DDD):</h3>
 * <p>Venda é o <b>Aggregate Root</b> do módulo Vendas. Os itens da venda
 * ({@link ItemVenda}) só existem no contexto de uma Venda — se a venda
 * for removida, os itens também são. Isso é garantido pelo
 * {@code CascadeType.ALL} + {@code orphanRemoval = true}.</p>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code ManyToOne}  com {@link Usuario}    — quem realizou a venda</li>
 *   <li>{@code OneToMany}  com {@link ItemVenda}  — itens que compõem a venda (cascade)</li>
 * </ul>
 *
 * <h3>Auditoria:</h3>
 * <p>Toda venda deve gerar um registro em {@code log_acessos} com
 * ação "VENDA" (implementado no EJB de vendas).</p>
 *
 * @see ItemVenda
 * @see FormaPagamento
 * @see Usuario
 */
@Entity
@Table(name = "vendas")
public class Venda implements Serializable {

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
     * Data e hora em que a venda foi realizada.
     */
    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda;

    /**
     * Valor total da venda (soma dos itens).
     *
     * <p>{@code BigDecimal} para precisão monetária absoluta.
     * Armazenado como {@code NUMERIC(12,2)} — suporta valores
     * de até R$ 9.999.999.999,99.</p>
     */
    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    /**
     * Forma de pagamento utilizada pelo cliente.
     * Armazenado como texto (VARCHAR) no banco.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Usuário que realizou/registrou a venda.
     *
     * <p>Referência cross-boundary (DDD): Venda (módulo Vendas)
     * referencia Usuario (módulo Identidade). Aceitável no monolito WAR.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Itens que compõem esta venda.
     *
     * <h4>CascadeType.ALL:</h4>
     * <p>Todas as operações de persistência (persist, merge, remove, refresh, detach)
     * propagam da Venda para seus itens automaticamente. Isso significa que
     * ao salvar uma Venda, todos os seus itens são salvos juntos.</p>
     *
     * <h4>orphanRemoval = true:</h4>
     * <p>Se um item for removido da lista {@code itens}, o JPA automaticamente
     * o deleta do banco. Itens não existem sem uma Venda pai.</p>
     *
     * <h4>mappedBy = "venda":</h4>
     * <p>O lado dono do relacionamento está em {@link ItemVenda#venda}.
     * A FK {@code venda_id} fica na tabela {@code itens_venda}.</p>
     */
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public Venda() {
    }

    /**
     * Construtor de conveniência.
     *
     * @param dataVenda       data/hora da venda
     * @param valorTotal      valor total
     * @param formaPagamento  forma de pagamento
     * @param usuario         quem realizou a venda
     */
    public Venda(LocalDateTime dataVenda, BigDecimal valorTotal,
                 FormaPagamento formaPagamento, Usuario usuario) {
        this.dataVenda = dataVenda;
        this.valorTotal = valorTotal;
        this.formaPagamento = formaPagamento;
        this.usuario = usuario;
    }

    // ==========================================================
    // Métodos de composição
    // ==========================================================

    /**
     * Adiciona um item à venda, mantendo a consistência bidirecional.
     *
     * <p>Seta automaticamente a referência {@code item.venda = this},
     * garantindo que o lado dono do relacionamento esteja correto.</p>
     *
     * @param item item a ser adicionado
     */
    public void adicionarItem(ItemVenda item) {
        itens.add(item);
        item.setVenda(this);
    }

    /**
     * Remove um item da venda, mantendo a consistência bidirecional.
     *
     * <p>Com {@code orphanRemoval = true}, o item será automaticamente
     * deletado do banco na próxima sincronização.</p>
     *
     * @param item item a ser removido
     */
    public void removerItem(ItemVenda item) {
        itens.remove(item);
        item.setVenda(null);
    }

    /**
     * Recalcula o valor total da venda com base nos itens.
     *
     * <p>Soma {@code precoUnitario * quantidade} de cada item.
     * Deve ser chamado pelo EJB antes de persistir a venda.</p>
     */
    public void recalcularTotal() {
        this.valorTotal = itens.stream()
            .map(item -> item.getPrecoUnitario()
                             .multiply(BigDecimal.valueOf(item.getQuantidade())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public LocalDateTime getDataVenda() {
        return dataVenda;
    }

    public void setDataVenda(LocalDateTime dataVenda) {
        this.dataVenda = dataVenda;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public List<ItemVenda> getItens() {
        return itens;
    }

    public void setItens(List<ItemVenda> itens) {
        this.itens = itens;
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Venda venda = (Venda) o;
        return Objects.equals(id, venda.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Venda{" +
               "id=" + id +
               ", dataVenda=" + dataVenda +
               ", valorTotal=" + valorTotal +
               ", formaPagamento=" + formaPagamento +
               ", qtdItens=" + itens.size() +
               '}';
    }
}
