package com.authord.mkdocs.ports.topic

/**
 * Port for markdown file mutations synchronized with topic-tree/nav operations.
 *
 * Usage contract:
 * - Inputs: instance scope, relative markdown paths, and operation mode.
 * - Outputs: updated paths and success/failure results.
 * - Errors: [TopicSyncErrorCode.FILE_IO], [TopicSyncErrorCode.VALIDATION], and
 *   [TopicSyncErrorCode.INSTANCE_SCOPE].
 *
 * Usage example:
 * ```kotlin
 * gateway.renameMarkdownFile(instance, "old.md", "new.md")
 * gateway.rewriteRelativeMarkdownLinks(instance, "old.md", "new.md")
 * ```
 */
interface DocsFileGateway {
    /**
     * Creates a markdown file in the active docs scope.
     *
     * Inputs: [instance], docs_dir-relative [relativePath], and [initialContent].
     * Output: [TopicGatewayResult.Success] containing normalized created path.
     * Errors/failure modes: FILE_IO for write failures, VALIDATION for invalid paths.
     */
    fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String>

    /**
     * Deletes or recovers a markdown file according to [mode].
     *
     * Inputs: [instance], docs_dir-relative [relativePath], and delete [mode].
     * Output: [TopicGatewayResult.Success] containing affected path.
     * Errors/failure modes: FILE_IO for delete/recovery failures, VALIDATION for unsupported modes.
     */
    fun deleteMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        mode: TopicDeleteMode,
    ): TopicGatewayResult<String>

    /**
     * Renames a markdown file while preserving docs scope.
     *
     * Inputs: [instance], [fromRelativePath], [toRelativePath].
     * Output: [TopicGatewayResult.Success] containing final path.
     * Errors/failure modes: FILE_IO for rename failures, INSTANCE_SCOPE for out-of-scope paths.
     */
    fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String>

    /**
     * Moves a markdown file to a different docs_dir-relative location.
     *
     * Inputs: [instance], [fromRelativePath], [toRelativePath].
     * Output: [TopicGatewayResult.Success] containing destination path.
     * Errors/failure modes: FILE_IO for move failures, INSTANCE_SCOPE for cross-scope moves.
     */
    fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String>

    /**
     * Rewrites relative markdown links in docs scope for a rename/move operation.
     *
     * Inputs: [instance], [fromRelativePath], [toRelativePath].
     * Output: [TopicGatewayResult.Success] containing rewrite count.
     * Errors/failure modes: FILE_IO for document update failures, VALIDATION for malformed paths.
     */
    fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int>

    /**
     * Inserts or replaces the first Markdown H1 heading for [relativePath].
     *
     * Default behavior is a no-op success to preserve backward compatibility for
     * adapters that have not implemented heading synchronization.
     */
    fun upsertMarkdownTitleHeading(
        instance: TopicInstanceRef,
        relativePath: String,
        title: String,
    ): TopicGatewayResult<String> = TopicGatewayResult.Success(relativePath)
}
