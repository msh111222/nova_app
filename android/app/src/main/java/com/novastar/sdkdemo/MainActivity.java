package com.novastar.sdkdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

import com.sun.jna.Native;

public class MainActivity extends FlutterActivity {

    private static final String CHANNEL = "com.novastar/bridge";
    private static final String TAG = "NovaSDK";

    private ViplexCore sdkInstance;
    private volatile boolean isLoginProcessStarted = false;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        Log.e(TAG, "===== MainActivity configureFlutterEngine 被调用 =====");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, 1001);
            }
        }

        try {
            sdkInstance = Native.load("viplexcore", ViplexCore.class);
        } catch (Throwable e) {
            Log.e(TAG, "load viplexcore fail", e);
        }

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if ("initAndLogin".equals(call.method)) {
                        isLoginProcessStarted = false;
                        connectToDevice(
                                (String) call.argument("sn"),
                                (String) call.argument("username"),
                                (String) call.argument("password"),
                                result
                        );
                    } else if ("publishText".equals(call.method)) {
                        publishTextProgram(
                                (String) call.argument("sn"),
                                (String) call.argument("text"),
                                result
                        );
                    } else if ("publishMultiWindow".equals(call.method)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> windows = (List<Map<String, Object>>) call.argument("windows");
                        publishMultiWindowProgram(
                                (String) call.argument("sn"),
                                ((Number) call.argument("ledWidth")).intValue(),
                                ((Number) call.argument("ledHeight")).intValue(),
                                windows,
                                result
                        );
                    } else {
                        result.notImplemented();
                    }
                });
    }

    private void connectToDevice(String sn, String username, String password, MethodChannel.Result result) {
        new Thread(() -> {
            try {
                File rootDir = getExternalFilesDir("demotest");
                if (rootDir != null && !rootDir.exists()) rootDir.mkdirs();
                String rootPath = (rootDir != null) ? rootDir.getAbsolutePath() : "";

                sdkInstance.nvSetDevLang("Java");
                sdkInstance.nvInit(rootPath, "{\"company\":\"NovaStar\",\"phone\":\"1\",\"email\":\"a@b.c\"}");

                sdkInstance.nvSearchTerminalAsync((code, data) -> {
                    if (isLoginProcessStarted || code != 0) return;
                    isLoginProcessStarted = true;

                    try { Thread.sleep(300); } catch (Exception ignore) {}

                    try {
                        JSONObject json = new JSONObject();
                        json.put("sn", sn);
                        json.put("username", username);
                        json.put("password", password);
                        json.put("loginType", 0);
                        json.put("rememberPwd", 1);

                        sdkInstance.nvLoginAsync(json.toString(), (c, d) -> runOnUiThread(() -> {
                            if (c == 0) result.success(d);
                            else result.error("Login", String.valueOf(c), d);
                        }));
                    } catch (Exception e) {
                        Log.e(TAG, "Login error", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "connect crash", e);
            }
        }).start();
    }

    // 原版发送文字
    private void publishTextProgram(String sn, String text, MethodChannel.Result result) {
        Log.e(TAG, "收到文字发送请求: " + text);
        new Thread(() -> {
            try {
                File demoDir = getExternalFilesDir("demotest");
                if (demoDir == null) {
                    runOnUiThread(() -> result.error("Path", "demoDir is null", null));
                    return;
                }
                if (!demoDir.exists()) demoDir.mkdirs();
                String rootPath = demoDir.getAbsolutePath();

                String createJson = "{\"name\":\"TextDemo\",\"width\":128,\"height\":64,\"tplID\":1,\"winInfo\":{\"height\":64,\"width\":128,\"left\":0,\"top\":0,\"zindex\":0,\"index\":0}}";

                sdkInstance.nvCreateProgramAsync(createJson, (c1, d1) -> {
                    if (c1 != 0) {
                        runOnUiThread(() -> result.error("CreateProgram", String.valueOf(c1), d1));
                        return;
                    }

                    int pid = 1;
                    try {
                        pid = new JSONObject(d1).getJSONObject("onSuccess").getInt("programID");
                    } catch (Exception e) {
                        Log.e(TAG, "解析programID失败", e);
                    }
                    final int programId = pid;

                    String editJson = buildTextEditJson(programId, text);

                    sdkInstance.nvSetPageProgramAsync(editJson, (c2, d2) -> {
                        if (c2 != 0) {
                            runOnUiThread(() -> result.error("EditProgram", String.valueOf(c2), d2));
                            return;
                        }

                        String makeJson = "{\"programID\":" + programId + ",\"outPutPath\":\"" + rootPath + "\",\"mediasPath\":[]}";

                        sdkInstance.nvMakeProgramAsync(makeJson, (c3, d3) -> {
                            if (c3 != 0) {
                                runOnUiThread(() -> result.error("MakeProgram", String.valueOf(c3), d3));
                                return;
                            }

                            String transferJson = "{\"sn\":\"" + sn + "\",\"programName\":\"program" + programId + "\",\"iconPath\":\"\",\"iconName\":\"\",\"deviceIdentifier\":\"TextDemo\",\"startPlayAfterTransferred\":true,\"insertPlay\":true,\"sendProgramFilePaths\":{\"programPath\":\"" + rootPath + "/program" + programId + "\",\"mediasPath\":{}}}";

                            sdkInstance.nvStartTransferProgramAsync(transferJson, (c4, d4) -> {
                                if (c4 == 65362 || c4 == 65363) return;
                                runOnUiThread(() -> {
                                    if (c4 == 0) result.success("发送文字成功");
                                    else result.error("TransferProgram", String.valueOf(c4), d4);
                                });
                            });
                        });
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "PublishText Crash", e);
                runOnUiThread(() -> result.error("Crash", e.getMessage(), null));
            }
        }).start();
    }

    private String buildTextEditJson(int programId, String text) {
        return "{\"programID\":" + programId + ",\"pageID\":1,\"pageInfo\":{\"name\":\"jiemu\",\"widgetContainers\":[{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":1,\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"\",\"type\":\"ARCH_TEXT\",\"constraints\":[{\"cron\":[\"0 0 0 ? * 1,2,3,4,5,6,7\"],\"endTime\":\"4016-06-06T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"0px,0px,0px,0px\",\"style\":0,\"backgroundColor\":\"#FF000000\",\"name\":\"border\",\"cornerRadius\":\"2%\",\"effects\":{\"headTailSpacing\":\"10\",\"isHeadTail\":false,\"speedByPixelEnable\":false,\"speed\":3,\"animation\":\"CLOCK_WISE\"}},\"inAnimation\":{\"type\":0,\"duration\":0},\"duration\":10000,\"name\":\"archText0\",\"originalDataSource\":\"\",\"extraData\":{},\"metadata\":{\"itemSource\":\"\",\"content\":{\"displayStyle\":{\"type\":\"SCROLL\",\"singleLine\":false,\"pageSwitchAttributes\":{\"inAnimation\":{\"type\":1,\"duration\":1000},\"remainDuration\":10000},\"scrollAttributes\":{\"effects\":{\"animation\":\"MARQUEE_LEFT\",\"speed\":3.0,\"speedByPixelEnable\":false,\"isHeadTail\":false,\"headTailSpacing\":\"10\"}},\"offset\":{\"x\":0,\"y\":0},\"rotateAttributes\":{\"angle\":0,\"duration\":0}},\"textAttributes\":[{\"key\":1,\"attributes\":{\"backgroundColor\":\"#00000000\",\"textColor\":\"#ffff0000\",\"font\":{\"family\":[\"Arial\"],\"style\":\"NORMAL\",\"size\":20,\"isUnderline\":false},\"letterSpacing\":0,\"shadowEnable\":false,\"shadowRadius\":10,\"shadowDx\":2,\"shadowDy\":2,\"shadowColor\":\"#00ff00\",\"strokeEnable\":false,\"strokeWidth\":0,\"strokeColor\":\"\",\"effects\":{\"TempTexturePath\":\"\",\"colors\":[],\"type\":\"\",\"texture\":\"\"}}}],\"autoPaging\":true,\"paragraphs\":[{\"verticalAlignment\":\"CENTER\",\"horizontalAlignment\":\"CENTER\",\"backgroundColor\":\"#00000000\",\"lineSpacing\":0,\"letterSpacing\":0,\"lines\":[{\"segs\":[{\"attributeKey\":1,\"content\":\"" + text + "\"}]}]}],\"backgroundMusic\":{\"duration\":1000,\"isTextSync\":false}},\"HorTextAlignment\":\"CENTER\",\"VerTextAlignment\":\"CENTER\",\"pictureList\":[],\"textAntialiasing\":false,\"type\":\"SCROLL\"}}]},\"enable\":true,\"id\":1,\"itemsSource\":\"\",\"layout\":{\"height\":\"1.0\",\"width\":\"1.0\",\"x\":\"0.0\",\"y\":\"0.0\"},\"name\":\"widgetContainers1\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":0,\"containerType\":\"textWin\"}]}}";
    }

    // 多窗口发送
    private void publishMultiWindowProgram(String sn, int ledWidth, int ledHeight,
                                            List<Map<String, Object>> windows,
                                            MethodChannel.Result result) {
        Log.e(TAG, "收到多窗口节目请求，共 " + windows.size() + " 个窗口");

        new Thread(() -> {
            try {
                File demoDir = getExternalFilesDir("demotest");
                if (demoDir == null) {
                    runOnUiThread(() -> result.error("Path", "demoDir is null", null));
                    return;
                }
                if (!demoDir.exists()) demoDir.mkdirs();
                String rootPath = demoDir.getAbsolutePath();

                List<WindowData> windowDataList = new ArrayList<>();
                Map<String, String> mediasPathMap = new HashMap<>();

                for (Map<String, Object> windowMap : windows) {
                    WindowData data = new WindowData();
                    data.id = getStringValue(windowMap, "id", "");
                    data.type = getStringValue(windowMap, "type", "text");
                    data.x = getIntValue(windowMap, "x", 0);
                    data.y = getIntValue(windowMap, "y", 0);
                    data.w = getIntValue(windowMap, "w", 64);
                    data.h = getIntValue(windowMap, "h", 32);
                    data.text = getStringValue(windowMap, "text", "");
                    data.filePath = getStringValue(windowMap, "filePath", "");
                    data.fileName = getStringValue(windowMap, "fileName", "");

                    if (("image".equals(data.type) || "video".equals(data.type)) &&
                            data.filePath != null && !data.filePath.isEmpty()) {

                        String ext = ".jpg";
                        if (data.fileName != null && data.fileName.contains(".")) {
                            ext = data.fileName.substring(data.fileName.lastIndexOf("."));
                        } else if ("video".equals(data.type)) {
                            ext = ".mp4";
                        }

                        String md5 = getFileMD5Sync(data.filePath);
                        data.md5FileName = md5 + ext;

                        String targetPath = rootPath + "/" + data.md5FileName;
                        copyFile(data.filePath, targetPath);
                        data.targetFilePath = targetPath;

                        mediasPathMap.put(targetPath, data.md5FileName);
                    }

                    windowDataList.add(data);
                }

                String createJson = "{\"name\":\"TextDemo\",\"width\":128,\"height\":64,\"tplID\":1,\"winInfo\":{\"height\":64,\"width\":128,\"left\":0,\"top\":0,\"zindex\":0,\"index\":0}}";

                final String fRootPath = rootPath;
                final Map<String, String> fMediasPathMap = mediasPathMap;
                final List<WindowData> fWindowDataList = windowDataList;

                sdkInstance.nvCreateProgramAsync(createJson, (c1, d1) -> {
                    if (c1 != 0) {
                        runOnUiThread(() -> result.error("CreateProgram", String.valueOf(c1), d1));
                        return;
                    }

                    int pid = 1;
                    try {
                        pid = new JSONObject(d1).getJSONObject("onSuccess").getInt("programID");
                    } catch (Exception e) {}
                    final int programId = pid;

                    String editJson = buildMultiWindowEditJson(programId, ledWidth, ledHeight, fWindowDataList);
                    Log.e(TAG, "多窗口editJson: " + editJson);

                    sdkInstance.nvSetPageProgramAsync(editJson, (c2, d2) -> {
                        Log.e(TAG, "编辑回调: code=" + c2 + ", data=" + d2);
                        if (c2 != 0) {
                            runOnUiThread(() -> result.error("EditProgram", String.valueOf(c2), d2));
                            return;
                        }

                        String makeJson = "{\"programID\":" + programId + ",\"outPutPath\":\"" + fRootPath + "\",\"mediasPath\":[]}";

                        sdkInstance.nvMakeProgramAsync(makeJson, (c3, d3) -> {
                            if (c3 != 0) {
                                runOnUiThread(() -> result.error("MakeProgram", String.valueOf(c3), d3));
                                return;
                            }

                            StringBuilder mediasJson = new StringBuilder("{");
                            boolean first = true;
                            for (Map.Entry<String, String> entry : fMediasPathMap.entrySet()) {
                                if (!first) mediasJson.append(",");
                                mediasJson.append("\"").append(entry.getKey()).append("\":\"")
                                        .append(entry.getValue()).append("\"");
                                first = false;
                            }
                            mediasJson.append("}");

                            String transferJson = "{\"sn\":\"" + sn + "\",\"programName\":\"program" + programId + "\",\"iconPath\":\"\",\"iconName\":\"\",\"deviceIdentifier\":\"TextDemo\",\"startPlayAfterTransferred\":true,\"insertPlay\":true,\"sendProgramFilePaths\":{\"programPath\":\"" + fRootPath + "/program" + programId + "\",\"mediasPath\":" + mediasJson.toString() + "}}";

                            sdkInstance.nvStartTransferProgramAsync(transferJson, (c4, d4) -> {
                                if (c4 == 65362 || c4 == 65363) return;
                                if (c4 == 65361 || c4 == 0) {
                                    runOnUiThread(() -> result.success("发送成功，共 " + fWindowDataList.size() + " 个窗口"));
                                    return;
                                }
                                runOnUiThread(() -> result.error("TransferProgram", String.valueOf(c4), d4));
                            });
                        });
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "PublishMultiWindow Crash", e);
                runOnUiThread(() -> result.error("Crash", e.getMessage(), null));
            }
        }).start();
    }

    private String buildMultiWindowEditJson(int programId, int ledWidth, int ledHeight,
                                             List<WindowData> windowDataList) {
        // 只有一个文字窗口时，直接用原版格式
        if (windowDataList.size() == 1 && "text".equals(windowDataList.get(0).type)) {
            WindowData data = windowDataList.get(0);
            return "{\"programID\":" + programId + ",\"pageID\":1,\"pageInfo\":{\"name\":\"jiemu\",\"widgetContainers\":[{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":1,\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"\",\"type\":\"ARCH_TEXT\",\"constraints\":[{\"cron\":[\"0 0 0 ? * 1,2,3,4,5,6,7\"],\"endTime\":\"4016-06-06T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"0px,0px,0px,0px\",\"style\":0,\"backgroundColor\":\"#FF000000\",\"name\":\"border\",\"cornerRadius\":\"2%\",\"effects\":{\"headTailSpacing\":\"10\",\"isHeadTail\":false,\"speedByPixelEnable\":false,\"speed\":3,\"animation\":\"CLOCK_WISE\"}},\"inAnimation\":{\"type\":0,\"duration\":0},\"duration\":10000,\"name\":\"archText0\",\"originalDataSource\":\"\",\"extraData\":{},\"metadata\":{\"itemSource\":\"\",\"content\":{\"displayStyle\":{\"type\":\"SCROLL\",\"singleLine\":false,\"pageSwitchAttributes\":{\"inAnimation\":{\"type\":1,\"duration\":1000},\"remainDuration\":10000},\"scrollAttributes\":{\"effects\":{\"animation\":\"MARQUEE_LEFT\",\"speed\":3.0,\"speedByPixelEnable\":false,\"isHeadTail\":false,\"headTailSpacing\":\"10\"}},\"offset\":{\"x\":0,\"y\":0},\"rotateAttributes\":{\"angle\":0,\"duration\":0}},\"textAttributes\":[{\"key\":1,\"attributes\":{\"backgroundColor\":\"#00000000\",\"textColor\":\"#ffff0000\",\"font\":{\"family\":[\"Arial\"],\"style\":\"NORMAL\",\"size\":20,\"isUnderline\":false},\"letterSpacing\":0,\"shadowEnable\":false,\"shadowRadius\":10,\"shadowDx\":2,\"shadowDy\":2,\"shadowColor\":\"#00ff00\",\"strokeEnable\":false,\"strokeWidth\":0,\"strokeColor\":\"\",\"effects\":{\"TempTexturePath\":\"\",\"colors\":[],\"type\":\"\",\"texture\":\"\"}}}],\"autoPaging\":true,\"paragraphs\":[{\"verticalAlignment\":\"CENTER\",\"horizontalAlignment\":\"CENTER\",\"backgroundColor\":\"#00000000\",\"lineSpacing\":0,\"letterSpacing\":0,\"lines\":[{\"segs\":[{\"attributeKey\":1,\"content\":\"" + escapeJson(data.text) + "\"}]}]}],\"backgroundMusic\":{\"duration\":1000,\"isTextSync\":false}},\"HorTextAlignment\":\"CENTER\",\"VerTextAlignment\":\"CENTER\",\"pictureList\":[],\"textAntialiasing\":false,\"type\":\"SCROLL\"}}]},\"enable\":true,\"id\":1,\"itemsSource\":\"\",\"layout\":{\"height\":\"1.0\",\"width\":\"1.0\",\"x\":\"0.0\",\"y\":\"0.0\"},\"name\":\"widgetContainers1\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":0,\"containerType\":\"textWin\"}]}}";
        }

        // 多窗口情况
        StringBuilder widgetContainers = new StringBuilder();
        for (int i = 0; i < windowDataList.size(); i++) {
            WindowData data = windowDataList.get(i);

            double layoutX = (double) data.x / ledWidth;
            double layoutY = (double) data.y / ledHeight;
            double layoutW = (double) data.w / ledWidth;
            double layoutH = (double) data.h / ledHeight;

            String containerJson;
            if ("text".equals(data.type)) {
                containerJson = buildTextContainer(i + 1, data, layoutX, layoutY, layoutW, layoutH);
            } else if ("image".equals(data.type)) {
                containerJson = buildImageContainer(i + 1, data, layoutX, layoutY, layoutW, layoutH);
            } else if ("video".equals(data.type)) {
                containerJson = buildVideoContainer(i + 1, data, layoutX, layoutY, layoutW, layoutH);
            } else {
                continue;
            }

            if (widgetContainers.length() > 0) {
                widgetContainers.append(",");
            }
            widgetContainers.append(containerJson);
        }

        return "{\"programID\":" + programId + ",\"pageID\":1,\"pageInfo\":{\"name\":\"jiemu\",\"widgetContainers\":[" + widgetContainers.toString() + "]}}";
    }

    private String buildTextContainer(int index, WindowData data,
                                       double layoutX, double layoutY, double layoutW, double layoutH) {
        return "{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":1,\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"\",\"type\":\"ARCH_TEXT\",\"constraints\":[{\"cron\":[\"0 0 0 ? * 1,2,3,4,5,6,7\"],\"endTime\":\"4016-06-06T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"0px,0px,0px,0px\",\"style\":0,\"backgroundColor\":\"#FF000000\",\"name\":\"border\",\"cornerRadius\":\"2%\",\"effects\":{\"headTailSpacing\":\"10\",\"isHeadTail\":false,\"speedByPixelEnable\":false,\"speed\":3,\"animation\":\"CLOCK_WISE\"}},\"inAnimation\":{\"type\":0,\"duration\":0},\"duration\":10000,\"name\":\"archText0\",\"originalDataSource\":\"\",\"extraData\":{},\"metadata\":{\"itemSource\":\"\",\"content\":{\"displayStyle\":{\"type\":\"SCROLL\",\"singleLine\":false,\"pageSwitchAttributes\":{\"inAnimation\":{\"type\":1,\"duration\":1000},\"remainDuration\":10000},\"scrollAttributes\":{\"effects\":{\"animation\":\"MARQUEE_LEFT\",\"speed\":3.0,\"speedByPixelEnable\":false,\"isHeadTail\":false,\"headTailSpacing\":\"10\"}},\"offset\":{\"x\":0,\"y\":0},\"rotateAttributes\":{\"angle\":0,\"duration\":0}},\"textAttributes\":[{\"key\":1,\"attributes\":{\"backgroundColor\":\"#00000000\",\"textColor\":\"#ffff0000\",\"font\":{\"family\":[\"Arial\"],\"style\":\"NORMAL\",\"size\":20,\"isUnderline\":false},\"letterSpacing\":0,\"shadowEnable\":false,\"shadowRadius\":10,\"shadowDx\":2,\"shadowDy\":2,\"shadowColor\":\"#00ff00\",\"strokeEnable\":false,\"strokeWidth\":0,\"strokeColor\":\"\",\"effects\":{\"TempTexturePath\":\"\",\"colors\":[],\"type\":\"\",\"texture\":\"\"}}}],\"autoPaging\":true,\"paragraphs\":[{\"verticalAlignment\":\"CENTER\",\"horizontalAlignment\":\"CENTER\",\"backgroundColor\":\"#00000000\",\"lineSpacing\":0,\"letterSpacing\":0,\"lines\":[{\"segs\":[{\"attributeKey\":1,\"content\":\"" + escapeJson(data.text) + "\"}]}]}],\"backgroundMusic\":{\"duration\":1000,\"isTextSync\":false}},\"HorTextAlignment\":\"CENTER\",\"VerTextAlignment\":\"CENTER\",\"pictureList\":[],\"textAntialiasing\":false,\"type\":\"SCROLL\"}}]},\"enable\":true,\"id\":" + index + ",\"itemsSource\":\"\",\"layout\":{\"height\":\"" + layoutH + "\",\"width\":\"" + layoutW + "\",\"x\":\"" + layoutX + "\",\"y\":\"" + layoutY + "\"},\"name\":\"widgetContainers" + index + "\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":" + (index - 1) + ",\"containerType\":\"textWin\"}";
    }

    private String buildImageContainer(int index, WindowData data,
                                        double layoutX, double layoutY, double layoutW, double layoutH) {
        return "{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":" + index + ",\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"" + data.md5FileName + "\",\"type\":\"PICTURE\",\"constraints\":[{\"cron\":[],\"endTime\":\"4099-12-30T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"0px,0px,0px,0px\",\"style\":0,\"backgroundColor\":\"#ff000000\",\"name\":\"border\",\"cornerRadius\":\"0%\",\"effects\":{\"headTailSpacing\":\"\",\"isHeadTail\":false,\"speedByPixelEnable\":true,\"speed\":0,\"animation\":\"NONE\"}},\"inAnimation\":{\"type\":0,\"duration\":1000},\"duration\":20000,\"name\":\"image" + index + "\",\"originalDataSource\":\"" + data.targetFilePath + "\",\"functionStorage\":\"\",\"isSupportSpecialEffects\":false}]},\"enable\":true,\"id\":" + index + ",\"itemsSource\":\"\",\"layout\":{\"height\":\"" + layoutH + "\",\"width\":\"" + layoutW + "\",\"x\":\"" + layoutX + "\",\"y\":\"" + layoutY + "\"},\"name\":\"widgetContainers" + index + "\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":" + (index - 1) + ",\"containerType\":\"pictureVideoWin\"}";
    }

    private String buildVideoContainer(int index, WindowData data,
                                        double layoutX, double layoutY, double layoutW, double layoutH) {
        return "{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":" + index + ",\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"" + data.md5FileName + "\",\"type\":\"VIDEO\",\"constraints\":[{\"cron\":[\"0 0 0 ? * 1,2,3,4,5,6,7\"],\"endTime\":\"4016-06-06T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"0px,0px,0px,0px\",\"style\":0,\"backgroundColor\":\"#FF000000\",\"name\":\"border\",\"cornerRadius\":\"0%\",\"effects\":{\"headTailSpacing\":\"10\",\"isHeadTail\":false,\"speedByPixelEnable\":false,\"speed\":3,\"animation\":\"NONE\"}},\"inAnimation\":{\"type\":0,\"duration\":1000},\"duration\":10000,\"name\":\"video" + index + "\",\"originalDataSource\":\"" + data.targetFilePath + "\",\"metadata\":{\"volume\":100}}]},\"enable\":true,\"id\":" + index + ",\"itemsSource\":\"\",\"layout\":{\"height\":\"" + layoutH + "\",\"width\":\"" + layoutW + "\",\"x\":\"" + layoutX + "\",\"y\":\"" + layoutY + "\"},\"name\":\"widgetContainers" + index + "\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":" + (index - 1) + ",\"containerType\":\"pictureVideoWin\"}";
    }

    private String getFileMD5Sync(String filePath) {
        final String[] md5Result = {""};
        final Object lock = new Object();
        final boolean[] isCompleted = {false};

        String md5Req = "{\"filePath\":\"" + filePath + "\"}";
        sdkInstance.nvGetFileMD5Async(md5Req, (code, data) -> {
            if (data != null && !data.isEmpty()) {
                try {
                    if (data.startsWith("{")) {
                        JSONObject json = new JSONObject(data);
                        if (json.has("md5")) {
                            md5Result[0] = json.getString("md5");
                        } else if (json.has("onSuccess")) {
                            md5Result[0] = json.getJSONObject("onSuccess").optString("md5", "");
                        }
                    } else {
                        md5Result[0] = data;
                    }
                } catch (Exception e) {
                    md5Result[0] = data;
                }
            }
            isCompleted[0] = true;
            synchronized (lock) {
                lock.notify();
            }
        });

        synchronized (lock) {
            try {
                if (!isCompleted[0]) {
                    lock.wait(10000);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "等待MD5超时", e);
            }
        }

        if (md5Result[0] == null || md5Result[0].isEmpty()) {
            md5Result[0] = "file_" + System.currentTimeMillis();
        }
        return md5Result[0];
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private void copyFile(String src, String dst) throws Exception {
        try (FileInputStream is = new FileInputStream(src);
             FileOutputStream os = new FileOutputStream(dst)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
        }
    }

    private static class WindowData {
        String id;
        String type;
        int x, y, w, h;
        String text;
        String filePath;
        String fileName;
        String md5FileName;
        String targetFilePath;
    }
}