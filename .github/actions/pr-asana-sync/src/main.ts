import asana, {Client} from 'asana'
import {info, setFailed, getInput, debug, setOutput} from '@actions/core'
import {context, getOctokit} from '@actions/github'
import {
  PullRequest,
  PullRequestEvent,
  PullRequestReviewEvent,
  User
} from '@octokit/webhooks-types'

import {renderMD} from './markdown'
import {getDueOn} from './helper'

const CUSTOM_FIELD_NAMES = {
  url: 'Github URL',
  status: 'Github Status'
}

const USER_MAP: {[key: string]: string} = JSON.parse(
  getInput('USER_MAP', {required: false}) || '{}'
)

type PRState = 'Open' | 'Closed' | 'Merged' | 'Approved' | 'Draft'

type PRFields = {
  url: asana.resources.CustomField
  status: asana.resources.CustomField
}
const client = Client.create({
  defaultHeaders: {
    'asana-enable':
      'new_user_task_lists,new_project_templates,new_goal_memberships'
  }
}).useAccessToken(getInput('ASANA_ACCESS_TOKEN', {required: true}))

const ASANA_WORKSPACE_ID = getInput('ASANA_WORKSPACE_ID', {required: true})
const PROJECT_ID = getInput('ASANA_PROJECT_ID', {required: true})
// Users which will not receive PRs/reviews tasks
const RANDOMIZED_REVIEWERS = getInput('RANDOMIZED_REVIEWERS')
const RANDOMIZED_REVIEWERS_LIST = RANDOMIZED_REVIEWERS.split(',')

function getUserIdFromLogin(login: string): string | undefined {
  const userId = USER_MAP[login]
  return userId
}

function getApprovalStatus(
  prState: PRState,
  author: string
):
  | 'pending'
  | 'commented'
  | 'changes_requested'
  | 'approved'
  | 'rejected'
  | undefined {
  if (context.eventName === 'pull_request_review') {
    switch (prState) {
      case 'Approved':
      case 'Merged':
        return 'approved'
      case 'Closed':
        return 'rejected'
    }

    const reviewPayload = context.payload as PullRequestReviewEvent
    if (reviewPayload.action === 'submitted') {
      if (
        reviewPayload.review.state === 'approved' ||
        reviewPayload.review.state === 'changes_requested' ||
        (reviewPayload.review.state === 'commented' &&
          reviewPayload.review.user.login !== author)
      ) {
        return reviewPayload.review.state
      }
    } else if (reviewPayload.review.state === 'dismissed') {
      return 'pending'
    }
  }

  return undefined
}

async function findPRTask(
  customFields: PRFields
): Promise<asana.resources.Tasks.Type | null> {
  // Let's first try to search using PR URL
  const payload = context.payload as PullRequestEvent
  const prURL = payload.pull_request.html_url

  const prTasks = await client.tasks.searchInWorkspace(ASANA_WORKSPACE_ID, {
    [`custom_fields.${customFields.url.gid}.value`]: prURL,
    // eslint-disable-next-line camelcase
    opt_fields: 'name,parent,completed'
  })
  if (prTasks.data.length > 0) {
    info(`Found PR task using searchInWorkspace: ${prTasks.data[0].gid}`)
    return prTasks.data[0]
  } else {
    // searchInWorkspace can fail for recently created Asana tasks. Let's look
    // at 100 most recent tasks in destination project
    // https://developers.asana.com/reference/searchtasksforworkspace#eventual-consistency
    const projectTasks = await client.tasks.findByProject(PROJECT_ID, {
      // eslint-disable-next-line camelcase
      opt_fields: 'custom_fields',
      limit: 100
    })

    for (const task of projectTasks.data) {
      info(`Checking task ${task.gid} for PR link`)
      for (const field of task.custom_fields) {
        if (
          field.gid === customFields.url.gid &&
          field.display_value === prURL
        ) {
          info(`Found existing task ID ${task.gid} for PR ${prURL}`)
          return task
        }
      }
    }
  }
  info(`No matching Asana task found for PR ${prURL}`)
  return null
}

async function createPRTask(
  parentTaskId: string | null,
  followers: string[] | undefined,
  title: string,
  prStatus: string,
  customFields: PRFields,
  automatedPR: boolean
): Promise<asana.resources.Tasks.Type> {
  info('Creating new PR task')
  const payload = context.payload as PullRequestEvent
  const data: asana.resources.Tasks.CreateParams & {workspace: string} = {
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
  }

  if (!automatedPR) {
    // eslint-disable-next-line camelcase
    data.due_on = getDueOn(1)
  }

  if (parentTaskId) {
    data.parent = parentTaskId
  }

  return client.tasks.create(data)
}

function isImportantAutomatedPR(payload: PullRequestEvent): boolean {
  const githubAuthor = payload.pull_request.user.login
  // WebView2 update
  return (
    githubAuthor === 'daxmobile' &&
    payload.pull_request.head.ref.startsWith('webview2/')
  )
}

function getFollowers(
  githubAuthor: string,
  automatedPR: boolean
): string[] | undefined {
  const authorId = getUserIdFromLogin(githubAuthor)
  if (authorId) {
    return [authorId]
  }

  // if it's a PR created by automation add everyone to it
  if (automatedPR) {
    return Object.values(USER_MAP)
  }

  return undefined
}

async function getAssignee(
  payload: PullRequestEvent,
  allowRandomReviewer: boolean
): Promise<string | undefined> {
  const githubAuthor = payload.pull_request.user.login
  let githubAssignee: string | undefined =
    payload.pull_request.assignees.find(user => user.login !== githubAuthor)
      ?.login ??
    payload.pull_request.requested_reviewers
      .map(user => user as User)
      .filter(user => user !== undefined)
      .find(user => user.login !== githubAuthor)?.login

  if (allowRandomReviewer && !githubAssignee) {
    info('Setting up random reviewer as noone is assigned to this PR')
    const possibleReviewers = RANDOMIZED_REVIEWERS_LIST.filter(
      reviewer => reviewer !== githubAuthor
    )
    if (possibleReviewers.length > 0) {
      const randomReviewer =
        possibleReviewers[Math.floor(Math.random() * possibleReviewers.length)]

      const octokit = getOctokit(getInput('GITHUB_TOKEN'))
      const reviewerResponse = await octokit.rest.pulls.requestReviewers({
        owner: context.repo.owner,
        repo: context.repo.repo,
        // eslint-disable-next-line camelcase
        pull_number: payload.pull_request.number,
        reviewers: [randomReviewer]
      })

      const assigneeResponse = await octokit.rest.issues.addAssignees({
        owner: context.repo.owner,
        repo: context.repo.repo,
        // eslint-disable-next-line camelcase
        issue_number: payload.pull_request.number,
        assignees: [randomReviewer]
      })

      if (reviewerResponse.status !== 201 || assigneeResponse.status !== 201) {
        info(`Could not assign to a random reviewer.`)
      } else {
        githubAssignee = randomReviewer
        info(`PR is assigned to a random reviewer: ${randomReviewer}.`)
      }
    } else {
      info(
        `PR won't be assigned to random reviewer as RANDOMIZED_REVIEWERS list is empty.`
      )
    }
  }

  return githubAssignee
}

async function createOrFindPRTask(
  payload: PullRequestEvent,
  title: string,
  prStatus: string,
  customFields: PRFields
): Promise<asana.resources.Tasks.Type | undefined> {
  const body = payload.pull_request.body || ''
  const asanaTaskMatch = body.match(
    /Task\/Issue URL:.*https:\/\/app.asana.*\/([0-9]+).*/
  )
  let parentTaskId = asanaTaskMatch && asanaTaskMatch[1]
  let openShipReviewTask: asana.resources.Tasks.Type | undefined
  let shipReviewPRTask: asana.resources.Tasks.Type | undefined
  if (parentTaskId) {
    info(`Found Asana task mention with parent ID: ${parentTaskId}`)

    try {
      let subTasks = await client.tasks.subtasks(parentTaskId, {
        // eslint-disable-next-line camelcase
        opt_fields: 'name,completed',
        limit: 100
      })
      openShipReviewTask = subTasks.data.find(
        t => t.name.startsWith('Ship Review') && !t.completed
      )
      if (openShipReviewTask) {
        subTasks = await client.tasks.subtasks(openShipReviewTask.gid, {
          // eslint-disable-next-line camelcase
          opt_fields: 'name,completed,assignee,custom_fields',
          limit: 100
        })
        shipReviewPRTask = subTasks.data.find(
          t =>
            t.name.includes('Pull Request') &&
            ((t.custom_fields.every(
              customField => customField.gid !== customFields.url.gid
            ) &&
              !t.completed) ||
              t.custom_fields.some(
                customField =>
                  customField.gid === customFields.url.gid &&
                  customField.display_value === payload.pull_request.html_url
              ))
        )
      }
    } catch (e) {
      info(`Can't access parent task: ${parentTaskId}: ${e}`)
      info(`Add 'dax' user to respective projects to enable this feature`)
      parentTaskId = null
    }
  }

  const githubAuthor = payload.pull_request.user.login
  const automatedPR = isImportantAutomatedPR(payload)
  const followers = getFollowers(githubAuthor, automatedPR)
  if (!followers) {
    info(
      `Skipping Asana sync for PR created by ${githubAuthor} as they are not in USER_MAP`
    )
    return
  }

  let task
  // PR is opened
  if (['opened'].includes(payload.action)) {
    // the parent task has a Ship Review ...
    if (openShipReviewTask) {
      // ... and an approriate PR review task
      if (shipReviewPRTask) {
        await client.tasks.addProject(shipReviewPRTask.gid, {
          project: PROJECT_ID
        })
        client.tasks.updateTask(shipReviewPRTask.gid, {
          // eslint-disable-next-line camelcase
          custom_fields: {
            [customFields.url.gid]: payload.pull_request.html_url,
            [customFields.status.gid]: prStatus
          }
        })
        task = shipReviewPRTask
        setOutput('result', 'updated')
        // ... otherwise create a new code review task under the ship review task
      } else {
        task = await createPRTask(
          openShipReviewTask.gid,
          followers,
          title,
          prStatus,
          customFields,
          automatedPR
        )
        setOutput('result', 'created')
      }
      // if parent doesn't have a Ship Review just create a new Code Review task
    } else {
      task = await createPRTask(
        parentTaskId,
        followers,
        title,
        prStatus,
        customFields,
        automatedPR
      )
      setOutput('result', 'created')
    }
  } else {
    const maxRetries = 5
    let retries = 0

    while (retries < maxRetries) {
      // Wait for PR to appear
      task = await findPRTask(customFields)
      if (task) {
        setOutput('result', 'updated')
        break
      }
      info(`PR task not found yet. Sleeping...`)
      await new Promise(resolve => setTimeout(resolve, 20000))
      retries++
    }

    // if PR task cannot be found although this is an ongoing PR
    if (!task) {
      // the parent task has a Ship Review ...
      if (openShipReviewTask) {
        // ... and an PR review task that's not completed without any PR link
        if (shipReviewPRTask) {
          // add the Ship Review PR task to the code review project and assign the PR link to it
          await client.tasks.addProject(shipReviewPRTask.gid, {
            project: PROJECT_ID
          })
          client.tasks.updateTask(shipReviewPRTask.gid, {
            // eslint-disable-next-line camelcase
            custom_fields: {
              [customFields.url.gid]: payload.pull_request.html_url,
              [customFields.status.gid]: prStatus
            }
          })
          task = shipReviewPRTask
          setOutput('result', 'updated')
          // ... otherwise abort sync as this action cannot open a new Ship Review PR task
        } else {
          info(
            `Skipping code review task creation for PR because the linked Asana task already has a pending '${openShipReviewTask.name}' task but no open PR review subtask`
          )
          return
        }
        // if parent doesn't have a Ship Review just create a new Code Review task
      } else {
        info(
          `Waited a long time and no task appeared. Assuming old PR and creating a new task.`
        )
        task = await createPRTask(
          parentTaskId,
          followers,
          title,
          prStatus,
          customFields,
          automatedPR
        )
        setOutput('result', 'created')
      }
    }
  }

  return task
}

async function run(): Promise<void> {
  try {
    info(`Event: ${context.eventName}.`)
    if (
      !['pull_request', 'pull_request_target', 'pull_request_review'].includes(
        context.eventName
      )
    ) {
      info('Only runs for PR changes and reviews')
      return
    }

    info(`Event JSON: \n${JSON.stringify(context, null, 2)}`)
    const payload = context.payload as PullRequestEvent
    const githubAuthor = payload.pull_request.user.login
    const automatedPR = isImportantAutomatedPR(payload)
    const title = automatedPR
      ? payload.pull_request.title
      : `Code review for PR #${payload.pull_request.number}: ${payload.pull_request.title}`
    // PR metadata
    const customFields = await findCustomFields(ASANA_WORKSPACE_ID)
    const prState = getPRState(payload.pull_request)
    const prStatus =
      customFields.status.enum_options?.find(f => f.name === prState)?.gid || ''
    const approvalStatus = getApprovalStatus(prState, githubAuthor)

    const task = await createOrFindPRTask(
      payload,
      title,
      prStatus,
      customFields
    )
    if (!task) {
      return
    }

    let body = payload.pull_request.body || 'Empty description'
    // Update description of automated PR with the created Asana task url
    if (automatedPR && !body.includes('Issue URL:')) {
      const octokit = getOctokit(getInput('GITHUB_TOKEN'))
      body = `Task/Issue URL: ${task.permalink_url}
Copy for release note: N/A
CC:

**Description**:
${payload.pull_request.body}
`
      await octokit.rest.pulls.update({
        owner: context.repo.owner,
        repo: context.repo.repo,
        // eslint-disable-next-line camelcase
        pull_number: payload.pull_request.number,
        body
      })
    }

    const htmlUrl = payload.pull_request.html_url
    const preamble = `**Note:** This description is automatically updated from Github. **Changes will be LOST**.

PR: ${htmlUrl}`

    // Asana has limits on size of notes. Let's be very conservative and trim the text
    const truncatedBody = (
      body.length > 5000 ? `${body.slice(0, 5000)}…` : body
    ).replace(/^---$[\s\S]*/gm, '')

    // Unformatted plaintext notes for fallback
    const notes = `
${preamble}

${truncatedBody}`

    // Rich-text notes with some custom "fixes" for Asana to render things
    const htmlNotes = `<body>${renderMD(notes)}</body>`

    setOutput('task_url', task.permalink_url)
    const taskId = task.gid

    const allowRandomReviewer =
      !automatedPR &&
      prState === 'Open' &&
      payload.pull_request.assignee?.login !== githubAuthor &&
      RANDOMIZED_REVIEWERS_LIST.includes(githubAuthor)
    const githubAssignee = await getAssignee(payload, allowRandomReviewer)

    // Close task if already closed or if PR is closed
    const closeTask =
      task.completed ||
      ['closed'].includes(payload.pull_request.state) ||
      approvalStatus === 'approved'

    if (
      !closeTask &&
      (approvalStatus === 'commented' || approvalStatus === 'changes_requested')
    ) {
      const sectionId = getInput('ASANA_IN_PROGRESS_SECTION_ID')
      if (sectionId) {
        await client.sections.addTask(sectionId, {task: task.gid})
      }
    }

    const updateParams: asana.resources.Tasks.UpdateParams = {
      // eslint-disable-next-line camelcase
      custom_fields: {
        [customFields.status.gid]: prStatus
      }
    }

    if (payload.action === 'ready_for_review') {
      // eslint-disable-next-line camelcase
      updateParams.due_on = getDueOn(1)
    }

    if (approvalStatus && approvalStatus !== 'commented') {
      // eslint-disable-next-line camelcase
      updateParams.approval_status = approvalStatus
    }

    if (!closeTask && githubAssignee) {
      updateParams.assignee = getUserIdFromLogin(githubAssignee)
    }

    try {
      // do not update title and description of Ship Review PR tasks
      if (task.name !== 'Ship Review: Pull Request(s)') {
        updateParams.name = title
        // eslint-disable-next-line camelcase
        updateParams.html_notes = htmlNotes
      }
      info(
        `Update task with html update params ${JSON.stringify(updateParams)}`
      )
      // Try using html notes first and fall back to unformatted if this fails
      await client.tasks.updateTask(taskId, updateParams)
    } catch (err) {
      if (updateParams.html_notes) {
        delete updateParams.html_notes
        updateParams.notes = notes
        info(
          `Updating task with HTML notes failed. Retrying with plaintext ${JSON.stringify(
            updateParams
          )}`
        )
        await client.tasks.updateTask(taskId, updateParams)
      } else if (err instanceof Error) {
        setFailed(`${err.message}\nStacktrace:\n${err.stack}`)
      }
    }
  } catch (error) {
    if (error instanceof Error) {
      setFailed(`${error.message}\nStacktrace:\n${error.stack}`)
    }
  }
}

async function findCustomFields(workspaceGid: string): Promise<PRFields> {
  const apiResponse = await client.customFields.getCustomFieldsForWorkspace(
    workspaceGid
  )
  // pull all fields from the API with the streaming
  const stream = apiResponse.stream()
  const customFields: asana.resources.CustomFields.Type[] = []
  stream.on('data', field => {
    customFields.push(field)
  })
  await new Promise<void>(resolve => stream.on('end', resolve))

  const githubUrlField = customFields.find(
    f => f.name === CUSTOM_FIELD_NAMES.url
  )
  const githubStatusField = customFields.find(
    f => f.name === CUSTOM_FIELD_NAMES.status
  )
  if (!githubUrlField || !githubStatusField) {
    debug(JSON.stringify(customFields))
    throw new Error('Custom fields are missing. Please create them')
  } else {
    debug(`${CUSTOM_FIELD_NAMES.url} field GID: ${githubUrlField?.gid}`)
    debug(`${CUSTOM_FIELD_NAMES.status} field GID: ${githubStatusField?.gid}`)
  }
  return {
    url: githubUrlField as asana.resources.CustomField,
    status: githubStatusField as asana.resources.CustomField
  }
}

function getPRState(pr: PullRequest): PRState {
  if (pr.merged) {
    return 'Merged'
  }
  if (pr.state === 'open') {
    if (pr.draft) {
      return 'Draft'
    }
    return 'Open'
  }
  return 'Closed'
}

run()