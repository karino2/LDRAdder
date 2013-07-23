package org.jarx.android.livedoor.reader;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by karino on 7/22/13.
 */
public class DiscoveryActivity extends Activity {

    final int DIALOG_ID_DISCOVER = 1;
    final int DIALOG_ID_ADD = 2;

    ApiClient client = new ApiClient();

    ProgressDialog dialogDiscover;
    ProgressDialog dialogAdd;

    public boolean login() throws IOException, ReaderException {
        String loginId = ReaderPreferences.getLoginId(this);
        String password = ReaderPreferences.getPassword(this);
        return client.login(loginId, password);
    }

    Handler handler = new Handler();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.discovery);




        findButton(R.id.buttonDiscover).setEnabled(false);

        new Thread() {
            public void run() {
                try {
                    login();
                    handler.post(new Runnable(){
                        @Override
                        public void run() {
                            findButton(R.id.buttonDiscover).setEnabled(true);
                        }
                    });
                } catch (Exception e) {
                    // NOTE: ignore IOException, ParseException, ReaderException
                }
            }
        }.start();

        (findButton(R.id.buttonDiscover)).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                DiscoveryTask task = new DiscoveryTask(new OnFinishListener(){
                    @Override
                    public void done() {
                        onDiscoverResultComing();
                    }
                });
                EditText et = (EditText)findViewById(R.id.editUrl);
                task.execute(et.getText().toString());
            }
        });

        findButton(R.id.buttonAdd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AddLinksTask task = new AddLinksTask(new OnFinishListener() {
                    @Override
                    public void done() {
                        onSubscribeDone();
                    }
                });
                ArrayList<String> urls = new ArrayList<String>();
                ListView lv = findDiscoverListView();
                SparseBooleanArray isChecked = lv.getCheckedItemPositions();
                for(int i = 0; i < lv.getCount(); i++) {
                    if(isChecked.get(i) == true) {
                        urls.add((String) lv.getItemAtPosition(i));
                    }
                }
                task.execute(urls);
            }
        });

        findButton(R.id.buttonAdd).setEnabled(false);
        ListView lv = findDiscoverListView();
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CheckedTextView checkedTextView =
                        (CheckedTextView) view.findViewById(android.R.id.text1);
                if (checkedTextView.isChecked() || findDiscoverListView().getCheckedItemCount() >= 1) {
                    findButton(R.id.buttonAdd).setEnabled(true);
                } else {
                    findButton(R.id.buttonAdd).setEnabled(false);
                }

            }
        });

    }

    private void onSubscribeDone() {
        findDiscoverListView().setAdapter(null);
        findButton(R.id.buttonAdd).setEnabled(false);
        EditText et = (EditText)findViewById(R.id.editUrl);
        et.setText("");
        showMessage("Subscribe done");
    }


    private ListView findDiscoverListView() {
        return (ListView)findViewById(R.id.listViewDiscover);
    }

    Button findButton(int id) {
        return (Button)findViewById(id);
    }

    private void onDiscoverResultComing() {
        if(discoverResult != null) {
            ListView lv = findDiscoverListView();
            lv.setAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, discoverResultToStringList()));
        }
    }

    List<String> discoverResultToStringList() {
        ArrayList<String> res = new ArrayList<String>();
        for(int i = 0; i < discoverResult.size(); i++) {
            String feedlink = (String) ((JSONObject)discoverResult.get(i)).get("feedlink");
            if(feedlink != null)
                res.add(feedlink);
        }
        return res;
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
            case DIALOG_ID_DISCOVER:
                dialogDiscover = new ProgressDialog(this);
                dialogDiscover.setTitle("Auto discovery feeds...");
                return dialogDiscover;
            case DIALOG_ID_ADD:
                dialogAdd = new ProgressDialog(this);
                dialogAdd.setTitle("Add links");
                return dialogAdd;
        }
        return super.onCreateDialog(id);
    }

    JSONArray discoverResult = null;

    interface OnFinishListener {
        void done();
    }
    class DiscoveryTask extends AsyncTask<String, Integer, Boolean> {


        OnFinishListener listener;
        DiscoveryTask(OnFinishListener onFinish) {
            listener = onFinish;
        }

        @Override
        protected void onPreExecute() {
            showDialog(DIALOG_ID_DISCOVER);
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                discoverResult = null;
                JSONArray array = client.discoverWithLinks(urls[0]);
                if(array.size() == 0) {
                    array = client.discoverWithUrl(urls[0]);
                    if(array.size() == 0)
                        return false;
                }
                discoverResult = array;
                /*
                JSONObject hash = (JSONObject)array.get(0);
                */
                // null
                // Object obj = hash.get("hoge");
                // Log.d("lreader", (String)hash.get("feedlink"));
            } catch (IOException e) {
                Log.d("lreader", e.getMessage());
                return false;
            } catch (ParseException e) {
                Log.d("lreader", e.getMessage());
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            if(dialogDiscover != null) {
                dialogDiscover.dismiss();
                dialogDiscover = null;
            }
            if(aBoolean == false) {
                showMessage("FAIL");
                return;
            }
            listener.done();

        }
    }

    void showMessage(String msg) {
        Toast.makeText(DiscoveryActivity.this, msg, Toast.LENGTH_LONG).show();
    }

    class AddLinksTask extends AsyncTask<List<String>, Integer, Boolean> {
        OnFinishListener listener;
        AddLinksTask(OnFinishListener onFinish) {
            listener = onFinish;
        }

        @Override
        protected void onPreExecute() {
            showDialog(DIALOG_ID_ADD);
        }

        @Override
        protected Boolean doInBackground(List<String>... lists) {
            boolean isSuccess = true;
            int count = 0;
            publishProgress(count++);
            for(String url : lists[0]) {
                try {
                    if(!client.subscribe(url)) {
                        isSuccess = false;
                    }
                    publishProgress(count++);
                } catch (IOException e) {
                    showMessage(e.getMessage());
                    isSuccess = false;
                } catch (ReaderException e) {
                    showMessage(e.getMessage());
                    isSuccess = false;
                } catch (ParseException e) {
                    showMessage(e.getMessage());
                    isSuccess = false;
                }
            }
            return isSuccess;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if(dialogAdd != null)
                dialogAdd.setMessage("Add (" + values[0] + ") link...");
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            if(dialogAdd != null) {
                dialogAdd.dismiss();
                dialogAdd = null;
            }
            if(isSuccess == false) {
                showMessage("FAIL");
                return;
            }
            listener.done();
        }
    }

    }