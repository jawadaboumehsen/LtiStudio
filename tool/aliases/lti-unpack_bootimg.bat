@echo off
setlocal
set "DIR=%~dp0"
set "LTI_BIN=%DIR%lti.bat"
if not exist "%LTI_BIN%" set "LTI_BIN=lti"
"%LTI_BIN%" tools unpack_bootimg %*
