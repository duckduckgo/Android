import com.asana.Client
import kotlinx.coroutines.runBlocking
import java.io.File
import org.yaml.snakeyaml.Yaml

object AsanaSync {
    private val asanaToken = System.getenv("ASANA_ACCESS_TOKEN")
    private val taskName = System.getenv("ASANA_TASK_NAME")
    private val githubUsername = System.getenv("GITHUB_USERNAME")
    private val projectId = System.getenv("ASANA_PROJECT_ID")

    private val asanaClient = Client.accessToken(asanaToken)

    private fun parseUserMap(): Map<String, String> {
        val actionPath = System.getenv("GITHUB_ACTION_PATH") ?: "."
        val userMapFile = File("$actionPath/github_asana_mapping.yml")

        return try {
            if (!userMapFile.exists()) {
                System.err.println("User map file not found at: ${userMapFile.absolutePath}")
                return emptyMap()
            }

            Yaml().load<Map<String, String>>(userMapFile.readText()).also {
                println("Loaded user map from file: $it")
            }
        } catch (e: Exception) {
            System.err.println("Error parsing user map file: ${e.message}")
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun getAsanaUserId(githubUsername: String): String? {
        return userMap[githubUsername]?.also {
            println("Found Asana user ID $it for GitHub user $githubUsername")
        } ?: run {
            System.err.println("No Asana user ID found for GitHub user $githubUsername")
            null
        }
    }

    fun findAndAssignTask() = runBlocking {
        try {
            println("Searching for task with name: $taskName")

            // Search for tasks in the project
            val tasks = asanaClient.tasks.findByProject(projectId)
                .option("fields", listOf("name", "permalink_url"))
                .execute()

            // Find task by name
            val task = tasks.data.find { it.name == taskName }

            if (task != null) {
                println("Found task: ${task.name}")

                // Assign the task to the user
                val asanaUserId = getAsanaUserId(githubUsername)
                if (asanaUserId != null) {
                    asanaClient.tasks.update(task.gid)
                        .data("assignee", asanaUserId)
                        .execute()
                    println("Assigned task to Asana user ID: $asanaUserId")
                }

                // Set output for GitHub Actions
                File(System.getenv("GITHUB_OUTPUT")).appendText("ASANA_TASK_URL=${task.permalinkUrl}\n")
                println("Task URL: ${task.permalinkUrl}")
            } else {
                System.err.println("No task found with name: $taskName")
                System.exit(1)
            }
        } catch (e: Exception) {
            System.err.println("Error finding/assigning task: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }
}

fun main() {
    AsanaSync.findAndAssignTask()
}
