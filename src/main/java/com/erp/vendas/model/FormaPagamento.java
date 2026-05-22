package com.erp.vendas.model;

/**
 * Enum que representa as formas de pagamento aceitas pela barbearia.
 *
 * <p>Utilizado pela entidade {@link Venda} para registrar como
 * o cliente efetuou o pagamento.</p>
 *
 * <p>Armazenado no banco como {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)},
 * garantindo legibilidade em consultas SQL e relatórios.</p>
 *
 * @see Venda
 */
public enum FormaPagamento {

    /** Pagamento via boleto bancário. */
    BOLETO,

    /** Pagamento via cartão de crédito (maquininha). */
    CARTAO_CREDITO,

    /** Pagamento via PIX (transferência instantânea). */
    PIX
}
