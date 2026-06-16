package com.erp.compras.controller;

import com.erp.compras.model.Fornecedor;
import com.erp.compras.service.FornecedorService;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean do módulo de Fornecedores.
 *
 * <h3>Responsabilidades:</h3>
 * <ul>
 *   <li>Carregar e manter a lista de fornecedores ativos</li>
 *   <li>Orquestrar abertura dos diálogos de criar/editar</li>
 *   <li>Delegar persistência ao {@link FornecedorService}</li>
 *   <li>Exibir CNPJ formatado na tabela (visual) sem alterar o dado bruto</li>
 * </ul>
 *
 * @see FornecedorService
 */
@Named("fornecedorBean")
@ViewScoped
public class FornecedorBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(FornecedorBean.class.getName());

    @EJB
    private FornecedorService fornecedorService;

    // =========================================================
    // Estado da view
    // =========================================================

    /** Lista de fornecedores exibida na tabela. */
    private List<Fornecedor> fornecedores;

    /** Fornecedor em criação ou edição. */
    private Fornecedor fornecedorSelecionado;

    // =========================================================
    // Inicialização
    // =========================================================

    @PostConstruct
    public void init() {
        carregarFornecedores();
    }

    public void carregarFornecedores() {
        fornecedores = fornecedorService.listarAtivos();
    }

    // =========================================================
    // CRUD
    // =========================================================

    /** Prepara formulário limpo para criar novo fornecedor. */
    public void prepararCriar() {
        fornecedorSelecionado = new Fornecedor();
    }

    /** Salva novo fornecedor e atualiza a tabela. */
    public void salvar() {
        try {
            fornecedorService.salvar(fornecedorSelecionado);
            carregarFornecedores();
            adicionarMensagemSucesso("Fornecedor \"" + fornecedorSelecionado.getNome() + "\" cadastrado.");
            fornecedorSelecionado = new Fornecedor();
        } catch (IllegalArgumentException e) {
            adicionarMensagemErro(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[FornecedorBean] Erro ao salvar", e);
            adicionarMensagemErro("Erro interno ao salvar. Tente novamente.");
        }
    }

    /** Carrega fornecedor do banco e abre o diálogo de edição. */
    public void prepararEditar(Fornecedor f) {
        fornecedorSelecionado = fornecedorService.buscarPorId(f.getId());
    }

    /** Salva as alterações do fornecedor em edição. */
    public void editar() {
        try {
            fornecedorService.editar(fornecedorSelecionado);
            carregarFornecedores();
            adicionarMensagemSucesso("Fornecedor atualizado com sucesso.");
        } catch (IllegalArgumentException e) {
            adicionarMensagemErro(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[FornecedorBean] Erro ao editar", e);
            adicionarMensagemErro("Erro interno ao editar. Tente novamente.");
        }
    }

    /** Exclusão lógica do fornecedor (soft delete). */
    public void excluir(Fornecedor f) {
        try {
            fornecedorService.excluir(f.getId());
            carregarFornecedores();
            adicionarMensagemSucesso("Fornecedor \"" + f.getNome() + "\" removido.");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[FornecedorBean] Erro ao excluir", e);
            adicionarMensagemErro("Erro ao remover fornecedor. Tente novamente.");
        }
    }

    // =========================================================
    // Helpers para a view
    // =========================================================

    /**
     * Retorna o CNPJ formatado para exibição na tabela.
     * Ex: "12345678000190" → "12.345.678/0001-90"
     *
     * @param f fornecedor
     * @return CNPJ formatado ou "—" se null
     */
    public String cnpjFormatado(Fornecedor f) {
        return FornecedorService.formatarCnpj(f.getCnpj());
    }

    // =========================================================
    // Mensagens JSF
    // =========================================================

    private void adicionarMensagemSucesso(String texto) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Sucesso", texto));
    }

    private void adicionarMensagemErro(String texto) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erro", texto));
    }

    // =========================================================
    // Getters e Setters
    // =========================================================

    public List<Fornecedor> getFornecedores()            { return fornecedores; }

    public Fornecedor getFornecedorSelecionado()         { return fornecedorSelecionado; }
    public void setFornecedorSelecionado(Fornecedor f)   { this.fornecedorSelecionado = f; }
}
