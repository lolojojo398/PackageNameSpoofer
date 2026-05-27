package com.spoofer.har;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import com.spoofer.har.vpn.HarVpnService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int FILE_REQ = 200;
    private static final int VPN_REQ = 100;
    private ValueCallback<Uri[]> fileCb;
    private boolean vpnOn = false;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.webView = new WebView(this);
        setContentView(this.webView);
        WebSettings ws = this.webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMixedContentMode(0);
        this.webView.setWebViewClient(new WebViewClient());
        this.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params) {
                if (MainActivity.this.fileCb != null) {
                    MainActivity.this.fileCb.onReceiveValue(null);
                }
                MainActivity.this.fileCb = cb;
                try {
                    MainActivity.this.startActivityForResult(params.createIntent(), FILE_REQ);
                    return true;
                } catch (Exception e) {
                    try {
                        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                        fallback.addCategory(Intent.CATEGORY_OPENABLE);
                        fallback.setType("*/*");
                        MainActivity.this.startActivityForResult(Intent.createChooser(fallback, "选择HAR文件"), FILE_REQ);
                        return true;
                    } catch (Exception e2) {
                        MainActivity.this.fileCb = null;
                        Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
        });
        this.webView.addJavascriptInterface(new Bridge(), "Android");
        this.webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (this.webView != null && this.webView.canGoBack()) {
            this.webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == VPN_REQ) {
            if (res != RESULT_OK) {
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
                return;
            } else {
                doStartVpn();
                return;
            }
        }
        if (req == FILE_REQ) {
            if (res == RESULT_OK && data != null) {
                Uri uri = null;
                if (data.getData() != null) {
                    uri = data.getData();
                } else if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                    uri = data.getClipData().getItemAt(0).getUri();
                }
                if (uri != null) {
                    final Uri fileUri = uri;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            readFileAndNotify(fileUri);
                        }
                    }).start();
                }
            }
            if (this.fileCb != null) {
                this.fileCb.onReceiveValue(null);
                this.fileCb = null;
            }
        }
    }

    private void readFileAndNotify(Uri fileUri) {
        try {
            InputStream is = getContentResolver().openInputStream(fileUri);
            String content = readStream(is);
            is.close();
            FileOutputStream fos = openFileOutput("default.har", 0);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            final String js = "onFileLoaded('" + escapeJs(content) + "')";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(js, null);
                }
            });
        } catch (Exception e) {
            final String err = e.getMessage();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "读取文件失败: " + err, Toast.LENGTH_LONG).show();
                    webView.evaluateJavascript("onFileError('读取文件失败: " + err + "')", null);
                }
            });
        }
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void doStartVpn() {
        try {
            startService(new Intent(this, HarVpnService.class));
            this.vpnOn = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("onVpnStateChanged(true)", null);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "VPN failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopVpn() {
        try {
            stopService(new Intent(this, HarVpnService.class));
            this.vpnOn = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("onVpnStateChanged(false)", null);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "VPN stop failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String readStream(InputStream is) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toString("UTF-8");
    }

    public class Bridge {
        @JavascriptInterface
        public void startVpn() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent vi = VpnService.prepare(MainActivity.this);
                        if (vi != null) {
                            MainActivity.this.startActivityForResult(vi, VPN_REQ);
                        } else {
                            MainActivity.this.doStartVpn();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "VPN error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void stopVpn() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.stopVpn();
                }
            });
        }

        @JavascriptInterface
        public boolean isVpnRunning() {
            return MainActivity.this.vpnOn;
        }

        @JavascriptInterface
        public String loadDefaultHar() {
            try {
                File f = new File(MainActivity.this.getFilesDir(), "default.har");
                if (!f.exists()) {
                    return "{\"error\":\"No HAR file. Import one first.\"}";
                }
                FileInputStream fis = new FileInputStream(f);
                String s = MainActivity.this.readStream(fis);
                fis.close();
                return s;
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public String saveHar(String content) {
            try {
                FileOutputStream fos = MainActivity.this.openFileOutput("default.har", 0);
                fos.write(content.getBytes("UTF-8"));
                fos.close();
                return "{\"success\":true}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // ===== CURL support =====
        @JavascriptInterface
        public String saveCurl(String content) {
            try {
                FileOutputStream fos = MainActivity.this.openFileOutput("saved_curl.txt", 0);
                fos.write(content.getBytes("UTF-8"));
                fos.close();
                return "{\"success\":true}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public String loadCurl() {
            try {
                File f = new File(MainActivity.this.getFilesDir(), "saved_curl.txt");
                if (!f.exists()) {
                    return "";
                }
                FileInputStream fis = new FileInputStream(f);
                String s = MainActivity.this.readStream(fis);
                fis.close();
                return s;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String curlToEntry(String curlCommand) {
            try {
                return parseCurlToJson(curlCommand);
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        private String parseCurlToJson(String curl) {
            String method = "GET";
            String url = "";
            String body = "";
            JSONObject headers = new JSONObject();

            // Remove newlines and normalize
            curl = curl.replace("\\\n", " ").replaceAll("\\s+", " ").trim();

            // Simple curl parser
            String[] tokens = curl.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String tok = tokens[i];
                if (tok.equalsIgnoreCase("curl")) continue;

                if (tok.equalsIgnoreCase("-X") || tok.equalsIgnoreCase("--request")) {
                    if (i + 1 < tokens.length) {
                        method = tokens[++i].toUpperCase();
                    }
                } else if (tok.equalsIgnoreCase("-H") || tok.equalsIgnoreCase("--header")) {
                    if (i + 1 < tokens.length) {
                        String hdr = tokens[++i];
                        // Remove surrounding quotes if present
                        hdr = hdr.replaceAll("^['\"]|['\"]$", "");
                        int colonIdx = hdr.indexOf(':');
                        if (colonIdx > 0) {
                            String name = hdr.substring(0, colonIdx).trim();
                            String value = hdr.substring(colonIdx + 1).trim();
                            String lk = name.toLowerCase();
                            if (!lk.equals("content-length") && !lk.equals("host")) {
                                headers.put(name, value);
                            }
                        }
                    }
                } else if (tok.equalsIgnoreCase("-d") || tok.equalsIgnoreCase("--data") || tok.equalsIgnoreCase("--data-raw")) {
                    if (i + 1 < tokens.length) {
                        body = tokens[++i];
                        body = body.replaceAll("^['\"]|['\"]$", "");
                        if (!body.isEmpty()) {
                            method = "POST";
                        }
                    }
                } else if (tok.startsWith("http://") || tok.startsWith("https://")) {
                    url = tok.replaceAll("^['\"]|['\"]$", "");
                }
            }

            // Extract host from URL for Host header
            if (!url.isEmpty()) {
                try {
                    URL u = new URL(url);
                    String host = u.getHost();
                    int port = u.getPort();
                    if (port > 0 && port != 80 && port != 443) {
                        host = host + ":" + port;
                    }
                    if (!headers.has("Host")) {
                        headers.put("Host", host);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }

            JSONObject result = new JSONObject();
            result.put("method", method);
            result.put("url", url);
            result.put("headers", headers);
            result.put("body", body);
            return result.toString();
        }
        // ===== End CURL support =====

        @JavascriptInterface
        public String loadRewriteRules() {
            try {
                File f = new File(MainActivity.this.getFilesDir(), "rewrite_rules.json");
                if (!f.exists()) {
                    return "[]";
                }
                FileInputStream fis = new FileInputStream(f);
                String s = MainActivity.this.readStream(fis);
                fis.close();
                return s;
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String saveRewriteRules(String json) {
            try {
                FileOutputStream fos = MainActivity.this.openFileOutput("rewrite_rules.json", 0);
                fos.write(json.getBytes("UTF-8"));
                fos.close();
                return "{\"success\":true}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public void sendRequest(final String entryJson, final String cbId) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    sendRequestInternal(entryJson, cbId);
                }
            }).start();
        }

        private void sendRequestInternal(String entryJson, final String cbId) {
            try {
                JSONObject e = new JSONObject(entryJson);
                String method = e.optString("method", "GET");
                String url = e.optString("url", "");
                JSONObject hdrs = e.optJSONObject("headers");
                String body = e.optString("body", "");
                long t0 = System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(30000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                if (hdrs != null) {
                    Iterator<String> it = hdrs.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        String lk = k.toLowerCase();
                        if (lk.equals("content-length") || lk.equals("host") || lk.equals("accept-encoding")) {
                            continue;
                        }
                        c.setRequestProperty(k, hdrs.getString(k));
                    }
                }
                c.setRequestProperty("Accept-Encoding", "identity");
                if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) && !body.isEmpty()) {
                    c.setDoOutput(true);
                    OutputStream os = c.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }
                int code = c.getResponseCode();
                InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                String resp = MainActivity.this.readStream(is);
                is.close();
                c.disconnect();
                long elapsed = System.currentTimeMillis() - t0;
                String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
                JSONObject r = new JSONObject();
                r.put("success", true);
                r.put("status_code", code);
                r.put("elapsed_ms", elapsed);
                r.put("response", resp);
                r.put("timestamp", ts);
                final String out = r.toString();
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("onRequestResult('" + cbId + "'," + out + ")", null);
                    }
                });
            } catch (Exception ex) {
                try {
                    JSONObject r2 = new JSONObject();
                    r2.put("success", false);
                    r2.put("error", ex.getMessage());
                    r2.put("timestamp", new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()));
                    final String out2 = r2.toString();
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript("onRequestResult('" + cbId + "'," + out2 + ")", null);
                        }
                    });
                } catch (Exception e3) {
                    // ignore
                }
            }
        }

        @JavascriptInterface
        public String getCapturedPackets() {
            try {
                File f = new File(MainActivity.this.getFilesDir(), "captured_packets.json");
                if (!f.exists()) {
                    return "[]";
                }
                FileInputStream fis = new FileInputStream(f);
                String s = MainActivity.this.readStream(fis);
                fis.close();
                return s;
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void clearCapturedPackets() {
            try {
                File f = new File(MainActivity.this.getFilesDir(), "captured_packets.json");
                if (f.exists()) {
                    f.delete();
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
