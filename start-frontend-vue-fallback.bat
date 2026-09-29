@echo off
chcp 65001 >nul
echo ========================================================
echo   高校实习全过程管理系统 - 前端服务 (Vue 3 回退版本)
echo   访问地址: http://localhost:3000
echo   后端反向代理: http://localhost:8080
echo ========================================================
cd /d "%~dp0frontend"
npm.cmd run dev
