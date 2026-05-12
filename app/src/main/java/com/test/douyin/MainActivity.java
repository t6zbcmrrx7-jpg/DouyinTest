package com.test.douyin;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URL = "http://45.10.175.247:3260";
    private static final String CALLBACK_HOST = "auth://tauth.qq.com/?#";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "access_token=(.*?)&expires_in=(.*?)&openid=(.*?)&pay_token=(.*?)&"
    );

    private EditText etAccount;
    private Button btnAuth;
    private TextView tvResult;
    private WebView webView;
    private View authPanel, webPanel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentOpenid, currentAccessToken, currentPayToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAccount = findViewById(R.id.etAccount);
        btnAuth = findViewById(R.id.btnAuth);
        tvResult = findViewById(R.id.tvResult);
        webView = findViewById(R.id.webView);
        authPanel = findViewById(R.id.authPanel);
        webPanel = findViewById(R.id.webPanel);

        btnAuth.setOnClickListener(v -> doAuth());
        setupWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");

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
            }
        });
    }

    private void handleCallback(String url) {
        // 格式: auth://tauth.qq.com/?#access_token=xxx&expires_in=5184000&openid=xxx&pay_token=xxx&
        Matcher m = TOKEN_PATTERN.matcher(url);
        if (m.find()) {
            String accessToken = m.group(1);
            String expiresIn = m.group(2);
            String openid = m.group(3);
            String payToken = m.group(4);

            StringBuilder sb = new StringBuilder();
            sb.append("✓ QQ授权成功\n");
            sb.append("OpenID: ").append(openid).append("\n");
            sb.append("AccessToken: ").append(accessToken).append("\n");
            sb.append("PayToken: ").append(payToken).append("\n");
            sb.append("有效期: ").append(expiresIn).append("秒");

            showWebPanel(false);
            showResult(sb.toString(), false);
        } else {
            showWebPanel(false);
            showResult("未匹配到 Token\n" + url, true);
        }
    }

    private void doAuth() {
        String account = etAccount.getText().toString().trim();
        if (account.isEmpty()) {
            Toast.makeText(this, "请输入账号", Toast.LENGTH_SHORT).show();
            return;
        }

        btnAuth.setEnabled(false);
        btnAuth.setText("请求中...");
        tvResult.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                String urlStr = SERVER_URL + "/getOP/account=" + Uri.encode(account);
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    showError("服务器返回: " + code + (code == 404 ? " (账号不存在)" : ""));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                String response = sb.toString().trim();
                if (response.isEmpty()) {
                    showError("返回数据为空");
                    return;
                }

                String[] parts = response.split("----");
                if (parts.length < 2) {
                    showError("数据格式错误:\n" + response);
                    return;
                }

                String dataPart = parts[1];
                String[] fields = dataPart.split("\\|");
                if (fields.length < 3) {
                    showError("数据字段不足:\n" + response);
                    return;
                }

                currentOpenid = fields[0];
                currentAccessToken = fields[1];
                currentPayToken = fields[2];

                // 构造 QQ OAuth URL (与 QQLogin.apk 一致)
                String authUrl = "https://tauth.qq.com/cgi-bin/auth"
                        + "?openid=" + Uri.encode(currentOpenid)
                        + "&access_token=" + Uri.encode(currentAccessToken)
                        + "&pay_token=" + Uri.encode(currentPayToken);

                String info = "✓ 获取成功\n"
                        + "OpenID: " + currentOpenid + "\n"
                        + "Token: " + currentAccessToken + "\n\n"
                        + "正在打开 QQ 授权页面...";

                showWebPanel(true);
                showResult(info, false);

                // 在 WebView 中加载 QQ 授权页
                mainHandler.post(() -> webView.loadUrl(authUrl));

            } catch (Exception e) {
                showError("错误:\n" + e.getMessage());
            }
        });
    }

    private void showWebPanel(final boolean show) {
        mainHandler.post(() -> {
            authPanel.setVisibility(show ? View.GONE : View.VISIBLE);
            webPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            btnAuth.setEnabled(true);
            btnAuth.setText("授权登录");
        });
    }

    private void showError(final String msg) {
        mainHandler.post(() -> {
            btnAuth.setEnabled(true);
            btnAuth.setText("授权登录");
            showResult(msg, true);
        });
    }

    private void showResult(final String msg, final boolean isError) {
        mainHandler.post(() -> {
            btnAuth.setEnabled(true);
            btnAuth.setText("授权登录");
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(isError ? 0xFFE53E3E : 0xFF16A34A);
            tvResult.setText(msg);
        });
    }

    @Override
    public void onBackPressed() {
        if (webPanel.getVisibility() == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                showWebPanel(false);
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
