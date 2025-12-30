import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'main.dart';

class ProgramManagerScreen extends StatefulWidget {
  const ProgramManagerScreen({super.key});

  @override
  State<ProgramManagerScreen> createState() => _ProgramManagerScreenState();
}

class _ProgramManagerScreenState extends State<ProgramManagerScreen> {
  Map<String, Map<String, dynamic>> _programs = {};
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadPrograms();
  }

  Future<void> _loadPrograms() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final prefs = await SharedPreferences.getInstance();
      final keys = prefs.getKeys();
      
      Map<String, Map<String, dynamic>> programs = {};
      for (var key in keys) {
        if (key.startsWith('program_')) {
          String programName = key.substring(8); // 去掉 "program_" 前缀
          String? jsonStr = prefs.getString(key);
          if (jsonStr != null && jsonStr.isNotEmpty) {
            try {
              programs[programName] = json.decode(jsonStr);
            } catch (e) {
              // 跳过无效的 JSON
              continue;
            }
          }
        }
      }

      setState(() {
        _programs = programs;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('加载节目列表失败: $e')),
        );
      }
    }
  }

  Future<void> _deleteProgram(String programName) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('确认删除'),
        content: Text('确定要删除节目 "$programName" 吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: Text('删除'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        final prefs = await SharedPreferences.getInstance();
        await prefs.remove('program_$programName');
        
        setState(() {
          _programs.remove(programName);
        });

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('已删除节目: $programName')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('删除失败: $e')),
          );
        }
      }
    }
  }

  void _loadProgram(String programName) {
    final programData = _programs[programName];
    if (programData == null) return;

    // 返回到主页面，并传递节目数据
    Navigator.pop(context, programData);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('节目管理'),
        actions: [
          IconButton(
            icon: Icon(Icons.refresh),
            onPressed: _loadPrograms,
            tooltip: '刷新',
          ),
        ],
      ),
      body: _isLoading
          ? Center(child: CircularProgressIndicator())
          : _programs.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.folder_open, size: 64, color: Colors.grey),
                      SizedBox(height: 16),
                      Text(
                        '暂无保存的节目',
                        style: TextStyle(color: Colors.grey, fontSize: 16),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: EdgeInsets.all(12),
                  itemCount: _programs.length,
                  itemBuilder: (context, index) {
                    final programName = _programs.keys.elementAt(index);
                    final programData = _programs[programName]!;
                    final time = programData['time'] as String? ?? '未知时间';
                    final windowCount = (programData['windows'] as List?)?.length ?? 0;

                    return Card(
                      margin: EdgeInsets.only(bottom: 10),
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: Colors.blue,
                          child: Icon(Icons.article, color: Colors.white),
                        ),
                        title: Text(
                          programName,
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                        subtitle: Text(
                          '$time\n包含 $windowCount 个窗口',
                        ),
                        isThreeLine: true,
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: Icon(Icons.file_open, color: Colors.blue),
                              onPressed: () => _loadProgram(programName),
                              tooltip: '加载',
                            ),
                            IconButton(
                              icon: Icon(Icons.delete, color: Colors.red),
                              onPressed: () => _deleteProgram(programName),
                              tooltip: '删除',
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
    );
  }
}

