package com.erp.catalogo.controller;

import com.erp.catalogo.model.Categoria;
import com.erp.catalogo.service.ProdutoService;

import javax.ejb.EJB;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;

/**
 * Converter JSF para a entidade {@link Categoria}.
 *
 * <h3>Por que é necessário?</h3>
 * <p>O JSF trabalha com Strings na camada HTTP (parâmetros de formulário).
 * Quando um {@code <p:selectOneMenu>} exibe objetos {@code Categoria},
 * o JSF precisa converter o ID (String enviado pelo browser) de volta
 * para a entidade gerenciada pelo JPA.</p>
 *
 * <h3>Fluxo:</h3>
 * <pre>
 *   Browser → "42" (String)  →  getAsObject()  →  Categoria{id=42}
 *   Servidor → Categoria{id=42}  →  getAsString()  →  "42" (renderização HTML)
 * </pre>
 *
 * @see FacesConverter
 */
@FacesConverter(value = "categoriaConverter", managed = true)
public class CategoriaConverter implements Converter<Categoria> {

    /**
     * EJB injetado para buscar a Categoria pelo id no banco.
     * {@code managed = true} no @FacesConverter permite injeção de @EJB.
     */
    @EJB
    private ProdutoService produtoService;

    /**
     * Converte a String do parâmetro HTTP de volta para a entidade Categoria.
     *
     * @param context   contexto JSF
     * @param component componente que disparou a conversão
     * @param value     String com o id da categoria (ex: "42")
     * @return entidade Categoria ou {@code null} se value for vazio
     */
    @Override
    public Categoria getAsObject(FacesContext context,
                                  UIComponent  component,
                                  String       value) {
        if (value == null || value.isBlank()) return null;
        try {
            Long id = Long.parseLong(value);
            // Busca nas categorias já carregadas pelo ProdutoBean (evita nova query)
            return produtoService.listarCategorias()
                .stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converte a entidade Categoria para String (valor do option no HTML).
     *
     * @param context   contexto JSF
     * @param component componente que disparou a conversão
     * @param value     entidade Categoria
     * @return String com o id, ou "" se null
     */
    @Override
    public String getAsString(FacesContext context,
                               UIComponent  component,
                               Categoria    value) {
        if (value == null || value.getId() == null) return "";
        return value.getId().toString();
    }
}
