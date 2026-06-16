package com.erp.compras.model;

import com.erp.catalogo.model.Produto;
import java.math.BigDecimal;

/**
 * DTO (Data Transfer Object) que representa uma sugestão automática de compra.
 *
 * <h3>O que é este objeto?</h3>
 * <p>NÃO é uma entidade JPA — não é persistida no banco. É um objeto calculado
 * em memória pelo {@link com.erp.compras.service.CompraService} e exibido
 * na tela {@code sugestao_compras.xhtml}.</p>
 *
 * <h3>Campos calculados:</h3>
 * <ul>
 *   <li>{@link #mediaDiariaConsumo} — média de unidades vendidas por dia (30 dias)</li>
 *   <li>{@link #quantidadeSugerida} — quanto pedir ao fornecedor</li>
 *   <li>{@link #vendidosUltimos30Dias} — total vendido na janela de análise</li>
 * </ul>
 *
 * @see com.erp.compras.service.CompraService
 */
public class SugestaoCompra {

    /** Produto que precisa de reposição. */
    private Produto produto;

    /** Fornecedor principal do produto (pode ser null para serviços). */
    private Fornecedor fornecedor;

    /** Quantidade atual em estoque no momento da geração da sugestão. */
    private int estoqueAtual;

    /** Quantidade mínima configurada para disparar o alerta. */
    private int estoqueMinimo;

    /** Total de unidades vendidas nos últimos 30 dias (soma de itens_venda). */
    private int vendidosUltimos30Dias;

    /**
     * Média de consumo diário nos últimos 30 dias.
     * Calculada como: {@code vendidosUltimos30Dias / 30}.
     * Arredondamento HALF_UP com 2 casas decimais.
     */
    private BigDecimal mediaDiariaConsumo;

    /**
     * Quantidade sugerida para o pedido de compra.
     * Calculada pelo {@link com.erp.compras.service.CompraService#calcularQuantidadeSugerida}.
     */
    private int quantidadeSugerida;

    // =========================================================
    // Construtor padrão
    // =========================================================
    public SugestaoCompra() {}

    // =========================================================
    // Helpers para a view (evitam EL verbosa no XHTML)
    // =========================================================

    /**
     * Retorna o nome do produto ou "—" se null.
     *
     * @return nome do produto
     */
    public String getNomeProduto() {
        return produto != null ? produto.getNome() : "—";
    }

    /**
     * Retorna o nome do fornecedor ou "Sem fornecedor" se null.
     * Isso ocorre para serviços que não têm fornecedor físico.
     *
     * @return nome do fornecedor
     */
    public String getNomeFornecedor() {
        return fornecedor != null ? fornecedor.getNome() : "Sem fornecedor";
    }

    /**
     * Retorna o email de contato do fornecedor para facilitar o contato.
     *
     * @return email do fornecedor ou "—" se null
     */
    public String getEmailFornecedor() {
        return fornecedor != null ? fornecedor.getEmailContato() : "—";
    }

    /**
     * Indica criticidade: estoque = 0 (zerado) vs apenas abaixo do mínimo.
     *
     * @return {@code true} se o estoque estiver completamente zerado
     */
    public boolean isEstoqueZerado() {
        return estoqueAtual == 0;
    }

    /**
     * Retorna a severity do p:tag de urgência para o XHTML.
     * Estoque zerado → "danger"; apenas baixo → "warning".
     *
     * @return severity string para PrimeFaces
     */
    public String getSeveridadeUrgencia() {
        return isEstoqueZerado() ? "danger" : "warning";
    }

    /**
     * Rótulo de urgência legível para o usuário.
     *
     * @return "CRÍTICO" ou "BAIXO"
     */
    public String getRotuloUrgencia() {
        return isEstoqueZerado() ? "CRÍTICO" : "BAIXO";
    }

    // =========================================================
    // Getters e Setters
    // =========================================================

    public Produto getProduto()                      { return produto; }
    public void setProduto(Produto p)                { this.produto = p; }

    public Fornecedor getFornecedor()                { return fornecedor; }
    public void setFornecedor(Fornecedor f)          { this.fornecedor = f; }

    public int getEstoqueAtual()                     { return estoqueAtual; }
    public void setEstoqueAtual(int v)               { this.estoqueAtual = v; }

    public int getEstoqueMinimo()                    { return estoqueMinimo; }
    public void setEstoqueMinimo(int v)              { this.estoqueMinimo = v; }

    public int getVendidosUltimos30Dias()            { return vendidosUltimos30Dias; }
    public void setVendidosUltimos30Dias(int v)      { this.vendidosUltimos30Dias = v; }

    public BigDecimal getMediaDiariaConsumo()        { return mediaDiariaConsumo; }
    public void setMediaDiariaConsumo(BigDecimal v)  { this.mediaDiariaConsumo = v; }

    public int getQuantidadeSugerida()               { return quantidadeSugerida; }
    public void setQuantidadeSugerida(int v)         { this.quantidadeSugerida = v; }
}
