import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(home: const NovaLoginScreen());
  }
}

class NovaLoginScreen extends StatefulWidget {
  const NovaLoginScreen({super.key});
  @override
  State<NovaLoginScreen> createState() => _NovaLoginScreenState();
}

class _NovaLoginScreenState extends State<NovaLoginScreen> {
  static const platform = MethodChannel('com.novastar/bridge');

  // 登录信息
  final TextEditingController _snController = TextEditingController(
    text: "25611A000001735",
  );
  final TextEditingController _userController = TextEditingController(
    text: "admin",
  );
  final TextEditingController _passController = TextEditingController(
    text: "SN2008@+",
  );

  String _logText = "等待操作...";
  bool _isConnecting = false;
  bool _isLoggedIn = false;

  // 1. 登录
  Future<void> _connectToNova() async {
    FocusScope.of(context).unfocus();
    setState(() {
      _isConnecting = true;
      _logText = "正在登录...";
    });
    try {
      final String result = await platform.invokeMethod('initAndLogin', {
        "sn": _snController.text,
        "username": _userController.text,
        "password": _passController.text,
      });
      setState(() {
        _logText = "✅ 登录成功\n$result";
        _isLoggedIn = true;
      });
    } catch (e) {
      setState(() {
        _logText = "❌ 登录失败: $e";
      });
    } finally {
      setState(() {
        _isConnecting = false;
      });
    }
  }

  // 2. 发送图片
  Future<void> _publishProgram() async {
    setState(() {
      _isConnecting = true;
      _logText += "\n\n正在准备 4.png...";
    });
    try {
      final File imageFile = await _copyAssetToLocal("assets/4.png");
      final String result = await platform.invokeMethod('publishProgram', {
        "sn": _snController.text,
        "imagePath": imageFile.path,
      });
      setState(() {
        _logText = "🎉 图片发送结果: $result";
      });
    } catch (e) {
      setState(() {
        _logText += "\n❌ 图片发送失败: $e";
      });
    } finally {
      setState(() {
        _isConnecting = false;
      });
    }
  }

  Future<File> _copyAssetToLocal(String assetName) async {
    final byteData = await rootBundle.load(assetName);
    final tempDir = await getTemporaryDirectory();
    final file = File('${tempDir.path}/4.png');
    await file.writeAsBytes(
      byteData.buffer.asUint8List(
        byteData.offsetInBytes,
        byteData.lengthInBytes,
      ),
    );
    return file;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("NovaStar 官方素材测试")),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            // 登录输入区
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _snController,
                    decoration: const InputDecoration(labelText: "SN"),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextField(
                    controller: _userController,
                    decoration: const InputDecoration(labelText: "User"),
                  ),
                ),
              ],
            ),
            TextField(
              controller: _passController,
              decoration: const InputDecoration(labelText: "Pass"),
            ),
            const SizedBox(height: 15),

            // 登录按钮
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _isConnecting ? null : _connectToNova,
                child: const Text("1. 登录 (Login)"),
              ),
            ),

            const SizedBox(height: 15),

            // 图片发送按钮
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: (_isLoggedIn && !_isConnecting)
                    ? _publishProgram
                    : null,
                style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                child: const Text("2. 发送图片 (4.png)"),
              ),
            ),

            const SizedBox(height: 20),

            // 日志区
            Expanded(
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(8),
                color: Colors.grey[200],
                child: SingleChildScrollView(child: Text(_logText)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
