package org.jarx.android.livedoor.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ReaderPreferences {

    public static final String KEY_LOGIN_ID = "login_id";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SUBS_VIEW = "subs_view";
    public static final String KEY_SUBS_SORT = "subs_sort";
    public static final String KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours";
    public static final String KEY_SYNC_UNREAD_ONLY = "sync_unread_only";
    public static final String KEY_SYNC_NOTIFIABLE = "sync_notifiable";
    public static final String KEY_AUTO_TOUCH_ALL = "auto_touch_all";
    public static final String KEY_VIEW_UNREAD_ONLY = "view_unread_only";
    public static final String KEY_DISABLE_ITEM_LINKS = "disable_item_links";
    public static final String KEY_SHOW_ITEM_CONTROLLS = "show_item_controlls";
    public static final String KEY_ITEM_BODY_FONT_SIZE = "item_body_font_size";
    public static final String KEY_OMIT_ITEM_LIST = "omit_item_list";
    public static final String KEY_LAST_SYNC_TIME = "last_sync_time";

    public static final int SUBS_VIEW_FLAT = 1;
    public static final int SUBS_VIEW_FOLDER = 2;
    public static final int SUBS_VIEW_RATE = 3;
    public static final int SUBS_VIEW_SUBS = 4;

    public static final int SUBS_SORT_MODIFIED_DESC = 1;
    public static final int SUBS_SORT_MODIFIED_ASC = 2;
    public static final int SUBS_SORT_UNREAD_DESC = 3;
    public static final int SUBS_SORT_UNREAD_ASC = 4;
    public static final int SUBS_SORT_TITLE_ASC = 5;
    public static final int SUBS_SORT_RATE_DESC = 6;
    public static final int SUBS_SORT_SUBS_DESC = 7;
    public static final int SUBS_SORT_SUBS_ASC = 8;

    public static SharedPreferences getPreferences(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    public static String getString(Context c, String name) {
        return getPreferences(c).getString(name, null);
    }

    public static int getInt(Context c, String name, int def) {
        try {
            return getPreferences(c).getInt(name, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static long getLong(Context c, String name, long def) {
        return getPreferences(c).getLong(name, def);
    }

    public static boolean getBoolean(Context c, String name, boolean def) {
        return getPreferences(c).getBoolean(name, def);
    }

    public static void putLong(Context c, String name, long value) {
        SharedPreferences sp = getPreferences(c);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(name, value);
        editor.commit();
    }

    public static String getLoginId(Context c) {
        return getString(c, KEY_LOGIN_ID);
    }

    public static String getPassword(Context c) {
        return getString(c, KEY_PASSWORD);
    }

    public static void setLoginIdPassword(Context c, String loginId,
            String password) {
        SharedPreferences sp = getPreferences(c);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(KEY_LOGIN_ID, loginId);
        editor.putString(KEY_PASSWORD, password);
        editor.commit();
    }

    public static int getSubsView(Context c) {
        return getInt(c, KEY_SUBS_VIEW, SUBS_VIEW_FLAT);
    }

    public static void setSubsView(Context c, int subsView) {
        SharedPreferences sp = getPreferences(c);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(KEY_SUBS_VIEW, subsView);
        editor.commit();
    }

    public static int getSubsSort(Context c) {
        return getInt(c, KEY_SUBS_SORT, SUBS_SORT_MODIFIED_DESC);
    }

    public static void setSubsSort(Context c, int subsSort) {
        SharedPreferences sp = getPreferences(c);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(KEY_SUBS_SORT, subsSort);
        editor.commit();
    }

    public static long getSyncInterval(Context c) {
        String h = getString(c, KEY_SYNC_INTERVAL_HOURS);
        int hour = 2;
        if (h != null && h.length() != 0) {
            hour = Integer.parseInt(h);
        }
        return (hour * 60 * 60 * 1000);
    }

    public static boolean isSyncUnreadOnly(Context c) {
        return getBoolean(c, KEY_SYNC_UNREAD_ONLY, true);
    }

    public static boolean isSyncNotifiable(Context c) {
        return getBoolean(c, KEY_SYNC_NOTIFIABLE, true);
    }

    public static boolean isAutoTouchAll(Context c) {
        return getBoolean(c, KEY_AUTO_TOUCH_ALL, false);
    }

    public static boolean isViewUnreadOnly(Context c) {
        return getBoolean(c, KEY_VIEW_UNREAD_ONLY, false);
    }

    public static boolean isDisableItemLinks(Context c) {
        return getBoolean(c, KEY_DISABLE_ITEM_LINKS, false);
    }

    public static boolean isShowItemControlls(Context c) {
        return getBoolean(c, KEY_SHOW_ITEM_CONTROLLS, true);
    }

    public static boolean isOmitItemList(Context c) {
        return getBoolean(c, KEY_OMIT_ITEM_LIST, false);
    }

    public static int getItemBodyFontSize(Context c) {
        String fontSize = getString(c, KEY_ITEM_BODY_FONT_SIZE);
        if (fontSize != null && fontSize.length() != 0) {
            return Integer.parseInt(fontSize);
        } else {
            return 13;
        }
    }

    public static long getLastSyncTime(Context c) {
        return getLong(c, KEY_LAST_SYNC_TIME, 0);
    }

    public static void setLastSyncTime(Context c, long value) {
        putLong(c, KEY_LAST_SYNC_TIME, value);
    }
}
