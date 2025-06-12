import * as core from '@actions/core';
import * as github from '@actions/github';

async function run(): Promise<void> {
  try {
    // 1. Get inputs
    const token = core.getInput('github_token', { required: true });
    // const exampleInput = core.getInput('example_input');

    core.info(`Starting PR Sync Action...`);
    // core.info(`Example input was: ${exampleInput}`);

    // 2. Initialize Octokit client
    const octokit = github.getOctokit(token);

    // 3. Access GitHub context
    // The github.context object contains information about the event that triggered the workflow
    const context = github.context;

    if (context.eventName !== 'pull_request' && context.eventName !== 'pull_request_target') {
      core.setFailed(
        `This action is intended to run only on pull_request or pull_request_target events, not on ${context.eventName}.`
      );
      return;
    }

    if (!context.payload.pull_request) {
        core.setFailed('Could not get pull_request payload from context. Check the event trigger.');
        return;
    }

    const prNumber = context.payload.pull_request.number;
    const owner = context.repo.owner;
    const repo = context.repo.repo;

    core.info(`Operating on PR #${prNumber} in ${owner}/${repo}`);

    // --- Your Action Logic Here ---
    // Example: Get PR details
    const { data: pullRequest } = await octokit.rest.pulls.get({
      owner,
      repo,
      pull_number: prNumber,
    });

    core.info(`PR Title: ${pullRequest.title}`);
    core.info(`PR Body: ${pullRequest.body || '(No body)'}`);
    core.info(`PR Author: ${pullRequest.user?.login}`);

    // Example: Add a comment to the PR
    // await octokit.rest.issues.createComment({
    //   owner,
    //   repo,
    //   issue_number: prNumber,
    //   body: `Hello from the PR Sync Action! This PR is titled: "${pullRequest.title}"`,
    // });
    // core.info('Commented on the PR.');

    // Example: Set an output (if defined in action.yml)
    // core.setOutput('example_output', `Processed PR #${prNumber}`);

  } catch (error) {
    if (error instanceof Error) {
      core.setFailed(error.message);
    } else {
      core.setFailed('An unknown error occurred');
    }
  }
}

// Run the action
run();