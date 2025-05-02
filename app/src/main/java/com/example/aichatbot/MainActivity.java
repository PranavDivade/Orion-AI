package com.example.aichatbot;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    TextView welcomeTextView;
    EditText messageEditText;
    ImageButton sendButton;
    ImageButton clearButton;
    ImageButton speechButton, copyButton, textToSpeechButton, shareButton;
    List<Message> messageList;
    MessageAdapter messageAdapter;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String API_KEY = BuildConfig.GROQ_API_KEY;
    OkHttpClient client = new OkHttpClient();
    ClipboardManager clipboard;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;
    private TextToSpeech textToSpeech;
    private boolean isTextToSpeechEnabled = true;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Log API key status (first few characters only for security)
        Log.d(TAG, "API Key length: " + (API_KEY != null ? API_KEY.length() : 0));
        if (API_KEY == null || API_KEY.isEmpty()) {
            Toast.makeText(this, "API Key not configured. Please check your local.properties file.", Toast.LENGTH_LONG).show();
        }
        
        messageList = new ArrayList<>();

        recyclerView = findViewById(R.id.recycler_view);
        welcomeTextView = findViewById(R.id.welcome_text);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);
        clearButton = findViewById(R.id.clearButton);
        speechButton = findViewById(R.id.speech_button);
        copyButton = findViewById(R.id.copy_button);
        textToSpeechButton = findViewById(R.id.text_to_speech_button);
        shareButton = findViewById(R.id.shareButton);

        // Setup recycler view
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        sendButton.setOnClickListener((v) -> {
            String question = messageEditText.getText().toString().trim();
            if (!question.isEmpty()) {
                addToChat(question, Message.SENT_BY_ME);
                messageEditText.setText("");
                callAPI(question);
                welcomeTextView.setVisibility(View.GONE);

                // Disable text-to-speech functionality
                isTextToSpeechEnabled = false;
            }
        });

        clearButton.setOnClickListener((v) -> {
            clearChat();
        });

        messageEditText.requestFocus();
        showKeyboard();

        speechButton.setOnClickListener((v) -> {
            startSpeechToText();
        });

        copyButton.setOnClickListener((v) -> {
            String response = getLastResponse();
            if (!response.isEmpty()) {
                copyToClipboard(response);
            }
        });

        textToSpeechButton.setOnClickListener((v) -> {
            String response = getLastResponse();
            if (!response.isEmpty()) {
                textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        shareButton.setOnClickListener((v) -> {
            String response = getLastResponse();
            if (!response.isEmpty()) {
                shareResponse(response);
            }
        });

        RelativeLayout bottomLayout = findViewById(R.id.bottom_layout);
        final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                bottomLayout.getWindowVisibleDisplayFrame(r);
                int screenHeight = bottomLayout.getRootView().getHeight();

                int keypadHeight = screenHeight - r.bottom;

                if (keypadHeight > screenHeight * 0.15) { // Adjust the threshold as needed
                    clearButton.setVisibility(View.GONE);
                    shareButton.setVisibility(View.GONE);
                } else {
                    clearButton.setVisibility(View.VISIBLE);
                    shareButton.setVisibility(View.VISIBLE);
                }
            }
        };

        bottomLayout.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TextToSpeech", "Language not supported");
                    }
                } else {
                    Log.e("TextToSpeech", "Initialization failed");
                }
            }
        });

        checkTextToSpeechData();
    }

    private void startSpeechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something...");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not supported on your device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                messageEditText.setText(spokenText);
            }
        }
    }

    private void checkTextToSpeechData() {
        PackageManager pm = getPackageManager();
        int result = pm.checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        if (result != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    void addToChat(String message, String sentBy) {
        runOnUiThread(() -> {
            messageList.add(new Message(message, sentBy));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    void addResponse(String response, String sentBy) {
        runOnUiThread(() -> {
            messageList.add(new Message(response, sentBy));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    void clearChat() {
        messageList.clear();
        messageAdapter.notifyDataSetChanged();
    }

    void callAPI(String question) {
        // Show typing indicator
        addResponse("Typing...", Message.SENT_BY_BOT);

        // Validate API key
        if (API_KEY == null || API_KEY.isEmpty()) {
            handleError("API Key not configured. Please check your local.properties file.");
            return;
        }

        // Log request details (without sensitive data)
        Log.d(TAG, "Making API request to: " + API_URL);
        Log.d(TAG, "API Key length: " + API_KEY.length());

        // OkHttp
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "llama-3.3-70b-versatile");
            JSONArray messages = new JSONArray();
            
            // Add system message for better context
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a helpful AI assistant. Provide clear, concise, and accurate responses.");
            messages.put(systemMessage);
            
            // Add user message
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", question);
            messages.put(userMessage);
            
            jsonBody.put("messages", messages);
            jsonBody.put("max_tokens", 1024);
            jsonBody.put("temperature", 0.7);
            jsonBody.put("top_p", 0.9);
            jsonBody.put("frequency_penalty", 0.0);
            jsonBody.put("presence_penalty", 0.0);
        } catch (JSONException e) {
            e.printStackTrace();
            handleError("Error preparing request: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                handleError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(result);
                        if (jsonObject.has("choices") && jsonObject.getJSONArray("choices").length() > 0) {
                            String output = jsonObject.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            handleSuccess(output);
                        } else {
                            handleError("Invalid response format: No choices found");
                        }
                    } catch (JSONException e) {
                        handleError("Error parsing response: " + e.getMessage());
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.e(TAG, "Server error: " + response.code() + " - " + response.message() + "\nBody: " + errorBody);
                    handleError("Server error: " + response.code() + " - " + response.message());
                }
                response.close();
            }
        });
    }

    private void handleSuccess(String output) {
        runOnUiThread(() -> {
            // Hide typing indicator and display the response
            messageList.remove(messageList.size() - 1);
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
            addResponse(output, Message.SENT_BY_BOT);
            if (isTextToSpeechEnabled) {
                textToSpeech.speak(output, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    private void handleError(String errorMessage) {
        runOnUiThread(() -> {
            // Hide typing indicator and show error message
            messageList.remove(messageList.size() - 1);
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
            addResponse("Error: " + errorMessage, Message.SENT_BY_BOT);
        });
    }

    private String getLastResponse() {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message message = messageList.get(i);
            if (message.getSentBy().equals(Message.SENT_BY_BOT)) {
                return message.getMessage();
            }
        }
        return "";
    }

    private void copyToClipboard(String text) {
        ClipData clip = ClipData.newPlainText("ChatBotResponse", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Response copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void shareResponse(String text) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Share response"));
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }
}
