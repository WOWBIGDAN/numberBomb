#Requires -Version 5.1
[CmdletBinding()]
param(
	[string]$HostIp = "0.0.0.0",
	[int]$Port = 8889,
	[string]$PythonExe = "python"
)

# 设置终端使用UTF-8以避免中文乱码
$OutputEncoding = [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding  = New-Object System.Text.UTF8Encoding($false)

Write-Host "启动 Python 数字炸弹服务器..." -ForegroundColor Green

# 检查 Python 是否可用
$pythonPath = (Get-Command $PythonExe -ErrorAction SilentlyContinue).Path
if (-not $pythonPath) {
	Write-Error "未找到 Python，请先安装 Python 3.9+ 并配置 PATH。"
	exit 1
}

# 安装依赖（当前无第三方依赖，此步骤可跳过失败不退出）
try {
	& $PythonExe -m pip install -r requirements.txt | Out-Host
} catch {
	Write-Warning "依赖安装过程中出现问题，可忽略（当前仅标准库）。"
}

# 启动服务器
& $PythonExe server.py --host $HostIp --port $Port
