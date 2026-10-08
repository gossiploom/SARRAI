package com.sarrai.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Chat history: start a new chat, continue the last one, open/rename/delete any chat. */
public class ConversationsActivity extends Activity {

    private final int cyan = Color.rgb(0, 229, 255);
    private final int dark = Color.rgb(5, 8, 14);
    private final int panel = Color.rgb(12, 18, 28);
    private final int muted = Color.rgb(150, 165, 180);

    private LinearLayout list;
    private SarraiStore store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        store = SarraiStore.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("CHATS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView back = new TextView(this);
        back.setText("<");
        back.setTextColor(cyan);
        back.setTextSize(28);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(45), dp(50)));
        root.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(10), 0, dp(12));
        TextView newChat = button("+ New Chat", true);
        newChat.setOnClickListener(v -> openChat(-1));
        TextView cont = button("Continue Last", false);
        cont.setOnClickListener(v -> {
            long id = store.latestConversationId();
            if (id < 0) Toast.makeText(this, "No previous chats yet.", Toast.LENGTH_SHORT).show();
            else openChat(id);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(0, 0, dp(8), 0);
        actions.addView(newChat, lp);
        actions.addView(cont, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);

        TextView hint = new TextView(this);
        hint.setText("Tap a chat to continue it. Long-press to rename or delete.");
        hint.setTextColor(muted);
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(8));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private void openChat(long id) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, id);
        startActivity(i);
    }

    private void refresh() {
        list.removeAllViews();
        List<SarraiStore.Conversation> all = store.listConversations();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No chats yet. Tap \"+ New Chat\" to start.");
            empty.setTextColor(muted);
            empty.setPadding(0, dp(20), 0, 0);
            list.addView(empty);
            return;
        }
        for (SarraiStore.Conversation c : all) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(panel);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.rgb(25, 55, 70));
            card.setBackground(bg);

            TextView t = new TextView(this);
            t.setText(c.title);
            t.setTextColor(Color.WHITE);
            t.setTextSize(16);
            t.setMaxLines(2);
            card.addView(t);
            TextView sub = new TextView(this);
            sub.setText(store.countMessages(c.id) + " messages  •  "
                    + DateFormat.format("dd MMM yyyy, HH:mm", c.updatedAt));
            sub.setTextColor(muted);
            sub.setTextSize(12);
            card.addView(sub);

            card.setOnClickListener(v -> openChat(c.id));
            card.setOnLongClickListener(v -> { showOptions(c); return true; });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(0, dp(5), 0, dp(5));
            list.addView(card, p);
        }
    }

    private void showOptions(SarraiStore.Conversation c) {
        new AlertDialog.Builder(this)
                .setTitle(c.title)
                .setItems(new String[]{"Open", "Rename", "Delete"}, (d, which) -> {
                    if (which == 0) openChat(c.id);
                    else if (which == 1) rename(c);
                    else confirmDelete(c);
                }).show();
    }

    private void rename(SarraiStore.Conversation c) {
        EditText in = new EditText(this);
        in.setText(c.title);
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this).setTitle("Rename chat").setView(in)
                .setPositiveButton("Save", (d, w) -> { store.renameConversation(c.id, in.getText().toString()); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmDelete(SarraiStore.Conversation c) {
        new AlertDialog.Builder(this).setTitle("Delete chat?")
                .setMessage("\"" + c.title + "\" and all its messages will be permanently deleted.")
                .setPositiveButton("Delete", (d, w) -> { store.deleteConversation(c.id); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    private TextView button(String label, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.BLACK : cyan);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? cyan : panel);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), cyan);
        b.setBackground(bg);
        return b;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
