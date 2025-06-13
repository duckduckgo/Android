// NOTE: Because these tests are making live API calls, we decided to write these tests in a particular order to optimize speed and reduce API calls.
// The down side to this approach is that these tests are tightly coupled.
(function (root, factory) {
    if (typeof define === "function" && define.amd) {
        // AMD.
        define(["expect.js", "../../src/index"], factory);
    } else if (typeof module === "object" && module.exports) {
        // CommonJS-like environments that support module.exports, like Node.
        factory(require("expect.js"), require("../../src/index"));
    } else {
        // Browser globals (root is window)
        factory(root.expect, root.Asana);
    }
})(this, function (expect, Asana) {
    "use strict";

    var TEXT_CUSTOM_FIELD_GID;
    var USER_GID;
    var WORKSPACE_GID;

    var tasksApiInstance;
    var task;
    var tasks;

    before(async function () {
        require('dotenv').config()
        TEXT_CUSTOM_FIELD_GID = process.env.TEXT_CUSTOM_FIELD_GID;
        USER_GID = process.env.USER_GID;
        WORKSPACE_GID = process.env.WORKSPACE_GID;

        let client = Asana.ApiClient.instance;
        let token = client.authentications["token"];
        token.accessToken = process.env.PERSONAL_ACCESS_TOKEN;
        tasksApiInstance = new Asana.TasksApi();
    });

    describe("TasksApi", function () {
        describe("createTask", function () {
            it("should have created task", async function () {
                let body = {
                    data: {
                        assignee: USER_GID,
                        name: "Task 1",
                        notes: "Some description",
                        workspace: WORKSPACE_GID,
                    },
                };
                try {
                    task = await tasksApiInstance.createTask(body, {});
                } catch (error) {
                    throw error.response.body;
                }
                expect(task).to.be.ok();
            });
            it("should have a `name`", function () {
                expect(task.data.name).to.be("Task 1");
            });
            it("should have a description/`notes`", function () {
                expect(task.data.notes).to.be("Some description");
            });
        });
        describe("updateTask", function () {
            it("should have updated task", async function () {
                let body = {
                    data: {
                        name: "Task 1 - Updated",
                        notes: "Updated description",
                    },
                };
                try {
                    task = await tasksApiInstance.updateTask(
                        body,
                        task.data.gid,
                        {}
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(task).to.be.ok();
            });
            it("should have updated task `name`", function () {
                expect(task.data.name).to.be("Task 1 - Updated");
            });
            it("should have updated task description/`notes`", function () {
                expect(task.data.notes).to.be("Updated description");
            });
        });
        describe("getTask", function () {
            it("should return task", async function () {
                let response;
                try {
                    response = await tasksApiInstance.getTask(
                        task.data.gid,
                        {}
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(response).to.ok();
            });
        });
        describe("getTask with `opt_fields`", function () {
            describe("task for `html_notes` and `due_on` properties in `opt_fields`", function () {
                it("should return task", async function () {
                    let opts = {
                        opt_fields: "html_notes,due_on",
                    };
                    try {
                        task = await tasksApiInstance.getTask(
                            task.data.gid,
                            opts
                        );
                    } catch (error) {
                        throw error.response.body;
                    }
                    expect(task).to.be.ok();
                });
                it("should have `html_notes`", function () {
                    expect(task.data.html_notes).not.to.equal(null);
                });
                it("should have `due_on` with `null` value", function () {
                    expect(task.data.due_on).to.be(null);
                });
            });
        });
        describe("getTasks", function () {
            it("should return an array of tasks", async function () {
                let tasksApiInstance = new Asana.TasksApi();
                let opts = {
                    limit: 100,
                    assignee: USER_GID,
                    workspace: WORKSPACE_GID,
                };
                try {
                    tasks = await tasksApiInstance.getTasks(opts);
                } catch (error) {
                    throw error.response.body;
                }
                expect(tasks).to.be.ok();
                expect(tasks.data).to.be.an("array");
            });
        });
        describe("getTasks with `opt_fields`", function () {
            describe("ask for `workspace` and `due_on` properties in `opt_fields`", function () {
                it("should return an array of tasks", async function () {
                    let tasksApiInstance = new Asana.TasksApi();
                    let opts = {
                        assignee: USER_GID,
                        workspace: WORKSPACE_GID,
                        opt_fields: "workspace,due_on",
                    };

                    try {
                        tasks = await tasksApiInstance.getTasks(opts);
                    } catch (error) {
                        throw error.response.body;
                    }
                    expect(tasks).to.be.ok();
                    expect(tasks.data).to.be.an("array");
                });
                it("should have `workspace`", function () {
                    expect(tasks.data[0].workspace).not.to.equal(null);
                });
                it("should have `due_on`", function () {
                    expect("due_on" in tasks.data[0]).to.be(true);
                });
            });
        });
        describe("getTasks limit 1", function () {
            it("should return an array with 1 task", async function () {
                let tasksApiInstance = new Asana.TasksApi();
                let opts = {
                    limit: 1,
                    assignee: USER_GID,
                    workspace: WORKSPACE_GID,
                };

                try {
                    tasks = await tasksApiInstance.getTasks(opts);
                } catch (error) {
                    throw error.response.body;
                }
                expect(tasks.data.length).to.be(1);
                expect(tasks.data).to.be.an("array");
            });
        });
        describe("searchTasksForWorkspace", function () {
            it("should return an array of tasks", async function () {
                let opts = {
                    text: "Task",
                    completed: false,
                };

                try {
                    tasks = await tasksApiInstance.searchTasksForWorkspace(
                        WORKSPACE_GID,
                        opts
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(tasks).to.be.ok();
                expect(tasks.data).to.be.an("array");
            });
        });
        describe("searchTasksForWorkspace with custom field parameter - MATCH", function () {
            it("should return an array with one task that has matching custom field value from search query", async function () {
                let opts = {
                    limit: 1,
                    [`custom_fields.${TEXT_CUSTOM_FIELD_GID}.value`]:
                        "custom_value",
                };

                try {
                    tasks = await tasksApiInstance.searchTasksForWorkspace(
                        WORKSPACE_GID,
                        opts
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(tasks).to.be.ok();
                expect(tasks.data.length).to.be(1);
            });
        });
        describe("searchTasksForWorkspace with custom field parameter - NO MATCH", function () {
            it("should return an empty array", async function () {
                let opts = {
                    [`custom_fields.${TEXT_CUSTOM_FIELD_GID}.value`]:
                        "inimw8I23M4FRTPfApu1",
                };

                try {
                    tasks = await tasksApiInstance.searchTasksForWorkspace(
                        WORKSPACE_GID,
                        opts
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(tasks.data).to.be.empty();
            });
        });
        describe("deleteTask", function () {
            it("should be deleted", async function () {
                let response;
                try {
                    // NOTE: In the above tasks we are setting the global task variable in different tests. Make sure
                    // that future tests don't overwrite the original task created with another task otherwise
                    // that original task won't be deleted
                    response = await tasksApiInstance.deleteTask(task.data.gid);
                } catch (error) {
                    throw error.response.body;
                }
                expect(response).to.be.ok();
                expect(response.data).to.be.empty();
            });
        });
    });
});
