# MCP Server 实现文档

## 概述
已为simple-wg-manager实现MCP (Model Context Protocol) Server功能，支持通过JSON-RPC over HTTP/SSE提供WireGuard管理工具。

## 实现的接口

### 1. SSE端点
- `GET /mcp` - Server-Sent Events端点，用于流式传输

### 2. JSON-RPC端点
- `POST /mcp` - 处理所有MCP请求

## 支持的MCP方法

### 1. `initialize`
- 返回服务器信息和能力
- 设置`listChanged: true`表示tools列表可动态获取

### 2. `tools/list`
- 返回所有可用的tools列表（15个工具）

### 3. `tools/call`
- 调用指定的tool并返回结果

## 实现的Tools清单

### 状态监控类 (Category 1)
1. `get_wireguard_status` - 获取WireGuard服务状态（运行/停止/错误）
2. `get_wireguard_details` - 获取详细的WireGuard状态信息
3. `get_wireguard_statistics` - 获取WireGuard接口统计信息
4. `get_interface_config` - 获取接口配置信息
5. `get_server_config` - 获取服务器完整配置信息

### 客户端管理类 (Category 2)
6. `add_client` - 添加新客户端（参数：clientName, allowedIps）
7. `delete_client` - 删除客户端（参数：clientUuid）
8. `get_client_config` - 获取客户端配置（参数：clientUuid）
9. `get_online_clients` - 获取在线客户端列表

### 配置管理类
10. `get_config_file_content` - 获取配置文件内容（参数：interfaceName）
11. `restart_wireguard_service` - 重启WireGuard服务

### 系统信息类 (Category 4)
12. `get_system_info` - 获取系统信息（内核版本、WireGuard版本等）
13. `get_dashboard_statistics` - 获取仪表板统计数据
14. `get_traffic_statistics` - 获取流量统计信息
15. `get_uptime_statistics` - 获取运行时间统计

## 技术实现

### 文件位置
- `src/main/kotlin/com/leojay/simplewgad/controller/McpController.kt` - MCP控制器

### 依赖注入
- `WireGuardService` - WireGuard管理服务
- `StatisticsService` - 统计服务

### 错误处理
- 使用`Result<T>`类型进行错误处理
- 统一的JSON-RPC错误响应格式
- 详细的错误信息和时间戳

## 使用示例

### 1. 初始化连接
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}
```

### 2. 获取tools列表
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

### 3. 调用tool
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_wireguard_status",
    "arguments": {}
  }
}
```

### 4. 添加客户端
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "add_client",
    "arguments": {
      "clientName": "new-client",
      "allowedIps": "10.0.0.2/32"
    }
  }
}
```

## 构建和测试

### 构建命令
```bash
./gradlew build
```

### 运行服务器
```bash
./gradlew bootRun
```

### 测试命令
```bash
./gradlew test
```

## 下一步改进建议

1. **添加工具调用日志** - 记录所有tool调用
2. **实现权限控制** - 基于角色的访问控制
3. **添加WebSocket支持** - 双向实时通信
4. **实现工具调用限制** - 频率限制和配额管理
5. **添加工具调用历史** - 记录和查询历史调用
6. **实现工具调用验证** - 参数验证和输入清理
7. **添加工具调用监控** - 性能监控和告警
8. **实现工具调用缓存** - 缓存频繁调用的结果
9. **添加工具调用批处理** - 支持批量调用多个工具
10. **实现工具调用依赖** - 工具之间的依赖关系管理