#!/usr/bin/env bash
# ==============================================================================
# Jungle Climb: A Fuga do Rei - Script de Configuração e Compilação Automatizada
# ==============================================================================

set -e

echo "========================================================"
echo " Jungle Climb: A Fuga do Rei (com.jungleclimb.afugadorei)"
echo " Preparando ambiente e iniciando compilação..."
echo "========================================================"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Verifica Java
if ! command -v java &> /dev/null; then
    echo ""
    echo "[AVISO] Java JDK não foi detectado no PATH do sistema."
    echo "Para compilar localmente, instale o OpenJDK 17 ou use o GitHub Actions."
    echo "Se você enviar este projeto para o GitHub, o workflow .github/workflows/android-apk.yml"
    echo "irá compilar automaticamente e disponibilizar o arquivo JungleClimb-release.apk para download!"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n 1)
echo "[INFO] Java detectado: $JAVA_VER"

# 2. Verifica comando gradle
GRADLE_CMD=""
if command -v gradle &> /dev/null; then
    GRADLE_CMD="gradle"
elif [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    GRADLE_CMD="./gradlew"
fi

if [ -z "$GRADLE_CMD" ]; then
    echo ""
    echo "[AVISO] Gradle não foi detectado no sistema."
    echo "Instale o Gradle 8.5+ ou utilize o GitHub Actions configurado na pasta .github/workflows/."
    exit 1
fi

echo "[INFO] Executando compilação do APK de Release com $GRADLE_CMD..."
$GRADLE_CMD assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "========================================================"
    echo " SUCESSO! APK compilado com êxito!"
    echo " Caminho do APK: $APK_PATH"
    echo " Para instalar via USB (ADB):"
    echo " adb install -r $APK_PATH"
    echo "========================================================"
else
    echo "[INFO] Compilação finalizada. Verifique app/build/outputs/apk/ para os arquivos gerados."
fi
