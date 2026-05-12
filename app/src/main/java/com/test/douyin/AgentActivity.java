package com.test.douyin;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentActivity extends AppCompatActivity {

    private static final String SERVER_URL = "http://45.10.175.247:3260";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "access_token=(.*?)&expires_in=(.*?)&openid=(.*?)&pay_token=(.*?)&"
    );

    private WebView webView;
    private TextView tvStatus;
    private String account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent);

        webView = findViewById(R.id.webView);
        tvStatus = findViewById(R.id.tvStatus);

        account = getIntent().getStringExtra("account");
        if (account == null || account.isEmpty()) {
            Toast.makeText(this, "账号为空", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvStatus.setText("正在获取账号数据...");
        setupWebView();
        new GetDateTask().execute(account);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("auth://tauth.qq.com")) {
                    handleCallback(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (url.startsWith("auth://tauth.qq.com")) {
                    handleCallback(url);
                }
                tvStatus.setText("加载中: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!url.startsWith("auth://")) {
                    tvStatus.setText("QQ 授权页面已加载");
                }
            }
        });
    }

    private void handleCallback(String url) {
        Matcher m = TOKEN_PATTERN.matcher(url);
        if (m.find()) {
            String accessToken = m.group(1);
            String expiresIn = m.group(2);
            String openid = m.group(3);
            String payToken = m.group(4);

            StringBuilder result = new StringBuilder();
            result.append("✓ QQ 授权成功\n\n");
            result.append("OpenID: ").append(openid).append("\n");
            result.append("AccessToken: ").append(accessToken).append("\n");
            result.append("PayToken: ").append(payToken).append("\n");
            result.append("有效期: ").append(expiresIn).append(" 秒");

            tvStatus.setText(result.toString());
            webView.setVisibility(android.view.View.GONE);
        } else {
            tvStatus.setText("未匹配到 Token\n" + url);
        }
    }

    // ===== GetDateTask (与 QQLogin.apk 一致) =====
    private class GetDateTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                String accountId = params[0];
                URL url = new URL(SERVER_URL + "/getOP/account=" + Uri.encode(accountId));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code != 200) return null;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                return sb.toString().trim();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            if (response == null || response.isEmpty()) {
                tvStatus.setText("✗ 获取账号数据失败，请检查账号");
                return;
            }

            try {
                String[] parts = response.split("----");
                if (parts.length < 2) {
                    tvStatus.setText("✗ 数据格式错误:\n" + response);
                    return;
                }

                String dataPart = parts[1];
                String[] fields = dataPart.split("\\|");
                if (fields.length < 3) {
                    tvStatus.setText("✗ 数据字段不足:\n" + response);
                    return;
                }

                String openid = fields[0];
                String accessToken = fields[1];
                String payToken = fields[2];

                tvStatus.setText("数据获取成功，正在打开 QQ 授权...");

                // 构造 QQ OAuth URL (与 QQLogin.apk 一致)
                String authUrl = "https://tauth.qq.com/cgi-bin/auth"
                        + "?openid=" + Uri.encode(openid)
                        + "&access_token=" + Uri.encode(accessToken)
                        + "&pay_token=" + Uri.encode(payToken);

                webView.setVisibility(android.view.View.VISIBLE);
                webView.loadUrl(authUrl);

            } catch (Exception e) {
                tvStatus.setText("✗ 解析错误:\n" + e.getMessage());
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }
}
