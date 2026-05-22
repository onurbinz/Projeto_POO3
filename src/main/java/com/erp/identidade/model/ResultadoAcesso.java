package com.erp.identidade.model;

/**
 * Enum que representa o resultado de uma ação registrada no log de acessos.
 *
 * <p>Utilizado pela entidade {@link LogAcesso} para classificar
 * se uma operação foi concluída com sucesso ou resultou em erro.</p>
 *
 * <h3>Exemplos de uso:</h3>
 * <ul>
 *   <li>{@code SUCESSO} — Login bem-sucedido, venda concluída</li>
 *   <li>{@code ERRO} — Senha incorreta, permissão negada, falha na transação</li>
 * </ul>
 *
 * <p>Armazenado no banco como {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)},
 * garantindo legibilidade na tabela {@code log_acessos}.</p>
 *
 * @see LogAcesso
 */
public enum ResultadoAcesso {

    /** Operação concluída com sucesso. */
    SUCESSO,

    /** Operação resultou em erro ou foi negada. */
    ERRO
}
