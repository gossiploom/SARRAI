package com.sarrai.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ChatActivity extends Activity {

    private final int cyan = Color.rgb(0, 229, 255);
    private final int dark = Color.rgb(5, 8, 14);
    private final int panel = Color.rgb(12, 18, 28);
    private final int text = Color.WHITE;
    private final int muted = Color.rgb(150, 165, 180);

    private LinearLayout messages;
    private EditText input;
    private boolean generating = false;

    static {
        System.loadLibrary("sarrai");
    }

    private native String nativeGenerate(String message);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.sarrai_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);

        header.addView(
                logo,
                new LinearLayout.LayoutParams(dp(48), dp(48))
        );

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("SARRAI");
        title.setTextColor(text);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView status = new TextView(this);
        status.setText("LOCAL AI - OFFLINE");
        status.setTextColor(cyan);
        status.setTextSize(11);

        titleBox.addView(title);
        titleBox.addView(status);

        header.addView(
                titleBox,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        TextView back = new TextView(this);
        back.setText("<");
        back.setTextColor(cyan);
        back.setTextSize(28);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());

        header.addView(
                back,
                new LinearLayout.LayoutParams(dp(45), dp(50))
        );

        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(25, 55, 70));

        root.addView(
                divider,
                new LinearLayout.LayoutParams(-1, dp(1))
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(
                dp(14),
                dp(18),
                dp(14),
                dp(18)
        );

        addMessage(
                "Hello. I am SARRAI.\n\n"
                        + "I am running locally on this device. "
                        + "Send me a message to begin.",
                false
        );

        scroll.addView(messages);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(10)
        );

        input = new EditText(this);
        input.setHint("Message SARRAI...");
        input.setHintTextColor(muted);
        input.setTextColor(text);
        input.setTextSize(15);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setPadding(
                dp(14),
                dp(8),
                dp(14),
                dp(8)
        );

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(panel);
        inputBg.setCornerRadius(dp(12));
        inputBg.setStroke(
                dp(1),
                Color.rgb(25, 55, 70)
        );

        input.setBackground(inputBg);

        composer.addView(
                input,
                new LinearLayout.LayoutParams(0, dp(52), 1)
        );

        TextView send = new TextView(this);
        send.setText(">");
        send.setTextColor(Color.BLACK);
        send.setTextSize(22);
        send.setGravity(Gravity.CENTER);

        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(cyan);
        sendBg.setCornerRadius(dp(12));

        send.setBackground(sendBg);

        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(
                        dp(52),
                        dp(52)
                );

        sendParams.setMargins(dp(8), 0, 0, 0);

        composer.addView(send, sendParams);

        send.setOnClickListener(v -> sendMessage(send));

        root.addView(composer);

        setContentView(root);
    }

    private void sendMessage(TextView sendButton) {

        if (generating) {
            return;
        }

        String message =
                input.getText().toString().trim();

        if (message.isEmpty()) {
            return;
        }

        addMessage(message, true);
        input.setText("");

        generating = true;
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.5f);

        addMessage("SARRAI is thinking...", false);

        Thread worker = new Thread(() -> {

            String response;

            try {
                response = nativeGenerate(message);
            } catch (Throwable error) {
                response =
                        "ERROR: Local AI engine failed.\n\n"
                                + error.getClass().getSimpleName()
                                + ": "
                                + error.getMessage();
            }

            final String finalResponse = response;

            runOnUiThread(() -> {

                removeLastMessage();

                addMessage(finalResponse, false);

                generating = false;
                sendButton.setEnabled(true);
                sendButton.setAlpha(1.0f);
            });

        });

        worker.start();
    }

    private void addMessage(
            String message,
            boolean user) {

        TextView bubble = new TextView(this);

        bubble.setText(message);
        bubble.setTextSize(15);
        bubble.setTextColor(
                user ? Color.BLACK : text
        );

        bubble.setPadding(
                dp(14),
                dp(11),
                dp(14),
                dp(11)
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                user ? cyan : panel
        );

        bg.setCornerRadius(dp(14));

        bubble.setBackground(bg);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        params.setMargins(
                user ? dp(45) : dp(0),
                dp(5),
                user ? dp(0) : dp(45),
                dp(5)
        );

        bubble.setLayoutParams(params);

        messages.addView(bubble);
    }

    private void removeLastMessage() {

        int count = messages.getChildCount();

        if (count > 0) {
            messages.removeViewAt(count - 1);
        }
    }

    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }
}
