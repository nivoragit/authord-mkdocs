package com.authord.mkdocs.ui.intellij

/**
 * Renders setup HTML shown when no mkdocs config exists in the project root.
 */
class SetupPageRenderer {
    fun render(createProjectBridgeScript: String): String {
        return """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Create MkDocs Project</title>
                <style>
                  :root {
                    --bg: #f6f8fc;
                    --panel: #ffffff;
                    --text: #1f2937;
                    --muted: #6b7280;
                    --border: #dbe3f3;
                    --primary: #2065d1;
                    --primary-strong: #174ca0;
                    --input-bg: #ffffff;
                    --code-bg: #eef2f9;
                    --icon-color: #2065d1;
                    --success: #059669;
                  }
                  @media (prefers-color-scheme: dark) {
                    :root {
                      --bg: #1e1e2e;
                      --panel: #2a2a3c;
                      --text: #cdd6f4;
                      --muted: #9399b2;
                      --border: #45475a;
                      --primary: #89b4fa;
                      --primary-strong: #74c7ec;
                      --input-bg: #313244;
                      --code-bg: #313244;
                      --icon-color: #89b4fa;
                      --success: #a6e3a1;
                    }
                  }
                  * { box-sizing: border-box; }
                  html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    font-family: -apple-system, "Segoe UI", "SF Pro Text", "Helvetica Neue", Arial, sans-serif;
                    background:
                      radial-gradient(ellipse at 10% 20%, rgba(32,101,209,0.08) 0, transparent 50%),
                      radial-gradient(ellipse at 90% 80%, rgba(32,101,209,0.06) 0, transparent 50%),
                      var(--bg);
                    color: var(--text);
                  }
                  .page {
                    min-height: 100%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 24px;
                  }
                  .panel {
                    width: min(480px, 100%);
                    border: 1px solid var(--border);
                    border-radius: 16px;
                    background: var(--panel);
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
                    padding: 32px;
                    animation: fadeSlideUp 0.35s ease-out;
                  }
                  @keyframes fadeSlideUp {
                    from { opacity: 0; transform: translateY(12px); }
                    to   { opacity: 1; transform: translateY(0); }
                  }
                  .icon-row {
                    margin-bottom: 20px;
                  }
                  .icon-row svg {
                    width: 40px;
                    height: 40px;
                    color: var(--icon-color);
                  }
                  h1 {
                    margin: 0 0 8px;
                    font-size: 22px;
                    font-weight: 700;
                    line-height: 1.3;
                    letter-spacing: -0.01em;
                  }
                  .subtitle {
                    margin: 0 0 24px;
                    color: var(--muted);
                    font-size: 14px;
                    line-height: 1.5;
                  }
                  .subtitle code {
                    background: var(--code-bg);
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-size: 13px;
                    font-family: "JetBrains Mono", "Fira Code", "Cascadia Code", monospace;
                  }
                  label {
                    display: block;
                    font-size: 12px;
                    font-weight: 600;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                    color: var(--muted);
                    margin-bottom: 6px;
                  }
                  .input-group {
                    display: flex;
                    gap: 8px;
                    margin-bottom: 6px;
                  }
                  input {
                    flex: 1;
                    min-width: 0;
                    border: 1px solid var(--border);
                    border-radius: 10px;
                    padding: 10px 14px;
                    font-size: 14px;
                    color: var(--text);
                    background: var(--input-bg);
                    outline: none;
                    transition: border-color 0.15s, box-shadow 0.15s;
                  }
                  input:focus {
                    border-color: var(--primary);
                    box-shadow: 0 0 0 3px rgba(32, 101, 209, 0.15);
                  }
                  input:disabled {
                    opacity: 0.6;
                    cursor: not-allowed;
                  }
                  button {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    border: 0;
                    border-radius: 10px;
                    background: var(--primary);
                    color: #fff;
                    font-weight: 600;
                    font-size: 14px;
                    padding: 10px 18px;
                    cursor: pointer;
                    white-space: nowrap;
                    transition: background 0.15s, transform 0.08s, opacity 0.15s;
                  }
                  button:hover:not(:disabled) { background: var(--primary-strong); }
                  button:active:not(:disabled) { transform: translateY(1px); }
                  button:disabled {
                    opacity: 0.7;
                    cursor: not-allowed;
                  }
                  .spinner {
                    display: none;
                    width: 14px;
                    height: 14px;
                    border: 2px solid rgba(255,255,255,0.3);
                    border-top-color: #fff;
                    border-radius: 50%;
                    animation: spin 0.6s linear infinite;
                  }
                  @keyframes spin {
                    to { transform: rotate(360deg); }
                  }
                  button.loading .spinner { display: inline-block; }
                  button.loading .btn-label { display: none; }
                  .hint {
                    font-size: 12px;
                    color: var(--muted);
                    line-height: 1.4;
                  }
                  .hint kbd {
                    display: inline-block;
                    background: var(--code-bg);
                    border: 1px solid var(--border);
                    border-radius: 4px;
                    padding: 1px 5px;
                    font-size: 11px;
                    font-family: inherit;
                  }
                  @media (prefers-color-scheme: dark) {
                    input:focus {
                      box-shadow: 0 0 0 3px rgba(137, 180, 250, 0.2);
                    }
                    button { color: #1e1e2e; }
                    .spinner {
                      border-color: rgba(30,30,46,0.3);
                      border-top-color: #1e1e2e;
                    }
                  }
                </style>
              </head>
              <body>
                <main class="page">
                  <section class="panel">
                    <div class="icon-row">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                        <polyline points="14 2 14 8 20 8"/>
                        <line x1="12" y1="18" x2="12" y2="12"/>
                        <line x1="9" y1="15" x2="15" y2="15"/>
                      </svg>
                    </div>
                    <h1>Create Documentation</h1>
                    <p class="subtitle">              
                      Create a new project to get started.
                    </p>
                    <label for="project-name-input">Project name</label>
                    <div class="input-group">
                      <input id="project-name-input" type="text" value="my-project"
                             maxlength="64" placeholder="e.g. my-project" autocomplete="off" spellcheck="false" />
                      <button id="create-project-button" type="button">
                        <span class="btn-label">Create</span>
                        <span class="spinner"></span>
                      </button>
                    </div>
                    <p class="hint">
                      Press <kbd>Enter</kbd> or click Create.
                      Files will be added to the current project root.
                    </p>
                  </section>
                </main>
                <script>
                  (function() {
                    var input = document.getElementById("project-name-input");
                    var button = document.getElementById("create-project-button");
                    var submitted = false;

                    function setLoading(loading) {
                      if (loading) {
                        button.classList.add("loading");
                        button.disabled = true;
                        input.disabled = true;
                      } else {
                        button.classList.remove("loading");
                        button.disabled = false;
                        input.disabled = false;
                      }
                    }

                    function submit() {
                      if (submitted) return;
                      submitted = true;
                      setLoading(true);
                      var value = ((input && input.value) || "").trim() || "my-project";
                      var payload = encodeURIComponent(value);
                      ${createProjectBridgeScript}
                    }

                    if (button) {
                      button.addEventListener("click", submit);
                    }
                    if (input) {
                      input.addEventListener("keydown", function(event) {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          submit();
                        }
                      });
                      input.focus();
                      input.select();
                    }
                  })();
                </script>
              </body>
            </html>
        """.trimIndent()
    }
}
