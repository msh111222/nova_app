package com.novastar.sdkdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

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
        // 申请权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, 1001);
            }
        }

        // 加载 SDK
        try {
            sdkInstance = Native.load("viplexcore", ViplexCore.class);
        } catch (Throwable e) {
            Log.e(TAG, "load viplexcore fail", e);
        }

        // 方法通道处理
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
                    } else if ("publishProgram".equals(call.method)) {
                        publishProgramChain(
                                (String) call.argument("sn"),
                                (String) call.argument("imagePath"),
                                result
                        );
                    } else if ("publishText".equals(call.method)) {
                        publishTextProgram(
                                (String) call.argument("sn"),
                                (String) call.argument("text"),
                                result
                        );
                    } else {
                        result.notImplemented();
                    }
                });
    }

    // 登录
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

    // 发送图片
    private void publishProgramChain(String sn, String rawImagePath, MethodChannel.Result result) {
        new Thread(() -> {
            try {
                File demoDir = getExternalFilesDir("demotest");
                if (demoDir == null) {
                    runOnUiThread(() -> result.error("Path", "demoDir is null", null));
                    return;
                }
                if (!demoDir.exists()) demoDir.mkdirs();
                String rootPath = demoDir.getAbsolutePath();

                // 拷贝原始文件
                String fileName = "4.png";
                File targetFile = new File(demoDir, fileName);
                copyFile(rawImagePath, targetFile.getAbsolutePath());
                String finalPath = targetFile.getAbsolutePath();

                Log.e(TAG, "文件就位: " + finalPath);
                String createJson = "{\"name\":\"FlutterDemo\",\"width\":128,\"height\":64,\"tplID\":1,\"winInfo\":{\"height\":64,\"width\":128,\"left\":0,\"top\":0,\"zindex\":0,\"index\":0}}";

                sdkInstance.nvCreateProgramAsync(createJson, (c1, d1) -> {
                    if (c1 != 0) {
                        runOnUiThread(() -> result.error("Step1", String.valueOf(c1), d1));
                        return;
                    }
                    int pid = 1;
                    try { pid = new JSONObject(d1).getJSONObject("onSuccess").getInt("programID"); } catch (Exception e) {}
                    final int programId = pid;
                    
                    // 获取 MD5
                    String md5Req = "{\"filePath\":\"" + finalPath + "\"}";
                    sdkInstance.nvGetFileMD5Async(md5Req, (cMd5, dMd5) -> {
                        Log.e(TAG, "Step1.5 MD5=" + dMd5);
                        
                        // 关键修复：使用 MD5 文件名
                        String md5FileName = dMd5 + ".png";
                        try {
                            copyFile(finalPath, rootPath + "/" + md5FileName);
                        } catch (Exception e) {
                            Log.e(TAG, "复制MD5文件失败", e);
                        }

                        // Step 2: originalDataSource 指向 MD5 文件，dataSource 指向 MD5 文件名
                        String editJson = "{\"programID\":" + programId + ",\"pageID\":1,\"pageInfo\":{\"name\":\"jiemu\",\"widgetContainers\":[{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":1,\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"" + md5FileName + "\",\"type\":\"PICTURE\",\"constraints\":[{\"cron\":[],\"endTime\":\"4099-12-30T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"2px,3px,5%,6\",\"style\":0,\"backgroundColor\":\"#ff000000\",\"name\":\"border\",\"cornerRadius\":\"2%\",\"effects\":{\"headTailSpacing\":\"\",\"isHeadTail\":false,\"speedByPixelEnable\":true,\"speed\":0,\"animation\":\"CLOCK_WISE\"}},\"inAnimation\":{\"type\":0,\"duration\":1000},\"duration\":20000,\"name\":\"" + fileName + "\",\"originalDataSource\":\"" + rootPath + "/" + md5FileName + "\",\"functionStorage\":\"\",\"isSupportSpecialEffects\":false}]},\"enable\":true,\"id\":1,\"itemsSource\":\"\",\"layout\":{\"height\":\"1.0\",\"width\":\"1.0\",\"x\":\"0.0\",\"y\":\"0.0\"},\"name\":\"widgetContainers1\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":0}]}}";

                        sdkInstance.nvSetPageProgramAsync(editJson, (c2, d2) -> {
                            Log.e(TAG, "Step2 结果: code=" + c2 + ", data=" + d2); 
                            if (c2 != 0) {
                                runOnUiThread(() -> result.error("Step2", String.valueOf(c2), d2));
                                return;
                            }

                           // Step 3: 生成节目
                            String makeJson = "{\"programID\":" + programId + ",\"outPutPath\":\"" + rootPath + "\",\"mediasPath\":[{\"oldPath\":\"test\",\"newPath\":\"test\"}]}";
                            sdkInstance.nvMakeProgramAsync(makeJson, (c3, d3) -> {
                                if (c3 != 0) {
                                    runOnUiThread(() -> result.error("Step3", String.valueOf(c3), d3));
                                    return;
                                }

                                // Step 4: mediasPath 指向真实 MD5 文件
                                String transferJson = "{\"sn\":\"" + sn + "\",\"programName\":\"program" + programId + "\",\"iconPath\":\"\",\"iconName\":\"\",\"deviceIdentifier\":\"FlutterDemo\",\"startPlayAfterTransferred\":true,\"insertPlay\":true,\"sendProgramFilePaths\":{\"programPath\":\"" + rootPath + "/program" + programId + "\",\"mediasPath\":{\"" + rootPath + "/" + md5FileName + "\":\"" + md5FileName + "\"}}}";

                                sdkInstance.nvStartTransferProgramAsync(transferJson, (c4, d4) -> {
                                    if (c4 == 65362 || c4 == 65363) return;
                                    runOnUiThread(() -> {
                                        if (c4 == 0) result.success("发送图片成功");
                                        else result.error("Step4", String.valueOf(c4), d4);
                                    });
                                });
                            });
                        });
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "Crash", e);
                runOnUiThread(() -> result.error("Crash", e.getMessage(), null));
            }
        }).start();
    }

    // 发送文字
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

                // Step 1: 创建节目
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

                    // Step 2: 编辑节目 - 添加文字组件
                    String editJson = buildTextEditJson(programId, text);

                    sdkInstance.nvSetPageProgramAsync(editJson, (c2, d2) -> {
                        if (c2 != 0) {
                            runOnUiThread(() -> result.error("EditProgram", String.valueOf(c2), d2));
                            return;
                        }

                        // Step 3: 生成节目（文字不需要mediasPath映射）
                        String makeJson = "{\"programID\":" + programId + ",\"outPutPath\":\"" + rootPath + "\",\"mediasPath\":[]}";

                        sdkInstance.nvMakeProgramAsync(makeJson, (c3, d3) -> {
                            if (c3 != 0) {
                                runOnUiThread(() -> result.error("MakeProgram", String.valueOf(c3), d3));
                                return;
                            }

                            // Step 4: 传输节目（文字无需mediasPath）
                            String transferJson = "{\"sn\":\"" + sn + "\",\"programName\":\"program" + programId + "\",\"iconPath\":\"\",\"iconName\":\"\",\"deviceIdentifier\":\"TextDemo\",\"startPlayAfterTransferred\":true,\"insertPlay\":true,\"sendProgramFilePaths\":{\"programPath\":\"" + rootPath + "/program" + programId + "\",\"mediasPath\":{}}}";

                            sdkInstance.nvStartTransferProgramAsync(transferJson, (c4, d4) -> {
                                if (c4 == 65362 || c4 == 65363) return; // 忽略进度回调
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

    // 构建文字节目的JSON
private String buildTextEditJson(int programId, String text) {
    return "{\"programID\":" + programId + ",\"pageID\":1,\"pageInfo\":{\"name\":\"jiemu\",\"widgetContainers\":[{\"audioGroup\":\"\",\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"contents\":{\"widgetGroups\":[],\"widgets\":[{\"id\":1,\"enable\":true,\"repeatCount\":1,\"layout\":{\"y\":\"0\",\"height\":\"100%\",\"x\":\"0\",\"width\":\"100%\"},\"backgroundColor\":\"#00000000\",\"backgroundDrawable\":\"\",\"backgroundMusic\":\"\",\"zOrder\":0,\"displayRatio\":\"FULL\",\"outAnimation\":{\"type\":0,\"duration\":0},\"dataSource\":\"\",\"type\":\"ARCH_TEXT\",\"constraints\":[{\"cron\":[\"0 0 0 ? * 1,2,3,4,5,6,7\"],\"endTime\":\"4016-06-06T23:59:59Z+8:00\",\"startTime\":\"1970-01-01T00:00:00Z+8:00\"}],\"border\":{\"borderThickness\":\"0px,0px,0px,0px\",\"style\":0,\"backgroundColor\":\"#FF000000\",\"name\":\"border\",\"cornerRadius\":\"2%\",\"effects\":{\"headTailSpacing\":\"10\",\"isHeadTail\":false,\"speedByPixelEnable\":false,\"speed\":3,\"animation\":\"CLOCK_WISE\"}},\"inAnimation\":{\"type\":0,\"duration\":0},\"duration\":10000,\"name\":\"archText0\",\"originalDataSource\":\"\",\"extraData\":{},\"metadata\":{\"itemSource\":\"\",\"content\":{\"displayStyle\":{\"type\":\"SCROLL\",\"singleLine\":false,\"pageSwitchAttributes\":{\"inAnimation\":{\"type\":1,\"duration\":1000},\"remainDuration\":10000},\"scrollAttributes\":{\"effects\":{\"animation\":\"MARQUEE_LEFT\",\"speed\":3.0,\"speedByPixelEnable\":false,\"isHeadTail\":false,\"headTailSpacing\":\"10\"}},\"offset\":{\"x\":0,\"y\":0},\"rotateAttributes\":{\"angle\":0,\"duration\":0}},\"textAttributes\":[{\"key\":1,\"attributes\":{\"backgroundColor\":\"#00000000\",\"textColor\":\"#ffff0000\",\"font\":{\"family\":[\"Arial\"],\"style\":\"NORMAL\",\"size\":20,\"isUnderline\":false},\"letterSpacing\":0,\"shadowEnable\":false,\"shadowRadius\":10,\"shadowDx\":2,\"shadowDy\":2,\"shadowColor\":\"#00ff00\",\"strokeEnable\":false,\"strokeWidth\":0,\"strokeColor\":\"\",\"effects\":{\"TempTexturePath\":\"\",\"colors\":[],\"type\":\"\",\"texture\":\"\"}}}],\"autoPaging\":true,\"paragraphs\":[{\"verticalAlignment\":\"CENTER\",\"horizontalAlignment\":\"CENTER\",\"backgroundColor\":\"#00000000\",\"lineSpacing\":0,\"letterSpacing\":0,\"lines\":[{\"segs\":[{\"attributeKey\":1,\"content\":\"" + text + "\"}]}]}],\"backgroundMusic\":{\"duration\":1000,\"isTextSync\":false}},\"HorTextAlignment\":\"CENTER\",\"VerTextAlignment\":\"CENTER\",\"pictureList\":[],\"textAntialiasing\":false,\"type\":\"SCROLL\"}}]},\"enable\":true,\"id\":1,\"itemsSource\":\"\",\"layout\":{\"height\":\"1.0\",\"width\":\"1.0\",\"x\":\"0.0\",\"y\":\"0.0\"},\"name\":\"widgetContainers1\",\"pickCount\":0,\"pickPolicy\":\"ORDER\",\"zOrder\":0,\"containerType\":\"textWin\"}]}}";
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
}