package com.erp.identidade.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Entidade de auditoria obrigatória do sistema.
 *
 * <p>Registra todo acesso e operação financeira relevante,
 * conforme regra de arquitetura do ERP. Cada registro é
 * <strong>imutável</strong> — logs nunca devem ser editados ou excluídos.</p>
 *
 * <h3>Tipos de ações auditadas:</h3>
 * <ul>
 *   <li>{@code LOGIN}          — Tentativa de autenticação</li>
 *   <li>{@code LOGOUT}         — Encerramento de sessão</li>
 *   <li>{@code VENDA}          — Registro de venda/comanda</li>
 *   <li>{@code COMPRA}         — Pedido de compra a fornecedor</li>
 *   <li>{@code ESTORNO}        — Cancelamento/estorno financeiro</li>
 *   <li>{@code ALTERACAO_PRECO}— Mudança de preço no catálogo</li>
 * </ul>
 *
 * <h3>Relacionamentos:</h3>
 * <ul>
 *   <li>{@code ManyToOne} com {@link Usuario} — identifica quem executou a ação</li>
 * </ul>
 *
 * @see Usuario
 * @see ResultadoAcesso
 */
@Entity
@Table(name = "log_acessos")
public class LogAcesso implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==========================================================
    // Chave Primária
    // ==========================================================

    /**
     * Identificador único do registro de log.
     * Gerado automaticamente pelo PostgreSQL (BIGSERIAL).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ==========================================================
    // Relacionamentos
    // ==========================================================

    /**
     * Usuário que executou a ação.
     *
     * <p>{@code FetchType.LAZY}: os dados do usuário só são carregados
     * quando explicitamente acessados, otimizando consultas de listagem.</p>
     *
     * <p>{@code nullable = false}: toda ação deve ter um autor identificado.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // ==========================================================
    // Atributos
    // ==========================================================

    /**
     * Data e hora exata em que a ação foi executada.
     * Utiliza {@code LocalDateTime} do Java 8+ (suportado nativamente pelo Hibernate).
     */
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    /**
     * Descrição da ação executada.
     * Exemplos: "LOGIN", "VENDA", "COMPRA", "ESTORNO", "ALTERACAO_PRECO".
     */
    @Column(name = "acao", nullable = false, length = 100)
    private String acao;

    /**
     * Endereço IP de onde a ação foi executada.
     * Útil para rastreamento de segurança e auditoria.
     * Suporta IPv4 (15 chars) e IPv6 (45 chars).
     */
    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    /**
     * Resultado da ação: SUCESSO ou ERRO.
     *
     * <p>Armazenado como {@code VARCHAR} no banco (não como ordinal numérico),
     * garantindo que os dados sejam legíveis diretamente em consultas SQL.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 10)
    private ResultadoAcesso resultado;

    // ==========================================================
    // Construtores
    // ==========================================================

    /** Construtor padrão exigido pelo JPA. */
    public LogAcesso() {
    }

    /**
     * Construtor completo para criação de registros de auditoria.
     *
     * @param usuario   usuário que executou a ação
     * @param dataHora  momento exato da ação
     * @param acao      tipo da ação (ex: "LOGIN", "VENDA")
     * @param ip        endereço IP de origem
     * @param resultado SUCESSO ou ERRO
     */
    public LogAcesso(Usuario usuario, LocalDateTime dataHora, String acao,
                     String ip, ResultadoAcesso resultado) {
        this.usuario = usuario;
        this.dataHora = dataHora;
        this.acao = acao;
        this.ip = ip;
        this.resultado = resultado;
    }

    // ==========================================================
    // Getters e Setters
    // ==========================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public ResultadoAcesso getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoAcesso resultado) {
        this.resultado = resultado;
    }

    // ==========================================================
    // equals, hashCode e toString
    // ==========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogAcesso logAcesso = (LogAcesso) o;
        return Objects.equals(id, logAcesso.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LogAcesso{" +
               "id=" + id +
               ", usuarioId=" + (usuario != null ? usuario.getId() : null) +
               ", dataHora=" + dataHora +
               ", acao='" + acao + '\'' +
               ", ip='" + ip + '\'' +
               ", resultado=" + resultado +
               '}';
    }
}
