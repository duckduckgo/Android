package dev.verifyandroid.clipboardbridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class BridgeActivity extends Activity {
    private int retries;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().postDelayed(this::transfer, 300);
    }

    private void transfer() {
        if (!getWindow().getDecorView().hasWindowFocus()) {
            if (retries++ < 30) {
                getWindow().getDecorView().postDelayed(this::transfer, 100);
                return;
            }
            complete("error");
            return;
        }
        File file = new File(getFilesDir(), "value");
        String command = getIntent().getStringExtra("command");
        String status = "ok";
        try {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if ("read".equals(command)) {
                ClipData data = clipboard.getPrimaryClip();
                String text = "";
                if (data != null && data.getItemCount() > 0) {
                    if (data.getItemCount() != 1 || data.getItemAt(0).getText() == null) {
                        throw new IllegalArgumentException();
                    }
                    text = data.getItemAt(0).getText().toString();
                }
                Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
            } else if ("write".equals(command)) {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                ClipData data = ClipData.newPlainText("Verification clipboard", text);
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                data.getDescription().setExtras(extras);
                clipboard.setPrimaryClip(data);
            } else if ("clear".equals(command)) {
                if (Build.VERSION.SDK_INT >= 28) {
                    clipboard.clearPrimaryClip();
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception failure) {
            file.delete();
            status = "error";
        } finally {
            if (!"read".equals(command)) {
                file.delete();
            }
        }
        complete(status);
    }

    private void complete(String status) {
        try {
            Files.write(new File(getFilesDir(), "status").toPath(), status.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        finish();
    }
}
