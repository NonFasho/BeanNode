# PowerShell script to build and deploy the Headless Bean Node
Write-Host "🔄 Running mvn clean package..."

# Path to your Maven project directory
$projectPath = "C:\BeanNodes\HeadlessNode"

Push-Location $projectPath
mvn clean package
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Maven build failed. Deployment aborted." -ForegroundColor Red
    exit 1
}

Write-Host "📦 Build complete. Preparing to upload JAR..."

# Path to the JAR and destination on remote server
$jarPath = "C:\BeanNodes\HeadlessNode\target\HeadlessBeanNode-1.0-SNAPSHOT.jar"
$remote = "root@65.38.97.169:/root/beanchain/beanchain-node.jar"

# Upload using SCP
scp $jarPath $remote

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ JAR uploaded successfully!" -ForegroundColor Green
} else {
    Write-Host "❌ SCP upload failed!" -ForegroundColor Red
}
