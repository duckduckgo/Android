# asana [![GitHub release][release-image]][release-url] [![NPM Version][npm-image]][npm-url]

- API version: 1.0
- Package version: 3.0.16

## Installation

### For [Node.js](https://nodejs.org/)

#### npm install from [npmjs](https://www.npmjs.com/package/asana)

```shell
npm install asana --save
```

### For browser

Include the latest release directly from GitHub:

```html
<script src="https://github.com/Asana/node-asana/releases/download/v3.0.16/asana-min.js"></script>
```

Example usage (**NOTE**: be careful not to expose your access token):

```html
<script>
    let client = Asana.ApiClient.instance;
    let token = client.authentications['token'];
    token.accessToken = '<YOUR_ACCESS_TOKEN>';

    let usersApiInstance = new Asana.UsersApi();
    let user_gid = "me";
    let opts = {};

    usersApiInstance.getUser(user_gid, opts).then((result) => {
        console.log('API called successfully. Returned data: ' +  JSON.stringify(result.data, null, 2));
    }, (error) => {
        console.error(error.response.body);
    });
</script>
```

### Webpack Configuration

Using Webpack you may encounter the following error: "Module not found: Error:
Cannot resolve module", most certainly you should disable AMD loader. Add/merge
the following section to your webpack config:

```javascript
module: {
  rules: [
    {
      parser: {
        amd: false
      }
    }
  ]
}
```

## Getting Started

Please follow the [installation](#installation) instruction and execute the following JS code:

```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let usersApiInstance = new Asana.UsersApi();
let user_gid = "me"; // String | A string identifying a user. This can either be the string \"me\", an email, or the gid of a user.
let opts = { 
    "opt_fields": "email,name,photo,photo.image_1024x1024,photo.image_128x128,photo.image_21x21,photo.image_27x27,photo.image_36x36,photo.image_60x60,workspaces,workspaces.name" // [String] | This endpoint returns a compact resource, which excludes some properties by default. To include those optional properties, set this query parameter to a comma-separated list of the properties you wish to include.
};

usersApiInstance.getUser(user_gid, opts).then((result) => {
    console.log('API called successfully. Returned data: ' +  JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

### Example: GET, POST, PUT, DELETE on tasks

#### GET - get multiple tasks
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let opts = { 
    "limit": 50, // Number | Results per page. The number of objects to return per page. The value must be between 1 and 100.
    "project": "<YOUR_PROJECT_GID>", // String | The project to filter tasks on.
    "modified_since": "2012-02-22T02:06:58.158Z", // Date | Only return tasks that have been modified since the given time.  *Note: A task is considered “modified” if any of its properties change, or associations between it and other objects are modified (e.g.  a task being added to a project). A task is not considered modified just because another object it is associated with (e.g. a subtask) is modified. Actions that count as modifying the task include assigning, renaming, completing, and adding stories.*
    "opt_fields": "actual_time_minutes,approval_status,assignee,assignee.name,assignee_section,assignee_section.name,assignee_status,completed,completed_at,completed_by,completed_by.name,created_at,created_by,custom_fields,custom_fields.asana_created_field,custom_fields.created_by,custom_fields.created_by.name,custom_fields.currency_code,custom_fields.custom_label,custom_fields.custom_label_position,custom_fields.date_value,custom_fields.date_value.date,custom_fields.date_value.date_time,custom_fields.description,custom_fields.display_value,custom_fields.enabled,custom_fields.enum_options,custom_fields.enum_options.color,custom_fields.enum_options.enabled,custom_fields.enum_options.name,custom_fields.enum_value,custom_fields.enum_value.color,custom_fields.enum_value.enabled,custom_fields.enum_value.name,custom_fields.format,custom_fields.has_notifications_enabled,custom_fields.is_formula_field,custom_fields.is_global_to_workspace,custom_fields.is_value_read_only,custom_fields.multi_enum_values,custom_fields.multi_enum_values.color,custom_fields.multi_enum_values.enabled,custom_fields.multi_enum_values.name,custom_fields.name,custom_fields.number_value,custom_fields.people_value,custom_fields.people_value.name,custom_fields.precision,custom_fields.resource_subtype,custom_fields.text_value,custom_fields.type,dependencies,dependents,due_at,due_on,external,external.data,followers,followers.name,hearted,hearts,hearts.user,hearts.user.name,html_notes,is_rendered_as_separator,liked,likes,likes.user,likes.user.name,memberships,memberships.project,memberships.project.name,memberships.section,memberships.section.name,modified_at,name,notes,num_hearts,num_likes,num_subtasks,offset,parent,parent.created_by,parent.name,parent.resource_subtype,path,permalink_url,projects,projects.name,resource_subtype,start_at,start_on,tags,tags.name,uri,workspace,workspace.name" // [String] | This endpoint returns a compact resource, which excludes some properties by default. To include those optional properties, set this query parameter to a comma-separated list of the properties you wish to include.
};

// GET - get multiple tasks
tasksApiInstance.getTasks(opts).then((result) => {
    console.log('API called successfully. Returned data: ' + JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

#### POST - create a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let body = {
    "data": {
        "name": "New Task",
        "approval_status": "pending",
        "assignee_status": "upcoming",
        "completed": false,
        "external": {
            "gid": "1234",
            "data": "A blob of information.",
        },
        "html_notes": "<body>Mittens <em>really</em> likes the stuff from Humboldt.</body>",
        "is_rendered_as_separator": false,
        "liked": true,
        "assignee": "me",
        "projects": ["<YOUR_PROJECT_GID>"],
    },
};
let opts = {};

// POST - Create a task
tasksApiInstance.createTask(body, opts).then((result) => {
    console.log('API called successfully. Returned data: ' + JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

#### PUT - update a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let task_gid = "<YOUR_TASK_GID>";
let body = {
    "data": {
        "name": "Updated Task",
    },
};
let opts = {};

// PUT - Update a task
tasksApiInstance.updateTask(body, task_gid, opts).then((result) => {
    console.log('API called successfully. Returned data: ' + JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

#### DELETE - delete a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let task_gid = "<YOUR_TASK_GID>";

// DELETE - Delete a task
tasksApiInstance.deleteTask(task_gid).then((result) => {
    console.log('API called successfully. Returned data: ' + JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

## Documentation for API Endpoints

All URIs are relative to *https://app.asana.com/api/1.0*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*Asana.AllocationsApi* | [**createAllocation**](docs/AllocationsApi.md#createAllocation) | **POST** /allocations | Create an allocation
*Asana.AllocationsApi* | [**deleteAllocation**](docs/AllocationsApi.md#deleteAllocation) | **DELETE** /allocations/{allocation_gid} | Delete an allocation
*Asana.AllocationsApi* | [**getAllocation**](docs/AllocationsApi.md#getAllocation) | **GET** /allocations/{allocation_gid} | Get an allocation
*Asana.AllocationsApi* | [**getAllocations**](docs/AllocationsApi.md#getAllocations) | **GET** /allocations | Get multiple allocations
*Asana.AllocationsApi* | [**updateAllocation**](docs/AllocationsApi.md#updateAllocation) | **PUT** /allocations/{allocation_gid} | Update an allocation
*Asana.AttachmentsApi* | [**createAttachmentForObject**](docs/AttachmentsApi.md#createAttachmentForObject) | **POST** /attachments | Upload an attachment
*Asana.AttachmentsApi* | [**deleteAttachment**](docs/AttachmentsApi.md#deleteAttachment) | **DELETE** /attachments/{attachment_gid} | Delete an attachment
*Asana.AttachmentsApi* | [**getAttachment**](docs/AttachmentsApi.md#getAttachment) | **GET** /attachments/{attachment_gid} | Get an attachment
*Asana.AttachmentsApi* | [**getAttachmentsForObject**](docs/AttachmentsApi.md#getAttachmentsForObject) | **GET** /attachments | Get attachments from an object
*Asana.AuditLogAPIApi* | [**getAuditLogEvents**](docs/AuditLogAPIApi.md#getAuditLogEvents) | **GET** /workspaces/{workspace_gid}/audit_log_events | Get audit log events
*Asana.BatchAPIApi* | [**createBatchRequest**](docs/BatchAPIApi.md#createBatchRequest) | **POST** /batch | Submit parallel requests
*Asana.CustomFieldSettingsApi* | [**getCustomFieldSettingsForPortfolio**](docs/CustomFieldSettingsApi.md#getCustomFieldSettingsForPortfolio) | **GET** /portfolios/{portfolio_gid}/custom_field_settings | Get a portfolio&#x27;s custom fields
*Asana.CustomFieldSettingsApi* | [**getCustomFieldSettingsForProject**](docs/CustomFieldSettingsApi.md#getCustomFieldSettingsForProject) | **GET** /projects/{project_gid}/custom_field_settings | Get a project&#x27;s custom fields
*Asana.CustomFieldsApi* | [**createCustomField**](docs/CustomFieldsApi.md#createCustomField) | **POST** /custom_fields | Create a custom field
*Asana.CustomFieldsApi* | [**createEnumOptionForCustomField**](docs/CustomFieldsApi.md#createEnumOptionForCustomField) | **POST** /custom_fields/{custom_field_gid}/enum_options | Create an enum option
*Asana.CustomFieldsApi* | [**deleteCustomField**](docs/CustomFieldsApi.md#deleteCustomField) | **DELETE** /custom_fields/{custom_field_gid} | Delete a custom field
*Asana.CustomFieldsApi* | [**getCustomField**](docs/CustomFieldsApi.md#getCustomField) | **GET** /custom_fields/{custom_field_gid} | Get a custom field
*Asana.CustomFieldsApi* | [**getCustomFieldsForWorkspace**](docs/CustomFieldsApi.md#getCustomFieldsForWorkspace) | **GET** /workspaces/{workspace_gid}/custom_fields | Get a workspace&#x27;s custom fields
*Asana.CustomFieldsApi* | [**insertEnumOptionForCustomField**](docs/CustomFieldsApi.md#insertEnumOptionForCustomField) | **POST** /custom_fields/{custom_field_gid}/enum_options/insert | Reorder a custom field&#x27;s enum
*Asana.CustomFieldsApi* | [**updateCustomField**](docs/CustomFieldsApi.md#updateCustomField) | **PUT** /custom_fields/{custom_field_gid} | Update a custom field
*Asana.CustomFieldsApi* | [**updateEnumOption**](docs/CustomFieldsApi.md#updateEnumOption) | **PUT** /enum_options/{enum_option_gid} | Update an enum option
*Asana.CustomTypesApi* | [**getCustomTypes**](docs/CustomTypesApi.md#getCustomTypes) | **GET** /custom_types | Get all custom types associated with an object
*Asana.EventsApi* | [**getEvents**](docs/EventsApi.md#getEvents) | **GET** /events | Get events on a resource
*Asana.GoalRelationshipsApi* | [**addSupportingRelationship**](docs/GoalRelationshipsApi.md#addSupportingRelationship) | **POST** /goals/{goal_gid}/addSupportingRelationship | Add a supporting goal relationship
*Asana.GoalRelationshipsApi* | [**getGoalRelationship**](docs/GoalRelationshipsApi.md#getGoalRelationship) | **GET** /goal_relationships/{goal_relationship_gid} | Get a goal relationship
*Asana.GoalRelationshipsApi* | [**getGoalRelationships**](docs/GoalRelationshipsApi.md#getGoalRelationships) | **GET** /goal_relationships | Get goal relationships
*Asana.GoalRelationshipsApi* | [**removeSupportingRelationship**](docs/GoalRelationshipsApi.md#removeSupportingRelationship) | **POST** /goals/{goal_gid}/removeSupportingRelationship | Removes a supporting goal relationship
*Asana.GoalRelationshipsApi* | [**updateGoalRelationship**](docs/GoalRelationshipsApi.md#updateGoalRelationship) | **PUT** /goal_relationships/{goal_relationship_gid} | Update a goal relationship
*Asana.GoalsApi* | [**addFollowers**](docs/GoalsApi.md#addFollowers) | **POST** /goals/{goal_gid}/addFollowers | Add a collaborator to a goal
*Asana.GoalsApi* | [**createGoal**](docs/GoalsApi.md#createGoal) | **POST** /goals | Create a goal
*Asana.GoalsApi* | [**createGoalMetric**](docs/GoalsApi.md#createGoalMetric) | **POST** /goals/{goal_gid}/setMetric | Create a goal metric
*Asana.GoalsApi* | [**deleteGoal**](docs/GoalsApi.md#deleteGoal) | **DELETE** /goals/{goal_gid} | Delete a goal
*Asana.GoalsApi* | [**getGoal**](docs/GoalsApi.md#getGoal) | **GET** /goals/{goal_gid} | Get a goal
*Asana.GoalsApi* | [**getGoals**](docs/GoalsApi.md#getGoals) | **GET** /goals | Get goals
*Asana.GoalsApi* | [**getParentGoalsForGoal**](docs/GoalsApi.md#getParentGoalsForGoal) | **GET** /goals/{goal_gid}/parentGoals | Get parent goals from a goal
*Asana.GoalsApi* | [**removeFollowers**](docs/GoalsApi.md#removeFollowers) | **POST** /goals/{goal_gid}/removeFollowers | Remove a collaborator from a goal
*Asana.GoalsApi* | [**updateGoal**](docs/GoalsApi.md#updateGoal) | **PUT** /goals/{goal_gid} | Update a goal
*Asana.GoalsApi* | [**updateGoalMetric**](docs/GoalsApi.md#updateGoalMetric) | **POST** /goals/{goal_gid}/setMetricCurrentValue | Update a goal metric
*Asana.JobsApi* | [**getJob**](docs/JobsApi.md#getJob) | **GET** /jobs/{job_gid} | Get a job by id
*Asana.MembershipsApi* | [**createMembership**](docs/MembershipsApi.md#createMembership) | **POST** /memberships | Create a membership
*Asana.MembershipsApi* | [**deleteMembership**](docs/MembershipsApi.md#deleteMembership) | **DELETE** /memberships/{membership_gid} | Delete a membership
*Asana.MembershipsApi* | [**getMembership**](docs/MembershipsApi.md#getMembership) | **GET** /memberships/{membership_gid} | Get a membership
*Asana.MembershipsApi* | [**getMemberships**](docs/MembershipsApi.md#getMemberships) | **GET** /memberships | Get multiple memberships
*Asana.MembershipsApi* | [**updateMembership**](docs/MembershipsApi.md#updateMembership) | **PUT** /memberships/{membership_gid} | Update a membership
*Asana.OrganizationExportsApi* | [**createOrganizationExport**](docs/OrganizationExportsApi.md#createOrganizationExport) | **POST** /organization_exports | Create an organization export request
*Asana.OrganizationExportsApi* | [**getOrganizationExport**](docs/OrganizationExportsApi.md#getOrganizationExport) | **GET** /organization_exports/{organization_export_gid} | Get details on an org export request
*Asana.PortfolioMembershipsApi* | [**getPortfolioMembership**](docs/PortfolioMembershipsApi.md#getPortfolioMembership) | **GET** /portfolio_memberships/{portfolio_membership_gid} | Get a portfolio membership
*Asana.PortfolioMembershipsApi* | [**getPortfolioMemberships**](docs/PortfolioMembershipsApi.md#getPortfolioMemberships) | **GET** /portfolio_memberships | Get multiple portfolio memberships
*Asana.PortfolioMembershipsApi* | [**getPortfolioMembershipsForPortfolio**](docs/PortfolioMembershipsApi.md#getPortfolioMembershipsForPortfolio) | **GET** /portfolios/{portfolio_gid}/portfolio_memberships | Get memberships from a portfolio
*Asana.PortfoliosApi* | [**addCustomFieldSettingForPortfolio**](docs/PortfoliosApi.md#addCustomFieldSettingForPortfolio) | **POST** /portfolios/{portfolio_gid}/addCustomFieldSetting | Add a custom field to a portfolio
*Asana.PortfoliosApi* | [**addItemForPortfolio**](docs/PortfoliosApi.md#addItemForPortfolio) | **POST** /portfolios/{portfolio_gid}/addItem | Add a portfolio item
*Asana.PortfoliosApi* | [**addMembersForPortfolio**](docs/PortfoliosApi.md#addMembersForPortfolio) | **POST** /portfolios/{portfolio_gid}/addMembers | Add users to a portfolio
*Asana.PortfoliosApi* | [**createPortfolio**](docs/PortfoliosApi.md#createPortfolio) | **POST** /portfolios | Create a portfolio
*Asana.PortfoliosApi* | [**deletePortfolio**](docs/PortfoliosApi.md#deletePortfolio) | **DELETE** /portfolios/{portfolio_gid} | Delete a portfolio
*Asana.PortfoliosApi* | [**getItemsForPortfolio**](docs/PortfoliosApi.md#getItemsForPortfolio) | **GET** /portfolios/{portfolio_gid}/items | Get portfolio items
*Asana.PortfoliosApi* | [**getPortfolio**](docs/PortfoliosApi.md#getPortfolio) | **GET** /portfolios/{portfolio_gid} | Get a portfolio
*Asana.PortfoliosApi* | [**getPortfolios**](docs/PortfoliosApi.md#getPortfolios) | **GET** /portfolios | Get multiple portfolios
*Asana.PortfoliosApi* | [**removeCustomFieldSettingForPortfolio**](docs/PortfoliosApi.md#removeCustomFieldSettingForPortfolio) | **POST** /portfolios/{portfolio_gid}/removeCustomFieldSetting | Remove a custom field from a portfolio
*Asana.PortfoliosApi* | [**removeItemForPortfolio**](docs/PortfoliosApi.md#removeItemForPortfolio) | **POST** /portfolios/{portfolio_gid}/removeItem | Remove a portfolio item
*Asana.PortfoliosApi* | [**removeMembersForPortfolio**](docs/PortfoliosApi.md#removeMembersForPortfolio) | **POST** /portfolios/{portfolio_gid}/removeMembers | Remove users from a portfolio
*Asana.PortfoliosApi* | [**updatePortfolio**](docs/PortfoliosApi.md#updatePortfolio) | **PUT** /portfolios/{portfolio_gid} | Update a portfolio
*Asana.ProjectBriefsApi* | [**createProjectBrief**](docs/ProjectBriefsApi.md#createProjectBrief) | **POST** /projects/{project_gid}/project_briefs | Create a project brief
*Asana.ProjectBriefsApi* | [**deleteProjectBrief**](docs/ProjectBriefsApi.md#deleteProjectBrief) | **DELETE** /project_briefs/{project_brief_gid} | Delete a project brief
*Asana.ProjectBriefsApi* | [**getProjectBrief**](docs/ProjectBriefsApi.md#getProjectBrief) | **GET** /project_briefs/{project_brief_gid} | Get a project brief
*Asana.ProjectBriefsApi* | [**updateProjectBrief**](docs/ProjectBriefsApi.md#updateProjectBrief) | **PUT** /project_briefs/{project_brief_gid} | Update a project brief
*Asana.ProjectMembershipsApi* | [**getProjectMembership**](docs/ProjectMembershipsApi.md#getProjectMembership) | **GET** /project_memberships/{project_membership_gid} | Get a project membership
*Asana.ProjectMembershipsApi* | [**getProjectMembershipsForProject**](docs/ProjectMembershipsApi.md#getProjectMembershipsForProject) | **GET** /projects/{project_gid}/project_memberships | Get memberships from a project
*Asana.ProjectStatusesApi* | [**createProjectStatusForProject**](docs/ProjectStatusesApi.md#createProjectStatusForProject) | **POST** /projects/{project_gid}/project_statuses | Create a project status
*Asana.ProjectStatusesApi* | [**deleteProjectStatus**](docs/ProjectStatusesApi.md#deleteProjectStatus) | **DELETE** /project_statuses/{project_status_gid} | Delete a project status
*Asana.ProjectStatusesApi* | [**getProjectStatus**](docs/ProjectStatusesApi.md#getProjectStatus) | **GET** /project_statuses/{project_status_gid} | Get a project status
*Asana.ProjectStatusesApi* | [**getProjectStatusesForProject**](docs/ProjectStatusesApi.md#getProjectStatusesForProject) | **GET** /projects/{project_gid}/project_statuses | Get statuses from a project
*Asana.ProjectTemplatesApi* | [**deleteProjectTemplate**](docs/ProjectTemplatesApi.md#deleteProjectTemplate) | **DELETE** /project_templates/{project_template_gid} | Delete a project template
*Asana.ProjectTemplatesApi* | [**getProjectTemplate**](docs/ProjectTemplatesApi.md#getProjectTemplate) | **GET** /project_templates/{project_template_gid} | Get a project template
*Asana.ProjectTemplatesApi* | [**getProjectTemplates**](docs/ProjectTemplatesApi.md#getProjectTemplates) | **GET** /project_templates | Get multiple project templates
*Asana.ProjectTemplatesApi* | [**getProjectTemplatesForTeam**](docs/ProjectTemplatesApi.md#getProjectTemplatesForTeam) | **GET** /teams/{team_gid}/project_templates | Get a team&#x27;s project templates
*Asana.ProjectTemplatesApi* | [**instantiateProject**](docs/ProjectTemplatesApi.md#instantiateProject) | **POST** /project_templates/{project_template_gid}/instantiateProject | Instantiate a project from a project template
*Asana.ProjectsApi* | [**addCustomFieldSettingForProject**](docs/ProjectsApi.md#addCustomFieldSettingForProject) | **POST** /projects/{project_gid}/addCustomFieldSetting | Add a custom field to a project
*Asana.ProjectsApi* | [**addFollowersForProject**](docs/ProjectsApi.md#addFollowersForProject) | **POST** /projects/{project_gid}/addFollowers | Add followers to a project
*Asana.ProjectsApi* | [**addMembersForProject**](docs/ProjectsApi.md#addMembersForProject) | **POST** /projects/{project_gid}/addMembers | Add users to a project
*Asana.ProjectsApi* | [**createProject**](docs/ProjectsApi.md#createProject) | **POST** /projects | Create a project
*Asana.ProjectsApi* | [**createProjectForTeam**](docs/ProjectsApi.md#createProjectForTeam) | **POST** /teams/{team_gid}/projects | Create a project in a team
*Asana.ProjectsApi* | [**createProjectForWorkspace**](docs/ProjectsApi.md#createProjectForWorkspace) | **POST** /workspaces/{workspace_gid}/projects | Create a project in a workspace
*Asana.ProjectsApi* | [**deleteProject**](docs/ProjectsApi.md#deleteProject) | **DELETE** /projects/{project_gid} | Delete a project
*Asana.ProjectsApi* | [**duplicateProject**](docs/ProjectsApi.md#duplicateProject) | **POST** /projects/{project_gid}/duplicate | Duplicate a project
*Asana.ProjectsApi* | [**getProject**](docs/ProjectsApi.md#getProject) | **GET** /projects/{project_gid} | Get a project
*Asana.ProjectsApi* | [**getProjects**](docs/ProjectsApi.md#getProjects) | **GET** /projects | Get multiple projects
*Asana.ProjectsApi* | [**getProjectsForTask**](docs/ProjectsApi.md#getProjectsForTask) | **GET** /tasks/{task_gid}/projects | Get projects a task is in
*Asana.ProjectsApi* | [**getProjectsForTeam**](docs/ProjectsApi.md#getProjectsForTeam) | **GET** /teams/{team_gid}/projects | Get a team&#x27;s projects
*Asana.ProjectsApi* | [**getProjectsForWorkspace**](docs/ProjectsApi.md#getProjectsForWorkspace) | **GET** /workspaces/{workspace_gid}/projects | Get all projects in a workspace
*Asana.ProjectsApi* | [**getTaskCountsForProject**](docs/ProjectsApi.md#getTaskCountsForProject) | **GET** /projects/{project_gid}/task_counts | Get task count of a project
*Asana.ProjectsApi* | [**projectSaveAsTemplate**](docs/ProjectsApi.md#projectSaveAsTemplate) | **POST** /projects/{project_gid}/saveAsTemplate | Create a project template from a project
*Asana.ProjectsApi* | [**removeCustomFieldSettingForProject**](docs/ProjectsApi.md#removeCustomFieldSettingForProject) | **POST** /projects/{project_gid}/removeCustomFieldSetting | Remove a custom field from a project
*Asana.ProjectsApi* | [**removeFollowersForProject**](docs/ProjectsApi.md#removeFollowersForProject) | **POST** /projects/{project_gid}/removeFollowers | Remove followers from a project
*Asana.ProjectsApi* | [**removeMembersForProject**](docs/ProjectsApi.md#removeMembersForProject) | **POST** /projects/{project_gid}/removeMembers | Remove users from a project
*Asana.ProjectsApi* | [**updateProject**](docs/ProjectsApi.md#updateProject) | **PUT** /projects/{project_gid} | Update a project
*Asana.RulesApi* | [**triggerRule**](docs/RulesApi.md#triggerRule) | **POST** /rule_triggers/{rule_trigger_gid}/run | Trigger a rule
*Asana.SectionsApi* | [**addTaskForSection**](docs/SectionsApi.md#addTaskForSection) | **POST** /sections/{section_gid}/addTask | Add task to section
*Asana.SectionsApi* | [**createSectionForProject**](docs/SectionsApi.md#createSectionForProject) | **POST** /projects/{project_gid}/sections | Create a section in a project
*Asana.SectionsApi* | [**deleteSection**](docs/SectionsApi.md#deleteSection) | **DELETE** /sections/{section_gid} | Delete a section
*Asana.SectionsApi* | [**getSection**](docs/SectionsApi.md#getSection) | **GET** /sections/{section_gid} | Get a section
*Asana.SectionsApi* | [**getSectionsForProject**](docs/SectionsApi.md#getSectionsForProject) | **GET** /projects/{project_gid}/sections | Get sections in a project
*Asana.SectionsApi* | [**insertSectionForProject**](docs/SectionsApi.md#insertSectionForProject) | **POST** /projects/{project_gid}/sections/insert | Move or Insert sections
*Asana.SectionsApi* | [**updateSection**](docs/SectionsApi.md#updateSection) | **PUT** /sections/{section_gid} | Update a section
*Asana.StatusUpdatesApi* | [**createStatusForObject**](docs/StatusUpdatesApi.md#createStatusForObject) | **POST** /status_updates | Create a status update
*Asana.StatusUpdatesApi* | [**deleteStatus**](docs/StatusUpdatesApi.md#deleteStatus) | **DELETE** /status_updates/{status_update_gid} | Delete a status update
*Asana.StatusUpdatesApi* | [**getStatus**](docs/StatusUpdatesApi.md#getStatus) | **GET** /status_updates/{status_update_gid} | Get a status update
*Asana.StatusUpdatesApi* | [**getStatusesForObject**](docs/StatusUpdatesApi.md#getStatusesForObject) | **GET** /status_updates | Get status updates from an object
*Asana.StoriesApi* | [**createStoryForTask**](docs/StoriesApi.md#createStoryForTask) | **POST** /tasks/{task_gid}/stories | Create a story on a task
*Asana.StoriesApi* | [**deleteStory**](docs/StoriesApi.md#deleteStory) | **DELETE** /stories/{story_gid} | Delete a story
*Asana.StoriesApi* | [**getStoriesForTask**](docs/StoriesApi.md#getStoriesForTask) | **GET** /tasks/{task_gid}/stories | Get stories from a task
*Asana.StoriesApi* | [**getStory**](docs/StoriesApi.md#getStory) | **GET** /stories/{story_gid} | Get a story
*Asana.StoriesApi* | [**updateStory**](docs/StoriesApi.md#updateStory) | **PUT** /stories/{story_gid} | Update a story
*Asana.TagsApi* | [**createTag**](docs/TagsApi.md#createTag) | **POST** /tags | Create a tag
*Asana.TagsApi* | [**createTagForWorkspace**](docs/TagsApi.md#createTagForWorkspace) | **POST** /workspaces/{workspace_gid}/tags | Create a tag in a workspace
*Asana.TagsApi* | [**deleteTag**](docs/TagsApi.md#deleteTag) | **DELETE** /tags/{tag_gid} | Delete a tag
*Asana.TagsApi* | [**getTag**](docs/TagsApi.md#getTag) | **GET** /tags/{tag_gid} | Get a tag
*Asana.TagsApi* | [**getTags**](docs/TagsApi.md#getTags) | **GET** /tags | Get multiple tags
*Asana.TagsApi* | [**getTagsForTask**](docs/TagsApi.md#getTagsForTask) | **GET** /tasks/{task_gid}/tags | Get a task&#x27;s tags
*Asana.TagsApi* | [**getTagsForWorkspace**](docs/TagsApi.md#getTagsForWorkspace) | **GET** /workspaces/{workspace_gid}/tags | Get tags in a workspace
*Asana.TagsApi* | [**updateTag**](docs/TagsApi.md#updateTag) | **PUT** /tags/{tag_gid} | Update a tag
*Asana.TaskTemplatesApi* | [**deleteTaskTemplate**](docs/TaskTemplatesApi.md#deleteTaskTemplate) | **DELETE** /task_templates/{task_template_gid} | Delete a task template
*Asana.TaskTemplatesApi* | [**getTaskTemplate**](docs/TaskTemplatesApi.md#getTaskTemplate) | **GET** /task_templates/{task_template_gid} | Get a task template
*Asana.TaskTemplatesApi* | [**getTaskTemplates**](docs/TaskTemplatesApi.md#getTaskTemplates) | **GET** /task_templates | Get multiple task templates
*Asana.TaskTemplatesApi* | [**instantiateTask**](docs/TaskTemplatesApi.md#instantiateTask) | **POST** /task_templates/{task_template_gid}/instantiateTask | Instantiate a task from a task template
*Asana.TasksApi* | [**addDependenciesForTask**](docs/TasksApi.md#addDependenciesForTask) | **POST** /tasks/{task_gid}/addDependencies | Set dependencies for a task
*Asana.TasksApi* | [**addDependentsForTask**](docs/TasksApi.md#addDependentsForTask) | **POST** /tasks/{task_gid}/addDependents | Set dependents for a task
*Asana.TasksApi* | [**addFollowersForTask**](docs/TasksApi.md#addFollowersForTask) | **POST** /tasks/{task_gid}/addFollowers | Add followers to a task
*Asana.TasksApi* | [**addProjectForTask**](docs/TasksApi.md#addProjectForTask) | **POST** /tasks/{task_gid}/addProject | Add a project to a task
*Asana.TasksApi* | [**addTagForTask**](docs/TasksApi.md#addTagForTask) | **POST** /tasks/{task_gid}/addTag | Add a tag to a task
*Asana.TasksApi* | [**createSubtaskForTask**](docs/TasksApi.md#createSubtaskForTask) | **POST** /tasks/{task_gid}/subtasks | Create a subtask
*Asana.TasksApi* | [**createTask**](docs/TasksApi.md#createTask) | **POST** /tasks | Create a task
*Asana.TasksApi* | [**deleteTask**](docs/TasksApi.md#deleteTask) | **DELETE** /tasks/{task_gid} | Delete a task
*Asana.TasksApi* | [**duplicateTask**](docs/TasksApi.md#duplicateTask) | **POST** /tasks/{task_gid}/duplicate | Duplicate a task
*Asana.TasksApi* | [**getDependenciesForTask**](docs/TasksApi.md#getDependenciesForTask) | **GET** /tasks/{task_gid}/dependencies | Get dependencies from a task
*Asana.TasksApi* | [**getDependentsForTask**](docs/TasksApi.md#getDependentsForTask) | **GET** /tasks/{task_gid}/dependents | Get dependents from a task
*Asana.TasksApi* | [**getSubtasksForTask**](docs/TasksApi.md#getSubtasksForTask) | **GET** /tasks/{task_gid}/subtasks | Get subtasks from a task
*Asana.TasksApi* | [**getTask**](docs/TasksApi.md#getTask) | **GET** /tasks/{task_gid} | Get a task
*Asana.TasksApi* | [**getTaskForCustomID**](docs/TasksApi.md#getTaskForCustomID) | **GET** /workspaces/{workspace_gid}/tasks/custom_id/{custom_id} | Get a task for a given custom ID
*Asana.TasksApi* | [**getTasks**](docs/TasksApi.md#getTasks) | **GET** /tasks | Get multiple tasks
*Asana.TasksApi* | [**getTasksForProject**](docs/TasksApi.md#getTasksForProject) | **GET** /projects/{project_gid}/tasks | Get tasks from a project
*Asana.TasksApi* | [**getTasksForSection**](docs/TasksApi.md#getTasksForSection) | **GET** /sections/{section_gid}/tasks | Get tasks from a section
*Asana.TasksApi* | [**getTasksForTag**](docs/TasksApi.md#getTasksForTag) | **GET** /tags/{tag_gid}/tasks | Get tasks from a tag
*Asana.TasksApi* | [**getTasksForUserTaskList**](docs/TasksApi.md#getTasksForUserTaskList) | **GET** /user_task_lists/{user_task_list_gid}/tasks | Get tasks from a user task list
*Asana.TasksApi* | [**removeDependenciesForTask**](docs/TasksApi.md#removeDependenciesForTask) | **POST** /tasks/{task_gid}/removeDependencies | Unlink dependencies from a task
*Asana.TasksApi* | [**removeDependentsForTask**](docs/TasksApi.md#removeDependentsForTask) | **POST** /tasks/{task_gid}/removeDependents | Unlink dependents from a task
*Asana.TasksApi* | [**removeFollowerForTask**](docs/TasksApi.md#removeFollowerForTask) | **POST** /tasks/{task_gid}/removeFollowers | Remove followers from a task
*Asana.TasksApi* | [**removeProjectForTask**](docs/TasksApi.md#removeProjectForTask) | **POST** /tasks/{task_gid}/removeProject | Remove a project from a task
*Asana.TasksApi* | [**removeTagForTask**](docs/TasksApi.md#removeTagForTask) | **POST** /tasks/{task_gid}/removeTag | Remove a tag from a task
*Asana.TasksApi* | [**searchTasksForWorkspace**](docs/TasksApi.md#searchTasksForWorkspace) | **GET** /workspaces/{workspace_gid}/tasks/search | Search tasks in a workspace
*Asana.TasksApi* | [**setParentForTask**](docs/TasksApi.md#setParentForTask) | **POST** /tasks/{task_gid}/setParent | Set the parent of a task
*Asana.TasksApi* | [**updateTask**](docs/TasksApi.md#updateTask) | **PUT** /tasks/{task_gid} | Update a task
*Asana.TeamMembershipsApi* | [**getTeamMembership**](docs/TeamMembershipsApi.md#getTeamMembership) | **GET** /team_memberships/{team_membership_gid} | Get a team membership
*Asana.TeamMembershipsApi* | [**getTeamMemberships**](docs/TeamMembershipsApi.md#getTeamMemberships) | **GET** /team_memberships | Get team memberships
*Asana.TeamMembershipsApi* | [**getTeamMembershipsForTeam**](docs/TeamMembershipsApi.md#getTeamMembershipsForTeam) | **GET** /teams/{team_gid}/team_memberships | Get memberships from a team
*Asana.TeamMembershipsApi* | [**getTeamMembershipsForUser**](docs/TeamMembershipsApi.md#getTeamMembershipsForUser) | **GET** /users/{user_gid}/team_memberships | Get memberships from a user
*Asana.TeamsApi* | [**addUserForTeam**](docs/TeamsApi.md#addUserForTeam) | **POST** /teams/{team_gid}/addUser | Add a user to a team
*Asana.TeamsApi* | [**createTeam**](docs/TeamsApi.md#createTeam) | **POST** /teams | Create a team
*Asana.TeamsApi* | [**getTeam**](docs/TeamsApi.md#getTeam) | **GET** /teams/{team_gid} | Get a team
*Asana.TeamsApi* | [**getTeamsForUser**](docs/TeamsApi.md#getTeamsForUser) | **GET** /users/{user_gid}/teams | Get teams for a user
*Asana.TeamsApi* | [**getTeamsForWorkspace**](docs/TeamsApi.md#getTeamsForWorkspace) | **GET** /workspaces/{workspace_gid}/teams | Get teams in a workspace
*Asana.TeamsApi* | [**removeUserForTeam**](docs/TeamsApi.md#removeUserForTeam) | **POST** /teams/{team_gid}/removeUser | Remove a user from a team
*Asana.TeamsApi* | [**updateTeam**](docs/TeamsApi.md#updateTeam) | **PUT** /teams/{team_gid} | Update a team
*Asana.TimePeriodsApi* | [**getTimePeriod**](docs/TimePeriodsApi.md#getTimePeriod) | **GET** /time_periods/{time_period_gid} | Get a time period
*Asana.TimePeriodsApi* | [**getTimePeriods**](docs/TimePeriodsApi.md#getTimePeriods) | **GET** /time_periods | Get time periods
*Asana.TimeTrackingEntriesApi* | [**createTimeTrackingEntry**](docs/TimeTrackingEntriesApi.md#createTimeTrackingEntry) | **POST** /tasks/{task_gid}/time_tracking_entries | Create a time tracking entry
*Asana.TimeTrackingEntriesApi* | [**deleteTimeTrackingEntry**](docs/TimeTrackingEntriesApi.md#deleteTimeTrackingEntry) | **DELETE** /time_tracking_entries/{time_tracking_entry_gid} | Delete a time tracking entry
*Asana.TimeTrackingEntriesApi* | [**getTimeTrackingEntriesForTask**](docs/TimeTrackingEntriesApi.md#getTimeTrackingEntriesForTask) | **GET** /tasks/{task_gid}/time_tracking_entries | Get time tracking entries for a task
*Asana.TimeTrackingEntriesApi* | [**getTimeTrackingEntry**](docs/TimeTrackingEntriesApi.md#getTimeTrackingEntry) | **GET** /time_tracking_entries/{time_tracking_entry_gid} | Get a time tracking entry
*Asana.TimeTrackingEntriesApi* | [**updateTimeTrackingEntry**](docs/TimeTrackingEntriesApi.md#updateTimeTrackingEntry) | **PUT** /time_tracking_entries/{time_tracking_entry_gid} | Update a time tracking entry
*Asana.TypeaheadApi* | [**typeaheadForWorkspace**](docs/TypeaheadApi.md#typeaheadForWorkspace) | **GET** /workspaces/{workspace_gid}/typeahead | Get objects via typeahead
*Asana.UserTaskListsApi* | [**getUserTaskList**](docs/UserTaskListsApi.md#getUserTaskList) | **GET** /user_task_lists/{user_task_list_gid} | Get a user task list
*Asana.UserTaskListsApi* | [**getUserTaskListForUser**](docs/UserTaskListsApi.md#getUserTaskListForUser) | **GET** /users/{user_gid}/user_task_list | Get a user&#x27;s task list
*Asana.UsersApi* | [**getFavoritesForUser**](docs/UsersApi.md#getFavoritesForUser) | **GET** /users/{user_gid}/favorites | Get a user&#x27;s favorites
*Asana.UsersApi* | [**getUser**](docs/UsersApi.md#getUser) | **GET** /users/{user_gid} | Get a user
*Asana.UsersApi* | [**getUsers**](docs/UsersApi.md#getUsers) | **GET** /users | Get multiple users
*Asana.UsersApi* | [**getUsersForTeam**](docs/UsersApi.md#getUsersForTeam) | **GET** /teams/{team_gid}/users | Get users in a team
*Asana.UsersApi* | [**getUsersForWorkspace**](docs/UsersApi.md#getUsersForWorkspace) | **GET** /workspaces/{workspace_gid}/users | Get users in a workspace or organization
*Asana.WebhooksApi* | [**createWebhook**](docs/WebhooksApi.md#createWebhook) | **POST** /webhooks | Establish a webhook
*Asana.WebhooksApi* | [**deleteWebhook**](docs/WebhooksApi.md#deleteWebhook) | **DELETE** /webhooks/{webhook_gid} | Delete a webhook
*Asana.WebhooksApi* | [**getWebhook**](docs/WebhooksApi.md#getWebhook) | **GET** /webhooks/{webhook_gid} | Get a webhook
*Asana.WebhooksApi* | [**getWebhooks**](docs/WebhooksApi.md#getWebhooks) | **GET** /webhooks | Get multiple webhooks
*Asana.WebhooksApi* | [**updateWebhook**](docs/WebhooksApi.md#updateWebhook) | **PUT** /webhooks/{webhook_gid} | Update a webhook
*Asana.WorkspaceMembershipsApi* | [**getWorkspaceMembership**](docs/WorkspaceMembershipsApi.md#getWorkspaceMembership) | **GET** /workspace_memberships/{workspace_membership_gid} | Get a workspace membership
*Asana.WorkspaceMembershipsApi* | [**getWorkspaceMembershipsForUser**](docs/WorkspaceMembershipsApi.md#getWorkspaceMembershipsForUser) | **GET** /users/{user_gid}/workspace_memberships | Get workspace memberships for a user
*Asana.WorkspaceMembershipsApi* | [**getWorkspaceMembershipsForWorkspace**](docs/WorkspaceMembershipsApi.md#getWorkspaceMembershipsForWorkspace) | **GET** /workspaces/{workspace_gid}/workspace_memberships | Get the workspace memberships for a workspace
*Asana.WorkspacesApi* | [**addUserForWorkspace**](docs/WorkspacesApi.md#addUserForWorkspace) | **POST** /workspaces/{workspace_gid}/addUser | Add a user to a workspace or organization
*Asana.WorkspacesApi* | [**getWorkspace**](docs/WorkspacesApi.md#getWorkspace) | **GET** /workspaces/{workspace_gid} | Get a workspace
*Asana.WorkspacesApi* | [**getWorkspaceEvents**](docs/WorkspacesApi.md#getWorkspaceEvents) | **GET** /workspaces/{workspace_gid}/events | Get workspace events
*Asana.WorkspacesApi* | [**getWorkspaces**](docs/WorkspacesApi.md#getWorkspaces) | **GET** /workspaces | Get multiple workspaces
*Asana.WorkspacesApi* | [**removeUserForWorkspace**](docs/WorkspacesApi.md#removeUserForWorkspace) | **POST** /workspaces/{workspace_gid}/removeUser | Remove a user from a workspace or organization
*Asana.WorkspacesApi* | [**updateWorkspace**](docs/WorkspacesApi.md#updateWorkspace) | **PUT** /workspaces/{workspace_gid} | Update a workspace

## Optional fields

Our `opt_fields` feature allows you to request for properties of a resource that you want to be returned in the response (more information [here](https://developers.asana.com/docs/inputoutput-options)).

**NOTE**: by default, endpoints that return an array of results (EX: [Get multiple tasks](https://developers.asana.com/reference/gettasks), [Get multiple projects](https://developers.asana.com/reference/getprojects)), will return a compact version of those results (EX: [Get multiple tasks](https://developers.asana.com/reference/gettasks) returns an array of [TaskCompact](https://developers.asana.com/reference/tasks#taskcompact) objects).

### EX: [Get multiple tasks](https://developers.asana.com/reference/gettasks) / [**getTasks**](docs/TasksApi.md#getTasks) without `opt_fields`

#### Example Request
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let opts = {
    "limit": 2,
    "project": "<YOUR_PROJECT_GID>"
};

// GET - get multiple tasks
tasksApiInstance.getTasks(opts).then((result) => {
    console.log(JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

#### Example Response
```javascript
[
  {
    "gid": "123",
    "name": "Task 1",
    "resource_type": "task",
    "resource_subtype": "default_task"
  },
  {
    "gid": "456",
    "name": "Task 2",
    "resource_type": "task",
    "resource_subtype": "default_task"
  }
]
```

### EX: [Get multiple tasks](https://developers.asana.com/reference/gettasks) / [**getTasks**](docs/TasksApi.md#getTasks) with `opt_fields`

#### Example Request
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let opts = { 
    "limit": 1,
    "project": "<YOUR_PROJECT_GID>",
    "opt_fields": "actual_time_minutes,approval_status,assignee,assignee.name,assignee_section,assignee_section.name,assignee_status,completed,completed_at,completed_by,completed_by.name,created_at,created_by,custom_fields,custom_fields.asana_created_field,custom_fields.created_by,custom_fields.created_by.name,custom_fields.currency_code,custom_fields.custom_label,custom_fields.custom_label_position,custom_fields.date_value,custom_fields.date_value.date,custom_fields.date_value.date_time,custom_fields.description,custom_fields.display_value,custom_fields.enabled,custom_fields.enum_options,custom_fields.enum_options.color,custom_fields.enum_options.enabled,custom_fields.enum_options.name,custom_fields.enum_value,custom_fields.enum_value.color,custom_fields.enum_value.enabled,custom_fields.enum_value.name,custom_fields.format,custom_fields.has_notifications_enabled,custom_fields.is_formula_field,custom_fields.is_global_to_workspace,custom_fields.is_value_read_only,custom_fields.multi_enum_values,custom_fields.multi_enum_values.color,custom_fields.multi_enum_values.enabled,custom_fields.multi_enum_values.name,custom_fields.name,custom_fields.number_value,custom_fields.people_value,custom_fields.people_value.name,custom_fields.precision,custom_fields.resource_subtype,custom_fields.text_value,custom_fields.type,dependencies,dependents,due_at,due_on,external,external.data,followers,followers.name,hearted,hearts,hearts.user,hearts.user.name,html_notes,is_rendered_as_separator,liked,likes,likes.user,likes.user.name,memberships,memberships.project,memberships.project.name,memberships.section,memberships.section.name,modified_at,name,notes,num_hearts,num_likes,num_subtasks,offset,parent,parent.created_by,parent.name,parent.resource_subtype,path,permalink_url,projects,projects.name,resource_subtype,start_at,start_on,tags,tags.name,uri,workspace,workspace.name"
};

// GET - get multiple tasks
tasksApiInstance.getTasks(opts).then((result) => {
    console.log(JSON.stringify(result.data, null, 2));
}, (error) => {
    console.error(error.response.body);
});
```

#### Example Response
```javascript
[
  {
    "gid": "129839839",
    "actual_time_minutes": null,
    "assignee": {
      "gid": "120938293",
      "name": "user@example.com"
    },
    "assignee_status": "upcoming",
    "assignee_section": {
      "gid": "1094838938",
      "name": "Recently assigned"
    },
    "completed": false,
    "completed_at": null,
    "completed_by": null,
    "created_at": "2023-01-01T20:31:21.717Z",
    "created_by": {
      "gid": "1201784467042440",
      "resource_type": "user"
    },
    "custom_fields": [
      {
        "gid": "191859815",
        "enabled": true,
        "name": "Estimated time",
        "description": "Asana-created. Estimate time to complete a task.",
        "number_value": null,
        "precision": 0,
        "format": "duration",
        "currency_code": null,
        "custom_label": null,
        "created_by": null,
        "custom_label_position": null,
        "display_value": null,
        "resource_subtype": "number",
        "is_formula_field": false,
        "is_value_read_only": false,
        "type": "number"
      }
    ],
    "dependencies": [],
    "dependents": [],
    "due_at": "2025-01-20T02:06:58.000Z",
    "due_on": "2025-01-19",
    "followers": [
      {
        "gid": "120938293",
        "name": "user@example.com"
      }
    ],
    "hearted": true,
    "hearts": [
      {
        "gid": "594849843",
        "user": {
          "gid": "120938293",
          "name": "user@example.com"
        }
      }
    ],
    "html_notes": "<body>Example task notes</body>",
    "is_rendered_as_separator": false,
    "liked": true,
    "likes": [
      {
        "gid": "58303939",
        "user": {
          "gid": "120938293",
          "name": "user@example.com"
        }
      }
    ],
    "memberships": [
      {
        "project": {
          "gid": "4567",
          "name": "Example Project"
        },
        "section": {
          "gid": "8900",
          "name": "Untitled section"
        }
      }
    ],
    "modified_at": "2023-01-25T21:24:06.996Z",
    "name": "Task 1",
    "notes": "Example task notes",
    "num_hearts": 1,
    "num_likes": 1,
    "num_subtasks": 0,
    "parent": null,
    "permalink_url": "https://app.asana.com/0/58303939/129839839",
    "projects": [
      {
        "gid": "4567",
        "name": "Example Project"
      }
    ],
    "start_at": null,
    "start_on": null,
    "tags": [],
    "resource_subtype": "default_task",
    "workspace": {
      "gid": "111111",
      "name": "Example Workspace"
    }
  }
]
```

## Pagination

By default, endpoints that return an array of results (EX: [Get multiple tasks](https://developers.asana.com/reference/gettasks), [Get multiple projects](https://developers.asana.com/reference/getprojects)), will return a [Collection](src/utils/collection.js) object.
This collection object contains a `nextPage` method that can be used to fetch for the next page of results. **NOTE**: in order to use `nextPage` you must have provided a `limit` query parameter argument in the initial request.

### Use case

You may run into the following error when making a request to an endpoint that has >1000 results:

> "The result is too large. You should use pagination (may require specifying a workspace)!"

In this scenario you will want to use pagaintion to gather your results. To do this, you will need to provide a `limit` query parameter argument in your request. This `limit` query parameter represents the number of results per page. NOTE: the `limit` can only be between 1 and 100.

EX: Pagination gather all resources
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let opts = {
    "project": "<YOUR_PROJECT_GID>",
    "limit": 100,
};

async function getAllTasks(opts) {
    let tasks = await tasksApiInstance.getTasks(opts).then(async (response) => {
        let result = [];
        let page = response;
        while(true) {
            // Add items on page to list of results
            result = result.concat(page.data);
            // Fetch the next page
            page = await page.nextPage();
            // If the there is no data in the next page break from the loop
            if (!page.data) {
                break;
            }
        }
        return result;
    }, (error) => {
        console.error(error.response.body);
    });
    // Do something with the tasks. EX: print out results
    console.log('Tasks: ' + JSON.stringify(tasks, null, 2));
}

getAllTasks(opts);

```

Sample output:
```bash
Tasks: [
    {
      "gid": "123",
      "name": "task 1",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "456",
      "name": "task 2",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "789",
      "name": "task 3",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "101112",
      "name": "task 4",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "131415",
      "name": "task 5",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "161718",
      "name": "task 6",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "192021",
      "name": "task 7",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "222324",
      "name": "task 8",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "252627",
      "name": "task 9",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "282930",
      "name": "task 10",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
    {
      "gid": "313233",
      "name": "task 11",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
]
```

EX: Pagination do something per page
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let opts = {
    'project': "<YOUR_PROJECT_GID>",
    "limit": 5,
};

let pageIndex = 1;

tasksApiInstance.getTasks(opts).then(async (response) => {
    let page = response;
    while(true) {
        // Do something with the page results
        // EX: print the name of the tasks on that page
        console.log(`Page ${pageIndex}: `);
        page.data.forEach(task => {
            console.log(`    ${task.name}`);
        });
        pageIndex += 1;

        page = await page.nextPage();
        // If the there is no data in the next page break from the loop
        if (!page.data) {
            break;
        }
    }
}, (error) => {
    console.error(error.response.body);
});

```

Sample output:

```bash
Page 1:
    task 1
    task 2
    task 3
    task 4
    task 5
Page 2:
    task 6
    task 7
    task 8
    task 9
    task 10
Page 3:
    task 11
    task 12
    task 13
    task 14
    task 15
```

### Turning off Pagination

If you do not want a [Collection](src/utils/collection.js) object returned and want to implement your own pagination, you can disable pagination by setting `RETURN_COLLECTION` to `false`:

EX: Turning off pagination
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// Turn off pagination
client.RETURN_COLLECTION = false;

let tasksApiInstance = new Asana.TasksApi();
let opts = {
    'project': "<YOUR_PROJECT_GID>",
    'limit': 1
};
tasksApiInstance.getTasks(opts).then((result) => {
    console.log('API called successfully. Returned data: ' + JSON.stringify(result, null, 2));
})
```

Sample response:
```
API called successfully. Returned data: {
  "data": [
    {
      "gid": "<TASK_GID>",
      "name": "Task 1",
      "resource_type": "task",
      "resource_subtype": "default_task"
    },
  ],
  "next_page": {
    "offset": "gjJl2xAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJib3JkZXJfcmHilbI6IltcIlZ5IixcIjlaWlhVMkkzUUdOoXcEIsMTIwNDYxNTc0NTypNDI3MF0iLCJpYXQiOjE2OTc4MjgsSkjjQsImV4cCI6MTY5NzgyOTM2NH0.5VuMfKvqexoEsKfoPFtayWBNWiKvfR7_hN6MJaaIkx8",
    "path": "/tasks?project=123456&limit=1&offset=gjJl2xAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJib3JkZXJfcmHilbI6IltcIlZ5IixcIjlaWlhVMkkzUUdOoXcEIsMTIwNDYxNTc0NTypNDI3MF0iLCJpYXQiOjE2OTc4MjgsSkjjQsImV4cCI6MTY5NzgyOTM2NH0.5VuMfKvqexoEsKfoPFtayWBNWiKvfR7_hN6MJaaIkx8",
    "uri": "https://app.asana.com/api/1.0/tasks?project=123456&limit=1&offset=gjJl2xAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJib3JkZXJfcmHilbI6IltcIlZ5IixcIjlaWlhVMkkzUUdOoXcEIsMTIwNDYxNTc0NTypNDI3MF0iLCJpYXQiOjE2OTc4MjgsSkjjQsImV4cCI6MTY5NzgyOTM2NH0.5VuMfKvqexoEsKfoPFtayWBNWiKvfR7_hN6MJaaIkx8"
  }
}
```

## Getting events

In order to get events you will need a sync token. This sync token can be acquired in the error message from the initial
request to [getEvents](docs/EventsApi.md#getEvents).

```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let eventsApiInstance = new Asana.EventsApi();
let resource = "<YOUR_TASK_OR_PROJECT_GID>"; // String | A resource ID to subscribe to. The resource can be a task or project.
let opts = {
    "sync": ""
};
const timeouts = 5000

// Used to fetch for initial sync token
const setSyncToken = async () => {
    await eventsApiInstance.getEvents(resource, opts).then((result) => {
        console.log(JSON.stringify(result.data, null, 2));
    }, (error) => {
        let syncToken = error.response.body.sync;
        opts['sync'] = syncToken;
    });
}

const getEvents = async () => {
    console.log("Setting sync token");
    await setSyncToken();
    // Fetch for new events every 5 seconds
    console.log(`Fetching events every ${timeouts/1000} second(s)`);
    while(true) {
        await eventsApiInstance.getEvents(resource, opts).then((result) => {
            // Print response
            console.log(`Fetching events since sync: ${opts['sync']}`);
            console.log(JSON.stringify(result.data, null, 2));

            // Update the sync token with the new sync token provided in the response
            opts['sync'] = result._response.sync;
        }, (error) => {
            if (error.status === 412) {
                let syncToken = error.response.body.sync;
                opts['sync'] = syncToken;
                console.log(`412 error new sync token: ${syncToken}`);
            } else{
                console.error(error.response.text);
            }
        });
        await new Promise(resolve => setTimeout(resolve, timeouts));
    }
}

getEvents();
```

## Accessing repsonse data

```javascript
.
.
.
tasksApiInstance.getTask(task_gid, opts).then((task) => {
    let taskName = task.data.name;
    let taskNotes = task.data.notes;
    console.log(`taskName: ${taskName}`);
    console.log(`taskNotes: ${taskNotes}`);
}, (error) => {
    console.error(error.response.body);
});
```

## Accessing response status code and headers
Use the `<METHOD_NAME>WithHttpInfo` (EX: `getTaskWithHttpInfo`) method to make a request that returns a response with headers.

```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

let tasksApiInstance = new Asana.TasksApi();
let task_gid = "<YOUR_TASK_GID>";
let opts = {};

tasksApiInstance.getTaskWithHttpInfo(task_gid, opts).then((response_and_data) => {
    let data = response_and_data.data;
    let response = response_and_data.response;
    let task = data.data;
    let headers = response.headers;
    console.log(task);
    console.log(headers);
}, (error) => {
    console.error(error.response.body);
});
```

## Adding deprecation flag: "asana-enable" or "asana-disable" header

EX: Asana-Enable header
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// Add asana-enable header for the client
client.defaultHeaders['asana-enable'] = 'new_goal_memberships';
```

EX: Asana-Disable header
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// Add asana-disable header for the client
client.defaultHeaders['asana-disable'] = 'new_goal_memberships';
```

## Using the `callApi` method

Use the `callApi` method to make http calls when the endpoint does not exist in the current library version or has bugs

### Example: GET, POST, PUT, DELETE on tasks

#### GET - get a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// GET - get a task
client.callApi(
    path='/tasks/{task_gid}',
    httpMethod='GET',
    pathParams={"task_gid": "<YOUR_TASK_GID>"},
    queryParams={},
    headerParams={},
    formParams={},
    bodyParam=null,
    authNames=['token'],
    contentTypes=[],
    accepts=['application/json; charset=UTF-8'],
    returnType='Blob'
).then((response_and_data) => {
    let result = response_and_data.data;
    let task = result.data;
    console.log(task.name);
}, (error) => {
    console.error(error.response.body);
});
```

#### GET - get multiple tasks -> with opt_fields
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// GET - get multiple tasks -> with opt_fields
client.callApi(
    path='/tasks',
    httpMethod='GET',
    pathParams={},
    queryParams={
        "limit": 50,
        "modified_since": '2012-02-22T02:06:58.158Z', // OR new Date("2012-02-22T02:06:58.158Z")
        "project": '<YOUR_PROJECT_GID>',
        "opt_fields": 'name,notes'
    },
    headerParams={},
    formParams={},
    bodyParam=null,
    authNames=['token'],
    contentTypes=[],
    accepts=['application/json; charset=UTF-8'],
    returnType='Blob'
).then((response_and_data) => {
    let result = response_and_data.data;
    let tasks = result.data;
    if (tasks.length > 0) {
        console.log(`Task 1 Name: ${tasks[0].name}`);
        console.log(`Task 1 Notes: ${tasks[0].notes}`);
    }
}, (error) => {
    console.error(error.response.body);
});
```

#### POST - create a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// POST - create a task
client.callApi(
    path='/tasks',
    httpMethod='POST',
    pathParams={},
    queryParams={},
    headerParams={},
    formParams={},
    bodyParam={
        data: {
            "name": "New Task",
            "approval_status": "pending",
            "assignee_status": "upcoming",
            "completed": false,
            "html_notes": "<body>Mittens <em>really</em> likes the stuff from Humboldt.</body>",
            "is_rendered_as_separator": false,
            "liked": true,
            "assignee": "me",
            "projects": ["<YOUR_PROJECT_GID>"],
        }
    },
    authNames=['token'],
    contentTypes=[],
    accepts=['application/json; charset=UTF-8'],
    returnType='Blob'
).then((response_and_data) => {
    let result = response_and_data.data;
    let task = result.data;
    console.log(task.name);
}, (error) => {
    console.error(error.response.body);
});
```

#### PUT - update a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// PUT - update a task
client.callApi(
    path='/tasks/{task_gid}',
    httpMethod='PUT',
    pathParams={task_gid: "<YOUR_TASK_GID>"},
    queryParams={},
    headerParams={},
    formParams={},
    bodyParam={
        "data": {
            "name": "Updated Task",
            "html_notes": "<body>Updated task notes</body>",
            "due_at": "2025-01-20T02:06:58.147Z"
        }
    },
    authNames=['token'],
    contentTypes=[],
    accepts=['application/json; charset=UTF-8'],
    returnType='Blob'
).then((response_and_data) => {
    let result = response_and_data.data;
    let task = result.data;
    console.log(task.name);
}, (error) => {
    console.error(error.response.body);
});
```

#### DELETE - delete a task
```javascript
const Asana = require('asana');

let client = Asana.ApiClient.instance;
let token = client.authentications['token'];
token.accessToken = '<YOUR_ACCESS_TOKEN>';

// DELETE - delete a task
client.callApi(
    path='/tasks/{task_gid}',
    httpMethod='DELETE',
    pathParams={"task_gid": "<YOUR_TASK_GID>"},
    queryParams={},
    headerParams={},
    formParams={},
    bodyParam=null,
    authNames=['token'],
    contentTypes=[],
    accepts=['application/json; charset=UTF-8'],
    returnType='Blob'
).then((response_and_data) => {
    let result = response_and_data.data;
    let result = result.data;
    console.log(result);
}, (error) => {
    console.error(error.response.body);
});
```

[release-image]: https://img.shields.io/github/release/asana/node-asana.svg
[release-url]: https://github.com/Asana/node-asana/releases/tag/v3.0.16
[npm-image]: http://img.shields.io/npm/v/asana.svg?style=flat-square
[npm-url]: https://www.npmjs.org/package/asana
