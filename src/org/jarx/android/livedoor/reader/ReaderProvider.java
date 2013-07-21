package org.jarx.android.livedoor.reader;

import java.util.HashMap;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public class ReaderProvider extends ContentProvider {

    public static final String AUTHORITY = "org.jarx.android.livedoor.reader";

    public static final String BEGIN_TXN_URI_NAME
        = "content://" + AUTHORITY + "/begin_txn";
    public static final String SUCCESS_TXN_URI_NAME
        = "content://" + AUTHORITY + "/success_txn";
    public static final String END_TXN_URI_NAME
        = "content://" + AUTHORITY + "/end_txn";
    public static final String SUB_CONTENT_URI_NAME
        = "content://" + AUTHORITY + "/" + Subscription.TABLE_NAME;
    public static final String SUB_FOLDER_CONTENT_URI_NAME
        = SUB_CONTENT_URI_NAME + "/folder";
    public static final String SUB_RATE_CONTENT_URI_NAME
        = SUB_CONTENT_URI_NAME + "/rate";
    public static final String ITEM_CONTENT_URI_NAME
        = "content://" + AUTHORITY + "/" + Item.TABLE_NAME;
    public static final String PIN_CONTENT_URI_NAME
        = "content://" + AUTHORITY + "/" + Pin.TABLE_NAME;

    public static final Uri URI_TXN_BEGIN = Uri.parse(BEGIN_TXN_URI_NAME);
    public static final Uri URI_TXN_SUCCESS = Uri.parse(SUCCESS_TXN_URI_NAME);
    public static final Uri URI_TXN_END = Uri.parse(END_TXN_URI_NAME);

    private static final String TAG = "ReaderProvider";
    private static final String DATABASE_NAME = "reader.db";
    private static final int DATABASE_VERSION = 7;

    private static final String CONTENT_TYPE_ITEM
        = "vnd.android.cursor.item/vnd." + AUTHORITY;
    private static final String CONTENT_TYPE_DIR
        = "vnd.android.cursor.dir/vnd." + AUTHORITY;

    private static final UriMatcher uriMatcher;
    private static final int UM_BEGIN_TXN = 1;
    private static final int UM_SUCCESS_TXN = 2;
    private static final int UM_END_TXN = 3;
    private static final int UM_SUB_ID = 10;
    private static final int UM_SUBS = 11;
    private static final int UM_SUBS_FOLDER = 12;
    private static final int UM_SUBS_RATE = 13;
    private static final int UM_ITEM_ID = 20;
    private static final int UM_ITEMS = 21;
    private static final int UM_PIN_ID = 30;
    private static final int UM_PINS = 31;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "begin_txn", UM_BEGIN_TXN);
        uriMatcher.addURI(AUTHORITY, "success_txn", UM_SUCCESS_TXN);
        uriMatcher.addURI(AUTHORITY, "end_txn", UM_END_TXN);
        uriMatcher.addURI(AUTHORITY,
            Subscription.TABLE_NAME + "/#", UM_SUB_ID);
        uriMatcher.addURI(AUTHORITY,
            Subscription.TABLE_NAME, UM_SUBS);
        uriMatcher.addURI(AUTHORITY,
            Subscription.TABLE_NAME + "/folder", UM_SUBS_FOLDER);
        uriMatcher.addURI(AUTHORITY,
            Subscription.TABLE_NAME + "/rate", UM_SUBS_RATE);
        uriMatcher.addURI(AUTHORITY,
            Item.TABLE_NAME + "/#", UM_ITEM_ID);
        uriMatcher.addURI(AUTHORITY,
            Item.TABLE_NAME, UM_ITEMS);
        uriMatcher.addURI(AUTHORITY,
            Pin.TABLE_NAME + "/#", UM_PIN_ID);
        uriMatcher.addURI(AUTHORITY,
            Pin.TABLE_NAME, UM_PINS);
    }

    static String sqlCreateIndex(String tableName, String columnName) {
        StringBuilder buff = new StringBuilder(128);
        buff.append("create index idx_");
        buff.append(tableName);
        buff.append("_");
        buff.append(columnName);
        buff.append(" on ");
        buff.append(tableName);
        buff.append("(");
        buff.append(columnName);
        buff.append(")");
        return new String(buff);
    }

    static String sqlCreateIndex(String tableName, String indexName,
            String ... columnNames) {
        StringBuilder buff = new StringBuilder(128);
        buff.append("create index ");
        buff.append(indexName);
        buff.append(" on ");
        buff.append(tableName);
        buff.append("(");
        for (int i = 0; i < columnNames.length; i++) {
            if (i > 0) {
                buff.append(", ");
            }
            buff.append(columnNames[i]);
        }
        buff.append(")");
        return new String(buff);
    }

    private static String sqlIdWhere(String id, String where) {
        StringBuilder buff = new StringBuilder(128);
        buff.append(BaseColumns._ID);
        buff.append(" = ");
        buff.append(id);
        if (!TextUtils.isEmpty(where)) {
            buff.append(" and ");
            buff.append(where);
        }
        return new String(buff);
    }

    private static class ReaderOpenHelper extends SQLiteOpenHelper {

        private ReaderOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Subscription.SQL_CREATE_TABLE);
            db.execSQL(Item.SQL_CREATE_TABLE);
            db.execSQL(Pin.SQL_CREATE_TABLE);
            for (String column: Subscription.INDEX_COLUMNS) {
                db.execSQL(sqlCreateIndex(Subscription.TABLE_NAME, column));
            }
            for (String column: Item.INDEX_COLUMNS) {
                db.execSQL(sqlCreateIndex(Item.TABLE_NAME, column));
            }
            for (String column: Pin.INDEX_COLUMNS) {
                db.execSQL(sqlCreateIndex(Pin.TABLE_NAME, column));
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            for (String sql: Subscription.sqlForUpgrade(oldVersion, newVersion)) {
                db.execSQL(sql);
            }
            for (String sql: Item.sqlForUpgrade(oldVersion, newVersion)) {
                db.execSQL(sql);
            }
            for (String sql: Pin.sqlForUpgrade(oldVersion, newVersion)) {
                db.execSQL(sql);
            }
        }
    }

    private ReaderOpenHelper openHelper;

    @Override
    public boolean onCreate() {
        this.openHelper = new ReaderOpenHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
        case UM_SUB_ID:
        case UM_ITEM_ID:
        case UM_PIN_ID:
            return CONTENT_TYPE_ITEM;
        case UM_SUBS:
        case UM_SUBS_FOLDER:
        case UM_SUBS_RATE:
        case UM_ITEMS:
        case UM_PINS:
            return CONTENT_TYPE_DIR;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = this.openHelper.getReadableDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        String groupBy = null;
        String having = null;
        String limit = null;
        if (sortOrder != null) {
            int limitOff = sortOrder.indexOf(" limit ");
            if (limitOff != -1) {
                limit = sortOrder.substring(limitOff + " limit ".length());
                sortOrder = sortOrder.substring(0, limitOff);
            }
        }
        switch (uriMatcher.match(uri)) {
        case UM_BEGIN_TXN:
            db.beginTransaction();
            return null;
        case UM_SUCCESS_TXN:
            db.setTransactionSuccessful();
            return null;
        case UM_END_TXN:
            db.endTransaction();
            return null;
        case UM_SUB_ID:
            if (projection == null) {
                projection = Subscription.DEFAULT_SELECT;
            }
            qb.setTables(Subscription.TABLE_NAME);
            qb.appendWhere(Subscription._ID + " = "
                + uri.getPathSegments().get(1));
            break;
        case UM_SUBS:
            if (projection == null) {
                projection = Subscription.DEFAULT_SELECT;
            }
            qb.setTables(Subscription.TABLE_NAME);
            break;
        case UM_SUBS_FOLDER:
            qb.setTables(Subscription.TABLE_NAME);
            if (projection == null) {
                projection = new String[]{
                    "max(" + Subscription._ID + ") " + Subscription._ID,
                    Integer.toString(Subscription.GROUP_FOLDER),
                    "count(" + Subscription._ID + ")",
                    Subscription._FOLDER,
                    "sum(" + Subscription._UNREAD_COUNT + ")"
                };
            }
            groupBy = Subscription._FOLDER;
            break;
        case UM_SUBS_RATE:
            qb.setTables(Subscription.TABLE_NAME);
            if (projection == null) {
                projection = new String[]{
                    "max(" + Subscription._ID + ") " + Subscription._ID,
                    Integer.toString(Subscription.GROUP_RATE),
                    "count(" + Subscription._ID + ")",
                    Subscription._RATE,
                    "sum(" + Subscription._UNREAD_COUNT + ")"
                };
            }
            groupBy = Subscription._RATE;
            break;
        case UM_ITEM_ID:
            qb.setTables(Item.TABLE_NAME);
            qb.appendWhere(Item._ID + " = "
                + uri.getPathSegments().get(1));
            break;
        case UM_ITEMS:
            qb.setTables(Item.TABLE_NAME);
            // PENDING:
            //limit = "200";
            break;
        case UM_PIN_ID:
            qb.setTables(Pin.TABLE_NAME);
            qb.appendWhere(Pin._ID + " = "
                + uri.getPathSegments().get(1));
            break;
        case UM_PINS:
            qb.setTables(Pin.TABLE_NAME);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        Cursor c = qb.query(db, projection, selection, selectionArgs,
                groupBy, having, sortOrder, limit);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String tableName;
        Uri contentUri;
        switch (uriMatcher.match(uri)) {
        case UM_SUBS:
            tableName = Subscription.TABLE_NAME;
            contentUri = Subscription.CONTENT_URI;
            values.put(Subscription._DISABLED, 0);
            break;
        case UM_ITEMS:
            tableName = Item.TABLE_NAME;
            contentUri = Item.CONTENT_URI;
            break;
        case UM_PINS:
            tableName = Pin.TABLE_NAME;
            contentUri = Pin.CONTENT_URI;
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = openHelper.getWritableDatabase();
        long rowId = db.insert(tableName, tableName, values);
        if (rowId > 0) {
            Uri insertedUri = ContentUris.withAppendedId(contentUri, rowId);
            getContext().getContentResolver().notifyChange(insertedUri, null);
            return insertedUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        return update(uri, null, where, whereArgs, false);
    }

    @Override
    public int update(Uri uri, ContentValues values, String where,
            String[] whereArgs) {
        return update(uri, values, where, whereArgs, true);
    }

    private int update(Uri uri, ContentValues values, String where,
            String[] whereArgs, boolean update) {
        SQLiteDatabase db = this.openHelper.getWritableDatabase();
        String tableName;
        switch (uriMatcher.match(uri)) {
        case UM_SUB_ID:
            tableName = Subscription.TABLE_NAME;
            where = sqlIdWhere(uri.getPathSegments().get(1), where);
            break;
        case UM_SUBS:
            tableName = Subscription.TABLE_NAME;
            break;
        case UM_ITEM_ID:
            tableName = Item.TABLE_NAME;
            where = sqlIdWhere(uri.getPathSegments().get(1), where);
            break;
        case UM_ITEMS:
            tableName = Item.TABLE_NAME;
            break;
        case UM_PIN_ID:
            tableName = Pin.TABLE_NAME;
            where = sqlIdWhere(uri.getPathSegments().get(1), where);
            break;
        case UM_PINS:
            tableName = Pin.TABLE_NAME;
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        int count = update ? db.update(tableName, values, where, whereArgs):
            db.delete(tableName, where, whereArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
