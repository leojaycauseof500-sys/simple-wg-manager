#!/bin/bash

# ============================================
# simple-wg-manager 精简运行脚本
# 支持开发/生产环境，使用 java -jar 运行
# ============================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
DEFAULT_ENV="dev"
DEFAULT_PORT="8080"
JAVA_VERSION="21"
OBJECT_VERSION="1.0.0"

# 目录和文件路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
LIB_DIR="$BUILD_DIR/libs"

# ============================================
# 工具函数
# ============================================

print_header() {
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}============================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# ============================================
# 核心功能函数
# ============================================

build_project() {
    print_header "构建项目"
    
    if ./gradlew build; then
        print_success "项目构建成功"
        
        # 查找生成的 JAR 文件
        local jar_file
        jar_file=$(find "$LIB_DIR" -name "*.jar" -type f | head -n 1)
        
        if [[ -n "$jar_file" ]]; then
            print_success "JAR 文件: $(basename "$jar_file")"
            print_info "位置: $jar_file"
            print_info "大小: $(du -h "$jar_file" | cut -f1)"
        fi
        
        return 0
    else
        print_error "项目构建失败"
        return 1
    fi
}

clean_build() {
    print_header "清理并构建"
    
    if ./gradlew clean build; then
        print_success "清理构建成功"
        return 0
    else
        print_error "清理构建失败"
        return 1
    fi
}

run_tests() {
    print_header "运行测试"
    
    if ./gradlew test; then
        print_success "测试通过"
        return 0
    else
        print_error "测试失败"
        return 1
    fi
}

find_jar_file() {
    local jar_file
    
    # 优先查找可执行的 Spring Boot JAR（不包含 -plain 后缀）
    jar_file=$(find "$LIB_DIR" -name "simple-wg-ad-$OBJECT_VERSION.jar" -type f ! -name "*-plain.jar" | head -n 1)
    
    # 如果没找到，再查找任何 JAR 文件
    if [[ -z "$jar_file" ]]; then
        jar_file=$(find "$LIB_DIR" -name "*.jar" -type f | head -n 1)
    fi
    
    if [[ -z "$jar_file" ]]; then
        print_error "未找到 JAR 文件，请先运行构建命令"
        return 1
    fi
    
    echo "$jar_file"
    return 0
}

run_jar() {
    local env="${1:-$DEFAULT_ENV}"
    local jar_file
    local java_args=""
    
    # 查找 JAR 文件
    jar_file=$(find_jar_file)
    [[ $? -ne 0 ]] && return 1
    
    print_header "启动应用 (环境: $env)"
    
    # 设置环境参数
    case "$env" in
        dev)
            java_args="-Dspring.profiles.active=dev"
            print_info "使用开发环境配置"
            print_info "访问地址: http://localhost:$DEFAULT_PORT"
            ;;
        prod)
            java_args="-Dspring.profiles.active=prod"
            print_info "使用生产环境配置"
            ;;
        *)
            print_warning "未知环境 '$env'，使用默认配置"
            ;;
    esac
    
    # 添加用户自定义参数
    shift
    if [[ $# -gt 0 ]]; then
        print_info "自定义参数: $*"
        # 将所有剩余参数传递给Java
        java_args="$java_args $*"
    fi
    
    print_info "JAR 文件: $(basename "$jar_file")"
    print_info "Java 参数: $java_args"
    echo ""
    print_warning "按 Ctrl+C 停止应用"
    echo ""
    
    # 运行应用
    java $java_args -jar "$jar_file"
    
    if [[ $? -eq 0 ]]; then
        print_success "应用已停止"
        return 0
    else
        print_error "应用启动失败"
        return 1
    fi
}

run_dev() {
    run_jar "dev" "$@"
}

run_prod() {
    run_jar "prod" "$@"
}

show_help() {
    print_header "simple-wg-manager 运行脚本"
    
    echo -e "用法: ${GREEN}./run.sh [命令] [参数]${NC}"
    echo ""
    echo -e "核心命令:"
    echo -e "  ${GREEN}build${NC}                构建项目"
    echo -e "  ${GREEN}clean${NC}                清理并构建"
    echo -e "  ${GREEN}test${NC}                 运行测试"
    echo -e "  ${GREEN}run [环境] [参数]${NC}    运行应用"
    echo -e "  ${GREEN}dev [参数]${NC}           开发环境运行"
    echo -e "  ${GREEN}prod [参数]${NC}          生产环境运行"
    echo ""
    echo -e "环境选项:"
    echo -e "  ${BLUE}dev${NC}    - 开发环境 (默认)"
    echo -e "  ${BLUE}prod${NC}   - 生产环境"
    echo ""
    echo -e "运行参数示例:"
    echo -e "  ${BLUE}--server.port=9090${NC}         指定端口"
    echo -e "  ${BLUE}-Dlogging.level.root=DEBUG${NC} 设置日志级别"
    echo -e "  ${BLUE}-Dwg-manager.default.server-private-key=xxx${NC} 设置WireGuard私钥"
    echo ""
    echo -e "使用示例:"
    echo -e "  ${BLUE}./run.sh build${NC}              # 构建项目"
    echo -e "  ${BLUE}./run.sh run dev${NC}            # 开发环境运行"
    echo -e "  ${BLUE}./run.sh run prod${NC}           # 生产环境运行"
    echo -e "  ${BLUE}./run.sh dev --server.port=9090${NC}  # 开发环境指定端口"
    echo -e "  ${BLUE}./run.sh prod${NC}               # 生产环境运行"
    echo ""
    echo -e "快捷命令:"
    echo -e "  ${BLUE}./run.sh dev${NC}                # 开发环境运行"
    echo -e "  ${BLUE}./run.sh prod${NC}               # 生产环境运行"
    echo ""
    echo -e "环境要求: Java $JAVA_VERSION+"
}

# ============================================
# 主程序
# ============================================

main() {
    local command="${1:-help}"
    
    # 确保在项目根目录执行
    cd "$SCRIPT_DIR"
    
    case "$command" in
        build)
            build_project
            ;;
        clean)
            clean_build
            ;;
        test)
            run_tests
            ;;
        run)
            shift
            run_jar "$@"
            ;;
        dev)
            shift
            run_dev "$@"
            ;;
        prod)
            shift
            run_prod "$@"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "未知命令: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
