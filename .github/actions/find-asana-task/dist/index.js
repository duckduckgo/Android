// src/index.js
const core = require('@actions/core');
const axios = require('axios');

async function run() {
  try {
    const taskName = core.getInput('task_name');
    const asanaToken = core.getInput('asana_token');
    const projectId = core.getInput('project_id');

    const options = {
      method: 'GET',
      headers: {
        accept: 'application/json',
        authorization: 'Bearer ${asanaToken}'
      }
    };

    fetch('https://app.asana.com/api/1.0/projects/${projectId}/tasks?opt_fields=name', options)
      .then(response => response.json())
      .then(response => console.log(response))
      .catch(err => console.error(err));

    const tasks = response.data.data;
    const task = tasks.find(t => t.name.toLowerCase() === taskName.toLowerCase());

    if (!task) {
      core.setFailed(`Task '${taskName}' not found in project ID '${projectId}'.`);
    } else {
      core.info(`Task ID for '${taskName}': ${task.id}`);
      core.setOutput('task_id', task.id);
    }
  } catch (error) {
    core.setFailed(error.message);
  }
}

run();
