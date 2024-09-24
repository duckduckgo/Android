// src/index.js
const core = require('@actions/core');
const axios = require('axios');

async function run() {
  try {
    const taskName = core.getInput('task_name');
    const asanaToken = core.getInput('asana_token');
    const projectId = core.getInput('project_id');

    // Log the inputs for debugging
    core.info(`Searching for task: ${taskName} in project ID: ${projectId}`);

    const response = await axios.get(`https://app.asana.com/api/1.0/tasks/search`, {
      headers: {
        Authorization: `Bearer ${asanaToken}`,
      },
      params: {
        project: projectId,
        text: taskName,
      },
    });

    const tasks = response.data.data;
    const task = tasks.find(t => t.name.toLowerCase() === taskName.toLowerCase());

    if (!task) {
      core.setFailed(`Task '${taskName}' not found in project ID '${projectId}'.`);
    } else {
      core.info(`Task ID for '${taskName}': ${task.id}`);
      core.setOutput('task_id', task.id);
    }
  } catch (error) {
    core.setFailed(`Error: ${error.response ? error.response.data : error.message}`);
  }
}

run();
