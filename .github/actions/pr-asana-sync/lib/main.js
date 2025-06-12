"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
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
const core = __importStar(require("@actions/core"));
const github = __importStar(require("@actions/github"));
function run() {
    var _a;
    return __awaiter(this, void 0, void 0, function* () {
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
                core.setFailed(`This action is intended to run only on pull_request or pull_request_target events, not on ${context.eventName}.`);
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
            const { data: pullRequest } = yield octokit.rest.pulls.get({
                owner,
                repo,
                pull_number: prNumber,
            });
            core.info(`PR Title: ${pullRequest.title}`);
            core.info(`PR Body: ${pullRequest.body || '(No body)'}`);
            core.info(`PR Author: ${(_a = pullRequest.user) === null || _a === void 0 ? void 0 : _a.login}`);
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
        }
        catch (error) {
            if (error instanceof Error) {
                core.setFailed(error.message);
            }
            else {
                core.setFailed('An unknown error occurred');
            }
        }
    });
}
// Run the action
run();
