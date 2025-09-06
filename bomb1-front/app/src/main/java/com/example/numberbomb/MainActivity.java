package com.example.numberbomb;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText etPlayerName;
    private EditText etServerIP;
    private EditText etServerPort;
    private Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etPlayerName = findViewById(R.id.et_player_name);
        etServerIP = findViewById(R.id.et_server_ip);
        etServerPort = findViewById(R.id.et_server_port);
        btnConnect = findViewById(R.id.btn_connect);

        // 设置默认值
        etServerIP.setText("192.168.1.100"); // 请根据实际网络环境修改
        etServerPort.setText("8889");
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String playerName = etPlayerName.getText().toString().trim();
                String serverIP = etServerIP.getText().toString().trim();
                String serverPort = etServerPort.getText().toString().trim();

                if (playerName.isEmpty()) {
                    etPlayerName.setError("请输入玩家名称");
                    return;
                }

                if (serverIP.isEmpty()) {
                    etServerIP.setError("请输入服务器IP");
                    return;
                }

                if (serverPort.isEmpty()) {
                    etServerPort.setError("请输入服务器端口");
                    return;
                }

                // 启动游戏活动
                Intent intent = new Intent(MainActivity.this, GameActivity.class);
                intent.putExtra("player_name", playerName);
                intent.putExtra("server_ip", serverIP);
                intent.putExtra("server_port", Integer.parseInt(serverPort));
                startActivity(intent);
            }
        });
    }
}
