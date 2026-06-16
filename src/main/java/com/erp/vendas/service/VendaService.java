package com.erp.vendas.service;

import com.erp.catalogo.model.Produto;
import com.erp.identidade.model.Usuario;
import com.erp.vendas.model.FormaPagamento;
import com.erp.vendas.model.ItemVenda;
import com.erp.vendas.model.StatusVenda;
import com.erp.vendas.model.Venda;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Session Bean responsável pelo processamento de vendas.
 *
 * <h2>Fluxo do Checkout</h2>
 * <ol>
 *   <li>Recebe a lista de itens do carrinho (do Managed Bean {@code PdvBean})</li>
 *   <li>Bloqueia pessimisticamente cada {@link Produto} com {@code SELECT FOR UPDATE}</li>
 *   <li>Valida que o estoque é suficiente para todos os itens</li>
 *   <li>Decrementa o estoque de cada produto</li>
 *   <li>Simula a aprovação do pagamento conforme {@link FormaPagamento}</li>
 *   <li>Persiste a {@link Venda} com status {@code FECHADA}</li>
 * </ol>
 *
 * <h2>Controle de Concorrência — Pessimistic Locking</h2>
 * <p>O maior risco em um PDV com múltiplas abas/usuários simultâneos é a
 * "venda dupla": dois operadores finalizam vendas do mesmo produto ao mesmo
 * tempo, ambos lêem estoque = 1, ambos aprovam e o estoque fica negativo.</p>
 *
 * <p>A solução é {@link LockModeType#PESSIMISTIC_WRITE}, que emite
 * {@code SELECT ... FOR UPDATE} no PostgreSQL. Isso:</p>
 * <ul>
 *   <li>Bloqueia a linha do produto na tabela enquanto a transação estiver aberta</li>
 *   <li>Força outros threads a aguardarem o lock ser liberado</li>
 *   <li>Garante que o estoque lido é sempre o valor real no banco</li>
 * </ul>
 *
 * <p>O lock é liberado automaticamente ao final da transação (commit ou rollback).
 * O timeout padrão do PostgreSQL para lock evita deadlock eterno.</p>
 *
 * <h2>Rollback automático</h2>
 * <p>O método lança exceções não-verificadas ({@link EstoqueInsuficienteException},
 * {@link PagamentoRecusadoException}) que causam rollback automático no container
 * EJB. Nenhum {@code rollback} manual é necessário.</p>
 *
 * @see PdvBean
 * @see Venda
 * @see ItemVenda
 */
@Stateless
public class VendaService {

    private static final Logger LOG = Logger.getLogger(VendaService.class.getName());

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    // =========================================================
    // Checkout — operação principal (tudo ou nada)
    // =========================================================

    /**
     * Processa o checkout do carrinho: valida estoque, debita, aprova pagamento
     * e registra a venda — tudo em uma única transação.
     *
     * <h3>Atomicidade:</h3>
     * <p>Se qualquer passo falhar (estoque insuficiente, pagamento recusado,
     * erro de BD), o container fará rollback de TODA a transação: o estoque
     * NÃO é debitado e a venda NÃO é persistida.</p>
     *
     * @param itens          lista de itens do carrinho (produto + quantidade + preço)
     * @param formaPagamento forma de pagamento escolhida pelo cliente
     * @param usuario        operador que está realizando a venda
     * @return {@link Venda} persistida com id gerado
     * @throws EstoqueInsuficienteException se algum produto não tiver saldo — rollback total
     * @throws PagamentoRecusadoException   se a aprovação do pagamento falhar — rollback total
     * @throws IllegalArgumentException     se o carrinho estiver vazio
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Venda checkout(List<ItemVenda> itens,
                          FormaPagamento  formaPagamento,
                          Usuario         usuario) {

        // Guarda de entrada — nunca processar carrinho vazio
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Carrinho vazio. Adicione produtos antes de finalizar.");
        }

        LOG.info(String.format("[VendaService] Iniciando checkout | %d item(s) | %s | operador: %s",
            itens.size(), formaPagamento, usuario.getEmail()));

        // ---------------------------------------------------------------
        // FASE 1: Bloqueia e valida estoque com PESSIMISTIC_WRITE
        //
        // Por que bloquear ANTES de validar e não durante o decremento?
        //   • Garante que nenhum outro thread altere o estoque entre a
        //     leitura e a escrita (janela de race condition).
        //   • O lock é mantido até o commit — cobrindo toda a transação.
        //   • Ordem de lock sempre pelo id do produto (crescente) para
        //     prevenir deadlock entre transações concorrentes.
        // ---------------------------------------------------------------
        for (ItemVenda item : itens.stream()
                .sorted(java.util.Comparator.comparing(i -> i.getProduto().getId()))
                .toList()) {

            // Re-lê o produto do banco COM LOCK — ignora o cache de 1ª nível
            Produto produtoLockado = em.find(
                Produto.class,
                item.getProduto().getId(),
                LockModeType.PESSIMISTIC_WRITE  // → SELECT ... FOR UPDATE no PostgreSQL
            );

            if (produtoLockado == null || !produtoLockado.isAtivo()) {
                throw new EstoqueInsuficienteException(
                    "Produto '" + item.getProduto().getNome() + "' não está mais disponível.");
            }

            // Serviços não têm estoque físico — pular validação
            if (produtoLockado.isServico()) {
                item.setProduto(produtoLockado); // Substitui referência detached por gerenciada
                continue;
            }

            Integer estoqueAtual = produtoLockado.getQuantidadeEstoque();
            if (estoqueAtual == null || estoqueAtual < item.getQuantidade()) {
                throw new EstoqueInsuficienteException(String.format(
                    "Estoque insuficiente para '%s'. Disponível: %d | Solicitado: %d",
                    produtoLockado.getNome(),
                    estoqueAtual != null ? estoqueAtual : 0,
                    item.getQuantidade()
                ));
            }

            // Substitui a entidade detached (vinda do carrinho JSF) pela
            // entidade gerenciada recém-lida com lock — crítico para o merge funcionar
            item.setProduto(produtoLockado);
        }

        // ---------------------------------------------------------------
        // FASE 2: Debita estoque (ocorre APÓS validação de todos os itens)
        //
        // Separar validação de débito garante que, se qualquer produto
        // tiver estoque insuficiente, NENHUM produto terá seu estoque
        // decrementado — comportamento "tudo ou nada" dentro da transação.
        // ---------------------------------------------------------------
        BigDecimal total = BigDecimal.ZERO;
        for (ItemVenda item : itens) {
            Produto p = item.getProduto(); // Já é a entidade gerenciada com lock

            if (!p.isServico()) {
                int novoEstoque = p.getQuantidadeEstoque() - item.getQuantidade();
                p.setQuantidadeEstoque(novoEstoque);
                // Sem em.merge() — p já é entidade GERENCIADA; dirty checking fará o UPDATE
                LOG.fine(String.format("[VendaService] Estoque debitado: %s | %d → %d",
                    p.getNome(), p.getQuantidadeEstoque() + item.getQuantidade(), novoEstoque));
            }

            // Snapshot do preço atual (snapshot financeiro imutável)
            item.setPrecoUnitario(p.getPreco());
            total = total.add(p.getPreco().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }

        // ---------------------------------------------------------------
        // FASE 3: Simula aprovação de pagamento
        //
        // Executado APÓS o débito de estoque para que um pagamento recusado
        // cause rollback e devolva o estoque automaticamente.
        // Em produção, substituir pela chamada ao gateway real.
        // ---------------------------------------------------------------
        aprovarPagamento(formaPagamento, total);

        // ---------------------------------------------------------------
        // FASE 4: Persiste a Venda com status FECHADA
        // ---------------------------------------------------------------
        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setValorTotal(total);
        venda.setFormaPagamento(formaPagamento);
        venda.setStatus(StatusVenda.FECHADA);
        venda.setUsuario(usuario);

        for (ItemVenda item : itens) {
            venda.adicionarItem(item); // Seta item.venda = venda (consistência bidirecional)
        }

        em.persist(venda); // CascadeType.ALL propaga persist para os itens

        LOG.info(String.format(
            "[VendaService] Checkout concluído | VendaId: %d | Total: R$ %.2f | Forma: %s",
            venda.getId(), total, formaPagamento));

        return venda;
    }

    // =========================================================
    // Consultas de histórico
    // =========================================================

    /**
     * Retorna as últimas vendas fechadas, com itens e produtos carregados.
     * Usada na tela de histórico do PDV.
     *
     * @param limite máximo de registros a retornar
     * @return lista de vendas ordenadas da mais recente para a mais antiga
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Venda> listarRecentes(int limite) {
        return em.createQuery(
                "SELECT DISTINCT v FROM Venda v " +
                "LEFT JOIN FETCH v.itens i " +
                "LEFT JOIN FETCH i.produto " +
                "LEFT JOIN FETCH v.usuario " +
                "WHERE v.status = :status " +
                "ORDER BY v.dataVenda DESC",
                Venda.class)
            .setParameter("status", StatusVenda.FECHADA)
            .setMaxResults(limite)
            .getResultList();
    }

    // =========================================================
    // Simulação de gateway de pagamento
    // =========================================================

    /**
     * Simula a aprovação do pagamento conforme a forma escolhida.
     *
     * <h3>Comportamento simulado por forma:</h3>
     * <ul>
     *   <li>{@code PIX}            — aprovação instantânea (sempre OK)</li>
     *   <li>{@code CARTAO_CREDITO} — 98% de aprovação (2% recusado — simulação)</li>
     *   <li>{@code BOLETO}         — gera o boleto; venda confirmada (boleto não pago
     *                               é tratado em processo assíncrono — fora do escopo)</li>
     * </ul>
     *
     * <p>Em produção, este método deve chamar a API do gateway de pagamento
     * (ex: Stripe, PagSeguro, Mercado Pago) e tratar a resposta assíncrona.</p>
     *
     * @param forma  forma de pagamento
     * @param total  valor total da transação
     * @throws PagamentoRecusadoException se o pagamento for recusado — causa rollback
     */
    private void aprovarPagamento(FormaPagamento forma, BigDecimal total) {
        LOG.info(String.format("[VendaService] Processando pagamento | Forma: %s | Valor: R$ %.2f",
            forma, total));

        switch (forma) {
            case PIX -> {
                // PIX: aprovação instantânea — 100% de sucesso na simulação
                LOG.info("[VendaService] PIX aprovado instantaneamente.");
            }
            case CARTAO_CREDITO -> {
                // Simula 2% de recusa (ex: limite insuficiente, cartão expirado)
                if (Math.random() < 0.02) {
                    throw new PagamentoRecusadoException(
                        "Cartão recusado pela operadora. Tente outro método de pagamento.");
                }
                LOG.info("[VendaService] Cartão de crédito aprovado.");
            }
            case BOLETO -> {
                // Boleto: confirmação de geração — pagamento em até 3 dias úteis
                // A conciliação bancária é processo assíncrono fora do escopo deste checkout
                LOG.info("[VendaService] Boleto gerado. Pagamento confirmado na geração.");
            }
        }
    }

    // =========================================================
    // Exceções de domínio (unchecked → rollback automático EJB)
    // =========================================================

    /**
     * Exceção lançada quando um produto não possui estoque suficiente.
     *
     * <p>Por ser não-verificada ({@code RuntimeException}), o container EJB
     * faz rollback automático da transação sem configuração extra.</p>
     */
    public static class EstoqueInsuficienteException extends RuntimeException {
        public EstoqueInsuficienteException(String msg) { super(msg); }
    }

    /**
     * Exceção lançada quando o gateway de pagamento recusa a transação.
     *
     * <p>O rollback causado por esta exceção devolve o estoque debitado
     * na Fase 2, pois a transação inteira é revertida.</p>
     */
    public static class PagamentoRecusadoException extends RuntimeException {
        public PagamentoRecusadoException(String msg) { super(msg); }
    }
}
