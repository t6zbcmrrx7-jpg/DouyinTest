package com.test.douyin;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯开放 SDK AgentActivity
 * 抖音调用 QQ 登录时，Intent 会路由到此 Activity
 */
public class AgentActivity extends AppCompatActivity {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "access_token=(.*?)&expires_in=(.*?)&openid=(.*?)&pay_token=(.*?)&"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            Toast.makeText(this, "无数据", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String action = intent.getAction();
        Uri data = intent.getData();
        String scheme = data != null ? data.getScheme() : "null";

        // 显示接收到的 Intent 信息
        StringBuilder sb = new StringBuilder();
        sb.append("收到抖音授权请求\n");
        sb.append("Action: ").append(action).append("\n");
        sb.append("Scheme: ").append(scheme).append("\n");
        if (data != null) {
            sb.append("URI: ").append(data.toString()).append("\n");
            // 打印所有参数
            for (String key : data.getQueryParameterNames()) {
                sb.append("  ").append(key).append(": ")
                        .append(data.getQueryParameter(key)).append("\n");
            }

            // 检查是否是 auth://tauth.qq.com 回调 （QQ OAuth 完成后的回调）
            if ("auth".equals(scheme) && "tauth.qq.com".equals(data.getHost())) {
                String url = data.toString();
                Matcher m = TOKEN_PATTERN.matcher(url);
                if (m.find()) {
                    sb.append("\n✓ 检测到 QQ 授权回调 Token:\n");
                    sb.append("AccessToken: ").append(m.group(1)).append("\n");
                    sb.append("ExpiresIn: ").append(m.group(2)).append("\n");
                    sb.append("OpenID: ").append(m.group(3)).append("\n");
                    sb.append("PayToken: ").append(m.group(4));
                }
            }
        }

        // 显示信息
        setContentView(R.layout.activity_agent);
        TextView tv = findViewById(R.id.tvAgentInfo);
        tv.setText(sb.toString());

        // 如果有 openid 参数，转发到 MainActivity 做下一步
        if (data != null && data.getQueryParameter("openid") != null) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.setData(data);
            startActivity(mainIntent);
        }

        Toast.makeText(this, "收到授权请求", Toast.LENGTH_LONG).show();
    }
}
