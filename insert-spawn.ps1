$ErrorActionPreference = 'Stop'
$path = 'C:\Users\PC\Downloads\CopyPlugin\AnarchyCore-NextGen\src\main\resources\config.yml'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# 读取全部内容（保留 BOM 与否无所谓，用 UTF8 解码）
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# 插入出生点配置段（在 messages: 段之前）
$anchor = '# ===================== 娑堟伅鏂囨湰 ====================='
$spawnSection = @'
# ===================== 出生点随机传送 =====================
spawn:
  enable: true                 # 是否启用首次加入随机出生点
  radius: 500                  # 随机传送半径（格），默认 500，可在配置中调整
  invulnerable-seconds: 10     # 传送后的无敌时间（秒）

'@

if ($content.Contains('# ===================== 娑堟伅鏂囨湰')) {
    $content = $content.Replace($anchor, $spawnSection + $anchor)
    [System.IO.File]::WriteAllText($path, $content, $utf8NoBom)
    Write-Output 'INSERTED_OK'
} else {
    # 尝试按 messages: 顶层键插入（兼容不同编码注释）
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.AddRange($content.Split([string[]]@("`r`n", "`n"), [StringSplitOptions]::None))
    $idx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].Trim() -eq 'messages:') { $idx = $i; break }
    }
    if ($idx -gt 0) {
        $insert = $spawnSection.TrimEnd() -split "`r?`n"
        $lines.InsertRange($idx, $insert)
        [System.IO.File]::WriteAllText($path, ($lines -join "`r`n"), $utf8NoBom)
        Write-Output 'INSERTED_OK_VIA_MESSAGES'
    } else {
        Write-Output 'ANCHOR_NOT_FOUND'
    }
}
