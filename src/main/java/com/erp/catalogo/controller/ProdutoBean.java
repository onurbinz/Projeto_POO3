package com.erp.catalogo.controller;

import com.erp.catalogo.model.Categoria;
import com.erp.catalogo.model.Produto;
import com.erp.catalogo.service.ProdutoService;
import com.erp.compras.model.Fornecedor;

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
 * Managed Bean (CDI) do módulo de Produtos/Estoque.
 *
 * <h3>Responsabilidades:</h3>
 * <ul>
 *   <li>Carregar produtos ativos e produtos com estoque baixo no {@link #init()}</li>
 *   <li>Controlar abertura/fechamento dos diálogos de criar e editar</li>
 *   <li>Delegar persistência ao {@link ProdutoService} (EJB transacional)</li>
 *   <li>Formatar mensagens de feedback via {@code FacesMessage}</li>
 * </ul>
 *
 * <h3>Alerta de estoque baixo:</h3>
 * <p>O bean expõe {@link #getProdutosEstoqueBaixo()} e {@link #isHaEstoqueBaixo()}.
 * A view usa esses métodos para renderizar condicionalmente o painel de alerta
 * no topo da página — sem lógica de negócio no XHTML.</p>
 *
 * <h3>Converter de entidades nos selects:</h3>
 * <p>As listas de categorias e fornecedores são carregadas uma única vez no
 * {@code @PostConstruct} e reusadas pelos {@code <p:selectOneMenu>} do formulário.
 * O {@link CategoriaConverter} e {@link FornecedorConverter} fazem a conversão
 * String ↔ entidade exigida pelo JSF para selects de objetos.</p>
 *
 * @see ProdutoService
 * @see CategoriaConverter
 * @see FornecedorConverter
 */
@Named("produtoBean")
@ViewScoped
public class ProdutoBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ProdutoBean.class.getName());

    // =========================================================
    // Dependências
    // =========================================================

    @EJB
    private ProdutoService produtoService;

    // =========================================================
    // Estado da view
    // =========================================================

    /** Lista de produtos ativos exibida na tabela principal. */
    private List<Produto> produtos;

    /**
     * Lista de produtos com estoque abaixo do mínimo.
     * Usada pelo painel de alerta no topo da página.
     */
    private List<Produto> produtosEstoqueBaixo;

    /** Produto em criação ou edição — compartilhado pelos dois diálogos. */
    private Produto produtoSelecionado;

    /** Lista de categorias para o select do formulário. */
    private List<Categoria> categorias;

    /** Lista de fornecedores para o select do formulário. */
    private List<Fornecedor> fornecedores;

    // =========================================================
    // Inicialização
    // =========================================================

    /**
     * Executado pelo CDI após a construção do bean.
     * Carrega todas as listas necessárias para a view de uma só vez.
     */
    @PostConstruct
    public void init() {
        carregarTudo();
    }

    /**
     * Recarrega produtos, alertas de estoque, categorias e fornecedores.
     * Chamado após cada operação de CRUD para manter a view sincronizada.
     */
    public void carregarTudo() {
        produtos              = produtoService.listarAtivos();
        produtosEstoqueBaixo  = produtoService.listarComEstoqueBaixo();
        categorias            = produtoService.listarCategorias();
        fornecedores          = produtoService.listarFornecedores();
    }

    // =========================================================
    // Diálogo CRIAR
    // =========================================================

    /**
     * Prepara o formulário para um novo produto.
     * Cria uma entidade vazia e abre o diálogo de criação.
     */
    public void prepararCriar() {
        produtoSelecionado = new Produto();
    }

    /**
     * Salva o novo produto e atualiza a view.
     */
    public void salvar() {
        try {
            produtoService.salvar(produtoSelecionado);
            carregarTudo();
            adicionarMensagemSucesso(
                "Produto \"" + produtoSelecionado.getNome() + "\" cadastrado com sucesso.");
            produtoSelecionado = new Produto(); // Limpa o formulário

        } catch (IllegalArgumentException e) {
            adicionarMensagemErro(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[ProdutoBean] Erro ao salvar produto", e);
            adicionarMensagemErro("Erro interno ao salvar. Tente novamente.");
        }
    }

    // =========================================================
    // Diálogo EDITAR
    // =========================================================

    /**
     * Carrega os dados do produto clicado e abre o diálogo de edição.
     *
     * <p>Usa {@code buscarPorId()} para garantir que a entidade está
     * gerenciada e com os relacionamentos inicializados (evita
     * {@code LazyInitializationException} fora do contexto JPA).</p>
     *
     * @param produto produto selecionado na tabela
     */
    public void prepararEditar(Produto produto) {
        produtoSelecionado = produtoService.buscarPorId(produto.getId());
    }

    /**
     * Salva as alterações do produto em edição e atualiza a view.
     */
    public void editar() {
        try {
            produtoService.editar(produtoSelecionado);
            carregarTudo();
            adicionarMensagemSucesso(
                "Produto \"" + produtoSelecionado.getNome() + "\" atualizado com sucesso.");

        } catch (IllegalArgumentException e) {
            adicionarMensagemErro(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[ProdutoBean] Erro ao editar produto", e);
            adicionarMensagemErro("Erro interno ao editar. Tente novamente.");
        }
    }

    // =========================================================
    // Exclusão lógica
    // =========================================================

    /**
     * Realiza a exclusão lógica (soft delete) do produto.
     * O produto some das listagens mas permanece no banco.
     *
     * @param produto produto a excluir
     */
    public void excluir(Produto produto) {
        try {
            produtoService.excluir(produto.getId());
            carregarTudo();
            adicionarMensagemSucesso(
                "Produto \"" + produto.getNome() + "\" removido do catálogo.");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[ProdutoBean] Erro ao excluir produto", e);
            adicionarMensagemErro("Erro ao remover produto. Tente novamente.");
        }
    }

    // =========================================================
    // Helpers para a view
    // =========================================================

    /**
     * Indica se existem produtos com estoque baixo.
     * Usado para renderizar condicionalmente o painel de alerta.
     *
     * @return {@code true} se houver pelo menos um produto abaixo do mínimo
     */
    public boolean isHaEstoqueBaixo() {
        return produtosEstoqueBaixo != null && !produtosEstoqueBaixo.isEmpty();
    }

    /**
     * Retorna o nome da categoria do produto, tratando null graciosamente.
     *
     * @param produto produto a consultar
     * @return nome da categoria ou "—" se não houver categoria
     */
    public String nomeCategoria(Produto produto) {
        if (produto.getCategoria() == null) return "—";
        return produto.getCategoria().getNome();
    }

    /**
     * Retorna o nome do fornecedor do produto, tratando null (serviços sem fornecedor).
     *
     * @param produto produto a consultar
     * @return nome do fornecedor ou "Interno" se serviço sem fornecedor
     */
    public String nomeFornecedor(Produto produto) {
        if (produto.getFornecedor() == null) return "Interno";
        return produto.getFornecedor().getNome();
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

    public List<Produto> getProdutos()                     { return produtos; }
    public List<Produto> getProdutosEstoqueBaixo()         { return produtosEstoqueBaixo; }
    public List<Categoria> getCategorias()                 { return categorias; }
    public List<Fornecedor> getFornecedores()              { return fornecedores; }

    public Produto getProdutoSelecionado()                 { return produtoSelecionado; }
    public void setProdutoSelecionado(Produto p)           { this.produtoSelecionado = p; }
}
