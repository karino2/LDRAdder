package org.jarx.android.livedoor.reader;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class NotifierWidget extends AppWidgetProvider {

    private static final String TAG = "NotifierWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        AppWidgetManager awman = AppWidgetManager.getInstance(context);
        int unreadCount = ReaderManager.countUnread(context);
        for (int appWidgetId: appWidgetIds) {
            RemoteViews views = createRemoteViews(context, unreadCount);
            awman.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action != null && (
                action.equals(ReaderService.ACTION_SYNC_SUBS_FINISHED)
                    || action.equals(ReaderService.ACTION_UNREAD_MODIFIED))) {
            int unreadCount = ReaderManager.countUnread(context);
            RemoteViews views = createRemoteViews(context, unreadCount);

            AppWidgetManager awman = AppWidgetManager.getInstance(context);
            ComponentName widget = new ComponentName(context, NotifierWidget.class);
            awman.updateAppWidget(widget, views);
        }
    }

    private RemoteViews createRemoteViews(Context context, int unreadCount) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
            R.layout.notifier);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 0);

        views.setOnClickPendingIntent(R.id.widget, pendingIntent);
        views.setTextViewText(R.id.unread_count, Integer.toString(unreadCount));

        return views;
    }
}
