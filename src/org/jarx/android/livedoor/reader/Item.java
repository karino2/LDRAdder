package org.jarx.android.livedoor.reader;

import java.io.Serializable;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import static org.jarx.android.livedoor.reader.Utils.*; 

public class Item implements Serializable, BaseColumns {

    public static final String TABLE_NAME = "item";

    public static final Uri CONTENT_URI
        = Uri.parse(ReaderProvider.ITEM_CONTENT_URI_NAME);

    public static final String[] SELECT_ID = {_ID};
    public static final String[] SELECT_COUNT = {"count(" + _ID + ")"};
    public static final String[] SELECT_MAX_ID = {"max(" + _ID + ")", "count(" + _ID + ")"};
    public static final String[] SELECT_MIN_ID = {"min(" + _ID + ")", "count(" + _ID + ")"};

    public static final String _SUBSCRIPTION_ID = "subscription_id";
    public static final String _URI = "uri";
    public static final String _TITLE = "title";
    public static final String _BODY = "body";
    public static final String _AUTHOR = "author";
    public static final String _UNREAD = "unread";
    public static final String _CREATED_TIME = "created_time";
    public static final String _MODIFIED_TIME = "modified_time";

    public static final String SQL_CREATE_TABLE
        = "create table if not exists " + TABLE_NAME + " ("
        + _ID + " integer primary key,"
        + _SUBSCRIPTION_ID + " integer,"
        + _URI + " text,"
        + _TITLE + " text,"
        + _BODY + " text,"
        + _AUTHOR + " text,"
        + _UNREAD + " integer,"
        + _CREATED_TIME + " integer,"
        + _MODIFIED_TIME + " integer"
        + ")";

    public static final String[] INDEX_COLUMNS = {
        _SUBSCRIPTION_ID,
        _TITLE,
        _UNREAD,
        _CREATED_TIME,
        _MODIFIED_TIME
    };

    public static String[] sqlForUpgrade(int oldVersion, int newVersion) {
        if (oldVersion < 6) {
            return new String[] {
                ReaderProvider.sqlCreateIndex(TABLE_NAME,
                    "idx_item_unread_by_sub_id",
                    new String[]{_SUBSCRIPTION_ID, _UNREAD})
            };
        }
        return new String[0];
    }

    private static final String TAG = "Item";

    private long id;
    private long subscriptionId;
    private String uri;
    private String title;
    private String body;
    private String author;
    private boolean unread;
    private long createdTime;
    private long modifiedTime;

    public Item() {
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSubscriptionId() {
        return this.subscriptionId;
    }

    public void setSubscriptionId(long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSummary() {
        if (this.body == null) {
            return "";
        }
        StringBuilder buff = new StringBuilder(256);
        long time = this.getCreatedOrModifiedTime();
        if (time > 0) {
            buff.append(formatTimeAgo(time));
        }

        String summary = stripWhitespaces(htmlAsPlainText(this.body));
        if (summary != null && summary.length() > 0) {
            if (buff.length() > 0) {
                buff.append(" | ");
            }
            // NOTE: @via twitter 140 / 2 chars
            if (summary.length() <= 70) {
                buff.append(summary);
            } else {
                buff.append(summary.substring(0, 70));
                buff.append("...");
            }
        }
        return new String(buff);
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isUnread() {
        return this.unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }

    public long getCreatedTime() {
        return this.createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getModifiedTime() {
        return this.modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public long getCreatedOrModifiedTime() {
        return Math.max(this.createdTime, this.modifiedTime);
    }

    public static class FilterCursor extends CursorWrapper {

        private final Item item;
        private final int posId;
        private final int posSubscriptionId;
        private final int posUri;
        private final int posTitle;
        private final int posBody;
        private final int posAuthor;
        private final int posUnread;
        private final int posCreatedTime;
        private final int posModifiedTime;

        public FilterCursor(Cursor cursor) {
            this(cursor, null);
        }

        public FilterCursor(Cursor cursor, Item item) {
            super(cursor);
            this.item = item;
            this.posId = getColumnIndex(Item._ID);
            this.posSubscriptionId = getColumnIndex(Item._SUBSCRIPTION_ID);
            this.posUri = getColumnIndex(Item._URI);
            this.posTitle = getColumnIndex(Item._TITLE);
            this.posBody = getColumnIndex(Item._BODY);
            this.posAuthor = getColumnIndex(Item._AUTHOR);
            this.posUnread = getColumnIndex(Item._UNREAD);
            this.posCreatedTime = getColumnIndex(Item._CREATED_TIME);
            this.posModifiedTime = getColumnIndex(Item._MODIFIED_TIME);
        }

        public Item getItem() {
            Item item = (this.item == null) ? new Item(): this.item;
            item.setId(getLong(this.posId));
            item.setSubscriptionId(getLong(this.posSubscriptionId));
            item.setUri(getString(this.posUri));
            item.setTitle(getString(this.posTitle));
            item.setBody(getString(this.posBody));
            item.setAuthor(getString(this.posAuthor));
            item.setUnread(getInt(this.posUnread) == 1);
            item.setCreatedTime(getLong(this.posCreatedTime));
            item.setModifiedTime(getLong(this.posModifiedTime));
            return item;
        }

        public long getId() {
            return getLong(this.posId);
        }

        public boolean isUnread() {
            return (getInt(this.posUnread) == 1);
        }
    }
}
