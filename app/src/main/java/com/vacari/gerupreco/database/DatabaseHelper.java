package com.vacari.gerupreco.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.model.sqlite.Notification;

import java.sql.SQLException;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static final String DATABASE_NAME = "gerupreco_database.db";

    /**
     * v2 acrescenta a tabela do carrinho.
     */
    private static final int DATABASE_VERSION = 2;

    private Dao<Notification, Integer> notificationDAO;
    private Dao<CartItem, Integer> cartItemDAO;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, Notification.class);
            TableUtils.createTable(connectionSource, CartItem.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ao subir de versao a base e recriada. O carrinho e uma lista de compras
     * descartavel e as notificacoes estao desativadas, entao nao ha dado que
     * justifique escrever migracao.
     */
    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            TableUtils.dropTable(connectionSource, Notification.class, true);
            TableUtils.dropTable(connectionSource, CartItem.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Dao<Notification, Integer> getNotificationDAO() throws SQLException {
        if (notificationDAO == null) {
            notificationDAO = DaoManager.createDao(getConnectionSource(), Notification.class);
        }
        return notificationDAO;
    }

    public Dao<CartItem, Integer> getCartItemDAO() throws SQLException {
        if (cartItemDAO == null) {
            cartItemDAO = DaoManager.createDao(getConnectionSource(), CartItem.class);
        }
        return cartItemDAO;
    }
}
