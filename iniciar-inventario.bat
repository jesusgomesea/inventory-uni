@echo off
setlocal
chcp 65001 >nul
title Inventario GLPI - inicializador

rem ===========================================================================
rem  Sobe o Inventario GLPI (mesmo esquema do HELP-AGENT):
rem   - backend  (Spring Boot, perfil local)  -> so em 127.0.0.1:8091
rem   - frontend (Angular)                    -> 0.0.0.0:%PORTA_WEB%, com proxy /api para o backend
rem  Uso:  iniciar-inventario.bat             (GLPI de verdade: precisa de backend\config\application-local.yml)
rem        iniciar-inventario.bat simulador   (GLPI de mentira, para demonstracao; login tecnico / tecnico)
rem  Para parar: parar-inventario.bat
rem  Portas diferentes das do HELP-AGENT (80 e 8080), que roda nesta mesma maquina.
rem ===========================================================================

set "PORTA_WEB=4300"
set "PERFIS=local"
if /I "%~1"=="simulador" set "PERFIS=local,simulador"

set "RAIZ=%~dp0"
rem O TEMP desta maquina tem nome curto 8.3, que quebra o socket interno da JVM. A JVM usa esta pasta no lugar.
set "JVM_TMP=%USERPROFILE%\.helpagent-tmp"
if not exist "%JVM_TMP%" mkdir "%JVM_TMP%"

where java >nul 2>&1 || (echo [ERRO] Java nao encontrado no PATH. Instale o JDK 21. & pause & exit /b 1)
where node >nul 2>&1 || (echo [ERRO] Node.js nao encontrado no PATH. & pause & exit /b 1)

if /I not "%~1"=="simulador" (
  findstr /R /C:"^inventario:" "%RAIZ%backend\config\application-local.yml" >nul 2>&1 || (
    echo [AVISO] O endereco do GLPI e o App-Token ainda nao foram preenchidos em backend\config\application-local.yml.
    echo         Sem eles o login nao funciona. Para so ver a tela: iniciar-inventario.bat simulador
    echo.
  )
)

netstat -ano | findstr /R /C:":8091 .*LISTENING" >nul && (
  echo [AVISO] A porta 8091 ja esta em uso - o backend provavelmente ja esta rodando. Use parar-inventario.bat antes.
  pause & exit /b 1
)
netstat -ano | findstr /R /C:":%PORTA_WEB% .*LISTENING" >nul && (
  echo [AVISO] A porta %PORTA_WEB% ja esta em uso. Use parar-inventario.bat ou troque PORTA_WEB neste arquivo.
  pause & exit /b 1
)

if not exist "%RAIZ%frontend\node_modules" (
  echo Instalando dependencias do frontend pela primeira vez...
  rem registry.npmjs.org e bloqueado nesta rede; o espelho do Yarn serve os mesmos pacotes.
  pushd "%RAIZ%frontend"
  call npm install --registry=https://registry.yarnpkg.com --no-audit --no-fund || (popd & echo [ERRO] npm install falhou. & pause & exit /b 1)
  popd
)

echo Iniciando backend (perfis %PERFIS%)...
start "Inventario - backend" /D "%RAIZ%backend" cmd /k ""%RAIZ%backend\mvnw.cmd" -q spring-boot:run -Dspring-boot.run.profiles=%PERFIS% "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=%JVM_TMP%""

echo Aguardando o backend responder (a primeira vez pode levar alguns minutos)...
set /a TENTATIVAS=0
:espera
set /a TENTATIVAS+=1
curl -s -o nul --max-time 2 http://127.0.0.1:8091/actuator/health && goto pronto
if %TENTATIVAS% GEQ 150 (
  echo [ERRO] O backend nao respondeu em 5 minutos. Veja a janela "Inventario - backend".
  pause & exit /b 1
)
timeout /t 2 /nobreak >nul
goto espera

:pronto
echo Backend no ar. Iniciando frontend na porta %PORTA_WEB%...
start "Inventario - frontend" /D "%RAIZ%frontend" cmd /k npx ng serve --host 0.0.0.0 --port %PORTA_WEB%

echo.
echo ===========================================================================
echo  Inventario GLPI no ar (o frontend termina de compilar em alguns segundos):
echo    Nesta maquina : http://localhost:%PORTA_WEB%
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /C:"IPv4"') do for /f "tokens=*" %%j in ("%%i") do echo    Pelo IP       : http://%%j:%PORTA_WEB%
if /I "%~1"=="simulador" echo    SIMULADOR: dados ficticios. Login tecnico / tecnico
echo  Mantenha as duas janelas abertas. Para parar: parar-inventario.bat
echo ===========================================================================
timeout /t 8 /nobreak >nul
