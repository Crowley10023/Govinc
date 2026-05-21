/*
 * assessment-import.js — UI for the "import answers from document" feature.
 *
 * Flow:
 *   1. User clicks the "📥 Import Answers" button → modal opens.
 *   2. User picks a file → POST /assessment/{id}/import/upload (multipart).
 *   3. Server returns a list of per-control proposals; we render them with
 *      a checkbox, dropdown (to override the proposed answer) and editable
 *      comment textarea per row.
 *   4. User clicks "Apply Selected" → POST /assessment/{id}/import/apply with
 *      the acknowledged rows; on success the page is reloaded so the new
 *      answers and comments appear in the regular control table.
 */
(function () {
    'use strict';

    var modal = null;
    var proposalsBody = null;
    var statusEl = null;
    var summaryEl = null;
    var applyBtn = null;
    var maturityAnswers = [];   // [{id,name,rating},...] sorted by rating asc

    function $(id) { return document.getElementById(id); }

    function csrfHeader() {
        var t = document.querySelector('meta[name="_csrf"]');
        var h = document.querySelector('meta[name="_csrf_header"]');
        if (t && h) {
            var o = {};
            o[h.getAttribute('content')] = t.getAttribute('content');
            return o;
        }
        return {};
    }

    function escapeHtml(s) {
        if (s === undefined || s === null) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function ensureRefs() {
        modal = $('assessment-import-modal');
        proposalsBody = $('import-proposals-body');
        statusEl = $('import-status');
        summaryEl = $('import-summary');
        applyBtn = $('import-apply-btn');
    }

    window.openAssessmentImport = function () {
        ensureRefs();
        if (!modal) return;
        // Reset state on each open.
        $('import-step-upload').style.display = 'block';
        $('import-step-review').style.display = 'none';
        applyBtn.style.display = 'none';
        statusEl.textContent = '';
        proposalsBody.innerHTML = '';
        summaryEl.textContent = '';
        var f = $('import-file-input');
        if (f) f.value = '';
        modal.style.display = 'flex';
    };

    window.closeAssessmentImport = function () {
        ensureRefs();
        if (modal) modal.style.display = 'none';
    };

    window.runAssessmentImport = function () {
        ensureRefs();
        var input = $('import-file-input');
        if (!input || !input.files || input.files.length === 0) {
            statusEl.textContent = 'Please choose a file first.';
            statusEl.style.color = '#b00';
            return;
        }
        var file = input.files[0];
        var aid = window.assessmentId;
        if (!aid) { statusEl.textContent = 'Missing assessment id.'; return; }

        var fd = new FormData();
        fd.append('file', file);

        statusEl.style.color = '#555';
        statusEl.textContent = 'Reading and analyzing "' + file.name + '"…';
        $('import-analyze-btn').disabled = true;

        fetch('/assessment/' + encodeURIComponent(aid) + '/import/upload', {
            method: 'POST',
            headers: csrfHeader(),
            body: fd
        }).then(function (r) {
            return r.json().then(function (j) { return { ok: r.ok, body: j }; });
        }).then(function (res) {
            $('import-analyze-btn').disabled = false;
            if (!res.ok) {
                statusEl.style.color = '#b00';
                statusEl.textContent = 'Failed: ' + (res.body && res.body.error ? res.body.error : 'unknown error');
                return;
            }
            maturityAnswers = res.body.maturityAnswers || [];
            renderProposals(res.body.proposals || []);
            statusEl.textContent = 'Read ' + res.body.proposalCount + ' control(s) from "' + res.body.fileName + '".';
        }).catch(function (err) {
            $('import-analyze-btn').disabled = false;
            statusEl.style.color = '#b00';
            statusEl.textContent = 'Upload failed: ' + err;
        });
    };

    function renderProposals(proposals) {
        ensureRefs();
        proposalsBody.innerHTML = '';
        var exactCount = 0, aiCount = 0, noneCount = 0;

        proposals.forEach(function (p, idx) {
            var tr = document.createElement('tr');
            tr.style.borderBottom = '1px solid #eee';
            tr.style.verticalAlign = 'top';
            tr.dataset.controlId = p.controlId;

            var srcLabel = '';
            var srcStyle = '';
            if (p.matchType === 'EXACT') {
                exactCount++;
                srcLabel = 'Exact';
                srcStyle = 'background:#dff5e1;color:#1a6b2b;';
            } else if (p.matchType === 'AI') {
                aiCount++;
                srcLabel = 'AI';
                srcStyle = 'background:#e1edff;color:#1a4a8a;';
            } else {
                noneCount++;
                srcLabel = 'None';
                srcStyle = 'background:#f0f0f0;color:#555;';
            }

            // Build dropdown of all maturity answers, pre-selecting the proposed one.
            var optsHtml = maturityAnswers.map(function (a) {
                var sel = (a.id === p.proposedAnswerId) ? ' selected' : '';
                return '<option value="' + a.id + '"' + sel + '>'
                    + escapeHtml(a.name) + ' (' + a.rating + '%)</option>';
            }).join('');

            // Default: accept rows with an answer proposed, leave NONE rows unchecked.
            var checked = (p.matchType !== 'NONE') ? 'checked' : '';

            var conf = (typeof p.confidence === 'number')
                ? ' · conf ' + Math.round(p.confidence * 100) + '%'
                : '';

            tr.innerHTML =
                '<td style="text-align:center;padding:0.3em;">'
                +   '<input type="checkbox" class="import-row-check" ' + checked + ' />'
                + '</td>'
                + '<td style="padding:0.3em;">'
                +   '<span style="display:inline-block;padding:0.1em 0.5em;border-radius:3px;font-size:0.8em;'
                +   srcStyle + '">' + srcLabel + '</span>'
                +   '<div style="font-size:0.75em;color:#888;margin-top:2px;">' + escapeHtml(conf.trim()) + '</div>'
                + '</td>'
                + '<td style="padding:0.3em;">'
                +   '<div style="font-weight:600;">' + escapeHtml(p.controlReference || '') + '</div>'
                +   '<div>' + escapeHtml(p.controlName || '') + '</div>'
                + '</td>'
                + '<td style="padding:0.3em;">'
                +   '<select class="import-row-answer" style="width:100%;">' + optsHtml + '</select>'
                + '</td>'
                + '<td style="padding:0.3em;">'
                +   '<textarea class="import-row-comment" rows="3" style="width:100%;font-size:0.92em;">'
                +   escapeHtml(p.comment || '') + '</textarea>'
                + (p.evidence
                    ? '<details style="margin-top:0.2em;"><summary style="cursor:pointer;color:#666;font-size:0.82em;">evidence</summary>'
                    +   '<div style="font-size:0.82em;color:#555;background:#fafafa;padding:0.3em;border:1px solid #eee;margin-top:0.2em;">'
                    +   escapeHtml(p.evidence) + '</div></details>'
                    : '')
                + '</td>';
            proposalsBody.appendChild(tr);
        });

        summaryEl.textContent = proposals.length + ' control(s) — '
            + exactCount + ' exact, ' + aiCount + ' AI, ' + noneCount + ' none';
        $('import-step-upload').style.display = 'none';
        $('import-step-review').style.display = 'block';
        applyBtn.style.display = 'inline-block';
    }

    window.importToggleAll = function (on) {
        ensureRefs();
        proposalsBody.querySelectorAll('.import-row-check').forEach(function (cb) {
            cb.checked = !!on;
        });
    };

    window.applyAssessmentImport = function () {
        ensureRefs();
        var aid = window.assessmentId;
        if (!aid) return;

        var items = [];
        proposalsBody.querySelectorAll('tr').forEach(function (tr) {
            var cb = tr.querySelector('.import-row-check');
            if (!cb || !cb.checked) return;
            var sel = tr.querySelector('.import-row-answer');
            var cmt = tr.querySelector('.import-row-comment');
            items.push({
                controlId: parseInt(tr.dataset.controlId, 10),
                answerId: sel ? parseInt(sel.value, 10) : null,
                comment: cmt ? cmt.value : ''
            });
        });

        if (items.length === 0) {
            statusEl.style.color = '#b00';
            statusEl.textContent = 'Nothing selected to apply.';
            return;
        }

        applyBtn.disabled = true;
        statusEl.style.color = '#555';
        statusEl.textContent = 'Applying ' + items.length + ' answer(s)…';

        var headers = Object.assign({ 'Content-Type': 'application/json' }, csrfHeader());

        fetch('/assessment/' + encodeURIComponent(aid) + '/import/apply', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ items: items })
        }).then(function (r) {
            return r.json().then(function (j) { return { ok: r.ok, body: j }; });
        }).then(function (res) {
            applyBtn.disabled = false;
            if (!res.ok) {
                statusEl.style.color = '#b00';
                statusEl.textContent = 'Apply failed: ' + (res.body && res.body.error ? res.body.error : 'unknown');
                return;
            }
            statusEl.style.color = '#1a6b2b';
            statusEl.textContent = 'Applied ' + res.body.applied + ', skipped ' + res.body.skipped + '. Reloading…';
            setTimeout(function () { window.location.reload(); }, 800);
        }).catch(function (err) {
            applyBtn.disabled = false;
            statusEl.style.color = '#b00';
            statusEl.textContent = 'Apply failed: ' + err;
        });
    };

    // Close on backdrop click (consistent with other modals on the page).
    document.addEventListener('click', function (e) {
        ensureRefs();
        if (modal && e.target === modal) closeAssessmentImport();
    });
})();
