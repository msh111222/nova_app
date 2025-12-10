import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';
import 'dart:math';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const NovaEditorScreen(),
    );
  }
}

enum ContentType { text, image, video }

class WindowItem {
  String id;
  ContentType type;
  int x, y, w, h;
  String text;
  String fontFamily;
  int fontSize;
  Color fontColor;
  String fontStyle;
  String scrollDirection;
  double scrollSpeed;
  bool isHeadTail;
  bool isStatic;
  int letterSpacing;
  int lineSpacing;
  Color fontBgColor;
  Color windowBgColor;
  String filePath;
  String fileName;

  WindowItem({
    required this.id,
    required this.type,
    this.x = 0,
    this.y = 0,
    this.w = 64,
    this.h = 32,
    this.text = "新文字",
    this.fontFamily = "Arial",
    this.fontSize = 20,
    this.fontColor = Colors.red,
    this.fontStyle = "NORMAL",
    this.scrollDirection = "MARQUEE_LEFT",
    this.scrollSpeed = 3.0,
    this.isHeadTail = false,
    this.isStatic = false,
    this.letterSpacing = 0,
    this.lineSpacing = 0,
    this.fontBgColor = Colors.transparent,
    this.windowBgColor = Colors.transparent,
    this.filePath = "",
    this.fileName = "",
  });

  Map<String, dynamic> toMap() {
    return {
      "id": id,
      "type": type.name,
      "x": x,
      "y": y,
      "w": w,
      "h": h,
      "text": text,
      "fontFamily": fontFamily,
      "fontSize": fontSize,
      "fontColor": '#ff${fontColor.value.toRadixString(16).substring(2)}',
      "fontStyle": fontStyle,
      "scrollDirection": isStatic ? "STATIC" : scrollDirection,
      "scrollSpeed": scrollSpeed,
      "isHeadTail": isHeadTail,
      "isStatic": isStatic,
      "letterSpacing": letterSpacing,
      "lineSpacing": lineSpacing,
      "fontBgColor": '#${fontBgColor.value.toRadixString(16).padLeft(8, '0')}',
      "windowBgColor":
          '#${windowBgColor.value.toRadixString(16).padLeft(8, '0')}',
      "filePath": filePath,
      "fileName": fileName,
    };
  }
}

class NovaEditorScreen extends StatefulWidget {
  const NovaEditorScreen({super.key});
  @override
  State<NovaEditorScreen> createState() => _NovaEditorScreenState();
}

class _NovaEditorScreenState extends State<NovaEditorScreen> {
  static const platform = MethodChannel('com.novastar/bridge');

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

  final int _ledWidth = 128;
  final int _ledHeight = 64;

  List<WindowItem> _windows = [];
  String? _selectedWindowId;

  WindowItem? get _selectedWindow {
    if (_selectedWindowId == null) return null;
    try {
      return _windows.firstWhere((w) => w.id == _selectedWindowId);
    } catch (e) {
      return null;
    }
  }

  String _generateId() {
    return DateTime.now().millisecondsSinceEpoch.toString() +
        Random().nextInt(1000).toString();
  }

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

  // 测试原版发送文字（只传 sn 和 text）
  Future<void> _testPublishText() async {
    setState(() {
      _isConnecting = true;
      _logText = "测试原版发送文字...";
    });

    try {
      final String result = await platform.invokeMethod('publishText', {
        "sn": _snController.text,
        "text": "测试文字",
      });

      setState(() {
        _logText = "✅ 原版发送成功: $result";
      });
    } catch (e) {
      setState(() {
        _logText = "❌ 原版发送失败: $e";
      });
    } finally {
      setState(() {
        _isConnecting = false;
      });
    }
  }

  void _addTextWindow() {
    setState(() {
      final newWindow = WindowItem(
        id: _generateId(),
        type: ContentType.text,
        x: 0,
        y: 0,
        w: _ledWidth,
        h: _ledHeight ~/ 2,
        text: "新文字${_windows.length + 1}",
      );
      _windows.add(newWindow);
      _selectedWindowId = newWindow.id;
    });
  }

  Future<void> _addImageWindow() async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.image,
      );
      if (result == null || result.files.single.path == null) return;

      String filePath = result.files.single.path!;
      String fileName = result.files.single.name;

      setState(() {
        final newWindow = WindowItem(
          id: _generateId(),
          type: ContentType.image,
          x: 0,
          y: 0,
          w: _ledWidth,
          h: _ledHeight ~/ 2,
          filePath: filePath,
          fileName: fileName,
        );
        _windows.add(newWindow);
        _selectedWindowId = newWindow.id;
      });
    } catch (e) {
      setState(() {
        _logText = "❌ 选择图片失败: $e";
      });
    }
  }

  Future<void> _addVideoWindow() async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.video,
      );
      if (result == null || result.files.single.path == null) return;

      String filePath = result.files.single.path!;
      String fileName = result.files.single.name;

      setState(() {
        final newWindow = WindowItem(
          id: _generateId(),
          type: ContentType.video,
          x: 0,
          y: 0,
          w: _ledWidth,
          h: _ledHeight ~/ 2,
          filePath: filePath,
          fileName: fileName,
        );
        _windows.add(newWindow);
        _selectedWindowId = newWindow.id;
      });
    } catch (e) {
      setState(() {
        _logText = "❌ 选择视频失败: $e";
      });
    }
  }

  void _deleteSelectedWindow() {
    if (_selectedWindowId == null) return;
    setState(() {
      _windows.removeWhere((w) => w.id == _selectedWindowId);
      _selectedWindowId = _windows.isNotEmpty ? _windows.last.id : null;
    });
  }

  Future<void> _publishProgram() async {
    if (_windows.isEmpty) {
      setState(() {
        _logText = "⚠️ 请先添加至少一个内容窗口";
      });
      return;
    }

    FocusScope.of(context).unfocus();
    setState(() {
      _isConnecting = true;
      _logText = "正在发送节目...\n共 ${_windows.length} 个窗口";
    });

    try {
      List<Map<String, dynamic>> windowsData = _windows
          .map((w) => w.toMap())
          .toList();

      final String result = await platform.invokeMethod('publishMultiWindow', {
        "sn": _snController.text,
        "ledWidth": _ledWidth,
        "ledHeight": _ledHeight,
        "windows": windowsData,
      });

      setState(() {
        _logText = "🎉 发送成功: $result";
      });
    } catch (e) {
      setState(() {
        _logText = "❌ 发送失败: $e";
      });
    } finally {
      setState(() {
        _isConnecting = false;
      });
    }
  }

  void _pickColor(String type) {
    if (_selectedWindow == null) return;
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text("选择颜色"),
          content: Wrap(
            spacing: 10,
            children:
                [
                  Colors.red,
                  Colors.green,
                  Colors.blue,
                  Colors.yellow,
                  Colors.orange,
                  Colors.purple,
                  Colors.white,
                  Colors.black,
                  Colors.cyan,
                  Colors.pink,
                  Colors.transparent,
                ].map((color) {
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        if (type == "font") _selectedWindow!.fontColor = color;
                        if (type == "fontBg")
                          _selectedWindow!.fontBgColor = color;
                        if (type == "windowBg")
                          _selectedWindow!.windowBgColor = color;
                      });
                      Navigator.pop(context);
                    },
                    child: Container(
                      width: 40,
                      height: 40,
                      margin: EdgeInsets.all(5),
                      decoration: BoxDecoration(
                        color: color,
                        border: Border.all(color: Colors.grey),
                        borderRadius: BorderRadius.circular(5),
                      ),
                    ),
                  );
                }).toList(),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("NovaStar 节目编辑器")),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildLoginSection(),
            Divider(height: 20),
            Text(
              "【节目画布】 LED: ${_ledWidth}x${_ledHeight}",
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            SizedBox(height: 8),
            _buildLedCanvas(),
            SizedBox(height: 10),
            _buildAddButtons(),
            Divider(height: 20),
            _buildWindowList(),
            Divider(height: 20),
            if (_selectedWindow != null) _buildPropertyEditor(),
            Divider(height: 20),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                onPressed:
                    (_isLoggedIn && !_isConnecting && _windows.isNotEmpty)
                    ? _publishProgram
                    : null,
                style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                child: Text(
                  "📤 发送节目 (${_windows.length} 个窗口)",
                  style: TextStyle(fontSize: 16),
                ),
              ),
            ),
            SizedBox(height: 15),
            Container(
              width: double.infinity,
              height: 120,
              padding: EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.grey[200],
                borderRadius: BorderRadius.circular(5),
              ),
              child: SingleChildScrollView(child: Text(_logText)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLoginSection() {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _snController,
                decoration: InputDecoration(labelText: "SN", isDense: true),
              ),
            ),
            SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: _userController,
                decoration: InputDecoration(labelText: "User", isDense: true),
              ),
            ),
            SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: _passController,
                decoration: InputDecoration(labelText: "Pass", isDense: true),
                obscureText: true,
              ),
            ),
          ],
        ),
        SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _isConnecting ? null : _connectToNova,
            child: Text(_isLoggedIn ? "✅ 已登录" : "登录"),
          ),
        ),
      ],
    );
  }

  Widget _buildLedCanvas() {
    return LayoutBuilder(
      builder: (context, constraints) {
        double maxCanvasWidth = constraints.maxWidth;
        double aspectRatio = _ledWidth / _ledHeight;
        double canvasWidth = maxCanvasWidth;
        double canvasHeight = canvasWidth / aspectRatio;

        if (canvasHeight > 200) {
          canvasHeight = 200;
          canvasWidth = canvasHeight * aspectRatio;
        }

        double scale = canvasWidth / _ledWidth;

        return Center(
          child: Container(
            width: canvasWidth,
            height: canvasHeight,
            decoration: BoxDecoration(
              color: Colors.black,
              border: Border.all(color: Colors.grey, width: 2),
            ),
            child: Stack(
              children: _windows.map((window) {
                return _buildWindowWidget(
                  window,
                  scale,
                  canvasWidth,
                  canvasHeight,
                );
              }).toList(),
            ),
          ),
        );
      },
    );
  }

  Widget _buildWindowWidget(
    WindowItem window,
    double scale,
    double canvasWidth,
    double canvasHeight,
  ) {
    bool isSelected = window.id == _selectedWindowId;
    double boxLeft = window.x * scale;
    double boxTop = window.y * scale;
    double boxWidth = window.w * scale;
    double boxHeight = window.h * scale;

    Color borderColor;
    Color bgColor;
    IconData typeIcon;

    switch (window.type) {
      case ContentType.text:
        borderColor = Colors.blue;
        bgColor = Colors.blue.withOpacity(0.3);
        typeIcon = Icons.text_fields;
        break;
      case ContentType.image:
        borderColor = Colors.green;
        bgColor = Colors.green.withOpacity(0.3);
        typeIcon = Icons.image;
        break;
      case ContentType.video:
        borderColor = Colors.orange;
        bgColor = Colors.orange.withOpacity(0.3);
        typeIcon = Icons.videocam;
        break;
    }

    return Positioned(
      left: boxLeft,
      top: boxTop,
      child: GestureDetector(
        onTap: () {
          setState(() {
            _selectedWindowId = window.id;
          });
        },
        onPanUpdate: (details) {
          setState(() {
            _selectedWindowId = window.id;
            double newLeft = boxLeft + details.delta.dx;
            double newTop = boxTop + details.delta.dy;
            newLeft = newLeft.clamp(0, canvasWidth - boxWidth);
            newTop = newTop.clamp(0, canvasHeight - boxHeight);
            window.x = (newLeft / scale).round().clamp(0, _ledWidth - window.w);
            window.y = (newTop / scale).round().clamp(0, _ledHeight - window.h);
          });
        },
        child: Container(
          width: boxWidth,
          height: boxHeight,
          decoration: BoxDecoration(
            color: bgColor,
            border: Border.all(
              color: isSelected ? Colors.yellow : borderColor,
              width: isSelected ? 3 : 2,
            ),
          ),
          child: Stack(
            children: [
              Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(typeIcon, color: Colors.white, size: 16),
                    SizedBox(height: 2),
                    Text(
                      window.type == ContentType.text
                          ? window.text
                          : window.fileName,
                      style: TextStyle(color: Colors.white, fontSize: 10),
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
              if (isSelected)
                Positioned(
                  right: 0,
                  bottom: 0,
                  child: GestureDetector(
                    onPanUpdate: (details) {
                      setState(() {
                        double newWidth = boxWidth + details.delta.dx;
                        double newHeight = boxHeight + details.delta.dy;
                        double minSize = 10 * scale;
                        newWidth = newWidth.clamp(
                          minSize,
                          canvasWidth - boxLeft,
                        );
                        newHeight = newHeight.clamp(
                          minSize,
                          canvasHeight - boxTop,
                        );
                        window.w = (newWidth / scale).round().clamp(
                          10,
                          _ledWidth - window.x,
                        );
                        window.h = (newHeight / scale).round().clamp(
                          10,
                          _ledHeight - window.y,
                        );
                      });
                    },
                    child: Container(
                      width: 20,
                      height: 20,
                      decoration: BoxDecoration(
                        color: Colors.yellow,
                        border: Border.all(color: Colors.orange, width: 1),
                      ),
                      child: Icon(
                        Icons.open_in_full,
                        size: 12,
                        color: Colors.black,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAddButtons() {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _addTextWindow,
                icon: Icon(Icons.text_fields, size: 18),
                label: Text("文字"),
                style: ElevatedButton.styleFrom(backgroundColor: Colors.blue),
              ),
            ),
            SizedBox(width: 8),
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _addImageWindow,
                icon: Icon(Icons.image, size: 18),
                label: Text("图片"),
                style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
              ),
            ),
            SizedBox(width: 8),
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _addVideoWindow,
                icon: Icon(Icons.videocam, size: 18),
                label: Text("视频"),
                style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
              ),
            ),
          ],
        ),
        SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: (_isLoggedIn && !_isConnecting)
                ? _testPublishText
                : null,
            style: ElevatedButton.styleFrom(backgroundColor: Colors.purple),
            child: Text("🧪 测试原版发送文字"),
          ),
        ),
      ],
    );
  }

  Widget _buildWindowList() {
    if (_windows.isEmpty) {
      return Container(
        padding: EdgeInsets.all(20),
        alignment: Alignment.center,
        child: Text("暂无内容，请点击上方按钮添加", style: TextStyle(color: Colors.grey)),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text("【内容列表】", style: TextStyle(fontWeight: FontWeight.bold)),
        SizedBox(height: 8),
        ..._windows.asMap().entries.map((entry) {
          int index = entry.key;
          WindowItem window = entry.value;
          bool isSelected = window.id == _selectedWindowId;

          String typeName = window.type == ContentType.text
              ? "文字"
              : window.type == ContentType.image
              ? "图片"
              : "视频";
          String content = window.type == ContentType.text
              ? window.text
              : window.fileName;

          return Container(
            margin: EdgeInsets.only(bottom: 5),
            decoration: BoxDecoration(
              color: isSelected
                  ? Colors.blue.withOpacity(0.1)
                  : Colors.grey[100],
              border: Border.all(
                color: isSelected ? Colors.blue : Colors.grey[300]!,
              ),
              borderRadius: BorderRadius.circular(5),
            ),
            child: ListTile(
              dense: true,
              leading: Text(
                "${index + 1}",
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              title: Text(
                "[$typeName] $content",
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                "位置: (${window.x}, ${window.y}) 大小: ${window.w}x${window.h}",
              ),
              trailing: IconButton(
                icon: Icon(Icons.delete, color: Colors.red),
                onPressed: () {
                  setState(() {
                    _selectedWindowId = window.id;
                  });
                  _deleteSelectedWindow();
                },
              ),
              onTap: () {
                setState(() {
                  _selectedWindowId = window.id;
                });
              },
            ),
          );
        }).toList(),
      ],
    );
  }

  Widget _buildPropertyEditor() {
    final window = _selectedWindow!;

    return Container(
      padding: EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.grey[100],
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.blue),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                "【编辑: ${window.type == ContentType.text
                    ? '文字'
                    : window.type == ContentType.image
                    ? '图片'
                    : '视频'}】",
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              IconButton(
                icon: Icon(Icons.delete, color: Colors.red),
                onPressed: _deleteSelectedWindow,
              ),
            ],
          ),
          SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _buildNumberField(
                  "X",
                  window.x,
                  (v) => window.x = v,
                  0,
                  _ledWidth - window.w,
                ),
              ),
              SizedBox(width: 8),
              Expanded(
                child: _buildNumberField(
                  "Y",
                  window.y,
                  (v) => window.y = v,
                  0,
                  _ledHeight - window.h,
                ),
              ),
              SizedBox(width: 8),
              Expanded(
                child: _buildNumberField(
                  "W",
                  window.w,
                  (v) => window.w = v,
                  10,
                  _ledWidth - window.x,
                ),
              ),
              SizedBox(width: 8),
              Expanded(
                child: _buildNumberField(
                  "H",
                  window.h,
                  (v) => window.h = v,
                  10,
                  _ledHeight - window.y,
                ),
              ),
            ],
          ),
          SizedBox(height: 10),
          if (window.type == ContentType.text) _buildTextPropertyEditor(window),
          if (window.type == ContentType.image)
            _buildImagePropertyEditor(window),
          if (window.type == ContentType.video)
            _buildVideoPropertyEditor(window),
        ],
      ),
    );
  }

  Widget _buildNumberField(
    String label,
    int value,
    Function(int) onChanged,
    int min,
    int max,
  ) {
    return TextField(
      decoration: InputDecoration(labelText: label, isDense: true),
      keyboardType: TextInputType.number,
      controller: TextEditingController(text: value.toString()),
      onChanged: (v) {
        int? parsed = int.tryParse(v);
        if (parsed != null) {
          setState(() {
            onChanged(parsed.clamp(min, max));
          });
        }
      },
    );
  }

  Widget _buildTextPropertyEditor(WindowItem window) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          decoration: InputDecoration(labelText: "文字内容"),
          controller: TextEditingController(text: window.text),
          onChanged: (v) => setState(() => window.text = v),
        ),
        SizedBox(height: 10),
        Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<String>(
                value: window.fontFamily,
                decoration: InputDecoration(labelText: "字体", isDense: true),
                items: ["Arial", "SimSun", "KaiTi", "SimHei", "Microsoft YaHei"]
                    .map(
                      (f) => DropdownMenuItem(
                        value: f,
                        child: Text(f, style: TextStyle(fontSize: 12)),
                      ),
                    )
                    .toList(),
                onChanged: (v) => setState(() => window.fontFamily = v!),
              ),
            ),
            SizedBox(width: 8),
            Expanded(
              child: TextField(
                decoration: InputDecoration(labelText: "字号", isDense: true),
                keyboardType: TextInputType.number,
                controller: TextEditingController(
                  text: window.fontSize.toString(),
                ),
                onChanged: (v) =>
                    setState(() => window.fontSize = int.tryParse(v) ?? 20),
              ),
            ),
          ],
        ),
        SizedBox(height: 10),
        Row(
          children: [
            Text("字色: "),
            GestureDetector(
              onTap: () => _pickColor("font"),
              child: Container(
                width: 30,
                height: 30,
                margin: EdgeInsets.only(right: 15),
                decoration: BoxDecoration(
                  color: window.fontColor,
                  border: Border.all(color: Colors.grey),
                ),
              ),
            ),
            Text("字背景: "),
            GestureDetector(
              onTap: () => _pickColor("fontBg"),
              child: Container(
                width: 30,
                height: 30,
                margin: EdgeInsets.only(right: 15),
                decoration: BoxDecoration(
                  color: window.fontBgColor,
                  border: Border.all(color: Colors.grey),
                ),
              ),
            ),
            Text("窗口背景: "),
            GestureDetector(
              onTap: () => _pickColor("windowBg"),
              child: Container(
                width: 30,
                height: 30,
                decoration: BoxDecoration(
                  color: window.windowBgColor,
                  border: Border.all(color: Colors.grey),
                ),
              ),
            ),
          ],
        ),
        SizedBox(height: 10),
        Row(
          children: [
            Checkbox(
              value: window.isStatic,
              onChanged: (v) => setState(() => window.isStatic = v!),
            ),
            Text("静止"),
            SizedBox(width: 15),
            Checkbox(
              value: window.isHeadTail,
              onChanged: (v) => setState(() => window.isHeadTail = v!),
            ),
            Text("首尾相连"),
          ],
        ),
        if (!window.isStatic)
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: window.scrollDirection,
                  decoration: InputDecoration(labelText: "滚动方向", isDense: true),
                  items:
                      [
                            "MARQUEE_LEFT",
                            "MARQUEE_RIGHT",
                            "MARQUEE_UP",
                            "MARQUEE_DOWN",
                          ]
                          .map(
                            (d) => DropdownMenuItem(
                              value: d,
                              child: Text(d, style: TextStyle(fontSize: 11)),
                            ),
                          )
                          .toList(),
                  onChanged: (v) => setState(() => window.scrollDirection = v!),
                ),
              ),
              SizedBox(width: 8),
              Expanded(
                child: TextField(
                  decoration: InputDecoration(labelText: "速度", isDense: true),
                  keyboardType: TextInputType.number,
                  controller: TextEditingController(
                    text: window.scrollSpeed.toString(),
                  ),
                  onChanged: (v) => setState(
                    () => window.scrollSpeed = double.tryParse(v) ?? 3.0,
                  ),
                ),
              ),
            ],
          ),
      ],
    );
  }

  Widget _buildImagePropertyEditor(WindowItem window) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text("文件: ${window.fileName}", style: TextStyle(fontSize: 14)),
        SizedBox(height: 8),
        ElevatedButton.icon(
          onPressed: () async {
            FilePickerResult? result = await FilePicker.platform.pickFiles(
              type: FileType.image,
            );
            if (result != null && result.files.single.path != null) {
              setState(() {
                window.filePath = result.files.single.path!;
                window.fileName = result.files.single.name;
              });
            }
          },
          icon: Icon(Icons.folder_open),
          label: Text("更换图片"),
        ),
      ],
    );
  }

  Widget _buildVideoPropertyEditor(WindowItem window) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text("文件: ${window.fileName}", style: TextStyle(fontSize: 14)),
        SizedBox(height: 8),
        ElevatedButton.icon(
          onPressed: () async {
            FilePickerResult? result = await FilePicker.platform.pickFiles(
              type: FileType.video,
            );
            if (result != null && result.files.single.path != null) {
              setState(() {
                window.filePath = result.files.single.path!;
                window.fileName = result.files.single.name;
              });
            }
          },
          icon: Icon(Icons.folder_open),
          label: Text("更换视频"),
        ),
      ],
    );
  }
}
