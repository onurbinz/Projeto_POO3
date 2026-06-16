package com.erp.catalogo.service;

import com.erp.catalogo.model.Categoria;
import com.erp.catalogo.model.Produto;
import com.erp.compras.model.Fornecedor;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.logging.Logger;

/**
 * Session Bean responsável pelas regras de negócio do catálogo de produtos.
 *
 * <h3>Operações:</h3>
 * <ul>
 *   <li>{@link #salvar(Produto)}      — persiste um novo produto</li>
 *   <li>{@link #editar(Produto)}      — atualiza produto existente</li>
 *   <li>{@link #excluir(Long)}        — exclusão LÓGICA (soft delete): ativo = false</li>
 *   <li>{@link #listarAtivos()}       — lista produtos com ativo = true</li>
 *   <li>{@link #listarComEstoqueBaixo()} — produtos ativos abaixo do estoque mínimo</li>
 *   <li>{@link #buscarPorId(Long)}    — busca produto pelo id</li>
 *   <li>{@link #listarCategorias()}   — lista todas as categorias (para selects)</li>
 *   <li>{@link #listarFornecedores()} — lista todos os fornecedores (para selects)</li>
 * </ul>
 *
 * <h3>Soft Delete:</h3>
 * <p>O método {@link #excluir(Long)} nunca faz DELETE físico. Marca o campo
 * {@code ativo = false} na entidade. O motivo: registros de venda anteriores
 * referenciam o produto via FK — deletar fisicamente quebraria a integridade
 * referencial e perderia o histórico financeiro.</p>
 *
 * <h3>Transações:</h3>
 * <p>Cada método declara explicitamente seu comportamento transacional via
 * {@link TransactionAttribute}, seguindo o princípio do menor privilégio:
 * leituras usam {@code SUPPORTS} (não criam transação desnecessária),
 * escritas usam {@code REQUIRED}.</p>
 *
 * @see Produto
 * @see Categoria
 * @see Fornecedor
 */
@Stateless
public class ProdutoService {

    private static final Logger LOG = Logger.getLogger(ProdutoService.class.getName());

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    // =========================================================
    // Escrita — REQUIRED (cria ou reutiliza transação ativa)
    // =========================================================

    /**
     * Persiste um novo produto no catálogo.
     *
     * <p>O produto deve ter {@link Categoria} preenchida.
     * O fornecedor é opcional (serviços não têm fornecedor).</p>
     *
     * @param produto entidade sem id (gerado pelo banco)
     * @throws IllegalArgumentException se nome ou preço estiverem em branco/nulos
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void salvar(Produto produto) {
        validar(produto);
        produto.setAtivo(true); // Garante que novo produto começa ativo
        em.persist(produto);
        LOG.info("[ProdutoService] Produto criado: " + produto.getNome());
    }

    /**
     * Atualiza os dados de um produto existente.
     *
     * <p>Usa {@code em.merge()} para sincronizar a entidade detached
     * (retornada pelo managed bean após a view) com o contexto de persistência.</p>
     *
     * @param produto entidade com id preenchido e dados atualizados
     * @throws EntityNotFoundException se o produto não existir no banco
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void editar(Produto produto) {
        validar(produto);
        if (em.find(Produto.class, produto.getId()) == null) {
            throw new EntityNotFoundException(
                "Produto não encontrado para edição: id=" + produto.getId());
        }
        em.merge(produto);
        LOG.info("[ProdutoService] Produto editado: id=" + produto.getId());
    }

    /**
     * Exclusão LÓGICA (soft delete) — marca ativo = false.
     *
     * <p>O registro permanece no banco para preservar histórico de vendas.
     * O produto desaparece de todas as listagens da aplicação, mas
     * ainda pode ser recuperado via query direta ao banco se necessário.</p>
     *
     * @param id identificador do produto a excluir
     * @throws EntityNotFoundException se o produto não existir
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void excluir(Long id) {
        Produto produto = em.find(Produto.class, id);
        if (produto == null) {
            throw new EntityNotFoundException(
                "Produto não encontrado para exclusão: id=" + id);
        }
        // Soft delete: preserva integridade referencial com tabela de vendas
        produto.desativar(); // Equivale a: produto.setAtivo(false)
        LOG.info("[ProdutoService] Produto excluído (soft delete): " +
                 produto.getNome() + " (id=" + id + ")");
    }

    // =========================================================
    // Leitura — SUPPORTS (participa de transação se houver, senão sem TX)
    // =========================================================

    /**
     * Lista todos os produtos ativos, com categoria e fornecedor carregados.
     *
     * <p>Usa {@code LEFT JOIN FETCH} para carregar os relacionamentos
     * {@code LAZY} em uma única query — evita N+1 queries ao renderizar
     * a tabela no JSF.</p>
     *
     * @return lista ordenada por nome (nunca null)
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Produto> listarAtivos() {
        return em.createQuery(
                "SELECT DISTINCT p FROM Produto p " +
                "LEFT JOIN FETCH p.categoria " +
                "LEFT JOIN FETCH p.fornecedor " +
                "WHERE p.ativo = true " +
                "ORDER BY p.nome",
                Produto.class)
            .getResultList();
    }

    /**
     * Lista produtos ativos com estoque abaixo ou igual ao mínimo configurado.
     *
     * <p>Considera apenas produtos físicos (com quantidadeEstoque != null).
     * Serviços são ignorados pois não têm estoque físico.</p>
     *
     * @return lista de produtos que precisam de reposição (pode ser vazia)
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Produto> listarComEstoqueBaixo() {
        return em.createQuery(
                "SELECT p FROM Produto p " +
                "WHERE p.ativo = true " +
                "AND p.quantidadeEstoque IS NOT NULL " +
                "AND p.quantidadeMinima IS NOT NULL " +
                "AND p.quantidadeEstoque <= p.quantidadeMinima " +
                "ORDER BY p.quantidadeEstoque ASC",
                Produto.class)
            .getResultList();
    }

    /**
     * Busca um produto pelo id, incluindo categoria e fornecedor.
     *
     * @param id identificador
     * @return entidade Produto ou {@code null} se não encontrada
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Produto buscarPorId(Long id) {
        return em.find(Produto.class, id);
    }

    /**
     * Lista todas as categorias disponíveis para o select do formulário.
     *
     * @return lista ordenada por nome
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Categoria> listarCategorias() {
        return em.createQuery(
                "SELECT c FROM Categoria c ORDER BY c.nome",
                Categoria.class)
            .getResultList();
    }

    /**
     * Lista todos os fornecedores disponíveis para o select do formulário.
     *
     * @return lista ordenada por nome
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Fornecedor> listarFornecedores() {
        return em.createQuery(
                "SELECT f FROM Fornecedor f ORDER BY f.nome",
                Fornecedor.class)
            .getResultList();
    }

    // =========================================================
    // Validação interna
    // =========================================================

    /**
     * Valida as regras de negócio básicas antes de persistir.
     *
     * @param produto entidade a validar
     * @throws IllegalArgumentException se algum campo obrigatório falhar
     */
    private void validar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new IllegalArgumentException("Nome do produto é obrigatório.");
        }
        if (produto.getPreco() == null || produto.getPreco().signum() <= 0) {
            throw new IllegalArgumentException("Preço deve ser maior que zero.");
        }
        if (produto.getCategoria() == null) {
            throw new IllegalArgumentException("Categoria é obrigatória.");
        }
        // Estoque mínimo só faz sentido se houver controle de estoque
        if (produto.getQuantidadeEstoque() != null
                && produto.getQuantidadeMinima() != null
                && produto.getQuantidadeMinima() < 0) {
            throw new IllegalArgumentException("Quantidade mínima não pode ser negativa.");
        }
    }
}
