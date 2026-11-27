import {ApiClient, CustomFieldsApi, SectionsApi, TasksApi} from 'asana'
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

type CustomField = {
  gid: string
  name: string
  enum_options?: {gid: string; name: string}[]
}

type Task = {
  gid: string
  name: string
  completed: boolean
  permalink_url: string
  custom_fields: {gid: string; display_value: string | null}[]
}

type TaskParams = {
  workspace?: string
  resource_subtype?: string
  custom_fields?: {[gid: string]: string}
  name?: string
  projects?: string[]
  followers?: string[]
  parent?: string
  due_on?: string
  approval_status?: string
  assignee?: string
  notes?: string
  html_notes?: string
}

type Page<T> = {data: T[] | null; nextPage?: () => Promise<Page<T>>}

type PRFields = {
  url: CustomField
  status: CustomField
}

const client = new ApiClient()
client.authentications.token.accessToken = getInput('ASANA_ACCESS_TOKEN', {
  required: true
})
client.defaultHeaders = {
  'asana-enable':
    'new_user_task_lists,new_project_templates,new_goal_memberships'
}
const tasksApi = new TasksApi(client)
const sectionsApi = new SectionsApi(client)
const customFieldsApi = new CustomFieldsApi(client)

const ASANA_WORKSPACE_ID = getInput('ASANA_WORKSPACE_ID', {required: true})
const PROJECT_ID = getInput('ASANA_PROJECT_ID', {required: true})
// Users which will not receive PRs/reviews tasks
const RANDOMIZED_REVIEWERS = getInput('RANDOMIZED_REVIEWERS')
const RANDOMIZED_REVIEWERS_LIST = RANDOMIZED_REVIEWERS.split(',')
// Tolerance for clock skew between GitHub and Asana when scanning the project
// for tasks modified since the PR was opened
const CLOCK_SKEW_TOLERANCE_MS = 60 * 60 * 1000
const MAX_SCANNED_TASKS = 500

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

async function findPRTask(customFields: PRFields): Promise<Task | null> {
  // Let's first try to search using PR URL
  const payload = context.payload as PullRequestEvent
  const prURL = payload.pull_request.html_url

  const prTasks: Page<Task> = await tasksApi.searchTasksForWorkspace(
    ASANA_WORKSPACE_ID,
    {
      [`custom_fields.${customFields.url.gid}.value`]: prURL,
      // eslint-disable-next-line camelcase
      opt_fields: 'name,parent,completed,permalink_url'
    }
  )
  if (prTasks.data && prTasks.data.length > 0) {
    info(`Found PR task using searchInWorkspace: ${prTasks.data[0].gid}`)
    return prTasks.data[0]
  }

  // Asana recommends using GET /tasks for consistent lookup as search infrastructure
  // can have delays of up to 60 seconds due to indexing.
  // https://developers.asana.com/reference/searchtasksforworkspace#eventual-consistency
  const modifiedSince = new Date(
    Date.parse(payload.pull_request.created_at) - CLOCK_SKEW_TOLERANCE_MS
  ).toISOString()
  let page: Page<Task> = await tasksApi.getTasks({
    project: PROJECT_ID,
    // eslint-disable-next-line camelcase
    modified_since: modifiedSince,
    // eslint-disable-next-line camelcase
    opt_fields: 'name,completed,custom_fields,permalink_url',
    limit: 100
  })
  let scanned = 0
  while (page.data && page.nextPage && scanned < MAX_SCANNED_TASKS) {
    for (const task of page.data) {
      info(`Checking task ${task.gid} for PR link`)
      if (linksPR(task, customFields, prURL)) {
        info(`Found existing task ID ${task.gid} for PR ${prURL}`)
        return task
      }
    }
    scanned += page.data.length
    page = await page.nextPage()
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
): Promise<Task> {
  info('Creating new PR task')
  const payload = context.payload as PullRequestEvent
  const data: TaskParams = {
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

  const response = await tasksApi.createTask(
    {data},
    // eslint-disable-next-line camelcase
    {opt_fields: 'name,completed,permalink_url'}
  )
  return response.data
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
): Promise<Task | undefined> {
  const body = payload.pull_request.body || ''
  const asanaTaskMatch = body.match(
    /Task\/Issue URL:.*https:\/\/app.asana.*\/([0-9]+).*/
  )
  let parentTaskId = asanaTaskMatch && asanaTaskMatch[1]
  const prURL = payload.pull_request.html_url
  let linkedPRTask: Task | undefined
  let openShipReviewTask: Task | undefined
  let shipReviewPRTask: Task | undefined
  if (parentTaskId) {
    info(`Found Asana task mention with parent ID: ${parentTaskId}`)

    try {
      let subTasks: Task[] = (
        await tasksApi.getSubtasksForTask(parentTaskId, {
          // eslint-disable-next-line camelcase
          opt_fields: 'name,completed,custom_fields,permalink_url',
          limit: 100
        })
      ).data
      linkedPRTask = subTasks.find(t => linksPR(t, customFields, prURL))
      openShipReviewTask = subTasks.find(
        t => t.name.startsWith('Ship Review') && !t.completed
      )
      if (openShipReviewTask) {
        subTasks = (
          await tasksApi.getSubtasksForTask(openShipReviewTask.gid, {
            // eslint-disable-next-line camelcase
            opt_fields: 'name,completed,assignee,custom_fields,permalink_url',
            limit: 100
          })
        ).data
        linkedPRTask ??= subTasks.find(t => linksPR(t, customFields, prURL))
        shipReviewPRTask = subTasks.find(
          t =>
            t.name.includes('Pull Request') &&
            !t.completed &&
            t.custom_fields.every(
              customField => customField.gid !== customFields.url.gid
            )
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

  // The workflow's concurrency group serializes runs per PR, so whichever
  // event reaches here first creates the task and every later one finds it,
  // under the linked task when there is one and by lookup otherwise.
  const existingTask = linkedPRTask ?? (await findPRTask(customFields))
  if (existingTask) {
    setOutput('result', 'updated')
    return existingTask
  }

  // the parent task has a Ship Review ...
  if (openShipReviewTask) {
    // ... and a PR review subtask we can adopt: add it to the code review
    // project and point it at this PR
    if (shipReviewPRTask) {
      await tasksApi.addProjectForTask(
        {data: {project: PROJECT_ID}},
        shipReviewPRTask.gid
      )
      tasksApi.updateTask(
        {
          data: {
            // eslint-disable-next-line camelcase
            custom_fields: {
              [customFields.url.gid]: payload.pull_request.html_url,
              [customFields.status.gid]: prStatus
            }
          }
        },
        shipReviewPRTask.gid
      )
      setOutput('result', 'updated')
      return shipReviewPRTask
    }

    // ... otherwise only the `opened` run may add a new code review task under
    // the ship review task; any later event aborts the sync instead
    if (payload.action !== 'opened') {
      info(
        `Skipping code review task creation for PR because the linked Asana task already has a pending '${openShipReviewTask.name}' task but no open PR review subtask`
      )
      return
    }
    const task = await createPRTask(
      openShipReviewTask.gid,
      followers,
      title,
      prStatus,
      customFields,
      automatedPR
    )
    setOutput('result', 'created')
    return task
  }

  // if parent doesn't have a Ship Review just create a new Code Review task
  const task = await createPRTask(
    parentTaskId,
    followers,
    title,
    prStatus,
    customFields,
    automatedPR
  )
  setOutput('result', 'created')
  return task
}

function linksPR(task: Task, customFields: PRFields, prURL: string): boolean {
  return task.custom_fields.some(
    field => field.gid === customFields.url.gid && field.display_value === prURL
  )
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
        await sectionsApi.addTaskForSection(sectionId, {
          body: {data: {task: task.gid}}
        })
      }
    }

    const updateParams: TaskParams = {
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
      await tasksApi.updateTask({data: updateParams}, taskId)
    } catch (err) {
      if (updateParams.html_notes) {
        delete updateParams.html_notes
        updateParams.notes = notes
        info(
          `Updating task with HTML notes failed. Retrying with plaintext ${JSON.stringify(
            updateParams
          )}`
        )
        await tasksApi.updateTask({data: updateParams}, taskId)
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
  const customFields: CustomField[] = []
  let page: Page<CustomField> =
    await customFieldsApi.getCustomFieldsForWorkspace(workspaceGid, {
      // eslint-disable-next-line camelcase
      opt_fields: 'name,enum_options,enum_options.name',
      limit: 100
    })
  while (page.data && page.nextPage) {
    customFields.push(...page.data)
    page = await page.nextPage()
  }

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
    url: githubUrlField,
    status: githubStatusField
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
