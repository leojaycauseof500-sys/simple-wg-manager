# WireGuard Admin Panel

一个基于 Spring Boot 的 WireGuard 管理面板，提供 Web 界面来监控和管理 WireGuard VPN 服务。

## 功能特性

- ✅ WireGuard 服务状态监控
- ✅ 内核模块加载状态检查
- ✅ 进程运行状态检查
- ✅ 网络接口状态检查
- ✅ 详细的 WireGuard 配置信息查看
- ✅ 多语言支持（中文/英文）

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
yourusername ALL=(ALL) NOPASSWD: /usr/bin/wg *
yourusername ALL=(ALL) NOPASSWD: /usr/bin/wg-quick *
```



### 4. 修改应用配置

编辑 `src/main/resources/application.yml`，根据需要调整配置：

```yaml
wg-manager:
  default:
    interface: wg0
    config-path: /etc/wireguard/
    
server:
  port: 8080
```

### 5. 运行应用

```bash
# 开发环境
./run.sh prod

# 生产环境
./run.sh dev 

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

