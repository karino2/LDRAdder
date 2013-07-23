package org.jarx.android.livedoor.reader;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import static org.jarx.android.livedoor.reader.Utils.asInt;

public class ApiClient {

    public static final String URL_BASE = "http://reader.livedoor.com";
    public static final String URL_READER = URL_BASE + "/reader/";

    private static final String URL_API_BASE = URL_BASE + "/api";
    private static final String URL_LOGIN = "https://member.livedoor.com/login/index";
    private static final String URL_LOGOUT = URL_BASE + "/reader/logout";
    private static final String URL_API_SUBS = URL_API_BASE + "/subs";
    private static final String URL_API_ALL = URL_API_BASE + "/all";
    private static final String URL_API_UNREAD = URL_API_BASE + "/unread";
    private static final String URL_API_TOUCH_ALL = URL_API_BASE + "/touch_all";
    private static final String URL_API_PIN_ALL = URL_API_BASE + "/pin/all";
    private static final String URL_API_PIN_ADD = URL_API_BASE + "/pin/add";
    private static final String URL_API_PIN_REMOVE = URL_API_BASE + "/pin/remove";
    private static final String URL_API_PIN_CLEAR = URL_API_BASE + "/pin/clear";
    private static final String URL_API_SUBSCRIBE = URL_API_BASE + "/feed/subscribe";
    private static final String URL_API_DISCOVER = "http://rpc.reader.livedoor.com/feed/discover";
    private static final String URL_RPC_NOTIFY = "http://rpc.reader.livedoor.com/notify";

    private final DefaultHttpClient client;
    private String loginId;
    private String password;
    private String apiKey;

    public ApiClient() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        HttpProtocolParams.setUserAgent(params, getClass().getName());

        final SchemeRegistry reg = new SchemeRegistry();
        reg.register(new Scheme("http",
            PlainSocketFactory.getSocketFactory(), 80));
        reg.register(new Scheme("https",
            SSLSocketFactory.getSocketFactory(), 443));

        final ThreadSafeClientConnManager manager
            = new ThreadSafeClientConnManager(params, reg);

        this.client = new DefaultHttpClient(manager, params);
        this.client.getParams().setParameter("http.socket.timeout", 30 * 1000);
    }

    public boolean login(String loginId, String password)
            throws IOException, ReaderException {
        if (loginId == null || password == null) {
            return false;
        }
        if (this.apiKey == null || !this.loginId.equals(loginId)) {
            this.apiKey = null;
            this.loginId = loginId;
            this.password = password;
            initApiKey();
        }
        return isLogined();
    }

    public void logout() {
        this.apiKey = null;
        this.loginId = null;
        this.password = null;
        // PENDING: logout
    }

    public boolean isLogined() {
        return (this.apiKey != null);
    }

    public String getLoginId() {
        return this.loginId;
    }

    private void initApiKey() throws IOException, ReaderException {
        if (this.loginId == null || this.password == null) {
            throw new IllegalStateException("no login info");
        }
        if (this.apiKey != null) {
            return;
        }

        HttpResponse res = this.client.execute(new HttpGet(URL_READER));
        int resStatus = res.getStatusLine().getStatusCode();
        if (resStatus != HttpStatus.SC_OK) {
            throw new IOException("invalid http status " + resStatus);
        }

        List<NameValuePair> params = new ArrayList<NameValuePair>(4);
        params.add(new BasicNameValuePair(".sv", "reader"));
        params.add(new BasicNameValuePair(".next", URL_READER));
        params.add(new BasicNameValuePair("livedoor_id", this.loginId));
        params.add(new BasicNameValuePair("password", this.password));

        BufferedReader in = new BufferedReader(doPostReader(URL_LOGIN, params));
        try {
            boolean logined = false;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.indexOf("var ApiKey") == 0) {
                    logined = true;
                    break;
                }
            }
            if (!logined) {
                throw new ReaderException("login failure");
            }
        } finally {
            in.close();
        }

        List<Cookie> cookies = this.client.getCookieStore().getCookies();
        for (Cookie cookie: cookies) {
            String cookieName = cookie.getName();
            if (cookieName.equals("reader_sid")) {
                this.apiKey = cookie.getValue();
                break;
            }
        }
    }

    /** implements /api/subs */
    public java.io.Reader readSubs(boolean unread, int fromId, int limit)
            throws IOException, ReaderException {
        initApiKey();

        StringBuilder buff = new StringBuilder(URL_API_SUBS.length() + 40);
        buff.append(URL_API_SUBS);
        buff.append("?unread=");
        buff.append(unread ? 1: 0);
        buff.append("&from_id=");
        buff.append(fromId);
        buff.append("&limit=");
        buff.append(limit);

        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));

        return doPostReader(new String(buff), params);
    }

    public void handleSubs(boolean unread, int fromId, int limit, ContentHandler handler)
            throws IOException, ParseException, ReaderException {
        new JSONParser().parse(readSubs(unread, fromId, limit), handler);
    }

    public JSONArray subs(boolean unread, int fromId, int limit)
            throws IOException, ParseException, ReaderException {
        return toJSONArray(readSubs(unread, fromId, limit));
    }

    public JSONArray discoverWithLinks(String url) throws IOException, ParseException {
        return discover(url, "links");
    }

    public JSONArray discoverWithUrl(String url) throws IOException, ParseException {
        return discover(url, "url");
    }

    private JSONArray discover(String url, String urlParamName) throws IOException, ParseException {
        List<NameValuePair> params = new ArrayList<NameValuePair>(2);
        if(!url.endsWith("/")) {
            url = url + "/";
        }
        params.add(new BasicNameValuePair(urlParamName, url));
        params.add(new BasicNameValuePair("format", "json"));
        return toJSONArray(doPostReader(URL_API_DISCOVER, params));
    }

    /** implements /api/all */
    public java.io.Reader readAll(long subId, int offset, int limit)
            throws IOException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(4);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));
        params.add(new BasicNameValuePair("subscribe_id", Long.toString(subId)));
        params.add(new BasicNameValuePair("offset", Integer.toString(offset)));
        params.add(new BasicNameValuePair("limit", Integer.toString(limit)));

        return doPostReader(URL_API_ALL, params);
    }

    public void handleAll(long subId, int offset, int limit, ContentHandler handler)
            throws IOException, ParseException, ReaderException {
        new JSONParser().parse(readAll(subId, offset, limit), handler);
    }

    public JSONArray all(long subId, int offset, int limit)
            throws IOException, ParseException, ReaderException {
        return toJSONArray(readAll(subId, offset, limit));
    }

    /** implements /api/unread */
    public java.io.Reader readUnread(long subId)
            throws IOException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));
        params.add(new BasicNameValuePair("subscribe_id", Long.toString(subId)));

        return doPostReader(URL_API_UNREAD, params);
    }

    public void handleUnread(long subId, ContentHandler handler)
            throws IOException, ParseException, ReaderException {
        new JSONParser().parse(readUnread(subId), handler);
    }

    public JSONArray unread(long subId)
            throws IOException, ParseException, ReaderException {
        JSONObject o = toJSONObject(readUnread(subId));
        return (JSONArray) o.get("items");
    }

    /** implements /api/touch_all */
    public boolean touchAll(long subId)
            throws IOException, ParseException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));
        params.add(new BasicNameValuePair("subscribe_id", Long.toString(subId)));

        JSONObject result = toJSONObject(doPostReader(URL_API_TOUCH_ALL, params));
        int errorCode = asInt(result.get("ErrorCode"));
        int isSuccess = asInt(result.get("isSuccess"));
        return (errorCode == 0 && isSuccess == 1);
    }

    /** implements /api/pin/all */
    public java.io.Reader readPinAll()
            throws IOException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(3);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));

        return doPostReader(URL_API_PIN_ALL, params);
    }

    public void handlePinAll(ContentHandler handler)
            throws IOException, ParseException, ReaderException {
        new JSONParser().parse(readPinAll(), handler);
    }

    /** implements /api/pin/add */
    public boolean pinAdd(String link, String title)
            throws IOException, ParseException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(3);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));
        params.add(new BasicNameValuePair("link", link));
        params.add(new BasicNameValuePair("title", title));

        JSONObject result = toJSONObject(doPostReader(URL_API_PIN_ADD, params));
        int errorCode = asInt(result.get("ErrorCode"));
        int isSuccess = asInt(result.get("isSuccess"));
        return (errorCode == 0 && isSuccess == 1);
    }

    public int subscribe(String link) throws IOException, ReaderException, ParseException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("feedlink", link));
        JSONObject result = toJSONObject(doPostReader(URL_API_SUBSCRIBE, params));

        int errorCode = asInt(result.get("ErrorCode"));
        /*
        int isSuccess = asInt(result.get("isSuccess"));
        return (errorCode == 0 && isSuccess == 1);
        */


        return errorCode;

    }

    /** implements /api/pin/add */
    public boolean pinRemove(String link)
            throws IOException, ParseException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(2);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));
        params.add(new BasicNameValuePair("link", link));

        JSONObject result = toJSONObject(doPostReader(URL_API_PIN_REMOVE, params));
        int errorCode = asInt(result.get("ErrorCode"));
        int isSuccess = asInt(result.get("isSuccess"));
        return (errorCode == 0 && isSuccess == 1);
    }

    /** implements /api/pin/add */
    public boolean pinClear()
            throws IOException, ParseException, ReaderException {
        initApiKey();

        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("apiKey", this.apiKey));

        JSONObject result = toJSONObject(doPostReader(URL_API_PIN_CLEAR, params));
        int errorCode = asInt(result.get("ErrorCode"));
        int isSuccess = asInt(result.get("isSuccess"));
        return (errorCode == 0 && isSuccess == 1);
    }

    public int countUnread() throws IOException {
        if (this.loginId == null) {
            throw new IllegalStateException("no login id");
        }

        StringBuilder buff = new StringBuilder(
            URL_RPC_NOTIFY.length() + this.loginId.length() + 5);
        buff.append(URL_RPC_NOTIFY);
        buff.append("?user=");
        buff.append(this.loginId);

        String result = readString(doGetReader(new String(buff)));
        String[] results = result.split("\\|");
        if (results.length > 1) {
            return Integer.parseInt(results[1]);
        }
        return 0;
    }

    public InputStream doGetInputStream(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        HttpResponse res = this.client.execute(get);
        int resStatus = res.getStatusLine().getStatusCode();
        if (resStatus != HttpStatus.SC_OK) {
            throw new IOException("invalid http status " + resStatus);
        }

        final HttpEntity entity = res.getEntity();
        if (entity == null) {
            throw new IOException("null response entity");
        }

        return new FilterInputStream(entity.getContent()) {
            public void close() throws IOException {
                super.close();
                // entity.consumeContent();
            }
        };
    }

    public InputStream doPostInputStream(String url, List<NameValuePair> params)
            throws IOException {
        HttpPost post = new HttpPost(url);
        post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));

        HttpResponse res = this.client.execute(post);
        int resStatus = res.getStatusLine().getStatusCode();
        if (resStatus != HttpStatus.SC_OK) {
            throw new IOException("invalid http status " + resStatus);
        }

        final HttpEntity entity = res.getEntity();
        if (entity == null) {
            throw new IOException("null response entity");
        }

        return new FilterInputStream(entity.getContent()) {
            public void close() throws IOException {
                super.close();
                // entity.consumeContent();
            }
        };
    }

    public java.io.Reader doGetReader(String url) throws IOException {
        return new InputStreamReader(doGetInputStream(url), HTTP.UTF_8);
    }

    public java.io.Reader doPostReader(String url, List<NameValuePair> params)
            throws IOException {
        return new InputStreamReader(doPostInputStream(url, params), HTTP.UTF_8);
    }

    public static String readString(java.io.Reader in) throws IOException {
        try {
            StringBuilder b = new StringBuilder(2048);
            char[] c = new char[1024];
            int len = in.read(c);
            while (len != -1) {
                b.append(c, 0, len);
                len = in.read(c);
            }
            return new String(b);
        } finally {
            in.close();
        }
    }

    private JSONArray toJSONArray(java.io.Reader in)
            throws IOException, ParseException {
        return (JSONArray) new JSONParser().parse(readString(in));
    }

    private JSONObject toJSONObject(java.io.Reader in)
            throws IOException, ParseException {
        return (JSONObject) new JSONParser().parse(readString(in));
    }
}
