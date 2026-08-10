package com.vacari.gerupreco.repository;

import android.content.Context;

import com.j256.ormlite.dao.Dao;
import com.vacari.gerupreco.database.DatabaseManager;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Carrinho local. Segue o mesmo formato estatico e sincrono do
 * NotificationRepository: a tabela e pequena e a leitura sai na hora.
 */
public class CartRepository {

    private CartRepository() {
    }

    public static List<CartItem> findAll(Context context) {
        try {
            return dao(context).queryBuilder().orderBy("description", true).query();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int countItems(Context context) {
        try {
            return (int) dao(context).countOf();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Soma as quantidades, e nao as linhas: 3x cerveja conta como 3 no contador.
     */
    public static int countUnits(Context context) {
        int units = 0;
        for (CartItem cartItem : findAll(context)) {
            units += cartItem.getQuantity();
        }
        return units;
    }

    /**
     * Produto ja no carrinho apenas soma quantidade, em vez de virar linha nova.
     *
     * @return true se criou uma linha, false se apenas incrementou.
     */
    public static boolean add(Context context, Item item) {
        try {
            CartItem existing = findByBarCode(context, item.getBarCode());

            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + 1);
                dao(context).update(existing);
                return false;
            }

            CartItem cartItem = new CartItem();
            cartItem.setBarCode(item.getBarCode());
            cartItem.setDescription(item.getDescription());
            cartItem.setSize(item.getSize());
            cartItem.setUnitMeasure(item.getUnitMeasure());
            cartItem.setQuantity(1);
            cartItem.setCreatedAt(System.currentTimeMillis());
            dao(context).create(cartItem);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return quantos produtos entraram como linha nova.
     */
    public static int addAll(Context context, List<Item> items) {
        int created = 0;
        for (Item item : items) {
            if (add(context, item)) {
                created++;
            }
        }
        return created;
    }

    public static void updateQuantity(Context context, CartItem cartItem, int quantity) {
        try {
            if (quantity <= 0) {
                dao(context).delete(cartItem);
                return;
            }
            cartItem.setQuantity(quantity);
            dao(context).update(cartItem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void delete(Context context, CartItem cartItem) {
        try {
            dao(context).delete(cartItem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void clear(Context context) {
        try {
            Dao<CartItem, Integer> dao = dao(context);
            dao.delete(dao.deleteBuilder().prepare());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> barCodes(List<CartItem> cartItems) {
        List<String> barCodes = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            if (StringUtil.isNotEmpty(cartItem.getBarCode())) {
                barCodes.add(cartItem.getBarCode());
            }
        }
        return barCodes;
    }

    /**
     * Documento antigo do Firestore pode nao ter o campo, e o ORMLite estoura
     * ao comparar com nulo. Sem codigo de barras nao ha o que casar, entao o
     * produto entra como linha nova.
     */
    private static CartItem findByBarCode(Context context, String barCode) throws Exception {
        if (StringUtil.isEmpty(barCode)) {
            return null;
        }
        return dao(context).queryBuilder().where().eq("barCode", barCode).queryForFirst();
    }

    private static Dao<CartItem, Integer> dao(Context context) throws Exception {
        return DatabaseManager.getDB(context).getCartItemDAO();
    }
}
