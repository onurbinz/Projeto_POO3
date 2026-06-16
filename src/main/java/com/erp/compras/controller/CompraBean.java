package com.erp.compras.controller;

import com.erp.compras.model.SugestaoCompra;
import com.erp.compras.service.CompraService;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;

/**
 * Managed Bean da tela de Sugestões de Compra.
 *
 * <h3>Responsabilidade única:</h3>
 * <p>Carrega as sugestões de compra geradas pelo {@link CompraService}
 * e as expõe para a view {@code sugestao_compras.xhtml}.
 * Não possui estado complexo — é essencialmente um "bridge" entre
 * o EJB de análise e a view de apresentação.</p>
 *
 * <h3>Atualização manual:</h3>
 * <p>O método {@link #atualizar()} permite que o usuário recarregue
 * a análise on-demand via botão "Atualizar" na tela, sem precisar
 * recarregar a página inteira (AJAX).</p>
 *
 * @see CompraService
 * @see SugestaoCompra
 */
@Named("compraBean")
@ViewScoped
public class CompraBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(CompraBean.class.getName());

    @EJB
    private CompraService compraService;

    // =========================================================
    // Estado da view
    // =========================================================

    /** Lista de sugestões geradas pelo EJB. */
    private List<SugestaoCompra> sugestoes;

    // =========================================================
    // Inicialização
    // =========================================================

    @PostConstruct
    public void init() {
        carregarSugestoes();
    }

    // =========================================================
    // Ações
    // =========================================================

    /**
     * Recarrega as sugestões de compra via AJAX (botão "Atualizar").
     * Útil após o usuário registrar uma entrada de estoque manual.
     */
    public void atualizar() {
        carregarSugestoes();
        LOG.info("[CompraBean] Sugestões recarregadas pelo usuário.");
    }

    // =========================================================
    // Helpers para a view
    // =========================================================

    /**
     * Indica se existem sugestões de compra.
     * Usado para renderizar condicionalmente a mensagem "Nenhum produto crítico".
     *
     * @return {@code true} se houver pelo menos uma sugestão
     */
    public boolean isHaSugestoes() {
        return sugestoes != null && !sugestoes.isEmpty();
    }

    /**
     * Conta sugestões com estoque zerado (urgência máxima).
     * Exibido no badge de alerta no cabeçalho da página.
     *
     * @return número de produtos com estoque = 0
     */
    public long getTotalCriticos() {
        if (sugestoes == null) return 0;
        return sugestoes.stream().filter(SugestaoCompra::isEstoqueZerado).count();
    }

    // =========================================================
    // Auxiliares privados
    // =========================================================

    private void carregarSugestoes() {
        sugestoes = compraService.gerarSugestoes();
    }

    // =========================================================
    // Getters
    // =========================================================

    public List<SugestaoCompra> getSugestoes()   { return sugestoes; }
}
