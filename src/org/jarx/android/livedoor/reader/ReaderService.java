package org.jarx.android.livedoor.reader;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ReaderService extends Service {

    public static final String ACTION_SYNC_SUBS_FINISHED
        = "org.jarx.android.livedoor.reader.action.SYNC_SUBS_FINISHED";
    public static final String ACTION_UNREAD_MODIFIED
        = "org.jarx.android.livedoor.reader.action.UNREAD_MODIFIED";

    private static final String TAG = "ReaderService";
    private static final long RMAN_INTERVAL = 30 * 60 * 1000;

    class ReaderBinder extends Binder {

        ReaderService getService() {
            return ReaderService.this;
        }

        ReaderManager getManager() {
            return ReaderService.this.getSharedReaderManager();
        }
    }

    private ReaderManager rman;
    private long rmanExpiredTime;
    private NotificationManager nman;
    private Timer timer;
    private boolean syncRunning;
    private boolean started;
    private MessageFormat syncFinishedFormat;

    @Override
    public void onCreate() {
        super.onCreate();

        this.nman = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        this.syncFinishedFormat = new MessageFormat(
            getText(R.string.msg_sync_finished).toString());

        Context c = getApplicationContext();
        long interval = ReaderPreferences.getSyncInterval(c);
        if (interval > 0) {
            long delay = 0;
            long lastSyncTime = ReaderPreferences.getLastSyncTime(c);
            if (lastSyncTime > 0) {
                long now = System.currentTimeMillis();
                delay = Math.max((lastSyncTime + interval) - now, 0);
            }
            startSyncTimer(delay, interval);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelSyncTimer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ReaderBinder();
    }

    @Override
    public void onRebind(Intent intent) {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ReaderManager rm = this.rman;
        if (rm != null) {
            try {
                rm.logout();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.rman = null;
        return true;
    }

    public ReaderManager getSharedReaderManager() {
        ReaderManager rm = this.rman;
        long now = System.currentTimeMillis();
        if (rm != null) {
            if (this.rmanExpiredTime > 0 && this.rmanExpiredTime < now) {
                // NOTE: no logout
            } else {
                return rm;
            }
        }
        rm = new ReaderManager(getApplicationContext());
        this.rman = rm;
        this.rmanExpiredTime = System.currentTimeMillis() + RMAN_INTERVAL;
        return rm;
    }

    public boolean startSync() {
        long interval = ReaderPreferences.getSyncInterval(getApplicationContext());
        return startSyncTimer(0, interval);
    }

    public synchronized boolean startSyncTimer(long delay, long interval) {
        if (this.syncRunning) {
            return false;
        }
        if (this.timer != null) {
            this.timer.cancel();
        }
        this.timer = new Timer();

        TimerTask timerTask = new TimerTask() {
            public void run() {
                Context context = getApplicationContext();
                ReaderManager rm = ReaderManager.newInstance(context);
                ReaderService.this.setSyncRunning(true);
                try {
                    if (rm.login()) {
                        ReaderPreferences.setLastSyncTime(
                            context, System.currentTimeMillis());
                        ReaderService.this.notifySyncStarted();
                        int syncCount = rm.sync();
                        ReaderService.this.notifySyncFinished(syncCount);
                        rm.logout();
                    }
                } catch (IOException e) {
                    ReaderService.this.notifySyncError(e);
                } catch (ReaderException e) {
                    ReaderService.this.notifySyncError(e);
                } catch (Throwable e) {
                    ReaderService.this.notifySyncError(e);
                } finally {
                    ReaderService.this.setSyncRunning(false);
                }
            }
        };
        if (interval == 0) {
            this.timer.schedule(timerTask, delay);
        } else {
            this.timer.schedule(timerTask, delay, interval);
        }
        return true;
    }

    public synchronized void cancelSyncTimer() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }

    private void setSyncRunning(boolean syncRunning) {
        this.syncRunning = syncRunning;
    }

    private boolean isSyncNotifiable() {
        return ReaderPreferences.isSyncNotifiable(getApplicationContext());
    }

    private void notifySyncStarted() {
        if (!isSyncNotifiable()) {
            return;
        }
        sendNotify(android.R.drawable.stat_notify_sync,
            getText(R.string.msg_sync_started));
    }

    private void notifySyncFinished(int syncCount) {
        if (!isSyncNotifiable()) {
            return;
        }
        Context context = getApplicationContext();
        ReaderManager rm = ReaderManager.newInstance(context);
        int unreadCount = rm.countUnread();
        String msg = this.syncFinishedFormat.format(
            new Integer[]{syncCount, unreadCount});
        sendNotify(android.R.drawable.stat_notify_sync, msg);
    }

    private void notifySyncError(IOException e) {
        e.printStackTrace();
        sendNotify(R.drawable.stat_notify_sync_error,
            getText(R.string.err_io) + "(" + e.getLocalizedMessage() + ")");
    }

    private void notifySyncError(Throwable e) {
        e.printStackTrace();
        sendNotify(R.drawable.stat_notify_sync_error, e.getLocalizedMessage());
    }

    private void sendNotify(int icon, CharSequence message) {
        CharSequence title = getText(R.string.app_name);
        Notification notification = new Notification(
            icon, message, System.currentTimeMillis());
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 0);
        notification.setLatestEventInfo(this, title, message, pendingIntent);
        this.nman.notify(R.layout.sub_list, notification);
    }
}
