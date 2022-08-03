import Tools from "./tools";

export function matches(config: any) {
  const result = Tools.find(config);
  if (config.type === "css") {
    return !!result.target;
  } else if (config.type === "checkbox") {
    return !!result.target && result.target.checked;
  }
}

export async function executeAction(config: any, param?: any): Promise<boolean | void> {
  switch (config.type) {
    case "click":
      return clickAction(config);
    case "list":
      return listAction(config, param);
    case "consent":
      return consentAction(config, param);
    case "ifcss":
      return ifCssAction(config, param);
    case "waitcss":
      return waitCssAction(config);
    case "foreach":
      return forEachAction(config, param);
    case "hide":
      return hideAction(config);
    case "slide":
      return slideAction(config);
    case "close":
      return closeAction(config);
    case "wait":
      return waitAction(config);
    case "eval":
      return evalAction(config);
    default:
      throw "Unknown action type: " + config.type;
  }
}

const STEP_TIMEOUT = 0;

function waitTimeout(timeout: number): Promise<void> {
  return new Promise(resolve => {
    setTimeout(() => {
      resolve();
    }, timeout);
  });
}

async function clickAction(config: any) {
  const result = Tools.find(config);
  if (result.target != null) {
    result.target.click();
  }
  return waitTimeout(STEP_TIMEOUT);
}

async function listAction(config: any, param: any) {
  for (let action of config.actions) {
    await executeAction(action, param);
  }
}

async function consentAction(config: any, consentTypes: any) {
  for (const consentConfig of config.consents) {
    const shouldEnable = consentTypes.indexOf(consentConfig.type) !== -1;
    if (consentConfig.matcher && consentConfig.toggleAction) {
      const isEnabled = matches(consentConfig.matcher);
      if (isEnabled !== shouldEnable) {
        await executeAction(consentConfig.toggleAction);
      }
    } else {
      if (shouldEnable) {
        await executeAction(consentConfig.trueAction);
      } else {
        await executeAction(consentConfig.falseAction);
      }
    }
  }
}

async function ifCssAction(config: any, param: any) {
  const result = Tools.find(config);
  if (!result.target) {
    if (config.trueAction) {
      await executeAction(config.trueAction, param);
    }
  } else {
    if (config.falseAction) {
      await executeAction(config.falseAction, param);
    }
  }
}

async function waitCssAction(config: any) {
  await new Promise<void>(resolve => {
    let numRetries = config.retries || 10;
    const waitTime = config.waitTime || 250;
    const checkCss = () => {
      const result = Tools.find(config);
      if (
        (config.negated && result.target) ||
        (!config.negated && !result.target)
      ) {
        if (numRetries > 0) {
          numRetries -= 1;
          setTimeout(checkCss, waitTime);
        } else {
          resolve();
        }
      } else {
        resolve();
      }
    };
    checkCss();
  });
}

async function forEachAction(config: any, param: any) {
  const results = Tools.find(config, true);
  const oldBase = Tools.base;
  for (const result of results) {
    if (result.target) {
      Tools.setBase(result.target);
      await executeAction(config.action, param);
    }
  }
  Tools.setBase(oldBase);
}

async function hideAction(config: any) {
  const result = Tools.find(config);
  if (result.target) {
    result.target.classList.add("Autoconsent-Hidden");
    // result.target.setAttribute("style", "display: none;");
  }
}

async function slideAction(config: any) {
  const result = Tools.find(config);
  const dragResult = Tools.find(config.dragTarget);
  if (result.target) {
    let targetBounds = result.target.getBoundingClientRect();
    let dragTargetBounds = dragResult.target.getBoundingClientRect();

    let yDiff = dragTargetBounds.top - targetBounds.top;
    let xDiff = dragTargetBounds.left - targetBounds.left;

    if (this.config.axis.toLowerCase() === "y") {
      xDiff = 0;
    }
    if (this.config.axis.toLowerCase() === "x") {
      yDiff = 0;
    }

    let screenX = window.screenX + targetBounds.left + targetBounds.width / 2.0;
    let screenY = window.screenY + targetBounds.top + targetBounds.height / 2.0;
    let clientX = targetBounds.left + targetBounds.width / 2.0;
    let clientY = targetBounds.top + targetBounds.height / 2.0;

    let mouseDown = document.createEvent("MouseEvents");
    mouseDown.initMouseEvent(
      "mousedown",
      true,
      true,
      window,
      0,
      screenX,
      screenY,
      clientX,
      clientY,
      false,
      false,
      false,
      false,
      0,
      result.target
    );
    let mouseMove = document.createEvent("MouseEvents");
    mouseMove.initMouseEvent(
      "mousemove",
      true,
      true,
      window,
      0,
      screenX + xDiff,
      screenY + yDiff,
      clientX + xDiff,
      clientY + yDiff,
      false,
      false,
      false,
      false,
      0,
      result.target
    );
    let mouseUp = document.createEvent("MouseEvents");
    mouseUp.initMouseEvent(
      "mouseup",
      true,
      true,
      window,
      0,
      screenX + xDiff,
      screenY + yDiff,
      clientX + xDiff,
      clientY + yDiff,
      false,
      false,
      false,
      false,
      0,
      result.target
    );
    result.target.dispatchEvent(mouseDown);
    await this.waitTimeout(10);
    result.target.dispatchEvent(mouseMove);
    await this.waitTimeout(10);
    result.target.dispatchEvent(mouseUp);
  }
}

async function waitAction(config: any) {
  await waitTimeout(config.waitTime);
}

async function closeAction(config: any) {
  window.close();
}

async function evalAction(config: any): Promise<boolean> {
  console.log("eval!", config.code);
  return new Promise(resolve => {
    try {
      if (config.async) {
        window.eval(config.code);
        setTimeout(() => {
          resolve(window.eval("window.__consentCheckResult"));
        }, config.timeout || 250);
      } else {
        resolve(window.eval(config.code));
      }
    } catch (e) {
      console.warn("eval error", e, config.code);
      resolve(false);
    }
  });
}
