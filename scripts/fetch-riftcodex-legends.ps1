# Saves every Riftbound legend card from Riftcodex to legends_raw.json (Windows PowerShell).
# Riftcodex refuses the cloud dev machines, so run this at home, then:
#   python3 scripts/build_legend_assets.py legends_raw.json
$all = @()
$page = 1
do {
    $r = Invoke-RestMethod "https://api.riftcodex.com/cards?size=100&page=$page" -UserAgent "RiftTracker legend importer (personal fan app)"
    $all += $r.items | Where-Object { $_.classification.type -eq "Legend" }
    Write-Host "page $page of $($r.pages)"
    $page++
} while ($page -le $r.pages)
ConvertTo-Json @($all) -Depth 10 | Out-File -Encoding utf8 legends_raw.json
Write-Host "$($all.Count) legend cards saved to legends_raw.json"
