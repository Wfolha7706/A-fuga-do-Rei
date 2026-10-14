@echo off
chcp 65001 >nul
echo ========================================================
echo  Jungle Climb: A Fuga do Rei (com.jungleclimb.afugadorei)
echo  Preparando ambiente e iniciando compilação no Windows...
echo ========================================================

cd /d "%~dp0"

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo.
    echo [AVISO] O Java JDK não foi detectado no PATH do seu Windows.
    echo.
    echo Duas formas simples de obter o APK compilado:
    echo 1. Nuvem (Sem instalar nada): Envie este repositório para o seu GitHub.
    echo    O GitHub Actions (.github/workflows/android-apk.yml) irá compilar
    echo    automaticamente em 2 minutos e liberar o download na aba "Actions".
    echo.
    echo 2. Localmente: Baixe e instale o OpenJDK 17 e o Android Command Line Tools,
    echo    depois execute este script novamente.
    echo.
    pause
    exit /b 1
)

where gradle >nul 2>nul
if %errorlevel% equ 0 (
    echo [INFO] Compilando com gradle local...
    gradle assembleRelease
) else (
    if exist "gradlew.bat" (
        echo [INFO] Compilando com gradlew.bat...
        call gradlew.bat assembleRelease
    ) else (
        echo [AVISO] Gradle não foi detectado no PATH.
        echo Recomendado: Use o GitHub Actions para compilação 100%% automatizada na nuvem.
        pause
        exit /b 1
    )
)

if exist "app\build\outputs\apk\release\app-release.apk" (
    echo.
    echo ========================================================
    echo  SUCESSO! APK compilado com êxito!
    echo  Caminho: app\build\outputs\apk\release\app-release.apk
    echo ========================================================
)

pause
