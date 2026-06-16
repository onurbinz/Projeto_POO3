package com.erp.catalogo.controller;

import com.erp.catalogo.model.Produto;
import com.erp.catalogo.service.ProdutoService;

import javax.ejb.EJB;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;

/**
 * Converter JSF para a entidade {@link Produto}.
 *
 * <h3>Contexto de uso:</h3>
 * <p>Obrigatório para o {@code <p:autoComplete>} do PDV que usa
 * {@code itemValue="#{prod}"} — o JSF precisa converter o objeto
 * Produto para String (id) e de volta para a entidade ao submeter o form.</p>
 *
 * <h3>Fluxo:</h3>
 * <pre>
 *   getAsString(): Produto → "42"          (renderização do HTML)
 *   getAsObject(): "42"    → Produto{id=42} (recebimento do submit)
 * </pre>
 *
 * <p>{@code managed = true} permite injeção de {@code @EJB} no converter.</p>
 *
 * @see ProdutoService
 */
@FacesConverter(value = "produtoConverter", managed = true)
public class ProdutoConverter implements Converter<Produto> {

    @EJB
    private ProdutoService produtoService;

    @Override
    public Produto getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return produtoService.buscarPorId(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, Produto value) {
        if (value == null || value.getId() == null) return "";
        return value.getId().toString();
    }
}
