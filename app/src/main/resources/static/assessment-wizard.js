// =================== Assessment Wizard ===================
// A guided, AI-powered wizard for walking through all assessment questions.

(function() {
    'use strict';

    // ---- Wizard State ----
    var wizardState = {
        active: false,
        controls: [],          // ordered list of { controlId, controlName, controlDetail, domainId, domainName, reference, tag, answered, answerId, answerText, comment, takenOver, overridden }
        currentIndex: 0,
        maturityAnswers: [],   // [{ id, answer, rating, description }]
        assessmentId: 0,
        securityCatalogId: 0,
        aiGuesses: {},         // controlId -> { suggestedAnswerId } — from previous answers
        aiGuideCache: {},      // controlId -> { questions }
        prefetchQueue: [],     // control indices to prefetch
        saving: false,
        totalControls: 0,
        answeredCount: 0
    };

    // ---- Initialize & Open Wizard ----
    window.openAssessmentWizard = function() {
        wizardState.assessmentId = window.assessmentId || 0;
        wizardState.securityCatalogId = window.securityCatalogId || 0;
        wizardState.active = true;
        wizardState.aiGuesses = {};
        wizardState.aiGuideCache = {};
        wizardState.prefetchQueue = [];
        wizardState.currentIndex = 0;
        wizardState.saving = false;

        // Collect controls from DOM
        collectControlsFromDOM();

        // Collect maturity answers from the first dropdown
        collectMaturityAnswers();

        // Show wizard modal
        $('#wizard-modal-bg').css('display', 'flex');
        showWizardLoading('AI is analyzing the best assessment order...');

        // Ask AI for optimal order
        requestAIOrder();
    };

    function collectControlsFromDOM() {
        var controls = [];
        $('tr[data-control-id]').each(function() {
            var $row = $(this);
            var controlId = $row.data('control-id');
            var controlName = $row.data('control-name') || '';
            var $select = $row.find('.answer-select');
            var $textarea = $row.find('.comment-textarea');
            var answerId = $select.val() || '';
            var answerText = answerId ? $select.find('option:selected').text().trim() : '';
            var comment = $textarea.val() || '';

            // Get domain info
            var $domain = $row.closest('.domain-collapsible');
            var domainName = $domain.find('.domain-title').first().text().trim();
            var domainId = $domain.find('.domain-checkmark').first().data('domain-id') || '';

            // Get control detail from the ctrl-desc-wrap
            var controlDetail = '';
            var $detail = $row.find('.ctrl-desc-wrap > div').first();
            if ($detail.length) {
                controlDetail = $detail.clone().children('.ctrl-reference').remove().end().text().trim();
            }
            var reference = $row.data('control-reference') || '';
            var tag = $row.data('control-tag') || '';

            // Check taken-over and override state
            var takenOver = $row.find('.taken-over-label').length > 0;
            var overridden = $row.find('.override-slider-checkbox:checked').length > 0;
            var isDisabled = $select.prop('disabled') && !overridden;

            controls.push({
                controlId: controlId,
                controlName: controlName,
                controlDetail: controlDetail,
                domainId: domainId,
                domainName: domainName,
                reference: reference,
                tag: tag,
                answered: !!answerId,
                answerId: answerId,
                answerText: answerText,
                comment: comment,
                takenOver: takenOver,
                overridden: overridden,
                disabled: isDisabled
            });
        });
        wizardState.controls = controls;
        wizardState.totalControls = controls.length;
        wizardState.answeredCount = controls.filter(function(c) { return c.answered; }).length;
    }

    function collectMaturityAnswers() {
        var answers = [];
        var $firstSelect = $('select.answer-select').first();
        if ($firstSelect.length) {
            $firstSelect.find('option').each(function() {
                var val = $(this).val();
                var text = $(this).text().trim();
                if (val && text !== '-- select an answer --') {
                    answers.push({
                        id: val,
                        answer: text,
                        rating: $(this).attr('data-rating') !== undefined ? Number($(this).attr('data-rating')) : null,
                        description: $(this).attr('data-description') || ''
                    });
                }
            });
        }
        wizardState.maturityAnswers = answers;
    }

    // ---- AI Order Request ----
    function requestAIOrder() {
        var controlsData = wizardState.controls.map(function(c) {
            return {
                name: c.controlName,
                domainName: c.domainName,
                reference: c.reference,
                detail: c.controlDetail,
                answered: c.answered
            };
        });

        $.ajax({
            url: '/assessment/wizard-order-controls',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ controls: controlsData, securityCatalogId: wizardState.securityCatalogId }),
            success: function(resp) {
                if (resp && resp.success && resp.order) {
                    reorderControls(resp.order);
                }
                hideWizardLoading();
                // Find first unanswered control
                var startIdx = 0;
                for (var i = 0; i < wizardState.controls.length; i++) {
                    if (!wizardState.controls[i].answered && !wizardState.controls[i].disabled) {
                        startIdx = i;
                        break;
                    }
                }
                wizardState.currentIndex = startIdx;
                renderCurrentControl();
                // Start prefetching AI guesses
                prefetchNextGuesses();
            },
            error: function() {
                hideWizardLoading();
                renderCurrentControl();
                prefetchNextGuesses();
            }
        });
    }

    function reorderControls(order) {
        var original = wizardState.controls.slice();
        var reordered = [];
        var usedIndices = new Set();

        for (var i = 0; i < order.length; i++) {
            var idx = order[i];
            if (idx >= 0 && idx < original.length && !usedIndices.has(idx)) {
                reordered.push(original[idx]);
                usedIndices.add(idx);
            }
        }
        // Add any controls not in the order
        for (var j = 0; j < original.length; j++) {
            if (!usedIndices.has(j)) {
                reordered.push(original[j]);
            }
        }
        wizardState.controls = reordered;
    }

    // ---- Prefetch Guide Questions + Previous-Answer Guesses ----
    function prefetchNextGuesses() {
        // Build previous-answers context from already-answered controls
        var previousAnswers = [];
        for (var i = 0; i < wizardState.controls.length; i++) {
            var c = wizardState.controls[i];
            if (c.answered && c.answerText) {
                previousAnswers.push({ controlName: c.controlName, answer: c.answerText });
            }
            if (previousAnswers.length >= 10) break;
        }

        var current = wizardState.currentIndex;
        for (var offset = 0; offset <= 2; offset++) {
            var idx = current + offset;
            if (idx < wizardState.controls.length) {
                var ctrl = wizardState.controls[idx];
                if (!ctrl.disabled) {
                    // Guess from previous answers (only if we have some answered controls)
                    if (previousAnswers.length > 0 && !wizardState.aiGuesses[ctrl.controlId]) {
                        prefetchGuessForControl(ctrl, previousAnswers);
                    }
                    // Guide questions
                    if (!wizardState.aiGuideCache[ctrl.controlId]) {
                        prefetchGuideForControl(ctrl);
                    }
                }
            }
        }
    }

    function prefetchGuessForControl(ctrl, previousAnswers) {
        wizardState.aiGuesses[ctrl.controlId] = { loading: true };
        if (wizardState.controls[wizardState.currentIndex] &&
            wizardState.controls[wizardState.currentIndex].controlId === ctrl.controlId) {
            renderMaturityAnswerCards(wizardState.controls[wizardState.currentIndex]);
        }
        $.ajax({
            url: '/assessment/wizard-guess-answer',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                controlName: ctrl.controlName,
                controlDetail: ctrl.controlDetail,
                domainName: ctrl.domainName,
                maturityModelAnswers: wizardState.maturityAnswers,
                previousAnswers: previousAnswers
            }),
            success: function(resp) {
                if (resp && resp.success && resp.suggestedAnswerId) {
                    wizardState.aiGuesses[ctrl.controlId] = {
                        suggestedAnswerId: resp.suggestedAnswerId,
                        loading: false
                    };
                } else {
                    wizardState.aiGuesses[ctrl.controlId] = { loading: false, failed: true };
                }
                // Re-render answer cards if this is the current control
                if (wizardState.controls[wizardState.currentIndex] &&
                    wizardState.controls[wizardState.currentIndex].controlId === ctrl.controlId) {
                    renderMaturityAnswerCards(wizardState.controls[wizardState.currentIndex]);
                }
            },
            error: function() {
                wizardState.aiGuesses[ctrl.controlId] = { loading: false, failed: true };
                if (wizardState.controls[wizardState.currentIndex] &&
                    wizardState.controls[wizardState.currentIndex].controlId === ctrl.controlId) {
                    renderMaturityAnswerCards(wizardState.controls[wizardState.currentIndex]);
                }
            }
        });
    }

    function prefetchGuideForControl(ctrl) {
        if (ctrl.disabled || wizardState.aiGuideCache[ctrl.controlId]) return;
        // Mark as loading to prevent duplicate requests
        wizardState.aiGuideCache[ctrl.controlId] = { loading: true, questions: null, answers: {} };

        $.ajax({
            url: '/assessment/generate-answering-guide-questions',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                controlId: ctrl.controlId,
                controlName: ctrl.controlName,
                controlDetail: ctrl.controlDetail,
                securityCatalogId: wizardState.securityCatalogId
            }),
            success: function(resp) {
                if (resp && resp.success && resp.questions) {
                    wizardState.aiGuideCache[ctrl.controlId] = {
                        loading: false,
                        questions: resp.questions,
                        answers: {}
                    };
                    // Re-render right panel if this is the current control
                    if (wizardState.controls[wizardState.currentIndex] &&
                        wizardState.controls[wizardState.currentIndex].controlId === ctrl.controlId) {
                        renderGuideQuestions(ctrl, resp.questions, {});
                    }
                } else {
                    wizardState.aiGuideCache[ctrl.controlId] = { loading: false, failed: true };
                    if (wizardState.controls[wizardState.currentIndex] &&
                        wizardState.controls[wizardState.currentIndex].controlId === ctrl.controlId) {
                        renderAIGuideSection(ctrl);
                    }
                }
            },
            error: function() {
                wizardState.aiGuideCache[ctrl.controlId] = { loading: false, failed: true };
                if (wizardState.controls[wizardState.currentIndex] &&
                    wizardState.controls[wizardState.currentIndex].controlId === ctrl.controlId) {
                    renderAIGuideSection(ctrl);
                }
            }
        });
    }

    // ---- Render Current Control ----
    function renderCurrentControl() {
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl) {
            renderWizardComplete();
            return;
        }

        updateWizardProgress();
        updateNavigationState();

        // Control info section
        var $info = $('#wizard-control-info');
        var domainBadge = ctrl.domainName ? '<span class="wizard-domain-badge">' + escapeHtml(ctrl.domainName) + '</span>' : '';
        var refBadge = ctrl.reference ? '<span class="wizard-ref-badge">Ref: ' + escapeHtml(ctrl.reference) + '</span>' : '';

        $info.html(
            '<div class="wizard-control-header">' +
                '<div class="wizard-control-badges">' + domainBadge + refBadge + '</div>' +
                '<h3 class="wizard-control-title">' + escapeHtml(ctrl.controlName) + '</h3>' +
                '<div class="wizard-control-detail">' + escapeHtml(ctrl.controlDetail || 'No detail available') + '</div>' +
            '</div>'
        );

        // Taken-over notice
        if (ctrl.disabled) {
            $info.append(
                '<div class="wizard-taken-over-notice">' +
                    '<span>&#128274;</span> This control is managed by an Org Service and cannot be edited here.' +
                '</div>'
            );
        }

        // Maturity answers selection
        renderMaturityAnswerCards(ctrl);

        // Comment section
        var $comment = $('#wizard-comment-area');
        $comment.html(
            '<label class="wizard-comment-label">Comment <span class="text-muted">(optional)</span></label>' +
            '<textarea id="wizard-comment-input" class="wizard-comment-input" placeholder="Add notes or context for this assessment..."' +
            (ctrl.disabled ? ' disabled' : '') +
            '>' + escapeHtml(ctrl.comment || '') + '</textarea>' +
            (!ctrl.disabled ? '<button type="button" class="wizard-comment-clear-btn" onclick="wizardClearComment()">Clear</button>' : '')
        );

        // AI Guide section (right panel — auto-loads)
        renderAIGuideSection(ctrl);

        // Scroll left column to top
        $('.wizard-col-left').scrollTop(0);
    }

    function renderMaturityAnswerCards(ctrl) {
        var $answers = $('#wizard-answer-cards');
        $answers.empty();

        var guideCache = wizardState.aiGuideCache[ctrl.controlId];
        var guideSuggestedId = (guideCache && guideCache.guideSuggestedAnswerId) ? String(guideCache.guideSuggestedAnswerId) : null;
        var guessCache = wizardState.aiGuesses[ctrl.controlId];
        var guessSuggestedId = (guessCache && !guessCache.loading && !guessCache.failed && guessCache.suggestedAnswerId)
            ? String(guessCache.suggestedAnswerId) : null;

        var guessLoading = (guessCache && guessCache.loading === true);

        wizardState.maturityAnswers.forEach(function(ans) {
            var isSelected = ctrl.answerId && String(ctrl.answerId) === String(ans.id);
            var isGuideSuggested = guideSuggestedId && String(ans.id) === guideSuggestedId;
            var isGuessSuggested = guessSuggestedId && String(ans.id) === guessSuggestedId;
            var classes = 'wizard-answer-card';
            if (isSelected) classes += ' wizard-answer-selected';
            if (isGuideSuggested && !isSelected) classes += ' wizard-answer-guide-suggested';
            else if (isGuessSuggested && !isSelected) classes += ' wizard-answer-suggested';

            var ratingBar = '';
            if (ans.rating !== null && ans.rating !== undefined) {
                ratingBar = '<div class="wizard-rating-bar"><div class="wizard-rating-fill" style="width:' + ans.rating + '%"></div></div>';
            }

            var pills = '';
            if (isSelected) pills += '<span class="wizard-answer-check">&#10003;</span>';
            if (isGuessSuggested) pills += '<span class="wizard-answer-ai-tag">Based on previous answers</span>';
            if (isGuideSuggested) pills += '<span class="wizard-answer-guide-tag">Based on Guide</span>';

            var html =
                '<div class="' + classes + '" data-answer-id="' + ans.id + '" data-answer-text="' + escapeHtml(ans.answer) + '"' +
                (ctrl.disabled ? '' : ' onclick="wizardSelectAnswer(\'' + ans.id + '\', \'' + escapeJsString(ans.answer) + '\')"') + '>' +
                    '<div class="wizard-answer-card-top">' +
                        '<span class="wizard-answer-name">' + escapeHtml(ans.answer) + '</span>' +
                        '<span class="wizard-answer-pills">' + pills + '</span>' +
                    '</div>' +
                    (ans.description ? '<div class="wizard-answer-desc">' + escapeHtml(ans.description) + '</div>' : '') +
                    ratingBar +
                '</div>';
            $answers.append(html);
        });

        // Show loading hint while guess is being fetched
        if (guessLoading) {
            $answers.append(
                '<div class="wizard-guess-loading-hint">' +
                    '<span class="wizard-guess-spinner"></span>' +
                    '<span>Analyzing previous answers...</span>' +
                '</div>'
            );
        }
    }

    function renderAIGuideSection(ctrl) {
        var $guide = $('#wizard-ai-guide');
        if (ctrl.disabled) {
            $guide.html('<div class="wizard-guide-disabled"><span>&#128274;</span> This control is read-only and cannot be assessed here.</div>');
            return;
        }

        var cached = wizardState.aiGuideCache[ctrl.controlId];

        // Not yet started — auto-trigger fetch and show spinner
        if (!cached) {
            $guide.html(
                '<div class="wizard-guide-section wizard-guide-active">' +
                    '<div class="wizard-guide-header">' +
                        '<span class="wizard-guide-icon">&#128161;</span>' +
                        '<span class="wizard-guide-title">AI Guided Assessment</span>' +
                    '</div>' +
                    '<div class="wizard-ai-guess-loading">' +
                        '<div class="wizard-pulse-dot"></div>' +
                        '<span>Generating assessment questions...</span>' +
                    '</div>' +
                '</div>'
            );
            prefetchGuideForControl(ctrl);
            return;
        }

        // Still loading
        if (cached.loading) {
            $guide.html(
                '<div class="wizard-guide-section wizard-guide-active">' +
                    '<div class="wizard-guide-header">' +
                        '<span class="wizard-guide-icon">&#128161;</span>' +
                        '<span class="wizard-guide-title">AI Guided Assessment</span>' +
                    '</div>' +
                    '<div class="wizard-ai-guess-loading">' +
                        '<div class="wizard-pulse-dot"></div>' +
                        '<span>Generating assessment questions...</span>' +
                    '</div>' +
                '</div>'
            );
            return;
        }

        // Failed
        if (cached.failed) {
            $guide.html(
                '<div class="wizard-guide-section wizard-guide-active">' +
                    '<div class="wizard-guide-header">' +
                        '<span class="wizard-guide-icon">&#128161;</span>' +
                        '<span class="wizard-guide-title">AI Guided Assessment</span>' +
                    '</div>' +
                    '<p class="wizard-guide-error">Could not generate questions.</p>' +
                    '<button type="button" class="wizard-guide-retry-btn" onclick="wizardStartGuide()">&#8635; Retry</button>' +
                '</div>'
            );
            return;
        }

        // Questions ready
        if (cached.questions) {
            renderGuideQuestions(ctrl, cached.questions, cached.answers || {});
        }
    }

    function renderGuideQuestions(ctrl, questions, userAnswers) {
        var html = '<div class="wizard-guide-section wizard-guide-active">' +
            '<div class="wizard-guide-header">' +
                '<span class="wizard-guide-icon">&#128161;</span>' +
                '<span class="wizard-guide-title">AI Guided Assessment</span>' +
            '</div>' +
            '<div class="wizard-guide-questions">';

        questions.forEach(function(q, idx) {
            var yesSelected = userAnswers[idx] === 'Yes' ? ' question-pill-selected' : '';
            var noSelected = userAnswers[idx] === 'No' ? ' question-pill-selected' : '';
            html +=
                '<div class="wizard-guide-question">' +
                    '<div class="wizard-guide-q-label">' + (idx + 1) + '. ' + escapeHtml(q) + '</div>' +
                    '<div class="wizard-guide-q-pills">' +
                        '<button type="button" class="question-pill' + yesSelected + '" data-q-idx="' + idx + '" data-value="Yes" onclick="wizardGuideAnswer(this)">Yes</button>' +
                        '<button type="button" class="question-pill' + noSelected + '" data-q-idx="' + idx + '" data-value="No" onclick="wizardGuideAnswer(this)">No</button>' +
                    '</div>' +
                '</div>';
        });

        // Check if all answered
        var allAnswered = questions.length > 0 && Object.keys(userAnswers).length === questions.length;
        html += '<button type="button" class="guide-submit-answers-btn' + (allAnswered ? '' : ' disabled') + '" onclick="wizardSubmitGuide()"' +
            (allAnswered ? '' : ' disabled') + '>Get AI Recommendation</button>';

        html += '</div></div>';
        $('#wizard-ai-guide').html(html);
    }

    // ---- Wizard Actions ----
    window.wizardSelectAnswer = function(answerId, answerText) {
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl || ctrl.disabled) return;

        ctrl.answerId = answerId;
        ctrl.answerText = answerText;
        ctrl.answered = true;

        // Update card UI
        renderMaturityAnswerCards(ctrl);

        // Save to backend
        saveCurrentAnswer();
    };

    // wizardStartGuide is now a retry/refresh — clears cache and re-fetches
    window.wizardStartGuide = function() {
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl) return;
        delete wizardState.aiGuideCache[ctrl.controlId];
        renderAIGuideSection(ctrl);
    };

    window.wizardGuideAnswer = function(btn) {
        var $btn = $(btn);
        var idx = $btn.data('q-idx');
        var value = $btn.data('value');
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl) return;

        // Toggle selection
        $btn.siblings('.question-pill').removeClass('question-pill-selected');
        $btn.addClass('question-pill-selected');

        // Store answer
        var cache = wizardState.aiGuideCache[ctrl.controlId];
        if (cache) {
            cache.answers[idx] = value;
            // Enable submit button if all answered
            var allAnswered = cache.questions.length > 0 && Object.keys(cache.answers).length === cache.questions.length;
            var $submit = $btn.closest('.wizard-guide-questions').find('.guide-submit-answers-btn');
            if (allAnswered) {
                $submit.removeClass('disabled').prop('disabled', false);
            }
        }
    };

    window.wizardSubmitGuide = function() {
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl) return;

        var cache = wizardState.aiGuideCache[ctrl.controlId];
        if (!cache || !cache.questions) return;

        var answers = [];
        for (var i = 0; i < cache.questions.length; i++) {
            if (!cache.answers[i]) {
                alert('Please answer all questions.');
                return;
            }
            answers.push(cache.answers[i]);
        }

        var $guide = $('#wizard-ai-guide');
        $guide.find('.guide-submit-answers-btn').prop('disabled', true).text('Analyzing...');

        $.ajax({
            url: '/assessment/generate-answer-from-guide',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                controlId: ctrl.controlId,
                controlName: ctrl.controlName,
                securityCatalogId: wizardState.securityCatalogId,
                questions: cache.questions,
                answers: answers,
                maturityModelAnswers: wizardState.maturityAnswers
            }),
            success: function(resp) {
                if (resp && resp.proposedAnswerId) {
                    // Store guide suggestion and highlight the pill — user still clicks to select
                    cache.guideSuggestedAnswerId = resp.proposedAnswerId;
                    renderMaturityAnswerCards(ctrl);
                    $guide.find('.guide-submit-answers-btn').prop('disabled', true).text('Analyzed \u2713');

                    // Use the comment returned alongside the proposal (no extra round-trip needed).
                    // Save immediately to server so the SSE broadcast triggered by the subsequent
                    // answer save already contains the comment.
                    if (resp.comment) {
                        $('#wizard-comment-input').val(resp.comment);
                        // Cancel any pending debounce timer so the immediate save wins
                        if (window._commentSaveTimers && window._commentSaveTimers[ctrl.controlId]) {
                            clearTimeout(window._commentSaveTimers[ctrl.controlId]);
                            delete window._commentSaveTimers[ctrl.controlId];
                        }
                        var csrfMeta = document.querySelector('meta[name="_csrf"]');
                        var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
                        var csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
                        var csrfHeaderName = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';
                        var headers = { 'Content-Type': 'application/json' };
                        headers[csrfHeaderName] = csrfToken;
                        fetch('/assessment/' + wizardState.assessmentId + '/control/' + ctrl.controlId + '/comment', {
                            method: 'PUT',
                            headers: headers,
                            body: JSON.stringify({ comment: resp.comment })
                        }).then(function() {
                            ctrl.comment = resp.comment;
                            // Sync to main page textarea
                            var $ta = $('textarea.comment-textarea[data-control-id="' + ctrl.controlId + '"]');
                            if ($ta.length && !$ta.prop('disabled')) { $ta.val(resp.comment); }
                        });
                    }
                }
            },
            error: function() {
                $guide.find('.guide-submit-answers-btn').prop('disabled', false).text('Get AI Recommendation');
                alert('Error analyzing answers. Please try again.');
            }
        });
    };

    // ---- Navigation ----
    window.wizardNext = function() {
        saveCurrentComment();
        wizardState.currentIndex++;
        if (wizardState.currentIndex >= wizardState.controls.length) {
            renderWizardComplete();
            return;
        }
        renderCurrentControl();
        prefetchNextGuesses();
    };

    window.wizardPrev = function() {
        saveCurrentComment();
        if (wizardState.currentIndex > 0) {
            wizardState.currentIndex--;
            renderCurrentControl();
        }
    };

    window.wizardSkip = function() {
        saveCurrentComment();
        wizardState.currentIndex++;
        if (wizardState.currentIndex >= wizardState.controls.length) {
            renderWizardComplete();
            return;
        }
        renderCurrentControl();
        prefetchNextGuesses();
    };

    window.wizardGoTo = function(index) {
        if (index >= 0 && index < wizardState.controls.length) {
            saveCurrentComment();
            wizardState.currentIndex = index;
            renderCurrentControl();
            prefetchNextGuesses();
        }
    };

    window.closeAssessmentWizard = function() {
        wizardState.active = false;

        var ctrl = wizardState.controls[wizardState.currentIndex];
        var comment = (ctrl && !ctrl.disabled) ? ($('#wizard-comment-input').val() || '') : null;
        var commentChanged = ctrl && !ctrl.disabled && comment !== null && comment !== ctrl.comment;

        if (commentChanged) {
            ctrl.comment = comment;
            // Update main page textarea immediately (visual sync)
            var $textarea = $('textarea.comment-textarea[data-control-id="' + ctrl.controlId + '"]');
            if ($textarea.length && !$textarea.prop('disabled')) {
                $textarea.val(comment);
            }
            // Cancel any debounce timer for this control to avoid a double-save after reload
            if (window._commentSaveTimers && window._commentSaveTimers[ctrl.controlId]) {
                clearTimeout(window._commentSaveTimers[ctrl.controlId]);
                delete window._commentSaveTimers[ctrl.controlId];
            }
            // Save directly (not via debounce) then reload
            var csrfMeta = document.querySelector('meta[name="_csrf"]');
            fetch('/assessment/' + wizardState.assessmentId + '/control/' + ctrl.controlId + '/comment', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrfMeta ? csrfMeta.getAttribute('content') : ''
                },
                body: JSON.stringify({ comment: comment })
            }).finally(function() {
                $('#wizard-modal-bg').css('display', 'none');
                window.location.reload();
            });
        } else {
            $('#wizard-modal-bg').css('display', 'none');
            // Reload to sync all changes
            window.location.reload();
        }
    };

    // ---- Save Logic ----
    function saveCurrentAnswer() {
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl || !ctrl.answerId || ctrl.disabled) return;

        var assessmentId = wizardState.assessmentId;
        if (!assessmentId) return;

        // Also update the DOM dropdown on the main page
        var $select = $('select.answer-select[data-control-id="' + ctrl.controlId + '"]');
        if ($select.length) {
            $select.val(ctrl.answerId).trigger('change');
        }

        // Update answered count
        wizardState.answeredCount = wizardState.controls.filter(function(c) { return c.answered; }).length;
        updateWizardProgress();
    }

    window.wizardClearComment = function() {
        $('#wizard-comment-input').val('');
        saveCurrentComment();
    };

    function saveCurrentComment() {
        var ctrl = wizardState.controls[wizardState.currentIndex];
        if (!ctrl || ctrl.disabled) return;

        var comment = $('#wizard-comment-input').val() || '';
        if (comment !== ctrl.comment) {
            ctrl.comment = comment;

            // Save comment to the main page textarea
            var $textarea = $('textarea.comment-textarea[data-control-id="' + ctrl.controlId + '"]');
            if ($textarea.length && !$textarea.prop('disabled')) {
                $textarea.val(comment).trigger('input');
            }
        }
    }

    // ---- Progress & Navigation UI ----
    function updateWizardProgress() {
        var current = wizardState.currentIndex + 1;
        var total = wizardState.controls.length;
        var answered = wizardState.controls.filter(function(c) { return c.answered; }).length;
        var pct = total > 0 ? Math.round((answered / total) * 100) : 0;

        $('#wizard-progress-current').text(current);
        $('#wizard-progress-total').text(total);
        $('#wizard-progress-answered').text(answered);
        $('#wizard-progress-pct').text(pct + '%');
        $('#wizard-progress-bar-fill').css('width', pct + '%');

        // Update minimap
        renderMinimap();
    }

    function updateNavigationState() {
        var isFirst = wizardState.currentIndex === 0;
        var isLast = wizardState.currentIndex >= wizardState.controls.length - 1;

        $('#wizard-prev-btn').prop('disabled', isFirst);
        $('#wizard-next-btn').prop('disabled', isLast).text(isLast ? 'Finish' : 'Next →');
    }

    function renderMinimap() {
        var $map = $('#wizard-minimap');
        $map.empty();
        var maxVisible = 50; // Show dots for up to 50 controls
        var step = wizardState.controls.length > maxVisible ? Math.ceil(wizardState.controls.length / maxVisible) : 1;

        for (var i = 0; i < wizardState.controls.length; i += step) {
            var ctrl = wizardState.controls[i];
            var classes = 'wizard-minimap-dot';
            if (i === wizardState.currentIndex) classes += ' current';
            else if (ctrl.answered) classes += ' answered';
            else if (ctrl.disabled) classes += ' disabled';

            $map.append('<span class="' + classes + '" title="' + escapeHtml(ctrl.controlName) +
                '" data-idx="' + i + '" onclick="wizardGoTo(' + i + ')"></span>');
        }
    }

    // ---- Completion View ----
    function renderWizardComplete() {
        var answered = wizardState.controls.filter(function(c) { return c.answered; }).length;
        var total = wizardState.controls.length;
        var pct = total > 0 ? Math.round((answered / total) * 100) : 0;

        var $info = $('#wizard-control-info');
        $info.html(
            '<div class="wizard-complete">' +
                '<div class="wizard-complete-icon">&#127942;</div>' +
                '<h3>Assessment Wizard Complete</h3>' +
                '<p class="wizard-complete-summary">' +
                    'You have answered <strong>' + answered + '</strong> of <strong>' + total + '</strong> controls (' + pct + '% complete).' +
                '</p>' +
                (pct < 100 ?
                    '<p class="wizard-complete-hint">Some controls are still unanswered. You can close the wizard and continue manually, or restart to review them.</p>' +
                    '<button type="button" class="btn-action" onclick="wizardGoToFirstUnanswered()">Review Unanswered</button>'
                    : '<p class="wizard-complete-hint">All controls have been assessed. You can close this wizard now.</p>'
                ) +
            '</div>'
        );
        $('#wizard-ai-guess').html('');
        $('#wizard-answer-cards').html('');
        $('#wizard-comment-area').html('');
        $('#wizard-ai-guide').html('');
        updateWizardProgress();
    }

    window.wizardGoToFirstUnanswered = function() {
        for (var i = 0; i < wizardState.controls.length; i++) {
            if (!wizardState.controls[i].answered && !wizardState.controls[i].disabled) {
                wizardState.currentIndex = i;
                renderCurrentControl();
                prefetchNextGuesses();
                return;
            }
        }
        // All answered
        renderWizardComplete();
    };

    // ---- Loading overlay ----
    function showWizardLoading(text) {
        $('#wizard-loading-overlay').addClass('active');
        $('#wizard-loading-label').text(text || 'Loading...');
    }

    function hideWizardLoading() {
        $('#wizard-loading-overlay').removeClass('active');
    }

    // ---- Helpers ----
    function escapeHtml(str) {
        if (!str) return '';
        return $('<span/>').text(str).html();
    }

    function escapeJsString(str) {
        if (!str) return '';
        return str.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"');
    }

})();
