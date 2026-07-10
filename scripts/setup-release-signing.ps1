param(
    [string]$KeystorePath = "$env:USERPROFILE\timeline-unlocker-release.p12",
    [string]$KeyAlias = "timeline-unlocker"
)

$ErrorActionPreference = "Stop"

function Read-Secret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Set-RepositorySecret([string]$Name, [string]$Value, [string]$Repository) {
    $Value | & gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to configure GitHub secret: $Name"
    }
}

if (Test-Path -LiteralPath $KeystorePath) {
    throw "Keystore already exists: $KeystorePath"
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found. Install a JDK and ensure its bin directory is on PATH."
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI was not found. Install it and run 'gh auth login'."
}

& gh auth status
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run 'gh auth login' first."
}

$remoteUrl = (& git remote get-url origin).Trim()
if ($LASTEXITCODE -ne 0 -or $remoteUrl -notmatch "github\.com[:/]([^/]+)/([^/.]+)(?:\.git)?$") {
    throw "Could not determine the GitHub repository from origin."
}
$repository = "$($matches[1])/$($matches[2])"

$storePassword = Read-Secret "Keystore password"
if ($storePassword.Length -lt 16) {
    throw "Use a keystore password of at least 16 characters."
}
$keyPassword = Read-Secret "Key password (press Enter to reuse the keystore password)"
if ([string]::IsNullOrEmpty($keyPassword)) {
    $keyPassword = $storePassword
}
if ($keyPassword.Length -lt 16) {
    throw "Use a key password of at least 16 characters."
}

$parent = Split-Path -Parent $KeystorePath
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}

& keytool -genkeypair -v `
    -keystore $KeystorePath `
    -storetype PKCS12 `
    -storepass $storePassword `
    -alias $KeyAlias `
    -keypass $keyPassword `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname "CN=Timeline Unlocker, OU=Release, O=Timeline Unlocker, C=US"
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to create the release keystore."
}

$certificateOutput = & keytool -list -v `
    -keystore $KeystorePath `
    -storepass $storePassword `
    -alias $KeyAlias
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to read the release certificate."
}
$certificateHash = ($certificateOutput | Select-String "SHA256:").ToString().Split(":", 2)[1].Trim()
if ([string]::IsNullOrEmpty($certificateHash)) {
    throw "Could not extract the certificate SHA-256 fingerprint."
}

$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
Set-RepositorySecret RELEASE_KEYSTORE_BASE64 $keystoreBase64 $repository
Set-RepositorySecret RELEASE_STORE_PASSWORD $storePassword $repository
Set-RepositorySecret RELEASE_KEY_ALIAS $KeyAlias $repository
Set-RepositorySecret RELEASE_KEY_PASSWORD $keyPassword $repository
Set-RepositorySecret RELEASE_APK_CERT_SHA256 $certificateHash $repository

Write-Host "Release signing secrets configured for $repository."
Write-Host "Back up the keystore securely: $KeystorePath"
