import AutoConsent from "../lib/web";
import { BackgroundMessage } from "../lib/messages";
import { MessageSender, RuleBundle } from "../lib/types";
import * as rules from '../rules/rules.json';

declare global {
  interface Window {
    autoconsentSendMessage: MessageSender;
    autoconsentReceiveMessage: (message: BackgroundMessage) => Promise<void>;
  }
}

if (!window.autoconsentReceiveMessage) {
  const consent = new AutoConsent(window.autoconsentSendMessage, null, <RuleBundle>rules);

  window.autoconsentReceiveMessage = (message: BackgroundMessage) => {
    return Promise.resolve(consent.receiveMessageCallback(message));
  };
} else {
  console.warn('autoconsent already initialized', window.autoconsentReceiveMessage);
}