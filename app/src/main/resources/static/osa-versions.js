// Org service assessment: version switching, "make new version" and change indicators vs. previous version.
(function () {
    var csrfEl = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderEl = document.querySelector('meta[name="_csrf_header"]');
    var tip = null;

    window.osaSwitchVersion = function (v) {
        var url = new URL(window.location.href);
        url.searchParams.set('version', v);
        window.location.href = url.toString();
    };

    window.osaMakeNewVersion = function (btn) {
        if (!confirm('Freeze the current ratings and comments as a version and start a new one?')) return;
        var hdrs = {};
        if (csrfEl) hdrs[csrfHeaderEl ? csrfHeaderEl.getAttribute('content') : 'X-CSRF-TOKEN'] = csrfEl.getAttribute('content');
        btn.disabled = true;
        fetch('/orgservice-assessment/' + btn.getAttribute('data-org-service-id') + '/new-version', { method: 'POST', headers: hdrs })
            .then(function (r) { return r.json(); })
            .then(function (d) {
                if (d.success) {
                    var url = new URL(window.location.href);
                    url.searchParams.delete('version');
                    window.location.href = url.toString();
                } else { btn.disabled = false; alert(d.message || 'Could not create a new version.'); }
            })
            .catch(function () { btn.disabled = false; alert('Could not create a new version.'); });
    };

    function esc(s) {
        var d = document.createElement('div');
        d.appendChild(document.createTextNode(s == null ? '' : s));
        return d.innerHTML;
    }

    function ensureTip() {
        if (!tip) {
            tip = document.createElement('div');
            tip.className = 'osa-tip';
            document.body.appendChild(tip);
        }
        return tip;
    }

    function showTip(el) {
        var t = ensureTip();
        t.innerHTML = el._tipHtml;
        t.style.display = 'block';
        var r = el.getBoundingClientRect();
        var w = t.offsetWidth, h = t.offsetHeight;
        var left = Math.min(Math.max(8, r.left + r.width / 2 - w / 2), window.innerWidth - w - 8);
        var top = r.top - h - 10;
        if (top < 8) top = r.bottom + 10;
        t.style.left = left + 'px';
        t.style.top = top + 'px';
    }

    function hideTip() { if (tip) tip.style.display = 'none'; }

    function answerLabel(pct) {
        var model = (typeof currentMaturityModel !== 'undefined') ? currentMaturityModel : null;
        if (!model || !model.maturityAnswers) return null;
        var m = model.maturityAnswers.find(function (a) { return String(a.rating) === String(pct); });
        return m ? m.answer : null;
    }

    function badge(cell, kind) {
        var b = cell.querySelector('.osa-delta[data-kind="' + kind + '"]');
        if (!b) {
            b = document.createElement('span');
            b.className = 'osa-delta';
            b.setAttribute('data-kind', kind);
            b.tabIndex = 0;
            b.textContent = '\u21BB changed';
            b.addEventListener('mouseenter', function () { showTip(b); });
            b.addEventListener('focus', function () { showTip(b); });
            b.addEventListener('mouseleave', hideTip);
            b.addEventListener('blur', hideTip);
            cell.appendChild(b);
        }
        return b;
    }

    function refreshRow(row) {
        if (!row.hasAttribute('data-prev-percent')) return;
        var ver = row.getAttribute('data-prev-version');
        var pctInput = row.querySelector('.control-percent');
        var ta = row.querySelector('.control-comment');

        if (pctInput) {
            var cell = pctInput.closest('td');
            var prevPct = row.getAttribute('data-prev-percent');
            var b = cell.querySelector('.osa-delta[data-kind="rating"]');
            if (String(parseInt(pctInput.value || '0', 10)) !== String(parseInt(prevPct, 10))) {
                b = badge(cell, 'rating');
                var cur = answerLabel(pctInput.value);
                var frames = historyFrames(row.getAttribute('data-control-id'), function (h) {
                    var l = answerLabel(h.percent);
                    return esc(h.percent) + '%' + (l ? ' &ndash; ' + esc(l) : '');
                });
                b._tipHtml = '<div class="osa-tip-title">Rating history</div>' + frames +
                    '<div class="osa-tip-now">Now: ' + esc(pctInput.value || '0') + '%' + (cur ? ' &ndash; ' + esc(cur) : '') + '</div>';
            } else if (b) { b.remove(); }
        }

        if (ta) {
            var cCell = ta.closest('td');
            var prevC = (row.getAttribute('data-prev-comment') || '').trim();
            var cb = cCell.querySelector('.osa-delta[data-kind="comment"]');
            if (ta.value.trim() !== prevC) {
                cb = badge(cCell, 'comment');
                cb._tipHtml = '<div class="osa-tip-title">Comment history</div>' +
                    historyFrames(row.getAttribute('data-control-id'), function (h) {
                        var c = (h.comment || '').trim();
                        if (c.length > 300) c = c.substring(0, 300) + '\u2026';
                        return c ? esc(c) : '<em>(empty)</em>';
                    });
            } else if (cb) { cb.remove(); }
        }
    }

    // Up to 5 previous versions, each in its own frame.
    function historyFrames(controlId, render) {
        var list = (window.OSA_HISTORY && window.OSA_HISTORY[controlId]) || [];
        if (!list.length) return '<div class="osa-tip-frame"><em>No history available</em></div>';
        return list.slice(0, 5).map(function (h) {
            return '<div class="osa-tip-frame"><div class="osa-tip-key">Version ' + esc(h.version) + '</div>' +
                '<div class="osa-tip-text">' + render(h) + '</div></div>';
        }).join('');
    }

    function refreshAll() { document.querySelectorAll('.control-row').forEach(refreshRow); }

    document.addEventListener('DOMContentLoaded', function () {
        if (window.OSA_READONLY) {
            document.querySelectorAll('.control-row input, .control-row select, .control-row textarea, .control-row button')
                .forEach(function (el) { el.setAttribute('inert', ''); el.disabled = true; });
        }
        refreshAll();
        document.addEventListener('input', function (e) {
            var row = e.target.closest && e.target.closest('.control-row');
            if (row) refreshRow(row);
        });
        document.addEventListener('change', function (e) {
            var row = e.target.closest && e.target.closest('.control-row');
            if (row) setTimeout(function () { refreshRow(row); }, 0);
        });
        var sel = document.getElementById('maturityModelSelect');
        if (sel) sel.addEventListener('change', function () { setTimeout(refreshAll, 0); });
    });
})();
