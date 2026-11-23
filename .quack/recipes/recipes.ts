import quack from "quack";

const setup = quack.recipe({
  name: "app.setup",
  description: "Download the privacy config, complete onboarding and grant the notification permission",
  run() {
    // A freshly installed app sits in the NEVER standby bucket, which has no network in the background.
    quack.shell(`am set-standby-bucket ${quack.app.package} active`);
    quack.waitUntil(
      () => {
        try {
          quack.runAction("privacy_config.download");
          return true;
        } catch {
          return false;
        }
      },
      { timeout: "15s", every: "1s" },
    );
    quack.runAction("onboarding.complete");
    // POST_NOTIFICATIONS is a runtime permission only from Android 13 (API 33), and pm grant fails below it.
    if (Number(quack.shell("getprop ro.build.version.sdk").trim()) >= 33) {
      quack.shell(`pm grant ${quack.app.package} android.permission.POST_NOTIFICATIONS`);
    }
    quack.openApp();
  },
});

quack.recipe({
  name: "app.reset",
  description: "Delete all app data, then set the app up",
  run() {
    quack.shell(`pm clear ${quack.app.package}`);
    quack.runRecipe(setup);
  },
});

quack.recipe({
  name: "sync.demo",
  description: "Back up this device to sync, then pair it using code",
  params: {
    code: { type: "string", description: "Pairing code", required: true, env: "QUACK_SYNC_RECOVERY_CODE" },
  },
  run({ code }) {
    quack.openApp();
    quack.wait("2s");
    const screen = quack.runAction<{ screen: string | null }>("app.screen");
    if (screen.data.screen !== "SyncActivity") {
      quack.runAction("goto.sync_settings");
    }
    const status = quack.runAction<{ signedIn: boolean }>("sync.status");
    if (!status.data.signedIn) {
      quack.runAction("sync.backup");
      quack.wait("5s");
    }
    quack.runAction("sync.pair.scanner", { code });
  },
});

quack.recipe({
  name: "goto.back",
  description: "Press the back button",
  run() {
    quack.shell("input keyevent KEYCODE_BACK");
  },
});
