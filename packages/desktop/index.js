const path = require('path');

let electron;
try {
  electron = require('electron');
} catch {
  electron = null;
}

function createWindow() {
  if (!electron || !electron.BrowserWindow) {
    return;
  }

  const win = new electron.BrowserWindow({
    width: 1280,
    height: 800,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
    },
  });

  win.loadFile(path.join(__dirname, 'index.html'));
}

if (electron && electron.app) {
  electron.app.whenReady().then(createWindow);

  electron.app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
      electron.app.quit();
    }
  });

  electron.app.on('activate', () => {
    if (electron.BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
}

module.exports = { createWindow };
