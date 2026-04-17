package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists instance registration/selection state across service lifecycles for one project root.
 */
data class PersistedInstanceRegistryState(
    val instances: List<TopicInstanceRef>,
    val activeInstanceId: String?,
)

/**
 * Default in-memory implementation of [InstanceRegistryPort].
 *
 * Behavior:
 * - Discovers root `mkdocs.yml`/`mkdocs.yaml` as default instance.
 * - Accepts explicit add-instance registrations.
 * - Persists last selected instance and registered set per project root.
 */
class InstanceRegistryService(
    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs(),
    private val stateStore: InstanceRegistryStateStore = InMemoryInstanceRegistryStateStore,
) : InstanceRegistryPort {
    private var loadedProjectKey: String? = null
    private val instancesById = linkedMapOf<String, TopicInstanceRef>()
    private var activeInstanceId: String? = null

    override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> {
        val projectRoot = absoluteNormalizedPath(projectRootPath)
        if (projectRoot.isBlank()) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Project root path is blank"),
            )
        }

        loadProjectState(projectRoot)
        val rootPath = Path.of(projectRoot)
        val configPath = sequenceOf("mkdocs.yml", "mkdocs.yaml")
            .map(rootPath::resolve)
            .firstOrNull(Files::exists)
            ?: return TopicGatewayResult.Success(null)

        val docsDirPath = resolveDocsDirPath(configPath)
        val defaultInstance = TopicInstanceRef(
            instanceId = DEFAULT_INSTANCE_ID,
            configPath = normalizeAbsolutePath(configPath),
            docsDirPath = normalizeAbsolutePath(docsDirPath),
        )
        instancesById[DEFAULT_INSTANCE_ID] = defaultInstance
        if (activeInstanceId == null) {
            activeInstanceId = DEFAULT_INSTANCE_ID
        }
        persistLoadedState()
        return TopicGatewayResult.Success(defaultInstance)
    }

    override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> {
        val normalizedConfigPath = absoluteNormalizedPath(instance.configPath)
        if (normalizedConfigPath.isBlank()) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Config path must not be blank"),
            )
        }
        if (!normalizedConfigPath.endsWith("mkdocs.yml") && !normalizedConfigPath.endsWith("mkdocs.yaml")) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Config path must target mkdocs.yml or mkdocs.yaml"),
            )
        }
        val configFilePath = Path.of(normalizedConfigPath)
        if (!Files.exists(configFilePath)) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Config file does not exist: $normalizedConfigPath"),
            )
        }

        ensureProjectContext(configFilePath.parent?.parent ?: configFilePath.parent)
        val normalizedInstance = instance.copy(
            configPath = normalizedConfigPath,
            docsDirPath = absoluteNormalizedPath(instance.docsDirPath),
        )
        instancesById[instance.instanceId] = normalizedInstance
        if (activeInstanceId == null) {
            activeInstanceId = instance.instanceId
        }
        persistLoadedState()
        return TopicGatewayResult.Success(Unit)
    }

    override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> {
        return TopicGatewayResult.Success(
            instancesById.values.sortedBy { it.instanceId },
        )
    }

    override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        val instance = instancesById[instanceId]
            ?: return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Unknown instance: $instanceId"),
            )
        activeInstanceId = instanceId
        persistLoadedState()
        return TopicGatewayResult.Success(instance)
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(activeInstanceId?.let(instancesById::get))
    }

    private fun ensureProjectContext(projectRootPath: Path?) {
        if (projectRootPath == null) {
            return
        }
        val projectKey = normalizeAbsolutePath(projectRootPath)
        if (loadedProjectKey == null) {
            loadProjectState(projectKey)
        }
    }

    private fun loadProjectState(projectRootPath: String) {
        val projectKey = pathPolicy.comparisonKey(projectRootPath)
        if (loadedProjectKey == projectKey) {
            return
        }
        val persisted = stateStore.load(projectKey)
        loadedProjectKey = projectKey
        instancesById.clear()
        persisted?.instances?.forEach { instance ->
            instancesById[instance.instanceId] = instance
        }
        activeInstanceId = persisted?.activeInstanceId
    }

    private fun persistLoadedState() {
        val projectKey = loadedProjectKey ?: return
        stateStore.save(
            projectKey = projectKey,
            state = PersistedInstanceRegistryState(
                instances = instancesById.values.toList(),
                activeInstanceId = activeInstanceId,
            ),
        )
    }

    private fun resolveDocsDirPath(configPath: Path): Path {
        val docsDir = readDocsDirValue(configPath) ?: return resolveImplicitDocsDirPath(configPath)
        val docsDirPath = runCatching { Path.of(docsDir) }.getOrNull() ?: return resolveImplicitDocsDirPath(configPath)
        return if (docsDirPath.isAbsolute) docsDirPath.normalize() else configPath.parent.resolve(docsDirPath).normalize()
    }

    private fun readDocsDirValue(configPath: Path): String? {
        if (!Files.exists(configPath)) {
            return null
        }
        val regex = Regex("""^\s*docs_dir\s*:\s*["']?([^"'\r\n#]+)["']?\s*(?:#.*)?$""")
        return Files.readAllLines(configPath)
            .asSequence()
            .mapNotNull { line -> regex.find(line)?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun absoluteNormalizedPath(path: String): String {
        return normalizeAbsolutePath(Path.of(path))
    }

    private fun normalizeAbsolutePath(path: Path): String {
        return pathPolicy.normalize(
            path.toAbsolutePath().normalize().toString(),
        )
    }

    companion object {
        private const val DEFAULT_INSTANCE_ID = "default"
    }
}

/**
 * Persistence boundary for [InstanceRegistryService] state snapshots.
 */
interface InstanceRegistryStateStore {
    /**
     * Loads persisted state for a project key.
     */
    fun load(projectKey: String): PersistedInstanceRegistryState?

    /**
     * Saves persisted state for a project key.
     */
    fun save(projectKey: String, state: PersistedInstanceRegistryState)
}

private object InMemoryInstanceRegistryStateStore : InstanceRegistryStateStore {
    private val stateByProjectKey = ConcurrentHashMap<String, PersistedInstanceRegistryState>()

    override fun load(projectKey: String): PersistedInstanceRegistryState? {
        return stateByProjectKey[projectKey]
    }

    override fun save(projectKey: String, state: PersistedInstanceRegistryState) {
        stateByProjectKey[projectKey] = state
    }
}
