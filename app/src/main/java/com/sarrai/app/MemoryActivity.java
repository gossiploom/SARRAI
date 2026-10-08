package com.sarrai.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Long-term personal memory. Only the user adds, edits or deletes entries. */
public class MemoryActivity extends Activity {

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
        title.setText("MEMORY");
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

        TextView info = new TextView(this);
        info.setText("Facts SARRAI remembers across all chats. Stored only on this phone. "
                + "You can also say \"Remember my name is Dan\" or \"Forget my name\" in a chat.");
        info.setTextColor(muted);
        info.setTextSize(12);
        info.setPadding(0, dp(4), 0, dp(10));
        root.addView(info);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, 0, 0, dp(10));
        TextView add = button("+ Add Memory", true);
        add.setOnClickListener(v -> edit(null));
        TextView clear = button("Clear All", false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Clear all memories?")
                .setMessage("SARRAI will forget everything saved here. This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> { store.clearMemories(); refresh(); })
                .setNegativeButton("Cancel", null).show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(0, 0, dp(8), 0);
        actions.addView(add, lp);
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        list.removeAllViews();
        List<SarraiStore.Memory> all = store.listMemories();
        if (all.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("No memories saved yet.");
            e.setTextColor(muted);
            e.setPadding(0, dp(20), 0, 0);
            list.addView(e);
            return;
        }
        for (SarraiStore.Memory m : all) {
            TextView row = new TextView(this);
            row.setText(m.content);
            row.setTextColor(Color.WHITE);
            row.setTextSize(15);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(panel);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.rgb(25, 55, 70));
            row.setBackground(bg);
            row.setOnClickListener(v -> edit(m));
            row.setOnLongClickListener(v -> { confirmDelete(m); return true; });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.setMargins(0, dp(5), 0, dp(5));
            list.addView(row, p);
        }
    }

    private void edit(SarraiStore.Memory m) {
        EditText in = new EditText(this);
        in.setHint("e.g. My name is Dan");
        if (m != null) { in.setText(m.content); in.setSelection(in.getText().length()); }
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(m == null ? "Add memory" : "Edit memory").setView(in)
                .setPositiveButton("Save", (x, w) -> {
                    String t = in.getText().toString().trim();
                    if (t.isEmpty()) return;
                    if (m == null) store.addMemory(t); else store.updateMemory(m.id, t);
                    refresh();
                })
                .setNegativeButton("Cancel", null);
        if (m != null) d.setNeutralButton("Delete", (x, w) -> { store.deleteMemory(m.id); refresh(); });
        d.show();
    }

    private void confirmDelete(SarraiStore.Memory m) {
        new AlertDialog.Builder(this).setTitle("Delete memory?").setMessage(m.content)
                .setPositiveButton("Delete", (d, w) -> { store.deleteMemory(m.id); refresh(); })
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
