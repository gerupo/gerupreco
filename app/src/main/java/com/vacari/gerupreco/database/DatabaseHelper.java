package com.vacari.gerupreco.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.vacari.gerupreco.model.sqlite.CartItem;

import java.sql.SQLException;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static final String DATABASE_NAME = "gerupreco_database.db";

    /**
     * v2 acrescenta a tabela do carrinho.
     *
     * A tabela "notification", do preco-alvo em SQLite que nunca chegou a
     * funcionar, saiu daqui sem subir a versao de proposito: o onUpgrade abaixo
     * recria a base do zero, e subir a versao so para apagar uma tabela morta
     * levaria o carrinho do usuario junto. Em bases antigas ela fica para tras
     * sem ninguem consultando. O rastreamento que a substituiu vive no
     * Firestore, porque quem le aquela lista e a rotina no servidor.
     */
    private static final int DATABASE_VERSION = 2;

    private Dao<CartItem, Integer> cartItemDAO;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, CartItem.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ao subir de versao a base e recriada. O carrinho e uma lista de compras
     * descartavel, entao nao ha dado que justifique escrever migracao.
     */
    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            TableUtils.dropTable(connectionSource, CartItem.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Dao<CartItem, Integer> getCartItemDAO() throws SQLException {
        if (cartItemDAO == null) {
            cartItemDAO = DaoManager.createDao(getConnectionSource(), CartItem.class);
        }
        return cartItemDAO;
    }
}
