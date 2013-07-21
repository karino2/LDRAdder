package org.jarx.android.livedoor.reader;

import java.io.Serializable;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.provider.BaseColumns;
import static org.jarx.android.livedoor.reader.Utils.*; 

public class Pin implements Cloneable, Serializable, BaseColumns {

    public static final String TABLE_NAME = "pin";

    public static final Uri CONTENT_URI
        = Uri.parse(ReaderProvider.PIN_CONTENT_URI_NAME);

    public static final String _URI = "uri";
    public static final String _TITLE = "title";
    public static final String _ACTION = "action";
    public static final String _CREATED_TIME = "created_time";

    public static final String SQL_CREATE_TABLE
        = "create table if not exists " + TABLE_NAME + " ("
        + _ID + " integer primary key,"
        + _URI + " text,"
        + _TITLE + " text,"
        + _ACTION + " integer,"
        + _CREATED_TIME + " integer"
        + ")";

    public static final String[] INDEX_COLUMNS = {
        _URI,
        _ACTION
    };

    public static final int ACTION_NONE = 0;
    public static final int ACTION_ADD = 1;
    public static final int ACTION_REMOVE = 2;

    public static String[] sqlForUpgrade(int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            return new String[] {
                SQL_CREATE_TABLE,
                ReaderProvider.sqlCreateIndex(TABLE_NAME, _URI),
                ReaderProvider.sqlCreateIndex(TABLE_NAME, _ACTION)
            };
        }
        return new String[0];
    }

    private long id;
    private String uri;
    private String title;
    private int action;
    private long createdTime;

    public Pin() {
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
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getAction() {
        return this.action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public long getCreatedTime() {
        return this.createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError("clone error: " + e);
        }
    }

    public static class FilterCursor extends CursorWrapper {

        private final Pin pin;
        private final int posId;
        private final int posUri;
        private final int posTitle;
        private final int posAction;
        private final int posCreatedTime;

        public FilterCursor(Cursor cursor) {
            this(cursor, null);
        }

        public FilterCursor(Cursor cursor, Pin pin) {
            super(cursor);
            this.pin = pin;
            this.posId = getColumnIndex(Pin._ID);
            this.posUri = getColumnIndex(Pin._URI);
            this.posTitle = getColumnIndex(Pin._TITLE);
            this.posAction = getColumnIndex(Pin._ACTION);
            this.posCreatedTime = getColumnIndex(Pin._CREATED_TIME);
        }

        public Pin getPin() {
            Pin pin = (this.pin == null) ? new Pin(): this.pin;
            pin.setId(getLong(this.posId));
            pin.setUri(getString(this.posUri));
            pin.setTitle(getString(this.posTitle));
            pin.setAction(getInt(this.posAction));
            pin.setCreatedTime(getLong(this.posCreatedTime));
            return pin;
        }
    }
}
