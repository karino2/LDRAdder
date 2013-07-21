package org.jarx.android.livedoor.reader;

import java.io.IOException;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

public class ActivityHelper {

    public static final String EXTRA_SUB_ID = "subId";
    public static final String EXTRA_ITEM_ID = "itemId";
    public static final String EXTRA_WHERE= "itemWhere";

    static void bindTitle(Activity activity) {
        Context context = activity.getApplicationContext();
        String loginId = ReaderPreferences.getLoginId(context);
        if (loginId != null) {
            StringBuilder buff = new StringBuilder(64);
            buff.append(activity.getText(R.string.app_name));
            buff.append(" - ");
            buff.append(loginId);
            activity.setTitle(new String(buff));
        }
    }

    static void showToast(Context context, IOException e) {
        e.printStackTrace();
        showToast(context, context.getText(R.string.err_io)
            + " (" + e.getLocalizedMessage() + ")");
    }

    static void showToast(Context context, ReaderException e) {
        e.printStackTrace();
        showToast(context, e.getLocalizedMessage());
    }

    static void showToast(final Context context, final CharSequence text) {
        new Handler().post(new Runnable() {
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static class Where implements java.io.Serializable {

        StringBuilder buff;
        String[] args;

        Where(StringBuilder buff, String[] args) {
            this.buff = buff;
            this.args = args;
        }
    }
}
