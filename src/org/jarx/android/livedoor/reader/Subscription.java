package org.jarx.android.livedoor.reader;

import java.io.Serializable;
import java.util.Comparator;
import java.util.ArrayList;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcelable;
import android.os.Parcel;
import android.provider.BaseColumns;
import static org.jarx.android.livedoor.reader.Utils.*; 

public final class Subscription implements Serializable, BaseColumns {

    public static final String TABLE_NAME = "subscription";

    public static final Uri CONTENT_URI
        = Uri.parse(ReaderProvider.SUB_CONTENT_URI_NAME);
    public static final Uri FOLDER_CONTENT_URI
        = Uri.parse(ReaderProvider.SUB_FOLDER_CONTENT_URI_NAME);
    public static final Uri RATE_CONTENT_URI
        = Uri.parse(ReaderProvider.SUB_RATE_CONTENT_URI_NAME);

    public static final String _URI = "uri";
    public static final String _TITLE = "title";
    public static final String _ICON_URI = "icon_uri";
    public static final String _ICON = "icon";
    public static final String _RATE = "rate";
    public static final String _SUBSCRIBERS_COUNT = "subscribers_count";
    public static final String _UNREAD_COUNT = "unread_count";
    public static final String _FOLDER = "folder";
    public static final String _MODIFIED_TIME = "modified_time";
    public static final String _ITEM_SYNC_TIME = "item_sync_time";
    // NOTE: database version 5 or later
    public static final String _DISABLED = "disabled";
    public static final String _READ_ITEM_ID = "read_item_id";
    // NOTE: database version 7 or later
    public static final String _LAST_ITEM_ID = "last_item_id";

    public static final String[] DEFAULT_SELECT = {
        _ID, _URI, _TITLE, _RATE, _SUBSCRIBERS_COUNT, _UNREAD_COUNT,
        _FOLDER, _MODIFIED_TIME, _ITEM_SYNC_TIME, _DISABLED,
        _READ_ITEM_ID, _LAST_ITEM_ID
    };
    public static final String[] SELECT_ICON = {_ICON};

    public static final int GROUP_FOLDER = 1;
    public static final int GROUP_RATE = 2;

    public static final String SQL_CREATE_TABLE
        = "create table if not exists " + TABLE_NAME + " ("
        + _ID + " integer primary key, "
        + _URI + " text,"
        + _TITLE + " text,"
        + _ICON_URI + " text,"
        + _ICON + " blob,"
        + _RATE + " integer,"
        + _SUBSCRIBERS_COUNT + " integer,"
        + _UNREAD_COUNT + " integer,"
        + _FOLDER + " text,"
        + _MODIFIED_TIME + " integer,"
        + _ITEM_SYNC_TIME + " integer default 0,"
        + _DISABLED + " integer default 0,"
        + _READ_ITEM_ID + " integer,"
        + _LAST_ITEM_ID + " integer"
        + ")";

    public static final String[] INDEX_COLUMNS = {
        _RATE,
        _SUBSCRIBERS_COUNT,
        _UNREAD_COUNT,
        _FOLDER,
        _MODIFIED_TIME,
        _ITEM_SYNC_TIME,
        _DISABLED
    };

    public static final String[] SORT_ORDERS = {
        _MODIFIED_TIME + " desc",
        _MODIFIED_TIME + " asc",
        _UNREAD_COUNT + " desc",
        _UNREAD_COUNT + " asc",
        _TITLE + " asc",
        _RATE + " desc",
        _SUBSCRIBERS_COUNT + " desc",
        _SUBSCRIBERS_COUNT + " asc"
    };

    public static String[] sqlForUpgrade(int oldVersion, int newVersion) {
        ArrayList<String> sqls = new ArrayList<String>(6);
        if (oldVersion < 5) {
            sqls.add("alter table " + TABLE_NAME
                + " add " + _DISABLED + " integer");
            sqls.add("alter table " + TABLE_NAME
                + " add " + _READ_ITEM_ID + " integer");
            sqls.add(ReaderProvider.sqlCreateIndex(TABLE_NAME, _DISABLED));
        }
        if (oldVersion < 7) {
            sqls.add("alter table " + TABLE_NAME
                + " add " + _LAST_ITEM_ID + " integer");
            sqls.add("update " + TABLE_NAME + " set " + _LAST_ITEM_ID
                + " = (select max(" + Item.TABLE_NAME + "." + Item._ID
                + ") from " + Item.TABLE_NAME + " where "
                + Item.TABLE_NAME + "." + Item._SUBSCRIPTION_ID
                + " = " + TABLE_NAME + "." + _ID + "), "
                + _DISABLED + " = 0");
            sqls.add(ReaderProvider.sqlCreateIndex(TABLE_NAME,
                    "idx_subscription_uc_d",
                    new String[]{_UNREAD_COUNT, _DISABLED}));
            // PENDING: sqlite3 not supported.
            // alter table subscription *modify* disabled integer default 0
        }
        return sqls.toArray(new String[sqls.size()]);
    }

    private long id;
    private String uri;
    private String title;
    private String iconUri;
    private int rate;
    private int subscribersCount;
    private int unreadCount;
    private String folder;
    private Bitmap icon;
    private long modifiedTime;
    private long itemSyncTime;
    private boolean disabled;
    private long readItemId;
    private long lastItemId;

    public Subscription() {
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return this.title = title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIconUri() {
        return this.iconUri;
    }

    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    public Bitmap getIcon(Context context) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CONTENT_URI, this.getId());
        Cursor cursor = cr.query(uri, SELECT_ICON, null, null, null);
        try {
            cursor.moveToFirst();
            byte[] data = cursor.getBlob(0);
            if (data != null) {
                return BitmapFactory.decodeByteArray(
                    data, 0, data.length);
            }
        } catch (OutOfMemoryError e) {
            // NOTE: ignore, display no icon
        } finally {
            cursor.close();
        }
        return null;
    }

    public int getRate() {
        return this.rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public int getSubscribersCount() {
        return this.subscribersCount;
    }

    public void setSubscribersCount(int subscribersCount) {
        this.subscribersCount = subscribersCount;
    }

    public int getUnreadCount() {
        return this.unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String getFolder() {
        return this.folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public long getModifiedTime() {
        return this.modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public long getItemSyncTime() {
        return this.itemSyncTime;
    }

    public void setItemSyncTime(long itemSyncTime) {
        this.itemSyncTime = itemSyncTime;
    }

    public boolean isDisabled() {
        return this.disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public long getReadItemId() {
        return this.readItemId;
    }

    public void setReadItemId(long readItemId) {
        this.readItemId = readItemId;
    }

    public long getLastItemId() {
        return this.lastItemId;
    }

    public void setLastItemId(long lastItemId) {
        this.lastItemId = lastItemId;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Subscription) {
            Subscription s = (Subscription) o;
            return s.getId() == this.getId();
        }
        return false;
    }

    public String toString() {
        return "Subscription{id=" + this.id + ",title=" + this.title + "}";
    }

    public static class FilterCursor extends CursorWrapper {

        private final Subscription sub;
        private final int posId;
        private final int posUri;
        private final int posTitle;
        private final int posRate;
        private final int posSubsCount;
        private final int posUnreadCount;
        private final int posFolder;
        private final int posModifiedTime;
        private final int posItemSyncTime;
        private final int posDisabled;
        private final int posReadItemId;
        private final int posLastItemId;

        public FilterCursor(Cursor cursor) {
            this(cursor, null);
        }

        public FilterCursor(Cursor cursor, Subscription sub) {
            super(cursor);
            this.sub = sub;
            this.posId = getColumnIndex(Subscription._ID);
            this.posUri = getColumnIndex(Subscription._URI);
            this.posTitle = getColumnIndex(Subscription._TITLE);
            this.posRate = getColumnIndex(Subscription._RATE);
            this.posSubsCount = getColumnIndex(Subscription._SUBSCRIBERS_COUNT);
            this.posUnreadCount = getColumnIndex(Subscription._UNREAD_COUNT);
            this.posFolder = getColumnIndex(Subscription._FOLDER);
            this.posModifiedTime = getColumnIndex(Subscription._MODIFIED_TIME);
            this.posItemSyncTime = getColumnIndex(Subscription._ITEM_SYNC_TIME);
            this.posDisabled = getColumnIndex(Subscription._DISABLED);
            this.posReadItemId = getColumnIndex(Subscription._READ_ITEM_ID);
            this.posLastItemId = getColumnIndex(Subscription._LAST_ITEM_ID);
        }

        public Subscription getSubscription() {
            Subscription sub = (this.sub == null) ? new Subscription(): this.sub;
            sub.setId(getLong(this.posId));
            sub.setUri(getString(this.posUri));
            sub.setTitle(getString(this.posTitle));
            sub.setRate(getInt(this.posRate));
            sub.setSubscribersCount(getInt(this.posSubsCount));
            sub.setUnreadCount(getInt(this.posUnreadCount));
            sub.setFolder(getString(this.posFolder));
            sub.setModifiedTime(getLong(this.posModifiedTime));
            sub.setItemSyncTime(getLong(this.posItemSyncTime));
            sub.setDisabled(getInt(this.posDisabled) == 1);
            sub.setReadItemId(getLong(this.posReadItemId));
            sub.setLastItemId(getLong(this.posLastItemId));
            return sub;
        }
    }
}
