package com.sarrai.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int cyan = Color.rgb(0, 229, 255);
    private int dark = Color.rgb(5, 8, 14);
    private int panel = Color.rgb(12, 18, 28);
    private int text = Color.WHITE;
    private int muted = Color.rgb(150, 165, 180);

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
        header.setPadding(dp(18), dp(14), dp(18), dp(10));

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.sarrai.app.R.drawable.sarrai_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);

        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("SARRAI");
        title.setTextColor(text);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("Personal Artificial Intelligence");
        subtitle.setTextColor(cyan);
        subtitle.setTextSize(12);

        titleBox.addView(title);
        titleBox.addView(subtitle);

        header.addView(titleBox,
                new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(header);

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(25, 55, 70));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(22), dp(18), dp(25));

        TextView welcome = new TextView(this);
        welcome.setText("Welcome to SARRAI");
        welcome.setTextColor(text);
        welcome.setTextSize(24);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        content.addView(welcome);

        TextView description = new TextView(this);
        description.setText(
                "Your private AI assistant, designed to run locally on your device."
        );
        description.setTextColor(muted);
        description.setTextSize(14);
        description.setPadding(0, dp(6), 0, dp(24));

        content.addView(description);

        content.addView(createSection(
                "CHAT",
                "Talk with SARRAI using the local AI engine.",
                "Open Chat",
                v -> startActivity(new Intent(this, ChatActivity.class))
        ));

        content.addView(createSection(
                "KNOWLEDGE",
                "Add and manage documents that SARRAI can learn from.",
                "Manage Knowledge",
                v -> showMessage("Knowledge section coming next.")
        ));

        content.addView(createSection(
                "MEMORY",
                "Control information SARRAI is allowed to remember locally.",
                "Open Memory",
                v -> showMessage("Memory section coming next.")
        ));

        content.addView(createSection(
                "SETTINGS",
                "Configure the local model, storage and application preferences.",
                "Open Settings",
                v -> showMessage("Settings section coming next.")
        ));

        Space bottomSpace = new Space(this);
        content.addView(bottomSpace,
                new LinearLayout.LayoutParams(1, dp(20)));

        TextView status = new TextView(this);
        status.setText("Ã¢â€”Â  LOCAL MODE  Ã¢â‚¬Â¢  AI ENGINE READY");
        status.setTextColor(cyan);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        content.addView(status);

        scroll.addView(content);
        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private LinearLayout createSection(
            String heading,
            String description,
            String buttonText,
            View.OnClickListener listener) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setColor(panel);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(25, 55, 70));
        card.setBackground(background);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);

        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextColor(cyan);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(muted);
        desc.setTextSize(14);
        desc.setPadding(0, dp(6), 0, dp(12));

        card.addView(desc);

        TextView button = new TextView(this);
        button.setText(buttonText);
        button.setTextColor(Color.BLACK);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setOnClickListener(listener);

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(cyan);
        buttonBackground.setCornerRadius(dp(8));
        button.setBackground(buttonBackground);

        card.addView(button,
                new LinearLayout.LayoutParams(-1, dp(42)));

        return card;
    }

    private void showMessage(String message) {
        android.widget.Toast.makeText(
                this,
                message,
                android.widget.Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
