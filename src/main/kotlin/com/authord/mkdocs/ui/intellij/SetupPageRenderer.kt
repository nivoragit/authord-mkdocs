package com.authord.mkdocs.ui.intellij

/**
 * Renders setup HTML shown when no configuration file exists in the project root.
 */
class SetupPageRenderer {
    fun render(
        createProjectBridgeScript: String,
        suggestedProjectName: String = AuthordUiBundle.message("activation.default.siteName"),
    ): String {
        val resolvedProjectName = suggestedProjectName.trim().ifBlank {
            AuthordUiBundle.message("activation.default.siteName")
        }
        val emptyStateTitle = escapeHtmlText(AuthordUiBundle.message("setup.emptyState.title"))
        val addDocumentationLabel = escapeHtmlText(AuthordUiBundle.message("setup.button.add"))
        val gettingStartedLabel = escapeHtmlText(AuthordUiBundle.message("setup.link.gettingStarted"))
        val projectNamePrompt = escapeJavaScriptString(AuthordUiBundle.message("setup.prompt.projectName.message"))
        val projectNameRequired = escapeJavaScriptString(AuthordUiBundle.message("setup.error.projectNameRequired"))
        val escapedProjectName = escapeJavaScriptString(resolvedProjectName)
        val gettingStartedUrl = escapeHtmlAttribute(AuthordUiBundle.message("setup.gettingStarted.url"))
        return """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>$emptyStateTitle</title>
                <style>
                  :root {
                    --bg: #11141b;
                    --muted: #6e7683;
                    --accent: #6ea1ff;
                    --help: #76808d;
                  }
                  @media (prefers-color-scheme: light) {
                    :root {
                      --bg: #f5f7fb;
                      --muted: #5f6673;
                      --accent: #3f79e8;
                      --help: #6d7684;
                    }
                  }
                  * { box-sizing: border-box; }
                  html, body {
                    margin: 0;
                    padding: 0;
                    height: 100%;
                    width: 100%;
                    background: var(--bg);
                    font-family: -apple-system, "Segoe UI", "SF Pro Text", "Helvetica Neue", Arial, sans-serif;
                  }
                  .page {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100%;
                    padding: 24px 12px;
                  }
                  .content {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    transform: translateY(-6px);
                  }
                  .empty-state {
                    margin: 0;
                    color: var(--muted);
                    font-size: 16px;
                    line-height: 1.3;
                    font-weight: 500;
                  }
                  .add-documentation {
                    margin: 16px 0 0;
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    border: 0;
                    background: none;
                    color: var(--accent);
                    font-size: 16px;
                    line-height: 1.3;
                    font-weight: 500;
                    padding: 0;
                    cursor: pointer;
                  }
                  .add-documentation:disabled {
                    cursor: default;
                    opacity: 0.75;
                  }
                  .add-documentation .chevron {
                    width: 7px;
                    height: 7px;
                    border-right: 2px solid currentColor;
                    border-bottom: 2px solid currentColor;
                    transform: rotate(45deg) translateY(-2px);
                  }
                  .getting-started {
                    margin-top: 42px;
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    color: var(--accent);
                    font-size: 16px;
                    line-height: 1.3;
                    font-weight: 500;
                    text-decoration: none;
                  }
                  .help-icon {
                    width: 28px;
                    height: 28px;
                    border-radius: 999px;
                    border: 2px solid var(--help);
                    color: var(--help);
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 18px;
                    line-height: 1;
                    font-weight: 400;
                  }
                  .add-documentation:focus-visible,
                  .getting-started:focus-visible {
                    outline: 2px solid var(--accent);
                    outline-offset: 4px;
                    border-radius: 4px;
                  }
                </style>
              </head>
              <body>
                <main class="page">
                  <section class="content">
                    <p class="empty-state">$emptyStateTitle</p>
                    <button id="add-documentation-button" class="add-documentation" type="button" aria-label="$addDocumentationLabel">
                      <span>$addDocumentationLabel</span>
                      <span class="chevron" aria-hidden="true"></span>
                    </button>
                    <a
                      id="getting-started-link"
                      class="getting-started"
                      href="$gettingStartedUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <span class="help-icon" aria-hidden="true">?</span>
                      <span>$gettingStartedLabel</span>
                    </a>
                  </section>
                </main>
                <script>
                  (function() {
                    var button = document.getElementById("add-documentation-button");
                    var submitted = false;
                    var projectName = "$escapedProjectName";
                    var projectNamePrompt = "$projectNamePrompt";
                    var projectNameRequired = "$projectNameRequired";

                    function setLoading(loading) {
                      if (!button) return;
                      if (loading) {
                        button.disabled = true;
                      } else {
                        button.disabled = false;
                      }
                    }

                    function submit() {
                      if (submitted) return;
                      var enteredName = window.prompt(projectNamePrompt, projectName);
                      if (enteredName === null) {
                        return;
                      }
                      var normalizedName = String(enteredName || "").trim();
                      if (!normalizedName) {
                        if (window.alert) {
                          window.alert(projectNameRequired);
                        }
                        return;
                      }
                      submitted = true;
                      setLoading(true);
                      var payload = encodeURIComponent(normalizedName);
                      ${createProjectBridgeScript}
                    }

                    if (button) {
                      button.addEventListener("click", function(event) {
                        event.preventDefault();
                        submit();
                      });
                      button.addEventListener("keydown", function(event) {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          submit();
                        }
                      });
                    }
                  })();
                </script>
              </body>
            </html>
        """.trimIndent()
    }

    private fun escapeJavaScriptString(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\'' -> append("\\'")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun escapeHtmlAttribute(value: String): String {
        return escapeHtmlText(value).replace("\"", "&quot;")
    }

    private fun escapeHtmlText(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
