package org.jarx.android.livedoor.reader;

import java.io.IOException;
import android.app.Activity; 
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class ItemListActivity extends ListActivity
        implements ItemActivityHelper.Itemable {

    private static final String TAG = "ItemListActivity";
    private static final int DIALOG_MOVE = 3;
    private static final int REQUEST_ITEM_ID = 1;
    private static final int REQUEST_PREFERENCES = 1;

    private final Handler handler = new Handler();
    private Uri subUri;
    private Subscription sub;
    private long lastItemId;
    private ItemsAdapter itemsAdapter;
    private ReaderService readerService;
    private ReaderManager readerManager;
    private String keyword;
    private boolean unreadOnly;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            ItemListActivity.this.readerService = binder.getService();
            ItemListActivity.this.readerManager = binder.getManager();
            Subscription s = ItemListActivity.this.sub;
            ItemsAdapter a = ItemListActivity.this.itemsAdapter;
            if (s != null && s.getLastItemId() == 0
                    && a != null && a.getCount() == 0) {
                ItemActivityHelper.progressSyncItems(ItemListActivity.this, false);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            ItemListActivity.this.readerService = null;
            ItemListActivity.this.readerManager = null;
        }
    };

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ItemListActivity.this.initListAdapter();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bindService(new Intent(this, ReaderService.class), this.serviceConn,
            Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ReaderService.ACTION_SYNC_SUBS_FINISHED);
        filter.addAction(ReaderService.ACTION_UNREAD_MODIFIED);
        registerReceiver(this.refreshReceiver, filter);

        setContentView(R.layout.item_list);
        ActivityHelper.bindTitle(this);

        Intent intent = getIntent();
        long subId = intent.getLongExtra(ActivityHelper.EXTRA_SUB_ID, 0);
        this.subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        bindSubTitleView(true);
        ImageView iconView = (ImageView) findViewById(R.id.sub_icon);
        Bitmap icon = sub.getIcon(this);
        if (icon == null) {
            iconView.setImageResource(R.drawable.item_read);
        } else {
            iconView.setImageBitmap(icon);
        }

        final TextView keywordEdit = (TextView) findViewById(R.id.edit_keyword);
        keywordEdit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleSearch(v, keywordEdit);
                    return true;
                }
                return false;
            }
        });
        View search = findViewById(R.id.btn_search);
        search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleSearch(v, keywordEdit);
            }
        });

        initListAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        moveToItemId(this.lastItemId);
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

    private void bindSubTitleView(boolean reloadSub) {
        if (this.subUri == null) {
            return;
        }
        if (reloadSub) {
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(this.subUri, null, null, null, null);
            cursor.moveToFirst();
            this.sub = new Subscription.FilterCursor(cursor).getSubscription();
            cursor.close();
        }
        TextView subTitleView = (TextView) findViewById(R.id.sub_title);
        subTitleView.setText(this.sub.getTitle()
            + " (" + this.sub.getUnreadCount() + ")");
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case ItemActivityHelper.DIALOG_RELOAD:
            return ItemActivityHelper.createDialogReload(this);
        case DIALOG_MOVE:
            return new AlertDialog.Builder(this)
                .setIcon(R.drawable.alert_dialog_icon)
                .setTitle(R.string.dialog_items_move_title)
                .setSingleChoiceItems(R.array.dialog_items_move, 0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            switch (i) {
                            case 0:
                                moveToLastRead();
                                break;
                            case 1:
                                moveToNewUnread();
                                break;
                            case 2:
                                moveToOldUnread();
                                break;
                            }
                            dialog.dismiss();
                        }
                    }
                ).create();
        case ItemActivityHelper.DIALOG_REMOVE:
            return ItemActivityHelper.createDialogRemove(this);
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.menu_item_reload:
            showDialog(ItemActivityHelper.DIALOG_RELOAD);
            return true;
        case R.id.menu_item_touch_feed_local:
            ItemActivityHelper.progressTouchFeedLocal(this);
            return true;
        case R.id.menu_item_move:
            showDialog(DIALOG_MOVE);
            return true;
        case R.id.menu_unreads:
            toggleUnreadOnly(menuItem);
            return true;
        case R.id.menu_search:
            toggleSearchBar();
            return true;
        case R.id.menu_remove:
            showDialog(ItemActivityHelper.DIALOG_REMOVE);
            return true;
        case R.id.menu_item_setting:
            startActivityForResult(new Intent(this, ReaderPreferenceActivity.class),
                REQUEST_PREFERENCES);
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ITEM_ID && data != null) {
            this.lastItemId = data.getLongExtra(ActivityHelper.EXTRA_ITEM_ID, 0);
            bindSubTitleView(true);
            initListAdapter();
            moveToItemId(this.lastItemId);
        } else if (requestCode == REQUEST_PREFERENCES) {
            if (ReaderPreferences.isOmitItemList(this)) {
                finish();
            }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Long itemId = (Long) v.getTag();
        if (itemId != null) {
            this.lastItemId = itemId;
            Intent intent = new Intent(this, ItemActivity.class)
                .putExtra(ActivityHelper.EXTRA_SUB_ID, this.sub.getId())
                .putExtra(ActivityHelper.EXTRA_ITEM_ID, itemId)
                .putExtra(ActivityHelper.EXTRA_WHERE, createBaseWhere());
            startActivityForResult(intent, REQUEST_ITEM_ID);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_SEARCH:
            toggleSearchBar();
            return true;
        case KeyEvent.KEYCODE_BACK:
            View searchBar = findViewById(R.id.search_bar);
            if (searchBar.getVisibility() == View.VISIBLE) {
                toggleSearchBar();
                return true;
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleUnreadOnly(MenuItem menuItem) {
        this.unreadOnly = !this.unreadOnly;
        initListAdapter();
        if (this.unreadOnly) {
            menuItem.setTitle("Show unread");
        } else {
            menuItem.setTitle("Hide unread");
        }
    }

    private void toggleSearchBar() {
        View searchBar = findViewById(R.id.search_bar);
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            if (this.keyword != null) {
                this.keyword = null;
                initListAdapter();
            }
        } else {
            searchBar.setVisibility(View.VISIBLE);
        }
    }

    private void handleSearch(View v, TextView keywordEdit) {
        CharSequence keywordChars = keywordEdit.getText();
        if (keywordChars != null) {
            this.keyword = keywordChars.toString();
            initListAdapter();
        }
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromInputMethod(v.getWindowToken(), 0);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private ActivityHelper.Where createBaseWhere() {
        String keyword = this.keyword;
        String[] args = null;
        StringBuilder buff = new StringBuilder(
            (keyword == null) ? 64: 128 + keyword.length());
        buff.append(Item._SUBSCRIPTION_ID).append(" = ").append(this.sub.getId());
        if (keyword != null && keyword.length() > 0) {
            buff.append(" and (");
            buff.append(Item._TITLE).append(" like ? escape '\\'");
            buff.append(" or ");
            buff.append(Item._BODY).append(" like ? escape '\\'");
            buff.append(")");
            keyword = keyword.replaceAll("\\\\", "\\\\\\\\");
            keyword = keyword.replaceAll("%", "\\%");
            keyword = keyword.replaceAll("_", "\\_");
            keyword = "%" + keyword + "%";
            args = new String[]{keyword, keyword};
        }
        if (this.unreadOnly) {
            buff.append(" and ");
            buff.append(Item._UNREAD).append(" = 1");
        }
        return new ActivityHelper.Where(buff, args);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public Handler getHandler() {
        return this.handler;
    }

    @Override
    public ReaderManager getReaderManager() {
        return this.readerManager;
    }

    @Override
    public long getSubId() {
        return this.sub.getId();
    }

    @Override
    public Uri getSubUri() {
        return this.subUri;
    }

    @Override
    public void initItems() {
        bindSubTitleView(true);
        initListAdapter();
    }

    private void initListAdapter() {
        ActivityHelper.Where where = createBaseWhere();
        String orderby = Item._ID + " desc";
        Cursor cursor = managedQuery(Item.CONTENT_URI, null,
            new String(where.buff), where.args, orderby);;
        if (this.itemsAdapter == null) {
            this.itemsAdapter = new ItemsAdapter(this, cursor);
            setListAdapter(this.itemsAdapter);
        } else {
            this.itemsAdapter.changeCursor(cursor);
        }
    }

    private void moveToItemId(long itemId) {
        if (itemId <= 0) {
            return;
        }
        ActivityHelper.Where where = createBaseWhere();
        where.buff.append(" and ");
        where.buff.append(Item._ID).append(" > ").append(itemId);
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(Item.CONTENT_URI, Item.SELECT_COUNT,
                new String(where.buff), where.args, null);
        cursor.moveToNext();
        int pos = cursor.getInt(0);
        cursor.close();
        getListView().setSelectionFromTop(pos, 48);
    }

    private void moveToLastRead() {
        if (this.lastItemId == 0) {
            this.lastItemId = this.sub.getReadItemId();
        }
        moveToItemId(this.lastItemId);
    }

    private void moveToNewUnread() {
        if (this.itemsAdapter == null) {
            return;
        }
        Item.FilterCursor cursor = this.itemsAdapter.getItemCursor();
        int pos = cursor.getPosition();
        cursor.moveToFirst();
        while (cursor.moveToNext()) {
            if (cursor.isUnread()) {
                getListView().setSelectionFromTop(cursor.getPosition(), 48);
                return;
            }
        }
        cursor.moveToPosition(pos);
    }

    private void moveToOldUnread() {
        if (this.itemsAdapter == null) {
            return;
        }
        if (this.itemsAdapter == null) {
            return;
        }
        Item.FilterCursor cursor = this.itemsAdapter.getItemCursor();
        int pos = cursor.getPosition();
        cursor.moveToLast();
        while (cursor.moveToPrevious()) {
            if (cursor.isUnread()) {
                getListView().setSelectionFromTop(cursor.getPosition(), 48);
                return;
            }
        }
        cursor.moveToPosition(pos);
    }

    private void progressTouchFeedLocal() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_touch_running));
        dialog.show();
        final long subId = this.sub.getId();
        new Thread() {
            public void run() {
                ContentResolver cr = getContentResolver();
                ContentValues values = new ContentValues();

                StringBuilder where = new StringBuilder(64);
                where.append(Item._UNREAD + " = 1");
                where.append(" and ");
                where.append(Item._SUBSCRIPTION_ID + " = " + subId);
                values.put(Item._UNREAD, 0);
                cr.update(Item.CONTENT_URI, values, new String(where), null);

                values.clear();
                values.put(Subscription._UNREAD_COUNT, 0);
                cr.update(ItemListActivity.this.subUri, values, null, null);

                handler.post(new Runnable() {
                    public void run() {
                        initListAdapter();
                        ActivityHelper.showToast(getApplicationContext(),
                            getText(R.string.msg_touch_feed_local));
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private class ItemsAdapter extends ResourceCursorAdapter {

        private ItemsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.item_list_row,
                new Item.FilterCursor(cursor), false);
        }

        private Item.FilterCursor getItemCursor() {
            return (Item.FilterCursor) getCursor();
        }

        private void closeCursor() {
            Cursor cursor = getCursor();
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(new Item.FilterCursor(cursor));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Item.FilterCursor itemCursor = (Item.FilterCursor) cursor;

            ImageView iconView = (ImageView) view.findViewById(R.id.icon_read_unread);
            TextView titleView = (TextView) view.findViewById(R.id.title);
            TextView summaryView = (TextView) view.findViewById(R.id.summary);

            Item item = itemCursor.getItem();
            iconView.setImageResource(item.isUnread()
                ? R.drawable.item_unread: R.drawable.item_read);
            titleView.setText(item.getTitle());
            summaryView.setText(item.getSummary());

            view.setTag(item.getId());
        }

        @Override
        public void onContentChanged() {
            super.onContentChanged();
            ItemListActivity.this.bindSubTitleView(true);
        }
    }
}
