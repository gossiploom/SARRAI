package com.sarrai.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only persistent storage for SARRAI (Android built-in SQLite).
 * Tables: conversations, messages, memories. No network, no cloud.
 * The schema is plain SQL so the future Windows version can reuse it.
 */
public class SarraiStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "sarrai.db";
    private static final int DB_VERSION = 1;
    private static SarraiStore instance;

    public static final class Conversation {
        public long id; public String title; public long createdAt; public long updatedAt;
    }
    public static final class Message {
        public long id; public long conversationId; public String role; public String content; public long createdAt;
    }
    public static final class Memory {
        public long id; public String content; public long createdAt; public long updatedAt;
    }

    public static synchronized SarraiStore get(Context ctx) {
        if (instance == null) instance = new SarraiStore(ctx.getApplicationContext());
        return instance;
    }

    private SarraiStore(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

    @Override
    public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conversation_id, id)");
        db.execSQL("CREATE TABLE memories (id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { /* future migrations */ }

    // ---------- Conversations ----------
    public long createConversation(String title) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", title == null || title.trim().isEmpty() ? "New chat" : title.trim());
        v.put("created_at", now); v.put("updated_at", now);
        return getWritableDatabase().insert("conversations", null, v);
    }

    public List<Conversation> listConversations() {
        List<Conversation> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,created_at,updated_at FROM conversations ORDER BY updated_at DESC", null)) {
            while (c.moveToNext()) {
                Conversation x = new Conversation();
                x.id = c.getLong(0); x.title = c.getString(1); x.createdAt = c.getLong(2); x.updatedAt = c.getLong(3);
                out.add(x);
            }
        }
        return out;
    }

    public Conversation getConversation(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,created_at,updated_at FROM conversations WHERE id=?", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return null;
            Conversation x = new Conversation();
            x.id = c.getLong(0); x.title = c.getString(1); x.createdAt = c.getLong(2); x.updatedAt = c.getLong(3);
            return x;
        }
    }

    public long latestConversationId() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM conversations ORDER BY updated_at DESC LIMIT 1", null)) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    public void renameConversation(long id, String title) {
        if (title == null || title.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("title", title.trim());
        getWritableDatabase().update("conversations", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteConversation(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "conversation_id=?", new String[]{String.valueOf(id)});
        db.delete("conversations", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- Messages ----------
    public long addMessage(long conversationId, String role, String content) {
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("conversation_id", conversationId); v.put("role", role);
        v.put("content", content); v.put("created_at", now);
        long id = db.insert("messages", null, v);
        ContentValues u = new ContentValues();
        u.put("updated_at", now);
        db.update("conversations", u, "id=?", new String[]{String.valueOf(conversationId)});
        return id;
    }

    public List<Message> getMessages(long conversationId) {
        List<Message> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,conversation_id,role,content,created_at FROM messages WHERE conversation_id=? ORDER BY id ASC",
                new String[]{String.valueOf(conversationId)})) {
            while (c.moveToNext()) {
                Message m = new Message();
                m.id = c.getLong(0); m.conversationId = c.getLong(1); m.role = c.getString(2);
                m.content = c.getString(3); m.createdAt = c.getLong(4);
                out.add(m);
            }
        }
        return out;
    }

    public int countMessages(long conversationId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE conversation_id=?", new String[]{String.valueOf(conversationId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---------- Memories (only ever written by explicit user action) ----------
    public long addMemory(String content) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("content", content.trim()); v.put("created_at", now); v.put("updated_at", now);
        return getWritableDatabase().insert("memories", null, v);
    }

    public void updateMemory(long id, String content) {
        ContentValues v = new ContentValues();
        v.put("content", content.trim()); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("memories", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteMemory(long id) {
        getWritableDatabase().delete("memories", "id=?", new String[]{String.valueOf(id)});
    }

    public void clearMemories() { getWritableDatabase().delete("memories", null, null); }

    public List<Memory> listMemories() {
        List<Memory> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,content,created_at,updated_at FROM memories ORDER BY id ASC", null)) {
            while (c.moveToNext()) {
                Memory m = new Memory();
                m.id = c.getLong(0); m.content = c.getString(1); m.createdAt = c.getLong(2); m.updatedAt = c.getLong(3);
                out.add(m);
            }
        }
        return out;
    }

    /** Deletes memories containing the phrase (case-insensitive). Returns count removed. */
    public int forgetMatching(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) return 0;
        return getWritableDatabase().delete("memories", "LOWER(content) LIKE ?",
                new String[]{"%" + phrase.trim().toLowerCase() + "%"});
    }
}
