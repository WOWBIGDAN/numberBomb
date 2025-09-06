@echo off
echo 启动 Python 数字炸弹服务器...
set PYTHON_EXE=python

where %PYTHON_EXE% >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
	echo 未找到 Python，请先安装 Python 3.9+ 并配置 PATH。
	pause
	exit /b 1
)

%PYTHON_EXE% -m pip install -r requirements.txt
if %ERRORLEVEL% NEQ 0 (
	echo 依赖安装失败。
	pause
	exit /b 1
)

%PYTHON_EXE% server.py --host 0.0.0.0 --port 8888
pause
