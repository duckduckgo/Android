import { enableLogs } from "../lib/config";
import { BackgroundMessage, ContentScriptMessage } from "../lib/messages";
import { Config, RuleBundle } from "../lib/types";

const autoconsentConfig: Config = {
  enabled: true,
  autoAction: 'optOut', // if falsy, the extension will wait for an explicit user signal before opting in/out
  disabledCmps: [],
  enablePrehide: true,
  detectRetries: 20,
};

async function loadRules(): Promise<RuleBundle> {
  const res = await fetch("./rules.json");
  return await res.json();
}

chrome.runtime.onInstalled.addListener(() => {
  loadRules().then(rules => {
    chrome.storage.local.set({
      rules: rules,
    });
  });
});

function showOptOutStatus(
  tabId: number,
  status: "success" | "complete" | "working" | "available" | "verified" | "idle"
) {
  let title = "";
  let icon = "icons/cookie-idle.png";
  if (status === "success") {
    title = "Opt out successful!";
    icon = "icons/party.png";
  } else if (status === "complete") {
    title = "Opt out complete!";
    icon = "icons/tick.png";
  } else if (status === "working") {
    title = "Processing...";
    icon = "icons/cog.png";
  } else if (status === "verified") {
    title = "Verified";
    icon = "icons/verified.png";
  } else if (status === "idle") {
    title = "Idle";
    icon = "icons/cookie-idle.png";
  } else if (status === "available") {
    title = "Click to opt out";
    icon = "icons/cookie.png";
  }
  enableLogs && console.log('Setting action state to', status);
  chrome.action.setTitle({
    tabId,
    title,
  });
  chrome.action.setIcon({
    tabId,
    path: icon,
  });
}

chrome.runtime.onMessage.addListener(
  async (msg: ContentScriptMessage, sender: any) => {
    const tabId = sender.tab.id;
    const frameId = sender.frameId;
    if (enableLogs) {
      console.groupCollapsed(`${msg.type} from ${sender.origin}`);
      console.log(msg, sender);
      console.groupEnd();
    }
    const rules: RuleBundle = (await chrome.storage.local.get("rules")).rules;

    switch (msg.type) {
      case "init":
        if (frameId === 0) {
          showOptOutStatus(tabId, 'idle');
        }
        chrome.tabs.sendMessage(tabId, {
          type: "initResp",
          rules,
          config: autoconsentConfig,
        } as BackgroundMessage, {
          frameId,
        });
        break;
      case "eval":
        chrome.scripting.executeScript({
          target: {
            tabId,
            frameIds: [frameId],
          },
          world: "MAIN",
          args: [msg.code],
          func: (code) => {
            try {
              return window.eval(code);
            } catch (e) {
              // ignore CSP errors
              return;
            }
          },
        }).then(([result]) => {
          if (enableLogs) {
            console.groupCollapsed(`eval result for ${sender.origin}`);
            console.log(msg.code, result.result);
            console.groupEnd();
          }
          chrome.tabs.sendMessage(tabId, {
            id: msg.id,
            type: "evalResp",
            result: result.result,
          } as BackgroundMessage, {
            frameId,
          });
        });
        break;
      case "popupFound":
        showOptOutStatus(tabId, "available");
        chrome.storage.local.set({
          [`detected${tabId}`]: frameId,
        });
        break;
      case "optOutResult":
      case "optInResult":
        if (msg.result) {
          showOptOutStatus(tabId, "working");
          if (msg.scheduleSelfTest) {
            await chrome.storage.local.set({
              [`selfTest${tabId}`]: frameId,
            });
          }
        }
        break;
      case "selfTestResult":
        if (msg.result) {
          showOptOutStatus(tabId, "verified");
        }
        break;
      case "autoconsentDone": {
        showOptOutStatus(tabId, "success");
        // sometimes self-test needs to be done in another frame
        const selfTestKey = `selfTest${tabId}`;
        const selfTestFrameId = (await chrome.storage.local.get(selfTestKey))?.[selfTestKey];

        if (typeof selfTestFrameId === 'number') {
          enableLogs && console.log(`Requesting self-test in ${selfTestFrameId}`);
          chrome.storage.local.remove(selfTestKey);
          chrome.tabs.sendMessage(tabId, {
            type: "selfTest",
          }, {
            frameId: selfTestFrameId,
          });
        } else {
          enableLogs && console.log(`No self-test scheduled`);
        }
        break;
      }
      case "autoconsentError":
        console.error('Error:', msg.details);
        break;
    }
  }
);

chrome.action.onClicked.addListener(async (tab) => {
  const tabId = tab.id;
  const detectedKey = `detected${tabId}`;
  const frameId = (await chrome.storage.local.get(detectedKey))?.[detectedKey];
  if (typeof frameId === 'number') {
    chrome.storage.local.remove(detectedKey);
    enableLogs && console.log("action.onClicked", tabId, frameId);
    showOptOutStatus(tabId, "working");
    chrome.tabs.sendMessage(tabId, {
      type: "optOut",
    } as BackgroundMessage, {
      frameId,
    });
  }
});
