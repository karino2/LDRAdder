package org.jarx.android.livedoor.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class SubListActivityHelper extends ActivityHelper {

    public static interface SubListable {
        Activity getActivity();
        void initListAdapter();
        ReaderService getReaderService();
        Handler getHandler();
    }

    static final int REQUEST_PREFERENCES = 1;
    static final int DIALOG_VIEW_TYPE = 1;
    static final int DIALOG_SORT_TYPE = 2;
    static final int DIALOG_TOUCH_ALL_LOCAL = 3;
    static final int DIALOG_REMOVE_ITEMS = 4;

    static Dialog onCreateDialog(final SubListable listable, int id) {
        final Activity activity = listable.getActivity();
        final Context context = activity.getApplicationContext();
        switch (id) {
        case DIALOG_VIEW_TYPE:
            int subsViewWhich = ReaderPreferences.getSubsView(context) - 1;
            return new AlertDialog.Builder(activity)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_sub_list_view_title)
                .setSingleChoiceItems(R.array.dialog_sub_list_view, subsViewWhich,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ReaderPreferences.setSubsView(context, which + 1);
                            startSubListActivity(activity, context);
                            activity.finish();
                            dialog.dismiss();
                        }
                    }
                ).create();
        case DIALOG_SORT_TYPE:
            int defaultWhich = ReaderPreferences.getSubsSort(context) - 1;
            return new AlertDialog.Builder(activity)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_sub_list_sort_title)
                .setSingleChoiceItems(R.array.dialog_sub_list_sort, defaultWhich,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ReaderPreferences.setSubsSort(context, which + 1);
                            listable.initListAdapter();
                            dialog.dismiss();
                        }
                    }
                ).create();
        case DIALOG_TOUCH_ALL_LOCAL:
            return new AlertDialog.Builder(activity)
                .setTitle(R.string.txt_reads)
                .setMessage(R.string.msg_confirm_touch_all_local)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        touchAllLocal(listable);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).create();
        case DIALOG_REMOVE_ITEMS:
            return new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_sub_list_remove_items)
                .setSingleChoiceItems(R.array.dialog_sub_list_remove_items, 0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            progressRemoveItems(listable, i == 0);
                            dialog.dismiss();
                        }
                    }
                ).create();
        }
        return null;
    }

    static void startSubListActivity(Activity activity) {
        startSubListActivity(activity, activity.getApplicationContext());
    }

    static void startSubListActivity(Activity activity, Context context) {
        int subsView = ReaderPreferences.getSubsView(context);
        switch (subsView) {
        case 1:
            if (!(activity instanceof SubListActivity)) {
                activity.startActivity(
                    new Intent(activity, SubListActivity.class));
            }
            break;
        default:
            activity.startActivity(new Intent(activity, GroupSubListActivity.class));
        }
    }

    static void startItemActivities(Activity activity, long subId) {
        if (ReaderPreferences.isOmitItemList(activity)) {
            StringBuilder buff = new StringBuilder(128);
            buff.append(Item._SUBSCRIPTION_ID).append(" = ").append(subId);
            Intent intent = new Intent(activity, ItemActivity.class)
                .putExtra(ActivityHelper.EXTRA_SUB_ID, subId)
                .putExtra(ActivityHelper.EXTRA_WHERE, new Where(buff, null));
            activity.startActivity(intent);
        } else {
            activity.startActivity(new Intent(activity, ItemListActivity.class)
                .putExtra(ActivityHelper.EXTRA_SUB_ID, subId));
        }
    }

    static void touchAllLocal(final SubListable listable) {
        final Activity activity = listable.getActivity();
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setIndeterminate(true);
        dialog.setMessage(activity.getText(R.string.msg_running));
        dialog.show();
        new Thread() {
            public void run() {
                ContentResolver cr = activity.getContentResolver();
                ContentValues values = new ContentValues();

                values.put(Item._UNREAD, 0);
                cr.update(Item.CONTENT_URI, values, Item._UNREAD + " = 1", null);

                values.clear();
                values.put(Subscription._UNREAD_COUNT, 0);
                cr.update(Subscription.CONTENT_URI, values,
                    Subscription._UNREAD_COUNT + " <> 0", null);

                listable.getHandler().post(new Runnable() {
                    public void run() {
                        listable.initListAdapter();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    static void progressRemoveItems(final SubListable listable,
            final boolean all) {
        final Activity activity = listable.getActivity();
        final Context context = activity.getApplicationContext();
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setIndeterminate(true);
        dialog.setMessage(activity.getText(R.string.msg_remove_running));
        dialog.show();
        new Thread() {
            public void run() {
                String where = null;
                if (!all) {
                    where = Item._UNREAD + " = 0";
                }
                ContentResolver cr = activity.getContentResolver();
                cr.delete(Item.CONTENT_URI, where, null);
                if (all) {
                    ContentValues values = new ContentValues();
                    values.put(Subscription._UNREAD_COUNT, 0);
                    cr.update(Subscription.CONTENT_URI, values, null, null);
                    context.sendBroadcast(
                        new Intent(ReaderService.ACTION_UNREAD_MODIFIED));
                }
                listable.getHandler().post(new Runnable() {
                    public void run() {
                        showToast(context, activity.getText(
                            R.string.msg_remove_finished));
                        listable.initListAdapter();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }


    static boolean onCreateOptionsMenu(SubListable listable, Menu menu) {
        MenuInflater inflater = listable.getActivity().getMenuInflater();
        inflater.inflate(R.menu.sub_list, menu);
        return true;
    }

    static boolean onOptionsItemSelected(SubListable listable, MenuItem item) {
        final Activity activity = listable.getActivity();
        final Context context = activity.getApplicationContext();
        switch (item.getItemId()) {
        case R.id.menu_item_reload:
            if (listable.getReaderService().startSync()) {
                showToast(context, activity.getText(R.string.msg_sync_started));
            } else {
                showToast(context, activity.getText(R.string.msg_sync_running));
            }
            listable.initListAdapter();
            return true;
        case R.id.menu_item_view_type:
            activity.showDialog(DIALOG_VIEW_TYPE);
            return true;
        case R.id.menu_item_sort_type:
            activity.showDialog(DIALOG_SORT_TYPE);
            return true;
        case R.id.menu_item_touch_all_local:
            activity.showDialog(DIALOG_TOUCH_ALL_LOCAL);
            return true;
        case R.id.menu_item_pin:
            activity.startActivity(new Intent(activity, PinActivity.class));
            return true;
        case R.id.menu_item_remove_items:
            activity.showDialog(DIALOG_REMOVE_ITEMS);
            return true;
        case R.id.menu_item_setting:
            activity.startActivityForResult(new Intent(activity,
                ReaderPreferenceActivity.class), REQUEST_PREFERENCES);
            return true;
        }
        return false;
    }

    static void onActivityResult(SubListable listable, int requestCode,
            int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_PREFERENCES:
            listable.initListAdapter();
            break;
        }
    }
}
