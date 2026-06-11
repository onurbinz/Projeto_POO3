package com.erp.vendas.model;

/**
 * Representa o ciclo de vida de uma {@link Venda} (carrinho de compras).
 *
 * <h3>Transições de estado permitidas:</h3>
 * <pre>
 *   ABERTA ──► FECHADA
 *   ABERTA ──► CANCELADA
 * </pre>
 *
 * <p>Uma venda {@code FECHADA} ou {@code CANCELADA} não deve ter
 * seus itens alterados. Essa regra é imposta na camada de serviço (EJB).</p>
 *
 * @see Venda
 */
public enum StatusVenda {

    /**
     * Carrinho em composição.
     * Itens podem ser adicionados, alterados ou removidos.
     * Pagamento ainda não realizado.
     */
    ABERTA,

    /**
     * Venda concluída e paga.
     * O estoque já foi decrementado. Registro imutável.
     */
    FECHADA,

    /**
     * Venda cancelada antes do pagamento.
     * Caso o estoque tenha sido reservado, deve ser estornado.
     */
    CANCELADA
}
