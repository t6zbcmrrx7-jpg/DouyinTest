package com.test.douyin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etAccount;
    private Button btnAuth;
    private TextView tvResult;

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
        btnAuth.setText("启动中...");
        tvResult.setVisibility(View.GONE);

        // 启动 AgentActivity 处理授权
        Intent intent = new Intent(this, AgentActivity.class);
        intent.putExtra("account", account);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnAuth.setEnabled(true);
        btnAuth.setText("授权登录");
    }
}
