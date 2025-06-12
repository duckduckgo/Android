"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
const asana_1 = require("asana");
const core_1 = require("@actions/core");
const github_1 = require("@actions/github");
const markdown_1 = require("./markdown");
const helper_1 = require("./helper");
const CUSTOM_FIELD_NAMES = {
    url: 'Github URL',
    status: 'Github Status'
};
const USER_MAP = JSON.parse((0, core_1.getInput)('USER_MAP', { required: false }) || '{}');
const client = asana_1.Client.create({
    defaultHeaders: {
        'asana-enable': 'new_user_task_lists,new_project_templates,new_goal_memberships'
    }
}).useAccessToken((0, core_1.getInput)('ASANA_ACCESS_TOKEN')); // do not mark this as required to avoid PRs created by dependabot to fail
const ASANA_WORKSPACE_ID = (0, core_1.getInput)('ASANA_WORKSPACE_ID', { required: true });
const PROJECT_ID = (0, core_1.getInput)('ASANA_PROJECT_ID', { required: true });
// Users which will not receive PRs/reviews tasks
const RANDOMIZED_REVIEWERS = (0, core_1.getInput)('RANDOMIZED_REVIEWERS');
const RANDOMIZED_REVIEWERS_LIST = RANDOMIZED_REVIEWERS.split(',');
function getUserIdFromLogin(login) {
    const userId = USER_MAP[login];
    return userId;
}
function getApprovalStatus(prState, author) {
    if (github_1.context.eventName === 'pull_request_review') {
        switch (prState) {
            case 'Approved':
            case 'Merged':
                return 'approved';
            case 'Closed':
                return 'rejected';
        }
        const reviewPayload = github_1.context.payload;
        if (reviewPayload.action === 'submitted') {
            if (reviewPayload.review.state === 'approved' ||
                reviewPayload.review.state === 'changes_requested' ||
                (reviewPayload.review.state === 'commented' &&
                    reviewPayload.review.user.login !== author)) {
                return reviewPayload.review.state;
            }
        }
        else if (reviewPayload.review.state === 'dismissed') {
            return 'pending';
        }
    }
    return undefined;
}
function findPRTask(customFields) {
    return __awaiter(this, void 0, void 0, function* () {
        // Let's first try to search using PR URL
        const payload = github_1.context.payload;
        const prURL = payload.pull_request.html_url;
        const prTasks = yield client.tasks.searchInWorkspace(ASANA_WORKSPACE_ID, {
            [`custom_fields.${customFields.url.gid}.value`]: prURL,
            // eslint-disable-next-line camelcase
            opt_fields: 'name,parent,completed'
        });
        if (prTasks.data.length > 0) {
            (0, core_1.info)(`Found PR task using searchInWorkspace: ${prTasks.data[0].gid}`);
            return prTasks.data[0];
        }
        else {
            // searchInWorkspace can fail for recently created Asana tasks. Let's look
            // at 100 most recent tasks in destination project
            // https://developers.asana.com/reference/searchtasksforworkspace#eventual-consistency
            const projectTasks = yield client.tasks.findByProject(PROJECT_ID, {
                // eslint-disable-next-line camelcase
                opt_fields: 'custom_fields',
                limit: 100
            });
            for (const task of projectTasks.data) {
                (0, core_1.info)(`Checking task ${task.gid} for PR link`);
                for (const field of task.custom_fields) {
                    if (field.gid === customFields.url.gid &&
                        field.display_value === prURL) {
                        (0, core_1.info)(`Found existing task ID ${task.gid} for PR ${prURL}`);
                        return task;
                    }
                }
            }
        }
        (0, core_1.info)(`No matching Asana task found for PR ${prURL}`);
        return null;
    });
}
function createPRTask(parentTaskId, followers, title, prStatus, customFields, automatedPR) {
    return __awaiter(this, void 0, void 0, function* () {
        (0, core_1.info)('Creating new PR task');
        const payload = github_1.context.payload;
        const data = {
            workspace: ASANA_WORKSPACE_ID,
            // eslint-disable-next-line camelcase
            resource_subtype: 'approval',
            // eslint-disable-next-line camelcase
            custom_fields: {
                [customFields.url.gid]: payload.pull_request.html_url,
                [customFields.status.gid]: prStatus
            },
            name: title,
            projects: [PROJECT_ID],
            followers
        };
        if (!automatedPR) {
            // eslint-disable-next-line camelcase
            data.due_on = (0, helper_1.getDueOn)(1);
        }
        if (parentTaskId) {
            data.parent = parentTaskId;
        }
        return client.tasks.create(data);
    });
}
function isImportantAutomatedPR(payload) {
    const githubAuthor = payload.pull_request.user.login;
    // WebView2 update
    return (githubAuthor === 'daxmobile' &&
        payload.pull_request.head.ref.startsWith('webview2/'));
}
function getFollowers(githubAuthor, automatedPR) {
    const authorId = getUserIdFromLogin(githubAuthor);
    if (authorId) {
        return [authorId];
    }
    // if it's a PR created by automation add everyone to it
    if (automatedPR) {
        return Object.values(USER_MAP);
    }
    return undefined;
}
function getAssignee(payload, allowRandomReviewer) {
    var _a, _b, _c;
    return __awaiter(this, void 0, void 0, function* () {
        const githubAuthor = payload.pull_request.user.login;
        let githubAssignee = (_b = (_a = payload.pull_request.assignees.find(user => user.login !== githubAuthor)) === null || _a === void 0 ? void 0 : _a.login) !== null && _b !== void 0 ? _b : (_c = payload.pull_request.requested_reviewers
            .map(user => user)
            .filter(user => user !== undefined)
            .find(user => user.login !== githubAuthor)) === null || _c === void 0 ? void 0 : _c.login;
        if (allowRandomReviewer && !githubAssignee) {
            (0, core_1.info)('Setting up random reviewer as noone is assigned to this PR');
            const possibleReviewers = RANDOMIZED_REVIEWERS_LIST.filter(reviewer => reviewer !== githubAuthor);
            if (possibleReviewers.length > 0) {
                const randomReviewer = possibleReviewers[Math.floor(Math.random() * possibleReviewers.length)];
                const octokit = (0, github_1.getOctokit)((0, core_1.getInput)('GITHUB_TOKEN'));
                const reviewerResponse = yield octokit.rest.pulls.requestReviewers({
                    owner: github_1.context.repo.owner,
                    repo: github_1.context.repo.repo,
                    // eslint-disable-next-line camelcase
                    pull_number: payload.pull_request.number,
                    reviewers: [randomReviewer]
                });
                const assigneeResponse = yield octokit.rest.issues.addAssignees({
                    owner: github_1.context.repo.owner,
                    repo: github_1.context.repo.repo,
                    // eslint-disable-next-line camelcase
                    issue_number: payload.pull_request.number,
                    assignees: [randomReviewer]
                });
                if (reviewerResponse.status !== 201 || assigneeResponse.status !== 201) {
                    (0, core_1.info)(`Could not assign to a random reviewer.`);
                }
                else {
                    githubAssignee = randomReviewer;
                    (0, core_1.info)(`PR is assigned to a random reviewer: ${randomReviewer}.`);
                }
            }
            else {
                (0, core_1.info)(`PR won't be assigned to random reviewer as RANDOMIZED_REVIEWERS list is empty.`);
            }
        }
        return githubAssignee;
    });
}
function createOrFindPRTask(payload, title, prStatus, customFields) {
    return __awaiter(this, void 0, void 0, function* () {
        const body = payload.pull_request.body || '';
        const asanaTaskMatch = body.match(/Task\/Issue URL:.*https:\/\/app.asana.*\/([0-9]+).*/);
        let parentTaskId = asanaTaskMatch && asanaTaskMatch[1];
        let openShipReviewTask;
        let shipReviewPRTask;
        if (parentTaskId) {
            (0, core_1.info)(`Found Asana task mention with parent ID: ${parentTaskId}`);
            try {
                let subTasks = yield client.tasks.subtasks(parentTaskId, {
                    // eslint-disable-next-line camelcase
                    opt_fields: 'name,completed',
                    limit: 100
                });
                openShipReviewTask = subTasks.data.find(t => t.name.startsWith('Ship Review') && !t.completed);
                if (openShipReviewTask) {
                    subTasks = yield client.tasks.subtasks(openShipReviewTask.gid, {
                        // eslint-disable-next-line camelcase
                        opt_fields: 'name,completed,assignee,custom_fields',
                        limit: 100
                    });
                    shipReviewPRTask = subTasks.data.find(t => t.name.includes('Pull Request') &&
                        ((t.custom_fields.every(customField => customField.gid !== customFields.url.gid) &&
                            !t.completed) ||
                            t.custom_fields.some(customField => customField.gid === customFields.url.gid &&
                                customField.display_value === payload.pull_request.html_url)));
                }
            }
            catch (e) {
                (0, core_1.info)(`Can't access parent task: ${parentTaskId}: ${e}`);
                (0, core_1.info)(`Add 'dax' user to respective projects to enable this feature`);
                parentTaskId = null;
            }
        }
        const githubAuthor = payload.pull_request.user.login;
        const automatedPR = isImportantAutomatedPR(payload);
        const followers = getFollowers(githubAuthor, automatedPR);
        if (!followers) {
            (0, core_1.info)(`Skipping Asana sync for PR created by ${githubAuthor} as they are not in USER_MAP`);
            return;
        }
        let task;
        // PR is opened
        if (['opened'].includes(payload.action)) {
            // the parent task has a Ship Review ...
            if (openShipReviewTask) {
                // ... and an approriate PR review task
                if (shipReviewPRTask) {
                    yield client.tasks.addProject(shipReviewPRTask.gid, {
                        project: PROJECT_ID
                    });
                    client.tasks.updateTask(shipReviewPRTask.gid, {
                        // eslint-disable-next-line camelcase
                        custom_fields: {
                            [customFields.url.gid]: payload.pull_request.html_url,
                            [customFields.status.gid]: prStatus
                        }
                    });
                    task = shipReviewPRTask;
                    (0, core_1.setOutput)('result', 'updated');
                    // ... otherwise create a new code review task under the ship review task
                }
                else {
                    task = yield createPRTask(openShipReviewTask.gid, followers, title, prStatus, customFields, automatedPR);
                    (0, core_1.setOutput)('result', 'created');
                }
                // if parent doesn't have a Ship Review just create a new Code Review task
            }
            else {
                task = yield createPRTask(parentTaskId, followers, title, prStatus, customFields, automatedPR);
                (0, core_1.setOutput)('result', 'created');
            }
        }
        else {
            const maxRetries = 5;
            let retries = 0;
            while (retries < maxRetries) {
                // Wait for PR to appear
                task = yield findPRTask(customFields);
                if (task) {
                    (0, core_1.setOutput)('result', 'updated');
                    break;
                }
                (0, core_1.info)(`PR task not found yet. Sleeping...`);
                yield new Promise(resolve => setTimeout(resolve, 20000));
                retries++;
            }
            // if PR task cannot be found although this is an ongoing PR
            if (!task) {
                // the parent task has a Ship Review ...
                if (openShipReviewTask) {
                    // ... and an PR review task that's not completed without any PR link
                    if (shipReviewPRTask) {
                        // add the Ship Review PR task to the code review project and assign the PR link to it
                        yield client.tasks.addProject(shipReviewPRTask.gid, {
                            project: PROJECT_ID
                        });
                        client.tasks.updateTask(shipReviewPRTask.gid, {
                            // eslint-disable-next-line camelcase
                            custom_fields: {
                                [customFields.url.gid]: payload.pull_request.html_url,
                                [customFields.status.gid]: prStatus
                            }
                        });
                        task = shipReviewPRTask;
                        (0, core_1.setOutput)('result', 'updated');
                        // ... otherwise abort sync as this action cannot open a new Ship Review PR task
                    }
                    else {
                        (0, core_1.info)(`Skipping code review task creation for PR because the linked Asana task already has a pending '${openShipReviewTask.name}' task but no open PR review subtask`);
                        return;
                    }
                    // if parent doesn't have a Ship Review just create a new Code Review task
                }
                else {
                    (0, core_1.info)(`Waited a long time and no task appeared. Assuming old PR and creating a new task.`);
                    task = yield createPRTask(parentTaskId, followers, title, prStatus, customFields, automatedPR);
                    (0, core_1.setOutput)('result', 'created');
                }
            }
        }
        return task;
    });
}
function run() {
    var _a, _b, _c;
    return __awaiter(this, void 0, void 0, function* () {
        try {
            (0, core_1.info)(`Event: ${github_1.context.eventName}.`);
            if (!['pull_request', 'pull_request_target', 'pull_request_review'].includes(github_1.context.eventName)) {
                (0, core_1.info)('Only runs for PR changes and reviews');
                return;
            }
            (0, core_1.info)(`Event JSON: \n${JSON.stringify(github_1.context, null, 2)}`);
            const payload = github_1.context.payload;
            const githubAuthor = payload.pull_request.user.login;
            const automatedPR = isImportantAutomatedPR(payload);
            const title = automatedPR
                ? payload.pull_request.title
                : `Code review for PR #${payload.pull_request.number}: ${payload.pull_request.title}`;
            // PR metadata
            const customFields = yield findCustomFields(ASANA_WORKSPACE_ID);
            const prState = getPRState(payload.pull_request);
            const prStatus = ((_b = (_a = customFields.status.enum_options) === null || _a === void 0 ? void 0 : _a.find(f => f.name === prState)) === null || _b === void 0 ? void 0 : _b.gid) || '';
            const approvalStatus = getApprovalStatus(prState, githubAuthor);
            const task = yield createOrFindPRTask(payload, title, prStatus, customFields);
            if (!task) {
                return;
            }
            let body = payload.pull_request.body || 'Empty description';
            // Update description of automated PR with the created Asana task url
            if (automatedPR && !body.includes('Issue URL:')) {
                const octokit = (0, github_1.getOctokit)((0, core_1.getInput)('GITHUB_TOKEN'));
                body = `Task/Issue URL: ${task.permalink_url}
Copy for release note: N/A
CC:

**Description**:
${payload.pull_request.body}
`;
                yield octokit.rest.pulls.update({
                    owner: github_1.context.repo.owner,
                    repo: github_1.context.repo.repo,
                    // eslint-disable-next-line camelcase
                    pull_number: payload.pull_request.number,
                    body
                });
            }
            const htmlUrl = payload.pull_request.html_url;
            const preamble = `**Note:** This description is automatically updated from Github. **Changes will be LOST**.

PR: ${htmlUrl}`;
            // Asana has limits on size of notes. Let's be very conservative and trim the text
            const truncatedBody = (body.length > 5000 ? `${body.slice(0, 5000)}…` : body).replace(/^---$[\s\S]*/gm, '');
            // Unformatted plaintext notes for fallback
            const notes = `
${preamble}

${truncatedBody}`;
            // Rich-text notes with some custom "fixes" for Asana to render things
            const htmlNotes = `<body>${(0, markdown_1.renderMD)(notes)}</body>`;
            (0, core_1.setOutput)('task_url', task.permalink_url);
            const taskId = task.gid;
            const allowRandomReviewer = !automatedPR &&
                prState === 'Open' &&
                ((_c = payload.pull_request.assignee) === null || _c === void 0 ? void 0 : _c.login) !== githubAuthor &&
                RANDOMIZED_REVIEWERS_LIST.includes(githubAuthor);
            const githubAssignee = yield getAssignee(payload, allowRandomReviewer);
            // Close task if already closed or if PR is closed
            const closeTask = task.completed ||
                ['closed'].includes(payload.pull_request.state) ||
                approvalStatus === 'approved';
            if (!closeTask &&
                (approvalStatus === 'commented' || approvalStatus === 'changes_requested')) {
                const sectionId = (0, core_1.getInput)('ASANA_IN_PROGRESS_SECTION_ID');
                if (sectionId) {
                    yield client.sections.addTask(sectionId, { task: task.gid });
                }
            }
            const updateParams = {
                // eslint-disable-next-line camelcase
                custom_fields: {
                    [customFields.status.gid]: prStatus
                }
            };
            if (payload.action === 'ready_for_review') {
                // eslint-disable-next-line camelcase
                updateParams.due_on = (0, helper_1.getDueOn)(1);
            }
            if (approvalStatus && approvalStatus !== 'commented') {
                // eslint-disable-next-line camelcase
                updateParams.approval_status = approvalStatus;
            }
            if (!closeTask && githubAssignee) {
                updateParams.assignee = getUserIdFromLogin(githubAssignee);
            }
            try {
                // do not update title and description of Ship Review PR tasks
                if (task.name !== 'Ship Review: Pull Request(s)') {
                    updateParams.name = title;
                    // eslint-disable-next-line camelcase
                    updateParams.html_notes = htmlNotes;
                }
                (0, core_1.info)(`Update task with html update params ${JSON.stringify(updateParams)}`);
                // Try using html notes first and fall back to unformatted if this fails
                yield client.tasks.updateTask(taskId, updateParams);
            }
            catch (err) {
                if (updateParams.html_notes) {
                    delete updateParams.html_notes;
                    updateParams.notes = notes;
                    (0, core_1.info)(`Updating task with HTML notes failed. Retrying with plaintext ${JSON.stringify(updateParams)}`);
                    yield client.tasks.updateTask(taskId, updateParams);
                }
                else if (err instanceof Error) {
                    (0, core_1.setFailed)(`${err.message}\nStacktrace:\n${err.stack}`);
                }
            }
        }
        catch (error) {
            if (error instanceof Error) {
                (0, core_1.setFailed)(`${error.message}\nStacktrace:\n${error.stack}`);
            }
        }
    });
}
function findCustomFields(workspaceGid) {
    return __awaiter(this, void 0, void 0, function* () {
        const apiResponse = yield client.customFields.getCustomFieldsForWorkspace(workspaceGid);
        // pull all fields from the API with the streaming
        const stream = apiResponse.stream();
        const customFields = [];
        stream.on('data', field => {
            customFields.push(field);
        });
        yield new Promise(resolve => stream.on('end', resolve));
        const githubUrlField = customFields.find(f => f.name === CUSTOM_FIELD_NAMES.url);
        const githubStatusField = customFields.find(f => f.name === CUSTOM_FIELD_NAMES.status);
        if (!githubUrlField || !githubStatusField) {
            (0, core_1.debug)(JSON.stringify(customFields));
            throw new Error('Custom fields are missing. Please create them');
        }
        else {
            (0, core_1.debug)(`${CUSTOM_FIELD_NAMES.url} field GID: ${githubUrlField === null || githubUrlField === void 0 ? void 0 : githubUrlField.gid}`);
            (0, core_1.debug)(`${CUSTOM_FIELD_NAMES.status} field GID: ${githubStatusField === null || githubStatusField === void 0 ? void 0 : githubStatusField.gid}`);
        }
        return {
            url: githubUrlField,
            status: githubStatusField
        };
    });
}
function getPRState(pr) {
    if (pr.merged) {
        return 'Merged';
    }
    if (pr.state === 'open') {
        if (pr.draft) {
            return 'Draft';
        }
        return 'Open';
    }
    return 'Closed';
}
run();
