package org.jarx.android.livedoor.reader;

import java.io.IOException;
import android.app.Activity; 
import android.app.Dialog; 
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

    private static final String TAG = "LoginActivity";
    private static final int DIALOG_PROGRESS = 1;

    private final Handler handler = new Handler();
    private ProgressDialog progressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String loginId = ReaderPreferences.getLoginId(getApplicationContext());
        String caller = getIntent().getAction();
        if (caller != null && caller.equals(Intent.ACTION_MAIN)) {
            if (loginId != null) {
                startSubscription();
                return;
            }
        }

        if(caller != null && (caller.equals(Intent.ACTION_SEND) && loginId != null)) {
            String url = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if(url != null) {
                Intent intent = new Intent(this, DiscoveryActivity.class);
                intent.putExtra("feedurl", url);
                startActivity(intent);
                finish();
            }
        }

        Window w = getWindow();
        w.requestFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.login);
        w.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.icon);

        setTitle(getText(R.string.login_title));

        final TextView loginIdEdit = (TextView) findViewById(R.id.edit_login_id);
        final TextView passwordEdit = (TextView) findViewById(R.id.edit_password);
        final View loginButton = findViewById(R.id.btn_login);
        final View cancelButton = findViewById(R.id.btn_cancel);

        if (loginId != null) {
            loginIdEdit.setText(loginId);
        }

        loginButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String loginId = loginIdEdit.getText().toString();
                String password = passwordEdit.getText().toString();
                if (loginId.length() == 0 || password.length() == 0) {
                    showToast(getText(R.string.msg_login_fail));
                } else {
                    LoginActivity.this.saveAndSyncIfLogined(loginId, password);
                }
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                LoginActivity.this.finish();
            }
        });

        WebView info = (WebView) findViewById(R.id.info);
        info.loadData(getText(R.string.msg_login_info_html).toString(),
            "text/html", "utf-8");
        WebSettings settings = info.getSettings();
        settings.setDefaultFontSize(11);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_PROGRESS:
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setIndeterminate(true);
            dialog.setMessage(getText(R.string.msg_login_running));
            dialog.setMax(100);
            dialog.setProgress(0);
            this.progressDialog = dialog;
            return dialog;
        }
        return null;
    }

    private void startSubscription() {
        SubListActivityHelper.startSubListActivity(this);
        finish();
    }

    private void saveAndSyncIfLogined(final String loginId, final String password) {
        final Context c = getApplicationContext();
        String curLoginId = ReaderPreferences.getLoginId(c);
        String curPassword = ReaderPreferences.getPassword(c);
        if (curLoginId != null && curLoginId.equals(loginId)
                && curPassword != null && curPassword.equals(password)) {
            finish();
            return;
        }

        showDialog(DIALOG_PROGRESS);

        new Thread() {
            public void run() {
                ReaderManager rm = ReaderManager.newInstance(c);
                boolean success = false;
                try {
                    if (rm.login(loginId, password)) {
                        ReaderPreferences.setLoginIdPassword(c, loginId, password);
                        success = true;
                    } else {
                        showToast(getText(R.string.msg_login_fail));
                    }
                } catch (final IOException e) {
                    showToast(e);
                } catch (final Throwable e) {
                    showToast(e);
                }
                final boolean finish = success;
                handler.post(new Runnable() {
                    public void run() {
                        LoginActivity.this.progressDialog.dismiss();
                        if (finish) {
                            LoginActivity.this.startSubscription();
                        }
                    }
                });
            }
        }.start();
    }

    private void showToast(IOException e) {
        e.printStackTrace();
        showToast(getText(R.string.err_io) + " (" + e.getLocalizedMessage() + ")");
    }

    private void showToast(Throwable e) {
        showToast(e.getLocalizedMessage());
    }

    private void showToast(final CharSequence text) {
        this.handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
