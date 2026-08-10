package com.vacari.gerupreco.model.sqlite;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import lombok.Getter;
import lombok.Setter;

/**
 * Item do carrinho. Guarda uma copia da descricao, tamanho e unidade em vez de
 * so referenciar o produto no Firestore: assim a tela do carrinho monta sem
 * depender de rede, e um produto excluido do catalogo nao deixa o carrinho com
 * uma linha vazia.
 */
@Getter
@Setter
@DatabaseTable(tableName = "cart_item")
public class CartItem {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(index = true)
    private String barCode;

    @DatabaseField
    private String description;

    @DatabaseField
    private String size;

    @DatabaseField
    private String unitMeasure;

    @DatabaseField
    private int quantity;

    @DatabaseField
    private Long createdAt;

}
