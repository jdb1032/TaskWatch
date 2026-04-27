// app.js — TaskWatch Chrome Extension
// Ported from TaskWatch.java. All core logic preserved.

// ── State ─────────────────────────────────────────────────────────
let startTime       = 0;
let elapsedAtPause  = 0;
let lastLapTime     = 0;
let running         = false;
let lapNumber       = 0;
let laps            = [];   // [{totalMs, splitMs}]

let ahtValue        = 0;    // raw number from input
let ahtInSeconds    = false; // false = minutes, true = seconds
let intervalBreached = false;
let avgBreached      = false;
let softAlertsWhenOnPace = false;

let rafId = null;

// ── DOM refs ──────────────────────────────────────────────────────
const timeDisplay   = document.getElementById('time-display');
const splitDisplay  = document.getElementById('split-display');
const alertBanner   = document.getElementById('alert-banner');
const ahtField      = document.getElementById('aht-field');
const ahtUnit       = document.getElementById('aht-unit');
const playBtn       = document.getElementById('play-btn');
const lapBtn        = document.getElementById('lap-btn');
const resetBtn      = document.getElementById('reset-btn');
const lapTbody      = document.getElementById('lap-tbody');
const tileTotal     = document.getElementById('tile-total');
const tileAvg       = document.getElementById('tile-avg');
const tilePace      = document.getElementById('tile-pace');
const menuBtn       = document.getElementById('menu-btn');
const dropdownMenu  = document.getElementById('dropdown-menu');
const prefSoftAlerts = document.getElementById('pref-soft-alerts');
const menuNew       = document.getElementById('menu-new');

// ── Menu ──────────────────────────────────────────────────────────
menuBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  dropdownMenu.classList.toggle('hidden');
});

document.addEventListener('click', () => {
  dropdownMenu.classList.add('hidden');
});

dropdownMenu.addEventListener('click', (e) => e.stopPropagation());

prefSoftAlerts.addEventListener('change', () => {
  softAlertsWhenOnPace = prefSoftAlerts.checked;
  if (intervalBreached) {
    refreshIntervalVisuals();
  }
});

menuNew.addEventListener('click', () => {
  dropdownMenu.classList.add('hidden');
  onReset();
});

// ── Button handlers ───────────────────────────────────────────────
playBtn.addEventListener('click', onPlayPause);
lapBtn.addEventListener('click', onLap);
resetBtn.addEventListener('click', onReset);

// ── AHT input ─────────────────────────────────────────────────────
ahtField.addEventListener('input', parseAht);
ahtUnit.addEventListener('change', () => {
  ahtInSeconds = ahtUnit.value === 'sec';
  parseAht();
});

// Keyboard shortcut: Ctrl+S → no-op (no file saving in extension)
document.addEventListener('keydown', (e) => {
  if (e.ctrlKey && e.key === 's') e.preventDefault();
});

// ── Core actions ──────────────────────────────────────────────────
function onPlayPause() {
  if (!running) {
    startTime = performance.now();
    if (elapsedAtPause === 0) lastLapTime = 0;
    running = true;
    startLoop();
    stylePlayBtn('pause');
    lapBtn.disabled = false;
    resetBtn.disabled = true;
  } else {
    elapsedAtPause += performance.now() - startTime;
    running = false;
    stopLoop();
    stylePlayBtn('resume');
    lapBtn.disabled = true;
    resetBtn.disabled = false;
  }
}

function onLap() {
  const now = elapsed();
  const splitMs = laps.length === 0 ? now : now - lastLapTime;
  lastLapTime = now;
  lapNumber++;
  laps.push({ totalMs: now, splitMs });

  const fromStr  = lapNumber === 1 ? '00:00:00.00' : formatMs(laps[lapNumber - 2].totalMs);
  const toStr    = formatMs(now);
  const splitStr = formatHMS(splitMs);

  // Insert at top of table
  const tr = document.createElement('tr');
  tr.innerHTML = `
    <td class="col-num">${lapNumber}</td>
    <td class="col-from">${fromStr}</td>
    <td class="col-to">${toStr}</td>
    <td class="col-ht">${splitStr}</td>
  `;
  lapTbody.insertBefore(tr, lapTbody.firstChild);

  clearIntervalAlert();
  updateSummary();
}

function onReset() {
  elapsedAtPause = 0;
  lastLapTime    = 0;
  lapNumber      = 0;
  laps           = [];
  running        = false;
  stopLoop();

  timeDisplay.textContent  = '00:00:00.00';
  splitDisplay.textContent = '00:00:00';
  timeDisplay.style.color  = '';
  lapTbody.innerHTML       = '';

  tileTotal.textContent = 'Total \u00a0 —';
  tileAvg.textContent   = 'Avg \u00a0 —';
  resetPaceTile();

  clearIntervalAlert();
  clearAvgAlert();

  stylePlayBtn('play');
  lapBtn.disabled   = true;
  resetBtn.disabled = true;
}

// ── RAF loop ──────────────────────────────────────────────────────
function startLoop() {
  if (rafId) return;
  function tick() {
    updateDisplay();
    rafId = requestAnimationFrame(tick);
  }
  rafId = requestAnimationFrame(tick);
}

function stopLoop() {
  if (rafId) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  // Render one final frame at the paused time
  updateDisplay();
}

// ── Display & alert logic ─────────────────────────────────────────
function updateDisplay() {
  const now        = elapsed();
  const intervalMs = now - lastLapTime;
  timeDisplay.textContent  = formatMs(now);
  splitDisplay.textContent = formatHMS(intervalMs);
  checkIntervalBreach(intervalMs);
  updatePace();
}

function elapsed() {
  return running
    ? elapsedAtPause + (performance.now() - startTime)
    : elapsedAtPause;
}

// ── AHT helpers ───────────────────────────────────────────────────
function ahtLimitMs() {
  if (ahtValue <= 0) return 0;
  return ahtInSeconds ? ahtValue * 1000 : ahtValue * 60 * 1000;
}

function isOnPace() {
  const limit = ahtLimitMs();
  if (limit <= 0 || laps.length === 0) return false;
  const target = laps.length * limit;
  return (target - elapsed()) >= 0;
}

// ── Interval breach ───────────────────────────────────────────────
function checkIntervalBreach(intervalMs) {
  const limit = ahtLimitMs();
  if (limit <= 0) {
    if (intervalBreached) clearIntervalAlert();
    return;
  }
  const over = intervalMs >= limit;
  if (over) {
    const wasBreached = intervalBreached;
    intervalBreached = true;
    refreshIntervalVisuals();
  } else if (intervalBreached) {
    clearIntervalAlert();
  }
}

function refreshIntervalVisuals() {
  const quiet = softAlertsWhenOnPace && isOnPace();
  alertBanner.classList.toggle('hidden', quiet);
  splitDisplay.style.color = quiet ? 'var(--red-soft)' : 'var(--red)';
}

function clearIntervalAlert() {
  intervalBreached = false;
  alertBanner.classList.add('hidden');
  splitDisplay.style.color = '';
}

// ── Average breach ────────────────────────────────────────────────
function checkAvgBreach(avgMs) {
  const limit = ahtLimitMs();
  if (limit <= 0) {
    if (avgBreached) clearAvgAlert();
    return;
  }
  const over = avgMs > limit;
  if (over && !avgBreached) {
    avgBreached = true;
    tileAvg.classList.add('avg-breach');
    timeDisplay.style.color = 'var(--red2)';
    if (intervalBreached) refreshIntervalVisuals();
  } else if (!over && avgBreached) {
    clearAvgAlert();
  }
}

function clearAvgAlert() {
  avgBreached = false;
  tileAvg.classList.remove('avg-breach');
  timeDisplay.style.color = '';
}

// ── Pace ──────────────────────────────────────────────────────────
function updatePace() {
  const limit = ahtLimitMs();
  if (limit <= 0 || laps.length === 0) {
    resetPaceTile();
    return;
  }
  const target = laps.length * limit;
  const pace   = target - elapsed();
  const ahead  = pace >= 0;
  const sign   = ahead ? '+' : '−';
  tilePace.textContent = `Pace \u00a0 ${sign}${formatPace(Math.abs(pace))}`;
  tilePace.className = 'summary-tile ' + (ahead ? 'pace-ahead' : 'pace-behind');
}

function resetPaceTile() {
  tilePace.textContent = 'Pace \u00a0 —';
  tilePace.className   = 'summary-tile';
}

// ── Summary ───────────────────────────────────────────────────────
function updateSummary() {
  if (laps.length === 0) return;
  let total = 0;
  for (const l of laps) total += l.splitMs;
  const avg = Math.floor(total / laps.length);
  tileTotal.textContent = `Total \u00a0 ${formatHMS(total)}`;
  // Avg text set here; class managed by checkAvgBreach
  const prevClass = tileAvg.classList.contains('avg-breach') ? 'avg-breach' : '';
  tileAvg.textContent = `Avg \u00a0 ${formatHMS(avg)}`;
  if (prevClass) tileAvg.classList.add(prevClass);
  checkAvgBreach(avg);
  updatePace();
}

// ── AHT parse ─────────────────────────────────────────────────────
function parseAht() {
  const txt = ahtField.value.trim();
  const val = parseInt(txt, 10);
  ahtValue = (!isNaN(val) && val > 0) ? val : 0;

  const intervalMs = elapsed() - lastLapTime;
  checkIntervalBreach(intervalMs);

  if (laps.length > 0) {
    let total = 0;
    for (const l of laps) total += l.splitMs;
    checkAvgBreach(Math.floor(total / laps.length));
  } else if (avgBreached) {
    clearAvgAlert();
  }
  updatePace();
}

// ── Format helpers ────────────────────────────────────────────────

/** Full precision HH:MM:SS.cs — live clocks and From/To columns */
function formatMs(ms) {
  const centis = Math.floor(ms / 10);
  const cs = centis % 100;
  const s  = Math.floor(centis / 100) % 60;
  const m  = Math.floor(centis / 6000) % 60;
  const h  = Math.floor(centis / 360000);
  return `${pad(h)}:${pad(m)}:${pad(s)}.${pad(cs)}`;
}

/** Compact HH:MM:SS — Handle Time column and summary totals */
function formatHMS(ms) {
  const totalSec = Math.floor(ms / 1000);
  const s = totalSec % 60;
  const m = Math.floor(totalSec / 60) % 60;
  const h = Math.floor(totalSec / 3600);
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}

/** M:SS or H:MM:SS — pace tile (no centiseconds needed) */
function formatPace(ms) {
  const totalSecs = Math.floor(ms / 1000);
  const s = totalSecs % 60;
  const m = Math.floor(totalSecs / 60) % 60;
  const h = Math.floor(totalSecs / 3600);
  if (h > 0) return `${h}:${pad(m)}:${pad(s)}`;
  return `${m}:${pad(s)}`;
}

function pad(n) { return String(n).padStart(2, '0'); }

// ── Button styling ────────────────────────────────────────────────
function stylePlayBtn(state) {
  playBtn.className = 'btn';
  if (state === 'pause') {
    playBtn.textContent = '[ Pause ]';
    playBtn.classList.add('btn-pause');
  } else if (state === 'resume') {
    playBtn.textContent = '[ Resume ]';
    playBtn.classList.add('btn-primary');
  } else {
    playBtn.textContent = '[ Play ]';
    playBtn.classList.add('btn-primary');
  }
}
