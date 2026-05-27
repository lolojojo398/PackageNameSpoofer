package com.spoofer.har.proxy;

import android.content.Context;
import android.util.Log;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

public class HarRecorder {
    private static final String TAG = "HarRecorder";
    private final Context context;
    private final List<JSONObject> entries = new ArrayList<>();
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    public HarRecorder(Context context) {
        this.context = context;
        this.isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public synchronized void recordRequest(String method, String url, String headers, String body, String statusLine, String responseHeaders, String responseBody) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("startedDateTime", this.isoFormat.format(new Date()));
            entry.put("time", 0);
            JSONObject request = new JSONObject();
            request.put("method", method);
            request.put("url", url);
            request.put("httpVersion", "HTTP/1.1");
            JSONArray reqHeaders = new JSONArray();
            if (headers != null && !headers.isEmpty()) {
                String[] split = headers.split("\r\n");
                for (String h : split) {
                    if (h.isEmpty()) continue;
                    int colonIdx = h.indexOf(':');
                    if (colonIdx <= 0) continue;
                    JSONObject header = new JSONObject();
                    header.put("name", h.substring(0, colonIdx).trim());
                    header.put("value", h.substring(colonIdx + 1).trim());
                    reqHeaders.put(header);
                }
            }
            request.put("headers", reqHeaders);
            if (body != null && !body.isEmpty()) {
                JSONObject postData = new JSONObject();
                postData.put("mimeType", "application/x-www-form-urlencoded");
                postData.put("text", body);
                request.put("postData", postData);
            }
            request.put("bodySize", body != null ? body.length() : 0);
            entry.put("request", request);
            JSONObject response = new JSONObject();
            int statusCode = 200;
            if (statusLine != null && statusLine.length() > 11) {
                try {
                    statusCode = Integer.parseInt(statusLine.split(" ")[1]);
                } catch (Exception e) {
                    // ignore
                }
            }
            response.put("status", statusCode);
            response.put("statusText", "");
            response.put("httpVersion", "HTTP/1.1");
            JSONArray respHeaders = new JSONArray();
            if (responseHeaders != null && !responseHeaders.isEmpty()) {
                String[] split2 = responseHeaders.split("\r\n");
                for (String h2 : split2) {
                    if (h2.isEmpty()) continue;
                    int colonIdx2 = h2.indexOf(':');
                    if (colonIdx2 <= 0) continue;
                    JSONObject header2 = new JSONObject();
                    header2.put("name", h2.substring(0, colonIdx2).trim());
                    header2.put("value", h2.substring(colonIdx2 + 1).trim());
                    respHeaders.put(header2);
                }
            }
            response.put("headers", respHeaders);
            JSONObject content = new JSONObject();
            content.put("size", responseBody != null ? responseBody.length() : 0);
            content.put("mimeType", "text/plain");
            content.put("text", responseBody != null ? responseBody : "");
            response.put("content", content);
            response.put("bodySize", responseBody != null ? responseBody.length() : 0);
            entry.put("response", response);
            JSONObject app = new JSONObject();
            app.put("id", "captured");
            app.put("name", "Captured Traffic");
            entry.put("_app", app);
            synchronized (this.entries) {
                this.entries.add(entry);
            }
            Log.d(TAG, "Recorded: " + method + " " + url.substring(0, Math.min(url.length(), 80)));
        } catch (Exception e) {
            Log.e(TAG, "Error recording request", e);
        }
    }

    public synchronized void flush() {
        try {
            JSONObject har = new JSONObject();
            JSONObject log = new JSONObject();
            log.put("version", "1.2");
            JSONObject creator = new JSONObject();
            creator.put("name", "HarPacketSender");
            creator.put("version", "1.0");
            log.put("creator", creator);
            synchronized (this.entries) {
                log.put("entries", new JSONArray(this.entries));
            }
            har.put("log", log);
            FileOutputStream fos = this.context.openFileOutput("captured_packets.json", 0);
            fos.write(har.toString().getBytes("UTF-8"));
            fos.close();
            Log.i(TAG, "Flushed " + this.entries.size() + " entries to file");
        } catch (Exception e) {
            Log.e(TAG, "Error flushing HAR", e);
        }
    }

    public synchronized int getEntryCount() {
        return this.entries.size();
    }
}
