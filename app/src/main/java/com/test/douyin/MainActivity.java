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

    // ====== 配置 ======
    private static final String SERVER_URL = "http://45.10.175.247:3260";
    // 抖音包名 / URL Scheme，可按需修改
    private static final String DOUYIN_SCHEME = "snssdk1128://";

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

                // 格式: account----openid|access_token|pay_token
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

                String resultText = "✓ 获取成功\n"
                        + "账号: " + accountId + "\n"
                        + "OpenID: " + openid + "\n"
                        + "Token: " + accessToken;

                showSuccess(resultText);

                // 跳转抖音 (URL Scheme)
                // 可以按需修改拼接方式
                String douyinUrl = DOUYIN_SCHEME + "authorize?openid=" + Uri.encode(openid)
                        + "&access_token=" + Uri.encode(accessToken)
                        + "&pay_token=" + Uri.encode(payToken);

                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(douyinUrl));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    showError("无法打开抖音，请确认已安装:\n" + e.getMessage());
                }

            } catch (Exception e) {
                showError("网络错误:\n" + e.getMessage());
            }
        });
    }

    private void showError(final String msg) {
        mainHandler.post(() -> {
            btnAuth.setEnabled(true);
            btnAuth.setText("授权登录");
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(0xFFE53E3E);
            tvResult.setText("✗ " + msg);
        });
    }

    private void showSuccess(final String msg) {
        mainHandler.post(() -> {
            btnAuth.setEnabled(true);
            btnAuth.setText("授权登录");
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(0xFF16A34A);
            tvResult.setText(msg);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
