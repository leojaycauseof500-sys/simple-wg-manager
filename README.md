# WireGuard Admin Panel

一个基于 Spring Boot 的 WireGuard 管理面板，提供 Web 界面和 MCP Server 来监控和管理 WireGuard VPN 服务。

## 功能特性

### Web 管理界面
- ✅ WireGuard 服务状态监控
- ✅ 内核模块加载状态检查
- ✅ 进程运行状态检查
- ✅ 网络接口状态检查
- ✅ 详细的 WireGuard 配置信息查看
- ✅ 多语言支持（中文/英文）

### MCP (Model Context Protocol) Server
- ✅ 完整的 MCP 协议支持（JSON-RPC over HTTP/SSE）
- ✅ 15个 WireGuard 管理工具
- ✅ 状态监控工具：获取服务状态、详细信息和统计
- ✅ 客户端管理工具：添加、删除、配置客户端
- ✅ 系统信息工具：仪表板统计、流量统计、运行时间
- ✅ 配置管理工具：配置文件内容、服务重启

## 系统要求

- Java 21+
- Spring Boot 4.x
- WireGuard 已安装

## 安装与配置

### 1. 克隆项目
```bash
git clone <repository-url>
cd simplewgad
```

### 2. 构建项目
```bash
./run.sh build
```

### 3. 配置 sudoers 权限（重要！）

由于 WireGuard 管理需要特权命令，需要配置 sudoers 文件以允许应用无密码执行特定命令：

```bash
# 编辑 sudoers 文件
sudo visudo

# 在文件末尾添加以下内容（根据您的运行用户调整）
# 如果使用默认的 Spring Boot 运行用户，通常是当前用户
# 将 'yourusername' 替换为实际运行应用的用户名

yourusername ALL=(ALL) NOPASSWD: /usr/bin/wg *
yourusername ALL=(ALL) NOPASSWD: /usr/bin/wg-quick *

yourusername ALL=(ALL) NOPASSWD: /usr/bin/tee data/*
yourusername ALL=(ALL) NOPASSWD: /usr/bin/cat data/*

```

### 4. 修改应用配置

编辑 `src/main/resources/application.yml`，根据需要调整配置：

```yaml
wg-manager:
  default:
    interface-name: wg0 #管理页面在启动时会去查找这个接口的信息
    server-private-key: your_private_key #可以手动指定
    listen-port: 51280
    #    bash-path: /opt/homebrew/bin/bash #wg-quick似乎需要4.0往上,部分主机默认的bash可能版本过低,可以手动指定一下
    bash-path: /usr/bin/bash

```

### 5. 运行应用

```bash
# 开发环境
./run.sh dev

# 生产环境
./run.sh prod

### 6. MCP Server 使用

应用启动后，MCP Server 将在以下端点提供服务：

#### SSE 端点（流式传输）
```
GET http://localhost:8080/mcp
```

#### JSON-RPC 端点
```
POST http://localhost:8080/mcp
Content-Type: application/json
```

#### 支持的 MCP 方法
1. `initialize` - 初始化连接，获取服务器信息
2. `tools/list` - 获取可用工具列表（15个工具）
3. `tools/call` - 调用指定工具

#### 工具清单
- **状态监控**：`get_wireguard_status`, `get_wireguard_details`, `get_wireguard_statistics`, `get_interface_config`, `get_server_config`
- **客户端管理**：`add_client`, `delete_client`, `get_client_config`, `get_online_clients`
- **系统信息**：`get_system_info`, `get_dashboard_statistics`, `get_traffic_statistics`, `get_uptime_statistics`
- **配置管理**：`get_config_file_content`, `restart_wireguard_service`

#### 使用示例
```json
// 初始化
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}

// 获取工具列表
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}

// 调用工具：获取 WireGuard 状态
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_wireguard_status",
    "arguments": {}
  }
}

// 调用工具：添加客户端
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

详细文档请参考 [MCP_IMPLEMENTATION.md](./MCP_IMPLEMENTATION.md)

## 故障排除

### 1. 权限错误
如果遇到权限错误，检查：
- sudoers 配置是否正确
- 应用运行用户是否匹配
- 命令路径是否正确

### 2. WireGuard 命令不可用
确保 WireGuard 已正确安装：
```bash
# 检查 WireGuard 安装
wg --version

# 安装 WireGuard（Ubuntu/Debian）
sudo apt update
sudo apt install wireguard wireguard-tools

# 安装 WireGuard（CentOS/RHEL）
sudo yum install epel-release
sudo yum install wireguard-tools
```

### 3. 应用无法启动
检查：
- Java 版本是否符合要求
- 端口是否被占用
- 配置文件格式是否正确

### 4. MCP Server 连接问题
如果 MCP 客户端无法连接：
- 检查应用是否正常运行：`curl http://localhost:8080/mcp`
- 验证 SSE 端点：`curl -N http://localhost:8080/mcp`
- 检查 JSON-RPC 端点：`curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'`
- 确保防火墙允许 8080 端口访问

## 构建与测试

### 构建项目
```bash
# 完整构建（包括测试）
./gradlew build

# 仅编译
./gradlew compileKotlin

# 清理并重新构建
./gradlew clean build
```

### 运行测试
```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests "com.leojay.simplewgad.util.WgEntryParserTest"

# 运行测试并生成覆盖率报告
./gradlew jacocoTestReport
# 报告位置：build/reports/jacoco/jacocoTestReport/html/index.html
```

### 开发服务器
```bash
# 启动开发服务器
./gradlew bootRun

# 或使用脚本
./run.sh dev
```

## 技术栈

- **后端框架**: Spring Boot 4.x + Kotlin
- **前端**: Thymeleaf + HTML/CSS
- **构建工具**: Gradle
- **Java版本**: 21+
- **协议支持**: MCP (Model Context Protocol), JSON-RPC 2.0, SSE

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request 来改进本项目。

## 相关文档

- [MCP 实现文档](./MCP_IMPLEMENTATION.md) - 详细的 MCP Server 使用说明

