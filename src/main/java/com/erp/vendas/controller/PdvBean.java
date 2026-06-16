package com.erp.vendas.controller;

import com.erp.catalogo.model.Produto;
import com.erp.catalogo.service.ProdutoService;
import com.erp.identidade.model.Usuario;
import com.erp.vendas.model.FormaPagamento;
import com.erp.vendas.model.ItemVenda;
import com.erp.vendas.model.Venda;
import com.erp.vendas.service.VendaService;
import com.erp.vendas.service.VendaService.EstoqueInsuficienteException;
import com.erp.vendas.service.VendaService.PagamentoRecusadoException;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean do Ponto de Venda (PDV).
 *
 * <h3>Escopo: {@code @ViewScoped}</h3>
 * <p>O carrinho vive enquanto a página {@code pdv.xhtml} estiver aberta
 * na mesma aba. Uma nova aba ou navegação para outra página cria um
 * bean novo (carrinho vazio). Isso evita contaminação de carrinhos entre
 * operações distintas e é o padrão mais seguro para um PDV.</p>
 *
 * <h3>Responsabilidades:</h3>
 * <ul>
 *   <li>Manter a lista de itens do carrinho em memória</li>
 *   <li>Calcular totais dinamicamente via EL no XHTML</li>
 *   <li>Pesquisar produtos por nome/código para o campo de busca</li>
 *   <li>Adicionar, alterar quantidade e remover itens do carrinho</li>
 *   <li>Delegar o checkout ao {@link VendaService}</li>
 *   <li>Resolver o usuário autenticado via {@code SecurityContext}</li>
 * </ul>
 *
 * @see VendaService
 * @see pdv.xhtml
 */
@Named("pdvBean")
@ViewScoped
public class PdvBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(PdvBean.class.getName());

    // =========================================================
    // Dependências
    // =========================================================

    @EJB
    private VendaService vendaService;

    @EJB
    private ProdutoService produtoService;

    // =========================================================
    // Estado do carrinho
    // =========================================================

    /**
     * Itens atualmente no carrinho.
     * Cada ItemVenda guarda: produto, quantidade, precoUnitario (snapshot).
     */
    private final List<ItemVenda> itensCarrinho = new ArrayList<>();

    /** Produto selecionado no autocomplete para adicionar ao carrinho. */
    private Produto produtoSelecionado;

    /** Quantidade a adicionar para o produto selecionado. */
    private int quantidadeAdicionar = 1;

    /** Forma de pagamento escolhida (bind com selectOneMenu). */
    private FormaPagamento formaPagamento = FormaPagamento.PIX;

    /** Última venda finalizada — exibida no recibo após checkout. */
    private Venda ultimaVenda;

    /** Controla exibição do dialog de recibo pós-checkout. */
    private boolean dialogReciboVisivel = false;

    // =========================================================
    // Inicialização
    // =========================================================

    @PostConstruct
    public void init() {
        // Bean inicializado — carrinho começa vazio
        LOG.fine("[PdvBean] PDV inicializado para nova venda.");
    }

    // =========================================================
    // Busca de produto (autoComplete)
    // =========================================================

    /**
     * Método de autocomplete — chamado pelo {@code <p:autoComplete>}
     * ao digitar no campo de busca de produto.
     *
     * <p>Filtra por nome, case-insensitive, entre os produtos ativos.
     * O PrimeFaces chama este método passando o texto digitado.</p>
     *
     * @param query texto digitado pelo operador
     * @return lista de produtos cujo nome contém o texto
     */
    public List<Produto> buscarProdutos(String query) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.trim().toLowerCase();
        return produtoService.listarAtivos()
            .stream()
            .filter(p -> p.getNome().toLowerCase().contains(q))
            .toList();
    }

    // =========================================================
    // Operações do carrinho
    // =========================================================

    /**
     * Adiciona o produto selecionado ao carrinho.
     *
     * <h3>Regras:</h3>
     * <ul>
     *   <li>Se o produto já existe no carrinho, soma as quantidades</li>
     *   <li>Valida que a quantidade pedida não excede o estoque disponível</li>
     *   <li>O preço unitário é capturado agora (snapshot) — não muda mesmo
     *       se o preço do produto for alterado durante a sessão</li>
     * </ul>
     */
    public void adicionarAoCarrinho() {
        if (produtoSelecionado == null) {
            adicionarMensagemErro("Selecione um produto antes de adicionar.");
            return;
        }
        if (quantidadeAdicionar <= 0) {
            adicionarMensagemErro("Quantidade deve ser maior que zero.");
            return;
        }

        // Valida estoque disponível (apenas para produtos físicos)
        if (!produtoSelecionado.isServico()) {
            int disponivelEstoque = produtoSelecionado.getQuantidadeEstoque() != null
                ? produtoSelecionado.getQuantidadeEstoque() : 0;
            int jaNoCarrinho = quantidadeNoCarrinho(produtoSelecionado.getId());
            int totalPedido  = jaNoCarrinho + quantidadeAdicionar;

            if (totalPedido > disponivelEstoque) {
                adicionarMensagemErro(String.format(
                    "Estoque insuficiente para '%s'. Disponível: %d | Já no carrinho: %d | Pedindo: %d",
                    produtoSelecionado.getNome(), disponivelEstoque, jaNoCarrinho, quantidadeAdicionar
                ));
                return;
            }
        }

        // Verifica se o produto já está no carrinho
        ItemVenda itemExistente = itensCarrinho.stream()
            .filter(i -> i.getProduto().getId().equals(produtoSelecionado.getId()))
            .findFirst()
            .orElse(null);

        if (itemExistente != null) {
            // Incrementa a quantidade do item existente
            itemExistente.setQuantidade(itemExistente.getQuantidade() + quantidadeAdicionar);
        } else {
            // Cria novo item com snapshot de preço
            ItemVenda novoItem = new ItemVenda(
                produtoSelecionado,
                quantidadeAdicionar,
                produtoSelecionado.getPreco() // Snapshot do preço atual
            );
            itensCarrinho.add(novoItem);
        }

        LOG.fine(String.format("[PdvBean] Adicionado: %s x%d",
            produtoSelecionado.getNome(), quantidadeAdicionar));

        // Limpa o campo de busca e quantidade para o próximo item
        produtoSelecionado  = null;
        quantidadeAdicionar = 1;
    }

    /**
     * Remove um item completamente do carrinho.
     *
     * @param item item a remover
     */
    public void removerItem(ItemVenda item) {
        itensCarrinho.remove(item);
        LOG.fine("[PdvBean] Item removido: " + item.getProduto().getNome());
    }

    /**
     * Limpa o carrinho inteiro (botão "Cancelar venda").
     */
    public void limparCarrinho() {
        itensCarrinho.clear();
        produtoSelecionado  = null;
        quantidadeAdicionar = 1;
        formaPagamento      = FormaPagamento.PIX;
        ultimaVenda         = null;
        dialogReciboVisivel = false;
    }

    // =========================================================
    // Checkout
    // =========================================================

    /**
     * Finaliza a venda: chama o {@link VendaService#checkout} e exibe o recibo.
     *
     * <h3>Tratamento de erros:</h3>
     * <ul>
     *   <li>{@link EstoqueInsuficienteException} — mensagem clara ao operador</li>
     *   <li>{@link PagamentoRecusadoException}   — orienta tentar outra forma</li>
     *   <li>Qualquer outra exceção              — mensagem genérica + log de erro</li>
     * </ul>
     */
    public void finalizarVenda() {
        if (itensCarrinho.isEmpty()) {
            adicionarMensagemErro("Carrinho vazio. Adicione pelo menos um produto.");
            return;
        }

        try {
            Usuario operador = resolverUsuarioAutenticado();

            ultimaVenda = vendaService.checkout(
                new ArrayList<>(itensCarrinho), // Cópia defensiva
                formaPagamento,
                operador
            );

            // Sucesso — limpa o carrinho e exibe o recibo
            itensCarrinho.clear();
            dialogReciboVisivel = true;
            adicionarMensagemSucesso(String.format(
                "Venda #%d finalizada. Pagamento via %s aprovado. Total: R$ %.2f",
                ultimaVenda.getId(), formaPagamento, ultimaVenda.getValorTotal()
            ));

        } catch (EstoqueInsuficienteException e) {
            adicionarMensagemErro("⚠ " + e.getMessage());
        } catch (PagamentoRecusadoException e) {
            adicionarMensagemErro("✗ Pagamento recusado: " + e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[PdvBean] Erro inesperado no checkout", e);
            adicionarMensagemErro("Erro interno ao processar a venda. Contate o suporte.");
        }
    }

    // =========================================================
    // Totalizadores (chamados via EL na view)
    // =========================================================

    /**
     * Calcula o total do carrinho somando todos os subtotais.
     *
     * @return valor total do carrinho (BigDecimal para precisão monetária)
     */
    public BigDecimal getTotalCarrinho() {
        return itensCarrinho.stream()
            .map(ItemVenda::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Retorna a quantidade de itens distintos no carrinho (para o badge).
     *
     * @return número de linhas no carrinho
     */
    public int getQuantidadeItens() {
        return itensCarrinho.size();
    }

    /**
     * Indica se o carrinho está vazio (para controlar rendered/disabled no XHTML).
     *
     * @return {@code true} se não há itens
     */
    public boolean isCarrinhoVazio() {
        return itensCarrinho.isEmpty();
    }

    /**
     * Retorna os valores de FormaPagamento para o selectOneMenu.
     *
     * @return array dos valores do enum
     */
    public FormaPagamento[] getFormasPagamento() {
        return FormaPagamento.values();
    }

    /**
     * Label amigável para exibição de FormaPagamento.
     *
     * @param forma forma de pagamento
     * @return label legível
     */
    public String labelFormaPagamento(FormaPagamento forma) {
        return switch (forma) {
            case PIX            -> "PIX (instantâneo)";
            case CARTAO_CREDITO -> "Cartão de Crédito";
            case BOLETO         -> "Boleto Bancário";
        };
    }

    // =========================================================
    // Auxiliares privados
    // =========================================================

    /**
     * Resolve o usuário autenticado no Spring Security a partir do
     * {@code SecurityContextHolder}, e busca a entidade JPA pelo email.
     *
     * @return entidade {@link Usuario} gerenciada
     * @throws IllegalStateException se não houver autenticação ativa
     */
    private Usuario resolverUsuarioAutenticado() {
        // Obtém o email do principal autenticado pelo Spring Security
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Operador não autenticado.");
        }

        String email = auth.getName();

        // Busca a entidade JPA pelo email para usar como FK na Venda
        List<Usuario> resultado = FacesContext.getCurrentInstance()
            .getApplication()
            .evaluateExpressionGet(
                FacesContext.getCurrentInstance(),
                "#{usuarioBean}", // não usa usuarioBean — usa query direta
                Object.class
            ) == null
            ? List.of()
            : List.of();

        // Query JPQL direta — sem depender de outro bean
        javax.persistence.EntityManagerFactory emf =
            (javax.persistence.EntityManagerFactory) FacesContext.getCurrentInstance()
                .getExternalContext().getApplicationMap().get("entityManagerFactory");

        // Simplificado: injeta EM diretamente via campo anotado (disponível em EJB)
        // Como PdvBean é CDI, usa um lookup simples via Spring Security + JPQL no serviço
        // Para manter o padrão sem acoplamento extra, o EJB recebe apenas o email
        // e o VendaService deve resolver internamente — mas para simplicidade, buscamos aqui
        // via a query padrão do contexto de persistência compartilhado.

        // NOTA: PdvBean é CDI gerenciado pelo WildFly, portanto @PersistenceContext funciona
        return buscarUsuarioPorEmail(email);
    }

    /**
     * Quantidade total de um produto já adicionada ao carrinho.
     * Usado para validar se somar mais unidades ainda está no estoque.
     *
     * @param produtoId id do produto
     * @return quantidade total no carrinho (0 se não estiver)
     */
    private int quantidadeNoCarrinho(Long produtoId) {
        return itensCarrinho.stream()
            .filter(i -> i.getProduto().getId().equals(produtoId))
            .mapToInt(ItemVenda::getQuantidade)
            .sum();
    }

    /**
     * Busca a entidade {@link Usuario} pelo email usando EntityManager injetado no bean.
     * Necessário para associar o operador à venda.
     */
    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    private Usuario buscarUsuarioPorEmail(String email) {
        try {
            return em.createQuery(
                    "SELECT u FROM Usuario u WHERE u.email = :email",
                    Usuario.class)
                .setParameter("email", email)
                .getSingleResult();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Operador '" + email + "' não encontrado na base de dados.");
        }
    }

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

    public List<ItemVenda>   getItensCarrinho()                  { return itensCarrinho; }

    public Produto           getProdutoSelecionado()             { return produtoSelecionado; }
    public void              setProdutoSelecionado(Produto p)    { this.produtoSelecionado = p; }

    public int               getQuantidadeAdicionar()            { return quantidadeAdicionar; }
    public void              setQuantidadeAdicionar(int q)       { this.quantidadeAdicionar = q; }

    public FormaPagamento    getFormaPagamento()                  { return formaPagamento; }
    public void              setFormaPagamento(FormaPagamento f)  { this.formaPagamento = f; }

    public Venda             getUltimaVenda()                    { return ultimaVenda; }

    public boolean           isDialogReciboVisivel()             { return dialogReciboVisivel; }
    public void              setDialogReciboVisivel(boolean v)   { this.dialogReciboVisivel = v; }
}
