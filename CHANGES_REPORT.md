# SARRAI – Change Report

## Summary
The root cause of `GENERATION DECODE FAILED: -1` is fixed. In the generation loop, `batch.n_tokens` was set to `0` and never set back to `1`. Every generated token was therefore sent to `llama_decode()` as an **empty batch**. llama.cpp rejects an empty batch (`n_tokens == 0`) and returns `-1`. This explains the logs: the prompt decoded fine, then the first generated-token decode failed. The 7-character "response" was the first sampled piece, Qwen3's `<think>` tag.

## Files changed

### `app/src/main/cpp/sarrai_text_engine.cpp`
- **Main fix:** set `batch.n_tokens = 1` before each generation `llama_decode()`.
- The position counter now only moves forward after a decode succeeds.
- **Success and failure are now separate.** A new `generation_error` flag means the log says "completed successfully" only when generation really completed. Otherwise it logs "TERMINATED BY ERROR" with the position and token, and the user sees an error or a cut-short message.
- Generation stops cleanly when the context window (2048) is full. Prompts that are too long are rejected before decoding.
- The prompt batch grows to fit prompts longer than 512 tokens. `n_batch` is now the full context size and `n_ubatch` is 512.
- `llama_backend_init()` now runs only once.
- Qwen3 thinking mode is turned off by adding `/no_think`. Any `<think>…</think>` block is removed, so users only see the answer. This also saves tokens on low-power hardware.
- `llama_token_to_piece(..., special=false)` keeps control tokens out of the reply text.

### `app/src/main/cpp/sarrai_jni.cpp`
- Removed the duplicate model and context loader. `stringFromJNI` could load a second copy of the model into RAM, separate from `SarraiTextEngine`. The function is kept but no longer loads anything.
- Replies are now passed to Java as raw UTF-8 bytes (`new String(byte[], "UTF-8")`) instead of `NewStringUTF`. `NewStringUTF` can crash the app when the model outputs emoji or other 4-byte characters.

### `app/src/main/cpp/CMakeLists.txt`
- Replaced the hard-coded `C:/SARRAI/llama.cpp` include paths with a path relative to the repository (`LLAMA_CPP_DIR`). The project now builds on any machine or clone location.
- Removed the UTF-8 BOM.

### `app/src/main/cpp/sarrai_text_engine.h`
- Removed the UTF-8 BOM. There are no changes to the code.

### `.gitignore` (new)
- Ignores generated build output: `.gradle/`, `build/`, `app/build/`, `app/.cxx/`, `local.properties`, and IDE files. Already-committed build folders stay in history. You can remove them with `git rm -r --cached .gradle app/build app/.cxx build`.

### `CHANGES_REPORT.md` (new)
- This report.

## How to verify on the device
1. Rebuild in Android Studio and install on the Huawei MGA-LX3.
2. Run `adb logcat -s SARRAI SARRAI_TEXT` and send a message.
3. Expected: `Generation completed successfully. Tokens: N ...` and a real reply in the chat. There should be no `GENERATION DECODE FAILED`.

## Not changed
`ChatActivity.java`, `MainActivity.java`, Gradle files, llama.cpp sources, the prebuilt `.so` libraries and the model.
