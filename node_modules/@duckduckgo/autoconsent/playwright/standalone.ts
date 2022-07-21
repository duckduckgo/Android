import AutoConsent from "../lib/web";
import { BackgroundMessage } from "../lib/messages";
import { Config, RuleBundle } from "../lib/types";
import * as rules from '../rules/rules.json';

declare global {
  interface Window {
    initAutoconsentStandalone: () => void;
    autoconsentStandaloneSendMessage: (msg: string) => void;
    autoconsentStandaloneReceiveMessage: (message: BackgroundMessage) => void;
  }
}


window.initAutoconsentStandalone = (config: Config = {
  enabled: true,
  autoAction: 'optOut',
  disabledCmps: [],
  enablePrehide: true,
  detectRetries: 20,
}) => {
  if (!window.autoconsentStandaloneReceiveMessage) {
    const autoconsent = new AutoConsent(
      async message => {
          window.autoconsentStandaloneSendMessage(JSON.stringify(message));
      },
      config,
      <RuleBundle>rules
    );
    window.autoconsentStandaloneReceiveMessage = (msg) => {
      autoconsent.receiveMessageCallback(msg);
    }
  } else {
    console.warn('autoconsent already initialized', window.autoconsentStandaloneReceiveMessage);
  }
}
