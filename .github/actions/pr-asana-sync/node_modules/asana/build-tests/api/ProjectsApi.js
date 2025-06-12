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

    var TEAM_GID;
    var WORKSPACE_GID;

    var projectsApiInstance;
    var project;
    var projects;

    before(async function () {
        require('dotenv').config()
        TEAM_GID = process.env.TEAM_GID;
        WORKSPACE_GID = process.env.WORKSPACE_GID;

        let client = Asana.ApiClient.instance;
        let token = client.authentications["token"];
        token.accessToken = process.env.PERSONAL_ACCESS_TOKEN;

        projectsApiInstance = new Asana.ProjectsApi();
    });

    describe("ProjectsApi", function () {
        describe("createProject", function () {
            it("should have created project", async function () {
                let body = {
                    data: {
                        name: "Project 1",
                        notes: "Some description",
                        team: TEAM_GID,
                    },
                };

                try {
                    project = await projectsApiInstance.createProject(body, {});
                } catch (error) {
                    throw error.response.body;
                }
                expect(project).to.be.ok();
            });
            it("should have a `name`", function () {
                expect(project.data.name).to.be("Project 1");
            });
            it("should have a description/`notes`", function () {
                expect(project.data.notes).to.be("Some description");
            });
        });
        describe("updateProject", function () {
            it("should have updated project", async function () {
                let body = {
                    data: {
                        name: "Project 1 - Updated",
                        notes: "Updated description",
                    },
                };

                try {
                    project = await projectsApiInstance.updateProject(
                        body,
                        project.data.gid,
                        {}
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(project).to.be.ok();
            });
            it("should have updated project `name`", function () {
                expect(project.data.name).to.be("Project 1 - Updated");
            });
            it("should have updated project description/`notes`", function () {
                expect(project.data.notes).to.be("Updated description");
            });
        });
        describe("getProject", function () {
            it("should return project", async function () {
                let response;
                try {
                    response = await projectsApiInstance.getProject(
                        project.data.gid,
                        {}
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(response).to.ok();
            });
        });
        describe("getProject with `opt_fields`", function () {
            describe("ask for `html_notes` and `due_on` properties in `opt_fields`", function () {
                it("should return project", async function () {
                    let opts = {
                        opt_fields: "html_notes,due_on",
                    };

                    try {
                        project = await projectsApiInstance.getProject(
                            project.data.gid,
                            opts
                        );
                    } catch (error) {
                        throw error.response.body;
                    }
                    expect(project).to.ok();
                });
                it("should have `html_notes`", async function () {
                    expect(project.data.html_notes).not.to.equal(null);
                });
                it("should have `due_on` with `null` value", async function () {
                    expect(project.data.due_on).to.be(null);
                });
            });
        });
        describe("getProjects", function () {
            it("should return an array of projects", async function () {
                let opts = {
                    limit: 100,
                    workspace: WORKSPACE_GID,
                };
                try {
                    projects = await projectsApiInstance.getProjects(opts);
                } catch (error) {
                    throw error.response.body;
                }
                expect(projects).to.be.ok();
                expect(projects.data).to.be.an("array");
            });
        });
        describe("getProjects with `opt_fields`", function () {
            describe("ask for `color` and `completed` properties in `opt_fields`", function () {
                it("should return an array of projects", async function () {
                    let opts = {
                        workspace: WORKSPACE_GID,
                        opt_fields: "color,completed",
                    };

                    try {
                        projects = await projectsApiInstance.getProjects(opts);
                    } catch (error) {
                        throw error.response.body;
                    }
                    expect(projects).to.be.ok();
                    expect(projects.data).to.be.an("array");
                });
                it("should have `workspace`", function () {
                    expect("color" in projects.data[0]).to.be(true);
                });
                it("should have `completed`", function () {
                    expect("completed" in projects.data[0]).to.be(true);
                });
            });
        });
        describe("deleteProject", function () {
            it("should be deleted", async function () {
                let response;
                try {
                    response = await projectsApiInstance.deleteProject(
                        project.data.gid
                    );
                } catch (error) {
                    throw error.response.body;
                }
                expect(response.data).to.be.empty();
            });
        });
    });
});
