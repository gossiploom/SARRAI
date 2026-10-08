package com.sarrai.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the prompt sent through JNI to the C++ engine.
 * Order: system (honesty rules + user memories) -> recent history -> current message.
 * Keeps the total under a budget that fits the engine's 2048-token context,
 * leaving room for the reply. Token count is estimated (~4 chars per token).
 */
public final class ContextBuilder {

    /** Prompt budget in tokens; remainder of 2048 is left for the reply + template. */
    public static final int PROMPT_TOKEN_BUDGET = 1300;
    private static final int MEMORY_TOKEN_BUDGET = 350;

    private ContextBuilder() {}

    public static int estimateTokens(String s) { return s == null ? 0 : (s.length() + 3) / 4 + 4; }

    public static final String RULES =
            "You are SARRAI, a private AI assistant running fully offline on the user's device.\n"
            + "Rules:\n"
            + "- Be accurate. Only state facts you are confident are true.\n"
            + "- If you do not know or are unsure, say \"I don't know\" or \"I'm not sure\" instead of guessing. Never invent names, numbers, dates, quotes or sources.\n"
            + "- You have no internet access and cannot check current events; say so when asked about recent news.\n"
            + "- Use the facts under 'Known about the user' and the earlier conversation to answer questions about the user. If something is not there, say you don't have that information.\n"
            + "- Be concise and clear.";

    /** Returns [roles[], contents[]]. history must already include the current user message last. */
    public static String[][] build(List<SarraiStore.Memory> memories, List<String[]> history) {
        StringBuilder sys = new StringBuilder(RULES);
        if (memories != null && !memories.isEmpty()) {
            String query = history.isEmpty() ? "" : history.get(history.size() - 1)[1];
            List<SarraiStore.Memory> ranked = rank(memories, query);
            StringBuilder mem = new StringBuilder("\n\nKnown about the user (saved by the user):\n");
            int used = 0;
            for (SarraiStore.Memory m : ranked) {
                int t = estimateTokens(m.content);
                if (used + t > MEMORY_TOKEN_BUDGET) break;
                mem.append("- ").append(m.content).append('\n');
                used += t;
            }
            if (used > 0) sys.append(mem);
        }

        int budget = PROMPT_TOKEN_BUDGET - estimateTokens(sys.toString());
        List<String[]> kept = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; --i) {
            String[] msg = history.get(i);
            String content = msg[1];
            int t = estimateTokens(content);
            if (t > budget) {
                if (kept.isEmpty()) { // always keep the current message, truncated if huge
                    int chars = Math.max(200, budget * 4);
                    content = content.substring(Math.max(0, content.length() - chars));
                    kept.add(0, new String[]{msg[0], content});
                }
                break;
            }
            kept.add(0, msg);
            budget -= t;
        }
        // Qwen chat must not start with an assistant turn after system.
        while (kept.size() > 1 && !"user".equals(kept.get(0)[0])) kept.remove(0);

        String[] roles = new String[kept.size() + 1];
        String[] contents = new String[kept.size() + 1];
        roles[0] = "system"; contents[0] = sys.toString();
        for (int i = 0; i < kept.size(); ++i) { roles[i + 1] = kept.get(i)[0]; contents[i + 1] = kept.get(i)[1]; }
        return new String[][]{roles, contents};
    }

    /** Memories sharing words with the query come first; otherwise newest first. */
    private static List<SarraiStore.Memory> rank(List<SarraiStore.Memory> all, String query) {
        String[] words = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        List<SarraiStore.Memory> hit = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; --i) {
            SarraiStore.Memory m = all.get(i);
            String c = m.content.toLowerCase(Locale.ROOT);
            boolean match = false;
            for (String w : words) if (w.length() > 2 && c.contains(w)) { match = true; break; }
            (match ? hit : rest).add(m);
        }
        hit.addAll(rest);
        return hit;
    }

    /** Detects "remember ..." requests. Returns the fact to save, or null. */
    public static String parseRemember(String msg) {
        String t = msg.trim();
        String l = t.toLowerCase(Locale.ROOT);
        String[] prefixes = {"please remember that ", "please remember ", "remember that ", "remember: ", "remember ", "note that ", "don't forget that "};
        for (String p : prefixes) {
            if (l.startsWith(p)) {
                String fact = t.substring(p.length()).trim();
                fact = fact.replaceAll("[.!]+$", "").trim();
                if (fact.length() < 3) return null;
                // "my name is Dan" -> "User's name is Dan"
                if (fact.toLowerCase(Locale.ROOT).startsWith("my ")) fact = "User's " + fact.substring(3);
                else if (fact.toLowerCase(Locale.ROOT).startsWith("i ")) fact = "User " + fact.substring(2);
                else if (fact.toLowerCase(Locale.ROOT).startsWith("i'm ")) fact = "User is " + fact.substring(4);
                return fact;
            }
        }
        return null;
    }

    /** Detects "forget ..." requests. Returns the phrase to forget, "*" for everything, or null. */
    public static String parseForget(String msg) {
        String l = msg.trim().toLowerCase(Locale.ROOT).replaceAll("[.!]+$", "");
        if (l.equals("forget everything") || l.equals("forget all memories") || l.equals("clear your memory")) return "*";
        String[] prefixes = {"please forget that ", "please forget ", "forget that ", "forget about ", "forget "};
        for (String p : prefixes) {
            if (l.startsWith(p)) {
                String what = l.substring(p.length()).trim();
                if (what.startsWith("my ")) what = what.substring(3);
                return what.length() < 2 ? null : what;
            }
        }
        return null;
    }
}
