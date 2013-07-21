package org.jarx.android.livedoor.reader;

import java.io.IOException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class ItemActivityHelper extends ActivityHelper {

    public static final int DIALOG_RELOAD = 1;
    public static final int DIALOG_REMOVE = 98;

    public static interface Itemable {
        Activity getActivity();
        Handler getHandler();
        ReaderManager getReaderManager();
        long getSubId();
        Uri getSubUri();
        void initItems();
    }

    static Dialog createDialogReload(final Itemable itemable) {
        final Activity activity = itemable.getActivity();
        final Context context = activity.getApplicationContext();
        return new AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_items_reload_title)
            .setSingleChoiceItems(R.array.dialog_items_reload_items, 0,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        progressSyncItems(itemable, i == 1);
                        dialog.dismiss();
                    }
                }
            ).create();
    }

    static Dialog createDialogRemove(final Itemable itemable) {
        final Activity activity = itemable.getActivity();
        final Context context = activity.getApplicationContext();
        return new AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_items_remove_title)
            .setSingleChoiceItems(R.array.dialog_items_remove_items, 0,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        progressRemoveItems(itemable, i == 0);
                        dialog.dismiss();
                    }
                }
            ).create();
    }

    static void progressSyncItems(final Itemable itemable, final boolean force) {
        final Activity activity = itemable.getActivity();
        final Context context = activity.getApplicationContext();
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setIndeterminate(true);
        dialog.setMessage(activity.getText(R.string.msg_sync_running));
        dialog.show();
        new Thread() {
            public void run() {
                try {
                    ReaderManager rman = itemable.getReaderManager();
                    int syncType = (force) ? ReaderManager.ITEM_SYNC_WITH_READ:
                        ReaderManager.ITEM_SYNC_WITH_READ_IF_NO_UNREAD;
                    rman.syncItems(itemable.getSubId(), syncType);
                } catch (IOException e) {
                    showToast(context, e);
                } catch (ReaderException e) {
                    showToast(context, e);
                }
                itemable.getHandler().post(new Runnable() {
                    public void run() {
                        itemable.initItems();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    static void progressTouchFeedLocal(final Itemable itemable) {
        final Activity activity = itemable.getActivity();
        final Context context = activity.getApplicationContext();
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setIndeterminate(true);
        dialog.setMessage(activity.getText(R.string.msg_touch_running));
        dialog.show();
        new Thread() {
            public void run() {
                ContentResolver cr = activity.getContentResolver();
                ContentValues values = new ContentValues();
                long subId = itemable.getSubId();

                StringBuilder where = new StringBuilder(64);
                where.append(Item._UNREAD).append(" = 1");
                where.append(" and ");
                where.append(Item._SUBSCRIPTION_ID).append(" = ").append(subId);

                values.put(Item._UNREAD, 0);
                cr.update(Item.CONTENT_URI, values, new String(where), null);

                values.clear();
                values.put(Subscription._UNREAD_COUNT, 0);
                cr.update(itemable.getSubUri(), values, null, null);

                itemable.getHandler().post(new Runnable() {
                    public void run() {
                        itemable.initItems();
                        showToast(context, activity.getText(
                            R.string.msg_touch_feed_local));
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    static void progressRemoveItems(final Itemable itemable, final boolean all) {
        final Activity activity = itemable.getActivity();
        final Context context = activity.getApplicationContext();
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setIndeterminate(true);
        dialog.setMessage(activity.getText(R.string.msg_remove_running));
        dialog.show();
        new Thread() {
            public void run() {
                long subId = itemable.getSubId();
                StringBuilder where = new StringBuilder(64);
                where.append(Item._SUBSCRIPTION_ID).append(" = ").append(subId);
                if (!all) {
                    where.append(" and ");
                    where.append(Item._UNREAD).append(" = 0");
                }

                ContentResolver cr = activity.getContentResolver();
                cr.delete(Item.CONTENT_URI, new String(where), null);
                if (all) {
                    ContentValues values = new ContentValues();
                    values.put(Subscription._UNREAD_COUNT, 0);
                    cr.update(itemable.getSubUri(), values, null, null);
                }

                itemable.getHandler().post(new Runnable() {
                    public void run() {
                        showToast(context, activity.getText(
                            R.string.msg_remove_finished));
                        if (all) {
                            activity.sendBroadcast(new Intent(
                                ReaderService.ACTION_UNREAD_MODIFIED));
                        }
                        itemable.initItems();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }
}
