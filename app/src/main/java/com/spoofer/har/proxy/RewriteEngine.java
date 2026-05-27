package com.spoofer.har.proxy;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class RewriteEngine {
    private static final String TAG = "RewriteEngine";
    private final Context context;

    public RewriteEngine(Context context) {
        this.context = context;
    }

    public String applyRewrite(String url, String responseBody) {
        try {
            File rulesFile = new File(this.context.getFilesDir(), "rewrite_rules.json");
            if (!rulesFile.exists()) {
                return responseBody;
            }
            FileInputStream fis = new FileInputStream(rulesFile);
            byte[] data = new byte[(int) rulesFile.length()];
            fis.read(data);
            fis.close();
            String rulesJson = new String(data, "UTF-8");
            JSONArray rules = new JSONArray(rulesJson);
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                String urlMatch = rule.optString("urlMatch", "");
                String find = rule.optString("find", "");
                String replace = rule.optString("replace", "");
                if (!urlMatch.isEmpty() && url.contains(urlMatch) && !find.isEmpty()) {
                    responseBody = responseBody.replace(find, replace);
                    Log.d(TAG, "Applied rewrite rule: " + urlMatch);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying rewrite", e);
        }
        return responseBody;
    }
}
