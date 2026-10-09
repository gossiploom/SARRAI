# SARRAI – Local Conversation & Memory System: Change Report

All data stays on the phone (Android built-in SQLite, `sarrai.db`). No cloud, no new dependencies.
Inference still runs through the existing Java → JNI → C++ `SarraiTextEngine` → llama.cpp pipeline; native code is unchanged.

## New files
| File | Purpose |
|---|---|
| `app/src/main/java/com/sarrai/app/SarraiStore.java` | Local SQLite store. Tables `conversations` (id, title, created_at, updated_at), `messages` (id, conversation_id, role, content, created_at, cascade delete), `memories` (id, content, created_at, updated_at). Create/list/rename/delete chats, save/load messages, add/edit/delete/clear/forget memories. |
| `app/src/main/java/com/sarrai/app/ContextBuilder.java` | Builds each prompt: a **system** message with honesty rules + relevant saved memories, then as much recent history as fits (~1300-token prompt budget, leaving room for the reply in the 2048-token context). Also parses "remember …" / "forget …" requests. |
| `app/src/main/java/com/sarrai/app/ConversationsActivity.java` | Chat history screen: **+ New Chat**, **Continue Last**, tap to reopen, long-press to rename or delete. |
| `app/src/main/java/com/sarrai/app/MemoryActivity.java` | Memory screen: add, tap to edit, long-press to delete, Clear All. |
| `CHANGES_REPORT_MEMORY.md` | This report. |

## Modified files
| File | Change |
|---|---|
| `ChatActivity.java` | Saves every user and SARRAI message to disk; reopens a chat with its full history (`conversation_id` extra); chat title = first message (40 chars), renameable; handles "Remember my name is Dan", "Forget my name", "Forget everything" locally without calling the model; uses `ContextBuilder` so memories and honesty rules reach the engine; auto-scrolls; text is selectable. Error replies are not saved to the history sent to the model. |
| `MainActivity.java` | CHAT opens the history screen, MEMORY opens the memory screen; fixed the garbled "LOCAL MODE" status text. |
| `AndroidManifest.xml` | Registered `ConversationsActivity` and `MemoryActivity`; removed the byte-order mark. |

## Factual answers
The system rules tell SARRAI to state only facts it is confident in, say "I don't know" instead of guessing, never invent names/numbers/sources, and admit it has no internet. Note: a small offline model can still make mistakes; these rules reduce, but cannot fully eliminate, wrong answers.

## Memory policy
Conversation messages are **never** promoted to long-term memory automatically. Memories are added only by the user (Memory screen or an explicit "remember …" message).

## How to test
1. `./gradlew.bat assembleDebug`, install, open SARRAI → CHAT → **+ New Chat**.
2. Send "Remember my name is Dan" → confirmation; check MEMORY screen.
3. Start another new chat, ask "What is my name?" → should answer Dan.
4. Close the app fully, reopen → CHAT → **Continue Last** → previous messages are restored.
5. Long-press a chat to rename/delete it. Logcat: `adb logcat -s SARRAI SARRAI_TEXT`.

## Commits
`d0f63cd` storage · `e4905d0` context builder + history screen · `73dcb3b` memory screen · `6bfd2fa` chat screen · final commit: home screen, manifest, this report.
