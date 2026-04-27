// background.js
// Opens TaskWatch as a standalone window when the extension icon is clicked.
// If a window is already open, focuses it instead of opening a second one.

let taskWatchWindowId = null;

chrome.action.onClicked.addListener(() => {
  if (taskWatchWindowId !== null) {
    chrome.windows.get(taskWatchWindowId, {}, (win) => {
      if (chrome.runtime.lastError || !win) {
        openWindow();
      } else {
        chrome.windows.update(taskWatchWindowId, { focused: true });
      }
    });
  } else {
    openWindow();
  }
});

function openWindow() {
  chrome.windows.create({
    url: chrome.runtime.getURL("app.html"),
    type: "popup",
    width: 480,
    height: 720,
    focused: true
  }, (win) => {
    taskWatchWindowId = win.id;
  });
}

chrome.windows.onRemoved.addListener((windowId) => {
  if (windowId === taskWatchWindowId) {
    taskWatchWindowId = null;
  }
});
