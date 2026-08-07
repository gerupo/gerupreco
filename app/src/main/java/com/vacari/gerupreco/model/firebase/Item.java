package com.vacari.gerupreco.model.firebase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown=true)
public class Item {

    private String id;
    private String barCode;
    private String description;
    private String size;
    private String unitMeasure;
    private List<String> tags = new ArrayList<>();

    /**
     * Itens cadastrados antes das tags nao possuem o campo no Firestore,
     * entao a lista pode voltar nula da desserializacao.
     */
    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        return tags;
    }

}
