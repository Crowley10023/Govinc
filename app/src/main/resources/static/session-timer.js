/**
 * session-timer.js
 * Shows a countdown timer in the navigation header indicating how long the session
 * remains active. Displays a warning popup 2 minutes before expiry with a
 * "Keep Session" button that resets the server-side inactivity timer via a
 * lightweight POST to /session/keepalive.
 *
 * User activity (click, keydown, scroll) also silently extends the session.
 */
(function () {
  'use strict';

  var WARNING_THRESHOLD_SECONDS = 120; // Show popup at 2 minutes remaining
  var ACTIVITY_DEBOUNCE_MS = 3000;     // Debounce activity-triggered keepalive

  var timeoutMeta = document.querySelector('meta[name="session-timeout-minutes"]');
  if (!timeoutMeta) return;

  var timeoutMinutes = parseInt(timeoutMeta.getAttribute('content'), 10);
  if (isNaN(timeoutMinutes) || timeoutMinutes <= 0) return;

  var timeoutSeconds = timeoutMinutes * 60;
  var lastActivityTime = Date.now();
  var warningShown = false;
  var activityDebounceTimer = null;

  // ── Track user activity ──────────────────────────────────────────────────
  function onActivity() {
    lastActivityTime = Date.now();
    if (warningShown) {
      hideWarning();
    }
    // Debounce: send keepalive only once per ACTIVITY_DEBOUNCE_MS of activity
    if (!activityDebounceTimer) {
      activityDebounceTimer = setTimeout(function () {
        activityDebounceTimer = null;
        sendKeepalive();
      }, ACTIVITY_DEBOUNCE_MS);
    }
  }

  // mousemove is intentionally excluded – it fires hundreds of times per second and
  // would trigger a keepalive every 3 s even when the user is just moving the cursor.
  // Meaningful interactions (click, key, scroll, touch) are sufficient to detect activity.
  ['click', 'keydown', 'scroll', 'touchstart'].forEach(function (evt) {
    document.addEventListener(evt, onActivity, { passive: true });
  });

  // ── Session expired: redirect to login ───────────────────────────────────
  var redirecting = false;
  function redirectToLogin() {
    if (redirecting) return;
    redirecting = true;
    window.location.href = '/login?expired=1';
  }

  // ── Keepalive request ─────────────────────────────────────────────────────
  function sendKeepalive(callback) {
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    var csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
    var csrfHeaderName = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';
    var headers = { 'Content-Type': 'application/json' };
    headers[csrfHeaderName] = csrfToken;

    fetch('/session/keepalive', { method: 'POST', headers: headers })
      .then(function (res) {
        if (res.ok) {
          lastActivityTime = Date.now();
          hideWarning();
          if (typeof callback === 'function') callback();
        } else if (res.status === 401 || res.status === 302) {
          // Server-side session is already gone
          redirectToLogin();
        }
      })
      .catch(function () { /* network error – keep counting down client-side */ });
  }

  // ── Timer display ─────────────────────────────────────────────────────────
  function pad(n) { return n < 10 ? '0' + n : String(n); }

  function getRemainingSeconds() {
    var elapsed = (Date.now() - lastActivityTime) / 1000;
    return Math.max(0, timeoutSeconds - elapsed);
  }

  function updateTimerDisplay() {
    var display = document.getElementById('sessionTimeRemaining');
    if (!display) return;
    var remaining = getRemainingSeconds();
    var mins = Math.floor(remaining / 60);
    var secs = Math.floor(remaining % 60);
    display.textContent = pad(mins) + ':' + pad(secs);

    // Colour hint when getting close
    var timerEl = document.getElementById('sessionTimerDisplay');
    if (timerEl) {
      if (remaining <= WARNING_THRESHOLD_SECONDS) {
        timerEl.classList.add('session-timer-warning');
      } else {
        timerEl.classList.remove('session-timer-warning');
      }
    }
  }

  // ── Warning popup ─────────────────────────────────────────────────────────
  function showWarning() {
    warningShown = true;
    var overlay = document.getElementById('sessionExpiryOverlay');
    if (overlay) overlay.style.display = 'flex';
    updatePopupCountdown();
  }

  function hideWarning() {
    warningShown = false;
    var overlay = document.getElementById('sessionExpiryOverlay');
    if (overlay) overlay.style.display = 'none';
  }

  function updatePopupCountdown() {
    if (!warningShown) return;
    var countdown = document.getElementById('sessionExpiryCountdown');
    if (countdown) countdown.textContent = Math.max(0, Math.floor(getRemainingSeconds()));
  }

  // ── Main tick (every second) ──────────────────────────────────────────────
  setInterval(function () {
    var remaining = getRemainingSeconds();
    updateTimerDisplay();

    if (remaining <= 0) {
      redirectToLogin();
      return;
    }

    if (remaining <= WARNING_THRESHOLD_SECONDS && !warningShown) {
      showWarning();
    }

    if (warningShown) {
      updatePopupCountdown();
    }
  }, 1000);

  // ── Public API (referenced by navigation.html) ────────────────────────────
  window._sessionTimer = {
    keepSession: function () {
      sendKeepalive(function () {
        hideWarning();
      });
    }
  };

})();
