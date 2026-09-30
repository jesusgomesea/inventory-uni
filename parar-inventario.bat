@echo off
setlocal
chcp 65001 >nul
rem ===========================================================================
rem  Encerra o Inventario GLPI aberto por iniciar-inventario.bat.
rem  Encerra pelos processos que ocupam as portas do sistema (8091 = backend, PORTA_WEB = frontend),
rem  e so se o processo for java.exe ou node.exe: nunca derruba outro servico na mesma porta.
rem  NAO mexe nas portas do HELP-AGENT (80 e 8080).
rem ===========================================================================

rem Mesma porta configurada em iniciar-inventario.bat.
set "PORTA_WEB=4300"

call :encerrar 8091 java.exe
call :encerrar %PORTA_WEB% node.exe

echo Inventario parado.
timeout /t 3 /nobreak >nul
endlocal
exit /b 0

rem --- :encerrar <porta> <executavel esperado> ---------------------------------
:encerrar
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%1 .*LISTENING"') do (
  tasklist /FI "PID eq %%p" /FO CSV /NH | findstr /I /C:"%2" >nul && (
    taskkill /PID %%p /T /F >nul 2>&1
    echo Encerrado %2 ^(PID %%p^) da porta %1.
  )
)
exit /b 0
