package com.erp.identidade.service;

import com.erp.identidade.model.Papel;
import com.erp.identidade.model.Usuario;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session Bean responsável pelas regras de negócio do módulo de Usuários.
 *
 * <h3>Operações suportadas:</h3>
 * <ul>
 *   <li>{@link #salvar(Usuario, String)}   — criação de novo usuário com papel ADMIN ou DEFAULT</li>
 *   <li>{@link #editar(Usuario, String)}   — edição de dados e papel de usuário existente</li>
 *   <li>{@link #listarAtivos()}            — lista todos os usuários ativos</li>
 *   <li>{@link #listarTodos()}             — lista todos os usuários (ativos e inativos)</li>
 *   <li>{@link #inativar(Long)}            — inativação lógica (ativo = false, sem DELETE físico)</li>
 *   <li>{@link #buscarPorId(Long)}         — busca usuário pelo id</li>
 *   <li>{@link #emailJaCadastrado(String)} — validação de unicidade de email</li>
 * </ul>
 *
 * <h3>Papéis permitidos:</h3>
 * <p>A criação e edição de usuários aceitam exclusivamente {@code ROLE_ADMIN}
 * ou {@code ROLE_DEFAULT}. Qualquer outro valor é rejeitado com
 * {@link IllegalArgumentException}.</p>
 *
 * <h3>Segurança da senha:</h3>
 * <p>Este serviço <b>não</b> faz o hash da senha. O hash BCrypt deve ser aplicado
 * pelo Managed Bean antes de chamar {@code salvar()} ou {@code editar()},
 * usando o {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}
 * injetado via CDI.</p>
 *
 * @see Usuario
 * @see Papel
 */
@Stateless
public class UsuarioService {

    private static final Logger LOG = Logger.getLogger(UsuarioService.class.getName());

    /** Papéis válidos — apenas ADMIN e DEFAULT permitidos neste ERP. */
    public static final String ROLE_ADMIN   = "ROLE_ADMIN";
    public static final String ROLE_DEFAULT = "ROLE_DEFAULT";

    @PersistenceContext(unitName = "erpBarbershopPU")
    private EntityManager em;

    // =========================================================
    // Criação
    // =========================================================

    /**
     * Persiste um novo usuário com o papel informado.
     *
     * <p>O email deve ser único. A senha informada em {@code usuario.getSenha()}
     * deve estar previamente hashada em BCrypt pelo chamador.</p>
     *
     * @param usuario entidade preenchida (sem id — gerado pelo banco)
     * @param nomePapel {@code "ROLE_ADMIN"} ou {@code "ROLE_DEFAULT"}
     * @throws IllegalArgumentException se o papel for inválido ou o email já existir
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void salvar(Usuario usuario, String nomePapel) {
        validarPapel(nomePapel);

        if (emailJaCadastrado(usuario.getEmail())) {
            throw new IllegalArgumentException(
                "Email já cadastrado: " + usuario.getEmail());
        }

        Papel papel = buscarOuCriarPapel(nomePapel);
        usuario.getPapeis().clear();
        usuario.adicionarPapel(papel);
        usuario.setAtivo(true);

        em.persist(usuario);
        LOG.info(String.format("[UsuarioService] Usuário criado: %s | Papel: %s",
            usuario.getEmail(), nomePapel));
    }

    // =========================================================
    // Edição
    // =========================================================

    /**
     * Atualiza os dados de um usuário existente e reatribui seu papel.
     *
     * <p>Se {@code usuario.getSenha()} estiver em branco, a senha atual
     * do banco é mantida (não sobrescrita). Caso contrário, deve vir
     * previamente hashada em BCrypt.</p>
     *
     * @param usuario entidade com id preenchido e dados atualizados
     * @param nomePapel {@code "ROLE_ADMIN"} ou {@code "ROLE_DEFAULT"}
     * @throws IllegalArgumentException se o papel for inválido
     * @throws javax.persistence.EntityNotFoundException se o usuário não existir
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void editar(Usuario usuario, String nomePapel) {
        validarPapel(nomePapel);

        Usuario gerenciado = em.find(Usuario.class, usuario.getId());
        if (gerenciado == null) {
            throw new javax.persistence.EntityNotFoundException(
                "Usuário não encontrado: id=" + usuario.getId());
        }

        // Atualiza campos editáveis
        gerenciado.setNome(usuario.getNome());
        gerenciado.setEmail(usuario.getEmail());

        // Senha: só substitui se o Managed Bean enviar um novo hash
        if (usuario.getSenha() != null && !usuario.getSenha().isBlank()) {
            gerenciado.setSenha(usuario.getSenha());
        }

        // Reatribui o papel (remove todos e adiciona o novo)
        gerenciado.getPapeis().clear();
        Papel papel = buscarOuCriarPapel(nomePapel);
        gerenciado.adicionarPapel(papel);

        // merge implícito: entidade gerenciada é sincronizada ao final da transação
        LOG.info(String.format("[UsuarioService] Usuário editado: %s | Papel: %s",
            gerenciado.getEmail(), nomePapel));
    }

    // =========================================================
    // Listagem
    // =========================================================

    /**
     * Retorna todos os usuários com {@code ativo = true}, com papéis carregados.
     *
     * @return lista ordenada por nome (nunca null)
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Usuario> listarAtivos() {
        return em.createQuery(
                "SELECT DISTINCT u FROM Usuario u " +
                "LEFT JOIN FETCH u.papeis " +
                "WHERE u.ativo = true " +
                "ORDER BY u.nome",
                Usuario.class)
            .getResultList();
    }

    /**
     * Retorna todos os usuários (ativos e inativos), com papéis carregados.
     *
     * @return lista ordenada por nome (nunca null)
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Usuario> listarTodos() {
        return em.createQuery(
                "SELECT DISTINCT u FROM Usuario u " +
                "LEFT JOIN FETCH u.papeis " +
                "ORDER BY u.ativo DESC, u.nome",
                Usuario.class)
            .getResultList();
    }

    // =========================================================
    // Inativação lógica
    // =========================================================

    /**
     * Inativa logicamente um usuário (marca {@code ativo = false}).
     *
     * <p>Nunca remove fisicamente o registro — logs de auditoria referenciam
     * o usuário via FK e devem permanecer íntegros.</p>
     *
     * @param id identificador do usuário a inativar
     * @throws javax.persistence.EntityNotFoundException se não encontrado
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void inativar(Long id) {
        Usuario usuario = em.find(Usuario.class, id);
        if (usuario == null) {
            throw new javax.persistence.EntityNotFoundException(
                "Usuário não encontrado para inativação: id=" + id);
        }
        usuario.setAtivo(false);
        LOG.info(String.format("[UsuarioService] Usuário inativado: %s (id=%d)",
            usuario.getEmail(), id));
    }

    // =========================================================
    // Busca
    // =========================================================

    /**
     * Busca um usuário pelo id.
     *
     * @param id identificador
     * @return entidade Usuario ou {@code null} se não encontrada
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Usuario buscarPorId(Long id) {
        return em.find(Usuario.class, id);
    }

    /**
     * Verifica se um email já está cadastrado no banco.
     *
     * @param email email a verificar
     * @return {@code true} se já existir
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public boolean emailJaCadastrado(String email) {
        Long count = em.createQuery(
                "SELECT COUNT(u) FROM Usuario u WHERE u.email = :email",
                Long.class)
            .setParameter("email", email)
            .getSingleResult();
        return count > 0;
    }

    /**
     * Lista os papéis disponíveis para atribuição.
     * Retorna sempre ["ROLE_ADMIN", "ROLE_DEFAULT"].
     *
     * @return lista imutável de papéis permitidos
     */
    public List<String> listarPapeisDisponiveis() {
        return List.of(ROLE_ADMIN, ROLE_DEFAULT);
    }

    // =========================================================
    // Auxiliares privados
    // =========================================================

    /**
     * Valida se o papel informado é um dos dois permitidos.
     *
     * @param nomePapel papel a validar
     * @throws IllegalArgumentException se inválido
     */
    private void validarPapel(String nomePapel) {
        if (!ROLE_ADMIN.equals(nomePapel) && !ROLE_DEFAULT.equals(nomePapel)) {
            throw new IllegalArgumentException(
                "Papel inválido: '" + nomePapel + "'. Permitidos: ROLE_ADMIN, ROLE_DEFAULT");
        }
    }

    /**
     * Busca o {@link Papel} pelo nome ou cria um novo se não existir.
     *
     * <p>Garante que o papel exista no banco antes de vincular ao usuário.</p>
     *
     * @param nomePapel nome do papel (ex: "ROLE_ADMIN")
     * @return entidade Papel gerenciada
     */
    private Papel buscarOuCriarPapel(String nomePapel) {
        try {
            return em.createQuery(
                    "SELECT p FROM Papel p WHERE p.nome = :nome",
                    Papel.class)
                .setParameter("nome", nomePapel)
                .getSingleResult();
        } catch (NoResultException e) {
            LOG.info("[UsuarioService] Papel não encontrado, criando: " + nomePapel);
            Papel novoPapel = new Papel(nomePapel);
            em.persist(novoPapel);
            return novoPapel;
        }
    }
}
