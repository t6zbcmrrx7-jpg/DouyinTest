package com.test.douyin;

import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ====== 服务器配置 ======
    private static final String SERVER_URL = "http://45.10.175.247:3260";

    // ====== 目标应用配置 ======
    // QQ
    private static final String QQ_PKG = "com.tencent.mobileqq";
    private static final String QQ_SCHEME = "tencent1105602870://qzapp/mqzone/0?objectlocation=url&pasteboard=";
    // 抖音
    private static final String DOUYIN_PKG = "com.ss.android.ugc.aweme";
    private static final String DOUYIN_SCHEME = "snssdk1128://authorize?";
    // 当前选择的目标 (0=QQ, 1=抖音)
    private int targetApp = 0;

    private EditText etAccount;
    private Button btnAuth, btnSwitch;
    private TextView tvResult, tvTarget;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAccount = findViewById(R.id.etAccount);
        btnAuth = findViewById(R.id.btnAuth);
        btnSwitch = findViewById(R.id.btnSwitch);
        tvResult = findViewById(R.id.tvResult);
        tvTarget = findViewById(R.id.tvTarget);

        updateTargetDisplay();

        btnSwitch.setOnClickListener(v -> {
            targetApp = 1 - targetApp;
            updateTargetDisplay();
        });

        btnAuth.setOnClickListener(v -> doAuth());
    }

    private void updateTargetDisplay() {
        if (targetApp == 0) {
            tvTarget.setText("当前目标: QQ");
            btnSwitch.setText("切换至抖音");
        } else {
            tvTarget.setText("当前目标: 抖音");
            btnSwitch.setText("切换至QQ");
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

                // Android 授权跳转
                openTargetApp(openid, accessToken, payToken);

            } catch (Exception e) {
                showError("网络错误:\n" + e.getMessage());
            }
        });
    }

    private void openTargetApp(String openid, String accessToken, String payToken) {
        Intent intent;
        String pkg;
        String targetName;

        if (targetApp == 0) {
            // QQ 授权: 使用 iOS 相同的 pasteboard 方式
            String pasteboardData = generatePasteboardData(openid, accessToken, payToken);
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(QQ_SCHEME + Uri.encode(pasteboardData)));
            pkg = QQ_PKG;
            targetName = "QQ";
        } else {
            // 抖音授权
            String url = DOUYIN_SCHEME
                    + "openid=" + Uri.encode(openid)
                    + "&access_token=" + Uri.encode(accessToken)
                    + "&pay_token=" + Uri.encode(payToken);
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            pkg = DOUYIN_PKG;
            targetName = "抖音";
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 检查目标应用是否安装
        PackageManager pm = getPackageManager();
        List resolveList = pm.queryIntentActivities(intent, 0);

        if (resolveList != null && resolveList.size() > 0) {
            try {
                startActivity(intent);
                showInfo("正在打开" + targetName + "...");
            } catch (Exception e) {
                showError("打开" + targetName + "失败:\n" + e.getMessage());
            }
        } else {
            // URL scheme 没找到，尝试用包名直接打开
            Intent pkgIntent = pm.getLaunchIntentForPackage(pkg);
            if (pkgIntent != null) {
                pkgIntent.putExtra("openid", openid);
                pkgIntent.putExtra("access_token", accessToken);
                pkgIntent.putExtra("pay_token", payToken);
                try {
                    startActivity(pkgIntent);
                    showInfo("正在打开" + targetName + "...");
                    return;
                } catch (Exception ignored) {}
            }
            showError("未安装" + targetName + "\n请先安装官方" + targetName + "应用");
        }
    }

    // 生成 pasteboard 数据 (与 iOS 版保持一致)
    private String generatePasteboardData(String openid, String accessToken, String payToken) {
        // 使用和 iOS 相同的 binary plist base64 格式
        // 这里构造简单的参数拼接
        return openid + "|" + accessToken + "|" + payToken;
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

    private void showInfo(final String msg) {
        mainHandler.post(() -> {
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(0xFF3B82F6);
            tvResult.setText(msg);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
