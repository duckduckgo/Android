import fs from "fs";
import path from 'path';
import { test, expect, Page, Frame } from "@playwright/test";
import { waitFor } from "../lib/utils";
import { ContentScriptMessage } from "../lib/messages";
import { enableLogs } from "../lib/config";

const testRegion = (process.env.REGION || "NA").trim();

type TestOptions = {
  testOptOut: boolean;
  testSelfTest: boolean;
  skipRegions?: string[];
  onlyRegions?: string[];
};
const defaultOptions: TestOptions = {
  testOptOut: true,
  testSelfTest: true,
  skipRegions: [],
  onlyRegions: [],
};

const contentScript = fs.readFileSync(
  path.join(__dirname, "../dist/autoconsent.playwright.js"),
  "utf8"
);

export async function injectContentScript(page: Page | Frame) {
  try {
    await page.evaluate(contentScript);
  } catch (e) {
    // frame was detached
    // console.log(e);
  }
}

export function generateTest(
  url: string,
  expectedCmp: string,
  options: TestOptions = { testOptOut: true, testSelfTest: true }
) {
  test(`${url.split("://")[1]} .${testRegion}`, async ({ page }) => {
    if (options.onlyRegions && options.onlyRegions.length > 0 && !options.onlyRegions.includes(testRegion)) {
      test.skip();
    }
    if (options.skipRegions && options.skipRegions.includes(testRegion)) {
      test.skip();
    }
    enableLogs && page.on('console', async msg => {
      console.log(`    page log:`, msg.text());
    });
    await page.exposeBinding("autoconsentSendMessage", messageCallback);
    await page.goto(url, { waitUntil: "commit" });

    // set up a messaging function
    const received: ContentScriptMessage[] = [];

    function isMessageReceived(msg: Partial<ContentScriptMessage>, partial = true) {
      return received.some((m) => {
        const keysMatch = partial || Object.keys(m).length === Object.keys(msg).length;
        return keysMatch && Object.keys(msg).every(
          (k) => (<any>m)[k] === (<any>msg)[k]
        );
      });
    }

    let selfTestFrame: Frame = null;
    async function messageCallback({ frame }: { frame: Frame }, msg: ContentScriptMessage) {
      enableLogs && msg.type !== 'eval' && console.log(msg);
      received.push(msg);
      switch (msg.type) {
        case 'optInResult':
        case 'optOutResult': {
          if (msg.scheduleSelfTest) {
            selfTestFrame = frame;
          }
          break;
        }
        case 'autoconsentDone': {
          if (selfTestFrame && options.testSelfTest) {
            await selfTestFrame.evaluate(`autoconsentReceiveMessage({ type: "selfTest" })`);
          }
          break;
        }
        case 'eval': {
          const result = await frame.evaluate(msg.code);
          await frame.evaluate(`autoconsentReceiveMessage({ id: "${msg.id}", type: "evalResp", result: ${JSON.stringify(result)} })`);
          break;
        }
        case 'autoconsentError': {
          console.error(url, msg.details);
          break;
        }
      }
    }

    // inject content scripts into every frame
    await injectContentScript(page);
    page.frames().forEach(injectContentScript);
    page.on("framenavigated", injectContentScript);

    // wait for all messages and assertions
    await waitFor(() => isMessageReceived({ type: "popupFound", cmp: expectedCmp }), 50, 500);
    expect(isMessageReceived({ type: "popupFound", cmp: expectedCmp })).toBe(true);

    if (options.testOptOut) {
      await waitFor(() => isMessageReceived({ type: "optOutResult", result: true }), 50, 300);
      expect(isMessageReceived({ type: "optOutResult", result: true })).toBe(true);
    }
    if (options.testSelfTest && selfTestFrame) {
      await waitFor(() => isMessageReceived({ type: "selfTestResult", result: true }), 50, 300);
      expect(isMessageReceived({ type: "selfTestResult", result: true })).toBe(true);
    }
    await waitFor(() => isMessageReceived({ type: "autoconsentDone" }), 10, 500);
    expect(isMessageReceived({ type: "autoconsentDone" })).toBe(true);

    expect(isMessageReceived({ type: "autoconsentError" })).toBe(false);
  });
}

export default function generateCMPTests(
  cmp: string,
  sites: string[],
  options: Partial<TestOptions> = {}
) {
  test.describe(cmp, () => {
    sites.forEach((url) => {
      generateTest(url, cmp, Object.assign({}, defaultOptions, options));
    });
  });
}
