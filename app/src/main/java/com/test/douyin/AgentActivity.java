package com.test.douyin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentActivity extends AppCompatActivity {

    private static final String PREF_NAME = "myPrefs";
    private static final String KEY_RAW_TEXT = "raw_text";
    private static final String SERVER_URL = "http://45.10.175.247:3260";

    private EditText agEtKami;
    private TextView agKamiCode;
    private WebView webView;
    private LinearLayout ycPanel;

    public void closeActivity(View view) {
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent);

        agKamiCode = findViewById(R.id.tvStatus);
        agEtKami = findViewById(R.id.etAccount);
        webView = findViewById(R.id.webView);
        ycPanel = findViewById(R.id.ycPanel);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, 0);
        Button btnAuth = findViewById(R.id.btnAuth);
        btnAuth.setOnClickListener(v -> verifyAccount());

        // Check pd_key - controls which UI to show
        String pdKey = prefs.getString("pd_key", null);

        if (pdKey == null) {
            // Show WebView with QQ login page
            webView.setVisibility(android.view.View.VISIBLE);
            ycPanel.setVisibility(android.view.View.GONE);
            loadQQLoginPage();
        } else if ("1".equals(pdKey.trim())) {
            // Show account input form
            webView.setVisibility(android.view.View.GONE);
            ycPanel.setVisibility(android.view.View.VISIBLE);
            String rawText = prefs.getString(KEY_RAW_TEXT, "");
            Log.d("AgentActivity", rawText);
        } else {
            // Show WebView with QQ login page
            webView.setVisibility(android.view.View.VISIBLE);
            ycPanel.setVisibility(android.view.View.GONE);
            loadQQLoginPage();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadQQLoginPage() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new MyWebViewClient());

        // QQ XUI Login page (same URL as QQLogin.apk)
        String xuiUrl = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin"
                + "?pt_enable_pwd=1"
                + "&appid=716027609"
                + "&pt_3rd_aid=1106467070"
                + "&daid=381"
                + "&pt_skey_valid=0"
                + "&style=35"
                + "&force_qr=1"
                + "&autorefresh=1"
                + "&s_url=http%3A%2F%2Fconnect.qq.com"
                + "&refer_cgi=m_authorize"
                + "&ucheck=1"
                + "&fall_to_wv=1"
                + "&status_os=13"
                + "&redirect_uri=auth%3A%2F%2Ftauth.qq.com%2F"
                + "&client_id=1106467070"
                + "&pf=openmobile_android"
                + "&response_type=token"
                + "&scope=all"
                + "&sdkp=a"
                + "&sdkv=3.5.14"
                + "&switch=1"
                + "&loginfrom=add";

        webView.loadUrl(xuiUrl);
    }

    private void verifyAccount() {
        String account = agEtKami.getText().toString().trim();
        if (account.isEmpty()) {
            agKamiCode.setText("账号不可以为空");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, 0);
        prefs.edit().putString("num_key", account).apply();
        agKamiCode.setText("正在验证账号...");
        new GetDateTask().execute(account);
    }

    // ============================================================
    // GetDateTask: Fetch from server (same as QQLogin.apk)
    // ============================================================
    private class GetDateTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                URL url = new URL(SERVER_URL + "/getOP/account=" + Uri.encode(params[0]));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code != 200) return "请求失败，响应码：" + code;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                return sb.toString().trim();
            } catch (Exception e) {
                return "网络请求失败：" + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            boolean isSuccess = result != null && result.contains("----");

            SharedPreferences prefs = getSharedPreferences(PREF_NAME, 0);
            if (isSuccess) {
                prefs.edit().putString(KEY_RAW_TEXT, result).apply();
                agKamiCode.setText("账号验证成功");
                Toast.makeText(AgentActivity.this, "账号验证成功", Toast.LENGTH_SHORT).show();
            } else {
                prefs.edit().putString(KEY_RAW_TEXT, "").apply();
                agKamiCode.setText("账号验证失败，请检查网络或账号");
                Toast.makeText(AgentActivity.this, "账号验证失败", Toast.LENGTH_SHORT).show();
            }

            if (isSuccess) {
                proceedAfterTask();
            }
        }
    }

    // ============================================================
    // proceedAfterTask: 处理验证成功后的授权流程 (与 QQLogin.apk 一致)
    // ============================================================
    private void proceedAfterTask() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, 0);
        String rawText = prefs.getString(KEY_RAW_TEXT, "");

        // Extract data after "----": openid|access_token|pay_token|...
        String dataPart;
        if (rawText.contains("----")) {
            int idx = rawText.indexOf("----");
            dataPart = rawText.substring(idx + 4).trim();
        } else {
            dataPart = rawText;
        }

        // ========== convertSingleLine (与 QQLogin.apk 一致) ==========
        String test = convertSingleLine(dataPart);

        // Extract tokens from the converted string
        Pattern pattern = Pattern.compile("access_token=(.*?)&expires_in=(.*?)&openid=(.*?)&pay_token=(.*?)&");
        Matcher matcher = pattern.matcher(test);
        if (!matcher.find()) {
            Toast.makeText(this, "格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        String accessToken = matcher.group(1);
        String expiresIn = matcher.group(2);
        String openid = matcher.group(3);
        String payToken = matcher.group(4);

        // Build URL from template (与 QQLogin.apk 一致)
        String template = "access_token=(.*?)&expires_in=(.*?)&openid=(.*?)&pay_token=(.*?)&ret=(.*?)&pf=(.*?)&page_type=(.*?)&";
        String newUrl = template
                .replace("access_token=(.*?)&", "access_token=" + accessToken + "&")
                .replace("expires_in=(.*?)&", "expires_in=" + expiresIn + "&")
                .replace("openid=(.*?)&", "openid=" + openid + "&")
                .replace("pay_token=(.*?)&", "pay_token=" + payToken + "&")
                .replace("ret=(.*?)&", "ret=0&")
                .replace("pf=(.*?)&", "pf=desktop_m_qq-10000144-android-2002-&")
                .replace("page_type=(.*?)&", "page_type=1&");

        agKamiCode.setText("授权成功\nOpenID: " + openid + "\nToken: " + accessToken);

        // Set result and return to caller
        Intent intent = new Intent();
        intent.putExtra("key_response",
                "{\"ret\":\"0\",\"access_token\":\"" + accessToken
                        + "\",\"openid\":\"" + openid
                        + "\",\"pay_token\":\"" + payToken
                        + "\",\"expires_in\":\"" + expiresIn
                        + "\",\"pf\":\"desktop_m_qq-10000144-android-2002-\",\"page_type\":\"1\"}");
        setResult(RESULT_OK, intent);

        // Load callback URL so MyWebViewClient intercepts and finishes
        String callbackUrl = "auth://tauth.qq.com/?#" + newUrl;
        webView.loadUrl(callbackUrl);
    }

    // ============================================================
    // convertSingleLine (与 QQLogin.apk 完全一致!)
    // 输入: openid|access_token|pay_token|...
    // 输出: _Callback( {"ret":0, "url":"auth://tauth.qq.com/?#access_token=...&expires_in=5184000&openid=...&pay_token=..."})
    // ============================================================
    public static String convertSingleLine(String input) {
        if (input == null || input.trim().isEmpty()) return "";

        String[] params = input.trim().split("\\|");
        if (params.length < 3) return "";

        String openid = params[0];
        String accessToken = params[1];
        String payToken = params[2];

        return "_Callback( {\"ret\":0, \"url\":\"auth://tauth.qq.com/?#access_token="
                + accessToken
                + "&expires_in=5184000"
                + "&openid=" + openid
                + "&pay_token=" + payToken
                + "&ret=0"
                + "&pf=desktop_m_qq-10000144-android-2002-"
                + "&pfkey=0b7ff4e8e66e8c4a82074ee2f50dd633"
                + "&auth_time=1761679297"
                + "&page_type=0"
                + "\"});";
    }

    // ============================================================
    // MyWebViewClient: 拦截 auth:// 回调 (与 QQLogin.apk 一致)
    // ============================================================
    private class MyWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.startsWith("auth:")) {
                handleAuthRedirect(url);
                return true;
            }
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("auth:")) {
                handleAuthRedirect(url);
                return true;
            }
            return false;
        }

        private void handleAuthRedirect(String url) {
            // Save callback to file (与 QQLogin.apk 一致)
            writeToFile(url);

            Pattern pattern = Pattern.compile("access_token=(.*?)&expires_in=(.*?)&openid=(.*?)&pay_token=(.*?)&");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                String accessToken = matcher.group(1);
                String expiresIn = matcher.group(2);
                String openid = matcher.group(3);
                String payToken = matcher.group(4);

                Intent intent = new Intent();
                intent.putExtra("key_response",
                        "{\"ret\":\"0\",\"access_token\":\"" + accessToken
                                + "\",\"openid\":\"" + openid
                                + "\",\"pay_token\":\"" + payToken
                                + "\",\"expires_in\":\"" + expiresIn
                                + "\",\"pf\":\"desktop_m_qq-10000144-android-2002-\",\"page_type\":\"1\"}");
                setResult(RESULT_OK, intent);
                finish();
            }
        }

        private void writeToFile(String data) {
            try {
                File file = new File(Environment.getExternalStorageDirectory(), "_CallBack.txt");
                FileWriter writer = new FileWriter(file, true);
                writer.append(data).append("\n");
                writer.flush();
                writer.close();
            } catch (IOException ignored) {}
        }
    }
}
