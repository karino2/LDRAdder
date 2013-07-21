package org.jarx.android.livedoor.reader;

import android.app.Activity; 
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
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
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class SubListActivity extends ListActivity
        implements SubListActivityHelper.SubListable {

    private static final String TAG = "SubListActivity";

    private final Handler handler = new Handler();
    private SubsAdapter subsAdapter;
    private ReaderService readerService;
    private int lastPosition;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            SubListActivity.this.readerService = binder.getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            SubListActivity.this.readerService = null;
        }
    };

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SubListActivity.this.initListAdapter();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sub_list);

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
        if (this.subsAdapter != null
                && this.lastPosition < this.subsAdapter.getCount()) {
            ListView list = getListView();
            list.setSelectionFromTop(this.lastPosition, 48);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
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
    public void onListItemClick(ListView l, View v, int position, long id) {
        Long subId = (Long) v.getTag();
        if (subId != null) {
            this.lastPosition = position;
            SubListActivityHelper.startItemActivities(this, subId);
        }
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
    public synchronized void initListAdapter() {
        this.lastPosition = 0;
        Context context = getApplicationContext();
        StringBuilder where = new StringBuilder(64);
        where.append(Subscription._DISABLED).append(" = 0");
        if (ReaderPreferences.isViewUnreadOnly(context)) {
            where.append(" and ");
            where.append(Subscription._UNREAD_COUNT).append(" > 0");
        }
        int subsSort = ReaderPreferences.getSubsSort(context);
        if (subsSort < 1 || subsSort > Subscription.SORT_ORDERS.length) {
            subsSort = 1;
        }
        String orderby = Subscription.SORT_ORDERS[subsSort - 1];
        Cursor cursor = managedQuery(Subscription.CONTENT_URI, null,
           new String(where), null, orderby);
        if (this.subsAdapter == null) {
            this.subsAdapter = new SubsAdapter(this, cursor);
            setListAdapter(this.subsAdapter);
        } else {
            this.subsAdapter.changeCursor(cursor);
        }
    }

    private class SubsAdapter extends ResourceCursorAdapter {

        private SubsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.sub_list_row,
                new Subscription.FilterCursor(cursor), false);
        }

        private void closeCursor() {
            Cursor cursor = getCursor();
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(new Subscription.FilterCursor(cursor));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Subscription.FilterCursor subCursor = (Subscription.FilterCursor) cursor;

            ImageView iconView = (ImageView) view.findViewById(R.id.icon);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            RatingBar ratingBar = (RatingBar) view.findViewById(R.id.rating_bar);
            TextView etcView = (TextView) view.findViewById(R.id.etc);

            Subscription sub = subCursor.getSubscription();
            titleView.setText(sub.getTitle() + " (" + sub.getUnreadCount() + ")");
            ratingBar.setRating(sub.getRate());
            Bitmap icon = sub.getIcon(SubListActivity.this);
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
