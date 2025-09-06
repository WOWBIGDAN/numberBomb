package com.example.numberbomb;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameActivity extends AppCompatActivity {
    private TextView tvGameLog;
    private EditText etGuess;
    private Button btnSendGuess;
    private Button btnStartGame;
    private ScrollView scrollView;

    private String playerName;
    private String serverIP;
    private int serverPort;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private ExecutorService executor;
    private Handler mainHandler;

    private boolean isConnected = false;
    private boolean isMyTurn = false;
    private int minRange = 0;
    private int maxRange = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 获取传递的参数
        playerName = getIntent().getStringExtra("player_name");
        serverIP = getIntent().getStringExtra("server_ip");
        serverPort = getIntent().getIntExtra("server_port", 8888);

        initViews();
        setupListeners();

        // 初始化线程池和处理器
        executor = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());

        // 连接服务器
        connectToServer();
    }

    private void initViews() {
        tvGameLog = findViewById(R.id.tv_game_log);
        etGuess = findViewById(R.id.et_guess);
        btnSendGuess = findViewById(R.id.btn_send_guess);
        btnStartGame = findViewById(R.id.btn_start_game);
        scrollView = findViewById(R.id.scroll_view);

        // 设置标题
        setTitle("玩家: " + playerName);
    }

    private void setupListeners() {
        btnSendGuess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendGuess();
            }
        });

        btnStartGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendStartGame();
            }
        });
    }

    private void connectToServer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(serverIP, serverPort);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    // 发送加入游戏消息
                    sendMessage("JOIN:" + playerName);

                    isConnected = true;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            addGameLog("已连接到服务器");
                            btnStartGame.setEnabled(true);
                        }
                    });

                    // 开始接收服务器消息
                    receiveServerMessages();

                } catch (IOException e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(GameActivity.this, "连接服务器失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void receiveServerMessages() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String message;
                    while (isConnected && (message = reader.readLine()) != null) {
                        final String finalMessage = message;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleServerMessage(finalMessage);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (isConnected) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                addGameLog("与服务器断开连接");
                                isConnected = false;
                            }
                        });
                    }
                }
            }
        });
    }

    private void handleServerMessage(String message) {
        addGameLog("服务器: " + message);

        if (message.contains("你的回合")) {
            isMyTurn = true;
            btnSendGuess.setEnabled(true);
            etGuess.setEnabled(true);
            etGuess.setHint("范围: " + minRange + "-" + maxRange);
        } else if (message.contains("轮到") && !message.contains(playerName)) {
            isMyTurn = false;
            btnSendGuess.setEnabled(false);
            etGuess.setEnabled(false);
        } else if (message.contains("新范围:")) {
            // 解析新的范围
            parseNewRange(message);
        } else if (message.contains("炸弹爆炸")) {
            isMyTurn = false;
            btnSendGuess.setEnabled(false);
            etGuess.setEnabled(false);
        }
    }

    private void parseNewRange(String message) {
        try {
            // 简单的范围解析，实际项目中可能需要更复杂的解析逻辑
            if (message.contains("新范围:")) {
                String rangePart = message.substring(message.indexOf("新范围:") + 4);
                String[] parts = rangePart.split("-");
                if (parts.length == 2) {
                    minRange = Integer.parseInt(parts[0].trim());
                    maxRange = Integer.parseInt(parts[1].trim());
                    etGuess.setHint("范围: " + minRange + "-" + maxRange);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendGuess() {
        if (!isConnected || !isMyTurn) {
            return;
        }

        String guessStr = etGuess.getText().toString().trim();
        if (guessStr.isEmpty()) {
            Toast.makeText(this, "请输入猜测的数字", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int guess = Integer.parseInt(guessStr);
            if (guess < minRange || guess > maxRange) {
                Toast.makeText(this, "数字必须在 " + minRange + " 到 " + maxRange + " 之间", Toast.LENGTH_SHORT).show();
                return;
            }

            sendMessage("GUESS:" + playerName + ":" + guess);
            addGameLog("你猜测: " + guess);
            etGuess.setText("");

        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendStartGame() {
        if (isConnected) {
            sendMessage("START:");
            addGameLog("请求开始游戏");
        }
    }

    private void sendMessage(String message) {
        if (writer != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        writer.write(message + "\n");
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void addGameLog(String message) {
        String currentLog = tvGameLog.getText().toString();
        String newLog = currentLog + message + "\n";
        tvGameLog.setText(newLog);

        // 滚动到底部
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isConnected = false;

        if (executor != null) {
            executor.shutdown();
        }

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
