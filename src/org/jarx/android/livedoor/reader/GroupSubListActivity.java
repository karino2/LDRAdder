package org.jarx.android.livedoor.reader;

import android.app.Activity; 
import android.app.Dialog;
import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ResourceCursorTreeAdapter;
import android.widget.TextView;

public class GroupSubListActivity extends ExpandableListActivity
        implements SubListActivityHelper.SubListable {

    private static final String TAG = "GroupSubListActivity";
    private static final int REQUEST_PREFERENCES = 1;
    private static final int DIALOG_SUBS_VIEW = 1;
    private static final int DIALOG_SUBS_SORT = 2;

    private final Handler handler = new Handler();
    private SubsAdapter subsAdapter;
    private ReaderService readerService;
    private int lastGroupPosition = -1;
    private int lastChildPosition = -1;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            GroupSubListActivity.this.readerService = binder.getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            GroupSubListActivity.this.readerService = null;
        }
    };

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            GroupSubListActivity.this.initListAdapter();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sub_group_list);

        bindService(new Intent(this, ReaderService.class),
            this.serviceConn, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ReaderService.ACTION_SYNC_SUBS_FINISHED);
        filter.addAction(ReaderService.ACTION_UNREAD_MODIFIED);
        registerReceiver(this.refreshReceiver, filter);

        ActivityHelper.bindTitle(this);
        initListAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        int groupPosition = this.lastGroupPosition;
        int childPosition = this.lastChildPosition;
        if (this.subsAdapter != null
                && groupPosition >= 0
                && groupPosition < this.subsAdapter.getGroupCount()) {
            ExpandableListView list = getExpandableListView();
            list.expandGroup(groupPosition);
            if (this.subsAdapter.isChildSelectable(groupPosition, childPosition)) {
                list.setSelectedChild(groupPosition, childPosition, true);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this.serviceConn);
        unregisterReceiver(this.refreshReceiver);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        return SubListActivityHelper.onCreateDialog(this, id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return SubListActivityHelper.onCreateOptionsMenu(this, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return SubListActivityHelper.onOptionsItemSelected(this, item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SubListActivityHelper.onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            // NOTE: ignore search
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public ReaderService getReaderService() {
        return this.readerService;
    }

    @Override
    public Handler getHandler() {
        return this.handler;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        Long subId = (Long) v.getTag();
        if (subId != null) {
            this.lastGroupPosition = groupPosition;
            this.lastChildPosition = childPosition;
            SubListActivityHelper.startItemActivities(this, subId);
        }
        return true;
    }

    @Override
    public synchronized void initListAdapter() {
        this.lastGroupPosition = -1;
        this.lastChildPosition = -1;

        Context context = getApplicationContext();
        StringBuilder where = new StringBuilder(64);
        where.append(Subscription._DISABLED).append(" = 0");
        if (ReaderPreferences.isViewUnreadOnly(context)) {
            where.append(" and ");
            where.append(Subscription._UNREAD_COUNT).append(" > 0");
        }
        String orderby;
        Uri uri;
        int subsView = ReaderPreferences.getSubsView(context);
        if (subsView == 2) {
            uri = Subscription.FOLDER_CONTENT_URI;
            orderby = Subscription._FOLDER + " asc";
        } else {
            uri = Subscription.RATE_CONTENT_URI;
            orderby = Subscription._RATE + " desc";
        }
        Cursor cursor = managedQuery(uri, null, new String(where), null, orderby);
        if (this.subsAdapter == null) {
            this.subsAdapter = new SubsAdapter(this, cursor);
            setListAdapter(this.subsAdapter);
        } else {
            this.subsAdapter.changeCursor(cursor);
        }
    }

    private class SubsAdapter extends ResourceCursorTreeAdapter {

        private SubsAdapter(Context context, Cursor cursor) {
            super(context, cursor, R.layout.sub_group_list_row, R.layout.sub_list_row);
        }

        @Override
        public Cursor getChildrenCursor(Cursor groupCursor) {
            Context context = getApplicationContext();
            String where = "";
            String[] whereArgs = null;
            if (ReaderPreferences.isViewUnreadOnly(context)) {
                where = Subscription._UNREAD_COUNT + " > 0 and ";
            }
            int subsSort = ReaderPreferences.getSubsSort(context);
            if (subsSort < 1 || subsSort > Subscription.SORT_ORDERS.length) {
                subsSort = 1;
            }
            String orderby = Subscription.SORT_ORDERS[subsSort - 1];
            final int group = groupCursor.getInt(1);
            switch (group) {
            case Subscription.GROUP_FOLDER:
                where += Subscription._FOLDER + " = ?";
                whereArgs = new String[]{groupCursor.getString(3)};
                break;
            case Subscription.GROUP_RATE:
                where += Subscription._RATE + " = ?";
                whereArgs = new String[]{groupCursor.getString(3)};
                break;
            default:
                return null;
            }
            return new Subscription.FilterCursor(managedQuery(
                Subscription.CONTENT_URI, null, where, whereArgs, orderby));
        }

        @Override
        public void bindGroupView(View view, Context context, Cursor cursor,
                boolean isExpanded) {
            View textLayout = view.findViewById(R.id.text_layout);
            View ratingBarLayout = view.findViewById(R.id.rating_bar_layout);

            final int group = cursor.getInt(1);
            switch (group) {
            case Subscription.GROUP_FOLDER: {
                if (textLayout.getVisibility() != View.VISIBLE) {
                    textLayout.setVisibility(View.VISIBLE);
                }
                if (ratingBarLayout.getVisibility() != View.GONE) {
                    ratingBarLayout.setVisibility(View.GONE);
                }
                TextView titleView = (TextView) view.findViewById(R.id.title);
                CharSequence title = cursor.getString(3);
                if (title == null || title.length() == 0) {
                    title = getText(R.string.txt_no_folder);
                }
                StringBuilder buff = new StringBuilder(title.length() + 32);
                buff.append(title).append(" (");
                buff.append(cursor.getInt(2)).append(" feeds, ");
                buff.append(cursor.getInt(4)).append(" unreads)");
                titleView.setText(new String(buff));
                break;
            } case Subscription.GROUP_RATE: {
                if (textLayout.getVisibility() != View.GONE) {
                    textLayout.setVisibility(View.GONE);
                }
                if (ratingBarLayout.getVisibility() != View.VISIBLE) {
                    ratingBarLayout.setVisibility(View.VISIBLE);
                }
                RatingBar ratingBar = (RatingBar) view.findViewById(R.id.rating_bar);
                TextView countView = (TextView) view.findViewById(R.id.count);
                ratingBar.setRating(cursor.getInt(3));
                StringBuilder buff = new StringBuilder(32);
                buff.append(" (");
                buff.append(cursor.getInt(2)).append(" feeds, ");
                buff.append(cursor.getInt(4)).append(" unreads)");
                countView.setText(new String(buff));
                break;
            }}
        }

        @Override
        public void bindChildView(View view, Context context, Cursor cursor,
                boolean isLastChild) {
            Subscription.FilterCursor subCursor = (Subscription.FilterCursor) cursor;
            Subscription sub = subCursor.getSubscription();

            ImageView iconView = (ImageView) view.findViewById(R.id.icon);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            RatingBar ratingBar = (RatingBar) view.findViewById(R.id.rating_bar);
            TextView etcView = (TextView) view.findViewById(R.id.etc);

            titleView.setText(sub.getTitle() + " (" + sub.getUnreadCount() + ")");
            ratingBar.setRating(sub.getRate());
            Bitmap icon = sub.getIcon(GroupSubListActivity.this);
            if (icon == null) {
                iconView.setImageResource(R.drawable.item_read);
            } else {
                iconView.setImageBitmap(icon);
            }

            StringBuilder buff = new StringBuilder(64);
            buff.append(sub.getSubscribersCount());
            buff.append(" users");
            String folder = sub.getFolder();
            if (folder != null && folder.length() > 0) {
                buff.append(" | ");
                buff.append(folder);
            }
            etcView.setText(new String(buff));

            view.setTag(sub.getId());
        }
    }
}
