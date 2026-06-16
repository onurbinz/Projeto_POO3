package com.erp.compras.service;

import com.erp.compras.model.Fornecedor;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.logging.Logger;

/**
 * Session Bean de negócio para o cadastro de Fornecedores.
 *
 * <h3>Operações:</h3>
 * <ul>
 *   <li>{@link #salvar(Fornecedor)}         — persiste novo fornecedor</li>
 *   <li>{@link #editar(Fornecedor)}          — atualiza fornecedor existente</li>
 *   <li>{@link #excluir(Long)}               — exclusão lógica (ativo = false)</li>
 *   <li>{@link #listarAtivos()}              — lista fornecedores ativos</li>
 *   <li>{@link #buscarPorId(Long)}           — busca pelo id</li>
 *   <li>{@link #cnpjJaCadastrado(String, Long)} — valida unicidade de CNPJ</li>
 * </ul>
 *
 * <h3>Soft Delete:</h3>
 * <p>Fornecedores nunca são deletados fisicamente — produtos os referenciam via FK.
 * A exclusão lógica marca {@code ativo = false} e os remove das listagens.</p>
 *
 * @see Fornecedor
 */
@Stateless
public class FornecedorService {

    private static final Logger LOG = Logger.getLogger(FornecedorService.class.getName());

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    // =========================================================
    // Escrita
    // =========================================================

    /**
     * Persiste um novo fornecedor após validação de negócio.
     *
     * @param fornecedor entidade sem id
     * @throws IllegalArgumentException se CNPJ duplicado ou campos inválidos
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void salvar(Fornecedor fornecedor) {
        validar(fornecedor, null);
        fornecedor.setAtivo(true);
        em.persist(fornecedor);
        LOG.info("[FornecedorService] Fornecedor criado: " + fornecedor.getNome());
    }

    /**
     * Atualiza os dados de um fornecedor existente.
     *
     * @param fornecedor entidade com id preenchido
     * @throws EntityNotFoundException  se o fornecedor não existir
     * @throws IllegalArgumentException se CNPJ já usado por outro fornecedor
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void editar(Fornecedor fornecedor) {
        if (em.find(Fornecedor.class, fornecedor.getId()) == null) {
            throw new EntityNotFoundException(
                "Fornecedor não encontrado: id=" + fornecedor.getId());
        }
        validar(fornecedor, fornecedor.getId());
        em.merge(fornecedor);
        LOG.info("[FornecedorService] Fornecedor editado: id=" + fornecedor.getId());
    }

    /**
     * Exclusão lógica do fornecedor (soft delete).
     * Marca {@code ativo = false} — preserva a FK em produtos já cadastrados.
     *
     * @param id identificador do fornecedor
     * @throws EntityNotFoundException se não encontrado
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void excluir(Long id) {
        Fornecedor f = em.find(Fornecedor.class, id);
        if (f == null) {
            throw new EntityNotFoundException(
                "Fornecedor não encontrado: id=" + id);
        }
        f.setAtivo(false);
        LOG.info("[FornecedorService] Fornecedor inativado (soft delete): id=" + id);
    }

    // =========================================================
    // Leitura
    // =========================================================

    /**
     * Lista todos os fornecedores ativos, ordenados por nome.
     *
     * @return lista nunca null
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Fornecedor> listarAtivos() {
        return em.createQuery(
                "SELECT f FROM Fornecedor f WHERE f.ativo = true ORDER BY f.nome",
                Fornecedor.class)
            .getResultList();
    }

    /**
     * Busca fornecedor pelo id.
     *
     * @param id identificador
     * @return entidade ou {@code null}
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Fornecedor buscarPorId(Long id) {
        return em.find(Fornecedor.class, id);
    }

    /**
     * Verifica se o CNPJ já está cadastrado para outro fornecedor.
     * Permite edição do próprio fornecedor (exclui o id atual da verificação).
     *
     * @param cnpj CNPJ sem formatação (14 dígitos)
     * @param idIgnorar id do fornecedor em edição (null para criação)
     * @return {@code true} se CNPJ pertence a outro fornecedor
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public boolean cnpjJaCadastrado(String cnpj, Long idIgnorar) {
        String jpql = idIgnorar == null
            ? "SELECT COUNT(f) FROM Fornecedor f WHERE f.cnpj = :cnpj"
            : "SELECT COUNT(f) FROM Fornecedor f WHERE f.cnpj = :cnpj AND f.id <> :id";

        var query = em.createQuery(jpql, Long.class).setParameter("cnpj", cnpj);
        if (idIgnorar != null) query.setParameter("id", idIgnorar);

        return query.getSingleResult() > 0;
    }

    // =========================================================
    // Validação
    // =========================================================

    /**
     * Valida regras de negócio antes de persistir ou atualizar.
     *
     * @param f         fornecedor a validar
     * @param idIgnorar id a ignorar na checagem de CNPJ único (null = novo)
     * @throws IllegalArgumentException se alguma regra for violada
     */
    private void validar(Fornecedor f, Long idIgnorar) {
        if (f.getNome() == null || f.getNome().isBlank()) {
            throw new IllegalArgumentException("Nome do fornecedor é obrigatório.");
        }
        if (f.getCnpj() == null || f.getCnpj().isBlank()) {
            throw new IllegalArgumentException("CNPJ é obrigatório.");
        }
        // Remove máscara caso o usuário tenha digitado formatado
        String cnpjLimpo = f.getCnpj().replaceAll("[^\\d]", "");
        if (cnpjLimpo.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve ter 14 dígitos.");
        }
        f.setCnpj(cnpjLimpo); // Garante armazenamento sem formatação

        if (cnpjJaCadastrado(cnpjLimpo, idIgnorar)) {
            throw new IllegalArgumentException(
                "CNPJ " + formatarCnpj(cnpjLimpo) + " já cadastrado.");
        }
        if (f.getEmailContato() == null || f.getEmailContato().isBlank()) {
            throw new IllegalArgumentException("Email de contato é obrigatório.");
        }
    }

    /**
     * Formata o CNPJ para exibição nas mensagens de erro.
     * Exemplo: "12345678000190" → "12.345.678/0001-90"
     *
     * @param cnpj CNPJ com 14 dígitos sem formatação
     * @return CNPJ formatado
     */
    public static String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return cnpj;
        return cnpj.replaceFirst(
            "(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})",
            "$1.$2.$3/$4-$5");
    }
}
