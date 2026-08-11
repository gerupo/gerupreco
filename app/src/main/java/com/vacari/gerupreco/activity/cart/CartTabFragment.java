package com.vacari.gerupreco.activity.cart;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.vacari.gerupreco.model.sqlite.CartItem;

import java.util.List;

/**
 * Base das abas do comparador do carrinho.
 *
 * Os precos sao consultados uma unica vez pela CartCompareActivity e ficam com
 * ela; a aba so le e ordena. Por isso a aba se registra no host ao anexar -
 * assim o host redesenha as abas vivas quando a consulta volta ou o chip de
 * janela muda, sem depender das tags internas do FragmentStateAdapter.
 */
public abstract class CartTabFragment extends Fragment {

    private CartCompareActivity host;

    protected CartTabFragment(@LayoutRes int layout) {
        super(layout);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        host = (CartCompareActivity) context;
        host.registerTab(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initGUI(view);
        // A segunda aba so e criada ao deslizar, quando os precos ja chegaram;
        // sem este render ela abriria vazia ate o proximo toque num chip.
        render();
    }

    @Override
    public void onDetach() {
        if (host != null) {
            host.unregisterTab(this);
            host = null;
        }
        super.onDetach();
    }

    protected CartCompareActivity host() {
        return host;
    }

    /**
     * Redesenha com o que o host tem agora. Enquanto a consulta nao volta, o
     * mapa de precos esta vazio - o host chama de novo quando ela termina.
     */
    void render() {
        View view = getView();
        if (view == null || host == null) {
            return;
        }
        render(view);
    }

    protected abstract void initGUI(@NonNull View view);

    protected abstract void render(@NonNull View view);

    /**
     * Card de aviso com os produtos que ficaram de fora do calculo. As duas
     * abas tem avisos de mesmo desenho, so muda o texto no plural.
     */
    protected void renderWarning(@NonNull View view, @IdRes int cardId, @IdRes int textId,
                                 List<CartItem> items, @PluralsRes int plural) {
        MaterialCardView card = view.findViewById(cardId);

        if (items.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }

        StringBuilder names = new StringBuilder();
        for (CartItem cartItem : items) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(cartItem.getDescription());
        }

        TextView text = view.findViewById(textId);
        text.setText(getResources().getQuantityString(plural, items.size(),
                items.size(), names.toString()));
        card.setVisibility(View.VISIBLE);
    }
}
