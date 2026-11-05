@echo off
chcp 65001 >nul
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven est introuvable. Installe Maven puis relance ce fichier.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java est introuvable. Installe Java 17 ou plus recent.
  pause
  exit /b 1
)
echo Compilation de FactionDominion pour Paper 1.20.1...
mvn clean package
if errorlevel 1 (
  echo ECHEC : regarde les erreurs Maven ci-dessus.
) else (
  echo Termine. Le JAR se trouve dans target\faction-dominion-1.0.0.jar
)
pause
