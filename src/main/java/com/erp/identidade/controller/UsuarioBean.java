package com.erp.identidade.controller;

import com.erp.identidade.model.Papel;
import com.erp.identidade.model.Usuario;
import com.erp.identidade.service.UsuarioService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
 * Managed Bean (CDI) do módulo de Usuários.
 *
 * <h3>Responsabilidades:</h3>
 * <ul>
 *   <li>Carregar a lista de usuários na abertura da página ({@link #init()})</li>
 *   <li>Orquestrar abertura/fechamento dos diálogos de criação e edição</li>
 *   <li>Aplicar hash BCrypt na senha antes de delegar ao {@link UsuarioService}</li>
 *   <li>Exibir mensagens de feedback via {@code FacesMessage}</li>
 * </ul>
 *
 * <h3>Escopo:</h3>
 * <p>{@code @ViewScoped}: o bean vive enquanto a view {@code usuarios.xhtml}
 * estiver ativa na mesma aba do browser. Requisições AJAX mantêm o estado
 * sem criar novo bean a cada clique.</p>
 *
 * <h3>Injeção:</h3>
 * <p>O {@link UsuarioService} é um EJB {@code @Stateless} injetado via
 * {@code @EJB}. O {@link BCryptPasswordEncoder} é instanciado diretamente
 * (custo 12) pois não é gerenciado pelo Spring neste contexto CDI.</p>
 *
 * @see UsuarioService
 */
@Named("usuarioBean")
@ViewScoped
public class UsuarioBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(UsuarioBean.class.getName());

    // =========================================================
    // Dependências
    // =========================================================

    /** EJB de negócio — injetado pelo container WildFly. */
    @EJB
    private UsuarioService usuarioService;

    /**
     * Encoder BCrypt para hash da senha antes de persistir.
     * Custo 12 = mesmo padrão do SecurityConfig.
     */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    // =========================================================
    // Estado da view
    // =========================================================

    /** Lista completa de usuários exibida na tabela. */
    private List<Usuario> usuarios;

    /** Entidade em edição/criação — compartilhada pelos dois diálogos. */
    private Usuario usuarioSelecionado;

    /**
     * Papel selecionado nos selects do formulário.
     * Valor: {@code "ROLE_ADMIN"} ou {@code "ROLE_DEFAULT"}.
     */
    private String papelSelecionado;

    /**
     * Senha digitada no formulário (texto puro).
     * Convertida para BCrypt antes de enviar ao serviço.
     * Não armazenada na entidade — campo transitório da view.
     */
    private String senhaDigitada;

    /** Controla a visibilidade do diálogo de criação. */
    private boolean dialogCriarVisivel;

    /** Controla a visibilidade do diálogo de edição. */
    private boolean dialogEditarVisivel;

    // =========================================================
    // Inicialização
    // =========================================================

    /**
     * Executado pelo CDI após a construção do bean.
     * Carrega a lista inicial de usuários.
     */
    @PostConstruct
    public void init() {
        carregarUsuarios();
    }

    // =========================================================
    // Ações do dataTable
    // =========================================================

    /**
     * Recarrega a lista de usuários do banco.
     * Chamado após criar, editar ou inativar.
     */
    public void carregarUsuarios() {
        usuarios = usuarioService.listarTodos();
    }

    // =========================================================
    // Diálogo: CRIAR
    // =========================================================

    /**
     * Prepara o formulário para criar um novo usuário.
     * Limpa todos os campos e abre o diálogo de criação.
     */
    public void prepararCriar() {
        usuarioSelecionado = new Usuario();
        papelSelecionado   = UsuarioService.ROLE_DEFAULT;  // Padrão: DEFAULT
        senhaDigitada      = null;
        dialogCriarVisivel = true;
    }

    /**
     * Salva o novo usuário após validação e hash da senha.
     * Fecha o diálogo e recarrega a tabela em caso de sucesso.
     */
    public void salvar() {
        try {
            // Valida senha obrigatória na criação
            if (senhaDigitada == null || senhaDigitada.isBlank()) {
                adicionarMensagemErro("Senha obrigatória para criar usuário.");
                return;
            }
            if (senhaDigitada.length() < 6) {
                adicionarMensagemErro("Senha deve ter no mínimo 6 caracteres.");
                return;
            }

            // Hash BCrypt antes de persistir — nunca texto puro no banco
            usuarioSelecionado.setSenha(encoder.encode(senhaDigitada));

            usuarioService.salvar(usuarioSelecionado, papelSelecionado);

            carregarUsuarios();
            dialogCriarVisivel = false;
            senhaDigitada = null;
            adicionarMensagemSucesso("Usuário criado com sucesso.");

        } catch (IllegalArgumentException e) {
            adicionarMensagemErro(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[UsuarioBean] Erro ao salvar usuário", e);
            adicionarMensagemErro("Erro interno ao salvar. Tente novamente.");
        }
    }

    // =========================================================
    // Diálogo: EDITAR
    // =========================================================

    /**
     * Prepara o formulário de edição com os dados do usuário selecionado.
     * O campo de senha fica em branco — só é alterado se o usuário digitar algo.
     *
     * @param usuario usuário clicado na tabela
     */
    public void prepararEditar(Usuario usuario) {
        // Recarrega do banco para garantir dados frescos (evita LazyInitializationException)
        usuarioSelecionado  = usuarioService.buscarPorId(usuario.getId());
        senhaDigitada       = null;  // Senha em branco = mantém a atual
        papelSelecionado    = resolverPapelAtual(usuarioSelecionado);
        dialogEditarVisivel = true;
    }

    /**
     * Salva as alterações do usuário em edição.
     * Se {@link #senhaDigitada} estiver em branco, mantém a senha existente.
     */
    public void editar() {
        try {
            // Aplica novo hash apenas se uma nova senha foi digitada
            if (senhaDigitada != null && !senhaDigitada.isBlank()) {
                if (senhaDigitada.length() < 6) {
                    adicionarMensagemErro("Senha deve ter no mínimo 6 caracteres.");
                    return;
                }
                usuarioSelecionado.setSenha(encoder.encode(senhaDigitada));
            } else {
                // Sinaliza ao serviço para manter a senha atual (string em branco)
                usuarioSelecionado.setSenha(null);
            }

            usuarioService.editar(usuarioSelecionado, papelSelecionado);

            carregarUsuarios();
            dialogEditarVisivel = false;
            senhaDigitada = null;
            adicionarMensagemSucesso("Usuário atualizado com sucesso.");

        } catch (IllegalArgumentException e) {
            adicionarMensagemErro(e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[UsuarioBean] Erro ao editar usuário", e);
            adicionarMensagemErro("Erro interno ao editar. Tente novamente.");
        }
    }

    // =========================================================
    // Inativação
    // =========================================================

    /**
     * Inativa logicamente o usuário (marca {@code ativo = false}).
     * Solicita confirmação via diálogo PrimeFaces antes de executar.
     *
     * @param usuario usuário a inativar
     */
    public void inativar(Usuario usuario) {
        try {
            usuarioService.inativar(usuario.getId());
            carregarUsuarios();
            adicionarMensagemSucesso("Usuário \"" + usuario.getNome() + "\" inativado.");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[UsuarioBean] Erro ao inativar usuário", e);
            adicionarMensagemErro("Erro ao inativar. Tente novamente.");
        }
    }

    // =========================================================
    // Auxiliares de UI
    // =========================================================

    /**
     * Resolve o papel atual do usuário para pré-selecionar no select.
     * Retorna {@code "ROLE_DEFAULT"} se o usuário não possuir papel cadastrado.
     *
     * @param usuario entidade com papéis carregados
     * @return nome do papel atual
     */
    private String resolverPapelAtual(Usuario usuario) {
        if (usuario.getPapeis() == null || usuario.getPapeis().isEmpty()) {
            return UsuarioService.ROLE_DEFAULT;
        }
        return usuario.getPapeis().iterator().next().getNome();
    }

    /**
     * Retorna o rótulo amigável do papel para exibição na tabela.
     *
     * @param usuario entidade
     * @return "Administrador", "Padrão" ou "Sem papel"
     */
    public String labelPapel(Usuario usuario) {
        String papel = resolverPapelAtual(usuario);
        return switch (papel) {
            case UsuarioService.ROLE_ADMIN   -> "Administrador";
            case UsuarioService.ROLE_DEFAULT -> "Padrão";
            default                          -> "Sem papel";
        };
    }

    /**
     * Retorna os papéis disponíveis para o {@code <p:selectOneMenu>}.
     *
     * @return lista ["ROLE_ADMIN", "ROLE_DEFAULT"]
     */
    public List<String> getPapeisDisponiveis() {
        return usuarioService.listarPapeisDisponiveis();
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

    public List<Usuario> getUsuarios() { return usuarios; }

    public Usuario getUsuarioSelecionado() { return usuarioSelecionado; }
    public void setUsuarioSelecionado(Usuario u) { this.usuarioSelecionado = u; }

    public String getPapelSelecionado() { return papelSelecionado; }
    public void setPapelSelecionado(String p) { this.papelSelecionado = p; }

    public String getSenhaDigitada() { return senhaDigitada; }
    public void setSenhaDigitada(String s) { this.senhaDigitada = s; }

    public boolean isDialogCriarVisivel() { return dialogCriarVisivel; }
    public void setDialogCriarVisivel(boolean v) { this.dialogCriarVisivel = v; }

    public boolean isDialogEditarVisivel() { return dialogEditarVisivel; }
    public void setDialogEditarVisivel(boolean v) { this.dialogEditarVisivel = v; }
}
