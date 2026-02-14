# WireGuard Admin Panel

一个基于 Spring Boot 的 WireGuard 管理面板，提供 Web 界面来监控和管理 WireGuard VPN 服务。

## 功能特性

- ✅ WireGuard 服务状态监控
- ✅ 内核模块加载状态检查
- ✅ 进程运行状态检查
- ✅ 网络接口状态检查
- ✅ 详细的 WireGuard 配置信息查看
- ✅ 多语言支持（中文/英文）
- ✅ 响应式 Web 界面

## 系统要求

- Java 17+
- Spring Boot 3.x
- WireGuard 已安装
- Linux 系统（支持 systemd）

## 安装与配置

### 1. 克隆项目
```bash
git clone <repository-url>
cd simplewgad
```

### 2. 构建项目
```bash
./gradlew build
```

### 3. 配置 sudoers 权限（重要！）

由于 WireGuard 管理需要特权命令，需要配置 sudoers 文件以允许应用无密码执行特定命令：

```bash
# 编辑 sudoers 文件
sudo visudo

# 在文件末尾添加以下内容（根据您的运行用户调整）
# 如果使用默认的 Spring Boot 运行用户，通常是当前用户
# 将 'yourusername' 替换为实际运行应用的用户名
yourusername ALL=(ALL) NOPASSWD: /usr/bin/wg show
yourusername ALL=(ALL) NOPASSWD: /usr/bin/wg-quick *
yourusername ALL=(ALL) NOPASSWD: /usr/bin/ip link show type wireguard
yourusername ALL=(ALL) NOPASSWD: /usr/bin/ip -o link show type wireguard
yourusername ALL=(ALL) NOPASSWD: /bin/lsmod
yourusername ALL=(ALL) NOPASSWD: /usr/bin/pgrep -x wg-quick

# 或者，如果您希望更严格的控制，可以创建一个专用脚本
```

### 4. 创建专用脚本（可选但推荐）

为了更好的安全性，可以创建一个专用脚本：

```bash
# 创建脚本目录
sudo mkdir -p /usr/local/bin/wireguard-admin

# 创建状态检查脚本
sudo tee /usr/local/bin/wireguard-admin/status.sh << 'EOF'
#!/bin/bash
case "$1" in
    "show")
        /usr/bin/wg show
        ;;
    "interface")
        /usr/bin/ip -o link show type wireguard
        ;;
    "process")
        /usr/bin/pgrep -x wg-quick
        ;;
    "module")
        /bin/lsmod | grep wireguard
        ;;
    *)
        echo "Unknown command"
        exit 1
        ;;
esac
EOF

# 设置权限
sudo chmod 755 /usr/local/bin/wireguard-admin/status.sh
sudo chown root:root /usr/local/bin/wireguard-admin/status.sh

# 然后在 sudoers 中只允许这个脚本
# yourusername ALL=(ALL) NOPASSWD: /usr/local/bin/wireguard-admin/status.sh
```

### 5. 修改应用配置

编辑 `src/main/resources/application.yml`，根据需要调整配置：

```yaml
wg-manager:
  default:
    interface: wg0
    config-path: /etc/wireguard/
    
server:
  port: 8080
```

### 6. 运行应用

```bash
# 开发环境
./gradlew bootRun

# 生产环境
java -jar build/libs/simplewgad-*.jar

# 或者使用 systemd 服务
sudo cp docs/simplewgad.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable simplewgad
sudo systemctl start simplewgad
```

## 安全注意事项

### 1. 最小权限原则
- 只授予应用执行必要命令的权限
- 避免使用 ALL=(ALL) NOPASSWD: ALL 这样的宽泛权限
- 定期审查 sudoers 配置

### 2. 命令注入防护
- 应用代码中对命令参数进行了验证
- 使用参数化命令执行
- 避免直接拼接用户输入到命令中

### 3. 日志记录
- 所有特权命令执行都会被记录
- 定期检查应用日志
- 监控异常命令执行

### 4. 网络隔离
- 建议在内部网络运行管理面板
- 使用防火墙限制访问
- 启用 HTTPS

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

## 开发指南

### 项目结构
```
src/main/kotlin/com/leojay/simplewgad/
├── controller/     # Web 控制器
├── service/       # 业务逻辑
├── util/          # 工具类
├── config/        # 配置类
├── model/         # 数据模型
└── component/     # 组件
```

### 添加新功能
1. 在 `WireGuardService` 中添加业务逻辑
2. 创建相应的控制器端点
3. 更新前端界面
4. 添加国际化支持

### 测试
```bash
# 运行单元测试
./gradlew test

# 运行集成测试
./gradlew integrationTest
```

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 LICENSE 文件了解详情。

## 支持

如有问题，请：
1. 查看本文档
2. 检查 Issues 页面
3. 创建新的 Issue 描述问题
