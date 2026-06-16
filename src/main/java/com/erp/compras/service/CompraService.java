package com.erp.compras.service;

import com.erp.catalogo.model.Produto;
import com.erp.compras.model.Fornecedor;
import com.erp.compras.model.SugestaoCompra;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Session Bean responsável pela análise de necessidade de compras.
 *
 * <h3>Funcionalidade principal — Sugestões de Compra:</h3>
 * <p>O método {@link #gerarSugestoes()} identifica automaticamente quais produtos
 * precisam de reposição com base em dois critérios combinados:</p>
 * <ol>
 *   <li><b>Estoque atual ≤ Estoque mínimo</b> — produto já está em nível crítico</li>
 *   <li><b>Média de consumo dos últimos 30 dias</b> — calcula quantas unidades foram
 *       vendidas por dia no período e projeta a necessidade de reposição</li>
 * </ol>
 *
 * <h3>JPQL otimizada para média de 30 dias:</h3>
 * <p>A query agrega {@code SUM(iv.quantidade)} sobre {@code ItemVenda} filtrado
 * por período, groupando por produto. Isso evita carregar todos os itens de venda
 * em memória — o cálculo ocorre inteiramente no banco de dados (PostgreSQL).</p>
 *
 * <h3>Cálculo da quantidade sugerida:</h3>
 * <pre>
 *   mediadiaria    = totalVendido30Dias / 30
 *   qtdReposicao   = ceil(mediadiaria * 30) - estoqueAtual
 *   qtdSugerida    = max(qtdReposicao, estoqueMinimo * 2)
 * </pre>
 * <p>A lógica garante que o pedido cubra ao menos 30 dias de consumo projetado,
 * respeitando o piso de 2× o estoque mínimo como quantidade mínima de pedido.</p>
 *
 * @see SugestaoCompra
 * @see Produto
 */
@Stateless
public class CompraService {

    private static final Logger LOG = Logger.getLogger(CompraService.class.getName());

    /** Janela de análise em dias para o cálculo de consumo médio. */
    private static final int JANELA_DIAS = 30;

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    // =========================================================
    // Query principal — Sugestões de compra
    // =========================================================

    /**
     * Gera a lista de sugestões de compra para todos os produtos ativos
     * que estejam com estoque igual ou abaixo do mínimo configurado.
     *
     * <h3>JPQL — Média de consumo de 30 dias (query otimizada):</h3>
     * <p>A sub-consulta agrega o total vendido por produto nos últimos
     * {@value #JANELA_DIAS} dias diretamente no banco, sem trazer registros
     * individuais para a memória Java. O resultado é um {@code Object[]}
     * com {@code [produtoId, totalVendido]}.</p>
     *
     * @return lista de sugestões, uma por produto crítico (nunca null)
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<SugestaoCompra> gerarSugestoes() {

        LocalDateTime limite = LocalDateTime.now().minusDays(JANELA_DIAS);

        // -----------------------------------------------------------------
        // JPQL 1: Produtos com estoque <= mínimo (critério primário)
        // LEFT JOIN FETCH para carregar fornecedor e categoria em 1 query.
        // Apenas produtos físicos (quantidadeEstoque != null).
        // -----------------------------------------------------------------
        List<Produto> criticos = em.createQuery(
                "SELECT DISTINCT p FROM Produto p " +
                "LEFT JOIN FETCH p.categoria " +
                "LEFT JOIN FETCH p.fornecedor " +
                "WHERE p.ativo = true " +
                "AND p.quantidadeEstoque IS NOT NULL " +
                "AND p.quantidadeMinima IS NOT NULL " +
                "AND p.quantidadeEstoque <= p.quantidadeMinima " +
                "ORDER BY p.quantidadeEstoque ASC",
                Produto.class)
            .getResultList();

        if (criticos.isEmpty()) {
            return List.of(); // Nenhum produto crítico — lista vazia
        }

        // -----------------------------------------------------------------
        // JPQL 2: Consumo agregado dos últimos 30 dias por produto (otimizada).
        //
        // A query soma as quantidades vendidas (iv.quantidade) agrupando por
        // produto, filtrando apenas itens de vendas FECHADAS no período.
        // Retorna Object[2] = { produtoId (Long), totalVendido (Long) }.
        //
        // Por que essa query é otimizada?
        //   • Executa inteiramente no PostgreSQL (sem carregar entidades Java)
        //   • GROUP BY reduz N linhas de itens_venda a 1 linha por produto
        //   • Filtra pela FK produto_id com índice (muito mais rápido que
        //     carregar todos os ItemVenda e filtrar em memória)
        //   • COALESCE não é necessário aqui — produtos sem vendas simplesmente
        //     não aparecem na lista, tratado no Map com getOrDefault()
        // -----------------------------------------------------------------
        List<Object[]> consumo30Dias = em.createQuery(
                "SELECT iv.produto.id, SUM(iv.quantidade) " +
                "FROM ItemVenda iv " +
                "WHERE iv.venda.status = com.erp.vendas.model.StatusVenda.FECHADA " +
                "AND iv.venda.dataVenda >= :limite " +
                "AND iv.produto.id IN :ids " +
                "GROUP BY iv.produto.id",
                Object[].class)
            .setParameter("limite", limite)
            .setParameter("ids", criticos.stream().map(Produto::getId).toList())
            .getResultList();

        // Monta mapa { produtoId → totalVendido } para lookup O(1)
        var mapaConsumo = new java.util.HashMap<Long, Long>();
        for (Object[] row : consumo30Dias) {
            mapaConsumo.put((Long) row[0], (Long) row[1]);
        }

        // -----------------------------------------------------------------
        // Monta a lista de sugestões calculando a quantidade sugerida
        // -----------------------------------------------------------------
        List<SugestaoCompra> sugestoes = new ArrayList<>();
        for (Produto p : criticos) {
            long vendido30  = mapaConsumo.getOrDefault(p.getId(), 0L);
            int  sugerido   = calcularQuantidadeSugerida(p, vendido30);

            SugestaoCompra s = new SugestaoCompra();
            s.setProduto(p);
            s.setFornecedor(p.getFornecedor());
            s.setEstoqueAtual(p.getQuantidadeEstoque());
            s.setEstoqueMinimo(p.getQuantidadeMinima());
            s.setVendidosUltimos30Dias((int) vendido30);
            s.setMediaDiariaConsumo(vendido30 > 0
                ? BigDecimal.valueOf(vendido30)
                      .divide(BigDecimal.valueOf(JANELA_DIAS), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            s.setQuantidadeSugerida(sugerido);

            sugestoes.add(s);
            LOG.fine("[CompraService] Sugestão: " + p.getNome()
                + " | Estoque: " + p.getQuantidadeEstoque()
                + " | Vendido 30d: " + vendido30
                + " | Sugerido: " + sugerido);
        }

        LOG.info("[CompraService] Sugestões geradas: " + sugestoes.size() + " produtos críticos.");
        return sugestoes;
    }

    // =========================================================
    // Cálculo da quantidade sugerida
    // =========================================================

    /**
     * Calcula quantas unidades sugerir no pedido de compra.
     *
     * <h3>Fórmula:</h3>
     * <pre>
     *   mediadiaria   = vendido30Dias / JANELA_DIAS
     *   necessidade   = ceil(mediadiaria * JANELA_DIAS) - estoqueAtual
     *   pisoMinimo    = estoqueMinimo * 2
     *   qtdSugerida   = max(necessidade, pisoMinimo)
     * </pre>
     *
     * <p>O piso garante que mesmo produtos sem histórico de vendas (lançados
     * recentemente) recebam uma sugestão razoável para reabastecer o estoque.</p>
     *
     * @param produto     produto crítico
     * @param vendido30   total vendido nos últimos 30 dias
     * @return quantidade sugerida para pedir ao fornecedor (sempre >= 1)
     */
    private int calcularQuantidadeSugerida(Produto produto, long vendido30) {
        int estoqueAtual  = produto.getQuantidadeEstoque() != null ? produto.getQuantidadeEstoque() : 0;
        int estoqueMinimo = produto.getQuantidadeMinima()  != null ? produto.getQuantidadeMinima()  : 1;

        // Projeção: quantas unidades serão consumidas nos próximos 30 dias
        int projecao30Dias = (int) Math.ceil((double) vendido30);

        // Necessidade = projeção - estoque atual (nunca negativa)
        int necessidade = Math.max(0, projecao30Dias - estoqueAtual);

        // Piso: ao menos 2× o estoque mínimo para ter folga de segurança
        int pisoMinimo = estoqueMinimo * 2;

        // Retorna o maior entre necessidade e piso (mínimo 1 unidade)
        return Math.max(Math.max(necessidade, pisoMinimo), 1);
    }
}
