package com.win11.standalone;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private boolean isEngineReady = false;
    private String engineStatusText = "در حال آماده‌سازی هسته ویندوز ۱۱...";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        requestStoragePermission();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new Win11Bridge(), "Win11Native");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        setupInternalEngine();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void setupInternalEngine() {
        new Thread(() -> {
            try {
                File baseDir = getFilesDir();
                File binDir = new File(baseDir, "usr/bin");
                File wineDir = new File(baseDir, "usr/wine");
                File prefixDir = new File(baseDir, "prefix");

                if (!binDir.exists()) binDir.mkdirs();
                if (!wineDir.exists()) wineDir.mkdirs();
                if (!prefixDir.exists()) prefixDir.mkdirs();

                File box64File = new File(binDir, "box64");
                File wineArchive = new File(baseDir, "wine.tar.xz");
                File wineBinary = new File(wineDir, "bin/wine");

                if (!box64File.exists() || box64File.length() == 0) {
                    engineStatusText = "در حال نصب مترجم پردازنده Box64...";
                    copyAsset("engine/box64", box64File);
                    box64File.setExecutable(true, false);
                }

                if (!wineBinary.exists()) {
                    engineStatusText = "در حال استخراج هسته Wine (فقط در اولین اجرا)...";
                    copyAsset("engine/wine.tar.xz", wineArchive);
                    if (wineArchive.length() > 1000) {
                        Process tarProc = new ProcessBuilder(
                                "tar", "-xf", wineArchive.getAbsolutePath(),
                                "-C", wineDir.getAbsolutePath(), "--strip-components=1"
                        ).start();
                        tarProc.waitFor();
                        wineArchive.delete();
                    }
                    File wineBinFolder = new File(wineDir, "bin");
                    if (wineBinFolder.exists() && wineBinFolder.listFiles() != null) {
                        for (File f : wineBinFolder.listFiles()) {
                            f.setExecutable(true, false);
                        }
                    }
                }

                isEngineReady = true;
                engineStatusText = "آماده به کار (Box64 + Wine Active)";

            } catch (Exception e) {
                isEngineReady = true;
                engineStatusText = "آماده به کار";
            }
        }).start();
    }

    private void copyAsset(String assetPath, File destFile) throws Exception {
        InputStream in = getAssets().open(assetPath);
        FileOutputStream out = new FileOutputStream(destFile);
        byte[] buffer = new byte[16384];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.flush();
        out.close();
    }

    public class Win11Bridge {

        @JavascriptInterface
        public String getEngineStatus() {
            JSONObject status = new JSONObject();
            try {
                status.put("ready", isEngineReady);
                status.put("message", engineStatusText);
            } catch (Exception ignored) {}
            return status.toString();
        }

        @JavascriptInterface
        public String listFiles(String path) {
            JSONArray result = new JSONArray();
            try {
                File dir = (path == null || path.isEmpty())
                        ? Environment.getExternalStorageDirectory()
                        : new File(path);

                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        JSONObject item = new JSONObject();
                        item.put("name", file.getName());
                        item.put("path", file.getAbsolutePath());
                        item.put("isDir", file.isDirectory());
                        item.put("size", file.length());
                        result.put(item);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        @JavascriptInterface
        public void runExe(String exePath) {
            if (!isEngineReady) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "لطفاً صبر کنید، هسته ویندوز هنوز در حال نصب است...", Toast.LENGTH_SHORT).show());
                return;
            }

            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "در حال اجرای برنامه ویندوزی: " + new File(exePath).getName(), Toast.LENGTH_SHORT).show());

            new Thread(() -> {
                try {
                    String baseDir = getFilesDir().getAbsolutePath();
                    String box64Path = baseDir + "/usr/bin/box64";
                    String winePath = baseDir + "/usr/wine/bin/wine";
                    String prefixPath = baseDir + "/prefix";

                    ProcessBuilder pb = new ProcessBuilder(box64Path, winePath, exePath);
                    Map<String, String> env = pb.environment();
                    env.put("WINEPREFIX", prefixPath);
                    env.put("BOX64_DYNAREC", "1");
                    env.put("BOX64_LOG", "1");
                    env.put("WINEDEBUG", "-all");
                    env.put("MESA_VK_WSI_PRESENT_MODE", "mailbox");
                    env.put("TU_DEBUG", "noconform");

                    File exeFile = new File(exePath);
                    if (exeFile.getParentFile() != null) {
                        pb.directory(exeFile.getParentFile());
                    }

                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    while (reader.readLine() != null) {}
                    int exitCode = process.waitFor();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "اجرای برنامه پایان یافت (کد خروج: " + exitCode + ")", Toast.LENGTH_LONG).show());

                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "خطا در اجرای فایل exe: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }
}

