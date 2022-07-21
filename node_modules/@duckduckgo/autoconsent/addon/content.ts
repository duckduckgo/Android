import { BackgroundMessage } from "../lib/messages";
import AutoConsent from "../lib/web";

const consent = new AutoConsent(chrome.runtime.sendMessage);
chrome.runtime.onMessage.addListener((message: BackgroundMessage) => {
  return Promise.resolve(consent.receiveMessageCallback(message));
});
