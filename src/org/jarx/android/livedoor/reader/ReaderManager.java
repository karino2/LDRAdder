package org.jarx.android.livedoor.reader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import static org.jarx.android.livedoor.reader.Utils.*; 

public class ReaderManager {

    public static final int API_ALL_LIMIT = 100;
    public static final int ITEM_SYNC_UNREAD_ONLY = 0;
    public static final int ITEM_SYNC_WITH_READ_IF_NO_UNREAD = 1;
    public static final int ITEM_SYNC_WITH_READ = 2;

    private static final String TAG = "ReaderManager";

    public static ReaderManager newInstance(Context context) {
        return new ReaderManager(context);
    }

    private final ApiClient client = new ApiClient();
    private final Context context;

    public ReaderManager(Context context) {
        this.context = context;
    }

    public int sync() throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }

        final boolean unreadOnly = ReaderPreferences.isSyncUnreadOnly(this.context);
        String debugPrefix = "sync " + (unreadOnly ? "unread only": "all");
        Log.d(TAG, debugPrefix + " started.");

        SubsHandler subsHandler = new SubsHandler();
        syncSubs(unreadOnly, subsHandler);

        ReaderException firstError = null;
        int syncCount = 0;
        String subWhere = Subscription._MODIFIED_TIME
            + " <> " + Subscription._ITEM_SYNC_TIME;
        ContentResolver cr = this.context.getContentResolver();
        Subscription.FilterCursor cursor = new Subscription.FilterCursor(
            cr.query(Subscription.CONTENT_URI, null, subWhere, null, null));
        try {
            while (cursor.moveToNext()) {
                Subscription sub = cursor.getSubscription();
                Log.d(TAG, "sync items for subscription " + sub.getUri());
                try {
                    syncCount += syncItems(sub, null, unreadOnly);
                } catch (ReaderException e) {
                    if (firstError == null) {
                        firstError = e;
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } finally {
            cursor.close();
        }

        if (ReaderPreferences.isAutoTouchAll(this.context)) {
            for (long id: subsHandler.ids) {
                try {
                    this.client.touchAll(id);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }

        Log.d(TAG, debugPrefix + " finished.");
        return syncCount;
    }

    public int syncSubs(boolean unreadOnly) throws IOException, ReaderException {
        return syncSubs(unreadOnly, null);
    }

    private int syncSubs(boolean unreadOnly, SubsHandler subsHandler)
            throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }
        if (subsHandler == null) {
            subsHandler = new SubsHandler();
        }

        final int limit = 100;
        int syncCount = 0;
        int len = 0;
        try {
            do {
                this.client.handleSubs(unreadOnly, syncCount, limit, subsHandler);
                len = subsHandler.counter;
                syncCount += len;
            } while (len == limit);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }

        return syncCount;
    }

    public int syncItems(long subId, boolean unreadOnly)
            throws IOException, ReaderException {
        int syncType = (unreadOnly)
            ? ITEM_SYNC_UNREAD_ONLY: ITEM_SYNC_WITH_READ_IF_NO_UNREAD;
        return syncItems(subId, syncType);
    }

    public int syncItems(long subId, int syncType)
            throws IOException, ReaderException {
        Uri subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        ContentResolver cr = this.context.getContentResolver();
        Cursor cursor = cr.query(subUri, null, null, null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return 0;
        }
        Subscription sub = new Subscription.FilterCursor(cursor).getSubscription();
        cursor.close();
        return syncItems(sub, subUri, syncType);
    }

    public int syncItems(Subscription sub, Uri subUri, boolean unreadOnly)
            throws IOException, ReaderException {
        int syncType = (unreadOnly)
            ? ITEM_SYNC_UNREAD_ONLY: ITEM_SYNC_WITH_READ_IF_NO_UNREAD;
        return syncItems(sub, subUri, syncType);
    }

    public int syncItems(Subscription sub, Uri subUri, int syncType)
            throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }
        long subId = sub.getId();
        long subModifiedTime = sub.getModifiedTime();
        if (subUri == null) {
            subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        }
        int syncCount = 0;
        ItemsHandler itemsHandler = new ItemsHandler(subId, sub.getLastItemId());
        try {
            try {
                this.client.handleUnread(subId, itemsHandler);
                syncCount = itemsHandler.counter;
            } catch (IOException e) {
                // NOTE: ignore. if no unread item, server http status 500
            }
            if (syncType == ITEM_SYNC_WITH_READ
                    || (syncCount == 0 && syncType == ITEM_SYNC_WITH_READ_IF_NO_UNREAD)) {
                itemsHandler.unread = false;
                itemsHandler.continueIfExists = (syncType == ITEM_SYNC_WITH_READ);
                this.client.handleAll(subId, syncCount, API_ALL_LIMIT, itemsHandler);
                syncCount += itemsHandler.counter;
            }

            String where = Item._SUBSCRIPTION_ID + " = " + subId
                + " and " + Item._UNREAD + " = 1";
            ContentResolver cr = this.context.getContentResolver();
            Cursor cursor = cr.query(Item.CONTENT_URI, Item.SELECT_COUNT,
                where, null, null);
            cursor.moveToNext();
            int unreadCount = cursor.getInt(0);
            cursor.close();

            ContentValues subValues = new ContentValues();
            subValues.put(Subscription._ITEM_SYNC_TIME, subModifiedTime);
            subValues.put(Subscription._UNREAD_COUNT, unreadCount);
            if (itemsHandler.lastItemId > 0) {
                subValues.put(Subscription._LAST_ITEM_ID, itemsHandler.lastItemId);
            }
            cr.update(subUri, subValues, null, null);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
        return syncCount;
    }

    public int syncPins() throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }

        ContentResolver cr = this.context.getContentResolver();
        cr.query(ReaderProvider.URI_TXN_BEGIN, null, null, null, null);
        try {
            String where = Pin._ACTION + " > " + Pin.ACTION_NONE;
            String order = Pin._ID + " asc";
            Pin.FilterCursor cursor = new Pin.FilterCursor(
                cr.query(Pin.CONTENT_URI, null, where, null, null));
            try {
                while (cursor.moveToNext()) {
                    Pin pin = cursor.getPin();
                    if (pin.getAction() == Pin.ACTION_ADD) {
                        pinAdd(pin.getUri(), pin.getTitle(), false);
                    } else {
                        pinRemove(pin.getUri(), false);
                    }
                    Uri uri = ContentUris.withAppendedId(Pin.CONTENT_URI, pin.getId());
                    cr.delete(uri, null, null);
                }
            } finally {
                cursor.close();
            }

            PinsHandler pinsHandler = new PinsHandler();
            try {
                this.client.handlePinAll(pinsHandler);
            } catch (ParseException e) {
                throw new ReaderException("json parse error", e);
            }
            cr.query(ReaderProvider.URI_TXN_SUCCESS, null, null, null, null);
            return pinsHandler.counter;
        } finally {
            cr.query(ReaderProvider.URI_TXN_END, null, null, null, null);
        }
    }

    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager)
            this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    public boolean login() throws IOException, ReaderException {
        String loginId = ReaderPreferences.getLoginId(this.context);
        String password = ReaderPreferences.getPassword(this.context);
        return login(loginId, password);
    }

    public boolean login(String loginId, String password)
            throws IOException, ReaderException {
        return this.client.login(loginId, password);
    }

    public void logout() throws IOException {
        this.client.logout();
    }

    public boolean isLogined() {
        return this.client.isLogined();
    }

    public String getLoginId() {
        return this.client.getLoginId();
    }

    public int countUnread() {
        return countUnread(this.context);
    }

    public static int countUnread(Context context) {
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(Item.CONTENT_URI, Item.SELECT_COUNT,
            Item._UNREAD + " = 1", null, null);
        try {
            cursor.moveToNext();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    public boolean pinAdd(final String uri, final String title, boolean nowait)
            throws IOException, ReaderException {
        try {
            final ContentResolver cr = this.context.getContentResolver();
            cr.delete(Pin.CONTENT_URI, Pin._URI + " = ? and " + Pin._ACTION 
                + " > " + Pin.ACTION_NONE, new String[]{uri});

            final ContentValues values = new ContentValues();
            values.put(Pin._URI, uri);
            values.put(Pin._TITLE, title);
            values.put(Pin._ACTION, Pin.ACTION_ADD);
            values.put(Pin._CREATED_TIME, (long) (System.currentTimeMillis() / 1000));
            final Uri pinUri = cr.insert(Pin.CONTENT_URI, values);

            if (!isConnected()) {
                return true;
            }

            if (nowait) {
                new Thread() {
                    public void run() {
                        try {
                            runPinAdd(uri, title, cr, pinUri, values);
                        } catch (Exception e) {
                            // NOTE: ignore IOException, ParseException, ReaderException
                        }
                    }
                }.start();
                return true;
            }

            return runPinAdd(uri, title, cr, pinUri, values);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
    }

    private boolean runPinAdd(String uri, String title, ContentResolver cr,
            Uri pinUri, ContentValues values)
            throws IOException, ParseException, ReaderException {
        if (!isLogined()) {
            login();
        }
        boolean success = this.client.pinAdd(uri, title);
        cr.delete(Pin.CONTENT_URI, Pin._URI + " = ? and " + Pin._ACTION
            + " = " + Pin.ACTION_NONE, new String[]{uri});
        values.put(Pin._ACTION, Pin.ACTION_NONE);
        cr.update(pinUri, values, null, null);
        return success;
    }

    public boolean pinRemove(final String uri, boolean nowait)
            throws IOException, ReaderException {
        try {
            final ContentResolver cr = this.context.getContentResolver();
            cr.delete(Pin.CONTENT_URI, Pin._URI + " = ?", new String[]{uri});

            final ContentValues values = new ContentValues();
            values.put(Pin._URI, uri);
            values.put(Pin._ACTION, Pin.ACTION_REMOVE);
            values.put(Pin._CREATED_TIME, (long) (System.currentTimeMillis() / 1000));
            final Uri pinUri = cr.insert(Pin.CONTENT_URI, values);

            if (!isConnected()) {
                return true;
            }

            if (nowait) {
                new Thread() {
                    public void run() {
                        try {
                            runPinRemove(uri, cr, pinUri);
                        } catch (Exception e) {
                            // NOTE: ignore IOException, ParseException, ReaderException
                        }
                    }
                }.start();
                return true;
            }

            return runPinRemove(uri, cr, pinUri);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
    }

    private boolean runPinRemove(String uri, ContentResolver cr, Uri pinUri)
            throws IOException, ParseException, ReaderException {
        if (!isLogined()) {
            login();
        }
        boolean success = this.client.pinRemove(uri);
        cr.delete(pinUri, null, null);
        return success;
    }

    public boolean pinClear() throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }
        try {
            boolean success = this.client.pinClear();
            if (success) {
                ContentResolver cr = this.context.getContentResolver();
                cr.delete(Pin.CONTENT_URI, null, null);
            }
            return success;
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
    }

    private class SubsHandler extends ContentHandlerAdapter {

        private ContentResolver cr;
        private ContentValues values;
        private int unreadCount;
        private int counter;
        private List<Long> ids = new ArrayList<Long>();

        public void startJSON() throws ParseException, IOException {
            this.counter = 0;
            this.cr = ReaderManager.this.context.getContentResolver();
        }

        public boolean startObject() throws ParseException, IOException {
            this.values = new ContentValues();
            this.unreadCount = 0;
            this.counter++;
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            if (this.values != null) {
                long id = this.values.getAsLong(Subscription._ID);
                Uri uri = ContentUris.withAppendedId(Subscription.CONTENT_URI, id);
                if (this.cr.update(uri, this.values, null, null) == 0) {
                    try {
                        bindIcon();
                    } catch (IOException e) {
                        // ignore error for icon
                    }
                    this.values.put(Subscription._UNREAD_COUNT, this.unreadCount);
                    this.cr.insert(Subscription.CONTENT_URI, this.values);
                    ReaderManager.this.context.sendBroadcast(
                        new Intent(ReaderService.ACTION_SYNC_SUBS_FINISHED));
                }
                this.ids.add(id);
                this.values = null;
            }
            return true;
        }

        public boolean primitive(Object value) throws ParseException, IOException {
            if (this.key == null || this.values == null) {
                return true;
            } else if (this.key.equals("subscribe_id")) {
                this.values.put(Subscription._ID, asLong(value));
            } else if (this.key.equals("title")) {
                this.values.put(Subscription._TITLE, asString(value));
            } else if (this.key.equals("icon")) {
                String iconUri = asString(value);
                this.values.put(Subscription._ICON_URI, iconUri);
            } else if (this.key.equals("link")) {
                this.values.put(Subscription._URI, asString(value));
            } else if (this.key.equals("folder")) {
                this.values.put(Subscription._FOLDER, asString(value));
            } else if (this.key.equals("rate")) {
                this.values.put(Subscription._RATE, asInt(value));
            } else if (this.key.equals("unread_count")) {
                this.unreadCount = asInt(value);
            } else if (this.key.equals("subscribers_count")) {
                this.values.put(Subscription._SUBSCRIBERS_COUNT, asInt(value));
            } else if (this.key.equals("modified_on")) {
                this.values.put(Subscription._MODIFIED_TIME, asLong(value));
            }
            return true;
        }

        private void bindIcon() throws IOException {
            String iconUri = this.values.getAsString(Subscription._ICON_URI);
            if (TextUtils.isEmpty(iconUri)) {
                return;
            }
            Bitmap icon = null;
            InputStream in = ReaderManager.this.client.doGetInputStream(iconUri);
            try {
                icon = BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
            int size = icon.getWidth() * icon.getHeight() * 2;
            ByteArrayOutputStream out = new ByteArrayOutputStream(size);
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            this.values.put(Subscription._ICON, out.toByteArray());
        }
    }

    private class ItemsHandler extends ContentHandlerAdapter {

        private final long subId;
        private final long subLastItemId;
        private ContentResolver cr;
        private ContentValues values;
        private boolean startItems;
        private int counter;
        private boolean unread = true;
        private boolean continueIfExists;
        private long lastItemId;

        private ItemsHandler(long subId, long lastItemId) {
            this.subId = subId;
            this.subLastItemId = lastItemId;
        }

        public void startJSON() throws ParseException, IOException {
            this.counter = 0;
            this.cr = ReaderManager.this.context.getContentResolver();
        }

        public boolean startObject() throws ParseException, IOException {
            if (this.startItems) {
                this.values = new ContentValues();
                this.values.put(Item._SUBSCRIPTION_ID, this.subId);
                this.counter++;
            }
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            if (this.startItems) {
                long id = this.values.getAsLong(Item._ID);
                if (!continueIfExists && id <= this.subLastItemId) {
                    return continueIfExists;
                }
                Uri uri = ContentUris.withAppendedId(Item.CONTENT_URI, id);
                Cursor cursor = this.cr.query(uri, null, null, null, null);
                boolean exists = (cursor.getCount() > 0);
                cursor.close();
                if (exists) {
                    return continueIfExists;
                }

                this.values.put(Item._UNREAD, (this.unread ? 1: 0));
                this.cr.insert(Item.CONTENT_URI, this.values);
                this.values = null;

                this.lastItemId = Math.max(this.lastItemId, id);
            }
            return true;
        }

        public boolean startArray() throws ParseException, IOException {
            if (!this.startItems && this.key.equals("items")) {
                this.startItems = true;
            }
            return true;
        }

        public boolean endArray() throws ParseException, IOException {
            if (this.startItems) {
                this.startItems = false;
            }
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            if (this.key == null || this.values == null) {
                return true;
            } else if (this.key.equals("id")) {
                this.values.put(Item._ID, asLong(value));
            } else if (this.key.equals("title")) {
                this.values.put(Item._TITLE, asString(value));
            } else if (this.key.equals("body")) {
                this.values.put(Item._BODY, asString(value));
            } else if (this.key.equals("author")) {
                this.values.put(Item._AUTHOR, asString(value));
            } else if (this.key.equals("link")) {
                this.values.put(Item._URI, asString(value));
            } else if (this.key.equals("created_on")) {
                this.values.put(Item._CREATED_TIME, asLong(value));
            } else if (this.key.equals("modified_on")) {
                this.values.put(Item._MODIFIED_TIME, asLong(value));
            }
            return true;
        }
    }

    private class PinsHandler extends ContentHandlerAdapter {

        private ContentResolver cr;
        private ContentValues values;
        private int counter;

        public void startJSON() throws ParseException, IOException {
            this.counter = 0;
            this.cr = ReaderManager.this.context.getContentResolver();
            cr.delete(Pin.CONTENT_URI, null, null);
        }

        public boolean startObject() throws ParseException, IOException {
            this.values = new ContentValues();
            this.counter++;
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            if (this.values != null) {
                this.values.put(Pin._ACTION, Pin.ACTION_NONE);
                this.cr.insert(Pin.CONTENT_URI, this.values);
                this.values = null;
            }
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            if (this.key == null || this.values == null) {
                return true;
            } else if (this.key.equals("link")) {
                this.values.put(Pin._URI, asString(value));
            } else if (this.key.equals("title")) {
                this.values.put(Pin._TITLE, asString(value));
            } else if (this.key.equals("created_on")) {
                this.values.put(Pin._CREATED_TIME, asLong(value));
            }
            return true;
        }
    }

    private static abstract class ContentHandlerAdapter
            implements ContentHandler {

        protected String key;

        public void startJSON() throws ParseException, IOException {
        }

        public void endJSON() throws ParseException, IOException {
        }

        public boolean startObject() throws ParseException, IOException {
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            return true;
        }

        public boolean startObjectEntry(String key)
                throws ParseException, IOException {
            this.key = key;
            return true;
        }

        public boolean endObjectEntry() throws ParseException, IOException {
            this.key = null;
            return true;
        }

        public boolean startArray() throws ParseException, IOException {
            return true;
        }

        public boolean endArray() throws ParseException, IOException {
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            return true;
        }
    }
}
