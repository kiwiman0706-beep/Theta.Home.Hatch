package jp.yaegaki.theta.home.nas;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Synology NAS（DSM File Station API）への接続と、ネットワーク・Tailscale・NAS の状態。DSBooks と同じ手順：
 * entry.cgi で使える API を問い合わせ → SYNO.API.Auth（v3 まで）でログイン → SYNO.FileStation.List / Download。
 * Tailscale は外部アプリとして扱い、勝手にオンにはしない。VPN が無いだけで Tailscale がオフとは断定しない。
 */
public final class Nas {
    public static final String TAILSCALE = "com.tailscale.ipn";
    public static final int UNSET = 0, CHECKING = 1, OK = 2, UNREACHABLE = 3, AUTH = 4;

    public static final class Status {
        public boolean network, vpn, tailscale;
        public int nas = UNSET;
        public String message = "";
        public long checkedAt;
    }

    public static final class Item {
        public String name, path;
        public boolean dir;
        public long size, modified;
    }

    static final class AuthException extends IOException {
        AuthException(String m) { super(m); }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private JSONObject apis;
    private String sid;

    public Nas(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    public String base() { return prefs.getString("nasUrl", ""); }
    public String account() { return prefs.getString("nasAccount", ""); }
    public boolean configured() { return !base().isEmpty() && !account().isEmpty(); }

    public void save(String url, String account, String password) throws Exception {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        SharedPreferences.Editor e = prefs.edit().putString("nasUrl", u).putString("nasAccount", account.trim());
        if (!password.isEmpty()) e.putString("nasPassword", Secret.seal(password));
        e.apply();
        synchronized (this) { sid = null; apis = null; }
    }

    /** 端末のネットワークと VPN（Tailscale はここに出る）の有無。 */
    public static boolean[] network(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        boolean connected = false, vpn = false;
        if (cm != null) {
            NetworkCapabilities active = cm.getNetworkCapabilities(cm.getActiveNetwork());
            connected = active != null && active.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true;
            }
        }
        return new boolean[]{connected, vpn};
    }

    public static boolean tailscaleInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(TAILSCALE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 状態を調べる。通信するのでメインスレッドでは呼ばない。 */
    public Status check() {
        Status s = new Status();
        boolean[] net = network(context);
        s.network = net[0];
        s.vpn = net[1];
        s.tailscale = tailscaleInstalled(context);
        s.checkedAt = System.currentTimeMillis();
        if (!configured()) {
            s.nas = UNSET;
            s.message = "NAS は未登録です";
            return s;
        }
        try {
            ensureLogin();
            call("SYNO.FileStation.List", "list_share", map("offset", "0", "limit", "1"));
            s.nas = OK;
            s.message = "NAS に接続しています";
        } catch (AuthException e) {
            s.nas = AUTH;
            s.message = "NAS にログインできません（" + e.getMessage() + "）。設定でアカウントを確認してください。";
        } catch (Exception e) {
            s.nas = UNREACHABLE;
            s.message = !s.network ? "ネットワークに接続できません。"
                    : !s.vpn ? "NAS に届きません。Tailscale がオフならオンにしてください。"
                    : "NAS に届きません。Tailscale・NAS の電源・接続先を確認してください。";
        }
        return s;
    }

    /** フォルダの中身。path が空なら共有フォルダの一覧。 */
    public ArrayList<Item> list(String path) throws Exception {
        ensureLogin();
        ArrayList<Item> out = new ArrayList<>();
        boolean root = path == null || path.isEmpty();
        for (int offset = 0; offset < 5000; offset += 500) {
            JSONObject data = root
                    ? call("SYNO.FileStation.List", "list_share", map("offset", "" + offset, "limit", "500", "additional", "[\"time\"]"))
                    : call("SYNO.FileStation.List", "list", map("folder_path", path, "offset", "" + offset, "limit", "500", "additional", "[\"size\",\"time\"]"));
            JSONArray items = data.optJSONArray(root ? "shares" : "files");
            if (items == null) break;
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                Item it = new Item();
                it.name = o.optString("name");
                it.path = o.optString("path");
                it.dir = root || o.optBoolean("isdir");
                JSONObject add = o.optJSONObject("additional");
                if (add != null) {
                    it.size = add.optLong("size");
                    JSONObject time = add.optJSONObject("time");
                    if (time != null) it.modified = time.optLong("mtime") * 1000;
                }
                if (!it.name.startsWith(".") && !it.name.startsWith("#") && !it.name.equals("@eaDir")) out.add(it);
            }
            if (items.length() < 500) break;
        }
        out.sort((a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** NAS のファイルを端末に取得する。 */
    public void download(String path, File dest) throws Exception {
        ensureLogin();
        JSONObject api = api("SYNO.FileStation.Download");
        String url = base() + "/webapi/" + api.optString("path", "entry.cgi");
        String body = form(map("api", "SYNO.FileStation.Download", "version", "" + Math.min(2, api.optInt("maxVersion", 2)),
                "method", "download", "path", new JSONArray().put(path).toString(), "mode", "download", "_sid", sid));
        HttpURLConnection c = post(url, body, 60000);
        String type = c.getContentType();
        if (type != null && type.startsWith("application/json")) {
            JSONObject r = new JSONObject(read(c.getInputStream()));
            throw new IOException("取得できませんでした（" + r.optJSONObject("error") + "）");
        }
        File part = new File(dest.getPath() + ".part");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        if (!part.renameTo(dest)) throw new IOException("保存できませんでした");
    }

    /** NAS が作るサムネイル（SYNO.FileStation.Thumb）。写真・動画のみ。作れないときは null。 */
    public byte[] thumb(String path, boolean large) throws Exception {
        ensureLogin();
        JSONObject api = api("SYNO.FileStation.Thumb");
        String url = base() + "/webapi/" + api.optString("path", "entry.cgi");
        String body = form(map("api", "SYNO.FileStation.Thumb", "version", "" + Math.min(2, api.optInt("maxVersion", 2)),
                "method", "get", "path", path, "size", large ? "medium" : "small", "rotate", "1", "_sid", sid));
        HttpURLConnection c = post(url, body, 15000);
        String type = c.getContentType();
        if (type == null || !type.startsWith("image/")) return null;
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private synchronized void ensureLogin() throws Exception {
        if (sid != null) return;
        String base = base();
        if (base.isEmpty()) throw new IOException("NAS は未登録です");
        JSONObject r = new JSONObject(read(post(base + "/webapi/entry.cgi",
                form(map("api", "SYNO.API.Info", "version", "1", "method", "query", "query", "all")), 8000).getInputStream()));
        apis = r.optJSONObject("data");
        if (apis == null || !apis.has("SYNO.API.Auth")) throw new IOException("DSM の応答ではありません");
        String password = Secret.open(prefs.getString("nasPassword", ""));
        if (password.isEmpty()) throw new AuthException("パスワード未設定");
        JSONObject auth = apis.getJSONObject("SYNO.API.Auth");
        JSONObject login = new JSONObject(read(post(base + "/webapi/" + auth.optString("path", "entry.cgi"),
                form(map("api", "SYNO.API.Auth", "version", "" + Math.min(3, auth.optInt("maxVersion", 3)), "method", "login",
                        "account", account(), "passwd", password, "session", "FileStation", "format", "sid")), 10000).getInputStream()));
        if (!login.optBoolean("success")) {
            int code = login.optJSONObject("error") == null ? 0 : login.getJSONObject("error").optInt("code");
            throw new AuthException(code == 400 ? "アカウントかパスワードが違います" : code == 403 || code == 406 ? "2 段階認証には未対応です" : "エラー " + code);
        }
        sid = login.getJSONObject("data").getString("sid");
    }

    private JSONObject api(String name) throws IOException {
        JSONObject a = apis == null ? null : apis.optJSONObject(name);
        if (a == null) throw new IOException(name + " を確認できません");
        return a;
    }

    private JSONObject call(String apiName, String method, Map<String, String> params) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            JSONObject api = api(apiName);
            Map<String, String> p = new LinkedHashMap<>(params);
            p.put("api", apiName);
            p.put("version", "" + Math.min(2, api.optInt("maxVersion", 2)));
            p.put("method", method);
            p.put("_sid", sid);
            JSONObject r = new JSONObject(read(post(base() + "/webapi/" + api.optString("path", "entry.cgi"), form(p), 15000).getInputStream()));
            if (r.optBoolean("success")) return r.optJSONObject("data") == null ? new JSONObject() : r.getJSONObject("data");
            int code = r.optJSONObject("error") == null ? 0 : r.getJSONObject("error").optInt("code");
            // 105〜107・119 はセッション切れ。1 回だけログインし直す
            if (attempt == 0 && (code == 105 || code == 106 || code == 107 || code == 119)) {
                synchronized (this) { sid = null; }
                ensureLogin();
                continue;
            }
            throw new IOException("DSM エラー " + code);
        }
        throw new IOException("DSM に接続できません");
    }

    private static HttpURLConnection post(String url, String body, int timeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode());
        return c;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String form(Map<String, String> p) throws IOException {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return b.toString();
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
