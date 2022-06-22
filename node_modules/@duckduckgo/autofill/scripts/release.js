/* eslint-disable no-undef */
/* eslint-disable camelcase */
const Asana = require('asana')
const MarkdownIt = require('markdown-it')
const md = new MarkdownIt()

const ASANA_ACCESS_TOKEN = process.env.ASANA_ACCESS_TOKEN
const commit = process.env.GITHUB_SHA
const version = process.env.VERSION
const releaseUrl = process.env.RELEASE_URL
const releaseNotesRaw = process.env.RELEASE_NOTES
const releaseNotes = md.render(releaseNotesRaw)

const templateTaskGid = '1200547430029363'
const autofillProjectGid = '1198964220583541'
const releaseSectionGid = '1200559726959935'
const projectExtractorRegex = /\[\[project_gid=(\d+)]]\s/

let asana

const setupAsana = () => {
    asana = Asana.Client.create({
        'defaultHeaders': {
            'Asana-Enable': 'new_user_task_lists'
        }
    }).useAccessToken(ASANA_ACCESS_TOKEN)
}

const duplicateTemplateTask = (templateTaskGid) => {
    const duplicateOption = {
        include: ['notes', 'assignee', 'subtasks', 'projects'],
        name: `Autofill Release ${version}`,
        opt_fields: 'html_notes'
    }

    return asana.tasks.duplicateTask(templateTaskGid, duplicateOption)
}

const run = async () => {
    setupAsana()

    console.info('Asana on. Duplicating template task...')

    const { new_task } = await duplicateTemplateTask(templateTaskGid)

    const { html_notes: notes } = await asana.tasks.getTask(new_task.gid, { opt_fields: 'html_notes' })

    const updatedNotes =
        notes.replace('[[version]]', version)
            .replace('[[commit]]', commit)
            .replace('[[release_url]]', `<a href="${releaseUrl}">${releaseUrl}</a>`)
            .replace('[[notes]]', releaseNotes)
            .replace(/<\/?p>/ig, '\n')

    console.info('Updating task and moving to Release section...')

    await asana.tasks.updateTask(new_task.gid, {html_notes: updatedNotes})

    await asana.tasks.addProjectForTask(new_task.gid, { project: autofillProjectGid, section: releaseSectionGid })

    console.info('Getting subtasks...')

    const { data: subtasks } = await asana.tasks.getSubtasksForTask(new_task.gid, {opt_fields: 'name,html_notes'})

    console.info('Updating subtasks and moving to appropriate projects...')

    for (const subtask of subtasks) {
        const {gid, name, html_notes} = subtask

        const newName = name.replace('[[version]]', version)
        const projectGid = html_notes.match(projectExtractorRegex)[1]

        const subtaskNotes =
            html_notes.replace(projectExtractorRegex, '')
                .replace('[[notes]]', updatedNotes)

        await asana.tasks.updateTask(gid, { name: newName, html_notes: subtaskNotes })

        if (projectGid) {
            await asana.tasks.addProjectForTask(gid, { project: projectGid, insert_after: null })
        }
    }

    console.info('All done. Enjoy! 🎉')
}

run().catch((e) => {
    console.error(e.value?.errors)
    process.exit(1)
})
