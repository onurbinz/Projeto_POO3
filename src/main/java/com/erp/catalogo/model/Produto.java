package com.erp.catalogo.model;

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

import com.erp.compras.model.Fornecedor;

/**
 * Entidade que representa um produto ou serviço no catálogo da barbearia.
 *
 * <p>Produtos englobam tanto itens físicos (pomadas, lâminas, shampoos)
 * quanto serviços prestados (corte, barba, hidratação). Todos possuem
 * preço, e os itens físicos possuem controle de estoque.</p>
 *
 * <h3>Controle de Estoque:</h3>
 * <p>Quando {@code quantidadeEstoque} atinge ou fica abaixo de
 * {@code quantidadeMinima}, o sistema deve gerar um alerta de
 * reposição (implementado nos EJBs do módulo Compras).</p>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code ManyToOne} com {@link Categoria} — cada produto pertence a uma categoria</li>
 *   <li>{@code ManyToOne} com {@link Fornecedor} — cada produto tem um fornecedor principal</li>
 * </ul>
 *
 * <h3>Regra financeira:</h3>
 * <p>O campo {@code preco} utiliza {@link BigDecimal} e <b>nunca</b>
 * {@code double/float}. Tipos de ponto flutuante causam erros de
 * arredondamento em valores monetários (ex: 0.1 + 0.2 ≠ 0.3).</p>
 *
 * @see Categoria
 * @see Fornecedor
 */
@Entity
@Table(name = "produtos")
public class Produto implements Serializable {

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
     * Nome do produto ou serviço.
     * Exemplos: "Pomada Matte", "Corte Degradê", "Óleo para Barba".
     */
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    /**
     * Descrição detalhada do produto/serviço.
     * Exibida no catálogo e nos detalhes do produto.
     */
    @Column(name = "descricao", length = 500)
    private String descricao;

    /**
     * Preço de venda do produto/serviço.
     *
     * <p>Utiliza {@code BigDecimal} para precisão monetária absoluta.
     * Armazenado no PostgreSQL como {@code NUMERIC(10,2)} — suporta
     * valores de até R$ 99.999.999,99.</p>
     *
     * <p><b>precision = 10</b>: total de dígitos significativos.</p>
     * <p><b>scale = 2</b>: dígitos após a vírgula (centavos).</p>
     */
    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    /**
     * Quantidade atual em estoque.
     *
     * <p>Para serviços (que não têm estoque físico), pode ser
     * mantido como {@code null} ou {@code 0}.</p>
     */
    @Column(name = "quantidade_estoque")
    private Integer quantidadeEstoque;

    /**
     * Quantidade mínima em estoque antes de gerar alerta de reposição.
     *
     * <p>Quando {@code quantidadeEstoque <= quantidadeMinima}, o sistema
     * deve notificar o gerente para fazer um pedido ao fornecedor.</p>
     */
    @Column(name = "quantidade_minima")
    private Integer quantidadeMinima;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Categoria à qual este produto pertence.
     *
     * <p><b>Lado dono</b> do relacionamento ManyToOne.
     * É este lado que gera a coluna FK {@code categoria_id} na tabela {@code produtos}.</p>
     *
     * <p>{@code FetchType.LAZY}: a categoria só é carregada quando acessada,
     * evitando joins desnecessários em listagens de produtos.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /**
     * Fornecedor principal deste produto.
     *
     * <p><b>Referência cross-boundary (DDD)</b>: Produto está no módulo
     * Catálogo, mas referencia Fornecedor do módulo Compras. Isso é
     * aceitável em um monolito WAR com módulos lógicos.</p>
     *
     * <p>{@code nullable = true}: serviços podem não ter fornecedor
     * (ex: "Corte Degradê" é prestado internamente).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public Produto() {
    }

    /**
     * Construtor para produtos físicos (com estoque e fornecedor).
     *
     * @param nome              nome do produto
     * @param descricao         descrição detalhada
     * @param preco             preço de venda
     * @param quantidadeEstoque quantidade atual em estoque
     * @param quantidadeMinima  quantidade mínima para alerta
     * @param categoria         categoria do produto
     * @param fornecedor        fornecedor principal
     */
    public Produto(String nome, String descricao, BigDecimal preco,
                   Integer quantidadeEstoque, Integer quantidadeMinima,
                   Categoria categoria, Fornecedor fornecedor) {
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.quantidadeEstoque = quantidadeEstoque;
        this.quantidadeMinima = quantidadeMinima;
        this.categoria = categoria;
        this.fornecedor = fornecedor;
    }

    /**
     * Construtor para serviços (sem estoque, sem fornecedor).
     *
     * @param nome      nome do serviço
     * @param descricao descrição detalhada
     * @param preco     preço do serviço
     * @param categoria categoria do serviço
     */
    public Produto(String nome, String descricao, BigDecimal preco,
                   Categoria categoria) {
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
    }

    // ==========================================================
    // Métodos de negócio
    // ==========================================================

    /**
     * Verifica se o estoque está abaixo do mínimo configurado.
     *
     * @return {@code true} se o estoque precisa ser reposto
     */
    public boolean isEstoqueBaixo() {
        if (quantidadeEstoque == null || quantidadeMinima == null) {
            return false;
        }
        return quantidadeEstoque <= quantidadeMinima;
    }

    /**
     * Verifica se este item é um serviço (sem controle de estoque).
     *
     * @return {@code true} se não possui estoque configurado
     */
    public boolean isServico() {
        return quantidadeEstoque == null && fornecedor == null;
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

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public Integer getQuantidadeEstoque() {
        return quantidadeEstoque;
    }

    public void setQuantidadeEstoque(Integer quantidadeEstoque) {
        this.quantidadeEstoque = quantidadeEstoque;
    }

    public Integer getQuantidadeMinima() {
        return quantidadeMinima;
    }

    public void setQuantidadeMinima(Integer quantidadeMinima) {
        this.quantidadeMinima = quantidadeMinima;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public Fornecedor getFornecedor() {
        return fornecedor;
    }

    public void setFornecedor(Fornecedor fornecedor) {
        this.fornecedor = fornecedor;
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Produto produto = (Produto) o;
        return Objects.equals(id, produto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Produto{" +
               "id=" + id +
               ", nome='" + nome + '\'' +
               ", preco=" + preco +
               ", estoque=" + quantidadeEstoque +
               '}';
    }
}
