package com.erp.catalogo.controller;

import com.erp.catalogo.service.ProdutoService;
import com.erp.compras.model.Fornecedor;

import javax.ejb.EJB;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;

/**
 * Converter JSF para a entidade {@link Fornecedor}.
 *
 * <p>Mesma lógica do {@link CategoriaConverter} — necessário para que
 * o {@code <p:selectOneMenu>} de fornecedores funcione corretamente.</p>
 *
 * <p>O fornecedor é opcional para serviços (ex: "Corte Degradê" não tem
 * fornecedor). Por isso {@code getAsObject} retorna {@code null} quando
 * o value é vazio — não lança erro.</p>
 *
 * @see CategoriaConverter
 */
@FacesConverter(value = "fornecedorConverter", managed = true)
public class FornecedorConverter implements Converter<Fornecedor> {

    @EJB
    private ProdutoService produtoService;

    @Override
    public Fornecedor getAsObject(FacesContext context,
                                   UIComponent  component,
                                   String       value) {
        if (value == null || value.isBlank()) return null; // Fornecedor é opcional
        try {
            Long id = Long.parseLong(value);
            return produtoService.listarFornecedores()
                .stream()
                .filter(f -> f.getId().equals(id))
                .findFirst()
                .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext context,
                               UIComponent  component,
                               Fornecedor   value) {
        if (value == null || value.getId() == null) return "";
        return value.getId().toString();
    }
}
